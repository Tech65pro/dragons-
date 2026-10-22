package com.dragonbrain.core;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;

public final class AttackScheduler {
   private static final int APPROACH_TICKS = 20;
   private static final double APPROACH_RANGE = 26.0;
   private static final int PRESSURE_CAP = 12;
   private static final float PRESSURE_DECAY_PER_TICK = 0.012F;
   private final Map<AttackType, Integer> cooldowns = new EnumMap(AttackType.class);
   private final AttackScheduler.Stats stats = new AttackScheduler.Stats();
   private final Random random;
   private CombatState state = CombatState.IDLE;
   private AttackType current;
   private int ticksInState;
   private int plannedStateTicks;
   private int nextAttackDelay;
   private int ticksSinceDamagingAttack = 10000;
   private int ticksSinceAnyAttack = 10000;
   private int ticksWithoutTarget;
   private int ticksSincePressureBlock;
   private float pressure;
   private int consecutiveHits;
   private AttackType lastAttack;
   private boolean fakeoutUsed;
   private boolean comboUsed;
   private boolean phaseTransitionPending;
   private AttackType queuedAttack;
   private BossPhase phase = BossPhase.NORMAL;

   public AttackScheduler(Random random) {
      this.random = random;

      for (AttackType type : AttackType.values()) {
         this.cooldowns.put(type, 0);
      }
   }

