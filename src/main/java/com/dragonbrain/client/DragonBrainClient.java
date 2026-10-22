package com.dragonbrain.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DragonBrainClient implements ClientModInitializer {
   private static final Logger LOGGER = LoggerFactory.getLogger("dragonbrain/client");

   public void onInitializeClient() {
      LOGGER.info("DragonBrain client visuals ready");
   }
}
