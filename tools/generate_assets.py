#!/usr/bin/env python3
"""
DragonBrain 3.0 — original asset generator.

Every texture in this project (except the preserved DragonBrain 2.0 .ogg
sound files, which are this project's own MIT assets) is painted from
scratch by this script.  No pixels from any third-party resource pack
(Beyonder, Fancy End, or any other) are used anywhere.

Run:  python3 tools/generate_assets.py
Outputs:
  src/main/resources/assets/dragonbrain/textures/...
  resourcepack/DragonBrain-AbyssalEnd-Vanilla/assets/minecraft/textures/...
  tools/contact_sheet.png  (visual QA only, not shipped in the jar)
"""
import math
import os
import random
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "src/main/resources/assets/dragonbrain/textures")
RP = os.path.join(ROOT, "resourcepack/DragonBrain-AbyssalEnd-Vanilla/assets/minecraft/textures")
PRESERVED = os.path.join(ROOT, "_preserved_2_0")

# ---------------------------------------------------------------- palettes
# Art direction: deep violet / dark indigo / charcoal / muted magenta /
# restrained cyan.  Each biome gets a distinct sub-palette.
def H(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)

VOID_GARDEN   = [H(c) for c in ("#120a24", "#1d1038", "#2b1850", "#3d2270", "#5a3d99", "#7d5fc0")]
CYAN_ACCENT   = [H(c) for c in ("#0b3b46", "#11697a", "#1fa8b8", "#5fe3f0", "#c8fbff")]
CHORUS        = [H(c) for c in ("#241331", "#36204a", "#4b2f63", "#68467f", "#8a64a3", "#b294c9")]
END_STONE     = [H(c) for c in ("#8f8a6b", "#a8a37f", "#c2bd98", "#dcd8b4", "#efebc9")]
END_STONE_LO  = [H(c) for c in ("#55523e", "#6f6c52", "#8f8a6b", "#a8a37f")]
PETRIFIED     = [H(c) for c in ("#1c1a26", "#2a2738", "#3a3650", "#4e4969", "#6a6389", "#8d86ab")]
FOSSIL_BONE   = [H(c) for c in ("#9a917c", "#bdb299", "#ddd5ba", "#f2ecd6")]
ASTRAL        = [H(c) for c in ("#232030", "#322e45", "#45415e", "#5d5878", "#7c7699")]
MAGENTA       = [H(c) for c in ("#2c0c1e", "#471631", "#6e1e42", "#9c2a5c", "#d64d85", "#ff9dbd")]
ABYSSAL_METAL = [H(c) for c in ("#14101f", "#211a33", "#322747", "#46365f", "#5f4a80", "#8470ad")]
DRAGON_BASE   = [H(c) for c in ("#0b0714", "#170f2b", "#241744", "#32225c", "#443075", "#5b4391", "#775cb0")]
DRAGON_BELLY  = [H(c) for c in ("#6d5f8f", "#8d7eb3", "#aa9cc9", "#c9bfe0")]

def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))

def ramp(pal, t):
    t = max(0.0, min(0.999, t))
    x = t * (len(pal) - 1)
    i = int(x)
    return lerp(pal[i], pal[i + 1], x - i)

# ---------------------------------------------------------------- noise
class Noise:
    """Clustered value-noise: coarse random grid, bilinearly sampled.
    Produces controlled Minecraft-like mottling instead of TV static."""
    def __init__(self, seed, cells=4):
        r = random.Random(seed)
        self.cells = cells
        self.g = [[r.random() for _ in range(cells + 1)] for _ in range(cells + 1)]
        # wrap edges so 16x16 tiles are seamless
        for i in range(cells + 1):
            self.g[i][cells] = self.g[i][0]
            self.g[cells][i] = self.g[0][i]
    def at(self, x, y, w=16):
        fx, fy = x / w * self.cells, y / w * self.cells
        ix, iy = int(fx) % self.cells, int(fy) % self.cells
        tx, ty = fx - int(fx), fy - int(fy)
        g = self.g
        top = g[iy][ix] * (1 - tx) + g[iy][ix + 1] * tx
        bot = g[iy + 1][ix] * (1 - tx) + g[iy + 1][ix + 1] * tx
        return top * (1 - ty) + bot * ty

def speckle(rng, img, colour, count, w=16, h=16, alpha=None):
    px = img.load()
    for _ in range(count):
        x, y = rng.randrange(w), rng.randrange(h)
        if alpha is None or alpha(x, y):
            px[x, y] = colour

def canvas(w=16, h=16):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))

# ---------------------------------------------------------------- materials
def stone(seed, pal, lo=0.12, hi=0.82, cells=4, pit_seed=None):
    """Mottled stone with clustered shading + sparse pits/highlights."""
    n, n2 = Noise(seed, cells), Noise(seed + 7, 8)
    r = random.Random(pit_seed if pit_seed is not None else seed + 99)
    img = canvas(); px = img.load()
    for y in range(16):
        for x in range(16):
            v = 0.65 * n.at(x, y) + 0.35 * n2.at(x, y)
            px[x, y] = ramp(pal, lo + (hi - lo) * v)
    speckle(r, img, ramp(pal, lo * 0.6), 7)          # pits
    speckle(r, img, ramp(pal, min(1.0, hi + 0.1)), 4)  # worn highlights
    return img

