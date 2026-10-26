package com.dragonbrain.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("DragonBrain/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static DragonBrainConfig config = new DragonBrainConfig();

    private ConfigManager() {
    }

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("dragonbrain.json");
    }

    public static DragonBrainConfig get() {
        return config;
    }

    public static synchronized void load() {
        Path path = path();
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                DragonBrainConfig loaded = GSON.fromJson(reader, DragonBrainConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (Exception e) {
                LOGGER.warn("Could not read {}, using defaults: {}", path, e.getMessage());
                config = new DragonBrainConfig();
            }
        }
        config.clamp();
        save();
    }

    public static synchronized void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not write {}: {}", path, e.getMessage());
        }
    }
}
