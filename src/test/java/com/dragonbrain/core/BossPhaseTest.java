package com.dragonbrain.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BossPhaseTest {
   private static final float RAGE = 0.5F;
   private static final float FINAL = 0.25F;
   private static final float CATACLYSM = 0.1F;

   @Test
   void healthThresholdsSelectExpectedPhase() {
      assertEquals(BossPhase.NORMAL, BossPhase.forHealth(1.0F, RAGE, FINAL, CATACLYSM));
      assertEquals(BossPhase.NORMAL, BossPhase.forHealth(0.51F, RAGE, FINAL, CATACLYSM));
      assertEquals(BossPhase.RAGE, BossPhase.forHealth(0.5F, RAGE, FINAL, CATACLYSM));
      assertEquals(BossPhase.RAGE, BossPhase.forHealth(0.26F, RAGE, FINAL, CATACLYSM));
      assertEquals(BossPhase.FINAL, BossPhase.forHealth(0.25F, RAGE, FINAL, CATACLYSM));
      assertEquals(BossPhase.FINAL, BossPhase.forHealth(0.11F, RAGE, FINAL, CATACLYSM));
      assertEquals(BossPhase.CATACLYSM, BossPhase.forHealth(0.1F, RAGE, FINAL, CATACLYSM));
      assertEquals(BossPhase.CATACLYSM, BossPhase.forHealth(0.01F, RAGE, FINAL, CATACLYSM));
   }

   @Test
   void cataclysmStaysCataclysmAtAnyLowerHealth() {
      // The controller edge-triggers on phase change; forHealth must be stable
      // (never oscillate) while health keeps dropping below the threshold.
      for (float hp = CATACLYSM; hp >= 0.0F; hp -= 0.01F) {
         assertEquals(BossPhase.CATACLYSM, BossPhase.forHealth(hp, RAGE, FINAL, CATACLYSM));
      }
   }

   @Test
   void disabledCataclysmNeverTriggers() {
      // The controller passes -1 for a disabled mechanic and clamps to a tiny
      // epsilon; verify the plain -1 form used by the 2.0-compatible overload.
      assertEquals(BossPhase.FINAL, BossPhase.forHealth(0.02F, RAGE, FINAL, -1.0F));
      assertEquals(BossPhase.FINAL, BossPhase.forHealth(0.02F, RAGE, FINAL));
   }

   @Test
   void phaseOrdering() {
      assertTrue(BossPhase.CATACLYSM.isAtLeast(BossPhase.FINAL));
      assertTrue(BossPhase.FINAL.isAtLeast(BossPhase.RAGE));
      assertTrue(BossPhase.RAGE.isAtLeast(BossPhase.NORMAL));
      assertTrue(BossPhase.NORMAL.isAtLeast(BossPhase.NORMAL));
      assertFalse(BossPhase.RAGE.isAtLeast(BossPhase.FINAL));
   }

   @Test
   void newAttacksUnlockAtDesignedPhases() {
      assertEquals(BossPhase.NORMAL, AttackType.DREAD_ROAR.minPhase());
      assertEquals(BossPhase.RAGE, AttackType.VOID_RIFT.minPhase());
      assertEquals(BossPhase.RAGE, AttackType.METEOR_STORM.minPhase());
      assertEquals(BossPhase.FINAL, AttackType.GRAVITY_WELL.minPhase());
      assertFalse(AttackType.GRAVITY_WELL.isUnlockedIn(BossPhase.RAGE));
      assertTrue(AttackType.GRAVITY_WELL.isUnlockedIn(BossPhase.CATACLYSM));
   }
}
