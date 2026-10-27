package com.dragonbrain.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.EntityType;
import net.minecraft.command.DefaultPermissions;
import net.minecraft.command.permission.PermissionCheck;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

import com.dragonbrain.config.ConfigManager;
import com.dragonbrain.config.DragonBrainConfig;
import com.dragonbrain.core.AssetManifest;
import com.dragonbrain.core.BossPhase;
import com.dragonbrain.core.DifficultyMode;
import com.dragonbrain.core.DragonPhaseWatcher;
import com.dragonbrain.registry.ModSounds;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * {@code /dragonbrain} — operator-only (permission level 2).
 *
 * <p>The 2.0 subcommands (status, info, reload, debug, help, difficulty,
 * test &lt;mechanic&gt;) are preserved. Task 15 adds:
 * <ul>
 *   <li>{@code /dragonbrain status assets} — implemented asset groups with
 *       live registry counts.</li>
 *   <li>{@code /dragonbrain test assets} — real in-game registry checks,
 *       with an explicit note that texture/model file validation is the
 *       {@code validateAssets} Gradle task.</li>
 * </ul>
 */
public final class DragonBrainCommands {
    /** Operator gate (gamemasters, classic level 2). 1.21.11 replaced
     *  hasPermissionLevel with the PermissionCheck system; this matches
     *  the pattern DragonBrain 2.0 shipped (PermissionCheck.Require of
     *  DefaultPermissions.GAMEMASTERS). */
    private static final PermissionCheck OP_CHECK =
            new PermissionCheck.Require(DefaultPermissions.GAMEMASTERS);

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
                        .then(CommandManager.literal("assets")
                                .executes(ctx -> statusAssets(ctx.getSource()))))
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
                        .then(CommandManager.literal("assets")
                                .executes(ctx -> testAssets(ctx.getSource())))
                        .then(CommandManager.argument("mechanic", StringArgumentType.word())
                                .executes(ctx -> testMechanic(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "mechanic"))))));
    }

    private static int help(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal(
                "DragonBrain 3.0 — /dragonbrain status | status assets | test assets | "
                        + "test <mechanic> | difficulty <rookie|normal|expert|adaptive> | "
                        + "reload | debug | help"), false);
        return 1;
    }

    private static int status(ServerCommandSource source) {
        DragonBrainConfig config = ConfigManager.get();
        StringBuilder sb = new StringBuilder();
        sb.append("DragonBrain 3.0 | enabled=").append(config.enabled)
          .append(" | difficulty=").append(config.difficultyMode())
          .append(" | debug=").append(config.debug)
          .append(" | customTexture=").append(config.customTexture)
          .append(" | customSounds=").append(config.customSounds);
        ServerWorld world = source.getWorld();
        var dragons = world.getEntitiesByType(EntityType.ENDER_DRAGON,
                new Box(-512, world.getBottomY(), -512, 512, world.getTopYInclusive() + 1, 512),
                d -> true);
        if (dragons.isEmpty()) {
            sb.append(" | dragon: none in this world");
        } else {
            EnderDragonEntity dragon = dragons.getFirst();
            BossPhase phase = DragonPhaseWatcher.lastPhase(dragon.getUuid());
            sb.append(" | dragon HP=").append(String.format("%.0f/%.0f",
                    dragon.getHealth(), dragon.getMaxHealth()));
            sb.append(" | phase=").append(phase == null ? "unobserved" : phase.name());
        }
        source.sendFeedback(() -> Text.literal(sb.toString()), false);
        return 1;
    }

    private static int statusAssets(ServerCommandSource source) {
        source.sendFeedback(() -> Text.translatable("commands.dragonbrain.assets.header"), false);
        sendLine(source, "commands.dragonbrain.assets.blocks",
                AssetDiagnostics.countRegisteredBlocks() + "/" + AssetManifest.BLOCK_IDS.length);
        sendLine(source, "commands.dragonbrain.assets.items",
                AssetDiagnostics.countRegisteredItems() + "/" + AssetDiagnostics.totalItems());
        sendLine(source, "commands.dragonbrain.assets.entities",
                AssetManifest.ENTITY_IDS.length + " (+ dragon texture variants: "
                        + AssetManifest.DRAGON_TEXTURES.length + " files)");
        sendLine(source, "commands.dragonbrain.assets.sounds",
                String.valueOf(ModSounds.registered().size()));
        sendLine(source, "commands.dragonbrain.assets.particles",
                String.valueOf(AssetManifest.PARTICLE_IDS.length));
        source.sendFeedback(() -> Text.translatable("commands.dragonbrain.test.assets.note"), false);
        return 1;
    }

    private static int testAssets(ServerCommandSource source) {
        AssetDiagnostics.Report report = AssetDiagnostics.runRegistryChecks();
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

    private static int testMechanic(ServerCommandSource source, String mechanic) {
        ServerWorld world = source.getWorld();
        switch (mechanic.toLowerCase()) {
            case "assets" -> {
                return testAssets(source);
            }
            case "phase_transition" -> {
                world.playSound(null, source.getPlayer() != null
                                ? source.getPlayer().getBlockPos()
                                : world.getSpawnPos(),
                        ModSounds.DRAGON_ABYSSAL_TRANSITION, SoundCategory.HOSTILE, 0.8f, 1.0f);
                source.sendFeedback(() -> Text.literal(
                        "Played dragon_abyssal_transition (phase feedback test)"), false);
                return 1;
            }
            case "discovery" -> {
                world.playSound(null, source.getPlayer() != null
                                ? source.getPlayer().getBlockPos()
                                : world.getSpawnPos(),
                        ModSounds.DISCOVERY_CHIME, SoundCategory.PLAYERS, 0.7f, 1.0f);
                source.sendFeedback(() -> Text.literal("Played discovery_chime"), false);
                return 1;
            }
            default -> {
                source.sendError(Text.literal("Unknown mechanic '" + mechanic
                        + "'. Available here: assets, phase_transition, discovery"));
                return 0;
            }
        }
    }

    private static int reload(ServerCommandSource source) {
        ConfigManager.load();
        source.sendFeedback(() -> Text.literal("DragonBrain config reloaded from "
                + ConfigManager.path()), true);
        return 1;
    }

    private static int toggleDebug(ServerCommandSource source) {
        DragonBrainConfig config = ConfigManager.get();
        config.debug = !config.debug;
        ConfigManager.save();
        source.sendFeedback(() -> Text.literal("DragonBrain debug = " + config.debug), true);
        return 1;
    }

    private static int setDifficulty(ServerCommandSource source, String mode) {
        DifficultyMode parsed = DifficultyMode.parse(mode);
        ConfigManager.get().difficulty = parsed.name();
        ConfigManager.save();
        source.sendFeedback(() -> Text.literal("DragonBrain difficulty = " + parsed), true);
        return 1;
    }

    private static void sendLine(ServerCommandSource source, String key, String value) {
        source.sendFeedback(() -> Text.translatable(key, value), false);
    }
}
