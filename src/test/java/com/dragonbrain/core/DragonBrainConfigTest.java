package com.dragonbrain.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DragonBrainConfigTest {
   /** A representative DragonBrain 2.0 config file (schema 2, no 3.0 keys). */
   private static final String LEGACY_2_0_JSON = """
      {
        "schemaVersion": 2,
        "enabled": true,
        "difficulty": "EXPERT",
        "damageMultiplier": 1.5,
        "mechanicFakeout": false,
        "rageHealthThreshold": 0.6,
        "finalPhaseHealthThreshold": 0.3,
        "voidPulseDamage": 9.0
      }
      """;

   @Test
   void legacy20ConfigLoadsWithDefaultsForNewOptions() {
      DragonBrainConfig config = DragonBrainConfig.fromJson(LEGACY_2_0_JSON);
      // 2.0 values preserved
      assertEquals("EXPERT", config.difficulty);
      assertEquals(1.5F, config.damageMultiplier, 0.0001F);
      assertFalse(config.mechanicFakeout);
      assertEquals(0.6F, config.rageHealthThreshold, 0.0001F);
      assertEquals(0.3F, config.finalPhaseHealthThreshold, 0.0001F);
      assertEquals(9.0F, config.voidPulseDamage, 0.0001F);
      // 3.0 options receive safe defaults
      assertTrue(config.mechanicVoidRift);
      assertTrue(config.mechanicMeteorStorm);
      assertTrue(config.mechanicDreadRoar);
      assertTrue(config.mechanicGravityWell);
      assertTrue(config.mechanicCataclysmPhase);
      assertEquals(0.1F, config.cataclysmHealthThreshold, 0.0001F);
      assertEquals(7, config.meteorCount);
      assertEquals(1.0F, config.particleDensityMultiplier, 0.0001F);
      // and the file is stamped with the current schema on save
      assertEquals(3, config.schemaVersion);
   }

   @Test
   void malformedJsonFallsBackToDefaults() {
      DragonBrainConfig empty = DragonBrainConfig.fromJson("");
      assertEquals("NORMAL", empty.difficulty);
      assertFalse(empty.corrections.isEmpty());
      DragonBrainConfig broken = DragonBrainConfig.fromJson("{not json!!");
      assertEquals(7, broken.meteorCount);
      assertFalse(broken.corrections.isEmpty());
   }

   @Test
   void dangerousValuesAreClampedToFairRanges() {
      DragonBrainConfig config = DragonBrainConfig.fromJson("""
         {
           "meteorCount": 500,
           "meteorRadius": 40.0,
           "gravityWellPullStrength": 5.0,
           "voidRiftDamage": 900.0,
           "dreadRoarRadius": 1.0,
           "particleDensityMultiplier": 99.0,
           "voidRiftCooldownMultiplier": 0.01
         }
         """);
      assertEquals(16, config.meteorCount);
      assertEquals(6.0F, config.meteorRadius, 0.0001F);
      assertEquals(0.35F, config.gravityWellPullStrength, 0.0001F);
      assertEquals(24.0F, config.voidRiftDamage, 0.0001F);
      assertEquals(6.0F, config.dreadRoarRadius, 0.0001F);
      assertEquals(2.0F, config.particleDensityMultiplier, 0.0001F);
      assertEquals(0.4F, config.voidRiftCooldownMultiplier, 0.0001F);
      assertFalse(config.corrections.isEmpty());
   }

   @Test
   void thresholdOrderingIsRepaired() {
      DragonBrainConfig config = DragonBrainConfig.fromJson("""
         {
           "rageHealthThreshold": 0.5,
           "finalPhaseHealthThreshold": 0.25,
           "cataclysmHealthThreshold": 0.9
         }
         """);
      assertTrue(config.cataclysmHealthThreshold < config.finalPhaseHealthThreshold);
      assertTrue(config.finalPhaseHealthThreshold < config.rageHealthThreshold);
   }

   @Test
   void newMechanicTogglesAndCooldownsReachTheCore() {
      DragonBrainConfig config = DragonBrainConfig.defaults();
      config.mechanicMeteorStorm = false;
      config.mechanicVoidRift = false;
      assertFalse(config.mechanicEnabled(AttackType.METEOR_STORM));
      assertFalse(config.mechanicEnabled(AttackType.VOID_RIFT));
      assertTrue(config.mechanicEnabled(AttackType.DREAD_ROAR));
      assertTrue(config.mechanicEnabled(AttackType.GRAVITY_WELL));
      assertTrue(config.mechanicEnabled(AttackType.FIREBALL));

      config.gravityWellCooldownMultiplier = 1.7F;
      assertEquals(1.7F, config.abilityCooldownMultiplier(AttackType.GRAVITY_WELL), 0.0001F);
      assertEquals(1.0F, config.abilityCooldownMultiplier(AttackType.FIREBALL), 0.0001F);
   }

   @Test
   void defaultsRoundTripThroughJson() {
      DragonBrainConfig defaults = DragonBrainConfig.defaults();
      String json = defaults.toJson();
      assertTrue(json.contains("\"cataclysmHealthThreshold\""));
      assertTrue(json.contains("\"mechanicMeteorStorm\""));
      DragonBrainConfig back = DragonBrainConfig.fromJson(json);
      assertEquals(defaults.meteorCount, back.meteorCount);
      assertEquals(defaults.cataclysmHealthThreshold, back.cataclysmHealthThreshold, 0.0001F);
      assertEquals(defaults.gravityWellRadius, back.gravityWellRadius, 0.0001F);
      assertTrue(back.corrections.isEmpty());
   }

   @Test
   void nanValuesAreRepaired() {
      DragonBrainConfig config = DragonBrainConfig.defaults();
      config.meteorDamage = Float.NaN;
      config.validate();
      assertFalse(Float.isNaN(config.meteorDamage));
   }
}
