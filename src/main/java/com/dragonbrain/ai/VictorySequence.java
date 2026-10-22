package com.dragonbrain.ai;

import com.dragonbrain.registry.ModSounds;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;

public final class VictorySequence {
   private static final int DURATION_TICKS = 80;

   private VictorySequence() {
   }

   public static void play(ServerWorld world, Vec3d pos) {
      world.playSound(null, pos.x, pos.y, pos.z, ModSounds.VICTORY, SoundCategory.MASTER, 1.0F, 1.0F);
      world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y + 2.0, pos.z, 120, 3.0, 2.0, 3.0, 0.08);
      world.spawnParticles(ParticleTypes.PORTAL, pos.x, pos.y + 3.0, pos.z, 60, 1.5, 1.0, 1.5, 0.15);
      CombatManager.addCinematic(new CombatManager.Cinematic(world, pos, 80));
   }
}