   public AttackScheduler.Result tick(DifficultyTuning tuning, boolean hasTarget, double distance, boolean visible, Collection<AttackType> available) {
      this.tickCounters(tuning, hasTarget);
      if (this.phaseTransitionPending && this.state != CombatState.PHASE_TRANSITION) {
         this.enter(CombatState.PHASE_TRANSITION, null, 24);
         this.phaseTransitionPending = false;
         return new AttackScheduler.Result(AttackScheduler.Action.PHASE_TRANSITION, this.state, null, false);
      }

      switch (this.state) {
         case PHASE_TRANSITION:
            if (this.ticksInState >= this.plannedStateTicks) {
               this.enter(CombatState.IDLE, null, 0);
               this.nextAttackDelay = Math.min(this.nextAttackDelay, 20);
               return new AttackScheduler.Result(AttackScheduler.Action.BECOME_IDLE, this.state, null, false);
            }

            return AttackScheduler.Result.none(this.state, this.current);
         case IDLE:
         case APPROACH:
            if (!hasTarget) {
               this.enter(CombatState.IDLE, null, 0);
               return AttackScheduler.Result.none(this.state, null);
            } else if (this.state == CombatState.APPROACH && this.current != null) {
               if (this.ticksInState >= this.plannedStateTicks) {
                  int telegraph = tuning.telegraphTicks(this.current);
                  this.enter(CombatState.TELEGRAPH, this.current, telegraph);
                  return new AttackScheduler.Result(AttackScheduler.Action.START_TELEGRAPH, this.state, this.current, false);
               }

               return AttackScheduler.Result.none(this.state, this.current);
            } else if (this.nextAttackDelay > 0) {
               return AttackScheduler.Result.none(this.state, null);
            } else {
               AttackType forced = this.queuedAttack;
               this.queuedAttack = null;
               AttackType choice = forced != null ? forced : this.chooseAttack(tuning, distance, visible, available);
               if (choice == null) {
                  if ((Integer)this.cooldowns.getOrDefault(AttackType.REPOSITION, 0) <= 0) {
                     this.cooldowns.put(AttackType.REPOSITION, 40);
                     int duration = Math.max(20, Math.round(tuning.attackIntervalTicks() * 0.4F));
                     this.enter(CombatState.REPOSITION, null, duration);
                     return new AttackScheduler.Result(AttackScheduler.Action.ENTER_REPOSITION, this.state, null, false);
                  }

                  return AttackScheduler.Result.none(this.state, null);
               } else {
                  this.current = choice;
                  this.fakeoutUsed = false;
                  this.stats.attacksLaunched++;
                  this.ticksSinceAnyAttack = 0;
                  if (needsApproach(choice, distance)) {
                     this.enter(CombatState.APPROACH, choice, 20);
                     return new AttackScheduler.Result(AttackScheduler.Action.START_APPROACH, this.state, choice, false);
                  }

                  int telegraph = tuning.telegraphTicks(choice);
                  this.enter(CombatState.TELEGRAPH, choice, telegraph);
                  return new AttackScheduler.Result(AttackScheduler.Action.START_TELEGRAPH, this.state, choice, false);
               }
            }
         case TELEGRAPH:
            if (this.ticksInState >= this.plannedStateTicks) {
               AttackType fakeout = this.tryFakeout(tuning, available);
               if (fakeout != null) {
                  this.current = fakeout;
                  this.fakeoutUsed = true;
                  this.stats.fakeouts++;
                  int shorter = Math.max(tuning.minTelegraphTicks(), Math.round(tuning.telegraphTicks(fakeout) * 0.8F));
                  this.enter(CombatState.TELEGRAPH, fakeout, shorter);
                  return new AttackScheduler.Result(AttackScheduler.Action.START_TELEGRAPH, this.state, fakeout, true);
               }

               this.enter(CombatState.ATTACK, this.current, tuning.activeTicks(this.current));
               if (this.current != null && this.current.isDamaging()) {
                  this.ticksSinceDamagingAttack = 0;
               }

               return new AttackScheduler.Result(AttackScheduler.Action.START_ATTACK, this.state, this.current, false);
            }

            return AttackScheduler.Result.none(this.state, this.current);
         case ATTACK:
            if (this.ticksInState >= this.plannedStateTicks) {
               AttackType finished = this.current;
               int recovery = finished == null ? 20 : tuning.recoveryTicks(finished);
               this.enter(CombatState.RECOVERY, finished, recovery);
               return new AttackScheduler.Result(AttackScheduler.Action.ENTER_RECOVERY, this.state, finished, false);
            }

            return AttackScheduler.Result.none(this.state, this.current);
         case RECOVERY:
            if (this.ticksInState >= this.plannedStateTicks) {
               boolean pressured = this.isPressured(tuning);
               if (pressured) {
                  this.enter(CombatState.REPOSITION, null, tuning.hitPressureCooldownTicks());
                  this.consecutiveHits = 0;
                  return new AttackScheduler.Result(AttackScheduler.Action.ENTER_REPOSITION, this.state, null, false);
               }

               this.enter(CombatState.IDLE, null, 0);
               this.nextAttackDelay = this.rollAttackDelay(tuning);
               this.cooldowns.put(AttackType.REPOSITION, 0);
               return new AttackScheduler.Result(AttackScheduler.Action.BECOME_IDLE, this.state, null, false);
            }

            return AttackScheduler.Result.none(this.state, this.current);
         case REPOSITION:
            if (this.ticksInState >= this.plannedStateTicks) {
               this.enter(CombatState.IDLE, null, 0);
               this.nextAttackDelay = this.rollAttackDelay(tuning);
               return new AttackScheduler.Result(AttackScheduler.Action.BECOME_IDLE, this.state, null, false);
            }

            return AttackScheduler.Result.none(this.state, this.current);
         default:
            return AttackScheduler.Result.none(this.state, this.current);
      }
   }

   private void tickCounters(DifficultyTuning tuning, boolean hasTarget) {
      this.ticksInState++;
      this.ticksSinceDamagingAttack++;
      this.ticksSinceAnyAttack++;
      this.ticksSincePressureBlock++;
      if (this.nextAttackDelay > 0) {
         this.nextAttackDelay--;
      }

      for (Entry<AttackType, Integer> entry : this.cooldowns.entrySet()) {
         if ((Integer)entry.getValue() > 0) {
            entry.setValue((Integer)entry.getValue() - 1);
         }
      }

      this.pressure = Math.max(0.0F, this.pressure - 0.012F);
      this.ticksWithoutTarget = hasTarget ? 0 : this.ticksWithoutTarget + 1;
      if (this.ticksSincePressureBlock > tuning.hitPressureCooldownTicks() * 3) {
         this.pressure = 0.0F;
         this.consecutiveHits = 0;
      }
   }