def bricks(seed, pal, mortar, rows=4, crack=False, chisel=None):
    """Masonry courses: 4 rows, offset joints, per-brick tone, top highlight."""
    base = stone(seed, pal, 0.18, 0.8)
    px = base.load(); r = random.Random(seed + 3)
    rh = 16 // rows
    tones = {}
    for row in range(rows):
        off = 0 if row % 2 == 0 else 4
        for bx in range(-1, 3):
            tones[(row, bx)] = (r.random() - 0.5) * 0.22
    for y in range(16):
        row = y // rh
        for x in range(16):
            off = 0 if row % 2 == 0 else 4
            bx = ((x + off) // 8) - (1 if off else 0)
            local_x = (x + off) % 8
            if y % rh == 0 or local_x == 0:
                px[x, y] = mortar
            elif y % rh == 1:
                px[x, y] = lerp(px[x, y], (255, 255, 255, 255), 0.10)
            else:
                t = tones.get((row, bx), 0)
                px[x, y] = lerp(px[x, y], (255, 255, 255, 255) if t > 0 else (0, 0, 0, 255), abs(t) * 0.5)
    if crack:
        cx, cy = r.randrange(3, 13), r.randrange(2, 6)
        for _ in range(11):
            if 0 <= cx < 16 and 0 <= cy < 16:
                px[cx, cy] = ramp(pal, 0.03)
            cx += r.choice((-1, 0, 1)); cy += 1
    if chisel:  # 8x8 glyph centred, dark groove + lower-right highlight
        for gy, line in enumerate(chisel):
            for gx, ch in enumerate(line):
                if ch == "#":
                    x, y = 4 + gx, 4 + gy
                    px[x, y] = ramp(pal, 0.06)
                    if x + 1 < 16 and y + 1 < 16:
                        px[x + 1, y + 1] = lerp(px[x + 1, y + 1], (255, 255, 255, 255), 0.16)
    return base

def pillar_side(seed, pal):
    """Vertical fluted pillar: shaded columns + capital bands top/bottom."""
    img = stone(seed, pal, 0.2, 0.78); px = img.load()
    for x in range(16):
        shade = (math.sin((x % 4) / 3 * math.pi)) * 0.5 + 0.5
        for y in range(16):
            px[x, y] = lerp(ramp(pal, 0.10), px[x, y], 0.55 + 0.45 * shade)
            if x % 4 == 0:
                px[x, y] = lerp(px[x, y], ramp(pal, 0.04), 0.55)
    for y in (0, 1, 14, 15):
        for x in range(16):
            px[x, y] = ramp(pal, 0.30 if y in (1, 14) else 0.55)
    return img

def pillar_top(seed, pal, rune=None):
    img = stone(seed, pal, 0.25, 0.75); px = img.load()
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            px[x, y] = ramp(pal, 0.55)
        for (x, y) in ((i, 1), (i, 14), (1, i), (14, i)):
            px[x, y] = ramp(pal, 0.12)
    if rune:
        for gy, line in enumerate(rune):
            for gx, ch in enumerate(line):
                if ch == "#":
                    px[4 + gx, 4 + gy] = CYAN_ACCENT[3]
    return img

def layered(seed, pal, bands=5):
    """Sedimentary strata for petrified stone."""
    img = canvas(); px = img.load(); n = Noise(seed, 6)
    for y in range(16):
        band = (y * bands) // 16
        base = 0.15 + 0.6 * ((band % 3) / 2)
        for x in range(16):
            v = base + (n.at(x, y) - 0.5) * 0.22
            if y % (16 // bands or 1) == 0 and y:
                v *= 0.45
            px[x, y] = ramp(pal, max(0.02, min(0.95, v)))
    return img

def organic_cells(seed, pal, glow_pts=0, glow_pal=None):
    """Chorus-like organic surface: squarish cells with dark seams."""
    r = random.Random(seed)
    cell_tone = [[r.random() for _ in range(4)] for _ in range(4)]
    img = canvas(); px = img.load(); n = Noise(seed + 1, 8)
    for y in range(16):
        for x in range(16):
            cx, cy = x // 4, y // 4
            edge = x % 4 == 0 or y % 4 == 0
            v = 0.25 + 0.5 * cell_tone[cy][cx] + (n.at(x, y) - 0.5) * 0.18
            if edge:
                v *= 0.5
            if x % 4 == 1 and y % 4 == 1:
                v = min(0.95, v + 0.18)
            px[x, y] = ramp(pal, v)
    if glow_pal:
        speckle(r, img, glow_pal[-1], glow_pts)
        speckle(r, img, glow_pal[-2], glow_pts // 2)
    return img

def crystal(seed, pal, frames=1):
    """Faceted crystal block; diagonal facet bands + bright core."""
    imgs = []
    for f in range(frames):
        img = canvas(); px = img.load(); r = random.Random(seed)
        for y in range(16):
            for x in range(16):
                d = abs(x - y) // 3 + abs(15 - x - y) // 5
                v = 0.25 + 0.14 * (d % 3) + 0.05 * math.sin(f / max(frames, 1) * 6.28 + (x + y) * 0.4)
                px[x, y] = ramp(pal, max(0.05, min(0.92, v)))
        d = ImageDraw.Draw(img)
        d.line([(2, 13), (7, 3), (10, 6), (13, 2)], fill=pal[-1], width=1)
        d.line([(4, 14), (9, 7)], fill=pal[-2], width=1)
        speckle(r, img, pal[-1], 6)
        imgs.append(img)
    return imgs[0] if frames == 1 else imgs

def glass_tex(seed, frame_col, tint):
    """Voidglass: dark frame, faint diagonal gleam, mostly transparent."""
    img = canvas(); px = img.load()
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            px[x, y] = frame_col
    for (x, y) in ((3, 11), (4, 10), (5, 9), (6, 8), (8, 12), (9, 11), (10, 10)):
        px[x, y] = tint
    px[7, 7] = tint; px[11, 4] = tint
    return img

def metal(seed, pal):
    """Abyssal alloy: brushed panels, corner rivets, energy seam."""
    img = stone(seed, pal, 0.28, 0.62, cells=8); px = img.load()
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            px[x, y] = ramp(pal, 0.12)
        for (x, y) in ((i, 1), (i, 14), (1, i), (14, i)):
            px[x, y] = ramp(pal, 0.72)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px[x, y] = ramp(pal, 0.95)
    for i in range(5, 11):
        px[i, 7] = CYAN_ACCENT[2]; px[i, 8] = CYAN_ACCENT[1]
    px[7, 7] = CYAN_ACCENT[4]; px[8, 7] = CYAN_ACCENT[4]
    return img

def canopy(seed, pal, glow_pal):
    """Leaf-like canopy with cutout holes (alpha) and glowing spores."""
    img = organic_cells(seed, pal); px = img.load(); r = random.Random(seed + 5)
    for _ in range(26):
        px[r.randrange(16), r.randrange(16)] = (0, 0, 0, 0)
    speckle(r, img, glow_pal[-1], 8, alpha=lambda x, y: px[x, y][3] > 0)
    speckle(r, img, ramp(pal, 0.9), 10, alpha=lambda x, y: px[x, y][3] > 0)
    return img

def cross_sprite(draw_fn, w=16, h=16):
    img = canvas(w, h)
    draw_fn(ImageDraw.Draw(img), img.load())
    return img

# ---------------------------------------------------------------- block set
def save(img, *path):
    p = os.path.join(TEX, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)
    return p

def save_rp(img, *path):
    p = os.path.join(RP, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)
    return p

GLYPH_EYE = [
    "..####..",
    ".#....#.",
    "#..##..#",
    "#..##..#",
    "#......#",
    ".#....#.",
    "..####..",
    "...##...",
]
GLYPH_RUNE = [
    "...##...",
    "..#..#..",
    "...##...",
    ".##..##.",
    "...##...",
    "..#..#..",
    "...##...",
    "........",
]

def make_blocks():
    # --- VOID GARDENS -----------------------------------------------------
    moss = stone(101, VOID_GARDEN, 0.15, 0.7)
    r = random.Random(7); px = moss.load()
    speckle(r, moss, CYAN_ACCENT[3], 14); speckle(r, moss, CYAN_ACCENT[4], 5)
    speckle(r, moss, MAGENTA[4], 5)
    save(moss, "block/lumen_moss_block.png")

    muted_magenta = [lerp(c, CHORUS[i % len(CHORUS)], 0.45) for i, c in enumerate(MAGENTA)]
    petal = organic_cells(102, muted_magenta, glow_pts=6, glow_pal=MAGENTA)
    save(petal, "block/gloam_petal_block.png")

    # void lily: animated 16x64, 4 frames, stem sways, bloom pulses
    frames = []
    for f in range(4):
        sway = (0, 1, 0, -1)[f]
        def draw_lily(d, px, sway=sway, f=f):
            stem = [H("#2b1850"), H("#3d2270")]
            for y in range(9, 16):
                px[8 + (sway if y < 11 else 0), y] = stem[y % 2]
            px[6, 12] = stem[1]; px[10, 13] = stem[1]
            cx = 8 + sway
            bloom = [MAGENTA[3], MAGENTA[4], MAGENTA[5]]
            for (dx, dy, c) in [(-2, 5, 0), (2, 5, 0), (-1, 4, 1), (0, 3, 1), (1, 4, 1),
                                (-2, 6, 0), (2, 6, 0), (0, 6, 0)]:
                if 0 <= cx + dx < 16:
                    px[cx + dx, dy + 2] = bloom[c]
            core = CYAN_ACCENT[4] if f % 2 == 0 else CYAN_ACCENT[3]
            px[cx, 7] = core; px[cx, 6] = CYAN_ACCENT[2]
            px[cx - 3, 3] = CYAN_ACCENT[3]; px[cx + 3, 2] = CYAN_ACCENT[2]
        frames.append(cross_sprite(draw_lily))
    strip = Image.new("RGBA", (16, 64), (0, 0, 0, 0))
    for i, fr in enumerate(frames):
        strip.paste(fr, (0, i * 16))
    save(strip, "block/void_lily.png")
    with open(os.path.join(TEX, "block/void_lily.png.mcmeta"), "w") as fh:
        fh.write('{"animation": {"frametime": 8, "interpolate": false}}\n')

    # --- CHORUS WILDS -------------------------------------------------------
    bark_side = organic_cells(201, CHORUS); px = bark_side.load()
    for x in (3, 7, 11, 15):  # vertical grooves
        for y in range(16):
            px[x, y] = lerp(px[x, y], CHORUS[0], 0.6)
    save(bark_side, "block/chorus_bark_log.png")
    top = stone(202, CHORUS, 0.3, 0.7); px = top.load()
    for ring, col in ((7, CHORUS[1]), (5, CHORUS[4]), (3, CHORUS[1])):
        for x in range(8 - ring, 8 + ring):
            px[x, 8 - ring] = col; px[x, 7 + ring] = col
            px[8 - ring, x] = col; px[7 + ring, x] = col
    px[7, 7] = CHORUS[5]; px[8, 8] = CHORUS[5]
    save(top, "block/chorus_bark_log_top.png")
    save(canopy(203, CHORUS, CYAN_ACCENT), "block/chorus_canopy.png")
    save(organic_cells(204, CHORUS, glow_pts=10, glow_pal=CYAN_ACCENT),
         "block/wild_chorus_bulb.png")

    def draw_roots(d, px):
        cols = [CHORUS[1], CHORUS[2], CHORUS[3]]
        for (pts, c) in [([(8, 15), (7, 11), (8, 7), (7, 4)], 1),
                         ([(8, 12), (5, 9), (4, 6)], 2),
                         ([(8, 13), (11, 10), (12, 7)], 0),
                         ([(8, 15), (3, 13)], 0), ([(8, 15), (13, 14)], 1)]:
            d.line(pts, fill=cols[c], width=1)
        px[7, 3] = CYAN_ACCENT[3]; px[4, 5] = CHORUS[5]; px[12, 6] = CHORUS[4]
    save(cross_sprite(draw_roots), "block/chorus_roots.png")

    # --- SHATTERED EXPANSE --------------------------------------------------
    save(bricks(301, END_STONE_LO + END_STONE, END_STONE_LO[0], crack=True),
         "block/fractured_end_stone.png")
    save(pillar_side(302, END_STONE_LO + END_STONE), "block/shattered_pillar.png")
    save(pillar_top(303, END_STONE_LO + END_STONE), "block/shattered_pillar_top.png")
    rubble = stone(304, END_STONE_LO + END_STONE, 0.1, 0.85, cells=8)
    r = random.Random(31)
    speckle(r, rubble, END_STONE_LO[0], 22); speckle(r, rubble, END_STONE[-1], 10)
    save(rubble, "block/endstone_rubble.png")
    save(bricks(305, END_STONE_LO + END_STONE, END_STONE_LO[1], crack=True),
         "block/cracked_endstone_bricks.png")

    # --- PETRIFIED ABYSS ----------------------------------------------------
    save(layered(401, PETRIFIED), "block/petrified_stone.png")
    fossil = layered(402, PETRIFIED); px = fossil.load()
    d = ImageDraw.Draw(fossil)
    # rib-cage fossil arc + spine, bone palette with dark outline pass
    for t in range(0, 11):
        ang = math.pi * (0.15 + 0.7 * t / 10)
        x, y = int(8 + 5.5 * math.cos(ang)), int(9 - 4.5 * math.sin(ang))
        px[x, y] = FOSSIL_BONE[2]
        if y + 1 < 16: px[x, y + 1] = FOSSIL_BONE[1]
    for x in range(3, 14, 2):
        px[x, 10] = FOSSIL_BONE[3]; px[x, 11] = FOSSIL_BONE[0]
    speckle(random.Random(4), fossil, FOSSIL_BONE[1], 5)
    save(fossil, "block/fossil_block.png")
    save(bricks(403, PETRIFIED, PETRIFIED[0], chisel=GLYPH_EYE),
         "block/engraved_abyssal_bricks.png")
    weathered = bricks(404, PETRIFIED, PETRIFIED[0])
    n = Noise(405, 5); px = weathered.load()
    for y in range(16):
        for x in range(16):
            if n.at(x, y) > 0.72:  # violet weathering patches
                px[x, y] = lerp(px[x, y], VOID_GARDEN[3], 0.55)
    save(weathered, "block/weathered_masonry.png")

    # --- ASTRAL BARRENS -----------------------------------------------------
    dust = stone(501, ASTRAL, 0.22, 0.78, cells=6)
    r = random.Random(51)
    speckle(r, dust, CYAN_ACCENT[4], 4); speckle(r, dust, H("#efeaff"), 5)
    save(dust, "block/astral_dust_block.png")
    shale = layered(502, ASTRAL, bands=8); px = shale.load()
    speckle(random.Random(52), shale, CYAN_ACCENT[2], 9)
    save(shale, "block/starshale.png")
    frames = crystal(503, CYAN_ACCENT, frames=4)
    strip = Image.new("RGBA", (16, 64), (0, 0, 0, 0))
    for i, fr in enumerate(frames):
        strip.paste(fr, (0, i * 16))
    save(strip, "block/astral_crystal_block.png")
    with open(os.path.join(TEX, "block/astral_crystal_block.png.mcmeta"), "w") as fh:
        fh.write('{"animation": {"frametime": 6, "interpolate": false}}\n')

    # --- FUNCTIONAL / SPECIAL ----------------------------------------------
    save(glass_tex(601, ABYSSAL_METAL[2], H("#9fdcff")), "block/voidglass.png")
    save(metal(602, ABYSSAL_METAL), "block/abyssal_alloy_block.png")
    # eclipse lantern body (used by custom lantern model on all sides)
    lant = metal(603, ABYSSAL_METAL); px = lant.load()
    for y in range(5, 11):
        for x in range(5, 11):
            m = max(abs(x - 7.5), abs(y - 7.5))
            px[x, y] = CYAN_ACCENT[4] if m < 1.5 else (CYAN_ACCENT[3] if m < 2.5 else MAGENTA[4])
    save(lant, "block/eclipse_lantern.png")
    lant_top = metal(604, ABYSSAL_METAL); px = lant_top.load()
    for x in range(6, 10):
        for y in range(6, 10):
            px[x, y] = ramp(ABYSSAL_METAL, 0.15)
    save(lant_top, "block/eclipse_lantern_top.png")
    # abyssal trophy: pedestal stone + trophy uses dragon-scale material
    save(bricks(605, ABYSSAL_METAL, ABYSSAL_METAL[0], chisel=GLYPH_RUNE),
         "block/abyssal_trophy_base.png")
    scale = stone(606, DRAGON_BASE, 0.2, 0.8); px = scale.load()
    for y in range(16):
        for x in range(16):
            if (x + (2 if (y // 3) % 2 else 0)) % 4 == 0 or y % 3 == 0:
                px[x, y] = lerp(px[x, y], DRAGON_BASE[0], 0.45)
    speckle(random.Random(61), scale, MAGENTA[5], 4)
    save(scale, "block/abyssal_trophy_scale.png")

# ---------------------------------------------------------------- items
def item_sprite(name, draw_fn):
    save(cross_sprite(draw_fn), f"item/{name}.png")

def make_items():
    def shard(d, px):
        d.polygon([(8, 1), (11, 6), (9, 14), (6, 10), (5, 5)], fill=CYAN_ACCENT[2])
        d.polygon([(8, 1), (11, 6), (8, 8)], fill=CYAN_ACCENT[3])
        d.polygon([(8, 8), (9, 14), (6, 10)], fill=CYAN_ACCENT[1])
        px[8, 4] = CYAN_ACCENT[4]; px[7, 6] = CYAN_ACCENT[4]
    item_sprite("resonance_shard", shard)

    def ingot(d, px):
        d.polygon([(3, 9), (5, 6), (11, 6), (13, 9), (11, 11), (5, 11)], fill=ABYSSAL_METAL[3])
        d.polygon([(5, 6), (11, 6), (12, 8), (4, 8)], fill=ABYSSAL_METAL[5])
        d.line([(4, 8), (12, 8)], fill=CYAN_ACCENT[2])
        px[6, 7] = CYAN_ACCENT[4]
    item_sprite("abyssal_alloy_ingot", ingot)

    def glass_shard(d, px):
        d.polygon([(4, 12), (6, 4), (10, 5), (12, 11), (8, 13)], fill=H("#3d5a78"))
        d.polygon([(6, 4), (10, 5), (8, 9)], fill=H("#9fdcff"))
        px[7, 6] = (255, 255, 255, 255)
    item_sprite("voidglass_shard", glass_shard)

    def compass(d, px):
        d.ellipse([2, 2, 13, 13], fill=ABYSSAL_METAL[2], outline=ABYSSAL_METAL[5])
        d.ellipse([4, 4, 11, 11], fill=H("#0d0a1a"))
        d.polygon([(8, 3), (9, 8), (7, 8)], fill=MAGENTA[4])
        d.polygon([(8, 12), (9, 8), (7, 8)], fill=CYAN_ACCENT[3])
        px[8, 8] = (255, 255, 255, 255)
        for (x, y) in ((8, 2), (8, 13), (2, 8), (13, 8)):
            px[x, y] = CYAN_ACCENT[3]
    item_sprite("rift_compass", compass)

    def scale_item(d, px):
        d.polygon([(8, 2), (12, 6), (11, 11), (8, 14), (5, 11), (4, 6)], fill=DRAGON_BASE[3])
        d.polygon([(8, 2), (12, 6), (8, 8)], fill=DRAGON_BASE[5])
        d.line([(8, 3), (8, 13)], fill=MAGENTA[4])
        px[7, 5] = CYAN_ACCENT[4]
    item_sprite("cataclysm_scale", scale_item)

    def essence(d, px):
        d.ellipse([5, 4, 10, 9], fill=CYAN_ACCENT[3])
        d.polygon([(6, 8), (10, 8), (8, 13)], fill=CYAN_ACCENT[2])
        px[7, 6] = (255, 255, 255, 255); px[8, 6] = CYAN_ACCENT[4]
        px[4, 3] = CYAN_ACCENT[2]; px[11, 5] = CYAN_ACCENT[2]; px[12, 10] = CYAN_ACCENT[3]
    item_sprite("wisp_essence", essence)

    def fang(d, px):
        d.polygon([(5, 3), (9, 3), (10, 8), (8, 13), (7, 9), (5, 7)], fill=FOSSIL_BONE[2])
        d.polygon([(5, 3), (9, 3), (8, 6), (6, 6)], fill=MAGENTA[3])
        px[8, 10] = FOSSIL_BONE[3]
    item_sprite("rift_fang", fang)

    def core(d, px):
        d.rectangle([4, 4, 11, 11], fill=ABYSSAL_METAL[2], outline=ABYSSAL_METAL[5])
        d.rectangle([6, 6, 9, 9], fill=CYAN_ACCENT[3])
        px[7, 7] = (255, 255, 255, 255)
        for (x, y) in ((4, 4), (11, 4), (4, 11), (11, 11)):
            px[x, y] = MAGENTA[4]
    item_sprite("sentinel_core", core)

    def egg(base, spot):
        def fn(d, px):
            d.ellipse([4, 2, 11, 13], fill=base)
            r = random.Random(hash(base) & 0xFFFF)
            for _ in range(9):
                x, y = r.randrange(5, 11), r.randrange(3, 12)
                if ((x - 7.5) / 3.5) ** 2 + ((y - 7.5) / 5.5) ** 2 < 0.85:
                    px[x, y] = spot
        return fn
    item_sprite("end_wisp_spawn_egg", egg(CYAN_ACCENT[1], CYAN_ACCENT[4]))
    item_sprite("rift_stalker_spawn_egg", egg(H("#1c1030"), MAGENTA[4]))
    item_sprite("ancient_sentinel_spawn_egg", egg(PETRIFIED[2], CYAN_ACCENT[3]))

# ---------------------------------------------------------------- entities
def region(px, x0, y0, w, h, fn):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            px[x, y] = fn(x - x0, y - y0)

def make_entities():
    # END WISP 64x64. UV layout MUST match EndWispModel.java:
    #   core cube 8x8x8 @ (0,0) | crown spikes 2x4x2 @ (32,0) x4 offsets
    #   tail wisp 6x8x6 @ (0,20) | side fins 1x4x6 @ (40,10)
    img = canvas(64, 64); px = img.load()
    n = Noise(701, 8)
    region(px, 0, 0, 32, 16, lambda x, y: ramp(CYAN_ACCENT, 0.35 + 0.5 * n.at(x, y, 32)))
    # face: front of core cube is at u=8..16 (south face), v=8..16
    for y in range(8, 16):
        for x in range(8, 16):
            px[x, y] = lerp(px[x, y], (255, 255, 255, 255), 0.25)
    px[10, 11] = H("#062a33"); px[13, 11] = H("#062a33")     # eyes
    px[10, 12] = H("#062a33"); px[13, 12] = H("#062a33")
    px[11, 14] = H("#0b4a57"); px[12, 14] = H("#0b4a57")     # small mouth
    region(px, 32, 0, 8, 8, lambda x, y: ramp(MAGENTA, 0.45 + 0.05 * ((x + y) % 3)))
    region(px, 0, 20, 24, 14, lambda x, y: lerp(ramp(CYAN_ACCENT, 0.3 + 0.4 * n.at(x, y, 24)),
                                                 VOID_GARDEN[3], y / 20))
    region(px, 40, 10, 14, 10, lambda x, y: ramp(CYAN_ACCENT, 0.5 + 0.04 * ((x * y) % 5)))
    save(img, "entity/end_wisp.png")
    # emissive eyes overlay (same UV, only eye/core pixels opaque)
    eyes = canvas(64, 64); epx = eyes.load()
    for (x, y) in ((10, 11), (13, 11), (10, 12), (13, 12)):
        epx[x, y] = (230, 255, 255, 255)
    epx[11, 10] = CYAN_ACCENT[3]; epx[12, 10] = CYAN_ACCENT[3]
    save(eyes, "entity/end_wisp_eyes.png")

    # RIFT STALKER 64x64. UV MUST match RiftStalkerModel.java:
    #   body 12x10x18 @ (0,0)=60x28 | head 8x8x10 @ (0,32)=36x18
    #   legs 4x12x4 @ (40,32)=16x16 | tail 3x3x12 @ (0,52)=30x6
    #   dorsal spikes 2x6x2 @ (44,0)
    for variant, eye_col, scar in (("rift_stalker", MAGENTA[4], False),
                                   ("rift_stalker_windup", MAGENTA[5], True)):
        img = canvas(64, 64); px = img.load()
        hide = [H(c) for c in ("#120b20", "#1d1233", "#2a1a47", "#39245c", "#4c3178")]
        n = Noise(702, 10)
        region(px, 0, 0, 60, 28, lambda x, y: ramp(hide, 0.2 + 0.55 * n.at(x, y, 60)))
        region(px, 0, 32, 36, 18, lambda x, y: ramp(hide, 0.25 + 0.5 * n.at(x + 3, y, 36)))
        region(px, 40, 32, 16, 16, lambda x, y: ramp(hide, 0.12 + 0.35 * n.at(x, y + 5, 16)))
        region(px, 0, 52, 30, 6, lambda x, y: ramp(hide, 0.2 + 0.4 * n.at(x, y + 9, 30)))
        region(px, 44, 0, 8, 8, lambda x, y: ramp(MAGENTA, 0.35 + 0.05 * (y % 3)))
        # spine stripe on the body TOP face (u=18..30, v=0..18 for 12x10x18)
        for y in range(0, 18):
            px[23, y] = MAGENTA[2]; px[24, y] = MAGENTA[1]
        # head front face: u=10..18? head cube 8w,8h,10d: south face u=10(w?) -> u=10..18,v=42..50
        for (x, y) in ((12, 44), (15, 44)):
            px[x, y] = eye_col; px[x, y + 1] = eye_col
        for x in range(11, 17):  # jaw teeth line
            if x % 2 == 0:
                px[x, 48] = FOSSIL_BONE[3]
        if scar:  # wind-up: glowing gill slits on neck (both side faces)
            for x0 in (0, 26):
                for i in range(3):
                    px[x0 + 2 + i * 2, 44] = MAGENTA[5]
                    px[x0 + 2 + i * 2, 45] = MAGENTA[4]
        save(img, f"entity/{variant}.png")

    # ANCIENT SENTINEL 128x128. UV MUST match AncientSentinelModel.java:
    #   torso 16x20x10 @ (0,0)=52x30 | head 10x10x10 @ (0,36)=40x20
    #   arms 6x22x6 @ (56,0)=24x28 | legs 7x16x7 @ (0,60)=28x23
    #   crest 2x8x12 @ (88,0)
    img = canvas(128, 128); px = img.load()
    n = Noise(703, 16)
    region(px, 0, 0, 52, 30, lambda x, y: ramp(ABYSSAL_METAL, 0.2 + 0.5 * n.at(x, y, 52)))
    region(px, 0, 36, 40, 20, lambda x, y: ramp(ABYSSAL_METAL, 0.25 + 0.45 * n.at(x + 4, y, 40)))
    region(px, 56, 0, 24, 28, lambda x, y: ramp(ABYSSAL_METAL, 0.15 + 0.45 * n.at(x, y + 2, 24)))
    region(px, 0, 60, 28, 23, lambda x, y: ramp(ABYSSAL_METAL, 0.12 + 0.4 * n.at(x + 7, y, 28)))
    region(px, 88, 0, 28, 20, lambda x, y: ramp(PETRIFIED, 0.3 + 0.4 * n.at(x, y, 28)))
    # weathering: violet patina blotches + stone chips
    r = random.Random(73)
    for _ in range(240):
        x, y = r.randrange(128), r.randrange(90)
        if px[x, y][3]:
            px[x, y] = lerp(px[x, y], VOID_GARDEN[2], 0.4)
    # chest core on torso south face (u=10..26, v=10..30)
    for y in range(17, 23):
        for x in range(15, 21):
            m = max(abs(x - 17.5), abs(y - 19.5))
            px[x, y] = CYAN_ACCENT[4] if m < 1.2 else (CYAN_ACCENT[2] if m < 2.2 else ABYSSAL_METAL[0])
    # engraved bands on torso + arms
    for x in range(10, 26):
        px[x, 13] = CYAN_ACCENT[1]
    # head south face u=10..20, v=46..56: single visor eye + brow plate
    for x in range(12, 18):
        px[x, 49] = CYAN_ACCENT[3]; px[x, 50] = CYAN_ACCENT[4]
    for x in range(11, 19):
        px[x, 47] = ABYSSAL_METAL[0]
    save(img, "entity/ancient_sentinel.png")
    eyes = canvas(128, 128); epx = eyes.load()
    for x in range(12, 18):
        epx[x, 49] = CYAN_ACCENT[3]; epx[x, 50] = (235, 255, 255, 255)
    for y in range(17, 23):
        for x in range(15, 21):
            m = max(abs(x - 17.5), abs(y - 19.5))
            if m < 2.2:
                epx[x, y] = CYAN_ACCENT[4] if m < 1.2 else CYAN_ACCENT[2]
    save(eyes, "entity/ancient_sentinel_eyes.png")

# ---------------------------------------------------------------- dragon
def dragon_variant(kind):
    """Repaint the preserved 2.0 dragon UV mask with real material art.
    Alpha (== the vanilla dragon model UV islands) is preserved exactly so
    the texture stays compatible with the vanilla + 2.0 renderer."""
    src = Image.open(os.path.join(PRESERVED, "textures/entity/enderdragon/dragon.png")).convert("RGBA")
    eyes_src = Image.open(os.path.join(PRESERVED,
                          "textures/entity/enderdragon/dragon_eyes.png")).convert("RGBA")
    W, Hh = src.size
    sp = src.load(); ep = eyes_src.load()
    out = canvas(W, Hh); px = out.load()
    glowing = canvas(W, Hh); gx = glowing.load()
    n = Noise(801, 32); n2 = Noise(802, 64)
    if kind == "normal":
        body, belly, wing = DRAGON_BASE, DRAGON_BELLY, [H(c) for c in
            ("#150c28", "#221440", "#30205c", "#402c78", "#513b96")]
        crack_col, eye_hi, eye_lo = None, (240, 255, 255, 255), CYAN_ACCENT[3]
    elif kind == "rage":
        body = [lerp(c, MAGENTA[2], 0.45) for c in DRAGON_BASE]
        belly = [lerp(c, MAGENTA[4], 0.3) for c in DRAGON_BELLY]
        wing = [H(c) for c in ("#1d0a1e", "#33102e", "#4a1740", "#631f52", "#7d2868")]
        crack_col, eye_hi, eye_lo = MAGENTA[5], (255, 230, 245, 255), MAGENTA[4]
    else:  # final — Abyssal Cataclysm
        body = [H(c) for c in ("#05030d", "#0d0820", "#160d33", "#201347", "#2c1a5e", "#3a2278", "#4b2e96")]
        belly = [H(c) for c in ("#3a2a5e", "#4e3a7d", "#64499c", "#7d5fbc")]
        wing = [H(c) for c in ("#0d0416", "#1c0828", "#2e0e3d", "#421555", "#571d6e")]
        crack_col, eye_hi, eye_lo = CYAN_ACCENT[4], (255, 255, 255, 255), MAGENTA[5]

    # island bboxes by coarse region: wings live in the two large
    # bottom-left islands of the vanilla dragon sheet.
    def is_wing(x, y):
        return (y > 95 and x < 105) or (y > 150 and x < 60)

    for y in range(Hh):
        for x in range(W):
            if sp[x, y][3] == 0:
                continue
            v = 0.6 * n.at(x, y, W) + 0.4 * n2.at(x, y, W)
            if is_wing(x, y):
                # membrane: radial ribs + soft gradient, darker at edges
                rib = 0.18 if (x + y) % 11 == 0 else 0.0
                t = max(0.05, min(0.9, 0.25 + 0.5 * v + rib))
                px[x, y] = ramp(wing, t)
            else:
                # scalloped scale rows: offset 4x3 arcs, lit top edge,
                # dark groove at row base
                sx = (x + (2 if (y // 3) % 2 else 0)) % 4
                sy = y % 3
                t = 0.22 + 0.5 * v
                if sy == 0:
                    t += 0.16          # scale lip highlight
                elif sy == 2:
                    t -= 0.14          # groove shadow
                if sx == 0:
                    t -= 0.06
                px[x, y] = ramp(body, max(0.03, min(0.95, t)))
    # pale belly plates: bottom rows of the big torso island (top band of sheet)
    for y in range(38, 62):
        for x in range(0, 130):
            if sp[x, y][3]:
                v = 0.5 + 0.4 * n.at(x, y, W)
                c = ramp(belly, v)
                if y % 4 == 3:
                    c = lerp(c, body[0], 0.5)   # plate separators
                px[x, y] = c
    # dorsal ridge highlights: topmost spine band
    for y in range(0, 10):
        for x in range(0, 130):
            if sp[x, y][3]:
                px[x, y] = lerp(px[x, y], MAGENTA[3] if kind != "normal" else DRAGON_BASE[6], 0.35)
    # eyes: keep the exact 2.0 eye mask, repaint with hot core gradient
    eye_pts = [(x, y) for y in range(Hh) for x in range(W) if ep[x, y][3]]
    if eye_pts:
        xs = [p[0] for p in eye_pts]; ys = [p[1] for p in eye_pts]
        # two eye clusters: split by x median gap
        for (x, y) in eye_pts:
            gx[x, y] = eye_lo
        # bright pupil cores = cluster centroids
        eye_pts.sort()
        # simple split: use connected-ish halves by x
        mid = (min(xs) + max(xs)) // 2
        for group in ([p for p in eye_pts if p[0] <= mid], [p for p in eye_pts if p[0] > mid]):
            if group:
                cx = sum(p[0] for p in group) // len(group)
                cy = sum(p[1] for p in group) // len(group)
                for dx in (-1, 0, 1):
                    for dy in (-1, 0, 1):
                        if 0 <= cx + dx < W and 0 <= cy + dy < Hh and ep[cx + dx, cy + dy][3]:
                            gx[cx + dx, cy + dy] = eye_hi
    # emissive crack network for rage/final (drawn on BOTH layers:
    # dark char line on body, bright line on the emissive eyes layer)
    if crack_col:
        r = random.Random(900 if kind == "rage" else 901)
        walks = 9 if kind == "rage" else 16
        for _ in range(walks):
            # start from an opaque body pixel
            for _try in range(60):
                x, y = r.randrange(W), r.randrange(0, 150)
                if sp[x, y][3]:
                    break
            for _step in range(r.randrange(10, 30)):
                if 0 <= x < W and 0 <= y < Hh and sp[x, y][3]:
                    px[x, y] = lerp(px[x, y], (0, 0, 0, 255), 0.5)
                    gx[x, y] = crack_col
                    if r.random() < 0.3 and x + 1 < W and sp[x + 1, y][3]:
                        gx[x + 1, y] = lerp(crack_col, (0, 0, 0, 255), 0.45)
                x += r.choice((-1, 0, 1, 1)); y += r.choice((-1, 0, 1))
    return out, glowing

def make_dragon():
    for kind, body_name, eye_name in (
            ("normal", "dragon.png", "dragon_eyes.png"),
            ("rage", "dragon_rage.png", "dragon_rage_eyes.png"),
            ("final", "dragon_final.png", "dragon_final_eyes.png")):
        body, eyes = dragon_variant(kind)
        save(body, f"entity/enderdragon/{body_name}")
        save(eyes, f"entity/enderdragon/{eye_name}")

# ---------------------------------------------------------------- particles + icon
def make_particles_and_icon():
    mote = canvas(8, 8); px = mote.load()
    for y in range(8):
        for x in range(8):
            d = max(abs(x - 3.5), abs(y - 3.5))
            if d < 1: px[x, y] = (255, 255, 255, 255)
            elif d < 2: px[x, y] = CYAN_ACCENT[4]
            elif d < 3: px[x, y] = CYAN_ACCENT[2][:3] + (140,)
    save(mote, "particle/void_mote.png")
    spark = canvas(8, 8); px = spark.load()
    for i in range(8):
        px[i, 3] = MAGENTA[4]; px[i, 4] = MAGENTA[3]
        px[3, i] = MAGENTA[4]; px[4, i] = MAGENTA[3]
    px[3, 3] = (255, 255, 255, 255); px[4, 4] = MAGENTA[5]
    save(spark, "particle/resonance_spark.png")

    # mod icon 128x128: dragon eye sigil on abyssal scales
    icon = canvas(128, 128); d = ImageDraw.Draw(icon)
    n = Noise(999, 16); px = icon.load()
    for y in range(128):
        for x in range(128):
            px[x, y] = ramp(DRAGON_BASE, 0.15 + 0.45 * n.at(x, y, 128))
    d.ellipse([24, 44, 104, 84], fill=H("#0d0820"), outline=CYAN_ACCENT[2], width=3)
    d.ellipse([52, 48, 76, 80], fill=CYAN_ACCENT[3])
    d.rectangle([61, 50, 66, 78], fill=H("#05030d"))  # slit pupil
    d.polygon([(64, 18), (72, 34), (56, 34)], fill=MAGENTA[4])
    d.polygon([(30, 96), (44, 108), (30, 112)], fill=MAGENTA[3])
    d.polygon([(98, 96), (84, 108), (98, 112)], fill=MAGENTA[3])
    icon.save(os.path.join(ROOT, "src/main/resources/assets/dragonbrain/icon.png"))

# ---------------------------------------------------------------- vanilla RP
def make_resourcepack():
    """Separate OPTIONAL vanilla-overhaul pack. 100% original pixels."""
    save_rp(stone(1101, [H(c) for c in
            ("#7d7860", "#98937a", "#b3ae91", "#cfcaaa", "#e8e4c3")], 0.08, 0.9),
            "block/end_stone.png")
    save_rp(bricks(1102, END_STONE_LO + END_STONE, END_STONE_LO[0]), "block/end_stone_bricks.png")
    purpur = [H(c) for c in ("#6d557f", "#82689c", "#9a80b5", "#b39cc9", "#cdbade")]
    save_rp(organic_cells(1103, purpur), "block/purpur_block.png")
    save_rp(pillar_side(1104, purpur), "block/purpur_pillar.png")
    save_rp(pillar_top(1105, purpur), "block/purpur_pillar_top.png")
    obs = [H(c) for c in ("#07040f", "#100a20", "#1a1132", "#251a45", "#32245c")]
    save_rp(stone(1106, obs, 0.05, 0.85, cells=6), "block/obsidian.png")
    cry = stone(1107, obs, 0.05, 0.8, cells=6)
    r = random.Random(117); px = cry.load()
    cx, cy = 3, 1
    for _ in range(20):  # weeping violet tears
        if 0 <= cx < 16 and 0 <= cy < 16:
            px[cx, cy] = DRAGON_BASE[5]
            if r.random() < 0.5: px[cx, cy] = CYAN_ACCENT[2]
        cy += 1; cx += r.choice((-1, 0, 0, 1))
    save_rp(cry, "block/crying_obsidian.png")
    egg = stone(1108, obs, 0.05, 0.7)
    speckle(random.Random(118), egg, DRAGON_BASE[5], 18)
    speckle(random.Random(119), egg, MAGENTA[3], 8)
    save_rp(egg, "block/dragon_egg.png")
    save_rp(organic_cells(1109, CHORUS), "block/chorus_plant.png")
    fl = organic_cells(1110, CHORUS); px = fl.load()
    for y in range(6, 10):
        for x in range(6, 10):
            px[x, y] = MAGENTA[3]
    px[7, 7] = MAGENTA[5]; px[8, 8] = MAGENTA[5]
    save_rp(fl, "block/chorus_flower.png")
    dead = organic_cells(1111, [lerp(c, H("#8a8578"), 0.55) for c in CHORUS])
    save_rp(dead, "block/chorus_flower_dead.png")
    bedrock = stone(1112, [H(c) for c in ("#14141c", "#23232e", "#343440", "#4a4a58")], 0.02, 0.95, cells=3)
    save_rp(bedrock, "block/bedrock.png")

    def rp_item(name, fn):
        img = canvas(); fn(ImageDraw.Draw(img), img.load()); save_rp(img, f"item/{name}.png")
    def pearl(d, px):
        d.ellipse([3, 3, 12, 12], fill=DRAGON_BASE[3], outline=DRAGON_BASE[1])
        d.ellipse([5, 4, 8, 7], fill=CYAN_ACCENT[3]); px[6, 5] = (255, 255, 255, 255)
    rp_item("ender_pearl", pearl)
    def eye(d, px):
        d.ellipse([2, 5, 13, 10], fill=H("#0d3320")[:3] + (255,), outline=H("#d8e8c8"))
        d.ellipse([6, 5, 9, 10], fill=CYAN_ACCENT[2]); px[7, 7] = (0, 0, 0, 255)
        # purple-shifted eye per pack identity
        d.ellipse([2, 5, 13, 10], outline=CHORUS[5])
        d.ellipse([6, 5, 9, 10], fill=DRAGON_BASE[5]); px[7, 7] = CYAN_ACCENT[4]
    rp_item("ender_eye", eye)
    def chorus_fruit(d, px):
        d.ellipse([4, 5, 11, 12], fill=CHORUS[3], outline=CHORUS[1])
        d.line([(8, 5), (9, 2)], fill=CHORUS[2]); px[6, 7] = CHORUS[5]; px[9, 9] = CHORUS[2]
    rp_item("chorus_fruit", chorus_fruit)
    def popped(d, px):
        d.ellipse([4, 5, 11, 12], fill=CHORUS[5], outline=CHORUS[3])
        px[8, 4] = CHORUS[4]; px[6, 8] = CHORUS[3]; px[9, 10] = CHORUS[4]
    rp_item("popped_chorus_fruit", popped)

def contact_sheet():
    """Visual QA grid of every generated 16x (and key entity) texture."""
    files = []
    for root, _d, fs in os.walk(os.path.join(TEX, "block")):
        files += [os.path.join(root, f) for f in sorted(fs) if f.endswith(".png")]
    for root, _d, fs in os.walk(os.path.join(TEX, "item")):
        files += [os.path.join(root, f) for f in sorted(fs) if f.endswith(".png")]
    cell, cols = 64, 8
    rows = (len(files) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * (cell + 12)), (38, 38, 46, 255))
    d = ImageDraw.Draw(sheet)
    for i, p in enumerate(files):
        im = Image.open(p).convert("RGBA")
        if im.height > im.width:  # animated strip -> first frame
            im = im.crop((0, 0, 16, 16))
        im = im.resize((cell, cell), Image.NEAREST)
        x, y = (i % cols) * cell, (i // cols) * (cell + 12)
        sheet.paste(im, (x, y), im)
        d.text((x + 2, y + cell + 1), os.path.basename(p)[:13], fill=(220, 220, 230, 255))
    out = os.path.join(ROOT, "tools/contact_sheet.png")
    sheet.save(out)
    print("contact sheet:", out, f"({len(files)} textures)")

if __name__ == "__main__":
    make_blocks(); make_items(); make_entities(); make_dragon()
    make_particles_and_icon(); make_resourcepack(); contact_sheet()
    print("done")
