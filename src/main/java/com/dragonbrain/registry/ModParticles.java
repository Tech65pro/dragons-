package com.dragonbrain.registry;

import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import com.dragonbrain.DragonBrain;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;

public final class ModParticles {
    public static final SimpleParticleType VOID_MOTE =
            Registry.register(Registries.PARTICLE_TYPE, DragonBrain.id("void_mote"),
                    FabricParticleTypes.simple());
    public static final SimpleParticleType RESONANCE_SPARK =
            Registry.register(Registries.PARTICLE_TYPE, DragonBrain.id("resonance_spark"),
                    FabricParticleTypes.simple());

    private ModParticles() {
    }

    public static void init() {
    }
}
