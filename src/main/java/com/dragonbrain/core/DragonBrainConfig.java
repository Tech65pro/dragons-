package com.dragonbrain.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.List;

public final class DragonBrainConfig implements TuningFactory.ConfigView {
   // Schema history: 2 = DragonBrain 2.0, 3 = DragonBrain 3.0 (Abyssal Cataclysm).
   // Older files keep loading: Gson runs the field initializers for keys that
   // are missing from the JSON, so every 3.0 option below simply receives its
   // default when a 2.0 config is read.
   public static final int SCHEMA_VERSION = 3;
   public transient List<String> corrections = new ArrayList();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
   public int schemaVersion = 3;
   public boolean enabled = true;
   public String difficulty = "NORMAL";
   public boolean debug = false;
   public boolean debugTargetAnyLivingEntity = false;
   public float attackFrequencyMultiplier = 1.0F;
   public float cooldownMultiplier = 1.0F;
   public float warningDurationMultiplier = 1.0F;
   public float recoveryDurationMultiplier = 1.0F;
   public float projectileSpeedMultiplier = 1.0F;
   public float aimAccuracyMultiplier = 1.0F;
   public float predictionStrengthMultiplier = 1.0F;
   public int reactionDelayTicks = 0;
   public float damageMultiplier = 1.0F;
   public boolean enforceMinimumDamageInterval = true;
   public boolean consecutiveHitProtection = true;
   public boolean mechanicDive = true;
   public boolean mechanicWingGust = true;
   public boolean mechanicVoidPulse = true;
   public boolean mechanicCrystalOvercharge = true;
   public boolean mechanicStaggerWindow = true;
   public boolean mechanicRagePhase = true;
   public boolean mechanicFakeout = true;
   public boolean mechanicFinalPhase = true;
   public boolean mechanicVictorySequence = true;
   public boolean mechanicComboChains = true;
   public boolean preserveVanillaPerching = true;
   public boolean crystalOverchargeExtraFireball = true;
   public float wingGustDamage = 2.0F;
   public float voidPulseDamage = 6.0F;
   public float rageHealthThreshold = 0.5F;
   public float finalPhaseHealthThreshold = 0.25F;
   public boolean adaptiveDifficulty = true;
   public boolean customTexture = true;
   public boolean emissiveEyes = true;
   public boolean customSounds = true;
   public boolean bossMusic = true;
   public boolean ambientEndAudio = true;
   public boolean ambientParticles = true;
   public float soundVolume = 1.0F;
   public float musicVolume = 0.8F;
   public float ambientVolume = 0.6F;

   public static DragonBrainConfig defaults() {
      return new DragonBrainConfig();
   }

   public static DragonBrainConfig fromJson(String json) {
      DragonBrainConfig config;
      try {
         config = (DragonBrainConfig)GSON.fromJson(json, DragonBrainConfig.class);
      } catch (JsonSyntaxException ex) {
         config = null;
      }

      boolean malformed = config == null;
      if (malformed) {
         config = defaults();
      }

      config.validate();
      if (malformed) {
         config.corrections.add(0, "Config file was empty or malformed; defaults were restored.");
      }

      return config;
   }

   public String toJson() {
      return GSON.toJson(this);
   }

   public DragonBrainConfig copy() {
      return fromJson(this.toJson());
   }

