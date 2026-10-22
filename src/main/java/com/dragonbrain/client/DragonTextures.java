package com.dragonbrain.client;

import com.dragonbrain.config.ConfigManager;
import com.dragonbrain.core.DragonBrainConfig;
import com.dragonbrain.mixin.client.EnderDragonRendererAccessor;
import net.minecraft.client.render.entity.state.EnderDragonEntityRenderState;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

public final class DragonTextures {
   public static final Identifier CUSTOM_BODY = id("dragon.png");
   public static final Identifier CUSTOM_EYES = id("dragon_eyes.png");
   public static final Identifier FINAL_BODY = id("dragon_final.png");
   public static final Identifier FINAL_EYES = id("dragon_final_eyes.png");
   public static final Identifier CATACLYSM_BODY = id("dragon_cataclysm.png");
   public static final Identifier CATACLYSM_EYES = id("dragon_cataclysm_eyes.png");
   private static final ThreadLocal<EnderDragonEntityRenderState> CURRENT = new ThreadLocal();
   private static RenderLayer customBodyLayer;
   private static RenderLayer customDecalLayer;
   private static RenderLayer customEyesLayer;
   private static RenderLayer finalBodyLayer;
   private static RenderLayer finalDecalLayer;
   private static RenderLayer finalEyesLayer;
   private static RenderLayer cataclysmBodyLayer;
   private static RenderLayer cataclysmDecalLayer;
   private static RenderLayer cataclysmEyesLayer;

   private DragonTextures() {
   }

   private static Identifier id(String path) {
      return Identifier.of("dragonbrain", "textures/entity/enderdragon/" + path);
   }

   public static void push(EnderDragonEntityRenderState state) {
      CURRENT.set(state);
   }

   public static void pop() {
      CURRENT.remove();
   }

   private static boolean useFinal() {
      EnderDragonEntityRenderState state = (EnderDragonEntityRenderState)CURRENT.get();
      return state instanceof DragonBrainRenderState dragonState && dragonState.dragonbrain$isFinalPhase();
   }

   private static boolean useCataclysm() {
      EnderDragonEntityRenderState state = (EnderDragonEntityRenderState)CURRENT.get();
      return state instanceof DragonBrainRenderState dragonState && dragonState.dragonbrain$isCataclysmPhase();
   }

   private static boolean customEnabled() {
      return ConfigManager.get().customTexture;
   }

   public static RenderLayer body() {
      if (!customEnabled()) {
         return EnderDragonRendererAccessor.dragonbrain$vanillaCutout();
      }

      if (useCataclysm()) {
         if (cataclysmBodyLayer == null) {
            cataclysmBodyLayer = RenderLayers.entityCutoutNoCull(CATACLYSM_BODY);
         }

         return cataclysmBodyLayer;
      } else if (useFinal()) {
         if (finalBodyLayer == null) {
            finalBodyLayer = RenderLayers.entityCutoutNoCull(FINAL_BODY);
         }

         return finalBodyLayer;
      } else {
         if (customBodyLayer == null) {
            customBodyLayer = RenderLayers.entityCutoutNoCull(CUSTOM_BODY);
         }

         return customBodyLayer;
      }
   }

   public static RenderLayer decal() {
      if (!customEnabled()) {
         return EnderDragonRendererAccessor.dragonbrain$vanillaDecal();
      }

      if (useCataclysm()) {
         if (cataclysmDecalLayer == null) {
            cataclysmDecalLayer = RenderLayers.entityDecal(CATACLYSM_BODY);
         }

         return cataclysmDecalLayer;
      } else if (useFinal()) {
         if (finalDecalLayer == null) {
            finalDecalLayer = RenderLayers.entityDecal(FINAL_BODY);
         }

         return finalDecalLayer;
      } else {
         if (customDecalLayer == null) {
            customDecalLayer = RenderLayers.entityDecal(CUSTOM_BODY);
         }

         return customDecalLayer;
      }
   }

   public static RenderLayer eyes() {
      DragonBrainConfig config = ConfigManager.get();
      if (!config.customTexture || !config.emissiveEyes) {
         return EnderDragonRendererAccessor.dragonbrain$vanillaEyes();
      }

      if (useCataclysm()) {
         if (cataclysmEyesLayer == null) {
            cataclysmEyesLayer = RenderLayers.eyes(CATACLYSM_EYES);
         }

         return cataclysmEyesLayer;
      } else if (useFinal()) {
         if (finalEyesLayer == null) {
            finalEyesLayer = RenderLayers.eyes(FINAL_EYES);
         }

         return finalEyesLayer;
      } else {
         if (customEyesLayer == null) {
            customEyesLayer = RenderLayers.eyes(CUSTOM_EYES);
         }

         return customEyesLayer;
      }
   }
}
