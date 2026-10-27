package com.dragonbrain.registry;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;

import com.dragonbrain.DragonBrain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * All 2.0 sound ids are preserved (verified from the 2.0 jar). The 3.0
 * visual-overhaul sounds are newly synthesized originals; see
 * tools/generate_sounds.py. Definitions live in assets/dragonbrain/sounds.json.
 */
public final class ModSounds {
    private static final List<SoundEvent> REGISTERED = new ArrayList<>();

    // ---- preserved from DragonBrain 2.0 ----
    public static final SoundEvent DRAGON_ROAR = register("dragon_roar", 64);
    public static final SoundEvent DIVE_WARNING = register("dive_warning", 96);
    public static final SoundEvent FIREBALL_CHARGE = register("fireball_charge", 48);
    public static final SoundEvent FIREBALL_LAUNCH = register("fireball_launch", 48);
    public static final SoundEvent WING_GUST = register("wing_gust", 64);
    public static final SoundEvent VOID_PULSE = register("void_pulse", 80);
    public static final SoundEvent CRYSTAL_OVERCHARGE = register("crystal_overcharge", 64);
    public static final SoundEvent PHASE_TRANSITION = register("phase_transition", 128);
    public static final SoundEvent RAGE = register("rage", 128);
    public static final SoundEvent VICTORY = register("victory", 128);
    public static final SoundEvent DEFEAT = register("defeat", 128);
    public static final SoundEvent AMBIENT_END = register("end_ambience");
    public static final SoundEvent MUSIC_BOSS = register("music_boss");
    public static final SoundEvent MUSIC_FINAL = register("music_final");
    public static final SoundEvent STAGGER = register("stagger", 48);

    // ---- new in 3.0 ----
    public static final SoundEvent END_WISP_AMBIENT = register("end_wisp_ambient", 24);
    public static final SoundEvent END_WISP_HURT = register("end_wisp_hurt", 24);
    public static final SoundEvent END_WISP_DEATH = register("end_wisp_death", 24);
    public static final SoundEvent RIFT_STALKER_GROWL = register("rift_stalker_growl", 32);
    public static final SoundEvent RIFT_STALKER_WINDUP = register("rift_stalker_windup", 32);
    public static final SoundEvent RIFT_STALKER_BITE = register("rift_stalker_bite", 24);
    public static final SoundEvent ANCIENT_SENTINEL_CLANG = register("ancient_sentinel_clang", 32);
    public static final SoundEvent ANCIENT_SENTINEL_AWAKEN = register("ancient_sentinel_awaken", 48);
    public static final SoundEvent ANCIENT_SENTINEL_STEP = register("ancient_sentinel_step", 24);
    public static final SoundEvent VAULT_AMBIENCE = register("vault_ambience", 32);
    public static final SoundEvent DISCOVERY_CHIME = register("discovery_chime", 32);
    public static final SoundEvent REWARD_FANFARE = register("reward_fanfare", 48);
    public static final SoundEvent RIFT_COMPASS_PING = register("rift_compass_ping", 24);
    public static final SoundEvent DRAGON_ABYSSAL_TRANSITION = register("dragon_abyssal_transition", 128);
    public static final SoundEvent ECLIPSE_LANTERN_HUM = register("eclipse_lantern_hum", 16);

    private ModSounds() {
    }

    private static SoundEvent register(String name) {
        SoundEvent event = SoundEvent.of(DragonBrain.id(name));
        REGISTERED.add(event);
        return Registry.register(Registries.SOUND_EVENT, DragonBrain.id(name), event);
    }

    private static SoundEvent register(String name, int range) {
        SoundEvent event = SoundEvent.of(DragonBrain.id(name), (float) range);
        REGISTERED.add(event);
        return Registry.register(Registries.SOUND_EVENT, DragonBrain.id(name), event);
    }

    public static List<SoundEvent> registered() {
        return Collections.unmodifiableList(REGISTERED);
    }

    public static void init() {
        DragonBrain.LOGGER.debug("Registered {} sound events", REGISTERED.size());
    }
}
