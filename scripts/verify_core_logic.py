#!/usr/bin/env python3
"""
DragonBrain core-logic verification harness (no JDK required).

This is a line-by-line Python port of the Minecraft-independent Java core in
src/main/java/com/dragonbrain/core (AttackScheduler, TuningFactory,
DifficultyTuning, BossPhase, AttackType, DragonBrainConfig validation,
AimSolver, Vec), including an exact reimplementation of java.util.Random so
seeded runs make the same choices as the Java code.

It executes the same assertions as the JUnit suite in src/test. The JUnit
suite remains the authoritative test set (`./gradlew test`); this harness
exists so the logic can be exercised in environments without a JDK/Gradle.
Floating point uses Python doubles where Java uses floats; all assertions
are written as properties/inequalities so precision differences cannot hide
logic errors.

Exit code 0 = all checks passed.
"""
import json
import math
import sys
from enum import IntEnum

# --------------------------------------------------------------------------- #
# java.util.Random (48-bit LCG, same sequences as the JVM)
# --------------------------------------------------------------------------- #
class JavaRandom:
    def __init__(self, seed):
        self.seed = (seed ^ 0x5DEECE66D) & ((1 << 48) - 1)

    def _next(self, bits):
        self.seed = (self.seed * 0x5DEECE66D + 0xB) & ((1 << 48) - 1)
        return self.seed >> (48 - bits)

    def next_int(self, bound=None):
        if bound is None:
            v = self._next(32)
            return v - (1 << 32) if v >= (1 << 31) else v
        if bound & (bound - 1) == 0:
            return (bound * self._next(31)) >> 31
        while True:
            bits = self._next(31)
            val = bits % bound
            if bits - val + (bound - 1) < (1 << 31):
                return val

    def next_float(self):
        return self._next(24) / float(1 << 24)

    def next_double(self):
        return ((self._next(26) << 27) + self._next(27)) / float(1 << 53)

    def next_boolean(self):
        return self._next(1) != 0


# --------------------------------------------------------------------------- #
# core enums / data
# --------------------------------------------------------------------------- #
class BossPhase(IntEnum):
    NORMAL = 0
    RAGE = 1
    FINAL = 2
    CATACLYSM = 3

    @staticmethod
    def for_health(ratio, rage, final, cataclysm=-1.0):
        if ratio <= cataclysm:
            return BossPhase.CATACLYSM
        if ratio <= final:
            return BossPhase.FINAL
        return BossPhase.RAGE if ratio <= rage else BossPhase.NORMAL

    def is_at_least(self, other):
        return self.value >= other.value


class AttackType(IntEnum):
    FIREBALL = 0
    DIVE = 1
    WING_GUST = 2
    VOID_PULSE = 3
    DREAD_ROAR = 4
    VOID_RIFT = 5
    METEOR_STORM = 6
    GRAVITY_WELL = 7
    REPOSITION = 8
    HOVER = 9


# (damaging, baseCooldown, baseTelegraph, baseActive, pressure, minTelegraph, minPhase)
ATTACK_DATA = {
    AttackType.FIREBALL: (True, 90, 12, 26, 1.0, 0, BossPhase.NORMAL),
    AttackType.DIVE: (True, 140, 22, 30, 1.25, 0, BossPhase.NORMAL),
    AttackType.WING_GUST: (True, 120, 20, 18, 0.55, 0, BossPhase.NORMAL),
    AttackType.VOID_PULSE: (True, 170, 30, 34, 1.0, 0, BossPhase.NORMAL),
    AttackType.DREAD_ROAR: (True, 220, 26, 8, 0.8, 14, BossPhase.NORMAL),
    AttackType.VOID_RIFT: (True, 260, 30, 10, 1.1, 16, BossPhase.RAGE),
    AttackType.METEOR_STORM: (True, 340, 32, 90, 1.2, 16, BossPhase.RAGE),
    AttackType.GRAVITY_WELL: (True, 320, 30, 70, 0.9, 16, BossPhase.FINAL),
    AttackType.REPOSITION: (False, 60, 0, 24, 0.0, 0, BossPhase.NORMAL),
    AttackType.HOVER: (False, 80, 0, 30, 0.0, 0, BossPhase.NORMAL),
}


def at_damaging(t): return ATTACK_DATA[t][0]
def at_cooldown(t): return ATTACK_DATA[t][1]
def at_telegraph(t): return ATTACK_DATA[t][2]
def at_active(t): return ATTACK_DATA[t][3]
def at_pressure(t): return ATTACK_DATA[t][4]
def at_min_telegraph(t): return ATTACK_DATA[t][5]
def at_min_phase(t): return ATTACK_DATA[t][6]
def at_unlocked(t, phase): return phase.is_at_least(at_min_phase(t))


class CombatState(IntEnum):
    IDLE = 0
    APPROACH = 1
    TELEGRAPH = 2
    ATTACK = 3
    RECOVERY = 4
    REPOSITION = 5
    PHASE_TRANSITION = 6


class DifficultyMode(IntEnum):
    ROOKIE = 0
    NORMAL = 1
    EXPERT = 2
    ADAPTIVE = 3


def jround(x):
    """java.lang.Math.round(float) -> int"""
    return math.floor(x + 0.5)


# --------------------------------------------------------------------------- #
# DifficultyTuning
# --------------------------------------------------------------------------- #
class DifficultyTuning:
    def __init__(self, mode, attack_interval, jitter, cd_mult, tele_mult, rec_mult,
                 min_telegraph, min_damage_interval, aim_error, prediction, spread,
                 reaction_delay, projectile_speed, damage_mult, consecutive_hit_limit,
                 hit_pressure_cd, fakeout_chance, allow_combo, ability_cd_scales=None):
        self.mode = mode
        self.attack_interval_ticks = attack_interval
        self.attack_interval_jitter_ticks = jitter
        self.cooldown_multiplier = cd_mult
        self.telegraph_multiplier = tele_mult
        self.recovery_multiplier = rec_mult
        self.min_telegraph_ticks = min_telegraph
        self.min_damage_interval_ticks = min_damage_interval
        self.aim_error_degrees = aim_error
        self.prediction_strength = prediction
        self.projectile_spread_degrees = spread
        self.reaction_delay_ticks = reaction_delay
        self.projectile_speed = projectile_speed
        self.damage_multiplier = damage_mult
        self.consecutive_hit_limit = consecutive_hit_limit
        self.hit_pressure_cooldown_ticks = hit_pressure_cd
        self.fakeout_chance = fakeout_chance
        self.allow_combo = allow_combo
        self.ability_cooldown_scales = dict(ability_cd_scales or {})

    def telegraph_ticks(self, attack):
        base = jround(at_telegraph(attack) * self.telegraph_multiplier)
        return max(max(self.min_telegraph_ticks, at_min_telegraph(attack)), base)

    def active_ticks(self, attack):
        return max(4, jround(at_active(attack) / max(0.5, self.projectile_speed)))

    def recovery_ticks(self, attack):
        return max(6, jround(at_active(attack) * 0.6 * self.recovery_multiplier))

    def cooldown_ticks(self, attack):
        scale = self.ability_cooldown_scales.get(attack, 1.0)
        return max(1, jround(at_cooldown(attack) * self.cooldown_multiplier * scale))

    def clamp_damage(self, raw):
        return max(0.0, raw * self.damage_multiplier)


