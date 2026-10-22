package com.dragonbrain.core;

public enum BossPhase {
   NORMAL,
   RAGE,
   FINAL;

   public static BossPhase forHealth(float healthRatio, float rageThreshold, float finalThreshold) {
      if (healthRatio <= finalThreshold) {
         return FINAL;
      } else {
         return healthRatio <= rageThreshold ? RAGE : NORMAL;
      }
   }
}
