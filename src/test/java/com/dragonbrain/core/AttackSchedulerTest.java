package com.dragonbrain.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Drives the real scheduler through thousands of simulated ticks with a
 * compressed tuning (tiny telegraphs/cooldowns) and asserts the fairness
 * invariants as properties of the event stream.
 */
class AttackSchedulerTest {
   private static final Set<AttackType> ALL = EnumSet.allOf(AttackType.class);
   private static final Set<AttackType> NEW_ABILITIES = EnumSet.of(
      AttackType.DREAD_ROAR, AttackType.VOID_RIFT, AttackType.METEOR_STORM, AttackType.GRAVITY_WELL
   );

   /** Compressed tuning: attacks cycle every ~20 ticks so tests stay fast. */
   private static DifficultyTuning fast(float cooldownMultiplier, int minDamageInterval, float fakeoutChance, boolean allowCombo) {
      return fast(cooldownMultiplier, minDamageInterval, fakeoutChance, allowCombo, 1000);
   }

   private static DifficultyTuning fast(
      float cooldownMultiplier, int minDamageInterval, float fakeoutChance, boolean allowCombo, int consecutiveHitLimit
   ) {
      return new DifficultyTuning(
         DifficultyMode.NORMAL,
         5,
         0,
         cooldownMultiplier,
         0.05F,
         0.05F,
         2,
         minDamageInterval,
         0.0F,
         0.5F,
         0.0F,
         0,
         4.0F,
         1.0F,
         consecutiveHitLimit,
         50,
         fakeoutChance,
         allowCombo
      );
   }

   /** Ticks the scheduler and records every attack that starts a telegraph. */
   private static List<AttackScheduler.Result> run(AttackScheduler scheduler, DifficultyTuning tuning, int ticks, Set<AttackType> available) {
      List<AttackScheduler.Result> events = new ArrayList<>();
      for (int i = 0; i < ticks; i++) {
         AttackScheduler.Result result = scheduler.tick(tuning, true, 15.0, true, available);
         if (result.action() != AttackScheduler.Action.NONE) {
            events.add(result);
         }
      }
      return events;
   }

   private static List<AttackType> launches(List<AttackScheduler.Result> events) {
      List<AttackType> out = new ArrayList<>();
      for (AttackScheduler.Result r : events) {
         if (r.action() == AttackScheduler.Action.START_TELEGRAPH || r.action() == AttackScheduler.Action.START_APPROACH) {
            assertNotNull(r.attack());
            out.add(r.attack());
         }
      }
      return out;
   }

   @Test
   void choosingAnAttackStartsItsCooldownImmediately() {
      AttackScheduler scheduler = new AttackScheduler(new Random(1L));
      DifficultyTuning tuning = fast(0.02F, 0, 0.0F, false);
      AttackScheduler.Result first = scheduler.tick(tuning, true, 15.0, true, EnumSet.of(AttackType.FIREBALL));
      assertEquals(AttackScheduler.Action.START_TELEGRAPH, first.action());
      assertEquals(AttackType.FIREBALL, scheduler.getCurrentAttack());
      // round(90 * 0.02) = 2 ticks of cooldown, then it ticks down.
      assertEquals(2, scheduler.getCooldownTicks(AttackType.FIREBALL));
      scheduler.tick(tuning, true, 15.0, true, EnumSet.of(AttackType.FIREBALL));
      assertEquals(1, scheduler.getCooldownTicks(AttackType.FIREBALL));
      scheduler.tick(tuning, true, 15.0, true, EnumSet.of(AttackType.FIREBALL));
      assertEquals(0, scheduler.getCooldownTicks(AttackType.FIREBALL));
   }

   @Test
   void cooldownPreventsImmediateRelaunch() {
      AttackScheduler scheduler = new AttackScheduler(new Random(2L));
      DifficultyTuning tuning = fast(1.0F, 0, 0.0F, false); // fireball cooldown = 90 ticks
      List<Integer> launchTicks = new ArrayList<>();
      for (int tick = 0; tick < 400 && launchTicks.size() < 2; tick++) {
         AttackScheduler.Result r = scheduler.tick(tuning, true, 15.0, true, EnumSet.of(AttackType.FIREBALL));
         if (r.action() == AttackScheduler.Action.START_TELEGRAPH) {
            launchTicks.add(tick);
         }
      }
      assertEquals(2, launchTicks.size(), "expected exactly two launches");
      assertTrue(launchTicks.get(1) - launchTicks.get(0) >= 90, "second launch must respect the 90 tick cooldown");
   }

