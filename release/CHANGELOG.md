# Changelog

## 3.0.0 — Abyssal Cataclysm Update (Minecraft 1.21.11, Fabric)

### New abilities (all integrated into the existing fair scheduler)

- **Void Rift** (Rage+): 2–4 glowing purple fissure lanes are baked at cast
  time and drawn for the whole ≥16-tick telegraph, with safe gaps between
  them; eruption deals `voidRiftDamage` (7) magic damage and launches
  players upward. Cooldown 260 ticks.
- **Cataclysm Meteor Storm** (Rage+): scream telegraph, then individually
  telegraphed meteors (ground glyph + whistle, ≤4 falling at once, 7-tick
  spawn cadence, 17–21-tick falls, imperfect aim). Each impact is a single
  AoE (`meteorDamage` 8, radius 3, 45 % at the edge) — no block damage, no
  double hits per impact. Spawning stops when the active window ends.
  Cooldown 340 ticks.
- **Dread Roar** (all phases): a warning ring grows to the true radius
  (`dreadRoarRadius` 18) during a ≥14-tick telegraph, then fires a frontal
  cone shockwave (`dreadRoarDamage` 4) with heavy knockback; behind the
  dragon and through cover are both much weaker. Cooldown 220 ticks.
- **Gravity Well** (Final+): telegraphed boundary ring; during the active
  window players are gently pulled via velocity (escapable by walking —
  `gravityWellPullStrength` hard-capped at 0.35, no roots, no teleports),
  ending in an outward release pulse (`gravityWellDamage` 5). Cooldown 320.

### Cataclysm phase (new, ≤10 % HP, below Final)

- One-time edge-triggered transition with `cataclysm_transition` sound,
  ring bursts, and a scheduler pause; ability runtimes are cleared so
  nothing leaks across the transition.
- Dedicated `dragon_cataclysm.png` / `dragon_cataclysm_eyes.png` textures
  (client picks Cataclysm > Final > custom), brighter aura/trail particles,
  cooldowns ×0.62, recovery ×0.72, +2 fireballs per volley, +2 meteors per
  storm, and the new abilities are weighted ×1.35.
- Separate 42-second `music_cataclysm` boss track with explicit
  stop-packet handling on track switch, death and disable.
- Short abyssal flourish layered over the untouched vanilla death sequence
  (XP / portal / egg / respawn unchanged).

### Combat system

- Bounded combo chains: at most one chained attack per sequence, different
  attack, own cooldown ready, same minimum-damage-interval gate, full
  telegraph (0.85×, floor-clamped). Policy: Cataclysm on any difficulty,
  Expert in Final+, never in the Normal phase; master toggle
  `mechanicComboChains`.
- Per-attack minimum telegraph floors (14–16 ticks) that no difficulty or
  combo can undercut; combo/fakeout shortening respects the same floors.
- Per-ability cooldown multipliers in config; debug output now reports
  per-ability cooldowns, live meteor count, well age and combo count.

### Assets

- Regenerated base + Final dragon textures in an abyssal palette (deep
  violet scales, vein network, high-contrast wings; alpha channel identical
  to 2.0 so UV transparency is unchanged) and a new Cataclysm body/eye set.
- 8 new original OGGs: `void_rift_charge`, `void_rift_erupt`,
  `meteor_whistle`, `meteor_impact`, `dread_roar`, `gravity_well`,
  `cataclysm_transition`, `music_cataclysm` — all decode-validated
  (duration/channel/peak-checked, no clipping), with SoundEvents,
  sounds.json entries, subtitles and per-sound attenuation.
- Asset generators are deterministic scripts in `scripts/`.

### Config (schema 2 → 3, backward compatible)

- 2.0 `dragonbrain.json` files load as-is; the 23 new keys default safely,
  malformed JSON falls back to defaults with logged corrections, and the
  Cataclysm threshold is always repaired to sit below the Final threshold.
- `docs/example-dragonbrain.json` contains the full 64-key default config.

### Commands

- `/dragonbrain test` gains `rift`, `meteors`, `roar`, `well` and
  `cataclysm` (sets HP to 8 %); status lists all new mechanic toggles.

### Inherited 2.0 bugs fixed

1. `transitionCooldown` was set on phase change but never decremented, so
   only the first phase transition of a fight ever played its effects.
2. `collectAvailable` re-added Void Pulse near the ground even when
   `mechanicVoidPulse` was disabled.
3. Wing Gust and Void Pulse passed outward vectors to
   `LivingEntity.takeKnockback`, which pushes along the *negative* of the
   given vector — both attacks pulled players toward the dragon.
4. `allowCombo` was computed in tuning but never consumed by the scheduler
   (combos simply did not exist); implemented bounded in 3.0 (see above).

### Tests & verification

- JUnit 5 suite in `src/test/java/com/dragonbrain/core/` (scheduler
  fairness/cooldowns/combos, phase thresholds, telegraph floors, config
  defaults + 2.0 compatibility, aim solver) — runs with `./gradlew test`.
- `scripts/verify_core_logic.py` ports the core 1:1 (including an exact
  `java.util.Random`) and executes the same assertions offline:
  164 checks, 0 failures.
- See TESTING.md for the honest split of what was executed vs. what still
  needs a Gradle/client run.

### Deliberately not included

- Eclipse sky effect: skipped — a safe implementation could not be verified
  without a running client, and an unverified rendering change is a worse
  risk than a missing flourish.

## 2.0.0

- Fair telegraphed scheduler (Dive / Fireball / Wing Gust / Void Pulse),
  crystal overcharge, stagger window, fakeouts, Rage/Final phases,
  Rookie/Normal/Expert/Adaptive difficulty, custom textures with emissive
  eyes, custom sounds and two boss music tracks, victory sequence,
  `/dragonbrain` commands, JSON config (schema 2).
