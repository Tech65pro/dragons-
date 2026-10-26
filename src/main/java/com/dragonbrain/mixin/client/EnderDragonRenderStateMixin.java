package com.dragonbrain.mixin.client;

import net.minecraft.client.render.entity.state.EnderDragonRenderState;

import com.dragonbrain.client.DragonBrainRenderStateAccess;
import com.dragonbrain.core.BossPhase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Carries the DragonBrain boss phase on the vanilla dragon render state.
 * Mirrors the 2.0 design (EnderDragonRenderStateMixin existed in 2.0).
 */
@Mixin(EnderDragonRenderState.class)
public abstract class EnderDragonRenderStateMixin implements DragonBrainRenderStateAccess {
    @Unique
    private BossPhase dragonbrain$phase = BossPhase.NORMAL;
    @Unique
    private boolean dragonbrain$dying;

    @Override
    public void dragonbrain$setPhase(BossPhase phase) {
        this.dragonbrain$phase = phase == null ? BossPhase.NORMAL : phase;
    }

    @Override
    public BossPhase dragonbrain$getPhase() {
        return this.dragonbrain$phase;
    }

    @Override
    public void dragonbrain$setDying(boolean dying) {
        this.dragonbrain$dying = dying;
    }

    @Override
    public boolean dragonbrain$isDying() {
        return this.dragonbrain$dying;
    }
}
