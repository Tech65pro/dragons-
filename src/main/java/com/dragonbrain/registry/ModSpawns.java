package com.dragonbrain.registry;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.BiomeKeys;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;

/**
 * Conservative natural spawning in the outer End islands. The Ancient
 * Sentinel intentionally does NOT does not spawn ambiently; it is placed by
 * structures/commands (and its spawn egg) so ruins stay authored.
 */
public final class ModSpawns {
    private ModSpawns() {
    }

    public static void init() {
        SpawnRestriction.register(ModEntities.RIFT_STALKER, SpawnLocationTypes.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                HostileEntity::canSpawnIgnoreLightLevel);
        BiomeModifications.addSpawn(
                BiomeSelectors.includeByKey(
                        BiomeKeys.END_HIGHLANDS, BiomeKeys.END_MIDLANDS,
                        BiomeKeys.END_BARRENS, BiomeKeys.SMALL_END_ISLANDS),
                SpawnGroup.AMBIENT, ModEntities.END_WISP, 8, 1, 3);
        BiomeModifications.addSpawn(
                BiomeSelectors.includeByKey(BiomeKeys.END_HIGHLANDS, BiomeKeys.END_BARRENS),
                SpawnGroup.MONSTER, ModEntities.RIFT_STALKER, 6, 1, 2);
    }
}
