package com.dragonbrain.mixin.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EnderDragonEntityRenderer;
import net.minecraft.client.render.entity.state.EnderDragonRenderState;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.util.Identifier;

import com.dragonbrain.client.DragonBrainRenderStateAccess;
import com.dragonbrain.client.DragonTextures;
import com.dragonbrain.config.ConfigManager;
import com.dragonbrain.config.DragonBrainConfig;
import com.dragonbrain.core.BossPhase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Abyssal Cataclysm dragon appearance. Preserves vanilla rendering:
 * <ul>
 *   <li>only the body/eyes <em>textures</em> are swapped — model, crystal
 *       beams, death explosion and all vanilla mechanics are untouched;</li>
 *   <li>while the dragon is dying, the vanilla exploding texture wins
 *       (we never override in that state);</li>
 *   <li>{@code customTexture=false} in the config restores vanilla
 *       textures entirely; {@code emissiveEyes=false} restores vanilla eyes;</li>
 *   <li>phase is derived from the same health thresholds the server-side
 *       DragonPhaseWatcher uses, so visuals match sounds/particles.</li>
 * </ul>
 * All injections use {@code require = 0}: if a future hotfix renames a
 * renderer method, the worst case is vanilla textures — never a crash.
 */
@Mixin(EnderDragonEntityRenderer.class)
public abstract class EnderDragonRendererMixin {

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/boss/dragon/EnderDragonEntity;"
            + "Lnet/minecraft/client/render/entity/state/EnderDragonRenderState;F)V",
            at = @At("TAIL"), require = 0)
    private void dragonbrain$capturePhase(EnderDragonEntity dragon, EnderDragonRenderState state,
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

    @Inject(method = "getTexture(Lnet/minecraft/client/render/entity/state/EnderDragonRenderState;)"
            + "Lnet/minecraft/util/Identifier;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void dragonbrain$bodyTexture(EnderDragonRenderState state,
                                         CallbackInfoReturnable<Identifier> cir) {
        if (state instanceof DragonBrainRenderStateAccess access && !access.dragonbrain$isDying()) {
            Identifier custom = DragonTextures.bodyFor(access.dragonbrain$getPhase());
            if (custom != null) {
                cir.setReturnValue(custom);
            }
        }
    }

    /**
     * The eyes/glow pass obtains its vertex consumer via
     * {@code RenderLayer.getEyes(Identifier)}; substitute the phase's
     * emissive texture when enabled. Ordinal-guarded and optional.
     */
    @ModifyArg(method = "render(Lnet/minecraft/client/render/entity/state/EnderDragonRenderState;"
            + "Lnet/minecraft/client/util/math/MatrixStack;"
            + "Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/RenderLayer;getEyes("
                            + "Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;"),
            index = 0, require = 0)
    private Identifier dragonbrain$eyesTexture(Identifier original, EnderDragonRenderState state,
                                               net.minecraft.client.util.math.MatrixStack matrices,
                                               net.minecraft.client.render.VertexConsumerProvider consumers,
                                               int light) {
        if (state instanceof DragonBrainRenderStateAccess access && !access.dragonbrain$isDying()) {
            Identifier custom = DragonTextures.eyesFor(access.dragonbrain$getPhase());
            if (custom != null) {
                return custom;
            }
        }
        return original;
    }
}
