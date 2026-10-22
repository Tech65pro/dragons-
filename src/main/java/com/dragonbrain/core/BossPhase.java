package com.dragonbrain.core;

public enum BossPhase {
   NORMAL,
   RAGE,
   FINAL,
   CATACLYSM;

   public static BossPhase forHealth(float healthRatio, float rageThreshold, float finalThreshold, float cataclysmThreshold) {
      if (healthRatio <= cataclysmThreshold) {
         return CATACLYSM;
      } else if (healthRatio <= finalThreshold) {
         return FINAL;
      } else {
         return healthRatio <= rageThreshold ? RAGE : NORMAL;
      }
   }

   /** Kept for compatibility with DragonBrain 2.0 callers/tests: no Cataclysm phase. */
   public static BossPhase forHealth(float healthRatio, float rageThreshold, float finalThreshold) {
      return forHealth(healthRatio, rageThreshold, finalThreshold, -1.0F);
   }

   /** True when this phase is at least as advanced as {@code other} (NORMAL &lt; RAGE &lt; FINAL &lt; CATACLYSM). */
   public boolean isAtLeast(BossPhase other) {
      return this.ordinal() >= other.ordinal();
   }
}
