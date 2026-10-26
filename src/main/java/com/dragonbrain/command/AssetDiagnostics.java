package com.dragonbrain.command;

import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import com.dragonbrain.DragonBrain;
import com.dragonbrain.core.AssetManifest;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game half of the asset validation story. These checks are real but
 * deliberately limited to what a running server can know: whether every
 * content id in the generated {@link AssetManifest} is present in the
 * live registries. A server process cannot see client textures/models,
 * so pixel-level validation lives in {@code ./gradlew validateAssets}
 * (tools/validate_assets.py). The command output says exactly that.
 */
public final class AssetDiagnostics {
    public record Report(int present, int total, List<String> missing) {
        public boolean passed() {
            return missing.isEmpty();
        }
    }

    private AssetDiagnostics() {
    }

    public static int countRegisteredBlocks() {
        return countPresent(AssetManifest.BLOCK_IDS,
                id -> Registries.BLOCK.containsId(DragonBrain.id(id)));
    }

    public static int countRegisteredItems() {
        // block items share the block ids; count blocks' items + standalone items
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

    public static Report runRegistryChecks() {
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
        int total = AssetManifest.BLOCK_IDS.length * 2 + AssetManifest.ITEM_IDS.length
                + AssetManifest.ENTITY_IDS.length + AssetManifest.SOUND_IDS.length
                + AssetManifest.PARTICLE_IDS.length;
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