   public List<String> validate() {
      List<String> fixes = new ArrayList();
      this.schemaVersion = 2;
      DifficultyMode mode = DifficultyMode.parse(this.difficulty, DifficultyMode.NORMAL);
      if (!mode.id().equalsIgnoreCase(this.difficulty == null ? "" : this.difficulty.trim())) {
         fixes.add("difficulty '" + this.difficulty + "' is not valid; using NORMAL.");
      }

      this.difficulty = mode.name();
      this.attackFrequencyMultiplier = clamp(fixes, "attackFrequencyMultiplier", this.attackFrequencyMultiplier, 0.4F, 3.0F);
      this.cooldownMultiplier = clamp(fixes, "cooldownMultiplier", this.cooldownMultiplier, 0.4F, 3.0F);
      this.warningDurationMultiplier = clamp(fixes, "warningDurationMultiplier", this.warningDurationMultiplier, 0.5F, 3.0F);
      this.recoveryDurationMultiplier = clamp(fixes, "recoveryDurationMultiplier", this.recoveryDurationMultiplier, 0.5F, 3.0F);
      this.projectileSpeedMultiplier = clamp(fixes, "projectileSpeedMultiplier", this.projectileSpeedMultiplier, 0.5F, 2.0F);
      this.aimAccuracyMultiplier = clamp(fixes, "aimAccuracyMultiplier", this.aimAccuracyMultiplier, 0.25F, 3.0F);
      this.predictionStrengthMultiplier = clamp(fixes, "predictionStrengthMultiplier", this.predictionStrengthMultiplier, 0.0F, 2.0F);
      this.damageMultiplier = clamp(fixes, "damageMultiplier", this.damageMultiplier, 0.0F, 3.0F);
      this.soundVolume = clamp(fixes, "soundVolume", this.soundVolume, 0.0F, 2.0F);
      this.musicVolume = clamp(fixes, "musicVolume", this.musicVolume, 0.0F, 2.0F);
      this.ambientVolume = clamp(fixes, "ambientVolume", this.ambientVolume, 0.0F, 2.0F);
      if (this.reactionDelayTicks < 0 || this.reactionDelayTicks > 40) {
         fixes.add("reactionDelayTicks " + this.reactionDelayTicks + " out of range 0..40; clamped.");
         this.reactionDelayTicks = Math.max(0, Math.min(40, this.reactionDelayTicks));
      }

      this.wingGustDamage = clamp(fixes, "wingGustDamage", this.wingGustDamage, 0.0F, 12.0F);
      this.voidPulseDamage = clamp(fixes, "voidPulseDamage", this.voidPulseDamage, 0.0F, 20.0F);
      this.rageHealthThreshold = clamp(fixes, "rageHealthThreshold", this.rageHealthThreshold, 0.05F, 0.95F);
      this.finalPhaseHealthThreshold = clamp(fixes, "finalPhaseHealthThreshold", this.finalPhaseHealthThreshold, 0.02F, this.rageHealthThreshold);
      if (this.finalPhaseHealthThreshold >= this.rageHealthThreshold) {
         fixes.add("finalPhaseHealthThreshold must be lower than rageHealthThreshold; adjusted.");
         this.finalPhaseHealthThreshold = this.rageHealthThreshold * 0.5F;
      }

      // DragonBrain 3.0 options. All ranges are fairness clamps: damage caps keep
      // every telegraphed hit survivable, the pull cap keeps the Gravity Well
      // escapable on foot, and the meteor cap bounds entities/particles.
      this.cataclysmHealthThreshold = clamp(fixes, "cataclysmHealthThreshold", this.cataclysmHealthThreshold, 0.01F, this.finalPhaseHealthThreshold);
      if (this.cataclysmHealthThreshold >= this.finalPhaseHealthThreshold) {
         fixes.add("cataclysmHealthThreshold must be lower than finalPhaseHealthThreshold; adjusted.");
         this.cataclysmHealthThreshold = this.finalPhaseHealthThreshold * 0.5F;
      }

      this.voidRiftDamage = clamp(fixes, "voidRiftDamage", this.voidRiftDamage, 0.0F, 24.0F);
      this.voidRiftCooldownMultiplier = clamp(fixes, "voidRiftCooldownMultiplier", this.voidRiftCooldownMultiplier, 0.4F, 3.0F);
      this.meteorDamage = clamp(fixes, "meteorDamage", this.meteorDamage, 0.0F, 24.0F);
      this.meteorRadius = clamp(fixes, "meteorRadius", this.meteorRadius, 1.0F, 6.0F);
      this.meteorCooldownMultiplier = clamp(fixes, "meteorCooldownMultiplier", this.meteorCooldownMultiplier, 0.4F, 3.0F);
      if (this.meteorCount < 1 || this.meteorCount > 16) {
         fixes.add("meteorCount " + this.meteorCount + " out of range 1..16; clamped.");
         this.meteorCount = Math.max(1, Math.min(16, this.meteorCount));
      }

      this.dreadRoarDamage = clamp(fixes, "dreadRoarDamage", this.dreadRoarDamage, 0.0F, 16.0F);
      this.dreadRoarRadius = clamp(fixes, "dreadRoarRadius", this.dreadRoarRadius, 6.0F, 32.0F);
      this.dreadRoarCooldownMultiplier = clamp(fixes, "dreadRoarCooldownMultiplier", this.dreadRoarCooldownMultiplier, 0.4F, 3.0F);
      this.gravityWellDamage = clamp(fixes, "gravityWellDamage", this.gravityWellDamage, 0.0F, 20.0F);
      this.gravityWellRadius = clamp(fixes, "gravityWellRadius", this.gravityWellRadius, 5.0F, 24.0F);
      this.gravityWellPullStrength = clamp(fixes, "gravityWellPullStrength", this.gravityWellPullStrength, 0.0F, 0.35F);
      this.gravityWellCooldownMultiplier = clamp(fixes, "gravityWellCooldownMultiplier", this.gravityWellCooldownMultiplier, 0.4F, 3.0F);
      this.particleDensityMultiplier = clamp(fixes, "particleDensityMultiplier", this.particleDensityMultiplier, 0.25F, 2.0F);

      this.corrections = fixes;
      return fixes;
   }

   private static float clamp(List<String> fixes, String name, float value, float min, float max) {
      if (Float.isNaN(value)) {
         fixes.add(name + " was NaN; using " + min + ".");
         return min;
      }

      float clamped = Math.max(min, Math.min(max, value));
      if (Math.abs(clamped - value) > 1.0E-4F) {
         fixes.add(name + " " + value + " out of range " + min + ".." + max + "; clamped to " + clamped + ".");
      }

      return clamped;
   }

   public DifficultyMode mode() {
      return DifficultyMode.parse(this.difficulty, DifficultyMode.NORMAL);
   }

   public boolean mechanicEnabled(AttackType type) {
      return switch (type) {
         case FIREBALL -> true;
         case DIVE -> this.mechanicDive;
         case WING_GUST -> this.mechanicWingGust;
         case VOID_PULSE -> this.mechanicVoidPulse;
         case REPOSITION -> true;
         case HOVER -> true;
      };
   }

   @Override
   public float attackFrequencyMultiplier() {
      return this.attackFrequencyMultiplier;
   }

   @Override
   public float cooldownMultiplier() {
      return this.cooldownMultiplier;
   }

   @Override
   public float warningDurationMultiplier() {
      return this.warningDurationMultiplier;
   }

   @Override
   public float recoveryDurationMultiplier() {
      return this.recoveryDurationMultiplier;
   }

   @Override
   public float aimAccuracyMultiplier() {
      return this.aimAccuracyMultiplier;
   }

   @Override
   public float predictionStrengthMultiplier() {
      return this.predictionStrengthMultiplier;
   }

   @Override
   public float projectileSpeedMultiplier() {
      return this.projectileSpeedMultiplier;
   }

   @Override
   public float damageMultiplier() {
      return this.damageMultiplier;
   }

   @Override
   public int reactionDelayTicks() {
      return this.reactionDelayTicks;
   }

   @Override
   public boolean mechanicComboChains() {
      return this.mechanicComboChains;
   }
}
