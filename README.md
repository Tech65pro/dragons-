# DragonBrain 3.0 — Abyssal Cataclysm Update

A Fabric mod that replaces the Ender Dragon's vanilla combat behavior with a
fair, telegraphed, phase-driven boss fight for **Minecraft Java 1.21.11**.

| Requirement | Version |
| --- | --- |
| Minecraft | 1.21.11 |
| Fabric Loader | ≥ 0.19.5 |
| Fabric API | ≥ 0.141.6+1.21.11 |
| Java | ≥ 21 |

## What it does

DragonBrain keeps the vanilla dragon (perching, crystals, death sequence, XP,
portal, egg, respawn) and layers a server-authoritative combat brain on top:

- **Fair attack scheduler** — every damaging attack runs
  APPROACH → TELEGRAPH → ATTACK → RECOVERY. Damage can only happen inside the
  ATTACK window, attacks have global + per-ability cooldowns, a minimum
  damage interval, consecutive-hit protection, and imperfect (leading +
  gaussian error) aim.
- **Four difficulty modes** — Rookie / Normal / Expert / Adaptive, plus
  per-knob multipliers in the config.
- **Classic mechanics (2.0, preserved)** — Dive, aimed Fireball volleys, Wing
  Gust, Void Pulse, crystal overcharge, stagger window, fakeouts, Rage and
  Final phases, victory sequence, custom body/eye textures, custom sounds and
  boss music.

### New in 3.0

| Ability | From phase | Telegraph | Counterplay |
| --- | --- | --- | --- |
| **Dread Roar** | Normal | charging dust ring that grows to the real radius | leave the ring, or break line-of-sight (halves it) |
| **Void Rift** | Rage | glowing purple lanes baked at cast time (safe gaps stay safe) | stand between the lanes |
| **Cataclysm Meteor Storm** | Rage | scream, then per-meteor ground glyph + whistle | keep moving, never stand in a circle |
| **Gravity Well** | Final | visible boundary ring + inward spirals, escalating pitch | walk out before the release (pull is escapable by design) |

- **Cataclysm phase** at ≤ 10 % HP: dedicated body/eye textures, energy-crack
  aura, one-time bounded transition (ring bursts + `cataclysm_transition`
  sound), faster cooldowns/recovery, extra fireballs and meteors, and its own
  boss music track. Vanilla death, XP, portal and egg are untouched.
- **Bounded combos** — on Expert (Final+) and in Cataclysm (any difficulty)
  the dragon may chain at most one extra attack per sequence, always through
  a full (floor-clamped) telegraph.
- **Original assets** — procedurally composed/portable OGG audio for every new
  ability and a 42-second looping Cataclysm track; regenerated abyssal-palette
  dragon textures (base/Final) plus a dedicated Cataclysm set. Generators live
  in `scripts/` (`generate_sounds.py`, `generate_textures.py`).

## Configuration

`config/dragonbrain.json` (schema 3). Existing 2.0 configs load unchanged —
every new key has a safe default, and malformed values are repaired with a
logged correction. A complete annotated-by-key default file is
`docs/example-dragonbrain.json` (64 keys). Highlights:

- `mechanicVoidRift` / `mechanicMeteorStorm` / `mechanicDreadRoar` /
  `mechanicGravityWell` / `mechanicCataclysmPhase` — per-ability toggles
- `cataclysmHealthThreshold` (0.10, always forced below the Final threshold)
- per-ability damage/radius/count/pull and cooldown multipliers
  (`voidRiftDamage`, `meteorCount` (1–16), `dreadRoarRadius`,
  `gravityWellPullStrength` (hard-capped at 0.35), …)
- `cataclysmAura`, `cataclysmMusic`, `particleDensityMultiplier`

## Commands

```
/dragonbrain status
/dragonbrain debug
/dragonbrain test <dive|fireball|gust|pulse|rift|meteors|roar|well|
                   stagger|rage|final|cataclysm|victory|reset>
/dragonbrain reload
```

`test cataclysm` sets the dragon to 8 % HP to view the transition safely.
The status/debug output includes phase, selected attack, per-ability
cooldowns, telegraph timers and live ability state (active meteors, well
age, combo count).

## Building & testing

```bash
./gradlew clean build   # compiles, runs the JUnit suite, produces build/libs/dragonbrain-3.0.0.jar
./gradlew test          # just the core-logic tests (no Minecraft classes)
./gradlew runClient     # dev client for the in-game checklist
```

See **TESTING.md** for what is already verified, how, and the manual in-game
checklist. See **CHANGELOG.md** for the full 2.0 → 3.0 change list, including
four inherited 2.0 bugs fixed along the way.

## Notes & limitations

- The repository originally shipped only the compiled 2.0 JAR; the source
  tree was reconstructed from it (decompiled + remapped to Yarn names)
  before 3.0 work began — see commit `26ff8ae`. The original JAR is kept at
  `dragonbrain-2.0.0.jar`.
- The development sandbox had no access to the Fabric/Mojang/Maven
  repositories, so the final Gradle build and in-game pass must run on a
  normal machine (TESTING.md lists exactly what was verified offline).
- No ability damages blocks; all effects are bounded and clean themselves up
  on death, stagger, disable and chunk unload.
