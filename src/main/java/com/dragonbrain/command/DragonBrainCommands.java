package com.dragonbrain.command;

import com.dragonbrain.ai.CombatManager;
import com.dragonbrain.ai.DragonCombatController;
import com.dragonbrain.config.ConfigManager;
import com.dragonbrain.core.BossPhase;
import com.dragonbrain.core.DifficultyMode;
import com.dragonbrain.core.DragonBrainConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.Locale;
import net.minecraft.command.permission.PermissionCheck;
import net.minecraft.command.DefaultPermissions;
import net.minecraft.util.Formatting;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.permission.PermissionCheck.Require;
import net.minecraft.server.command.CommandManager.RegistrationEnvironment;

public final class DragonBrainCommands {
   private static final PermissionCheck PERMISSION_CHECK = new Require(DefaultPermissions.GAMEMASTERS);

   private DragonBrainCommands() {
   }

   public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, RegistrationEnvironment environment) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal(
                                          "dragonbrain"
                                       )
                                       .requires(CommandManager.requirePermissionLevel(PERMISSION_CHECK)))
                                    .executes(context -> status(context)))
                                 .then(CommandManager.literal("status").executes(DragonBrainCommands::status)))
                              .then(CommandManager.literal("info").executes(DragonBrainCommands::status)))
                           .then(CommandManager.literal("reload").executes(DragonBrainCommands::reload)))
                        .then(CommandManager.literal("debug").executes(DragonBrainCommands::debug)))
                     .then(CommandManager.literal("help").executes(DragonBrainCommands::help)))
                  .then(
                     ((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("ai")
                              .then(CommandManager.literal("on").executes(ctx -> setEnabled(ctx, true))))
                           .then(CommandManager.literal("off").executes(ctx -> setEnabled(ctx, false))))
                        .then(
                           CommandManager.literal("status")
                              .executes(ctx -> info(ctx, "DragonBrain AI is " + (ConfigManager.get().enabled ? "enabled" : "disabled") + "."))
                        )
                  ))
               .then(CommandManager.literal("difficulty").then(CommandManager.argument("mode", StringArgumentType.word()).suggests((context, builder) -> {
                  for (DifficultyMode mode : DifficultyMode.values()) {
                     builder.suggest(mode.id());
                  }

                  return builder.buildFuture();
               }).executes(DragonBrainCommands::setDifficulty))))
            .then(CommandManager.literal("test").then(CommandManager.argument("mechanic", StringArgumentType.word()).suggests((context, builder) -> {
               for (String name : new String[]{"dive", "fireball", "gust", "pulse", "stagger", "rage", "final", "victory", "reset"}) {
                  builder.suggest(name);
               }

               return builder.buildFuture();
            }).executes(DragonBrainCommands::test)))
      );
   }

   private static int status(CommandContext<ServerCommandSource> context) {
      DragonBrainConfig config = ConfigManager.get();
      ServerCommandSource source = (ServerCommandSource)context.getSource();
      source.sendFeedback(
         () -> Text.literal("DragonBrain 2.0")
            .formatted(Formatting.LIGHT_PURPLE)
            .append(Text.literal(" — difficulty: ").formatted(Formatting.GRAY))
            .append(Text.literal(config.difficulty).formatted(Formatting.AQUA)),
         false
      );
      source.sendFeedback(
         () -> Text.literal(
            String.format(
               Locale.ROOT,
               "attackFrequency=%.2f cooldown=%.2f warning=%.2f recovery=%.2f aim=%.2f damage=%.2f",
               new Object[]{
                  config.attackFrequencyMultiplier,
                  config.cooldownMultiplier,
                  config.warningDurationMultiplier,
                  config.recoveryDurationMultiplier,
                  config.aimAccuracyMultiplier,
                  config.damageMultiplier
               }
            )
         ),
         false
      );
      source.sendFeedback(
         () -> Text.literal(
            String.format(
               Locale.ROOT,
               "mechanics: dive=%s gust=%s pulse=%s overcharge=%s stagger=%s rage=%s fakeout=%s final=%s victory=%s",
               new Object[]{
                  onOff(config.mechanicDive),
                  onOff(config.mechanicWingGust),
                  onOff(config.mechanicVoidPulse),
                  onOff(config.mechanicCrystalOvercharge),
                  onOff(config.mechanicStaggerWindow),
                  onOff(config.mechanicRagePhase),
                  onOff(config.mechanicFakeout),
                  onOff(config.mechanicFinalPhase),
                  onOff(config.mechanicVictorySequence)
               }
            )
         ),
         false
      );
      if (CombatManager.controllers().isEmpty()) {
         source.sendFeedback(() -> Text.literal("No dragon is currently being handled.").formatted(Formatting.GRAY), false);
      } else {
         for (DragonCombatController controller : CombatManager.controllers()) {
            String line = controller.debugLine();
            source.sendFeedback(() -> Text.literal(line).formatted(Formatting.DARK_GRAY), false);
            if (config.mode() == DifficultyMode.ADAPTIVE) {
               source.sendFeedback(
                  () -> Text.literal(
                        String.format(
                           Locale.ROOT,
                           "adaptive=%.2f (%s) target=%.2f",
                           new Object[]{controller.adaptive().value(), controller.adaptive().band(), controller.adaptive().target()}
                        )
                     )
                     .formatted(Formatting.DARK_AQUA),
                  false
               );
            }

            BossPhase phase = controller.phase();
            source.sendFeedback(() -> Text.literal("phase=" + phase).formatted(Formatting.DARK_PURPLE), false);
         }
      }

      return 1;
   }

   private static int reload(CommandContext<ServerCommandSource> context) {
      for (String line : ConfigManager.reload()) {
         ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal(line).formatted(Formatting.GRAY), false);
      }

      ((ServerCommandSource)context.getSource())
         .sendFeedback(
            () -> Text.literal("DragonBrain config reloaded (" + ConfigManager.get().difficulty + ").").formatted(Formatting.GREEN), true
         );
      return 1;
   }

   private static int debug(CommandContext<ServerCommandSource> context) {
      DragonBrainConfig config = ConfigManager.get();
      config.debug = !config.debug;
      ConfigManager.save();
      String message = "DragonBrain debug " + (config.debug ? "enabled" : "disabled") + " (remember to /dragonbrain reload on other worlds).";
      return info(context, message);
   }

   private static int setDifficulty(CommandContext<ServerCommandSource> context) {
      String raw = StringArgumentType.getString(context, "mode");
      DifficultyMode mode = DifficultyMode.parse(raw, null);
      if (mode == null) {
         ((ServerCommandSource)context.getSource())
            .sendError(Text.literal("Unknown difficulty '" + raw + "'. Valid values: rookie, normal, expert, adaptive."));
         return 0;
      } else {
         DragonBrainConfig config = ConfigManager.get();
         config.difficulty = mode.name();
         config.validate();
         boolean saved = ConfigManager.save();
         ((ServerCommandSource)context.getSource())
            .sendFeedback(
               () -> Text.literal("DragonBrain difficulty set to " + mode.name() + (saved ? "." : " (could not save to disk!)"))
                  .formatted(Formatting.GREEN),
               true
            );
         return 1;
      }
   }

   private static int setEnabled(CommandContext<ServerCommandSource> context, boolean enabled) {
      DragonBrainConfig config = ConfigManager.get();
      config.enabled = enabled;
      ConfigManager.save();
      ((ServerCommandSource)context.getSource())
         .sendFeedback(() -> Text.literal("DragonBrain AI " + (enabled ? "enabled" : "disabled") + ".").formatted(Formatting.GREEN), true);
      return 1;
   }

   private static int test(CommandContext<ServerCommandSource> context) {
      String mechanic = StringArgumentType.getString(context, "mechanic").toLowerCase(Locale.ROOT);
      ServerCommandSource source = (ServerCommandSource)context.getSource();
      Vec3d pos;
      if (source.getEntity() instanceof ServerPlayerEntity player) {
         pos = player.getEntityPos();
      } else {
         pos = source.getPosition();
      }

      ServerWorld world = source.getWorld();
      boolean ok = CombatManager.forceMechanic(world, pos, mechanic);
      if (!ok) {
         source.sendError(
            Text.literal(
               "Could not trigger '"
                  + mechanic
                  + "'. Valid: dive, fireball, gust, pulse, stagger, rage, final, victory, reset. A dragon must be loaded within 256 blocks."
            )
         );
         return 0;
      } else {
         source.sendFeedback(() -> Text.literal("Triggered DragonBrain mechanic: " + mechanic).formatted(Formatting.GREEN), true);
         return 1;
      }
   }

   private static int info(CommandContext<ServerCommandSource> context, String message) {
      ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal(message).formatted(Formatting.GRAY), false);
      return 1;
   }

   private static int help(CommandContext<ServerCommandSource> context) {
      String[] lines = new String[]{
         "/dragonbrain status — config summary and live boss state",
         "/dragonbrain difficulty <rookie|normal|expert|adaptive>",
         "/dragonbrain reload — re-read config/dragonbrain.json",
         "/dragonbrain debug — toggle debug logging",
         "/dragonbrain ai <on|off> — enable/disable the overhaul (vanilla dragon returns)",
         "/dragonbrain test <dive|fireball|gust|pulse|stagger|rage|final|victory|reset>"
      };

      for (String line : lines) {
         String text = line;
         ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal(text).formatted(Formatting.GRAY), false);
      }

      return 1;
   }

   private static String onOff(boolean value) {
      return value ? "on" : "off";
   }
}
