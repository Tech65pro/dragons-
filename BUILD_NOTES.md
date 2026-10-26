# Build notes — verification status (honest edition)

## What could NOT be run in this environment
The sandbox that produced this task can only reach github.com,
registry.npmjs.org and pypi.org. Therefore:
- `maven.fabricmc.net`, Mojang's piston servers and
  `services.gradle.org` are unreachable → **Gradle/Loom cannot resolve
  dependencies → `./gradlew build` was not executed here.**
- No JDK could be downloaded (GitHub release assets redirect off the
  allowlist) → **no `javac` compilation or in-game render test ran here.**
- `cdn.modrinth.com` is unreachable → the Beyonder pack archive could
  not be downloaded/inspected (licence itself verified via the
  Modrinth API — see CREDITS.md). Result: **zero third-party files reused.**

## What WAS verified here (real results, 2026-10-26)
- `python3 tools/validate_assets.py --strict` → **PASS**
  (441 checks + 135 JSON parses): registry↔manifest id parity,
  blockstate/model/texture chains, item definitions, 80 lang keys,
  30 sound defs ↔ 30 valid Ogg files, loot/recipe reference integrity,
  texture dimensions, animated-strip .mcmeta pairing, no orphan
  textures, and the new dragon sheets keeping the **exact** 2.0
  alpha/UV mask.
- All generated JSON parses; the optional resource-pack ZIP was built
  and verified (`testzip` clean, `pack_format` 75).
- Java sources were syntax-parsed with `javalang`: 32/40 files parse
  cleanly under its Java-8 grammar; the remaining 8 use Java 14–21
  features (switch expressions, records, pattern-matching) that
  `javalang` cannot read. This is partial syntax evidence only — it is
  **not** a compilation.

## First build on a networked machine
1. Confirm current Yarn + Fabric API builds for 1.21.11 at
   https://fabricmc.net/develop and update `gradle.properties`
   (`yarn_mappings`, `fabric_api_version` are best-known values from
   the 2.0 jar manifest era: Loom 1.19.2, Loader 0.19.5).
2. `gradle wrapper --gradle-version 9.8.0` once (only
   `gradle-wrapper.properties` could be committed from the sandbox),
   then `./gradlew validateAssets build`.
3. API points to double-check against your exact Yarn build if the
   compiler complains (written against 1.21.11-era Yarn from memory of
   the 1.21.2+ render-state APIs; each is a one-line fix if renamed):
   - `BlockRenderLayerMap` / `RenderLayer` cutout-translucent registration
   - `EyesFeatureRenderer#getEyesTexture` name/signature
   - `ParticleFactory` lambda (Random parameter) and `SpriteProvider#getSprite(Random)`
   - `Item.Settings#spawnEgg(EntityType)` and `SpawnEggItem(Settings)`
   - `BlockState#getModelOffset(BlockPos)`, `Entity#getEntityWorld()`,
     `World#getTopYInclusive()`, `ModelPart.origin*` fields,
     `ModelTransform.origin(...)`
   - Dragon renderer targets in `EnderDragonRendererMixin`
     (`updateRenderState`, `getTexture`, `render` + `RenderLayer.getEyes`).
     All injections are `require = 0`, so even a renamed target degrades
     to vanilla dragon textures instead of crashing; `customTexture`
     can also be disabled in `config/dragonbrain.json`.
4. `./gradlew runClient` → `/dragonbrain test assets` (operator) and
   eyeball the five biome block sets, the three mobs (`/summon
   dragonbrain:end_wisp` etc.) and the dragon at 100 % / 50 % / 20 % HP
   for the NORMAL / RAGE / FINAL texture transitions.

## Known scope limits (not silently dropped — declared)
- The repository contained only the compiled 2.0 jar, no sources. The
  2.0 AI internals (AttackScheduler, CombatManager, MusicDirector, …)
  exist only as intermediary-mapped bytecode and could not be restored
  1:1 in this visual-overhaul task. What is preserved: mod id, package
  layout, boss phases, difficulty modes, all 15 sound ids + audio,
  config keys, command surface, and the dragon texture/renderer design.
  Behaviour implemented in 3.0 source: phase watcher (sounds/particles/
  reward drops), commands, config, all new content above.
- Biome *world generation* placement of the new blocks is out of this
  task's scope: blocks are fully registered, craftable and in the
  creative tab; wisps/stalkers have conservative End spawn rules;
  sentinels are structure/command/egg placed by design.
