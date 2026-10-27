# DragonBrain 3.0 — End Visual Overhaul

A Fabric mod for **Minecraft Java 1.21.11** that keeps DragonBrain's
intelligent, fair, cinematic Ender Dragon fight and gives the End a
complete, cohesive visual identity: five biome material sets, three new
creatures, the **Abyssal Cataclysm** dragon, original particles and
sound design. **Every texture and sound in 3.0 is original work or a
preserved DragonBrain 2.0 asset — no third-party pack files are
bundled** (see `CREDITS.md`).

## Highlights
- **22 blocks** across Void Gardens, Chorus Wilds, Shattered Expanse,
  Petrified Abyss and Astral Barrens, plus Voidglass, Abyssal Alloy,
  Eclipse Lantern and the Abyssal Trophy.
- **3 creatures** with working models, states and telegraphs: End Wisp,
  Rift Stalker (wind-up → lunge → recovery), Ancient Sentinel
  (dormant → awakened → guard / stagger).
- **Abyssal Cataclysm dragon**: three texture sets bound to the real
  boss phases (NORMAL / RAGE / FINAL), emissive eyes + crack networks,
  phase sounds and a bounded particle aura. Vanilla model, crystal
  beams and death sequence untouched.
- **30 sounds**: the original 15 from DragonBrain 2.0 (preserved
  bit-for-bit) + 15 new synthesized originals.
- **Diagnostics**: `/dragonbrain status assets`, `/dragonbrain test assets`,
  and `./gradlew validateAssets` (441+ offline checks).

## Requirements
Minecraft **1.21.11**, Fabric Loader ≥ 0.19.0, Fabric API ≥ 0.141.0, Java 21.
No OptiFine/Continuity/CIT/CEM/shader dependency is required.

## Optional: vanilla-End texture refresh
`resourcepack/DragonBrain-AbyssalEnd-Vanilla/` (format 75) re-skins
vanilla End blocks/items in the same palette. Build the zip with
`./gradlew zipVanillaResourcePack`. The mod works fully without it.
Third-party packs *Beyonder - End Tweaks* (MIT) and *Fancy End*
(CC-BY-NC-ND-4.0) are compatible alternatives — download them from
their authors on Modrinth; they are **not** included here.

## Building
`./gradlew build` (see `BUILD_NOTES.md`). CI is green end-to-end:
compile + asset validation, a dedicated-server RCON smoke test, and a
headless-client resource-load test. The built jar is attached to every
Actions run as the `DragonBrain-3.0` artifact.

## Regenerating assets
```
pip install pillow numpy soundfile
python3 tools/generate_assets.py    # textures + optional RP textures
python3 tools/generate_sounds.py    # new sounds + copies preserved 2.0 audio
python3 tools/generate_json.py      # models/blockstates/items/loot/recipes/lang (+ AssetManifest.java)
python3 tools/validate_assets.py --strict
```

## Licence
MIT — see `LICENSE` and `CREDITS.md`.
