#!/usr/bin/env python3
"""
DragonBrain 3.0 — original sound synthesis.

All NEW sounds are synthesized from scratch (numpy oscillators / filtered
noise) — no samples from any third party.  The 15 DragonBrain 2.0 .ogg
files are this project's own assets and are copied verbatim from
_preserved_2_0/.

Run:  python3 tools/generate_sounds.py   (needs numpy + soundfile)
"""
import os
import shutil
import numpy as np
import soundfile as sf

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/dragonbrain/sounds")
SR = 44100
rng = np.random.default_rng(20261026)

def env(n, attack=0.01, decay=0.35):
    t = np.linspace(0, 1, n)
    a = np.minimum(t / max(attack, 1e-4), 1.0)
    return a * np.exp(-t / decay)

def tone(freq, dur, kind="sine", glide=1.0, attack=0.01, decay=0.4, harmonics=(1.0,)):
    n = int(SR * dur)
    t = np.arange(n) / SR
    f = freq * (1.0 + (glide - 1.0) * t / dur)
    phase = np.cumsum(f) / SR * 2 * np.pi
    sig = np.zeros(n)
    for i, h in enumerate(harmonics, start=1):
        if kind == "sine":
            sig += h * np.sin(phase * i)
        elif kind == "triangle":
            sig += h * (2 / np.pi) * np.arcsin(np.sin(phase * i))
    return sig * env(n, attack, decay)

def noise_burst(dur, lowpass=0.2, attack=0.01, decay=0.4):
    n = int(SR * dur)
    x = rng.standard_normal(n)
    kernel = np.ones(max(2, int(1 / lowpass))) / max(2, int(1 / lowpass))
    x = np.convolve(x, kernel, mode="same")
    return x * env(n, attack, decay)

def mix_at(buf, sig, start_s, gain=1.0):
    i = int(start_s * SR)
    end = min(len(buf), i + len(sig))
    buf[i:end] += gain * sig[: end - i]

def save(name, sig, peak=0.85):
    m = np.max(np.abs(sig)) or 1.0
    sig = (sig / m * peak).astype(np.float32)
    os.makedirs(OUT, exist_ok=True)
    sf.write(os.path.join(OUT, name + ".ogg"), sig, SR, format="OGG", subtype="VORBIS")
    print("sound:", name, f"{len(sig)/SR:.2f}s")

