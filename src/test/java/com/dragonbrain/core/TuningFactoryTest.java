package com.dragonbrain.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TuningFactoryTest {
   private static DifficultyTuning tuning(DifficultyMode mode, DragonBrainConfig config, BossPhase phase) {
      return TuningFactory.create(mode, 0.5F, config, phase);
   }

   @Test
   void newMajorAttacksKeepReadableTelegraphsOnEveryDifficulty() {
      DragonBrainConfig config = DragonBrainConfig.defaults();
      for (DifficultyMode mode : new DifficultyMode[]{DifficultyMode.ROOKIE, DifficultyMode.NORMAL, DifficultyMode.EXPERT}) {
         DifficultyTuning t = tuning(mode, config, BossPhase.NORMAL);
         for (AttackType attack : new AttackType[]{AttackType.VOID_RIFT, AttackType.METEOR_STORM, AttackType.DREAD_ROAR, AttackType.GRAVITY_WELL}) {
            int telegraph = t.telegraphTicks(attack);
            assertTrue(telegraph >= 12, mode + " " + attack + " telegraph " + telegraph + " < 12");
            assertTrue(telegraph >= attack.minTelegraphTicks(), mode + " " + attack + " below its own floor");
         }
      }
   }

   @Test
   void attackFloorRescuesTelegraphAgainstMinimalConfigWarning() {
      DragonBrainConfig config = DragonBrainConfig.defaults();
      config.warningDurationMultiplier = 0.5F; // lowest the config allows
      DifficultyTuning expert = tuning(DifficultyMode.EXPERT, config, BossPhase.CATACLYSM);
      // 30 * 0.8 (expert) * 0.5 (config) = 12, below the rift's floor of 16.
      assertEquals(16, expert.telegraphTicks(AttackType.VOID_RIFT));
      assertEquals(16, expert.telegraphTicks(AttackType.METEOR_STORM));
      assertEquals(14, expert.telegraphTicks(AttackType.DREAD_ROAR));
      assertEquals(16, expert.telegraphTicks(AttackType.GRAVITY_WELL));
   }

   @Test
   void recoveryAndActiveHaveHardMinimums() {
      DifficultyTuning t = tuning(DifficultyMode.EXPERT, DragonBrainConfig.defaults(), BossPhase.CATACLYSM);
      for (AttackType attack : AttackType.values()) {
         assertTrue(t.recoveryTicks(attack) >= 6, attack + " recovery below minimum");
         assertTrue(t.activeTicks(attack) >= 4, attack + " active below minimum");
         assertTrue(t.cooldownTicks(attack) >= 1, attack + " cooldown below minimum");
      }
   }

   @Test
   void perAbilityCooldownMultipliersApply() {
      DragonBrainConfig config = DragonBrainConfig.defaults();
      config.voidRiftCooldownMultiplier = 2.0F;
      config.meteorCooldownMultiplier = 0.5F;
      DifficultyTuning t = tuning(DifficultyMode.NORMAL, config, BossPhase.NORMAL);
      assertEquals(Math.round(260 * 2.0F), t.cooldownTicks(AttackType.VOID_RIFT));
      assertEquals(Math.round(340 * 0.5F), t.cooldownTicks(AttackType.METEOR_STORM));
      // An untouched ability keeps the plain scaled cooldown.
      assertEquals(220, t.cooldownTicks(AttackType.DREAD_ROAR));
   }

   @Test
   void cataclysmAcceleratesCooldownsAndUnlocksCombos() {
      DragonBrainConfig config = DragonBrainConfig.defaults();
      DifficultyTuning normal = tuning(DifficultyMode.ROOKIE, config, BossPhase.NORMAL);
      DifficultyTuning cataclysm = tuning(DifficultyMode.ROOKIE, config, BossPhase.CATACLYSM);
      assertTrue(cataclysm.cooldownMultiplier() < normal.cooldownMultiplier());
      assertFalse(normal.allowCombo());
      // Cataclysm is the combo phase on every difficulty (config permitting).
      assertTrue(cataclysm.allowCombo());

      config.mechanicComboChains = false;
      assertFalse(tuning(DifficultyMode.ROOKIE, config, BossPhase.CATACLYSM).allowCombo());
      assertFalse(tuning(DifficultyMode.EXPERT, config, BossPhase.CATACLYSM).allowCombo());
   }

   @Test
   void expertCombosRemainALatePhaseToolOutsideCataclysm() {
      DragonBrainConfig config = DragonBrainConfig.defaults();
      assertFalse(tuning(DifficultyMode.EXPERT, config, BossPhase.NORMAL).allowCombo());
      assertTrue(tuning(DifficultyMode.EXPERT, config, BossPhase.FINAL).allowCombo());
      assertFalse(tuning(DifficultyMode.ROOKIE, config, BossPhase.FINAL).allowCombo());
   }

   @Test
   void clampDamageNeverNegative() {
      DragonBrainConfig config = DragonBrainConfig.defaults();
      config.damageMultiplier = 0.0F;
      DifficultyTuning t = tuning(DifficultyMode.NORMAL, config, BossPhase.NORMAL);
      assertEquals(0.0F, t.clampDamage(10.0F), 0.0001F);
      assertTrue(t.clampDamage(-5.0F) >= 0.0F);
   }
}
