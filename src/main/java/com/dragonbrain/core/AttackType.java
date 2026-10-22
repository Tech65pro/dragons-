package com.dragonbrain.core;

public enum AttackType {
   FIREBALL(true, 90, 12, 26, 1.0F),
   DIVE(true, 140, 22, 30, 1.25F),
   WING_GUST(true, 120, 20, 18, 0.55F),
   VOID_PULSE(true, 170, 30, 34, 1.0F),
   REPOSITION(false, 60, 0, 24, 0.0F),
   HOVER(false, 80, 0, 30, 0.0F);

   private final boolean damaging;
   private final int baseCooldownTicks;
   private final int baseTelegraphTicks;
   private final int baseActiveTicks;
   private final float pressureWeight;

   AttackType(boolean damaging, int baseCooldownTicks, int baseTelegraphTicks, int baseActiveTicks, float pressureWeight) {
      this.damaging = damaging;
      this.baseCooldownTicks = baseCooldownTicks;
      this.baseTelegraphTicks = baseTelegraphTicks;
      this.baseActiveTicks = baseActiveTicks;
      this.pressureWeight = pressureWeight;
   }

   public boolean isDamaging() {
      return this.damaging;
   }

   public int baseCooldownTicks() {
      return this.baseCooldownTicks;
   }

   public int baseTelegraphTicks() {
      return this.baseTelegraphTicks;
   }

   public int baseActiveTicks() {
      return this.baseActiveTicks;
   }

   public float pressureWeight() {
      return this.pressureWeight;
   }

   public boolean isOffensive() {
      return this == FIREBALL || this == DIVE || this == VOID_PULSE || this == WING_GUST;
   }
}
