package com.dragonbrain.mixin;

import com.dragonbrain.ai.CombatManager;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderDragonEntity.class)
public abstract class EnderDragonEntityMixin {
   @Inject(method = "tickMovement", at = @At("TAIL"))
   private void dragonbrain$tickController(CallbackInfo ci) {
      EnderDragonEntity self = (EnderDragonEntity)this;
      if (self.getEntityWorld() instanceof ServerWorld serverWorld) {
         CombatManager.tickDragon(self, serverWorld);
      }
   }
}
