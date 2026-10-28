package com.dragonbrain.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.DefaultPermissions;
import net.minecraft.command.permission.PermissionCheck;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import com.dragonbrain.DragonBrain;
import com.dragonbrain.config.ConfigManager;
import com.dragonbrain.config.DragonBrainConfig;
import com.dragonbrain.core.AssetManifest;
import com.dragonbrain.core.DifficultyMode;
import com.dragonbrain.core.DragonCombatController;
import com.dragonbrain.registry.ModEntities;
import com.dragonbrain.registry.ModSounds;
import com.dragonbrain.worldgen.ModWorldgen;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import java.util.List;
import java.util.Map;

/**
 * {@code /dragonbrain} — operator-only (gamemasters permission check,
 * the 1.21.11 permission system; matches the pattern shipped in 2.0).
 *
 * <pre>
 * /dragonbrain help
 * /dragonbrain status                     config + live dragon summary
 * /dragonbrain status biomes|structures|entities|items|assets|dragon
 * /dragonbrain test assets                real registry checks (90+ ids)
 * /dragonbrain test spawn <entity>        end_wisp|rift_stalker|ancient_sentinel
 * /dragonbrain test structure <structure> locates the nearest generated instance
 * /dragonbrain test boss [attack]         force a telegraphed attack via the real pipeline
 * /dragonbrain difficulty <mode>          rookie|normal|expert|adaptive
 * /dragonbrain reload | debug | info
 * </pre>
 * Status commands read the live registries; nothing here is hardcoded
 * success text. Vanilla {@code /locate}, {@code /summon} and
 * {@code /give} work with the same ids (e.g. {@code dragonbrain:void_gardens}).
 */
public final class DragonBrainCommands {
    private static final PermissionCheck OP_CHECK =
            new PermissionCheck.Require(DefaultPermissions.GAMEMASTERS);

    private static final Map<String, EntityType<?>> SPAWNABLE = Map.of(
            "end_wisp", ModEntities.END_WISP,
            "rift_stalker", ModEntities.RIFT_STALKER,
            "ancient_sentinel", ModEntities.ANCIENT_SENTINEL);

    private DragonBrainCommands() {
    }