# --------------------------------------------------------------------------- #
# DragonBrainConfig (defaults + validate, mirroring the Java class)
# --------------------------------------------------------------------------- #
class DragonBrainConfig:
    def __init__(self):
        self.schema_version = 3
        self.enabled = True
        self.difficulty = "NORMAL"
        self.debug = False
        self.debug_target_any_living_entity = False
        self.attack_frequency_multiplier = 1.0
        self.cooldown_multiplier = 1.0
        self.warning_duration_multiplier = 1.0
        self.recovery_duration_multiplier = 1.0
        self.projectile_speed_multiplier = 1.0
        self.aim_accuracy_multiplier = 1.0
        self.prediction_strength_multiplier = 1.0
        self.reaction_delay_ticks = 0
        self.damage_multiplier = 1.0
        self.enforce_minimum_damage_interval = True
        self.consecutive_hit_protection = True
        self.mechanic_dive = True
        self.mechanic_wing_gust = True
        self.mechanic_void_pulse = True
        self.mechanic_crystal_overcharge = True
        self.mechanic_stagger_window = True
        self.mechanic_rage_phase = True
        self.mechanic_fakeout = True
        self.mechanic_final_phase = True
        self.mechanic_victory_sequence = True
        self.mechanic_combo_chains = True
        self.preserve_vanilla_perching = True
        self.crystal_overcharge_extra_fireball = True
        self.wing_gust_damage = 2.0
        self.void_pulse_damage = 6.0
        self.rage_health_threshold = 0.5
        self.final_phase_health_threshold = 0.25
        self.mechanic_void_rift = True
        self.mechanic_meteor_storm = True
        self.mechanic_dread_roar = True
        self.mechanic_gravity_well = True
        self.mechanic_cataclysm_phase = True
        self.cataclysm_health_threshold = 0.1
        self.void_rift_damage = 7.0
        self.void_rift_cooldown_multiplier = 1.0
        self.meteor_damage = 8.0
        self.meteor_count = 7
        self.meteor_radius = 3.0
        self.meteor_cooldown_multiplier = 1.0
        self.dread_roar_damage = 4.0
        self.dread_roar_radius = 18.0
        self.dread_roar_cooldown_multiplier = 1.0
        self.gravity_well_damage = 5.0
        self.gravity_well_radius = 12.0
        self.gravity_well_pull_strength = 0.1
        self.gravity_well_cooldown_multiplier = 1.0
        self.cataclysm_aura = True
        self.cataclysm_music = True
        self.particle_density_multiplier = 1.0
        self.adaptive_difficulty = True
        self.custom_texture = True
        self.emissive_eyes = True
        self.custom_sounds = True
        self.boss_music = True
        self.ambient_end_audio = True
        self.ambient_particles = True
        self.sound_volume = 1.0
        self.music_volume = 0.8
        self.ambient_volume = 0.6
        self.corrections = []

    # camelCase json key -> attribute
    KEYMAP = {}

    @classmethod
    def _keymap(cls):
        if cls.KEYMAP:
            return cls.KEYMAP
        import re
        for attr in list(vars(DragonBrainConfig()).keys()):
            camel = re.sub(r"_([a-z])", lambda m: m.group(1).upper(), attr)
            cls.KEYMAP[camel] = attr
        cls.KEYMAP["schemaVersion"] = "schema_version"
        return cls.KEYMAP

    @staticmethod
    def from_json(text):
        cfg = DragonBrainConfig()
        malformed = False
        try:
            data = json.loads(text) if text and text.strip() else None
            if not isinstance(data, dict):
                malformed = True
                data = {}
        except Exception:
            malformed = True
            data = {}
        km = DragonBrainConfig._keymap()
        for k, v in data.items():
            attr = km.get(k)
            if attr is not None:
                setattr(cfg, attr, v)
        cfg.validate()
        if malformed:
            cfg.corrections.insert(0, "Config file was empty or malformed; defaults were restored.")
        return cfg

    @staticmethod
    def _clamp(fixes, name, value, lo, hi):
        if isinstance(value, float) and math.isnan(value):
            fixes.append(f"{name} was NaN; using {lo}.")
            return lo
        clamped = max(lo, min(hi, value))
        if abs(clamped - value) > 1e-4:
            fixes.append(f"{name} {value} out of range {lo}..{hi}; clamped to {clamped}.")
        return clamped

    def validate(self):
        fixes = []
        c = DragonBrainConfig._clamp
        self.schema_version = 3
        valid = {"ROOKIE", "NORMAL", "EXPERT", "ADAPTIVE"}
        mode = self.difficulty.strip().upper() if isinstance(self.difficulty, str) else "NORMAL"
        if mode not in valid:
            fixes.append(f"difficulty '{self.difficulty}' is not valid; using NORMAL.")
            mode = "NORMAL"
        self.difficulty = mode
        self.attack_frequency_multiplier = c(fixes, "attackFrequencyMultiplier", self.attack_frequency_multiplier, 0.4, 3.0)
        self.cooldown_multiplier = c(fixes, "cooldownMultiplier", self.cooldown_multiplier, 0.4, 3.0)
        self.warning_duration_multiplier = c(fixes, "warningDurationMultiplier", self.warning_duration_multiplier, 0.5, 3.0)
        self.recovery_duration_multiplier = c(fixes, "recoveryDurationMultiplier", self.recovery_duration_multiplier, 0.5, 3.0)
        self.projectile_speed_multiplier = c(fixes, "projectileSpeedMultiplier", self.projectile_speed_multiplier, 0.5, 2.0)
        self.aim_accuracy_multiplier = c(fixes, "aimAccuracyMultiplier", self.aim_accuracy_multiplier, 0.25, 3.0)
        self.prediction_strength_multiplier = c(fixes, "predictionStrengthMultiplier", self.prediction_strength_multiplier, 0.0, 2.0)
        self.damage_multiplier = c(fixes, "damageMultiplier", self.damage_multiplier, 0.0, 3.0)
        self.sound_volume = c(fixes, "soundVolume", self.sound_volume, 0.0, 2.0)
        self.music_volume = c(fixes, "musicVolume", self.music_volume, 0.0, 2.0)
        self.ambient_volume = c(fixes, "ambientVolume", self.ambient_volume, 0.0, 2.0)
        if self.reaction_delay_ticks < 0 or self.reaction_delay_ticks > 40:
            fixes.append("reactionDelayTicks out of range 0..40; clamped.")
            self.reaction_delay_ticks = max(0, min(40, self.reaction_delay_ticks))
        self.wing_gust_damage = c(fixes, "wingGustDamage", self.wing_gust_damage, 0.0, 12.0)
        self.void_pulse_damage = c(fixes, "voidPulseDamage", self.void_pulse_damage, 0.0, 20.0)
        self.rage_health_threshold = c(fixes, "rageHealthThreshold", self.rage_health_threshold, 0.05, 0.95)
        self.final_phase_health_threshold = c(fixes, "finalPhaseHealthThreshold", self.final_phase_health_threshold, 0.02, self.rage_health_threshold)
        if self.final_phase_health_threshold >= self.rage_health_threshold:
            fixes.append("finalPhaseHealthThreshold must be lower than rageHealthThreshold; adjusted.")
            self.final_phase_health_threshold = self.rage_health_threshold * 0.5
        self.cataclysm_health_threshold = c(fixes, "cataclysmHealthThreshold", self.cataclysm_health_threshold, 0.01, self.final_phase_health_threshold)
        if self.cataclysm_health_threshold >= self.final_phase_health_threshold:
            fixes.append("cataclysmHealthThreshold must be lower than finalPhaseHealthThreshold; adjusted.")
            self.cataclysm_health_threshold = self.final_phase_health_threshold * 0.5
        self.void_rift_damage = c(fixes, "voidRiftDamage", self.void_rift_damage, 0.0, 24.0)
        self.void_rift_cooldown_multiplier = c(fixes, "voidRiftCooldownMultiplier", self.void_rift_cooldown_multiplier, 0.4, 3.0)
        self.meteor_damage = c(fixes, "meteorDamage", self.meteor_damage, 0.0, 24.0)
        self.meteor_radius = c(fixes, "meteorRadius", self.meteor_radius, 1.0, 6.0)
        self.meteor_cooldown_multiplier = c(fixes, "meteorCooldownMultiplier", self.meteor_cooldown_multiplier, 0.4, 3.0)
        if self.meteor_count < 1 or self.meteor_count > 16:
            fixes.append("meteorCount out of range 1..16; clamped.")
            self.meteor_count = max(1, min(16, self.meteor_count))
        self.dread_roar_damage = c(fixes, "dreadRoarDamage", self.dread_roar_damage, 0.0, 16.0)
        self.dread_roar_radius = c(fixes, "dreadRoarRadius", self.dread_roar_radius, 6.0, 32.0)
        self.dread_roar_cooldown_multiplier = c(fixes, "dreadRoarCooldownMultiplier", self.dread_roar_cooldown_multiplier, 0.4, 3.0)
        self.gravity_well_damage = c(fixes, "gravityWellDamage", self.gravity_well_damage, 0.0, 20.0)
        self.gravity_well_radius = c(fixes, "gravityWellRadius", self.gravity_well_radius, 5.0, 24.0)
        self.gravity_well_pull_strength = c(fixes, "gravityWellPullStrength", self.gravity_well_pull_strength, 0.0, 0.35)
        self.gravity_well_cooldown_multiplier = c(fixes, "gravityWellCooldownMultiplier", self.gravity_well_cooldown_multiplier, 0.4, 3.0)
        self.particle_density_multiplier = c(fixes, "particleDensityMultiplier", self.particle_density_multiplier, 0.25, 2.0)
        self.corrections = fixes
        return fixes

    def mechanic_enabled(self, t):
        return {
            AttackType.FIREBALL: True,
            AttackType.DIVE: self.mechanic_dive,
            AttackType.WING_GUST: self.mechanic_wing_gust,
            AttackType.VOID_PULSE: self.mechanic_void_pulse,
            AttackType.DREAD_ROAR: self.mechanic_dread_roar,
            AttackType.VOID_RIFT: self.mechanic_void_rift,
            AttackType.METEOR_STORM: self.mechanic_meteor_storm,
            AttackType.GRAVITY_WELL: self.mechanic_gravity_well,
            AttackType.REPOSITION: True,
            AttackType.HOVER: True,
        }[t]

    def ability_cooldown_multiplier(self, t):
        return {
            AttackType.VOID_RIFT: self.void_rift_cooldown_multiplier,
            AttackType.METEOR_STORM: self.meteor_cooldown_multiplier,
            AttackType.DREAD_ROAR: self.dread_roar_cooldown_multiplier,
            AttackType.GRAVITY_WELL: self.gravity_well_cooldown_multiplier,
        }.get(t, 1.0)


