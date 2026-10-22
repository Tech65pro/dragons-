package com.dragonbrain;

import com.dragonbrain.ai.CombatManager;
import com.dragonbrain.command.DragonBrainCommands;
import com.dragonbrain.config.ConfigManager;
import com.dragonbrain.core.DragonBrainConfig;
import com.dragonbrain.registry.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AfterDamage;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AfterDeath;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AllowDamage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DragonBrain implements ModInitializer {
   public static final String MOD_ID = "dragonbrain";
   public static final Logger LOGGER = LoggerFactory.getLogger("dragonbrain");

   public void onInitialize() {
      ModSounds.init();
      ConfigManager.load();
      DragonBrainConfig config = ConfigManager.get();
      ServerLivingEntityEvents.ALLOW_DAMAGE.register((AllowDamage)(entity, source, amount) -> CombatManager.allowDragonDamage(entity, source, amount));
      ServerLivingEntityEvents.AFTER_DAMAGE
         .register((AfterDamage)(entity, source, baseDamage, damageTaken, blocked) -> CombatManager.onDamageDealt(entity, source, damageTaken));
      ServerLivingEntityEvents.AFTER_DEATH.register((AfterDeath)(entity, source) -> {
         if (entity instanceof EnderDragonEntity dragon) {
            CombatManager.onDragonDeath(dragon);
         }
      });
      ServerTickEvents.END_SERVER_TICK.register(CombatManager::onServerTick);
      ServerLifecycleEvents.SERVER_STOPPED.register((ServerStopped)server -> CombatManager.onServerStopped());
      CommandRegistrationCallback.EVENT.register(DragonBrainCommands::register);
      LOGGER.info("DragonBrain 2.0 loaded (difficulty={}, enabled={})", config.difficulty, config.enabled);
   }
}
