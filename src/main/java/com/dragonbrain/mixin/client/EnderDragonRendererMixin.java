package com.dragonbrain.mixin.client;

import com.dragonbrain.client.DragonBrainRenderState;
import com.dragonbrain.client.DragonTextures;
import com.dragonbrain.config.ConfigManager;
import com.dragonbrain.core.DragonBrainConfig;
import net.minecraft.client.render.entity.state.EnderDragonEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.entity.EnderDragonEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderDragonEntityRenderer.class)
public class EnderDragonRendererMixin {
   @Inject(method = "updateRenderState(Lnet/minecraft/entity/boss/dragon/EnderDragonEntity;Lnet/minecraft/client/render/entity/state/EnderDragonEntityRenderState;F)V", at = @At("TAIL"))
   private void dragonbrain$captureDragonPhase(EnderDragonEntity entity, EnderDragonEntityRenderState state, float tickProgress, CallbackInfo ci) {
      if (state instanceof DragonBrainRenderState dragonState) {
         DragonBrainConfig config = ConfigManager.get();
         float ratio = entity.getMaxHealth() <= 0.0F ? 1.0F : entity.getHealth() / entity.getMaxHealth();
         dragonState.dragonbrain$setHealthRatio(ratio);
         boolean finalPhase = config.mechanicFinalPhase && ratio <= config.finalPhaseHealthThreshold * 1.15F;
         dragonState.dragonbrain$setFinalPhase(finalPhase);
         boolean cataclysmPhase = config.mechanicCataclysmPhase && ratio <= config.cataclysmHealthThreshold * 1.15F;
         dragonState.dragonbrain$setCataclysmPhase(cataclysmPhase);
      }
   }

   @Inject(
      method = "render(Lnet/minecraft/client/render/entity/state/EnderDragonEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
      at = @At("HEAD")
   )
   private void dragonbrain$pushState(EnderDragonEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera, CallbackInfo ci) {
      DragonTextures.push(state);
   }

   @Inject(
      method = "render(Lnet/minecraft/client/render/entity/state/EnderDragonEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
      at = @At("RETURN")
   )
   private void dragonbrain$popState(EnderDragonEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera, CallbackInfo ci) {
      DragonTextures.pop();
   }

   @Redirect(
      method = "render(Lnet/minecraft/client/render/entity/state/EnderDragonEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
      at = @At(value = "FIELD", opcode = 178, target = "Lnet/minecraft/client/render/entity/EnderDragonEntityRenderer;DRAGON_CUTOUT:Lnet/minecraft/client/render/RenderLayer;")
   )
   private static RenderLayer dragonbrain$bodyLayer() {
      return DragonTextures.body();
   }

   @Redirect(
      method = "render(Lnet/minecraft/client/render/entity/state/EnderDragonEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
      at = @At(value = "FIELD", opcode = 178, target = "Lnet/minecraft/client/render/entity/EnderDragonEntityRenderer;DRAGON_DECAL:Lnet/minecraft/client/render/RenderLayer;")
   )
   private static RenderLayer dragonbrain$decalLayer() {
      return DragonTextures.decal();
   }

   @Redirect(
      method = "render(Lnet/minecraft/client/render/entity/state/EnderDragonEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
      at = @At(value = "FIELD", opcode = 178, target = "Lnet/minecraft/client/render/entity/EnderDragonEntityRenderer;DRAGON_EYES:Lnet/minecraft/client/render/RenderLayer;")
   )
   private static RenderLayer dragonbrain$eyeLayer() {
      return DragonTextures.eyes();
   }
}
