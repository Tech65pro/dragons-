package com.dragonbrain.client;

import net.minecraft.client.render.entity.model.EntityModelLayer;

import com.dragonbrain.DragonBrain;

public final class ModModelLayers {
    public static final EntityModelLayer END_WISP =
            new EntityModelLayer(DragonBrain.id("end_wisp"), "main");
    public static final EntityModelLayer RIFT_STALKER =
            new EntityModelLayer(DragonBrain.id("rift_stalker"), "main");
    public static final EntityModelLayer ANCIENT_SENTINEL =
            new EntityModelLayer(DragonBrain.id("ancient_sentinel"), "main");

    private ModModelLayers() {
    }
}