# --------------------------------------------------------------------------- #
# TuningFactory
# --------------------------------------------------------------------------- #
def clamp01(v): return 0.0 if v < 0.0 else (1.0 if v > 1.0 else v)


class Knobs:
    def __init__(self, *a):
        (self.attack_interval, self.jitter, self.cooldown, self.telegraph, self.recovery,
         self.min_telegraph, self.min_damage_interval, self.aim_error, self.prediction,
         self.spread, self.reaction_delay, self.projectile_speed, self.damage,
         self.consecutive_hits, self.pressure_cooldown, self.fakeout_chance, self.combos) = a


ROOKIE_K = Knobs(150, 40, 1.55, 1.55, 1.7, 14, 26, 9.0, 0.3, 8.0, 14, 0.9, 0.6, 2, 120, 0.0, False)
NORMAL_K = Knobs(105, 30, 1.0, 1.0, 1.0, 10, 16, 5.5, 0.55, 5.0, 8, 1.0, 1.0, 3, 70, 0.12, False)
EXPERT_K = Knobs(75, 20, 0.78, 0.8, 0.75, 9, 12, 2.75, 0.82, 3.0, 4, 1.15, 1.25, 3, 50, 0.3, True)


def _lerp_i(a, b, t): return jround(a + (b - a) * t)
def _lerp_f(a, b, t): return a + (b - a) * t


def tuning_create(mode, adaptiveness, config, phase):
    if mode == DifficultyMode.ADAPTIVE:
        t = clamp01(adaptiveness)
        a, b = ROOKIE_K, EXPERT_K
        base = Knobs(
            _lerp_i(a.attack_interval, b.attack_interval, t), _lerp_i(a.jitter, b.jitter, t),
            _lerp_f(a.cooldown, b.cooldown, t), _lerp_f(a.telegraph, b.telegraph, t),
            _lerp_f(a.recovery, b.recovery, t), _lerp_i(a.min_telegraph, b.min_telegraph, t),
            _lerp_i(a.min_damage_interval, b.min_damage_interval, t), _lerp_f(a.aim_error, b.aim_error, t),
            _lerp_f(a.prediction, b.prediction, t), _lerp_f(a.spread, b.spread, t),
            _lerp_i(a.reaction_delay, b.reaction_delay, t), _lerp_f(a.projectile_speed, b.projectile_speed, t),
            _lerp_f(a.damage, b.damage, t), _lerp_i(a.consecutive_hits, b.consecutive_hits, t),
            _lerp_i(a.pressure_cooldown, b.pressure_cooldown, t), _lerp_f(a.fakeout_chance, b.fakeout_chance, t),
            t > 0.5)
    elif mode == DifficultyMode.ROOKIE:
        base = ROOKIE_K
    elif mode == DifficultyMode.EXPERT:
        base = EXPERT_K
    else:
        base = NORMAL_K

    phase_cooldown = {BossPhase.RAGE: 0.85, BossPhase.FINAL: 0.72, BossPhase.CATACLYSM: 0.62}.get(phase, 1.0)
    phase_recovery = {BossPhase.RAGE: 0.9, BossPhase.FINAL: 0.8, BossPhase.CATACLYSM: 0.72}.get(phase, 1.0)
    combos = config.mechanic_combo_chains and phase != BossPhase.NORMAL and (base.combos or phase == BossPhase.CATACLYSM)
    scales = {}
    for at in AttackType:
        s = config.ability_cooldown_multiplier(at)
        if abs(s - 1.0) > 1e-4:
            scales[at] = max(0.05, s)
    return DifficultyTuning(
        mode,
        max(20, jround(base.attack_interval * config.attack_frequency_multiplier)),
        max(0, jround(base.jitter * config.attack_frequency_multiplier)),
        base.cooldown * config.cooldown_multiplier * phase_cooldown,
        base.telegraph * config.warning_duration_multiplier,
        base.recovery * config.recovery_duration_multiplier * phase_recovery,
        max(8, base.min_telegraph),
        max(4, jround(base.min_damage_interval)),
        base.aim_error / max(0.1, config.aim_accuracy_multiplier),
        clamp01(base.prediction * config.prediction_strength_multiplier),
        base.spread / max(0.1, config.aim_accuracy_multiplier),
        base.reaction_delay + config.reaction_delay_ticks,
        max(0.5, base.projectile_speed * config.projectile_speed_multiplier),
        base.damage * config.damage_multiplier,
        max(1, base.consecutive_hits),
        max(10, jround(base.pressure_cooldown * config.recovery_duration_multiplier)),
        clamp01(base.fakeout_chance),
        combos,
        scales)