    public static void init() {
        CommandRegistrationCallback.EVENT.register(DragonBrainCommands::register);
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                 CommandRegistryAccess registryAccess,
                                 CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("dragonbrain")
                .requires(CommandManager.requirePermissionLevel(OP_CHECK))
                .executes(ctx -> help(ctx.getSource()))
                .then(CommandManager.literal("help").executes(ctx -> help(ctx.getSource())))
                .then(CommandManager.literal("status")
                        .executes(ctx -> status(ctx.getSource()))
                        .then(CommandManager.literal("assets").executes(ctx -> statusAssets(ctx.getSource())))
                        .then(CommandManager.literal("biomes").executes(ctx -> statusBiomes(ctx.getSource())))
                        .then(CommandManager.literal("structures").executes(ctx -> statusStructures(ctx.getSource())))
                        .then(CommandManager.literal("entities").executes(ctx -> statusEntities(ctx.getSource())))
                        .then(CommandManager.literal("items").executes(ctx -> statusItems(ctx.getSource())))
                        .then(CommandManager.literal("dragon").executes(ctx -> statusDragon(ctx.getSource()))))
                .then(CommandManager.literal("info").executes(ctx -> status(ctx.getSource())))
                .then(CommandManager.literal("reload").executes(ctx -> reload(ctx.getSource())))
                .then(CommandManager.literal("debug").executes(ctx -> toggleDebug(ctx.getSource())))
                .then(CommandManager.literal("difficulty")
                        .then(CommandManager.argument("mode", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (DifficultyMode mode : DifficultyMode.values()) {
                                        builder.suggest(mode.name().toLowerCase());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> setDifficulty(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "mode")))))
                .then(CommandManager.literal("test")
                        .then(CommandManager.literal("assets").executes(ctx -> testAssets(ctx.getSource())))
                        .then(CommandManager.literal("spawn")
                                .then(CommandManager.argument("entity", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            SPAWNABLE.keySet().forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> testSpawn(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "entity")))))
                        .then(CommandManager.literal("structure")
                                .then(CommandManager.argument("structure", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            ModWorldgen.STRUCTURE_IDS.forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> testStructure(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "structure")))))
                        .then(CommandManager.literal("boss")
                                .executes(ctx -> testBoss(ctx.getSource(), DragonCombatController.Attack.NONE))
                                .then(CommandManager.argument("attack", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (DragonCombatController.Attack a
                                                    : DragonCombatController.Attack.values()) {
                                                if (a != DragonCombatController.Attack.NONE) {
                                                    builder.suggest(a.name().toLowerCase());
                                                }
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> testBoss(ctx.getSource(),
                                                parseAttack(StringArgumentType.getString(ctx, "attack"))))))
                        .then(CommandManager.argument("mechanic", StringArgumentType.word())
                                .executes(ctx -> testMechanic(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "mechanic"))))));
    }

    // ------------------------------------------------------------------ help/status
    private static int help(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal(
                "DragonBrain — /dragonbrain status [biomes|structures|entities|items|assets|dragon] | "
                        + "test assets | test spawn <entity> | test structure <structure> | test boss [attack] | "
                        + "difficulty <rookie|normal|expert|adaptive> | reload | debug | help"), false);
        return 1;
    }

    private static int status(ServerCommandSource source) {
        DragonBrainConfig config = ConfigManager.get();
        StringBuilder sb = new StringBuilder("DragonBrain 3.1.0");
        sb.append(" | enabled=").append(config.enabled)
          .append(" | difficulty=").append(config.difficultyMode())
          .append(" | dragonAttacks=").append(config.dragonAttacksEnabled)
          .append(" | customBiomes=").append(config.customBiomes)
          .append(" | customTexture=").append(config.customTexture)
          .append(" | customSounds=").append(config.customSounds)
          .append(" | debug=").append(config.debug);
        EnderDragonEntity dragon = findDragon(source);
        sb.append(dragon == null ? " | dragon: none loaded"
                : " | dragon: " + DragonCombatController.describe(dragon));
        String line = sb.toString();
        source.sendFeedback(() -> Text.literal(line), false);
        return 1;
    }

    private static int statusBiomes(ServerCommandSource source) {
        var biomeIds = AssetDiagnostics.biomeIdsOrNull(source.getServer());
        if (biomeIds == null) {
            source.sendError(Text.literal("Biome registry unavailable on this server"));
            return 0;
        }
        send(source, "End biomes (use /locate biome dragonbrain:<id> in the End):");
        for (String id : ModWorldgen.BIOME_IDS) {
            boolean present = biomeIds.contains(DragonBrain.id(id));
            send(source, " " + (present ? "✓" : "✗ MISSING") + " dragonbrain:" + id);
        }
        return 1;
    }

    private static int statusStructures(ServerCommandSource source) {
        var structureIds = AssetDiagnostics.structureIdsOrNull(source.getServer());
        if (structureIds == null) {
            source.sendError(Text.literal("Structure registry unavailable on this server"));
            return 0;
        }
        send(source, "Structures (use /locate structure dragonbrain:<id>):");
        for (String id : ModWorldgen.STRUCTURE_IDS) {
            boolean present = structureIds.contains(DragonBrain.id(id));
            send(source, " " + (present ? "✓" : "✗ MISSING") + " dragonbrain:" + id);
        }
        return 1;
    }

    private static int statusEntities(ServerCommandSource source) {
        send(source, "Entities (use /summon dragonbrain:<id>):");
        for (String id : AssetManifest.ENTITY_IDS) {
            Identifier full = DragonBrain.id(id);
            boolean present = Registries.ENTITY_TYPE.containsId(full);
            send(source, " " + (present ? "✓" : "✗ MISSING") + " " + full);
        }
        return 1;
    }

    private static int statusItems(ServerCommandSource source) {
        send(source, "Items " + AssetDiagnostics.countRegisteredItems() + "/"
                + AssetDiagnostics.totalItems() + " registered (use /give @p dragonbrain:<id>):");
        for (String id : AssetManifest.ITEM_IDS) {
            boolean present = Registries.ITEM.containsId(DragonBrain.id(id));
            send(source, " " + (present ? "✓" : "✗ MISSING") + " dragonbrain:" + id);
        }
        return 1;
    }

    private static int statusAssets(ServerCommandSource source) {
        source.sendFeedback(() -> Text.translatable("commands.dragonbrain.assets.header"), false);
        sendLine(source, "commands.dragonbrain.assets.blocks",
                AssetDiagnostics.countRegisteredBlocks() + "/" + AssetManifest.BLOCK_IDS.length);
        sendLine(source, "commands.dragonbrain.assets.items",
                AssetDiagnostics.countRegisteredItems() + "/" + AssetDiagnostics.totalItems());
        sendLine(source, "commands.dragonbrain.assets.entities",
                AssetDiagnostics.countRegisteredEntities() + "/" + AssetManifest.ENTITY_IDS.length
                        + " (+ dragon texture variants: " + AssetManifest.DRAGON_TEXTURES.length + " files)");
        sendLine(source, "commands.dragonbrain.assets.sounds",
                String.valueOf(ModSounds.registered().size()));
        sendLine(source, "commands.dragonbrain.assets.particles",
                String.valueOf(AssetManifest.PARTICLE_IDS.length));
        send(source, " biomes: " + AssetDiagnostics.countRegisteredBiomes(source.getServer())
                + "/" + ModWorldgen.BIOME_IDS.size() + " registered");
        send(source, " structures: " + AssetDiagnostics.countRegisteredStructures(source.getServer())
                + "/" + ModWorldgen.STRUCTURE_IDS.size() + " registered");
        source.sendFeedback(() -> Text.translatable("commands.dragonbrain.test.assets.note"), false);
        return 1;
    }

    private static int statusDragon(ServerCommandSource source) {
        EnderDragonEntity dragon = findDragon(source);
        if (dragon == null) {
            source.sendError(Text.literal(
                    "No Ender Dragon is currently loaded (enter the End or load the central island)"));
            return 0;
        }
        send(source, "Dragon: " + DragonCombatController.describe(dragon));
        send(source, "Phase thresholds: RAGE <= " + ConfigManager.get().ragePhaseHealthThreshold
                + ", FINAL <= " + ConfigManager.get().finalPhaseHealthThreshold
                + " | difficulty " + ConfigManager.get().difficultyMode());
        return 1;
    }

    // ------------------------------------------------------------------ tests
    private static int testAssets(ServerCommandSource source) {
        AssetDiagnostics.Report report = AssetDiagnostics.runRegistryChecks(source.getServer());
        for (String missing : report.missing()) {
            source.sendFeedback(() -> Text.translatable("commands.dragonbrain.assets.missing", missing), false);
        }
        if (report.passed()) {
            source.sendFeedback(() -> Text.translatable(
                    "commands.dragonbrain.test.assets.pass", report.present(), report.total()), false);
        } else {
            source.sendError(Text.translatable(
                    "commands.dragonbrain.test.assets.fail", report.missing().size()));
        }
        source.sendFeedback(() -> Text.translatable("commands.dragonbrain.test.assets.note"), false);
        return report.passed() ? 1 : 0;
    }

    private static int testSpawn(ServerCommandSource source, String name) {
        EntityType<?> type = SPAWNABLE.get(name.toLowerCase());
        if (type == null) {
            source.sendError(Text.literal("Unknown test entity '" + name + "'. Available: "
                    + String.join(", ", SPAWNABLE.keySet())));
            return 0;
        }
        ServerWorld world = source.getWorld();
        Entity entity = type.create(world, SpawnReason.COMMAND);
        if (entity == null) {
            source.sendError(Text.literal("Failed to create entity dragonbrain:" + name));
            return 0;
        }
        BlockPos pos = BlockPos.ofFloored(source.getPosition().add(2.0, 1.0, 0.0));
        entity.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                source.getRotation().y + 180.0f, 0.0f);
        if (!world.spawnEntity(entity)) {
            source.sendError(Text.literal("World refused to spawn dragonbrain:" + name));
            return 0;
        }
        send(source, "Spawned dragonbrain:" + name + " at " + pos.toShortString()
                + " (alive=" + entity.isAlive() + ")");
        return 1;
    }

    private static int testStructure(ServerCommandSource source, String name) {
        String id = name.toLowerCase();
        if (!ModWorldgen.STRUCTURE_IDS.contains(id)) {
            source.sendError(Text.literal("Unknown structure '" + name + "'. Available: "
                    + String.join(", ", ModWorldgen.STRUCTURE_IDS)));
            return 0;
        }
        ServerWorld end = source.getServer().getWorld(World.END);
        if (end == null) {
            source.sendError(Text.literal("The End dimension is not available on this server"));
            return 0;
        }
        BlockPos from = source.getWorld().getRegistryKey() == World.END
                ? BlockPos.ofFloored(source.getPosition())
                : new BlockPos(1500, 64, 0);
        BlockPos found = end.locateStructure(ModWorldgen.locateTag(id), from, 100, false);
        if (found == null) {
            source.sendError(Text.literal("No dragonbrain:" + id
                    + " found within 100 chunks of " + from.toShortString()
                    + " in the End (structure is registered; try from farther-out islands)"));
            return 0;
        }
        long dist = Math.round(Math.sqrt(from.getSquaredDistance(found)));
        send(source, "Nearest dragonbrain:" + id + " at [" + found.getX() + ", ~, "
                + found.getZ() + "] (" + dist + " blocks from search origin in the End)");
        return 1;
    }

    private static int testBoss(ServerCommandSource source, DragonCombatController.Attack attack) {
        EnderDragonEntity dragon = findDragon(source);
        if (dragon == null) {
            source.sendError(Text.literal(
                    "No Ender Dragon is loaded — enter the End first. This test never spawns a dragon "
                            + "(use the vanilla crystal ritual to resummon), so progression is not bypassed."));
            return 0;
        }
        ServerWorld world = (ServerWorld) dragon.getEntityWorld();
        String result = DragonCombatController.forceAttack(world, dragon, attack);
        send(source, "Boss test: " + result);
        send(source, "Dragon: " + DragonCombatController.describe(dragon));
        return 1;
    }

    private static DragonCombatController.Attack parseAttack(String name) {
        try {
            return DragonCombatController.Attack.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return DragonCombatController.Attack.NONE;
        }
    }

    private static int testMechanic(ServerCommandSource source, String mechanic) {
        ServerWorld world = source.getWorld();
        switch (mechanic.toLowerCase()) {
            case "assets" -> {
                return testAssets(source);
            }
            case "phase_transition" -> {
                world.playSound(null, BlockPos.ofFloored(source.getPosition()),
                        ModSounds.DRAGON_ABYSSAL_TRANSITION, SoundCategory.HOSTILE, 0.8f, 1.0f);
                send(source, "Played dragon_abyssal_transition (phase feedback test)");
                return 1;
            }
            case "discovery" -> {
                world.playSound(null, BlockPos.ofFloored(source.getPosition()),
                        ModSounds.DISCOVERY_CHIME, SoundCategory.PLAYERS, 0.7f, 1.0f);
                send(source, "Played discovery_chime");
                return 1;
            }
            default -> {
                source.sendError(Text.literal("Unknown mechanic '" + mechanic
                        + "'. Available: assets, phase_transition, discovery "
                        + "(or use test spawn|structure|boss)"));
                return 0;
            }
        }
    }

    // ------------------------------------------------------------------ misc
    private static EnderDragonEntity findDragon(ServerCommandSource source) {
        ServerWorld end = source.getServer().getWorld(World.END);
        if (end == null) {
            return null;
        }
        List<EnderDragonEntity> dragons = end.getEntitiesByType(EntityType.ENDER_DRAGON,
                new Box(-512, end.getBottomY(), -512, 512, end.getTopYInclusive() + 1, 512),
                d -> d.isAlive());
        return dragons.isEmpty() ? null : dragons.getFirst();
    }

    private static int reload(ServerCommandSource source) {
        ConfigManager.load();
        send(source, "DragonBrain config reloaded from " + ConfigManager.path());
        return 1;
    }

    private static int toggleDebug(ServerCommandSource source) {
        DragonBrainConfig config = ConfigManager.get();
        config.debug = !config.debug;
        ConfigManager.save();
        send(source, "DragonBrain debug = " + config.debug);
        return 1;
    }

    private static int setDifficulty(ServerCommandSource source, String mode) {
        DifficultyMode parsed = DifficultyMode.parse(mode);
        ConfigManager.get().difficulty = parsed.name();
        ConfigManager.save();
        send(source, "DragonBrain difficulty = " + parsed);
        return 1;
    }

    private static void send(ServerCommandSource source, String line) {
        source.sendFeedback(() -> Text.literal(line), false);
    }

    private static void sendLine(ServerCommandSource source, String key, String value) {
        source.sendFeedback(() -> Text.translatable(key, value), false);
    }
}
