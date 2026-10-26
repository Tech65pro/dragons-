package com.dragonbrain.registry;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.TransparentBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

import com.dragonbrain.DragonBrain;
import com.dragonbrain.block.ChorusRootsBlock;
import com.dragonbrain.block.EclipseLanternBlock;
import com.dragonbrain.block.VoidLilyBlock;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Every block here has a matching texture, model, blockstate, item
 * definition, loot table and translation — enforced by
 * tools/validate_assets.py and {@code /dragonbrain test assets}.
 */
public final class ModBlocks {
    public static final Map<String, Block> ALL = new LinkedHashMap<>();

    // ---- Void Gardens ----
    public static final Block LUMEN_MOSS_BLOCK = register("lumen_moss_block",
            s -> new Block(s.mapColor(MapColor.PURPLE).sounds(BlockSoundGroup.MOSS_BLOCK)
                    .strength(0.6f).luminance(state -> 6)));
    public static final Block GLOAM_PETAL_BLOCK = register("gloam_petal_block",
            s -> new Block(s.mapColor(MapColor.MAGENTA).sounds(BlockSoundGroup.WOOD)
                    .strength(0.8f).luminance(state -> 3)));
    public static final Block VOID_LILY = register("void_lily",
            s -> new VoidLilyBlock(s.mapColor(MapColor.MAGENTA).sounds(BlockSoundGroup.CAVE_VINES)
                    .noCollision().breakInstantly().nonOpaque()
                    .luminance(state -> 9).offset(AbstractBlock.OffsetType.XZ)));

    // ---- Chorus Wilds ----
    public static final Block CHORUS_BARK_LOG = register("chorus_bark_log",
            s -> new PillarBlock(s.mapColor(MapColor.PURPLE).sounds(BlockSoundGroup.WOOD)
                    .strength(1.6f, 3.0f)));
    public static final Block CHORUS_CANOPY = register("chorus_canopy",
            s -> new TransparentBlock(s.mapColor(MapColor.PURPLE).sounds(BlockSoundGroup.AZALEA_LEAVES)
                    .strength(0.25f).nonOpaque().suffocates(Blocks::never)
                    .blockVision(Blocks::never).luminance(state -> 2)));
    public static final Block WILD_CHORUS_BULB = register("wild_chorus_bulb",
            s -> new Block(s.mapColor(MapColor.PURPLE).sounds(BlockSoundGroup.WOOD)
                    .strength(1.2f, 3.0f).luminance(state -> 4)));
    public static final Block CHORUS_ROOTS = register("chorus_roots",
            s -> new ChorusRootsBlock(s.mapColor(MapColor.PURPLE).sounds(BlockSoundGroup.ROOTS)
                    .noCollision().breakInstantly().nonOpaque()
                    .offset(AbstractBlock.OffsetType.XZ)));

    // ---- Shattered Expanse ----
    public static final Block FRACTURED_END_STONE = register("fractured_end_stone",
            s -> new Block(s.mapColor(MapColor.PALE_YELLOW).requiresTool()
                    .strength(2.2f, 9.0f).sounds(BlockSoundGroup.STONE)));
    public static final Block SHATTERED_PILLAR = register("shattered_pillar",
            s -> new PillarBlock(s.mapColor(MapColor.PALE_YELLOW).requiresTool()
                    .strength(2.5f, 9.0f).sounds(BlockSoundGroup.STONE)));
    public static final Block ENDSTONE_RUBBLE = register("endstone_rubble",
            s -> new Block(s.mapColor(MapColor.PALE_YELLOW).requiresTool()
                    .strength(1.2f, 6.0f).sounds(BlockSoundGroup.GRAVEL)));
    public static final Block CRACKED_ENDSTONE_BRICKS = register("cracked_endstone_bricks",
            s -> new Block(s.mapColor(MapColor.PALE_YELLOW).requiresTool()
                    .strength(2.5f, 9.0f).sounds(BlockSoundGroup.STONE)));

