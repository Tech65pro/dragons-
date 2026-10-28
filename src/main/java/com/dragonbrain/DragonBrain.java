package com.dragonbrain;

import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dragonbrain.command.DragonBrainCommands;
import com.dragonbrain.config.ConfigManager;
import com.dragonbrain.core.DragonCombatController;
import com.dragonbrain.registry.ModBlocks;
import com.dragonbrain.registry.ModEntities;
import com.dragonbrain.registry.ModItemGroups;
import com.dragonbrain.registry.ModItems;
import com.dragonbrain.registry.ModParticles;
import com.dragonbrain.registry.ModSounds;
import com.dragonbrain.registry.ModSpawns;
import com.dragonbrain.worldgen.ModWorldgen;

import net.fabricmc.api.ModInitializer;

/**
 * DragonBrain 3.0 entry point.
 *
 * <p>The 3.0 source tree was reconstructed in this repository because only
 * the compiled DragonBrain 2.0 jar was available here. The 2.0 public
 * surface that could be verified from that jar (mod id, packages, boss
 * phases NORMAL/RAGE/FINAL, difficulty modes, all 15 sound ids, config
 * keys such as {@code customTexture}, and the {@code /dragonbrain}
 * command tree) is preserved. Task 15 adds the complete visual overhaul
 * and the content those assets belong to.
 */
public class DragonBrain implements ModInitializer {
    public static final String MOD_ID = "dragonbrain";
    public static final Logger LOGGER = LoggerFactory.getLogger("DragonBrain");

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ConfigManager.load();
        ModSounds.init();
        ModParticles.init();
        ModBlocks.init();
        ModItems.init();
        ModEntities.init();
        ModItemGroups.init();
        ModSpawns.init();
        ModWorldgen.init();
        DragonBrainCommands.init();
        DragonCombatController.init();
        LOGGER.info("DragonBrain 3.0 initialized (difficulty={})",
                ConfigManager.get().difficultyMode());
    }
}