   private int rollAttackDelay(DifficultyTuning tuning) {
      int jitter = tuning.attackIntervalJitterTicks();
      int extra = jitter <= 0 ? 0 : this.random.nextInt(jitter + 1);
      return Math.max(10, tuning.attackIntervalTicks() + extra);
   }

   private boolean isPressured(DifficultyTuning tuning) {
      return this.consecutiveHits >= tuning.consecutiveHitLimit() || this.pressure >= 4.0F;
   }

   private AttackType chooseAttack(DifficultyTuning tuning, double distance, boolean visible, Collection<AttackType> available) {
      boolean damagingLocked = this.ticksSinceDamagingAttack < tuning.minDamageIntervalTicks();
      float total = 0.0F;
      EnumMap<AttackType, Float> weights = new EnumMap(AttackType.class);

      for (AttackType type : available) {
         if (type.isDamaging() && type.isUnlockedIn(this.phase) && (Integer)this.cooldowns.getOrDefault(type, 0) <= 0 && !damagingLocked) {
            float weight = this.baseWeight(type, distance, visible);
            if (!(weight <= 0.0F)) {
               weights.put(type, weight);
               total += weight;
            }
         }
      }

      if (total <= 0.0F) {
         return null;
      }

      float roll = this.random.nextFloat() * total;

      for (Entry<AttackType, Float> entry : weights.entrySet()) {
         roll -= entry.getValue();
         if (roll <= 0.0F) {
            this.cooldowns.put((AttackType)entry.getKey(), tuning.cooldownTicks((AttackType)entry.getKey()));
            this.lastAttack = (AttackType)entry.getKey();
            return (AttackType)entry.getKey();
         }
      }

      return null;
   }

   private float baseWeight(AttackType type, double distance, boolean visible) {
      float weight = switch (type) {
         case FIREBALL -> distance > 10.0 && distance < 64.0 ? (visible ? 1.0F : 0.25F) : 0.0F;
         case DIVE -> distance > 8.0 && distance < 48.0 ? 1.0F : 0.15F;
         case WING_GUST -> distance < 18.0 ? 1.1F : 0.0F;
         case VOID_PULSE -> distance < 30.0 ? 0.85F : 0.05F;
         case REPOSITION, HOVER -> 0.0F;
      };
      if (type == this.lastAttack) {
         weight *= 0.3F;
      }

      return weight;
   }

   private static boolean needsApproach(AttackType type, double distance) {
      return distance > 26.0 && (type == AttackType.WING_GUST || type == AttackType.VOID_PULSE || type == AttackType.DIVE);
   }

   private AttackType tryFakeout(DifficultyTuning tuning, Collection<AttackType> available) {
      if (!this.fakeoutUsed && this.current != null && this.current.isDamaging()) {
         if (this.random.nextFloat() >= tuning.fakeoutChance()) {
            return null;
         }

         AttackType best = null;

         for (AttackType type : available) {
            if (type != this.current && type.isDamaging() && (Integer)this.cooldowns.getOrDefault(type, 0) <= 0 && (best == null || this.random.nextBoolean())) {
               best = type;
            }
         }

         if (best == null) {
            return null;
         }

         this.cooldowns.put(best, tuning.cooldownTicks(best));
         this.lastAttack = best;
         return best;
      } else {
         return null;
      }
   }

   public void onDamageDealt(float weight) {
      this.pressure = Math.min(12.0F, this.pressure + Math.max(0.5F, weight));
      this.consecutiveHits++;
      this.stats.attacksLanded++;
      this.ticksSincePressureBlock = 0;
   }

   public void onAttackWhiffed() {
      this.pressure = Math.max(0.0F, this.pressure - 0.75F);
   }