# --------------------------------------------------------------------------- #
# AttackScheduler (1:1 port, see AttackScheduler.java)
# --------------------------------------------------------------------------- #
class Action(IntEnum):
    NONE = 0
    START_APPROACH = 1
    START_TELEGRAPH = 2
    START_ATTACK = 3
    ENTER_RECOVERY = 4
    ENTER_REPOSITION = 5
    BECOME_IDLE = 6
    PHASE_TRANSITION = 7


class Stats:
    def __init__(self): self.reset()
    def reset(self):
        self.attacks_launched = 0
        self.attacks_landed = 0
        self.attacks_cancelled = 0
        self.fakeouts = 0
        self.combos = 0
        self.pressure_blocks = 0


class Result:
    def __init__(self, action, state, attack, fakeout):
        self.action, self.state, self.attack, self.fakeout = action, state, attack, fakeout


def _none(state, attack): return Result(Action.NONE, state, attack, False)


class AttackScheduler:
    def __init__(self, random):
        self.random = random
        self.cooldowns = {t: 0 for t in AttackType}
        self.stats = Stats()
        self.state = CombatState.IDLE
        self.current = None
        self.ticks_in_state = 0
        self.planned_state_ticks = 0
        self.next_attack_delay = 0
        self.ticks_since_damaging_attack = 10000
        self.ticks_since_any_attack = 10000
        self.ticks_without_target = 0
        self.ticks_since_pressure_block = 0
        self.pressure = 0.0
        self.consecutive_hits = 0
        self.last_attack = None
        self.fakeout_used = False
        self.combo_used = False
        self.phase_transition_pending = False
        self.queued_attack = None
        self.phase = BossPhase.NORMAL

    def enter(self, state, attack, duration):
        self.state = state
        self.current = attack
        self.ticks_in_state = 0
        self.planned_state_ticks = duration

    def tick(self, tuning, has_target, distance, visible, available):
        self._tick_counters(tuning, has_target)
        if self.phase_transition_pending and self.state != CombatState.PHASE_TRANSITION:
            self.enter(CombatState.PHASE_TRANSITION, None, 24)
            self.phase_transition_pending = False
            return Result(Action.PHASE_TRANSITION, self.state, None, False)

        st = self.state
        if st == CombatState.PHASE_TRANSITION:
            if self.ticks_in_state >= self.planned_state_ticks:
                self.enter(CombatState.IDLE, None, 0)
                self.next_attack_delay = min(self.next_attack_delay, 20)
                return Result(Action.BECOME_IDLE, self.state, None, False)
            return _none(self.state, self.current)
        elif st in (CombatState.IDLE, CombatState.APPROACH):
            if not has_target:
                self.enter(CombatState.IDLE, None, 0)
                return _none(self.state, None)
            elif st == CombatState.APPROACH and self.current is not None:
                if self.ticks_in_state >= self.planned_state_ticks:
                    telegraph = tuning.telegraph_ticks(self.current)
                    self.enter(CombatState.TELEGRAPH, self.current, telegraph)
                    return Result(Action.START_TELEGRAPH, self.state, self.current, False)
                return _none(self.state, self.current)
            elif self.next_attack_delay > 0:
                return _none(self.state, None)
            else:
                forced = self.queued_attack
                self.queued_attack = None
                choice = forced if forced is not None else self._choose_attack(tuning, distance, visible, available)
                if choice is None:
                    if self.cooldowns.get(AttackType.REPOSITION, 0) <= 0:
                        self.cooldowns[AttackType.REPOSITION] = 40
                        duration = max(20, jround(tuning.attack_interval_ticks * 0.4))
                        self.enter(CombatState.REPOSITION, None, duration)
                        return Result(Action.ENTER_REPOSITION, self.state, None, False)
                    return _none(self.state, None)
                self.current = choice
                self.fakeout_used = False
                self.combo_used = False
                self.stats.attacks_launched += 1
                self.ticks_since_any_attack = 0
                if self._needs_approach(choice, distance):
                    self.enter(CombatState.APPROACH, choice, 20)
                    return Result(Action.START_APPROACH, self.state, choice, False)
                telegraph = tuning.telegraph_ticks(choice)
                self.enter(CombatState.TELEGRAPH, choice, telegraph)
                return Result(Action.START_TELEGRAPH, self.state, choice, False)
        elif st == CombatState.TELEGRAPH:
            if self.ticks_in_state >= self.planned_state_ticks:
                fakeout = self._try_fakeout(tuning, available)
                if fakeout is not None:
                    self.current = fakeout
                    self.fakeout_used = True
                    self.stats.fakeouts += 1
                    shorter = max(max(tuning.min_telegraph_ticks, at_min_telegraph(fakeout)),
                                  jround(tuning.telegraph_ticks(fakeout) * 0.8))
                    self.enter(CombatState.TELEGRAPH, fakeout, shorter)
                    return Result(Action.START_TELEGRAPH, self.state, fakeout, True)
                self.enter(CombatState.ATTACK, self.current, tuning.active_ticks(self.current))
                if self.current is not None and at_damaging(self.current):
                    self.ticks_since_damaging_attack = 0
                return Result(Action.START_ATTACK, self.state, self.current, False)
            return _none(self.state, self.current)
        elif st == CombatState.ATTACK:
            if self.ticks_in_state >= self.planned_state_ticks:
                finished = self.current
                combo = self._try_combo(tuning, available)
                if combo is not None:
                    self.current = combo
                    self.combo_used = True
                    self.stats.attacks_launched += 1
                    self.stats.combos += 1
                    self.ticks_since_any_attack = 0
                    combo_telegraph = max(max(tuning.min_telegraph_ticks, at_min_telegraph(combo)),
                                          jround(tuning.telegraph_ticks(combo) * 0.85))
                    self.enter(CombatState.TELEGRAPH, combo, combo_telegraph)
                    return Result(Action.START_TELEGRAPH, self.state, combo, False)
                recovery = 20 if finished is None else tuning.recovery_ticks(finished)
                self.enter(CombatState.RECOVERY, finished, recovery)
                return Result(Action.ENTER_RECOVERY, self.state, finished, False)
            return _none(self.state, self.current)
        elif st == CombatState.RECOVERY:
            if self.ticks_in_state >= self.planned_state_ticks:
                if self._is_pressured(tuning):
                    self.enter(CombatState.REPOSITION, None, tuning.hit_pressure_cooldown_ticks)
                    self.consecutive_hits = 0
                    return Result(Action.ENTER_REPOSITION, self.state, None, False)
                self.enter(CombatState.IDLE, None, 0)
                self.next_attack_delay = self._roll_attack_delay(tuning)
                self.cooldowns[AttackType.REPOSITION] = 0
                return Result(Action.BECOME_IDLE, self.state, None, False)
            return _none(self.state, self.current)
        elif st == CombatState.REPOSITION:
            if self.ticks_in_state >= self.planned_state_ticks:
                self.enter(CombatState.IDLE, None, 0)
                self.next_attack_delay = self._roll_attack_delay(tuning)
                return Result(Action.BECOME_IDLE, self.state, None, False)
            return _none(self.state, self.current)
        return _none(self.state, self.current)

    def _tick_counters(self, tuning, has_target):
        self.ticks_in_state += 1
        self.ticks_since_damaging_attack += 1
        self.ticks_since_any_attack += 1
        self.ticks_since_pressure_block += 1
        if self.next_attack_delay > 0:
            self.next_attack_delay -= 1
        for k in list(self.cooldowns.keys()):
            if self.cooldowns[k] > 0:
                self.cooldowns[k] -= 1
        self.pressure = max(0.0, self.pressure - 0.012)
        self.ticks_without_target = 0 if has_target else self.ticks_without_target + 1
        if self.ticks_since_pressure_block > tuning.hit_pressure_cooldown_ticks * 3:
            self.pressure = 0.0
            self.consecutive_hits = 0

    def _roll_attack_delay(self, tuning):
        jitter = tuning.attack_interval_jitter_ticks
        extra = 0 if jitter <= 0 else self.random.next_int(jitter + 1)
        return max(10, tuning.attack_interval_ticks + extra)

    def _is_pressured(self, tuning):
        return self.consecutive_hits >= tuning.consecutive_hit_limit or self.pressure >= 4.0

    def _choose_attack(self, tuning, distance, visible, available):
        damaging_locked = self.ticks_since_damaging_attack < tuning.min_damage_interval_ticks
        total = 0.0
        weights = {}
        for at in sorted(available, key=lambda x: x.value):
            if at_damaging(at) and at_unlocked(at, self.phase) and self.cooldowns.get(at, 0) <= 0 and not damaging_locked:
                w = self._base_weight(at, distance, visible)
                if not (w <= 0.0):
                    weights[at] = w
                    total += w
        if total <= 0.0:
            return None
        roll = self.random.next_float() * total
        for at in sorted(weights.keys(), key=lambda x: x.value):
            roll -= weights[at]
            if roll <= 0.0:
                self.cooldowns[at] = tuning.cooldown_ticks(at)
                self.last_attack = at
                return at
        return None

    def _base_weight(self, at, distance, visible):
        if at == AttackType.FIREBALL:
            w = (1.0 if visible else 0.25) if (10.0 < distance < 64.0) else 0.0
        elif at == AttackType.DIVE:
            w = 1.0 if (8.0 < distance < 48.0) else 0.15
        elif at == AttackType.WING_GUST:
            w = 1.1 if distance < 18.0 else 0.0
        elif at == AttackType.VOID_PULSE:
            w = 0.85 if distance < 30.0 else 0.05
        elif at == AttackType.DREAD_ROAR:
            w = 1.05 if distance < 20.0 else (0.3 if distance < 28.0 else 0.0)
        elif at == AttackType.VOID_RIFT:
            w = 0.9 if (6.0 < distance < 48.0) else 0.1
        elif at == AttackType.METEOR_STORM:
            w = 0.8 if distance < 56.0 else 0.15
        elif at == AttackType.GRAVITY_WELL:
            w = 0.85 if (6.0 < distance < 44.0) else 0.1
        else:
            w = 0.0
        if self.phase == BossPhase.CATACLYSM and at in (AttackType.VOID_RIFT, AttackType.METEOR_STORM, AttackType.GRAVITY_WELL, AttackType.DREAD_ROAR):
            w *= 1.35
        if at == self.last_attack:
            w *= 0.3
        return w

    @staticmethod
    def _needs_approach(at, distance):
        return distance > 26.0 and at in (AttackType.WING_GUST, AttackType.VOID_PULSE, AttackType.DIVE,
                                          AttackType.DREAD_ROAR, AttackType.VOID_RIFT, AttackType.GRAVITY_WELL)

    def _try_fakeout(self, tuning, available):
        if self.fakeout_used or self.current is None or not at_damaging(self.current):
            return None
        if self.random.next_float() >= tuning.fakeout_chance:
            return None
        best = None
        for at in sorted(available, key=lambda x: x.value):
            if (at != self.current and at_damaging(at) and at_unlocked(at, self.phase)
                    and self.cooldowns.get(at, 0) <= 0 and (best is None or self.random.next_boolean())):
                best = at
        if best is None:
            return None
        self.cooldowns[best] = tuning.cooldown_ticks(best)
        self.last_attack = best
        return best

    def _try_combo(self, tuning, available):
        if not tuning.allow_combo or self.combo_used or self.current is None or not at_damaging(self.current):
            return None
        if self.ticks_since_damaging_attack < tuning.min_damage_interval_ticks:
            return None
        if self.random.next_float() >= 0.65:
            return None
        best = None
        for at in sorted(available, key=lambda x: x.value):
            if (at != self.current and at_damaging(at) and at_unlocked(at, self.phase)
                    and self.cooldowns.get(at, 0) <= 0 and (best is None or self.random.next_boolean())):
                best = at
        if best is None:
            return None
        self.cooldowns[best] = tuning.cooldown_ticks(best)
        self.last_attack = best
        return best

    def on_damage_dealt(self, weight):
        self.pressure = min(12.0, self.pressure + max(0.5, weight))
        self.consecutive_hits += 1
        self.stats.attacks_landed += 1
        self.ticks_since_pressure_block = 0

    def interrupt(self, tuning, reason=""):
        if self.state not in (CombatState.TELEGRAPH, CombatState.ATTACK):
            return False
        interrupted = self.current
        self.enter(CombatState.RECOVERY, None,
                   max(10, tuning.recovery_ticks(AttackType.HOVER if interrupted is None else interrupted)))
        self.stats.attacks_cancelled += 1
        self.cooldowns[AttackType.REPOSITION] = 20
        self.pressure = max(0.0, self.pressure - 1.0)
        return True

    def begin_phase_transition(self):
        self.phase_transition_pending = True

    def set_phase(self, phase):
        self.phase = phase

    def get_cooldown_ticks(self, t):
        return self.cooldowns.get(t, 0)

    def reset(self):
        self.state = CombatState.IDLE
        self.current = None
        self.ticks_in_state = 0
        self.planned_state_ticks = 0
        self.next_attack_delay = 40
        self.ticks_since_damaging_attack = 10000
        self.ticks_since_any_attack = 10000
        self.pressure = 0.0
        self.consecutive_hits = 0
        self.last_attack = None
        self.fakeout_used = False
        self.combo_used = False
        self.phase_transition_pending = False
        self.queued_attack = None
        for k in self.cooldowns:
            self.cooldowns[k] = 0
        self.stats.reset()

    def allows_damage(self):
        return self.state == CombatState.ATTACK and self.current is not None and at_damaging(self.current)