   @Test
   void damageOnlyAllowedInsideAttackWindow() {
      AttackScheduler scheduler = new AttackScheduler(new Random(3L));
      DifficultyTuning tuning = fast(0.02F, 0, 0.0F, false);
      assertFalse(scheduler.allowsDamage());
      scheduler.tick(tuning, true, 15.0, true, EnumSet.of(AttackType.FIREBALL)); // -> TELEGRAPH
      assertFalse(scheduler.allowsDamage());
      // run until ATTACK starts
      boolean sawDamageWindow = false;
      for (int i = 0; i < 60; i++) {
         AttackScheduler.Result r = scheduler.tick(tuning, true, 15.0, true, EnumSet.of(AttackType.FIREBALL));
         if (r.action() == AttackScheduler.Action.START_ATTACK) {
            assertTrue(scheduler.allowsDamage());
            sawDamageWindow = true;
         }
         if (r.action() == AttackScheduler.Action.ENTER_RECOVERY) {
            assertFalse(scheduler.allowsDamage());
            break;
         }
      }
      assertTrue(sawDamageWindow);
   }

   @Test
   void minimumDamageIntervalBlocksFurtherDamagingAttacks() {
      AttackScheduler scheduler = new AttackScheduler(new Random(4L));
      DifficultyTuning tuning = fast(0.02F, 2000, 0.0F, false);
      List<AttackScheduler.Result> events = run(scheduler, tuning, 1500, ALL);
      assertEquals(1, scheduler.getStats().attacksLaunched);
      assertEquals(1, launches(events).size());
   }

   @Test
   void phaseLockedAbilitiesNeverChosenInNormalPhase() {
      AttackScheduler scheduler = new AttackScheduler(new Random(5L));
      scheduler.setPhase(BossPhase.NORMAL);
      List<AttackType> launched = launches(run(scheduler, fast(0.02F, 0, 0.0F, false), 6000, ALL));
      assertTrue(launched.size() > 20, "sanity: the fight actually happened (" + launched.size() + " attacks)");
      for (AttackType attack : launched) {
         assertTrue(attack.isUnlockedIn(BossPhase.NORMAL), attack + " must stay locked in NORMAL phase");
      }
   }

   @Test
   void cataclysmUnlocksTheNewArsenal() {
      AttackScheduler scheduler = new AttackScheduler(new Random(6L));
      scheduler.setPhase(BossPhase.CATACLYSM);
      List<AttackType> onlyNew = launches(run(scheduler, fast(0.02F, 0, 0.0F, false), 400, NEW_ABILITIES));
      assertFalse(onlyNew.isEmpty(), "new abilities must be launchable in CATACLYSM");
      for (AttackType attack : onlyNew) {
         assertTrue(NEW_ABILITIES.contains(attack));
      }

      AttackScheduler mixed = new AttackScheduler(new Random(7L));
      mixed.setPhase(BossPhase.CATACLYSM);
      List<AttackType> all = launches(run(mixed, fast(0.02F, 0, 0.0F, false), 6000, ALL));
      assertTrue(all.contains(AttackType.VOID_RIFT), "void rift should show up over a long cataclysm");
      assertTrue(all.contains(AttackType.GRAVITY_WELL), "gravity well should show up over a long cataclysm");
   }

   @Test
   void disabledAbilityIsNeverChosen() {
      AttackScheduler scheduler = new AttackScheduler(new Random(8L));
      scheduler.setPhase(BossPhase.CATACLYSM);
      Set<AttackType> available = EnumSet.allOf(AttackType.class);
      available.remove(AttackType.METEOR_STORM); // as if mechanicMeteorStorm=false
      List<AttackType> launched = launches(run(scheduler, fast(0.02F, 0, 0.0F, false), 6000, available));
      assertTrue(launched.size() > 20);
      assertFalse(launched.contains(AttackType.METEOR_STORM));
   }

   @Test
   void fakeoutsNeverSwapIntoPhaseLockedAttacks() {
      AttackScheduler scheduler = new AttackScheduler(new Random(9L));
      scheduler.setPhase(BossPhase.NORMAL);
      List<AttackScheduler.Result> events = run(scheduler, fast(0.02F, 0, 1.0F, false), 8000, ALL);
      assertTrue(scheduler.getStats().fakeouts > 0, "sanity: fakeouts actually happened");
      for (AttackScheduler.Result r : events) {
         if (r.fakeout()) {
            assertEquals(BossPhase.NORMAL, r.attack().minPhase(), "fakeout leaked into " + r.attack());
         }
      }
   }