    // ---- Petrified Abyss ----
    public static final Block PETRIFIED_STONE = register("petrified_stone",
            s -> new Block(s.mapColor(MapColor.DEEPSLATE_GRAY).requiresTool()
                    .strength(3.0f, 12.0f).sounds(BlockSoundGroup.DEEPSLATE)));
    public static final Block FOSSIL_BLOCK = register("fossil_block",
            s -> new Block(s.mapColor(MapColor.DEEPSLATE_GRAY).requiresTool()
                    .strength(3.0f, 12.0f).sounds(BlockSoundGroup.DEEPSLATE_BRICKS)));
    public static final Block ENGRAVED_ABYSSAL_BRICKS = register("engraved_abyssal_bricks",
            s -> new Block(s.mapColor(MapColor.BLACK).requiresTool()
                    .strength(3.5f, 15.0f).sounds(BlockSoundGroup.DEEPSLATE_TILES)));
    public static final Block WEATHERED_MASONRY = register("weathered_masonry",
            s -> new Block(s.mapColor(MapColor.DEEPSLATE_GRAY).requiresTool()
                    .strength(2.6f, 10.0f).sounds(BlockSoundGroup.DEEPSLATE_BRICKS)));

    // ---- Astral Barrens ----
    public static final Block ASTRAL_DUST_BLOCK = register("astral_dust_block",
            s -> new Block(s.mapColor(MapColor.GRAY).requiresTool()
                    .strength(1.4f, 6.0f).sounds(BlockSoundGroup.SCULK)));
    public static final Block STARSHALE = register("starshale",
            s -> new Block(s.mapColor(MapColor.GRAY).requiresTool()
                    .strength(2.8f, 10.0f).sounds(BlockSoundGroup.BASALT)
                    .luminance(state -> 2)));
    public static final Block ASTRAL_CRYSTAL_BLOCK = register("astral_crystal_block",
            s -> new Block(s.mapColor(MapColor.LIGHT_BLUE).requiresTool()
                    .strength(2.0f, 8.0f).sounds(BlockSoundGroup.AMETHYST_BLOCK)
                    .luminance(state -> 11)));

    // ---- functional / special ----
    public static final Block VOIDGLASS = register("voidglass",
            s -> new TransparentBlock(s.mapColor(MapColor.BLUE).requiresTool()
                    .strength(0.4f, 1200.0f).sounds(BlockSoundGroup.GLASS)
                    .nonOpaque().suffocates(Blocks::never).blockVision(Blocks::never)));
    public static final Block ABYSSAL_ALLOY_BLOCK = register("abyssal_alloy_block",
            s -> new Block(s.mapColor(MapColor.PURPLE).requiresTool()
                    .strength(5.0f, 18.0f).sounds(BlockSoundGroup.NETHERITE)
                    .luminance(state -> 4)));
    public static final Block ECLIPSE_LANTERN = register("eclipse_lantern",
            s -> new EclipseLanternBlock(s.mapColor(MapColor.PURPLE).requiresTool()
                    .strength(3.0f, 10.0f).sounds(BlockSoundGroup.METAL)
                    .nonOpaque().luminance(state -> 15)));
    public static final Block ABYSSAL_TROPHY = register("abyssal_trophy",
            s -> new Block(s.mapColor(MapColor.BLACK).requiresTool()
                    .strength(4.0f, 18.0f).sounds(BlockSoundGroup.DEEPSLATE_TILES)
                    .nonOpaque().luminance(state -> 5)));

    private ModBlocks() {
    }

    private static Block register(String name, Function<AbstractBlock.Settings, Block> factory) {
        Identifier id = DragonBrain.id(name);
        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, id);
        Block block = factory.apply(AbstractBlock.Settings.create().registryKey(blockKey));
        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, id);
        BlockItem item = new BlockItem(block, new Item.Settings().registryKey(itemKey));
        Registry.register(Registries.ITEM, itemKey, item);
        ALL.put(name, block);
        return Registry.register(Registries.BLOCK, blockKey, block);
    }

    public static void init() {
        DragonBrain.LOGGER.debug("Registered {} blocks", ALL.size());
    }
}