# --------------------------------------------------------------------------- #
# Vec + AimSolver
# --------------------------------------------------------------------------- #
class Vec:
    def __init__(self, x, y, z): self.x, self.y, self.z = x, y, z
    def add(self, o): return Vec(self.x + o.x, self.y + o.y, self.z + o.z)
    def subtract(self, o): return Vec(self.x - o.x, self.y - o.y, self.z - o.z)
    def multiply(self, f): return Vec(self.x * f, self.y * f, self.z * f)
    def length(self): return math.sqrt(self.x**2 + self.y**2 + self.z**2)
    def horizontal_length(self): return math.sqrt(self.x**2 + self.z**2)
    def normalize(self):
        l = self.length()
        return Vec.ZERO if l < 1e-6 else self.multiply(1.0 / l)


Vec.ZERO = Vec(0.0, 0.0, 0.0)


def aim_gaussian(random):
    u1 = max(1e-9, random.next_double())
    u2 = random.next_double()
    g = math.sqrt(-2.0 * math.log(u1)) * math.cos(2 * math.pi * u2)
    return max(-2.0, min(2.0, g))


def aim_rotate(d, yaw_deg, pitch_deg):
    yaw, pitch = math.radians(yaw_deg), math.radians(pitch_deg)
    cy, sy = math.cos(yaw), math.sin(yaw)
    x = d.x * cy + d.z * sy
    z = -d.x * sy + d.z * cy
    y = d.y
    cp, sp = math.cos(pitch), math.sin(pitch)
    return Vec(x, y * cp - z * sp, y * sp + z * cp).normalize()


