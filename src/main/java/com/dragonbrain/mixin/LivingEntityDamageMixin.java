package com.dragonbrain.mixin;

import com.dragonbrain.ai.CombatManager;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {
   @ModifyVariable(method = "damage(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;F)Z", at = @At("HEAD"), argsOnly = true, index = 3)
   private float dragonbrain$scaleDragonDamage(float amount, ServerWorld world, DamageSource source) {
      return CombatManager.scaleDragonDamage(source, amount);
   }
}
