package com.dragonbrain.mixin.client;

import com.dragonbrain.client.DragonBrainRenderState;
import net.minecraft.client.render.entity.state.EnderDragonEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EnderDragonEntityRenderState.class)
public class EnderDragonRenderStateMixin implements DragonBrainRenderState {
   @Unique
   private boolean dragonbrain$finalPhase;
   @Unique
   private boolean dragonbrain$cataclysmPhase;
   @Unique
   private float dragonbrain$healthRatio = 1.0F;

   @Override
   public void dragonbrain$setFinalPhase(boolean finalPhase) {
      this.dragonbrain$finalPhase = finalPhase;
   }

   @Override
   public boolean dragonbrain$isFinalPhase() {
      return this.dragonbrain$finalPhase;
   }

   @Override
   public void dragonbrain$setCataclysmPhase(boolean cataclysmPhase) {
      this.dragonbrain$cataclysmPhase = cataclysmPhase;
   }

   @Override
   public boolean dragonbrain$isCataclysmPhase() {
      return this.dragonbrain$cataclysmPhase;
   }

   @Override
   public void dragonbrain$setHealthRatio(float ratio) {
      this.dragonbrain$healthRatio = ratio;
   }

   @Override
   public float dragonbrain$getHealthRatio() {
      return this.dragonbrain$healthRatio;
   }
}