def aim_apply_error(direction, error_degrees, random):
    if error_degrees <= 0.01:
        return direction.normalize()
    return aim_rotate(direction.normalize(), aim_gaussian(random) * error_degrees,
                      aim_gaussian(random) * error_degrees * 0.6)


def aim_predict(shooter, target, velocity, travel_ticks, tuning, random):
    lag = max(0, tuning.reaction_delay_ticks) * 0.35
    lead_ticks = max(0.0, travel_ticks - lag)
    predicted = target.add(velocity.multiply(lead_ticks * tuning.prediction_strength))
    sloppiness = velocity.horizontal_length() * 0.35 * (1.0 - tuning.prediction_strength)
    if sloppiness <= 1e-6:
        return predicted
    off = Vec(aim_gaussian(random), aim_gaussian(random) * 0.5, aim_gaussian(random)).multiply(sloppiness)
    return predicted.add(off)


# --------------------------------------------------------------------------- #
# test runner (mirrors src/test/java/com/dragonbrain/core)
# --------------------------------------------------------------------------- #
FAILURES = []
CHECKS = [0]


def check(cond, msg):
    CHECKS[0] += 1
    if not cond:
        FAILURES.append(msg)
        print(f"FAIL: {msg}")


def eq(a, b, msg):
    check(a == b, f"{msg} (expected {b!r}, got {a!r})")


ALL_ATTACKS = list(AttackType)
NEW_ABILITIES = [AttackType.DREAD_ROAR, AttackType.VOID_RIFT, AttackType.METEOR_STORM, AttackType.GRAVITY_WELL]


def fast_tuning(cd_mult, min_dmg, fakeout, combo, hit_limit=1000):
    return DifficultyTuning(DifficultyMode.NORMAL, 5, 0, cd_mult, 0.05, 0.05, 2, min_dmg,
                            0.0, 0.5, 0.0, 0, 4.0, 1.0, hit_limit, 50, fakeout, combo)


def run_sched(sched, tuning, ticks, available):
    events = []
    for _ in range(ticks):
        r = sched.tick(tuning, True, 15.0, True, available)
        if r.action != Action.NONE:
            events.append(r)
    return events


def launches_of(events):
    return [r.attack for r in events if r.action in (Action.START_TELEGRAPH, Action.START_APPROACH)]


def test_boss_phase():
    f = BossPhase.for_health
    eq(f(1.0, 0.5, 0.25, 0.1), BossPhase.NORMAL, "hp 1.0 normal")
    eq(f(0.5, 0.5, 0.25, 0.1), BossPhase.RAGE, "hp .5 rage")
    eq(f(0.25, 0.5, 0.25, 0.1), BossPhase.FINAL, "hp .25 final")
    eq(f(0.11, 0.5, 0.25, 0.1), BossPhase.FINAL, "hp .11 final")
    eq(f(0.1, 0.5, 0.25, 0.1), BossPhase.CATACLYSM, "hp .1 cataclysm")
    hp = 0.1
    while hp >= 0.0:
        eq(f(hp, 0.5, 0.25, 0.1), BossPhase.CATACLYSM, "cataclysm stable")
        hp -= 0.01
    eq(f(0.02, 0.5, 0.25), BossPhase.FINAL, "disabled cataclysm -> final")
    check(BossPhase.CATACLYSM.is_at_least(BossPhase.FINAL), "ordering")
    eq(at_min_phase(AttackType.DREAD_ROAR), BossPhase.NORMAL, "roar phase")
    eq(at_min_phase(AttackType.VOID_RIFT), BossPhase.RAGE, "rift phase")
    eq(at_min_phase(AttackType.METEOR_STORM), BossPhase.RAGE, "meteor phase")
    eq(at_min_phase(AttackType.GRAVITY_WELL), BossPhase.FINAL, "well phase")
    check(not at_unlocked(AttackType.GRAVITY_WELL, BossPhase.RAGE), "well locked in rage")
    check(at_unlocked(AttackType.GRAVITY_WELL, BossPhase.CATACLYSM), "well open in cataclysm")


def test_tuning():
    cfg = DragonBrainConfig()
    for mode in (DifficultyMode.ROOKIE, DifficultyMode.NORMAL, DifficultyMode.EXPERT):
        t = tuning_create(mode, 0.5, cfg, BossPhase.NORMAL)
        for at in NEW_ABILITIES:
            tele = t.telegraph_ticks(at)
            check(tele >= 12, f"{mode.name} {at.name} telegraph {tele} >= 12")
            check(tele >= at_min_telegraph(at), f"{mode.name} {at.name} >= own floor")
    cfg2 = DragonBrainConfig()
    cfg2.warning_duration_multiplier = 0.5
    ex = tuning_create(DifficultyMode.EXPERT, 0.5, cfg2, BossPhase.CATACLYSM)
    eq(ex.telegraph_ticks(AttackType.VOID_RIFT), 16, "rift floored at 16")
    eq(ex.telegraph_ticks(AttackType.METEOR_STORM), 16, "meteor floored at 16")
    eq(ex.telegraph_ticks(AttackType.DREAD_ROAR), 14, "roar floored at 14")
    eq(ex.telegraph_ticks(AttackType.GRAVITY_WELL), 16, "well floored at 16")
    for at in ALL_ATTACKS:
        check(ex.recovery_ticks(at) >= 6, f"{at.name} recovery >= 6")
        check(ex.active_ticks(at) >= 4, f"{at.name} active >= 4")
        check(ex.cooldown_ticks(at) >= 1, f"{at.name} cooldown >= 1")
    cfg3 = DragonBrainConfig()
    cfg3.void_rift_cooldown_multiplier = 2.0
    cfg3.meteor_cooldown_multiplier = 0.5
    t3 = tuning_create(DifficultyMode.NORMAL, 0.5, cfg3, BossPhase.NORMAL)
    eq(t3.cooldown_ticks(AttackType.VOID_RIFT), 520, "rift cd x2")
    eq(t3.cooldown_ticks(AttackType.METEOR_STORM), 170, "meteor cd x0.5")
    eq(t3.cooldown_ticks(AttackType.DREAD_ROAR), 220, "roar cd plain")
    rookie_normal = tuning_create(DifficultyMode.ROOKIE, 0.5, DragonBrainConfig(), BossPhase.NORMAL)
    rookie_cata = tuning_create(DifficultyMode.ROOKIE, 0.5, DragonBrainConfig(), BossPhase.CATACLYSM)
    check(rookie_cata.cooldown_multiplier < rookie_normal.cooldown_multiplier, "cataclysm faster cooldowns")
    check(not rookie_normal.allow_combo, "no combo in normal phase")
    check(rookie_cata.allow_combo, "combo unlocked in cataclysm")
    cfg4 = DragonBrainConfig()
    cfg4.mechanic_combo_chains = False
    check(not tuning_create(DifficultyMode.ROOKIE, 0.5, cfg4, BossPhase.CATACLYSM).allow_combo, "combo config gate")
    check(not tuning_create(DifficultyMode.EXPERT, 0.5, DragonBrainConfig(), BossPhase.NORMAL).allow_combo, "expert no combo normal")
    check(tuning_create(DifficultyMode.EXPERT, 0.5, DragonBrainConfig(), BossPhase.FINAL).allow_combo, "expert combo final")
    check(not tuning_create(DifficultyMode.ROOKIE, 0.5, DragonBrainConfig(), BossPhase.FINAL).allow_combo, "rookie no combo final")
    cfg5 = DragonBrainConfig()
    cfg5.damage_multiplier = 0.0
    t5 = tuning_create(DifficultyMode.NORMAL, 0.5, cfg5, BossPhase.NORMAL)
    eq(t5.clamp_damage(10.0), 0.0, "zero damage multiplier")
    check(t5.clamp_damage(-5.0) >= 0.0, "damage never negative")


