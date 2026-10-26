package com.dragonbrain.core;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Box;

import com.dragonbrain.config.ConfigManager;
import com.dragonbrain.config.DragonBrainConfig;
import com.dragonbrain.registry.ModItems;
import com.dragonbrain.registry.ModParticles;
import com.dragonbrain.registry.ModSounds;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side phase plumbing for the dragon's visual/audio overhaul.
 *
 * <p>Deliberately implemented without touching the dragon's vanilla
 * phase manager: once per second we read each Ender Dragon's health,
 * map it to a {@link BossPhase}, and on a real transition play the
 * phase sounds and emit a bounded particle burst. The client renderer
 * independently derives the same phase from health (see the client
 * dragon-texture mixin), so texture, sound and particles always agree
 * and no vanilla/ai behaviour is altered.
 */
public final class DragonPhaseWatcher {
    private static final Map<UUID, BossPhase> LAST_PHASE = new HashMap<>();
    private static int tick;

    private DragonPhaseWatcher() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(DragonPhaseWatcher::onServerTick);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof EnderDragonEntity dragon
                    && dragon.getEntityWorld() instanceof ServerWorld world) {
                LAST_PHASE.remove(dragon.getUuid());
                // Structure/boss reward: Cataclysm Scales (trophy crafting).
                int count = 2 + world.getRandom().nextInt(3);
                dragon.dropStack(world, new ItemStack(ModItems.CATACLYSM_SCALE, count));
                if (ConfigManager.get().customSounds) {
                    world.playSound(null, dragon.getBlockPos(), ModSounds.VICTORY,
                            SoundCategory.HOSTILE, 1.0f, 1.0f);
                    world.playSound(null, dragon.getBlockPos(), ModSounds.REWARD_FANFARE,
                            SoundCategory.PLAYERS, 0.7f, 1.0f);
                }
            }
        });
    }

    private static void onServerTick(MinecraftServer server) {
        DragonBrainConfig config = ConfigManager.get();
        if (!config.enabled || ++tick % 20 != 0) {
            return;
        }
        for (ServerWorld world : server.getWorlds()) {
            for (EnderDragonEntity dragon : world.getEntitiesByType(
                    EntityType.ENDER_DRAGON,
                    new Box(-512, world.getBottomY(), -512, 512, world.getTopYInclusive() + 1, 512),
                    d -> true)) {
                if (!dragon.isAlive() || dragon.getMaxHealth() <= 0) {
                    continue;
                }
                float ratio = dragon.getHealth() / dragon.getMaxHealth();
                BossPhase phase = BossPhase.forHealth(ratio,
                        config.ragePhaseHealthThreshold, config.finalPhaseHealthThreshold);
                BossPhase previous = LAST_PHASE.put(dragon.getUuid(), phase);
                if (previous != null && previous != phase) {
                    onPhaseChanged(world, dragon, phase, config);
                }
                if (config.ambientParticles && phase != BossPhase.NORMAL) {
                    // Lightweight aura so the phase reads at a distance:
                    // 3 motes/sec, no full-screen effects.
                    world.spawnParticles(ModParticles.RESONANCE_SPARK,
                            dragon.getX(), dragon.getY() + 1.5, dragon.getZ(),
                            3, 2.5, 1.0, 2.5, 0.01);
                }
            }
        }
    }

    private static void onPhaseChanged(ServerWorld world, EnderDragonEntity dragon,
                                       BossPhase phase, DragonBrainConfig config) {
        if (config.customSounds) {
            switch (phase) {
                case RAGE -> {
                    world.playSound(null, dragon.getBlockPos(), ModSounds.RAGE,
                            SoundCategory.HOSTILE, 1.0f, 1.0f);
                    world.playSound(null, dragon.getBlockPos(), ModSounds.PHASE_TRANSITION,
                            SoundCategory.HOSTILE, 0.8f, 1.0f);
                }
                case FINAL -> world.playSound(null, dragon.getBlockPos(),
                        ModSounds.DRAGON_ABYSSAL_TRANSITION, SoundCategory.HOSTILE, 1.0f, 1.0f);
                case NORMAL -> {
                }
            }
        }
        world.spawnParticles(ModParticles.RESONANCE_SPARK,
                dragon.getX(), dragon.getY() + 2.0, dragon.getZ(),
                40, 3.0, 1.5, 3.0, 0.05);
    }

    /** Current known phase for status commands (null if never observed). */
    public static BossPhase lastPhase(UUID dragonUuid) {
        return LAST_PHASE.get(dragonUuid);
    }
}
