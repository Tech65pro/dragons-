package com.dragonbrain.config;

import com.dragonbrain.core.DragonBrainConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigManager {
   private static final Logger LOGGER = LoggerFactory.getLogger("dragonbrain/config");
   private static final String FILE_NAME = "dragonbrain.json";
   private static final String README_NAME = "dragonbrain.readme.txt";
   private static volatile DragonBrainConfig config = DragonBrainConfig.defaults();
   private static volatile long revision;

   private ConfigManager() {
   }

   public static DragonBrainConfig get() {
      return config;
   }

   public static long revision() {
      return revision;
   }

   private static Path configPath() {
      return FabricLoader.getInstance().getConfigDir().resolve("dragonbrain.json");
   }

   public static void load() {
      Path path = configPath();

      try {
         if (!Files.exists(path, new LinkOption[0])) {
            Files.createDirectories(path.getParent(), new FileAttribute[0]);
            DragonBrainConfig fresh = DragonBrainConfig.defaults();
            fresh.validate();
            Files.writeString(path, fresh.toJson(), StandardCharsets.UTF_8, new OpenOption[0]);
            writeReadme(path.getParent());
            LOGGER.info("Created default DragonBrain config at {}", path);
            config = fresh;
            revision++;
            return;
         }

         String json = Files.readString(path, StandardCharsets.UTF_8);
         DragonBrainConfig loaded = DragonBrainConfig.fromJson(json);
         config = loaded;
         revision++;
         if (!loaded.corrections.isEmpty()) {
            LOGGER.warn("DragonBrain config had {} issue(s):", loaded.corrections.size());

            for (String correction : loaded.corrections) {
               LOGGER.warn("  - {}", correction);
            }
         }

         LOGGER.info("Loaded DragonBrain config (difficulty={})", loaded.difficulty);
      } catch (IOException | RuntimeException ex) {
         LOGGER.error("Could not read DragonBrain config, using in-memory defaults", ex);
         DragonBrainConfig fallback = DragonBrainConfig.defaults();
         fallback.validate();
         config = fallback;
         revision++;
      }
   }

   public static List<String> reload() {
      load();
      List<String> lines = new ArrayList(config.corrections);
      if (lines.isEmpty()) {
         lines.add("Config reloaded with no issues.");
      }

      return lines;
   }

   public static boolean save() {
      try {
         Path path = configPath();
         Files.createDirectories(path.getParent(), new FileAttribute[0]);
         Files.writeString(path, config.toJson(), StandardCharsets.UTF_8, new OpenOption[0]);
         return true;
      } catch (IOException ex) {
         LOGGER.error("Could not save DragonBrain config", ex);
         return false;
      }
   }

   private static void writeReadme(Path dir) {
      String readme = "DragonBrain 3.0 configuration (Abyssal Cataclysm)\n==================================================\n\nFile: dragonbrain.json\nReload in game with: /dragonbrain reload\nThe server and the client both read this file (the client only uses the\npresentation values: customTexture, emissiveEyes, sound/music volume).\nConfigs written by DragonBrain 2.0 load unchanged; new 3.0 options simply\nreceive their defaults.\n\ndifficulty                     ROOKIE | NORMAL | EXPERT | ADAPTIVE\nattackFrequencyMultiplier      >1 = attacks less often (0.4 - 3.0)\ncooldownMultiplier             >1 = longer per-attack cooldowns (0.4 - 3.0)\nwarningDurationMultiplier      >1 = longer telegraphs (0.5 - 3.0). A hard floor of\n                               8 ticks is always enforced, even if you ask for less.\n                               The 3.0 major attacks carry their own higher floors\n                               (14-16 ticks) so they always stay readable.\nrecoveryDurationMultiplier     >1 = longer downtime after each attack\naimAccuracyMultiplier          >1 = the dragon aims better (misses less)\npredictionStrengthMultiplier   0 = no prediction (easiest to dodge), 2 = heavy leading\nreactionDelayTicks             extra aiming delay in ticks (0 - 40)\nprojectileSpeedMultiplier      fireball speed (0.5 - 2.0)\ndamageMultiplier               scales all damage the dragon deals to players\nenforceMinimumDamageInterval   at most one dragon hit per minimum interval (anti-spam)\nconsecutiveHitProtection       after repeated hits the dragon repositions instead\nmechanic*                      individual mechanic toggles (dive, gust, pulse,\n                               overcharge, stagger, rage, fakeout, final, victory,\n                               and 3.0: void rift, meteor storm, dread roar,\n                               gravity well, cataclysm phase)\nrageHealthThreshold            health ratio that starts the rage phase\nfinalPhaseHealthThreshold      health ratio that starts the final phase\ncataclysmHealthThreshold       health ratio that starts the cataclysm phase (3.0)\nvoidRiftDamage / meteorDamage / dreadRoarDamage / gravityWellDamage\n                               per-ability damage before damageMultiplier (3.0)\n*CooldownMultiplier            per-ability cooldown scales for the 3.0 powers\nmeteorCount / meteorRadius     meteor storm size (count scales with difficulty)\ndreadRoarRadius                dread roar blast radius\ngravityWellRadius              gravity well boundary radius\ngravityWellPullStrength        inward pull per tick, hard-capped so the well\n                               always stays escapable on foot (max 0.35)\ncataclysmAura                  particle aura + attack trails in cataclysm phase\ncataclysmMusic                 dedicated cataclysm boss track (else final track)\nparticleDensityMultiplier      scales the new 3.0 visual effect budgets (0.25 - 2.0)\ncustomTexture                  use the DragonBrain dragon texture (client side)\nemissiveEyes                   use the glowing eye layer (client side)\ncustomSounds / bossMusic       toggle DragonBrain audio\nambientEndAudio / ambientParticles   End atmosphere toggles\nsoundVolume / musicVolume / ambientVolume   0.0 - 2.0\n\nInvalid values never crash the server: they are clamped and reported in the log\nand by /dragonbrain reload.\n";

      try {
         Files.writeString(dir.resolve("dragonbrain.readme.txt"), readme, StandardCharsets.UTF_8, new OpenOption[0]);
      } catch (IOException var3) {
      }
   }
}