def test_config():
    legacy = json.dumps({
        "schemaVersion": 2, "enabled": True, "difficulty": "EXPERT", "damageMultiplier": 1.5,
        "mechanicFakeout": False, "rageHealthThreshold": 0.6, "finalPhaseHealthThreshold": 0.3,
        "voidPulseDamage": 9.0})
    cfg = DragonBrainConfig.from_json(legacy)
    eq(cfg.difficulty, "EXPERT", "legacy difficulty kept")
    eq(cfg.damage_multiplier, 1.5, "legacy damage kept")
    eq(cfg.mechanic_fakeout, False, "legacy fakeout kept")
    eq(cfg.rage_health_threshold, 0.6, "legacy rage threshold")
    check(cfg.mechanic_void_rift and cfg.mechanic_meteor_storm and cfg.mechanic_dread_roar
          and cfg.mechanic_gravity_well and cfg.mechanic_cataclysm_phase, "3.0 toggles default on")
    eq(cfg.cataclysm_health_threshold, 0.1, "cataclysm threshold default")
    eq(cfg.meteor_count, 7, "meteor count default")
    eq(cfg.schema_version, 3, "schema bumped to 3")

    empty = DragonBrainConfig.from_json("")
    eq(empty.difficulty, "NORMAL", "empty config defaults")
    check(len(empty.corrections) > 0, "empty config reports correction")
    broken = DragonBrainConfig.from_json("{not json!!")
    eq(broken.meteor_count, 7, "broken config defaults")
    check(len(broken.corrections) > 0, "broken config reports correction")

    wild = DragonBrainConfig.from_json(json.dumps({
        "meteorCount": 500, "meteorRadius": 40.0, "gravityWellPullStrength": 5.0,
        "voidRiftDamage": 900.0, "dreadRoarRadius": 1.0, "particleDensityMultiplier": 99.0,
        "voidRiftCooldownMultiplier": 0.01}))
    eq(wild.meteor_count, 16, "meteor count clamp")
    eq(wild.meteor_radius, 6.0, "meteor radius clamp")
    eq(wild.gravity_well_pull_strength, 0.35, "pull clamp (escapable)")
    eq(wild.void_rift_damage, 24.0, "rift damage clamp")
    eq(wild.dread_roar_radius, 6.0, "roar radius clamp")
    eq(wild.particle_density_multiplier, 2.0, "particle clamp")
    eq(wild.void_rift_cooldown_multiplier, 0.4, "rift cd clamp")

    order = DragonBrainConfig.from_json(json.dumps({
        "rageHealthThreshold": 0.5, "finalPhaseHealthThreshold": 0.25, "cataclysmHealthThreshold": 0.9}))
    check(order.cataclysm_health_threshold < order.final_phase_health_threshold < order.rage_health_threshold,
          "threshold ordering repaired")

    toggles = DragonBrainConfig()
    toggles.mechanic_meteor_storm = False
    toggles.mechanic_void_rift = False
    check(not toggles.mechanic_enabled(AttackType.METEOR_STORM), "meteor toggle off")
    check(not toggles.mechanic_enabled(AttackType.VOID_RIFT), "rift toggle off")
    check(toggles.mechanic_enabled(AttackType.DREAD_ROAR), "roar toggle on")
    toggles.gravity_well_cooldown_multiplier = 1.7
    eq(toggles.ability_cooldown_multiplier(AttackType.GRAVITY_WELL), 1.7, "well cd multiplier")
    eq(toggles.ability_cooldown_multiplier(AttackType.FIREBALL), 1.0, "default cd multiplier")

    nan_cfg = DragonBrainConfig()
    nan_cfg.meteor_damage = float("nan")
    nan_cfg.validate()
    check(not math.isnan(nan_cfg.meteor_damage), "NaN repaired")


