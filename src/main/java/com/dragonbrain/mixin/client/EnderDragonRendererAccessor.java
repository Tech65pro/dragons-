package com.dragonbrain.mixin.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EnderDragonEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EnderDragonEntityRenderer.class)
public interface EnderDragonRendererAccessor {
   @Accessor("DRAGON_CUTOUT")
   static RenderLayer dragonbrain$vanillaCutout() {
      throw new AssertionError();
   }

   @Accessor("DRAGON_DECAL")
   static RenderLayer dragonbrain$vanillaDecal() {
      throw new AssertionError();
   }

   @Accessor("DRAGON_EYES")
   static RenderLayer dragonbrain$vanillaEyes() {
      throw new AssertionError();
   }
}
