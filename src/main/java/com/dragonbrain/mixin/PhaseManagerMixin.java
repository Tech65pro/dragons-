package com.dragonbrain.mixin;

import com.dragonbrain.ai.CombatManager;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.phase.PhaseManager;
import net.minecraft.entity.boss.dragon.phase.PhaseType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PhaseManager.class)
public abstract class PhaseManagerMixin {
   @Shadow
   @Final
   private EnderDragonEntity dragon;

   @Inject(method = "setPhase", at = @At("HEAD"), cancellable = true)
   private void dragonbrain$vetoVanillaAttackPhase(PhaseType<?> type, CallbackInfo ci) {
      if (CombatManager.vetoPhaseChange(this.dragon, type)) {
         ci.cancel();
      }
   }
}