   @Test
   void comboChainsAtMostOnceAndOnlyWhenAllowed() {
      AttackScheduler scheduler = new AttackScheduler(new Random(10L));
      scheduler.setPhase(BossPhase.CATACLYSM);
      run(scheduler, fast(0.02F, 1, 0.0F, true), 8000, ALL);
      int combos = scheduler.getStats().combos;
      int launched = scheduler.getStats().attacksLaunched;
      assertTrue(combos >= 1, "expected at least one combo over a long cataclysm");
      // Every combo must be preceded by a non-combo launch: chains capped at 2.
      assertTrue(2 * combos <= launched, "combos=" + combos + " launched=" + launched);

      AttackScheduler noCombo = new AttackScheduler(new Random(10L));
      noCombo.setPhase(BossPhase.CATACLYSM);
      run(noCombo, fast(0.02F, 1, 0.0F, false), 8000, ALL);
      assertEquals(0, noCombo.getStats().combos);
   }

   @Test
   void comboRespectsMinimumDamageInterval() {
      AttackScheduler scheduler = new AttackScheduler(new Random(11L));
      scheduler.setPhase(BossPhase.CATACLYSM);
      run(scheduler, fast(0.02F, 2000, 0.0F, true), 1500, ALL);
      assertEquals(0, scheduler.getStats().combos);
      assertEquals(1, scheduler.getStats().attacksLaunched);
   }

   @Test
   void phaseTransitionFiresExactlyOnce() {
      AttackScheduler scheduler = new AttackScheduler(new Random(12L));
      DifficultyTuning tuning = fast(0.02F, 0, 0.0F, false);
      scheduler.beginPhaseTransition();
      int transitions = 0;
      for (int i = 0; i < 200; i++) {
         AttackScheduler.Result r = scheduler.tick(tuning, true, 15.0, true, ALL);
         if (r.action() == AttackScheduler.Action.PHASE_TRANSITION) {
            transitions++;
         }
      }
      assertEquals(1, transitions);
   }

   @Test
   void interruptOnlyWorksDuringTelegraphOrAttack() {
      AttackScheduler scheduler = new AttackScheduler(new Random(13L));
      DifficultyTuning tuning = fast(0.02F, 0, 0.0F, false);
      assertFalse(scheduler.interrupt(tuning, "test")); // IDLE
      scheduler.tick(tuning, true, 15.0, true, EnumSet.of(AttackType.FIREBALL)); // -> TELEGRAPH
      assertEquals(CombatState.TELEGRAPH, scheduler.getState());
      assertTrue(scheduler.interrupt(tuning, "test"));
      assertEquals(CombatState.RECOVERY, scheduler.getState());
      assertFalse(scheduler.interrupt(tuning, "test")); // already recovering
   }

   @Test
   void consecutiveHitPressureForcesReposition() {
      // 2.0 fairness feature kept in 3.0: after repeated landed hits the
      // dragon must break off instead of chaining pressure forever.
      AttackScheduler scheduler = new AttackScheduler(new Random(14L));
      DifficultyTuning tuning = fast(0.02F, 0, 0.0F, false, 2);
      scheduler.tick(tuning, true, 15.0, true, EnumSet.of(AttackType.FIREBALL)); // launch
      boolean repositioned = false;
      for (int i = 0; i < 80 && !repositioned; i++) {
         AttackScheduler.Result r = scheduler.tick(tuning, true, 15.0, true, EnumSet.of(AttackType.FIREBALL));
         if (r.action() == AttackScheduler.Action.START_ATTACK) {
            scheduler.onDamageDealt(1.0F);
            scheduler.onDamageDealt(1.0F);
         }
         if (r.action() == AttackScheduler.Action.ENTER_REPOSITION) {
            repositioned = true;
         }
      }
      assertTrue(repositioned);
   }

   @Test
   void noTargetMeansNoAttacks() {
      AttackScheduler scheduler = new AttackScheduler(new Random(15L));
      for (int i = 0; i < 300; i++) {
         scheduler.tick(fast(0.02F, 0, 0.0F, false), false, 0.0, false, ALL);
      }
      assertEquals(0, scheduler.getStats().attacksLaunched);
      assertEquals(CombatState.IDLE, scheduler.getState());
   }
}
