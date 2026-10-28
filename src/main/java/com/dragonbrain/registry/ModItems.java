package com.dragonbrain.registry;

import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

import com.dragonbrain.DragonBrain;
import com.dragonbrain.item.RiftCompassItem;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class ModItems {
    public static final Map<String, Item> ALL = new LinkedHashMap<>();

    public static final Item RESONANCE_SHARD = register("resonance_shard",
            s -> new Item(s.rarity(Rarity.UNCOMMON)));
    public static final Item ABYSSAL_ALLOY_INGOT = register("abyssal_alloy_ingot",
            s -> new Item(s.rarity(Rarity.RARE)));
    public static final Item VOIDGLASS_SHARD = register("voidglass_shard",
            s -> new Item(s));
    public static final Item RIFT_COMPASS = register("rift_compass",
            s -> new RiftCompassItem(s.maxCount(1).rarity(Rarity.UNCOMMON)));
    public static final Item CATACLYSM_SCALE = register("cataclysm_scale",
            s -> new Item(s.rarity(Rarity.EPIC)));
    public static final Item WISP_ESSENCE = register("wisp_essence",
            s -> new Item(s.rarity(Rarity.UNCOMMON)));
    public static final Item RIFT_FANG = register("rift_fang",
            s -> new Item(s));
    public static final Item SENTINEL_CORE = register("sentinel_core",
            s -> new Item(s.rarity(Rarity.RARE)));

    public static final Item END_WISP_SPAWN_EGG = register("end_wisp_spawn_egg",
            s -> new SpawnEggItem(s.spawnEgg(ModEntities.END_WISP)));
    public static final Item RIFT_STALKER_SPAWN_EGG = register("rift_stalker_spawn_egg",
            s -> new SpawnEggItem(s.spawnEgg(ModEntities.RIFT_STALKER)));
    public static final Item ANCIENT_SENTINEL_SPAWN_EGG = register("ancient_sentinel_spawn_egg",
            s -> new SpawnEggItem(s.spawnEgg(ModEntities.ANCIENT_SENTINEL)));

    private ModItems() {
    }

    private static Item register(String name, Function<Item.Settings, Item> factory) {
        Identifier id = DragonBrain.id(name);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        Item item = factory.apply(new Item.Settings().registryKey(key));
        ALL.put(name, item);
        return Registry.register(Registries.ITEM, key, item);
    }

    public static void init() {
        DragonBrain.LOGGER.debug("Registered {} items", ALL.size());
    }
}
