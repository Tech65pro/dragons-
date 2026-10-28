package com.dragonbrain.worldgen;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.world.gen.feature.Feature;

import com.dragonbrain.DragonBrain;

public final class ModFeatures {
    public static final Feature<EndFormationFeature.Config> END_FORMATION =
            Registry.register(Registries.FEATURE, DragonBrain.id("end_formation"),
                    new EndFormationFeature());

    private ModFeatures() {
    }

    public static void init() {
        DragonBrain.LOGGER.debug("Registered worldgen features");
    }
}
