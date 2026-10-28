package com.dragonbrain.command;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import com.dragonbrain.DragonBrain;
import com.dragonbrain.core.AssetManifest;
import com.dragonbrain.worldgen.ModWorldgen;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game half of the asset/content validation story. These checks are
 * real but deliberately limited to what a running server can know:
 * presence in the live registries (including the datapack-driven biome
 * and structure registries). A server process cannot see client
 * textures/models, so pixel-level validation lives in
 * {@code ./gradlew validateAssets} (tools/validate_assets.py). The
 * command output says exactly that.
 */
public final class AssetDiagnostics {
    public record Report(int present, int total, List<String> missing) {
        public boolean passed() {
            return missing.isEmpty();
        }
    }

    private AssetDiagnostics() {
    }

    /** Identifiers registered in the biome registry, or null if unavailable. */
    public static java.util.Set<Identifier> biomeIdsOrNull(MinecraftServer server) {
        try {
            return server.getRegistryManager().get(RegistryKeys.BIOME).getIds();
        } catch (RuntimeException e) {
            DragonBrain.LOGGER.warn("Could not access biome registry: {}", e.getMessage());
            return null;
        }
    }

    /** Identifiers registered in the structure registry, or null if unavailable. */
    public static java.util.Set<Identifier> structureIdsOrNull(MinecraftServer server) {
        try {
            return server.getRegistryManager().get(RegistryKeys.STRUCTURE).getIds();
        } catch (RuntimeException e) {
            DragonBrain.LOGGER.warn("Could not access structure registry: {}", e.getMessage());
            return null;
        }
    }

    public static int countRegisteredBlocks() {
        return countPresent(AssetManifest.BLOCK_IDS,
                id -> Registries.BLOCK.containsId(DragonBrain.id(id)));
    }

    public static int countRegisteredItems() {
        int n = 0;
        for (String id : AssetManifest.BLOCK_IDS) {
            if (Registries.ITEM.containsId(DragonBrain.id(id))) {
                n++;
            }
        }
        for (String id : AssetManifest.ITEM_IDS) {
            if (Registries.ITEM.containsId(DragonBrain.id(id))) {
                n++;
            }
        }
        return n;
    }

    public static int totalItems() {
        return AssetManifest.BLOCK_IDS.length + AssetManifest.ITEM_IDS.length;
    }

    public static int countRegisteredEntities() {
        return countPresent(AssetManifest.ENTITY_IDS,
                id -> Registries.ENTITY_TYPE.containsId(DragonBrain.id(id)));
    }

    public static int countRegisteredBiomes(MinecraftServer server) {
        java.util.Set<Identifier> ids = biomeIdsOrNull(server);
        if (ids == null) {
            return 0;
        }
        int n = 0;
        for (String id : ModWorldgen.BIOME_IDS) {
            if (ids.contains(DragonBrain.id(id))) {
                n++;
            }
        }
        return n;
    }

    public static int countRegisteredStructures(MinecraftServer server) {
        java.util.Set<Identifier> ids = structureIdsOrNull(server);
        if (ids == null) {
            return 0;
        }
        int n = 0;
        for (String id : ModWorldgen.STRUCTURE_IDS) {
            if (ids.contains(DragonBrain.id(id))) {
                n++;
            }
        }
        return n;
    }

    public static Report runRegistryChecks(MinecraftServer server) {
        List<String> missing = new ArrayList<>();
        int present = 0;
        present += check(missing, "block", AssetManifest.BLOCK_IDS,
                id -> Registries.BLOCK.containsId(DragonBrain.id(id)));
        present += check(missing, "block_item", AssetManifest.BLOCK_IDS,
                id -> Registries.ITEM.containsId(DragonBrain.id(id)));
        present += check(missing, "item", AssetManifest.ITEM_IDS,
                id -> Registries.ITEM.containsId(DragonBrain.id(id)));
        present += check(missing, "entity_type", AssetManifest.ENTITY_IDS,
                id -> Registries.ENTITY_TYPE.containsId(DragonBrain.id(id)));
        present += check(missing, "sound_event", AssetManifest.SOUND_IDS,
                id -> Registries.SOUND_EVENT.containsId(DragonBrain.id(id)));
        present += check(missing, "particle_type", AssetManifest.PARTICLE_IDS,
                id -> Registries.PARTICLE_TYPE.containsId(DragonBrain.id(id)));
        java.util.Set<Identifier> biomeIds = biomeIdsOrNull(server);
        present += check(missing, "biome", ModWorldgen.BIOME_IDS.toArray(String[]::new),
                id -> biomeIds != null && biomeIds.contains(DragonBrain.id(id)));
        java.util.Set<Identifier> structureIds = structureIdsOrNull(server);
        present += check(missing, "structure", ModWorldgen.STRUCTURE_IDS.toArray(String[]::new),
                id -> structureIds != null && structureIds.contains(DragonBrain.id(id)));
        int total = AssetManifest.BLOCK_IDS.length * 2 + AssetManifest.ITEM_IDS.length
                + AssetManifest.ENTITY_IDS.length + AssetManifest.SOUND_IDS.length
                + AssetManifest.PARTICLE_IDS.length
                + ModWorldgen.BIOME_IDS.size() + ModWorldgen.STRUCTURE_IDS.size();
        return new Report(present, total, missing);
    }

    private static int countPresent(String[] ids, java.util.function.Predicate<String> test) {
        int n = 0;
        for (String id : ids) {
            if (test.test(id)) {
                n++;
            }
        }
        return n;
    }

    private static int check(List<String> missing, String kind, String[] ids,
                             java.util.function.Predicate<String> test) {
        int present = 0;
        for (String id : ids) {
            Identifier full = DragonBrain.id(id);
            if (test.test(id)) {
                present++;
            } else {
                missing.add(kind + " " + full);
            }
        }
        return present;
    }
}
