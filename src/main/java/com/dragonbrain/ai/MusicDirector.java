package com.dragonbrain.ai;

import com.dragonbrain.core.BossPhase;
import com.dragonbrain.core.DragonBrainConfig;
import com.dragonbrain.registry.ModSounds;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundCategory;

final class MusicDirector {
   private static final int BOSS_TRACK_TICKS = 920;
   private static final int FINAL_TRACK_TICKS = 800;
   // Matches the generated music_cataclysm.ogg duration (42.0 s * 20 tps).
   private static final int CATACLYSM_TRACK_TICKS = 840;
   private MusicDirector.Track current = MusicDirector.Track.NONE;
   private int ticks;
   private int trackLength;

   void tick(ServerWorld world, EnderDragonEntity dragon, BossPhase phase, boolean inCombat, DragonBrainConfig config) {
      if (config.bossMusic && !(config.musicVolume <= 0.01F)) {
         MusicDirector.Track desired;
         if (!inCombat) {
            desired = MusicDirector.Track.NONE;
         } else if (phase == BossPhase.CATACLYSM && config.cataclysmMusic) {
            desired = MusicDirector.Track.CATACLYSM;
         } else if (phase == BossPhase.FINAL || phase == BossPhase.CATACLYSM) {
            desired = MusicDirector.Track.FINAL;
         } else {
            desired = MusicDirector.Track.COMBAT;
         }

         if (desired == MusicDirector.Track.NONE) {
            if (this.current != MusicDirector.Track.NONE) {
               this.stopTrack(world, dragon, this.current);
               this.current = MusicDirector.Track.NONE;
            }
         } else {
            if (desired != this.current || this.ticks >= this.trackLength) {
               if (desired != this.current && this.current != MusicDirector.Track.NONE) {
                  // DragonBrain 3.0: stop the outgoing track explicitly so a
                  // phase change never leaves two boss tracks overlapping.
                  this.stopTrack(world, dragon, this.current);
               }
               this.current = desired;
               this.ticks = 0;
               this.trackLength = switch (desired) {
                  case FINAL -> 800;
                  case CATACLYSM -> 840;
                  default -> 920;
               };
               SoundEvent sound = soundOf(desired);
               Vec3d pos = dragon.getEntityPos();
               world.playSound(null, pos.x, pos.y, pos.z, sound, SoundCategory.MUSIC, 0.9F * config.musicVolume, 1.0F);
            }

            this.ticks++;
         }
      } else if (this.current != MusicDirector.Track.NONE) {
         this.stopTrack(world, dragon, this.current);
         this.current = MusicDirector.Track.NONE;
      }
   }

   void onDying(ServerWorld world, EnderDragonEntity dragon) {
      if (this.current != MusicDirector.Track.NONE) {
         this.stopTrack(world, dragon, this.current);
         this.current = MusicDirector.Track.NONE;
      }
   }

   void stop(ServerWorld world) {
      this.current = MusicDirector.Track.NONE;
      this.ticks = 0;
   }

   void stop(ServerWorld world, EnderDragonEntity dragon) {
      if (this.current != MusicDirector.Track.NONE) {
         this.stopTrack(world, dragon, this.current);
      }
      this.stop(world);
   }

   private void stopTrack(ServerWorld world, EnderDragonEntity dragon, MusicDirector.Track track) {
      if (world == null || dragon == null || track == MusicDirector.Track.NONE) {
         return;
      }
      StopSoundS2CPacket packet = new StopSoundS2CPacket(soundOf(track).id(), SoundCategory.MUSIC);
      for (ServerPlayerEntity player : world.getPlayers()) {
         if (player.squaredDistanceTo(dragon) <= 192.0 * 192.0) {
            player.networkHandler.sendPacket(packet);
         }
      }
   }

   private static SoundEvent soundOf(MusicDirector.Track track) {
      return switch (track) {
         case FINAL -> ModSounds.MUSIC_FINAL;
         case CATACLYSM -> ModSounds.MUSIC_CATACLYSM;
         default -> ModSounds.MUSIC_BOSS;
      };
   }

   private enum Track {
      NONE,
      COMBAT,
      FINAL,
      CATACLYSM;
   }
}