def test_scheduler():
    # cooldown starts at selection and ticks down
    s = AttackScheduler(JavaRandom(1))
    t = fast_tuning(0.02, 0, 0.0, False)
    fb = [AttackType.FIREBALL]
    r = s.tick(t, True, 15.0, True, fb)
    eq(r.action, Action.START_TELEGRAPH, "first tick launches")
    eq(s.current, AttackType.FIREBALL, "current is fireball")
    eq(s.get_cooldown_ticks(AttackType.FIREBALL), 2, "cooldown = round(90*0.02)")
    s.tick(t, True, 15.0, True, fb)
    eq(s.get_cooldown_ticks(AttackType.FIREBALL), 1, "cooldown ticks down")
    s.tick(t, True, 15.0, True, fb)
    eq(s.get_cooldown_ticks(AttackType.FIREBALL), 0, "cooldown reaches 0")

    # cooldown spacing between launches
    s = AttackScheduler(JavaRandom(2))
    t = fast_tuning(1.0, 0, 0.0, False)
    launch_ticks = []
    for tick in range(400):
        if len(launch_ticks) >= 2:
            break
        r = s.tick(t, True, 15.0, True, fb)
        if r.action == Action.START_TELEGRAPH:
            launch_ticks.append(tick)
    eq(len(launch_ticks), 2, "two launches observed")
    check(launch_ticks[1] - launch_ticks[0] >= 90, "90-tick cooldown respected")

    # damage window
    s = AttackScheduler(JavaRandom(3))
    t = fast_tuning(0.02, 0, 0.0, False)
    check(not s.allows_damage(), "idle: no damage window")
    s.tick(t, True, 15.0, True, fb)
    check(not s.allows_damage(), "telegraph: no damage window")
    saw = False
    for _ in range(60):
        r = s.tick(t, True, 15.0, True, fb)
        if r.action == Action.START_ATTACK:
            check(s.allows_damage(), "attack: damage window open")
            saw = True
        if r.action == Action.ENTER_RECOVERY:
            check(not s.allows_damage(), "recovery: damage window closed")
            break
    check(saw, "attack window was reached")

    # min damage interval
    s = AttackScheduler(JavaRandom(4))
    ev = run_sched(s, fast_tuning(0.02, 2000, 0.0, False), 1500, ALL_ATTACKS)
    eq(s.stats.attacks_launched, 1, "min damage interval blocks second attack")
    eq(len(launches_of(ev)), 1, "one launch event")

    # phase gating in NORMAL
    s = AttackScheduler(JavaRandom(5))
    s.set_phase(BossPhase.NORMAL)
    launched = launches_of(run_sched(s, fast_tuning(0.02, 0, 0.0, False), 6000, ALL_ATTACKS))
    check(len(launched) > 20, f"fight happened ({len(launched)})")
    check(all(at_unlocked(a, BossPhase.NORMAL) for a in launched), "nothing phase-locked in NORMAL")

    # cataclysm unlocks
    s = AttackScheduler(JavaRandom(6))
    s.set_phase(BossPhase.CATACLYSM)
    only_new = launches_of(run_sched(s, fast_tuning(0.02, 0, 0.0, False), 400, NEW_ABILITIES))
    check(len(only_new) > 0, "new abilities launchable in cataclysm")
    check(all(a in NEW_ABILITIES for a in only_new), "only new abilities from new-only pool")
    s = AttackScheduler(JavaRandom(7))
    s.set_phase(BossPhase.CATACLYSM)
    mixed = launches_of(run_sched(s, fast_tuning(0.02, 0, 0.0, False), 6000, ALL_ATTACKS))
    check(AttackType.VOID_RIFT in mixed, "rift appears in long cataclysm")
    check(AttackType.GRAVITY_WELL in mixed, "well appears in long cataclysm")

    # disabled ability
    s = AttackScheduler(JavaRandom(8))
    s.set_phase(BossPhase.CATACLYSM)
    avail = [a for a in ALL_ATTACKS if a != AttackType.METEOR_STORM]
    launched = launches_of(run_sched(s, fast_tuning(0.02, 0, 0.0, False), 6000, avail))
    check(len(launched) > 20, "fight happened (disabled test)")
    check(AttackType.METEOR_STORM not in launched, "disabled meteor never chosen")

    # fakeouts respect phase locks
    s = AttackScheduler(JavaRandom(9))
    s.set_phase(BossPhase.NORMAL)
    ev = run_sched(s, fast_tuning(0.02, 0, 1.0, False), 8000, ALL_ATTACKS)
    check(s.stats.fakeouts > 0, "fakeouts happened")
    check(all(at_min_phase(r.attack) == BossPhase.NORMAL for r in ev if r.fakeout), "fakeouts stay in-phase")

    # combos: bounded, gated
    s = AttackScheduler(JavaRandom(10))
    s.set_phase(BossPhase.CATACLYSM)
    run_sched(s, fast_tuning(0.02, 1, 0.0, True), 8000, ALL_ATTACKS)
    check(s.stats.combos >= 1, f"combos occurred ({s.stats.combos})")
    check(2 * s.stats.combos <= s.stats.attacks_launched,
          f"chain cap: combos={s.stats.combos} launched={s.stats.attacks_launched}")
    s = AttackScheduler(JavaRandom(10))
    s.set_phase(BossPhase.CATACLYSM)
    run_sched(s, fast_tuning(0.02, 1, 0.0, False), 8000, ALL_ATTACKS)
    eq(s.stats.combos, 0, "no combos when disabled")
    s = AttackScheduler(JavaRandom(11))
    s.set_phase(BossPhase.CATACLYSM)
    run_sched(s, fast_tuning(0.02, 2000, 0.0, True), 1500, ALL_ATTACKS)
    eq(s.stats.combos, 0, "combo blocked by damage interval")
    eq(s.stats.attacks_launched, 1, "single attack under huge interval")

    # phase transition is one-shot
    s = AttackScheduler(JavaRandom(12))
    t = fast_tuning(0.02, 0, 0.0, False)
    s.begin_phase_transition()
    transitions = sum(1 for _ in range(200) if s.tick(t, True, 15.0, True, ALL_ATTACKS).action == Action.PHASE_TRANSITION)
    eq(transitions, 1, "phase transition fires exactly once")

    # interrupt windows
    s = AttackScheduler(JavaRandom(13))
    check(not s.interrupt(t), "no interrupt in idle")
    s.tick(t, True, 15.0, True, fb)
    eq(s.state, CombatState.TELEGRAPH, "in telegraph")
    check(s.interrupt(t), "interrupt works in telegraph")
    eq(s.state, CombatState.RECOVERY, "interrupt -> recovery")
    check(not s.interrupt(t), "no interrupt in recovery")

    # consecutive-hit pressure -> reposition (2.0 behavior preserved)
    s = AttackScheduler(JavaRandom(14))
    t = fast_tuning(0.02, 0, 0.0, False, 2)
    s.tick(t, True, 15.0, True, fb)
    repositioned = False
    for _ in range(80):
        if repositioned:
            break
        r = s.tick(t, True, 15.0, True, fb)
        if r.action == Action.START_ATTACK:
            s.on_damage_dealt(1.0)
            s.on_damage_dealt(1.0)
        if r.action == Action.ENTER_REPOSITION:
            repositioned = True
    check(repositioned, "pressure forces reposition")

    # no target -> no attacks
    s = AttackScheduler(JavaRandom(15))
    for _ in range(300):
        s.tick(fast_tuning(0.02, 0, 0.0, False), False, 0.0, False, ALL_ATTACKS)
    eq(s.stats.attacks_launched, 0, "no target, no attacks")
    eq(s.state, CombatState.IDLE, "idle without target")


def test_aim():
    eq(Vec(0, 0, 0).normalize().length(), 0.0, "zero normalize")
    eq(Vec(3, 4, 0).length(), 5.0, "length 3-4-5")
    d = Vec(1, 2, 3).normalize()
    out = aim_apply_error(d, 0.0, JavaRandom(1))
    check(abs(out.x - d.x) < 1e-9 and abs(out.y - d.y) < 1e-9 and abs(out.z - d.z) < 1e-9, "zero error aim")
    t = DifficultyTuning(DifficultyMode.NORMAL, 100, 0, 1.0, 1.0, 1.0, 8, 10, 0.0, 0.5, 0.0, 0, 1.0, 1.0, 3, 50, 0.0, False)
    p = aim_predict(Vec(0, 0, 0), Vec(10, 5, -3), Vec.ZERO, 20.0, t, JavaRandom(2))
    check(abs(p.x - 10) < 1e-9 and abs(p.y - 5) < 1e-9 and abs(p.z + 3) < 1e-9, "stationary prediction exact")
    t2 = DifficultyTuning(DifficultyMode.NORMAL, 100, 0, 1.0, 1.0, 1.0, 8, 10, 0.0, 1.0, 0.0, 0, 1.0, 1.0, 3, 50, 0.0, False)
    p2 = aim_predict(Vec(0, 0, 0), Vec(0, 0, 0), Vec(0.2, 0, 0), 20.0, t2, JavaRandom(3))
    check(2.0 < p2.x < 6.0, f"bounded lead ({p2.x:.2f})")
    rnd = JavaRandom(4)
    check(all(-2.0 <= aim_gaussian(rnd) <= 2.0 for _ in range(10000)), "gaussian clamped")


def main():
    test_boss_phase()
    test_tuning()
    test_config()
    test_scheduler()
    test_aim()
    print(f"\n{CHECKS[0]} checks, {len(FAILURES)} failures")
    if FAILURES:
        print("FAILED")
        return 1
    print("ALL CORE LOGIC CHECKS PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
