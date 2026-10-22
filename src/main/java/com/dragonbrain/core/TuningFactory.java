package com.dragonbrain.core;

public final class TuningFactory {
   public static final int MIN_TELEGRAPH_FLOOR = 8;
   private static final TuningFactory.Knobs ROOKIE = new TuningFactory.Knobs(
      150, 40, 1.55F, 1.55F, 1.7F, 14, 26, 9.0F, 0.3F, 8.0F, 14, 0.9F, 0.6F, 2, 120, 0.0F, false
   );
   private static final TuningFactory.Knobs NORMAL = new TuningFactory.Knobs(
      105, 30, 1.0F, 1.0F, 1.0F, 10, 16, 5.5F, 0.55F, 5.0F, 8, 1.0F, 1.0F, 3, 70, 0.12F, false
   );
   private static final TuningFactory.Knobs EXPERT = new TuningFactory.Knobs(
      75, 20, 0.78F, 0.8F, 0.75F, 9, 12, 2.75F, 0.82F, 3.0F, 4, 1.15F, 1.25F, 3, 50, 0.3F, true
   );

   private TuningFactory() {
   }

   public static DifficultyTuning create(DifficultyMode mode, float adaptiveness, TuningFactory.ConfigView config, BossPhase phase) {
      TuningFactory.Knobs base;
      if (mode == DifficultyMode.ADAPTIVE) {
         float t = clamp01(adaptiveness);
         base = lerp(ROOKIE, EXPERT, t);
      } else {
         base = switch (mode) {
            case ROOKIE -> ROOKIE;
            case EXPERT -> EXPERT;
            default -> NORMAL;
         };
      }

      return applyConfig(base, mode, config, phase);
   }

   private static DifficultyTuning applyConfig(TuningFactory.Knobs base, DifficultyMode mode, TuningFactory.ConfigView config, BossPhase phase) {
      float phaseCooldown = switch (phase) {
         case RAGE -> 0.85F;
         case FINAL -> 0.72F;
         case NORMAL -> 1.0F;
      };

      float phaseRecovery = switch (phase) {
         case RAGE -> 0.9F;
         case FINAL -> 0.8F;
         case NORMAL -> 1.0F;
      };
      boolean combos = base.combos() && phase != BossPhase.NORMAL && config.mechanicComboChains();
      return new DifficultyTuning(
         mode,
         scaleTicks(base.attackInterval(), config.attackFrequencyMultiplier()),
         Math.max(0, Math.round(base.intervalJitter() * config.attackFrequencyMultiplier())),
         base.cooldown() * config.cooldownMultiplier() * phaseCooldown,
         base.telegraph() * config.warningDurationMultiplier(),
         base.recovery() * config.recoveryDurationMultiplier() * phaseRecovery,
         Math.max(8, base.minTelegraph()),
         Math.max(4, Math.round(base.minDamageInterval())),
         base.aimError() / Math.max(0.1F, config.aimAccuracyMultiplier()),
         clamp01(base.prediction() * config.predictionStrengthMultiplier()),
         base.spread() / Math.max(0.1F, config.aimAccuracyMultiplier()),
         base.reactionDelay() + config.reactionDelayTicks(),
         Math.max(0.5F, base.projectileSpeed() * config.projectileSpeedMultiplier()),
         base.damage() * config.damageMultiplier(),
         Math.max(1, base.consecutiveHits()),
         Math.max(10, Math.round(base.pressureCooldown() * config.recoveryDurationMultiplier())),
         clamp01(base.fakeoutChance()),
         combos
      );
   }

   private static int scaleTicks(int value, float multiplier) {
      return Math.max(20, Math.round(value * multiplier));
   }

   private static TuningFactory.Knobs lerp(TuningFactory.Knobs a, TuningFactory.Knobs b, float t) {
      return new TuningFactory.Knobs(
         lerpI(a.attackInterval(), b.attackInterval(), t),
         lerpI(a.intervalJitter(), b.intervalJitter(), t),
         lerpF(a.cooldown(), b.cooldown(), t),
         lerpF(a.telegraph(), b.telegraph(), t),
         lerpF(a.recovery(), b.recovery(), t),
         lerpI(a.minTelegraph(), b.minTelegraph(), t),
         lerpI(a.minDamageInterval(), b.minDamageInterval(), t),
         lerpF(a.aimError(), b.aimError(), t),
         lerpF(a.prediction(), b.prediction(), t),
         lerpF(a.spread(), b.spread(), t),
         lerpI(a.reactionDelay(), b.reactionDelay(), t),
         lerpF(a.projectileSpeed(), b.projectileSpeed(), t),
         lerpF(a.damage(), b.damage(), t),
         lerpI(a.consecutiveHits(), b.consecutiveHits(), t),
         lerpI(a.pressureCooldown(), b.pressureCooldown(), t),
         lerpF(a.fakeoutChance(), b.fakeoutChance(), t),
         t > 0.5F
      );
   }

   private static int lerpI(int a, int b, float t) {
      return Math.round(a + (b - a) * t);
   }

   private static float lerpF(float a, float b, float t) {
      return a + (b - a) * t;
   }

   public static float clamp01(float value) {
      return value < 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
   }

   public interface ConfigView {
      float attackFrequencyMultiplier();

      float cooldownMultiplier();

      float warningDurationMultiplier();

      float recoveryDurationMultiplier();

      float aimAccuracyMultiplier();

      float predictionStrengthMultiplier();

      float projectileSpeedMultiplier();

      float damageMultiplier();

      int reactionDelayTicks();

      boolean mechanicComboChains();
   }

   private static final class DifficultyTuningDefaults {
      static final int EXPERT_MIN_TELEGRAPH = 9;
   }

   private record Knobs(
      int attackInterval,
      int intervalJitter,
      float cooldown,
      float telegraph,
      float recovery,
      int minTelegraph,
      int minDamageInterval,
      float aimError,
      float prediction,
      float spread,
      int reactionDelay,
      float projectileSpeed,
      float damage,
      int consecutiveHits,
      int pressureCooldown,
      float fakeoutChance,
      boolean combos
   ) {
   }
}
