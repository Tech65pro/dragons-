package com.dragonbrain.registry;

import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

import com.dragonbrain.DragonBrain;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;

public final class ModItemGroups {
    public static final ItemGroup MAIN = Registry.register(Registries.ITEM_GROUP,
            DragonBrain.id("main"),
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemGroup.dragonbrain.main"))
                    .icon(() -> new ItemStack(ModBlocks.ECLIPSE_LANTERN))
                    .entries((context, entries) -> {
                        ModBlocks.ALL.values().forEach(entries::add);
                        ModItems.ALL.values().forEach(entries::add);
                    })
                    .build());

    private ModItemGroups() {
    }

    public static void init() {
    }
}
