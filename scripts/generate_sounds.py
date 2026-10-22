#!/usr/bin/env python3
"""
DragonBrain 3.0 - original sound & music synthesis.

Every asset is generated from scratch with numpy (no samples, no recordings):
layered oscillators, filtered noise, waveshaping and Karplus-Strong plucks.
Output format matches the DragonBrain 2.0 assets: Ogg Vorbis, 44100 Hz,
mono for positional effects, stereo for music.

Usage:  python3 scripts/generate_sounds.py [ffmpeg_path]
Writes into src/main/resources/assets/dragonbrain/sounds/.
"""
import subprocess
import sys
import wave
from pathlib import Path

import numpy as np

SR = 44100
rng = np.random.default_rng(0xD3A60B)  # fixed seed: reproducible assets

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "src/main/resources/assets/dragonbrain/sounds"
FFMPEG = sys.argv[1] if len(sys.argv) > 1 else "ffmpeg"


# --------------------------------------------------------------------------- #
# small DSP toolbox
# --------------------------------------------------------------------------- #
def t_axis(dur):
    return np.arange(int(SR * dur)) / SR


def env_ad(dur, attack, decay_pow=1.0):
    """Attack/decay envelope reaching 1 after `attack` seconds."""
    t = t_axis(dur)
    e = np.minimum(1.0, t / max(attack, 1e-4))
    tail = np.clip(1.0 - np.clip(t - attack, 0, None) / max(dur - attack, 1e-4), 0, 1)
    return e * tail ** decay_pow


