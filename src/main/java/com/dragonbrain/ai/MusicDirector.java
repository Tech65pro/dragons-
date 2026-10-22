package com.dragonbrain.ai;

import com.dragonbrain.core.BossPhase;
import com.dragonbrain.core.DragonBrainConfig;
import com.dragonbrain.registry.ModSounds;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundCategory;

final class MusicDirector {
   private static final int BOSS_TRACK_TICKS = 920;
   private static final int FINAL_TRACK_TICKS = 800;
   private MusicDirector.Track current = MusicDirector.Track.NONE;
   private int ticks;
   private int trackLength;

   void tick(ServerWorld world, EnderDragonEntity dragon, BossPhase phase, boolean inCombat, DragonBrainConfig config) {
      if (config.bossMusic && !(config.musicVolume <= 0.01F)) {
         MusicDirector.Track desired = inCombat
            ? (phase == BossPhase.FINAL ? MusicDirector.Track.FINAL : MusicDirector.Track.COMBAT)
            : MusicDirector.Track.NONE;
         if (desired == MusicDirector.Track.NONE) {
            this.current = MusicDirector.Track.NONE;
         } else {
            if (desired != this.current || this.ticks >= this.trackLength) {
               this.current = desired;
               this.ticks = 0;
               this.trackLength = desired == MusicDirector.Track.FINAL ? 800 : 920;
               SoundEvent sound = desired == MusicDirector.Track.FINAL ? ModSounds.MUSIC_FINAL : ModSounds.MUSIC_BOSS;
               Vec3d pos = dragon.getEntityPos();
               world.playSound(null, pos.x, pos.y, pos.z, sound, SoundCategory.MUSIC, 0.9F * config.musicVolume, 1.0F);
            }

            this.ticks++;
         }
      } else {
         this.current = MusicDirector.Track.NONE;
      }
   }

   void onDying(ServerWorld world, EnderDragonEntity dragon) {
      this.current = MusicDirector.Track.NONE;
   }

   void stop(ServerWorld world) {
      this.current = MusicDirector.Track.NONE;
      this.ticks = 0;
   }

   private enum Track {
      NONE,
      COMBAT,
      FINAL;
   }
}
