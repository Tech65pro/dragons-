package com.dragonbrain.registry;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

import com.dragonbrain.DragonBrain;
import com.dragonbrain.entity.AncientSentinelEntity;
import com.dragonbrain.entity.EndWispEntity;
import com.dragonbrain.entity.RiftStalkerEntity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

public final class ModEntities {
    public static final EntityType<EndWispEntity> END_WISP = register("end_wisp",
            EntityType.Builder.create(EndWispEntity::new, SpawnGroup.AMBIENT)
                    .dimensions(0.6f, 0.9f));
    public static final EntityType<RiftStalkerEntity> RIFT_STALKER = register("rift_stalker",
            EntityType.Builder.create(RiftStalkerEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.9f, 1.0f));
    public static final EntityType<AncientSentinelEntity> ANCIENT_SENTINEL = register("ancient_sentinel",
            EntityType.Builder.create(AncientSentinelEntity::new, SpawnGroup.MONSTER)
                    .dimensions(1.2f, 2.7f));

    private ModEntities() {
    }

    private static <T extends net.minecraft.entity.Entity> EntityType<T> register(
            String name, EntityType.Builder<T> builder) {
        RegistryKey<EntityType<?>> key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, DragonBrain.id(name));
        return Registry.register(Registries.ENTITY_TYPE, key, builder.build(key));
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(END_WISP, EndWispEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(RIFT_STALKER, RiftStalkerEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ANCIENT_SENTINEL, AncientSentinelEntity.createAttributes());
    }
}
