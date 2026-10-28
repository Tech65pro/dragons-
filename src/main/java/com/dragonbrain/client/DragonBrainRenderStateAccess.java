package com.dragonbrain.client;

import com.dragonbrain.core.BossPhase;

/** Implemented by the EnderDragonRenderState mixin. */
public interface DragonBrainRenderStateAccess {
    void dragonbrain$setPhase(BossPhase phase);
    BossPhase dragonbrain$getPhase();
    void dragonbrain$setDying(boolean dying);
    boolean dragonbrain$isDying();
}
