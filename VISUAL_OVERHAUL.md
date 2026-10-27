# Task 15 — Premium Texture Pack, Custom Art & Visual Overhaul: Delivery Report

## A. Audit of the existing project (before any changes)

The repository contained **only** `README.md` and the compiled
`dragonbrain-2.0.0.jar` — no source tree. Full jar inventory
(84 entries): 40 classes, 15 `.ogg`, 5 `.png`, 5 `.json`.

| Asset (2.0) | Finding | Verdict |
|---|---|---|
| `textures/entity/enderdragon/dragon.png` (256×256) | Flat 4px checker noise, only **9 unique colours**, no difference between scales/belly/wing membrane | **Replace** (keep UV mask) |
| `dragon_final.png` | Same noise pattern hue-shifted to magenta (10 colours) — the archetypal "recolored" texture | **Replace** (keep UV mask) |
| `dragon_eyes.png` / `dragon_final_eyes.png` | Single flat colour, 86 px, no pupil/gradient | **Replace** (keep eye mask) |
| `icon.png` (128×128) | 8 colours, abstract blocks of noise | **Replace** |
| 15 sound files + `sounds.json` | Proper Ogg Vorbis, sensible volumes/attenuation, coherent | **Preserve bit-for-bit** |
| Blocks / items / non-dragon entities / models / blockstates / particles | **Do not exist in 2.0 at all** | Create (this task) |
| Code surface (from bytecode) | modid `dragonbrain`; phases `NORMAL/RAGE/FINAL`; difficulties `ROOKIE/NORMAL/EXPERT/ADAPTIVE`; config keys incl. `customTexture`, `emissiveEyes`; commands `status/info/reload/debug/help/difficulty/test`; renderer design with custom body/decal/eyes layers + FINAL variants | **Preserve** |
| Versions (jar manifest + fabric.mod.json) | MC 1.21.11, Loader 0.19.5, Loom 1.19.2, Gradle 9.8.0, Java 21, MIT | **Preserve** |

### Legal sourcing results
- **Beyonder - End Tweaks**: MIT licence verified via Modrinth API.
  Archive download impossible in this sandbox (`cdn.modrinth.com`
  blocked), internal notices therefore uninspectable → policy says do
  not reuse → **0 files reused**; documented as optional install in
  `CREDITS.md`.
- **Fancy End**: CC-BY-NC-ND-4.0 verified via Modrinth API →
  **not used in any form**; recommendation-only in `CREDITS.md`.
- Everything shipped is original (generators in `tools/`) or
  DragonBrain's own preserved 2.0 audio.

## B. Art direction implemented
One seeded, reproducible painter (`tools/generate_assets.py`) with
material-specific functions — scalloped scales, masonry courses,
fluted pillars, sedimentary strata, organic cells, faceted crystal,
brushed metal with energy seams, framed glass — over biome sub-palettes
(Void Gardens violet+cyan glow, Chorus mauve, Shattered pale-yellow
End Stone, Petrified blue-grey, Astral grey-violet). Clustered
value-noise instead of per-pixel static; seamless 16× tiling.
QA contact sheet: `tools/contact_sheet.png`.

## C. Biome block sets (all registered + modelled + loot + lang)
- **Void Gardens**: Lumen Moss Block (glow speckle), Gloam Petal Block,
  Void Lily (animated 4-frame cross plant, `frametime 8`, light 9).
- **Chorus Wilds**: Chorus Bark Log (axis pillar, growth-ring top),
  Chorus Canopy (cutout + spores), Wild Chorus Bulb, Chorus Roots.
- **Shattered Expanse**: Fractured End Stone (crack walk), Shattered
  Pillar (fluted + capital), Endstone Rubble, Cracked Endstone Bricks.
- **Petrified Abyss**: Petrified Stone (strata), Fossil Block (rib-cage
  fossil), Engraved Abyssal Bricks (chiselled eye glyph), Weathered
  Masonry (violet patina).
- **Astral Barrens**: Astral Dust Block, Starshale (fine strata + cyan
  mineral flecks), Astral Crystal Block (animated 4-frame shimmer).
- **Functional**: Voidglass (translucent framed glass), Abyssal Alloy
  Block, Eclipse Lantern (custom 4-element model, light 15, hum +
  spark interaction), Abyssal Trophy (custom pedestal model).

## D. Creatures & dragon
- **End Wisp**: core+crown spikes+fins+tail silhouette, readable face,
  emissive-eyes feature layer, flutter/bob/sway animation in
  `EndWispModel.setAngles`, void-mote trail (1 per 7 ticks).
- **Rift Stalker**: real `WINDUP` (0.7 s, snarl, crouch, head down,
  glowing-gill texture swap) → `LUNGE` → `RECOVERY` (1.2 s, head/tail
  droop). State is server-authoritative via `TrackedData`.
