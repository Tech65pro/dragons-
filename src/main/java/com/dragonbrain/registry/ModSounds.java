package com.dragonbrain.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.sound.SoundEvent;
import net.minecraft.registry.Registries;

public final class ModSounds {
   private static final List<SoundEvent> ALL = new ArrayList();
   public static final SoundEvent DRAGON_ROAR = register("dragon_roar", 64.0F);
   public static final SoundEvent DIVE_WARNING = register("dive_warning", 96.0F);
   public static final SoundEvent FIREBALL_CHARGE = register("fireball_charge", 48.0F);
   public static final SoundEvent FIREBALL_LAUNCH = register("fireball_launch", 48.0F);
   public static final SoundEvent WING_GUST = register("wing_gust", 64.0F);
   public static final SoundEvent VOID_PULSE = register("void_pulse", 80.0F);
   public static final SoundEvent CRYSTAL_OVERCHARGE = register("crystal_overcharge", 64.0F);
   public static final SoundEvent PHASE_TRANSITION = register("phase_transition", 128.0F);
   public static final SoundEvent RAGE = register("rage", 128.0F);
   public static final SoundEvent VICTORY = register("victory", 128.0F);
   public static final SoundEvent DEFEAT = register("defeat", 128.0F);
   public static final SoundEvent AMBIENT_END = register("end_ambience", 96.0F);
   public static final SoundEvent MUSIC_BOSS = register("music_boss", 128.0F);
   public static final SoundEvent MUSIC_FINAL = register("music_final", 128.0F);
   public static final SoundEvent STAGGER = register("stagger", 48.0F);
   // DragonBrain 3.0 (Abyssal Cataclysm) original sounds.
   public static final SoundEvent VOID_RIFT_CHARGE = register("void_rift_charge", 64.0F);
   public static final SoundEvent VOID_RIFT_ERUPT = register("void_rift_erupt", 96.0F);
   public static final SoundEvent METEOR_WHISTLE = register("meteor_whistle", 64.0F);
   public static final SoundEvent METEOR_IMPACT = register("meteor_impact", 96.0F);
   public static final SoundEvent DREAD_ROAR = register("dread_roar", 128.0F);
   public static final SoundEvent GRAVITY_WELL = register("gravity_well", 64.0F);
   public static final SoundEvent CATACLYSM_TRANSITION = register("cataclysm_transition", 160.0F);
   public static final SoundEvent MUSIC_CATACLYSM = register("music_cataclysm", 128.0F);

   private ModSounds() {
   }

   private static SoundEvent register(String name, float range) {
      Identifier id = Identifier.of("dragonbrain", name);
      SoundEvent event = SoundEvent.of(id, range);
      SoundEvent registered = (SoundEvent)Registry.register(Registries.SOUND_EVENT, id, event);
      ALL.add(registered);
      return registered;
   }

   public static void init() {
   }

   public static List<SoundEvent> all() {
      return Collections.unmodifiableList(ALL);
   }
}