def main():
    # --- End Wisp: airy two-note chime ------------------------------------
    buf = np.zeros(int(SR * 1.4))
    mix_at(buf, tone(1174.7, 0.8, harmonics=(1, 0.4, 0.15)), 0.0, 0.7)   # D6
    mix_at(buf, tone(1568.0, 1.0, harmonics=(1, 0.3)), 0.22, 0.55)      # G6
    mix_at(buf, noise_burst(0.6, lowpass=0.02), 0.0, 0.12)
    save("end_wisp_ambient", buf)
    save("end_wisp_death", tone(880, 0.9, glide=0.35, harmonics=(1, 0.5, 0.2)))
    save("end_wisp_hurt", tone(1318.5, 0.25, glide=1.25, harmonics=(1, 0.4)))

    # --- Rift Stalker: low growl / wind-up snarl / bite snap --------------
    buf = np.zeros(int(SR * 1.6))
    mix_at(buf, tone(82.4, 1.5, kind="triangle", glide=0.85, harmonics=(1, 0.6, 0.4, 0.2)), 0, 0.9)
    trem = 0.5 + 0.5 * np.sin(np.arange(len(buf)) / SR * 2 * np.pi * 11)
    mix_at(buf, noise_burst(1.4, lowpass=0.08, decay=0.9) * trem[: int(SR * 1.4)], 0.05, 0.5)
    save("rift_stalker_growl", buf)
    buf = np.zeros(int(SR * 1.0))
    mix_at(buf, tone(110, 0.9, kind="triangle", glide=1.9, harmonics=(1, 0.7, 0.3)), 0, 0.8)
    mix_at(buf, noise_burst(0.8, lowpass=0.04, decay=0.7), 0.1, 0.35)
    save("rift_stalker_windup", buf)
    buf = np.zeros(int(SR * 0.4))
    mix_at(buf, noise_burst(0.08, lowpass=0.5, decay=0.05), 0.0, 0.9)
    mix_at(buf, tone(220, 0.15, glide=0.4), 0.02, 0.4)
    save("rift_stalker_bite", buf)

    # --- Ancient Sentinel: stone-metal clang / awakening grind -------------
    buf = np.zeros(int(SR * 1.2))
    for f, g in ((196, 0.8), (294, 0.5), (587, 0.35), (1175, 0.18)):
        mix_at(buf, tone(f, 1.0, decay=0.25), 0.0, g)
    mix_at(buf, noise_burst(0.15, lowpass=0.3, decay=0.06), 0.0, 0.5)
    save("ancient_sentinel_clang", buf)
    buf = np.zeros(int(SR * 2.2))
    mix_at(buf, noise_burst(1.6, lowpass=0.05, attack=0.3, decay=0.9), 0.0, 0.7)
    mix_at(buf, tone(65.4, 2.0, glide=1.5, attack=0.25, decay=0.9), 0.1, 0.7)
    mix_at(buf, tone(523.25, 0.8, decay=0.3), 1.3, 0.3)   # core ignites
    save("ancient_sentinel_awaken", buf)
    save("ancient_sentinel_step", noise_burst(0.25, lowpass=0.12, decay=0.12) +
         tone(55, 0.25, decay=0.12))

    # --- Ambience / feedback ------------------------------------------------
    buf = np.zeros(int(SR * 6.0))
    base = noise_burst(6.0, lowpass=0.01, attack=0.4, decay=3.0)
    lfo = 0.6 + 0.4 * np.sin(np.arange(len(buf)) / SR * 2 * np.pi * 0.07)
    mix_at(buf, base * lfo, 0, 0.5)
    mix_at(buf, tone(130.8, 5.5, attack=0.4, decay=2.5), 0.2, 0.18)
    mix_at(buf, tone(196.0, 5.0, attack=0.5, decay=2.2), 0.4, 0.12)
    save("vault_ambience", buf)

    buf = np.zeros(int(SR * 1.6))
    for i, f in enumerate((659.3, 987.8, 1318.5)):
        mix_at(buf, tone(f, 1.0, harmonics=(1, 0.3)), i * 0.14, 0.5)
    save("discovery_chime", buf)

    buf = np.zeros(int(SR * 2.4))
    for i, f in enumerate((261.6, 329.6, 392.0, 523.3)):
        mix_at(buf, tone(f, 1.4, kind="triangle", harmonics=(1, 0.5, 0.2)), i * 0.18, 0.5)
    mix_at(buf, tone(1046.5, 1.2, harmonics=(1, 0.25)), 0.72, 0.35)
    save("reward_fanfare", buf)

    buf = np.zeros(int(SR * 0.9))
    mix_at(buf, tone(2093, 0.5, decay=0.2), 0.0, 0.6)
    mix_at(buf, tone(1046.5, 0.6, decay=0.3), 0.18, 0.3)
    save("rift_compass_ping", buf)

    buf = np.zeros(int(SR * 2.0))
    mix_at(buf, tone(55, 1.9, glide=2.6, attack=0.05, decay=0.8, harmonics=(1, 0.6, 0.35)), 0, 0.9)
    mix_at(buf, noise_burst(1.5, lowpass=0.03, attack=0.2, decay=0.8), 0.3, 0.45)
    mix_at(buf, tone(1568, 0.7, decay=0.25), 1.25, 0.25)
    save("dragon_abyssal_transition", buf)

    save("eclipse_lantern_hum", tone(174.6, 1.8, attack=0.3, decay=1.2, harmonics=(1, 0.3, 0.08)))

    # --- preserved 2.0 sounds (this project's own MIT assets) --------------
    src = os.path.join(ROOT, "_preserved_2_0/sounds")
    for f in sorted(os.listdir(src)):
        shutil.copy2(os.path.join(src, f), os.path.join(OUT, f))
        print("preserved:", f)

if __name__ == "__main__":
    main()
