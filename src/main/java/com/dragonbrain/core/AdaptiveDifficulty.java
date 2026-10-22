package com.dragonbrain.core;

public final class AdaptiveDifficulty {
   public static final int WINDOW_TICKS = 100;
   public static final float MAX_DELTA_PER_UPDATE = 0.02F;
   public static final float DEADBAND = 0.05F;
   private static final float SMOOTHING = 0.25F;
   private float value = 0.5F;
   private float smoothedTarget = 0.5F;
   private int ticksInWindow;
   private int launched;
   private int landed;
   private int hitsTakenByPlayers;
   private float damageToBoss;
   private float playerDamageAccumulator;
   private final float maxHealth;

   public AdaptiveDifficulty(float maxHealth) {
      this.maxHealth = Math.max(1.0F, maxHealth);
   }

   public void onAttackLaunched() {
      this.launched++;
   }

   public void onAttackLanded() {
      this.landed++;
   }

   public void onPlayerDamaged(float amount) {
      this.hitsTakenByPlayers++;
      this.playerDamageAccumulator = this.playerDamageAccumulator + Math.max(0.0F, amount);
   }

   public void onBossDamaged(float amount) {
      this.damageToBoss = this.damageToBoss + Math.max(0.0F, amount);
   }

   public void tick() {
      this.ticksInWindow++;
      if (this.ticksInWindow >= 100) {
         this.evaluate();
      }
   }

   private void evaluate() {
      float windows = this.ticksInWindow / 100.0F;
      float dodgeRatio = this.launched == 0 ? 0.6F : TuningFactory.clamp01(1.0F - (float)this.landed / this.launched);
      float hitsPerWindow = this.hitsTakenByPlayers / Math.max(1.0F, windows);
      float pressureScore = TuningFactory.clamp01(1.0F - hitsPerWindow / 6.0F);
      float progress = TuningFactory.clamp01(this.damageToBoss / Math.max(1.0F, this.maxHealth) * 8.0F / Math.max(1.0F, windows));
      float raw = 0.5F * dodgeRatio + 0.3F * pressureScore + 0.2F * progress;
      float target = TuningFactory.clamp01((raw - 0.35F) / 0.45F);
      this.smoothedTarget = this.smoothedTarget + (target - this.smoothedTarget) * 0.25F;
      float delta = this.smoothedTarget - this.value;
      if (Math.abs(delta) < 0.05F) {
         delta = 0.0F;
      }

      delta = Math.max(-0.02F, Math.min(0.02F, delta));
      this.value = TuningFactory.clamp01(this.value + delta);
      this.ticksInWindow = 0;
      this.launched = 0;
      this.landed = 0;
      this.hitsTakenByPlayers = 0;
      this.damageToBoss = 0.0F;
      this.playerDamageAccumulator = 0.0F;
   }

   public float value() {
      return this.value;
   }

   public float target() {
      return this.smoothedTarget;
   }

   public String band() {
      if (this.value < 0.33F) {
         return "rookie-leaning";
      } else {
         return this.value > 0.66F ? "expert-leaning" : "balanced";
      }
   }

   public void setValue(float newValue) {
      this.value = TuningFactory.clamp01(newValue);
      this.smoothedTarget = this.value;
   }

   public void reset() {
      this.value = 0.5F;
      this.smoothedTarget = 0.5F;
      this.ticksInWindow = 0;
      this.launched = 0;
      this.landed = 0;
      this.hitsTakenByPlayers = 0;
      this.damageToBoss = 0.0F;
      this.playerDamageAccumulator = 0.0F;
   }
}
