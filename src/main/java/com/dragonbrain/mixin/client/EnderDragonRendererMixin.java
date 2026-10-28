package com.dragonbrain.mixin.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EnderDragonEntityRenderer;
import net.minecraft.client.render.entity.state.EnderDragonEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.util.Identifier;

import com.dragonbrain.client.DragonBrainRenderStateAccess;
import com.dragonbrain.client.DragonTextures;
import com.dragonbrain.config.ConfigManager;
import com.dragonbrain.config.DragonBrainConfig;
import com.dragonbrain.core.BossPhase;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Abyssal Cataclysm dragon appearance for the 1.21.11 render pipeline.
 *
 * <p>In 1.21.11 the dragon renderer draws through three static layers
 * (DRAGON_CUTOUT body, DRAGON_DECAL death effect, DRAGON_EYES emissive).
 * At the head of each {@code render} call we point DRAGON_CUTOUT and
 * DRAGON_EYES at layers built from the current boss phase's textures;
 * DRAGON_DECAL is never touched, so the vanilla death explosion is
 * preserved exactly. While the dragon is dying, or when
 * {@code customTexture} is off, the original vanilla layers are restored.
 * This mirrors the layer-based design DragonBrain 2.0 shipped
 * (DragonTextures held body/decal/eyes layer triples in 2.0).
 *
 * <p>Phase is captured in {@code updateRenderState} from the same health
 * thresholds the server-side DragonCombatController uses. All injections are
 * {@code require = 0}: a renamed vanilla target degrades to vanilla
 * textures, never a crash.
 */
@Mixin(EnderDragonEntityRenderer.class)
public abstract class EnderDragonRendererMixin {
    @Shadow
    @Final
    @Mutable
    private static RenderLayer DRAGON_CUTOUT;

    @Shadow
    @Final
    @Mutable
    private static RenderLayer DRAGON_EYES;

    @Unique
    private static RenderLayer dragonbrain$vanillaCutout;
    @Unique
    private static RenderLayer dragonbrain$vanillaEyes;
    @Unique
    private static final RenderLayer[] dragonbrain$bodyLayers = new RenderLayer[BossPhase.values().length];
    @Unique
    private static final RenderLayer[] dragonbrain$eyesLayers = new RenderLayer[BossPhase.values().length];

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/boss/dragon/EnderDragonEntity;"
            + "Lnet/minecraft/client/render/entity/state/EnderDragonEntityRenderState;F)V",
            at = @At("TAIL"), require = 0)
    private void dragonbrain$capturePhase(EnderDragonEntity dragon, EnderDragonEntityRenderState state,
                                          float tickDelta, CallbackInfo ci) {
        if (state instanceof DragonBrainRenderStateAccess access) {
            DragonBrainConfig config = ConfigManager.get();
            float ratio = dragon.getMaxHealth() > 0
                    ? dragon.getHealth() / dragon.getMaxHealth() : 1.0f;
            access.dragonbrain$setPhase(BossPhase.forHealth(ratio,
                    config.ragePhaseHealthThreshold, config.finalPhaseHealthThreshold));
            access.dragonbrain$setDying(dragon.deathTime > 0);
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/EnderDragonEntityRenderState;"
            + "Lnet/minecraft/client/util/math/MatrixStack;"
            + "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;"
            + "Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"), require = 0)
    private void dragonbrain$swapLayers(EnderDragonEntityRenderState state, MatrixStack matrices,
                                        OrderedRenderCommandQueue queue, CameraRenderState cameraState,
                                        CallbackInfo ci) {
        if (dragonbrain$vanillaCutout == null) {
            dragonbrain$vanillaCutout = DRAGON_CUTOUT;
            dragonbrain$vanillaEyes = DRAGON_EYES;
        }
        boolean custom = state instanceof DragonBrainRenderStateAccess access
                && !access.dragonbrain$isDying()
                && ConfigManager.get().customTexture;
        if (!custom) {
            DRAGON_CUTOUT = dragonbrain$vanillaCutout;
            DRAGON_EYES = dragonbrain$vanillaEyes;
            return;
        }
        BossPhase phase = ((DragonBrainRenderStateAccess) state).dragonbrain$getPhase();
        int i = phase.ordinal();
        if (dragonbrain$bodyLayers[i] == null) {
            dragonbrain$bodyLayers[i] = RenderLayers.entityCutoutNoCull(DragonTextures.bodyFor(phase));
            Identifier eyes = DragonTextures.eyesFor(phase);
            dragonbrain$eyesLayers[i] = eyes != null ? RenderLayers.eyes(eyes) : dragonbrain$vanillaEyes;
        }
        DRAGON_CUTOUT = dragonbrain$bodyLayers[i];
        DRAGON_EYES = dragonbrain$eyesLayers[i];
    }

    /** Belt and braces for any code path that still asks the renderer for a texture. */
    @Inject(method = "getTexture(Lnet/minecraft/client/render/entity/state/EnderDragonEntityRenderState;)"
            + "Lnet/minecraft/util/Identifier;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void dragonbrain$bodyTexture(EnderDragonEntityRenderState state,
                                         CallbackInfoReturnable<Identifier> cir) {
        if (state instanceof DragonBrainRenderStateAccess access && !access.dragonbrain$isDying()) {
            Identifier custom = DragonTextures.bodyFor(access.dragonbrain$getPhase());
            if (custom != null) {
                cir.setReturnValue(custom);
            }
        }
    }
}
