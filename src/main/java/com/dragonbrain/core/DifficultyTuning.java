package com.dragonbrain.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

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
   boolean allowCombo,
   Map<AttackType, Float> abilityCooldownScales
) {
   public DifficultyTuning {
      EnumMap<AttackType, Float> copy = new EnumMap(AttackType.class);
      if (abilityCooldownScales != null) {
         copy.putAll(abilityCooldownScales);
      }
      abilityCooldownScales = Collections.unmodifiableMap(copy);
   }

   /** DragonBrain 2.0-compatible constructor (no per-ability cooldown scales). */
   public DifficultyTuning(
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
      this(
         mode,
         attackIntervalTicks,
         attackIntervalJitterTicks,
         cooldownMultiplier,
         telegraphMultiplier,
         recoveryMultiplier,
         minTelegraphTicks,
         minDamageIntervalTicks,
         aimErrorDegrees,
         predictionStrength,
         projectileSpreadDegrees,
         reactionDelayTicks,
         projectileSpeed,
         damageMultiplier,
         consecutiveHitLimit,
         hitPressureCooldownTicks,
         fakeoutChance,
         allowCombo,
         Map.of()
      );
   }
   public int telegraphTicks(AttackType attack) {
      int base = Math.round(attack.baseTelegraphTicks() * this.telegraphMultiplier);
      // DragonBrain 3.0: major attacks carry their own readability floor on top
      // of the per-difficulty minimum, so no configuration can shrink them
      // below their designed warning window.
      return Math.max(Math.max(this.minTelegraphTicks, attack.minTelegraphTicks()), base);
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
