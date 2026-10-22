#!/usr/bin/env python3
"""
DragonBrain 3.0 - abyssal dragon textures.

UV safety strategy: every output is derived per-pixel from the shipped
DragonBrain 2.0 textures. The alpha channel of the body textures is copied
verbatim, so painted/unpainted UV space (and therefore how the texture maps
onto the vanilla Ender Dragon model) cannot change. Color is reworked
through luminance ramps (existing scale shading and part coherence are
preserved) plus a deterministic ridged-noise energy-vein field that follows
the texture's own dark creases.

Outputs (all 256x256 RGBA, vanilla dragon UV):
  dragon.png                 improved abyssal base
  dragon_final.png           Final-phase variant (magenta crackle family)
  dragon_cataclysm.png       Cataclysm variant (near-black, brightest veins)
  dragon_cataclysm_eyes.png  Cataclysm glowing eyes (same 86 px + 1 px glow)

Usage:  python3 scripts/generate_textures.py
Requires pillow + numpy. Reads the current textures from src/main/resources
(dragon.png / dragon_eyes.png act as the luminance+alpha masters), so re-run
it from a clean checkout for reproducible results.
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
TEX = ROOT / "src/main/resources/assets/dragonbrain/textures/entity/enderdragon"
rng = np.random.default_rng(0xAB155A)


def load_rgba(path):
    return np.array(Image.open(path).convert("RGBA")).astype(np.float32)


def load_master(name):
    """Luminance/alpha masters always come from the shipped 2.0 JAR when it is
    present, so re-running this script never compounds its own output."""
    jar = ROOT / "dragonbrain-2.0.0.jar"
    entry = f"assets/dragonbrain/textures/entity/enderdragon/{name}"
    if jar.exists():
        import io
        import zipfile

        with zipfile.ZipFile(jar) as z:
            if entry in z.namelist():
                return np.array(Image.open(io.BytesIO(z.read(entry))).convert("RGBA")).astype(np.float32)
    print(f"warning: 2.0 JAR master for {name} not found, using working-tree file")
    return load_rgba(TEX / name)


def save_rgba(arr, path):
    Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA").save(path)
    print(f"wrote {path.relative_to(ROOT)}")


def value_noise(size, cells):
    """Bilinearly upscaled random grid (one octave of value noise)."""
    grid = rng.random((cells, cells)).astype(np.float32)
    img = Image.fromarray((grid * 255).astype(np.uint8)).resize((size, size), Image.BILINEAR)
    return np.array(img).astype(np.float32) / 255.0


def ridged_field(size):
    """Multi-octave ridged noise -> thin bright vein network in [0, 1]."""
    acc = np.zeros((size, size), np.float32)
    amp, total = 1.0, 0.0
    for cells in (8, 16, 32, 64, 128):
        n = value_noise(size, cells)
        ridge = 1.0 - np.abs(2.0 * n - 1.0)
        acc += amp * ridge ** 4
        total += amp
        amp *= 0.72
    return acc / total


def blur(arr, radius):
    img = Image.fromarray(np.clip(arr * 255, 0, 255).astype(np.uint8)).filter(
        ImageFilter.GaussianBlur(radius)
    )
    return np.array(img).astype(np.float32) / 255.0


def ramp(lum, stops):
    """Map luminance [0..1] through a list of (pos, (r,g,b)) color stops."""
    out = np.zeros(lum.shape + (3,), np.float32)
    xs = np.array([s[0] for s in stops], np.float32)
    for ch in range(3):
        ys = np.array([s[1][ch] for s in stops], np.float32)
        out[..., ch] = np.interp(lum, xs, ys)
    return out


def build_body(master, *, palette, vein_color, vein_gain, vein_width, membrane_boost, label):
    rgb, alpha = master[..., :3], master[..., 3]
    lum = (0.299 * rgb[..., 0] + 0.587 * rgb[..., 1] + 0.114 * rgb[..., 2]) / 255.0
    mask = alpha > 0

    # crisp the existing scale drawing (unsharp detail) before re-coloring
    detail = lum - blur(lum, 1.2)
    shaped = np.clip(lum + detail * 0.85, 0, 1)
    # gentle S-curve: deeper shadows, clearer highlights, coherent everywhere
    shaped = shaped * shaped * (3 - 2 * shaped) * 0.35 + shaped * 0.65

    # flat low-variance areas (wing membranes) get extra sheen contrast
    local_var = np.abs(lum - blur(lum, 2.5))
    flat = np.clip(1.0 - local_var * 14.0, 0, 1)
    shaped = np.clip(shaped + flat * membrane_boost * (shaped - 0.32), 0, 1)

    out = ramp(shaped, palette)

    # energy veins: strongest inside the texture's own dark creases so they
    # read as cracks in the scales, continuous across body parts
    crease = np.clip(blur(lum, 1.6) - lum, 0, 1)
    crease = crease / (crease[mask].max() + 1e-6)
    veins = ridged_field(lum.shape[0])
    painted = veins[mask]
    lo, hi = np.percentile(painted, [max(5.0, 62.0 - vein_width * 100.0), 99.5])
    veins_n = np.clip((veins - lo) / max(hi - lo, 1e-6), 0, 1)
    vein_mask = np.clip((veins_n - 0.5) / 0.5, 0, 1) ** 1.7
    vein_mask = vein_mask * (0.5 + 0.5 * crease)
    core = np.clip(vein_mask * 1.6 - 0.35, 0, 1)
    vc = np.array(vein_color, np.float32)
    out += (vein_mask * vein_gain)[..., None] * vc[None, None, :]
    out += (core * vein_gain * 0.8)[..., None] * np.array((235, 215, 255), np.float32)[None, None, :]

    result = np.zeros_like(master)
    result[..., :3] = np.where(mask[..., None], out, 0)
    result[..., 3] = alpha
    print(f"{label}: vein coverage {100 * float((vein_mask[mask] > 0.25).mean()):.1f}% of painted UV")
    return result


def build_eyes(master_eyes, body_alpha):
    rgba = master_eyes.copy()
    mask = rgba[..., 3] > 0
    # near-white violet cores
    core = np.array((232, 205, 255), np.float32)
    edge = np.array((168, 85, 255), np.float32)
    lum = rgba[..., :3].mean(axis=2, keepdims=True) / 255.0
    rgba[..., :3] = np.where(mask[..., None], edge + (core - edge) * np.clip(lum * 1.4, 0, 1), 0)
    # 1 px halo, only where the body texture proves model surface exists
    halo = np.zeros_like(mask)
    halo[1:, :] |= mask[:-1, :]
    halo[:-1, :] |= mask[1:, :]
    halo[:, 1:] |= mask[:, :-1]
    halo[:, :-1] |= mask[:, 1:]
    halo &= body_alpha > 0
    halo &= ~mask
    rgba[..., :3] = np.where(halo[..., None], np.array((150, 80, 230), np.float32), rgba[..., :3])
    rgba[..., 3] = np.where(mask, 255, np.where(halo, 110, 0))
    return rgba


def main():
    master = load_master("dragon.png")
    master_eyes = load_master("dragon_eyes.png")
    assert master.shape == (256, 256, 4), master.shape

    abyssal = [
        (0.00, (8, 5, 16)),
        (0.22, (26, 14, 46)),
        (0.45, (48, 27, 84)),
        (0.68, (79, 48, 130)),
        (0.88, (124, 86, 186)),
        (1.00, (176, 140, 235)),
    ]
    save_rgba(
        build_body(master, palette=abyssal, vein_color=(150, 85, 255), vein_gain=0.62,
                   vein_width=0.16, membrane_boost=0.55, label="dragon.png"),
        TEX / "dragon.png",
    )

    final_palette = [
        (0.00, (14, 4, 12)),
        (0.22, (52, 13, 40)),
        (0.45, (96, 24, 66)),
        (0.68, (148, 42, 94)),
        (0.88, (205, 80, 128)),
        (1.00, (245, 140, 175)),
    ]
    save_rgba(
        build_body(master, palette=final_palette, vein_color=(255, 90, 150), vein_gain=0.70,
                   vein_width=0.18, membrane_boost=0.5, label="dragon_final.png"),
        TEX / "dragon_final.png",
    )

    cataclysm_palette = [
        (0.00, (3, 2, 8)),
        (0.22, (13, 7, 28)),
        (0.45, (40, 20, 74)),
        (0.68, (72, 38, 122)),
        (0.88, (102, 58, 168)),
        (1.00, (160, 110, 230)),
    ]
    save_rgba(
        build_body(master, palette=cataclysm_palette, vein_color=(176, 110, 255), vein_gain=0.95,
                   vein_width=0.24, membrane_boost=0.8, label="dragon_cataclysm.png"),
        TEX / "dragon_cataclysm.png",
    )

    save_rgba(build_eyes(master_eyes, master[..., 3]), TEX / "dragon_cataclysm_eyes.png")


if __name__ == "__main__":
    main()
