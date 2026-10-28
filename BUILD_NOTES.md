# Build notes — verification status

## Verified working (GitHub Actions, 2026-10-27)
The full pipeline is green on the `build` workflow (`.github/workflows/build.yml`):

| Job | What it proves | Result |
|---|---|---|
| `build` | `./gradlew build zipVanillaResourcePack` compiles all 40 Java files against Minecraft 1.21.11 / Yarn 1.21.11+build.1 / Fabric API 0.141.6, runs `validateAssets` (442 checks), produces the remapped mod jar + resource-pack zip | ✅ success |
| `server-smoke` | Boots a real dedicated dev server and drives it over RCON (`tools/ci_server_smoke.py`): `status assets` (22/22 blocks, 33/33 items, 3 entities, 30 sounds, 2 particles), `test assets` (90/90), summons all 3 creatures, places blocks, changes difficulty, clean stop. Server log error-free | ✅ success (10/10 assertions) |
| `client-smoke` | Boots the dev client headlessly under Xvfb to full resource load (block atlas 2048×2048 created); fails on any missing-model/missing-texture/mixin error | ✅ success (0 errors) |

**Download the built jar:** GitHub → Actions → latest `build` run →
artifact **`DragonBrain-3.0`** (contains `dragonbrain-3.0.0.jar`,
sources jar, and `DragonBrain-AbyssalEnd-Vanilla.zip`).
Full run logs are mirrored on the `ci-logs` branch (`logs/smoke.log`,
`logs/server.log`, `logs/client.log`) because the authoring sandbox
cannot fetch Actions log archives directly. That branch is CI output
only — safe to delete.

## Building locally
```
./gradlew build                 # mod jar in build/libs/
./gradlew zipVanillaResourcePack
./gradlew runClient             # dev client
./gradlew runServer             # dev server (accept the EULA in run/eula.txt)
```
Toolchain (pinned in `gradle.properties`, verified in CI): Java 21+
compile target (CI uses JDK 25 to run Gradle), Gradle 9.8.2 wrapper,
Loom 1.19 (`net.fabricmc.fabric-loom-remap`), Loader 0.20.1,
Fabric API 0.141.6+1.21.11, Yarn 1.21.11+build.1. These came from
FabricMC/fabric-example-mod@1.21.11.

## What CI caught and fixed along the way
1. Six initial compile errors (PlantBlock `getCodec()`, `setCanOpenDoors`,
   removed `getSpawnPos`).
2. All recipes failed to parse on a real server: data-pack format 94.1
   wants ingredients as plain strings, not `{"item": ...}` objects.
   Fixed in the generator (`tools/generate_json.py`).
3. Client smoke false positives (loader "recommends missing" for two
   resource packs mistakenly listed as recommended mods — removed;
   narrator `flite` native missing on the runner — filtered).

## Known scope limits (unchanged, declared in VISUAL_OVERHAUL.md)
- 2.0 AI internals beyond the bytecode-verified surface were not
  restored (the repo only ever contained the compiled 2.0 jar).
- Biome worldgen placement of the new blocks is future work; they are
  registered, craftable, and in the creative tab.
- The dragon phase texture swap uses `require = 0` mixins that shadow
  the renderer's static layers; if a future hotfix renames those
  targets the mod degrades to vanilla dragon textures instead of
  crashing. Client smoke proves they apply cleanly on 1.21.11, but
  only visual inspection in a real fight confirms the swap timing —
  check the dragon at 100 % / 50 % / 20 % HP once in-game.

## 3.1.0 build notes

- Version lives in `gradle.properties` (`mod_version`); the release job
  reads it, verifies `dist/dragonbrain-<version>.jar` and the RP zip are
  non-empty, then creates or refreshes release `v<version>` — only on
  push, only after all three CI jobs pass. Logs are workflow artifacts
  (`server-smoke-logs`, `client-smoke-log`); nothing is pushed to branches
  by CI anymore.
- `tools/generate_worldgen.py` generates all datapack JSON + structure
  `.nbt` templates (gzip NBT, DataVersion 4671, `size`/`pos` as
  TAG_Int_Array as vanilla requires). `tools/validate_assets.py` parses
  the templates back and cross-checks palettes, loot, foundations,
  biome→feature and structure→pool references (700+ checks total).
- Server smoke additionally asserts `/locate biome` and
  `/locate structure` succeed from (2500, 2500) in a real generated End.

## 3.1.0 verification (2026-10-28)

Push run on `arena/caa966a9-dragons` (commit `dad47ed`): **build,
server-smoke, client-smoke and release all succeeded.** Server smoke
(18 assertions) includes `biomes: 5/5 registered`, `structures: 4/4
registered`, `Asset check passed: 99/99`, and successful
`/locate biome dragonbrain:void_gardens` + `/locate structure
dragonbrain:ancient_end_ruins` from (2500, 2500) in a generated End.
Release `v3.1.0` was auto-published by the release job from that run's
artifacts: https://github.com/Tech65pro/dragons-/releases/tag/v3.1.0
(jar 2,230,207 B, RP zip 31,079 B).

Yarn note: `1.21.11+build.1` predates several renames visible on the
yarn repo's 1.21.11 branch HEAD — e.g. `DynamicRegistryManager` there
has no `get(RegistryKey)`/`getWrapperOrThrow`; diagnostics use
`streamAllRegistries()` instead. Entity position is
`getEntityPos()` (via `HeldItemContext`); plain `Entity` has
`getEyePos()` only.
