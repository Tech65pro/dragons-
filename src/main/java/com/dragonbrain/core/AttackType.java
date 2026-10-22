package com.dragonbrain.core;

public enum AttackType {
   FIREBALL(true, 90, 12, 26, 1.0F, 0, BossPhase.NORMAL),
   DIVE(true, 140, 22, 30, 1.25F, 0, BossPhase.NORMAL),
   WING_GUST(true, 120, 20, 18, 0.55F, 0, BossPhase.NORMAL),
   VOID_PULSE(true, 170, 30, 34, 1.0F, 0, BossPhase.NORMAL),
   // DragonBrain 3.0 abilities. Their per-attack telegraph floors keep every
   // difficulty at or above the 12-18 tick readability window even after the
   // difficulty telegraph multiplier is applied.
   DREAD_ROAR(true, 220, 26, 8, 0.8F, 14, BossPhase.NORMAL),
   VOID_RIFT(true, 260, 30, 10, 1.1F, 16, BossPhase.RAGE),
   METEOR_STORM(true, 340, 32, 90, 1.2F, 16, BossPhase.RAGE),
   GRAVITY_WELL(true, 320, 30, 70, 0.9F, 16, BossPhase.FINAL),
   REPOSITION(false, 60, 0, 24, 0.0F, 0, BossPhase.NORMAL),
   HOVER(false, 80, 0, 30, 0.0F, 0, BossPhase.NORMAL);

   private final boolean damaging;
   private final int baseCooldownTicks;
   private final int baseTelegraphTicks;
   private final int baseActiveTicks;
   private final float pressureWeight;
   private final int minTelegraphTicks;
   private final BossPhase minPhase;

   AttackType(
      boolean damaging,
      int baseCooldownTicks,
      int baseTelegraphTicks,
      int baseActiveTicks,
      float pressureWeight,
      int minTelegraphTicks,
      BossPhase minPhase
   ) {
      this.damaging = damaging;
      this.baseCooldownTicks = baseCooldownTicks;
      this.baseTelegraphTicks = baseTelegraphTicks;
      this.baseActiveTicks = baseActiveTicks;
      this.pressureWeight = pressureWeight;
      this.minTelegraphTicks = minTelegraphTicks;
      this.minPhase = minPhase;
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

   /** Hard floor for this attack's telegraph, applied on top of the difficulty floor. 0 = none. */
   public int minTelegraphTicks() {
      return this.minTelegraphTicks;
   }

   /** First boss phase in which the scheduler may voluntarily pick this attack. */
   public BossPhase minPhase() {
      return this.minPhase;
   }

   public boolean isUnlockedIn(BossPhase phase) {
      return phase.isAtLeast(this.minPhase);
   }

   public boolean isOffensive() {
      return this == FIREBALL
         || this == DIVE
         || this == VOID_PULSE
         || this == WING_GUST
         || this == DREAD_ROAR
         || this == VOID_RIFT
         || this == METEOR_STORM
         || this == GRAVITY_WELL;
   }
}