def fade(sig, fade_in=0.005, fade_out=0.03):
    n = len(sig)
    fi = min(int(SR * fade_in), n // 2)
    fo = min(int(SR * fade_out), n // 2)
    if fi > 0:
        sig[:fi] *= np.linspace(0, 1, fi)
    if fo > 0:
        sig[-fo:] *= np.linspace(1, 0, fo)
    return sig


def sweep(f0, f1, dur, curve="exp"):
    t = t_axis(dur)
    if curve == "exp":
        f = f0 * (f1 / f0) ** (t / dur)
    else:
        f = f0 + (f1 - f0) * (t / dur)
    phase = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(phase), phase


def onepole_lp(sig, cutoff):
    a = 1 - np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(sig)
    acc = 0.0
    for i, x in enumerate(sig):
        acc += a * (x - acc)
        y[i] = acc
    return y


def lp_sweep(sig, f0, f1):
    """Time-varying one-pole lowpass (exponential cutoff glide)."""
    n = len(sig)
    f = f0 * (f1 / f0) ** (np.arange(n) / max(n, 1))
    a = 1 - np.exp(-2 * np.pi * f / SR)
    y = np.empty_like(sig)
    acc = 0.0
    for i in range(n):
        acc += a[i] * (sig[i] - acc)
        y[i] = acc
    return y


def peaking(sig, freq, q, gain_db):
    """RBJ peaking EQ biquad."""
    A = 10 ** (gain_db / 40)
    w0 = 2 * np.pi * freq / SR
    alpha = np.sin(w0) / (2 * q)
    b0 = 1 + alpha * A
    b1 = -2 * np.cos(w0)
    b2 = 1 - alpha * A
    a0 = 1 + alpha / A
    a1 = -2 * np.cos(w0)
    a2 = 1 - alpha / A
    y = np.empty_like(sig)
    x1 = x2 = y1 = y2 = 0.0
    for i, x in enumerate(sig):
        y0 = (b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0
        y[i] = y0
        x2, x1 = x1, x
        y2, y1 = y1, y0
    return y


def soft_clip(sig, drive=1.0):
    return np.tanh(sig * drive) / np.tanh(drive)


def normalize(sig, peak_db=-2.0):
    peak = np.max(np.abs(sig)) + 1e-12
    target = 10 ** (peak_db / 20)
    return sig * (target / peak)


def noise(dur):
    return rng.standard_normal(int(SR * dur))


def karplus(freq, dur, damp=0.996):
    n = int(SR * dur)
    period = max(2, int(SR / freq))
    buf = rng.standard_normal(period) * 0.6
    y = np.empty(n)
    idx = 0
    for i in range(n):
        cur = buf[idx]
        nxt = buf[(idx + 1) % period]
        buf[idx] = damp * 0.5 * (cur + nxt)
        y[i] = cur
        idx = (idx + 1) % period
    return y


def add_at(track, sig, start_sec, gain=1.0):
    i = int(start_sec * SR)
    end = min(len(track), i + len(sig))
    if end > i:
        track[i:end] += sig[: end - i] * gain
    return track


def roar_body(dur, f_start, f_end, growl_hz=29.0):
    """Layered fantasy roar: detuned saws + sub, throat-growl AM, waveshape."""
    t = t_axis(dur)
    f = f_start * (f_end / f_start) ** (t / dur)
    phase = 2 * np.pi * np.cumsum(f) / SR
    saw = 2 * ((phase / (2 * np.pi)) % 1.0) - 1
    saw2 = 2 * (((phase * 1.007) / (2 * np.pi)) % 1.0) - 1
    sub = np.sin(phase * 0.5)
    fifth = 2 * (((phase * 1.5) / (2 * np.pi)) % 1.0) - 1
    body = 0.5 * saw + 0.35 * saw2 + 0.55 * sub + 0.18 * fifth
    growl = 1.0 - 0.62 * (0.5 + 0.5 * np.sin(2 * np.pi * growl_hz * t))
    body = body * growl
    body = soft_clip(body, drive=2.2)
    body = peaking(body, 380, 1.1, 6.0)
    body = peaking(body, 850, 1.4, 4.0)
    body = onepole_lp(body, 2600)
    breath = lp_sweep(noise(dur), 400, 1400) * 0.22 * growl
    return body + breath


# --------------------------------------------------------------------------- #
# the sounds
# --------------------------------------------------------------------------- #
def void_rift_charge():
    dur = 1.8
    t = t_axis(dur)
    a, _ = sweep(110, 660, dur)
    b, _ = sweep(116, 698, dur)  # beating detune = tension
    rise = (t / dur) ** 1.6
    sig = (0.4 * a + 0.4 * b) * rise
    sub = np.sin(2 * np.pi * 42 * t) * 0.5 * rise
    nse = lp_sweep(noise(dur), 300, 3800) * 0.30 * rise
    sig = sig + sub + nse
    # accelerating crackle in the last 0.5 s
    crackle = np.zeros_like(sig)
    pos = int(SR * 1.3)
    interval = int(SR * 0.09)
    while pos < len(sig) - 400:
        burst = noise(0.012) * np.exp(-np.arange(529) / 90.0)
        crackle[pos:pos + len(burst)] += burst * 0.5
        interval = max(int(SR * 0.02), int(interval * 0.78))
        pos += interval
    sig += crackle
    return fade(sig * env_ad(dur, 0.06, 0.4))


def void_rift_erupt():
    dur = 1.3
    boom, _ = sweep(95, 30, dur)
    sig = boom * np.exp(-t_axis(dur) * 4.2) * 1.1
    nse = lp_sweep(noise(dur), 4200, 180) * np.exp(-t_axis(dur) * 5.0)
    sig += nse * 0.9
    # venting hiss content kept low and dark (no painful HF)
    hiss = lp_sweep(noise(dur), 900, 2400) * 0.18 * env_ad(dur, 0.02, 1.6)
    sig += hiss
    return fade(soft_clip(sig, 1.4))


def meteor_whistle():
    dur = 0.95
    # accelerating pitch drop, classic falling-body cue, kept moderate in level
    sig, _ = sweep(2300, 480, dur)
    vib = 1.0 + 0.012 * np.sin(2 * np.pi * 11 * t_axis(dur))
    sig = sig * vib * env_ad(dur, 0.09, 0.55) * 0.55
    whoosh = lp_sweep(noise(dur), 500, 2600) * 0.16 * env_ad(dur, 0.12, 0.8)
    return fade(onepole_lp(sig + whoosh, 5200))


def meteor_impact():
    dur = 1.5
    thud, _ = sweep(58, 27, dur)
    sig = thud * np.exp(-t_axis(dur) * 5.2) * 1.25
    debris = lp_sweep(noise(dur), 950, 140) * np.exp(-t_axis(dur) * 3.4)
    sig += debris * 0.85
    crack = lp_sweep(noise(dur), 3000, 300) * np.exp(-t_axis(dur) * 26) * 0.5
    sig += crack
    return fade(soft_clip(sig, 1.5))


def dread_roar():
    dur = 2.8
    sig = roar_body(dur, 74, 46)
    # swell in like a drawn breath, then the full roar, long decay
    swell = np.clip(t_axis(dur) / 0.38, 0, 1) ** 1.4
    decay = np.exp(-np.clip(t_axis(dur) - 1.5, 0, None) * 1.9)
    sig = sig * swell * (0.35 + 0.65 * decay)
    sub_tail = np.sin(2 * np.pi * 34 * t_axis(dur)) * 0.35 * swell * decay
    return fade(soft_clip(sig + sub_tail, 1.3))


def gravity_well():
    dur = 3.0
    t = t_axis(dur)
    pulse_rate = 2.0 + 4.0 * (t / dur)  # accelerating inward pulses
    pulse_phase = 2 * np.pi * np.cumsum(pulse_rate) / SR
    am = (0.5 + 0.5 * np.sin(pulse_phase)) ** 2
    core = np.sin(2 * np.pi * 54 * t) * am * 0.8
    suck = lp_sweep(noise(dur), 1300, 180) * 0.4 * (t / dur)
    down, _ = sweep(320, 74, dur)
    sig = core + suck + down * 0.25 * am
    return fade(sig * env_ad(dur, 0.25, 0.7))


def cataclysm_transition():
    dur = 4.4
    sig = np.zeros(int(SR * dur))
    # 0.0-1.2s: dissonant riser (minor-second cluster gliding up)
    riser_a, _ = sweep(220, 440, 1.25)
    riser_b, _ = sweep(233, 466, 1.25)
    riser = (riser_a + riser_b) * 0.22 * (t_axis(1.25) / 1.25) ** 1.5
    riser += lp_sweep(noise(1.25), 400, 4200) * 0.20 * (t_axis(1.25) / 1.25)
    sig = add_at(sig, riser, 0.0)
    # impact
    imp = meteor_impact() * 1.05
    sig = add_at(sig, imp, 1.18)
    # layered roar, lower than the standalone dread roar
    roar = roar_body(2.6, 62, 38, growl_hz=24.0)
    roar = roar * env_ad(2.6, 0.12, 1.1)
    sig = add_at(sig, roar, 1.25, gain=0.8)
    # decaying rumble + faint abyssal shimmer
    rum = np.sin(2 * np.pi * 30 * t_axis(2.4)) * np.exp(-t_axis(2.4) * 1.5) * 0.5
    sig = add_at(sig, rum, 1.9)
    for f, at, g in [(1760, 2.2, 0.07), (2349, 2.8, 0.05), (2637, 3.4, 0.04)]:
        tone = np.sin(2 * np.pi * f * t_axis(0.9)) * np.exp(-t_axis(0.9) * 5) * g
        sig = add_at(sig, tone, at)
    return fade(soft_clip(sig, 1.35), fade_out=0.12)


def music_cataclysm():
    """42.0s stereo, 120 BPM, 21 bars of 4/4 - loops cleanly (840 ticks)."""
    dur = 42.0
    n = int(SR * dur)
    L = np.zeros(n + SR)  # render 1 s extra for the loop crossfade
    R = np.zeros(n + SR)
    beat = 0.5
    # D natural minor, abyssal register
    D2, F2, G2, A2, C3, D3, F3, A3, D4, F4, E4, C4, A3_ = (
        73.42, 87.31, 98.0, 110.0, 130.81, 146.83, 174.61, 220.0, 293.66, 349.23, 329.63, 261.63, 220.0,
    )
    # drone bed: D1 + D2 slow beating
    t = np.arange(len(L)) / SR
    drone = (0.30 * np.sin(2 * np.pi * 36.71 * t) + 0.16 * np.sin(2 * np.pi * 73.42 * t)
             + 0.05 * np.sin(2 * np.pi * 110.0 * t))
    drone *= 0.85 + 0.15 * np.sin(2 * np.pi * 0.08 * t)
    L += drone
    R += drone * 0.96
    # pulsing plucked bass line, 8th notes with a sparse end-of-bar answer
    riff = [D2, None, D2, D3, None, D2, None, F2, D2, None, D2, D3, None, C3, None, A2,
            D2, None, D2, D3, None, D2, None, F2, G2, None, A2, None, F2, None, G2, None]
    for bar in range(21):
        gain = 0.55 if bar < 18 else 0.4
        for i, f in enumerate(riff):
            if f is None:
                continue
            start = (bar * 8 + i * 0.5) * beat
            pluck = karplus(f, 0.55, damp=0.994)
            pan = 0.5 + 0.18 * np.sin(i * 1.7)
            L = add_at(L, pluck, start, gain * gain * (1 - pan + 0.5))
            R = add_at(R, pluck, start, gain * gain * (pan + 0.5))
    # sub thump on bar starts
    for bar in range(21):
        thump = np.sin(2 * np.pi * 40 * t_axis(0.5)) * np.exp(-t_axis(0.5) * 7) * 0.5
        L = add_at(L, thump, bar * 8 * beat)
        R = add_at(R, thump, bar * 8 * beat)
    # sparse inharmonic bell motif every 2 bars
    motif = [D4, F4, A3_, None, E4, None, C4, A3_, D4, None, F4, E4, None, D4, None, None]
    for rep, start_bar in enumerate(range(1, 21, 4)):
        for i, f in enumerate(motif):
            if f is None:
                continue
            start = (start_bar * 8 + i * 0.5) * beat
            tt = t_axis(1.6)
            bell = (np.sin(2 * np.pi * f * tt) * np.exp(-tt * 3.2)
                    + 0.5 * np.sin(2 * np.pi * f * 2.76 * tt) * np.exp(-tt * 4.6)
                    + 0.25 * np.sin(2 * np.pi * f * 5.4 * tt) * np.exp(-tt * 6.5)) * 0.30
            pan = 0.3 if (rep + i) % 2 == 0 else 0.7
            L = add_at(L, bell, start, (1.1 - pan))
            R = add_at(R, bell, start, (0.4 + pan))
    # slow stereo noise swells (wind of the void)
    wind = lp_sweep(noise(14.0), 300, 900) * 0.10 * env_ad(14.0, 3.0, 0.8)
    for at in (2.0, 18.0, 32.0):
        L = add_at(L, wind, at)
        R = add_at(R, wind[::-1].copy(), at, 0.8)
    # seamless loop: fold the extra second back into the head
    xfade = SR
    w = np.linspace(0, 1, xfade)
    L[:xfade] = L[:xfade] * (1 - w) * 0 + L[:xfade] * w + L[n:n + xfade] * (1 - w)
    R[:xfade] = R[:xfade] * w + R[n:n + xfade] * (1 - w)
    L = soft_clip(L[:n] * 1.15, 1.6)
    R = soft_clip(R[:n] * 1.15, 1.6)
    return np.stack([L, R], axis=1)


# --------------------------------------------------------------------------- #
# writing
# --------------------------------------------------------------------------- #
def write_ogg(name, samples, quality, peak_db=-2.0):
    """samples: mono (n,) or stereo (n,2) float in [-1, 1]."""
    if samples.ndim == 1:
        samples = samples[:, None]
    samples = normalize(samples, peak_db)
    pcm = (np.clip(samples, -1, 1) * 32767).astype(np.int16)
    wav_path = OUT_DIR / f"{name}.wav"
    ogg_path = OUT_DIR / f"{name}.ogg"
    with wave.open(str(wav_path), "wb") as wf:
        wf.setnchannels(pcm.shape[1])
        wf.setsampwidth(2)
        wf.setframerate(SR)
        wf.writeframes(pcm.tobytes())
    subprocess.run(
        [FFMPEG, "-y", "-hide_banner", "-loglevel", "error", "-i", str(wav_path),
         "-c:a", "libvorbis", "-q:a", str(quality), str(ogg_path)],
        check=True,
    )
    wav_path.unlink()
    print(f"wrote {ogg_path.relative_to(ROOT)} ({ogg_path.stat().st_size} bytes)")


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    write_ogg("void_rift_charge", void_rift_charge(), 4)
    write_ogg("void_rift_erupt", void_rift_erupt(), 4)
    write_ogg("meteor_whistle", meteor_whistle(), 4)
    write_ogg("meteor_impact", meteor_impact(), 4)
    write_ogg("dread_roar", dread_roar(), 4)
    write_ogg("gravity_well", gravity_well(), 4)
    write_ogg("cataclysm_transition", cataclysm_transition(), 4)
    write_ogg("music_cataclysm", music_cataclysm(), 5, peak_db=-3.5)


if __name__ == "__main__":
    main()