   public boolean interrupt(DifficultyTuning tuning, String reason) {
      if (this.state != CombatState.TELEGRAPH && this.state != CombatState.ATTACK) {
         return false;
      }

      AttackType interrupted = this.current;
      this.enter(CombatState.RECOVERY, null, Math.max(10, tuning.recoveryTicks(interrupted == null ? AttackType.HOVER : interrupted)));
      this.stats.attacksCancelled++;
      this.cooldowns.put(AttackType.REPOSITION, 20);
      this.pressure = Math.max(0.0F, this.pressure - 1.0F);
      return true;
   }

   public void queueAttack(AttackType type) {
      this.queuedAttack = type;
      this.nextAttackDelay = 0;
   }

   public void forceRecovery(DifficultyTuning tuning, int ticks) {
      this.enter(CombatState.RECOVERY, null, Math.max(10, ticks));
   }

   public void beginPhaseTransition() {
      this.phaseTransitionPending = true;
   }

   public void setPhase(BossPhase phase) {
      this.phase = phase;
   }

   public BossPhase getPhase() {
      return this.phase;
   }

   private void enter(CombatState newState, AttackType attack, int duration) {
      this.state = newState;
      this.current = attack;
      this.ticksInState = 0;
      this.plannedStateTicks = duration;
   }

   public CombatState getState() {
      return this.state;
   }

   public AttackType getCurrentAttack() {
      return this.current;
   }

   public int getTicksInState() {
      return this.ticksInState;
   }

   public int getPlannedStateTicks() {
      return this.plannedStateTicks;
   }

   public int getTicksSinceDamagingAttack() {
      return this.ticksSinceDamagingAttack;
   }

   public int getTicksSinceAnyAttack() {
      return this.ticksSinceAnyAttack;
   }

   public int getTicksWithoutTarget() {
      return this.ticksWithoutTarget;
   }

   public float getPressure() {
      return this.pressure;
   }

   public int getConsecutiveHits() {
      return this.consecutiveHits;
   }

   public AttackType getLastAttack() {
      return this.lastAttack;
   }

   public AttackScheduler.Stats getStats() {
      return this.stats;
   }

   public void reset() {
      this.state = CombatState.IDLE;
      this.current = null;
      this.ticksInState = 0;
      this.plannedStateTicks = 0;
      this.nextAttackDelay = 40;
      this.ticksSinceDamagingAttack = 10000;
      this.ticksSinceAnyAttack = 10000;
      this.pressure = 0.0F;
      this.consecutiveHits = 0;
      this.lastAttack = null;
      this.fakeoutUsed = false;
      this.phaseTransitionPending = false;
      this.queuedAttack = null;
      this.cooldowns.replaceAll((k, v) -> 0);
      this.stats.reset();
   }

   public boolean allowsDamage() {
      return this.state == CombatState.ATTACK && this.current != null && this.current.isDamaging();
   }

   public enum Action {
      NONE,
      START_APPROACH,
      START_TELEGRAPH,
      START_ATTACK,
      ENTER_RECOVERY,
      ENTER_REPOSITION,
      BECOME_IDLE,
      PHASE_TRANSITION;
   }

   public record Result(AttackScheduler.Action action, CombatState state, AttackType attack, boolean fakeout) {
      public static AttackScheduler.Result none(CombatState state, AttackType attack) {
         return new AttackScheduler.Result(AttackScheduler.Action.NONE, state, attack, false);
      }
   }

   public static final class Stats {
      public int attacksLaunched;
      public int attacksLanded;
      public int attacksCancelled;
      public int fakeouts;
      public int combos;
      public int pressureBlocks;

      public void reset() {
         this.attacksLaunched = 0;
         this.attacksLanded = 0;
         this.attacksCancelled = 0;
         this.fakeouts = 0;
         this.combos = 0;
         this.pressureBlocks = 0;
      }

      public float hitRatio() {
         return this.attacksLaunched == 0 ? 0.0F : (float)this.attacksLanded / this.attacksLaunched;
      }
   }
}
