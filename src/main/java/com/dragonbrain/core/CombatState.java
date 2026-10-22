package com.dragonbrain.core;

public enum CombatState {
   IDLE,
   APPROACH,
   TELEGRAPH,
   ATTACK,
   RECOVERY,
   REPOSITION,
   PHASE_TRANSITION;

   public boolean isDamagingWindow() {
      return this == ATTACK;
   }

   public boolean isBusy() {
      return this == TELEGRAPH || this == ATTACK || this == RECOVERY || this == REPOSITION || this == PHASE_TRANSITION || this == APPROACH;
   }
}