- **Ancient Sentinel**: dormant slump, awakening (grind + core
  ignite), guard (arms crossed, 65 % damage cut), stagger (≥12 dmg hit,
  1.5 s), walk + attack swings, heavy step sounds. Only implemented
  states are animated/documented.
- **Ender Dragon — Abyssal Cataclysm**: scalloped scale shading, pale
  belly plates, ribbed wing membranes, dorsal ridge tint; **RAGE** adds
  magenta emissive cracks; **FINAL** is near-black indigo with a cyan
  emissive crack network and magenta-white eyes. Bound to the real
  `BossPhase` health thresholds on both client (texture) and server
  (sounds/particles); vanilla model/beams/death explosion preserved;
  `customTexture`/`emissiveEyes` config can disable either layer.

## E. Items & GUI language
Resonance Shard, Abyssal Alloy Ingot, Voidglass Shard, Rift Compass
(functional: finds nearest End Gateway/Portal Frame in the End,
bounded scan, 3 s cooldown), Cataclysm Scale (dragon reward drop),
Wisp Essence, Rift Fang, Sentinel Core, 3 spawn eggs — one sprite
language with the blocks; modern `items/` definitions + `models/item/`.

## F. Sounds & particles
30 registered sound events (see `CREDITS.md` for the preserved/new
split) with per-sound volume/attenuation/streaming in `sounds.json`
(+ matching registry ranges). Two lightweight particle types
(`void_mote`, `resonance_spark`) used by the wisp, lantern, compass
and dragon phase transitions. Caps: wisp 1/7 t, dragon aura 3/s,
transition burst 40 once. No shader/CIT/CEM/OptiFine requirements.

## G. Organisation
Mod assets under `assets/dragonbrain/…`, data under
`data/dragonbrain/…` (+ vanilla tag additions only). Optional vanilla
refresh is a **separate** pack at `resourcepack/DragonBrain-AbyssalEnd-Vanilla/`
with its own `pack.mcmeta` (**pack_format 75** — verified for 1.21.11),
`README.txt` install instructions, built to
`build/libs/DragonBrain-AbyssalEnd-Vanilla.zip` by
`./gradlew zipVanillaResourcePack`. The mod needs no pack to function.

## H. Validation (actual results)
- `tools/validate_assets.py --strict` — **PASS, 441 checks + 135 JSON
  parses**; wired as Gradle `validateAssets` (runs under `check`).
  The validator caught and fixed one real bug during development
  (particle sprite path resolution).
- `/dragonbrain status assets` — live registry counts per asset group.
- `/dragonbrain test assets` — real registry presence checks with
  explicit scope note; never prints success unless checks pass.
- Gradle build + in-game runtime: **verified in GitHub Actions**
  (2026-10-27): `build` compiles and packages; `server-smoke` boots a
  real dedicated server and passes 10/10 RCON assertions (registries
  22/22 blocks, 33/33 items, 90/90 asset check, all 3 summons, block
  placement, clean log); `client-smoke` boots the dev client under
  Xvfb to a created block atlas with zero model/texture/mixin errors.
  See `BUILD_NOTES.md`. (Sandbox-local Gradle remains impossible:
  Fabric/Mojang Maven are outside its network allowlist, hence CI.)

## I. Delivery lists
1. **Preserved**: 15 × 2.0 `.ogg` (bit-for-bit), dragon UV/eye masks,
   phases, difficulties, config keys, command surface, version pins.
2. **Reused (third party)**: **none** — Beyonder is MIT but its archive
   was uninspectable here; Fancy End is CC-BY-NC-ND-4.0 (no-derivatives).
3. **New original**: 22 block texture sets (+2 animated), 11 item
   sprites, 6 dragon sheets, 6 creature sheets (3 creatures + eyes/
   windup variants), 2 particle sprites, mod icon, 15 sounds, 16
   vanilla-RP textures; all models/blockstates/JSON; 40 Java files.
4. **Replaced**: `dragon.png`, `dragon_final.png`, both eye sheets,
   `icon.png` (2.0 originals retained in `_preserved_2_0/` for audit).
5. **Unimplemented / declared scope limits**: 2.0 AI internals beyond
   the verified surface (bytecode-only); biome worldgen placement of
   new blocks; GeckoLib-style keyframed animations (vanilla model
   animations implemented instead); GUI screens beyond lang/commands
   (none existed in 2.0).
6. **Resource pack requirements**: optional only; format 75; install in
   `resourcepacks/`; no dependencies.
7. **Build/validation**: validator PASS (442 checks, also enforced in
   CI via `check`); Gradle build, dedicated-server smoke test and
   headless-client smoke test all **green in GitHub Actions**
   (2026-10-27). Verified artifacts: Actions artifact
   `DragonBrain-3.0` (mod jar `dragonbrain-3.0.0.jar` + sources +
   `DragonBrain-AbyssalEnd-Vanilla.zip`, ~4.2 MB total) on the latest
   `build` workflow run; full logs mirrored on the `ci-logs` branch.
