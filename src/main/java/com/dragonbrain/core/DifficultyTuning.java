package com.dragonbrain.core;

public record DifficultyTuning(
   DifficultyMode mode,
   int attackIntervalTicks,
   int attackIntervalJitterTicks,
   float cooldownMultiplier,
   float telegraphMultiplier,
   float recoveryMultiplier,
   int minTelegraphTicks,
   int minDamageIntervalTicks,
   float aimErrorDegrees,
   float predictionStrength,
   float projectileSpreadDegrees,
   int reactionDelayTicks,
   float projectileSpeed,
   float damageMultiplier,
   int consecutiveHitLimit,
   int hitPressureCooldownTicks,
   float fakeoutChance,
   boolean allowCombo
) {
   public int telegraphTicks(AttackType attack) {
      int base = Math.round(attack.baseTelegraphTicks() * this.telegraphMultiplier);
      return Math.max(this.minTelegraphTicks, base);
   }

   public int activeTicks(AttackType attack) {
      return Math.max(4, Math.round(attack.baseActiveTicks() / Math.max(0.5F, this.projectileSpeed)));
   }

   public int recoveryTicks(AttackType attack) {
      int base = Math.round(attack.baseActiveTicks() * 0.6F * this.recoveryMultiplier);
      return Math.max(6, base);
   }

   public int cooldownTicks(AttackType attack) {
      return Math.max(1, Math.round(attack.baseCooldownTicks() * this.cooldownMultiplier));
   }

   public float clampDamage(float rawDamage) {
      return Math.max(0.0F, rawDamage * this.damageMultiplier);
   }
}
