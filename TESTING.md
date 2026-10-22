# Testing DragonBrain 3.0

This file separates three things clearly: what was **executed and passed**
during development, what **could not be executed** in the development
environment (and must run on your machine), and a **manual in-game
checklist**.

## Environment constraint (why some steps are yours to run)

The development sandbox had no route to the Fabric/Mojang/Gradle/Maven
repositories (TLS-blocked) and no JDK compiler could be obtained through the
allowed package channels. Therefore `./gradlew build`, the JUnit run and any
in-game listening/looking test were **not** executed there. Everything that
could be verified offline was; nothing is claimed beyond that.

## Executed during development (all passing)

| Check | How | Result |
| --- | --- | --- |
| Core combat logic | `scripts/verify_core_logic.py` — a 1:1 Python port of the MC-independent `core/` package with a bit-exact `java.util.Random`, mirroring the JUnit assertions | **164 checks, 0 failures** |
| Java syntax | `java-parser` (ANTLR Java grammar) over all sources | **33 files, 0 parse errors** (28 main + 5 test) |
| Resource wiring | scripted cross-check: entrypoints & mixin targets exist; 23 registered sounds ↔ `sounds.json` ↔ subtitle keys ↔ `.ogg` files; 6 referenced dragon textures exist; JSON validity | **passed** |
| Audio validation | ffmpeg full decode of all 23 OGGs + `volumedetect` peak/duration/channel inspection of the 8 new files | **all decode cleanly; peaks ≤ −0.1 dBFS; music exactly 42.00 s** |
| Texture validation | Pillow inspection | 256×256 RGBA; body alpha **bit-identical** to the 2.0 textures (UV layout preserved) |
| API verification | Yarn 1.21.11 mappings + decompiled vanilla sources | `takeKnockback` direction (drives a real 2.0 bugfix), `StopSoundS2CPacket(Identifier, SoundCategory)`, public `velocityDirty`, particle/damage-source names, `DustParticleEffect(int, float)` with `0xFFRRGGBB` (full alpha — safe under both RGB and ARGB readings) |

Run the offline logic check yourself any time (no JDK needed):

```bash
python3 scripts/verify_core_logic.py   # exit 0 = pass
```

### What the logic tests cover

- Phase thresholds and one-time phase determination, incl. Cataclysm below
  Final and disabled-phase behavior.
- Telegraph floors: ≥12 ticks globally; per-attack floors (Rift/Meteor/Well
  16, Roar 14) hold even on Expert with aggressive config multipliers;
  recovery ≥6, active ≥4, cooldowns ≥1.
- Scheduler fairness over thousands of seeded ticks: damage only inside the
  ATTACK window; per-attack cooldown spacing; minimum-damage-interval
  blocks back-to-back damaging attacks; phase gating (e.g. Gravity Well
  never at Rage, everything unlocked in Cataclysm); disabled abilities are
  never selected; fakeouts stay phase-legal; combos are bounded
  (≤1 chain per sequence) and fully gated; staggered interrupts produce a
  recovery window; no attacks without a target.
- Config: defaults, a literal 2.0 `dragonbrain.json` loading with new keys
  defaulted, malformed JSON → defaults + corrections, clamps
  (`meteorCount 500 → 16`, `gravityWellPullStrength 5 → 0.35`,
  `voidRiftDamage 900 → 24`, NaN repair), threshold ordering repair, and a
  serialize→load round-trip.
- Aim solver: stationary solutions, bounded lead prediction, clamped
  gaussian error.

## Must be run on a normal machine (not yet executed)

```bash
./gradlew clean build   # expect: compile + JUnit suite + build/libs/dragonbrain-3.0.0.jar
./gradlew test          # the JUnit 5 suite in src/test (same assertions as the Python port)
```

The JUnit tests exercise only `core/` + Gson (no Minecraft classes), so
they run fast and cannot be broken by mapping changes. If Loom resolves
differently on your machine, the test task needs JUnit on the test
classpath (already wired in `build.gradle`: junit-bom 5.11.4, jupiter,
platform-launcher, `useJUnitPlatform()`).

## Manual in-game checklist (`./gradlew runClient`, or a test server)

Setup: creative world → End → `/dragonbrain status`, then `/dragonbrain test`.

1. **Each ability**: `test rift`, `test meteors`, `test roar`, `test well` —
   telegraph is visible/audible, damage only in the warned zone, no block
   damage, no double-hit from one meteor.
2. **Counterplay**: stand between rift lanes (no damage); break line of
   sight to the roar behind a pillar (reduced effect); walk out of the
   gravity well before release (should be easy in plain movement gear).
3. **Phases**: `test rage`, `test final`, `test cataclysm` (8 % HP) —
   transition plays once, music switches to the Cataclysm track, body/eye
   textures switch,Scheduler pauses briefly, then resumes.
4. **Kill the dragon**: vanilla death beams, XP, portal + egg all appear;
   music stops; no leftover meteors/well effects continue.
5. **Config toggles**: set `mechanicMeteorStorm: false` etc. in
   `config/dragonbrain.json`, `/dragonbrain reload`, confirm the ability
   disappears from selection and `test meteors` cannot force damage without
   a telegraph (forced attacks still telegraph).
6. **Difficulty**: Rookie should show only 2 rift lanes and a small storm;
   Expert 4 lanes and a denser storm.
7. **Multiplayer sanity** (if available): second player hears music
   start/stop correctly on phase change and death; particles sane at
   `particleDensityMultiplier` 1.0.

## Known unverified points

- Exact on-model appearance of the regenerated textures (verified as flat
  UV maps with identical alpha; UV region placement relies on the 2.0
  layout being correct, since alpha/registration is unchanged).
- In-game perceived loudness/balance of the new sounds (verified
  objectively: decode, duration, peak headroom; never heard in a client
  during development).
- Dust particle tint rendering (uses full-alpha colors, chosen to be safe
  either way).
- Mixins compile against 1.21.11 names as reconstructed from the shipped
  2.0 JAR; a real client run is the only full proof.
