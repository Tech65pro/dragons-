# DragonBrain 3.1 — The Abyssal End

A Fabric mod for **Minecraft Java 1.21.11** that turns the End into a real
place to explore and gives the Ender Dragon a complete, fair, cinematic
fight: **five real biomes, four real structures, three creatures, a
telegraphed boss moveset with four difficulties**, and a cohesive original
art/sound identity. **Every texture and sound is original work or a
preserved DragonBrain 2.0 asset — no third-party pack files are bundled**
(see `CREDITS.md`, `FEATURE_AUDIT.md`).

## Highlights
- **5 real End biomes** on the outer islands — `dragonbrain:void_gardens`,
  `chorus_wilds`, `shattered_expanse`, `petrified_abyss`, `astral_barrens` —
  each with ground materials, formations, flora, fog and spawns. The central
  dragon island is untouched. `/locate biome dragonbrain:<id>`.
- **4 real structures** — `ancient_end_ruins`, `abandoned_observatory`,
  `resonance_vault`, `sentinel_sanctuary` — painted templates, loot chests,
  guardians. `/locate structure dragonbrain:<id>`, or craft a **Rift
  Compass** (locates the nearest one, or a gateway).
- **22 blocks, 11 items, 12 advancements**: Voidglass, Resonance Shards,
  Abyssal Alloy, Eclipse Lantern, Abyssal Trophy, Cataclysm Scales…
- **3 creatures**: End Wisp (follows explorers holding Wisp Essence),
  Rift Stalker (telegraphed blink-lunge), Ancient Sentinel (sanctuary
  guardian).
- **The dragon fight, completed**: Rookie/Normal/Expert/Adaptive;
  telegraphed fireballs, a snapshot dive, Wing Gust, jump-dodgeable Void
  Pulse, interruptible Crystal Overcharge, Expert fakeouts, poise stagger,
  mercy-eased adaptive scaling. Vanilla dragon AI, crystals, death, portal
  and resummoning preserved; everything is config-tunable or disableable.
- **30 sounds** (15 preserved from 2.0 bit-for-bit + 15 new), 2 particles,
  full operator diagnostics (`/dragonbrain status …`, `/dragonbrain test …`).

## Requirements
Minecraft **1.21.11**, Fabric Loader ≥ 0.20.1, Fabric API ≥ 0.141.6, Java 21.
No OptiFine/Continuity/CIT/CEM/shader dependency is required.

## Existing worlds
Drop-in upgrade from 3.0.0 (all registry ids unchanged). New biomes and
structures generate only in **newly generated chunks** — explored End
terrain never changes retroactively. Set `customBiomes: false` in
`config/dragonbrain.json` to restore vanilla biome selection for new chunks.

## Optional: vanilla-End texture refresh
`resourcepack/DragonBrain-AbyssalEnd-Vanilla/` (format 75, 24 original
textures) re-skins vanilla End blocks/items in the same palette. Build the
zip with `./gradlew zipVanillaResourcePack`. It is a *refresh*, not a full
overhaul, and the mod works fully without it. Third-party packs
*Beyonder - End Tweaks* (MIT) and *Fancy End* (CC-BY-NC-ND-4.0) are
compatible alternatives — download them from their authors on Modrinth;
they are **not** included here.

## Building
`./gradlew build` (see `BUILD_NOTES.md`). CI runs compile + 700+ asset and
worldgen checks, a dedicated-server RCON smoke test (including biome and
structure `locate` in a real generated End), and a headless-client
resource-load test. On push, a GitHub Release is published automatically
**only after all three pass**.

## Regenerating assets
```
pip install pillow numpy soundfile
python3 tools/generate_assets.py    # textures + optional RP textures
python3 tools/generate_sounds.py    # new sounds + copies preserved 2.0 audio
python3 tools/generate_json.py      # models/blockstates/items/loot/recipes/lang (+ AssetManifest.java)
python3 tools/generate_worldgen.py  # biomes/features/structures/NBT templates/loot/advancements
python3 tools/validate_assets.py --strict
```

## Licence
MIT — see `LICENSE` and `CREDITS.md`.
