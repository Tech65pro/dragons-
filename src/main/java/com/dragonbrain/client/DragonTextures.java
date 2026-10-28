package com.dragonbrain.client;

import net.minecraft.util.Identifier;

import com.dragonbrain.DragonBrain;
import com.dragonbrain.config.ConfigManager;
import com.dragonbrain.core.BossPhase;

/**
 * Abyssal Cataclysm dragon textures. Names/locations for the normal and
 * final sets are preserved from DragonBrain 2.0
 * (textures/entity/enderdragon/dragon.png etc.); 3.0 adds the missing
 * RAGE set so every real {@link BossPhase} has its own appearance.
 *
 * <p>All six textures keep the exact alpha/UV islands of the 2.0 sheets,
 * so they remain compatible with the vanilla dragon model and any
 * renderer that understood the 2.0 textures.
 */
public final class DragonTextures {
    public static final Identifier BODY = tex("dragon.png");
    public static final Identifier EYES = tex("dragon_eyes.png");
    public static final Identifier RAGE_BODY = tex("dragon_rage.png");
    public static final Identifier RAGE_EYES = tex("dragon_rage_eyes.png");
    public static final Identifier FINAL_BODY = tex("dragon_final.png");
    public static final Identifier FINAL_EYES = tex("dragon_final_eyes.png");

    private DragonTextures() {
    }

    private static Identifier tex(String name) {
        return DragonBrain.id("textures/entity/enderdragon/" + name);
    }

    /** Body texture for a phase, or null when custom textures are disabled. */
    public static Identifier bodyFor(BossPhase phase) {
        if (!ConfigManager.get().customTexture) {
            return null;
        }
        return switch (phase) {
            case NORMAL -> BODY;
            case RAGE -> RAGE_BODY;
            case FINAL -> FINAL_BODY;
        };
    }

    /** Emissive eyes/cracks texture for a phase, or null when disabled. */
    public static Identifier eyesFor(BossPhase phase) {
        if (!ConfigManager.get().customTexture || !ConfigManager.get().emissiveEyes) {
            return null;
        }
        return switch (phase) {
            case NORMAL -> EYES;
            case RAGE -> RAGE_EYES;
            case FINAL -> FINAL_EYES;
        };
    }
}
