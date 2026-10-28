package com.dragonbrain.core;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.DragonFireballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.dragonbrain.DragonBrain;
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
 * Server-authoritative Ender Dragon combat system ("Abyssal Cataclysm").
 *
 * <p>Design: the vanilla dragon, its PhaseManager, crystal healing, fight
 * sequencing, portal activation and respawning are left completely alone.
 * On top of that stable base this controller runs a fair, telegraphed
 * ability rotation per dragon:
 *
 * <ul>
 *   <li><b>FIREBALL</b> — charge sound + mouth sparks, then 1–3 real
 *       {@link DragonFireballEntity} projectiles with difficulty-scaled
 *       target leading.</li>
 *   <li><b>DIVE</b> — dive-warning sound and a particle ring marks the
 *       impact point (snapshotted when the telegraph starts, so moving
 *       always dodges), then a falling-off shockwave. Knockback is
 *       capped and mostly upward-reduced so it does not throw players
 *       into the void.</li>
 *   <li><b>WING_GUST</b> — short-range radial gust when players crowd the
 *       dragon; blocking with a shield cuts the knockback by 75%.</li>
 *   <li><b>VOID_PULSE</b> — expanding ground ring; jumping or leaving
 *       the radius avoids all damage. Two rings in the FINAL phase.</li>
 *   <li><b>CRYSTAL_OVERCHARGE</b> — a nearby End Crystal is overcharged
 *       over a 2 s channel (destroy it to cancel); success grants the
 *       dragon temporary Resistance and halves the next cooldown.</li>
 *   <li><b>FAKEOUT</b> (Expert/Adaptive) — a fireball telegraph that
 *       converts into a delayed dive at the player's new position; the
 *       second telegraph ring still gives a fair reaction window.</li>
 *   <li><b>STAGGER</b> — sustained player damage accumulates; crossing
 *       the difficulty threshold breaks the dragon's poise: current
 *       attack cancelled, bonus true damage, long recovery.</li>
 * </ul>
 *
 * <p>Every ability has: telegraph (sound + particles), reaction window,
 * per-ability and global cooldowns, a recovery window with no attacks,
 * target re-validation on execute, and difficulty scaling
 * (ROOKIE/NORMAL/EXPERT fixed, ADAPTIVE drifts with player performance).
 * Phase transitions (health thresholds) drive the client texture sets,
 * sounds and particles consistently on both sides.
 */
public final class DragonCombatController {
    public enum Attack { NONE, FIREBALL, DIVE, WING_GUST, VOID_PULSE, CRYSTAL_OVERCHARGE, FAKEOUT }

    private static final Map<UUID, DragonState> STATES = new HashMap<>();
    private static int refreshTick;

    private DragonCombatController() {
    }

    // ------------------------------------------------------------------ state
    private static final class DragonState {
        EnderDragonEntity dragon;
        BossPhase phase = BossPhase.NORMAL;
        Attack current = Attack.NONE;
        int attackTicks;
        int cooldownTicks = 80;
        int recoveryTicks;
        UUID targetUuid;
        Vec3d snapshotPos;
        EndCrystalEntity channelCrystal;
        boolean secondPulsePending;
        Attack lastAttack = Attack.NONE;
        float staggerAccumulator;
        int staggerDecayTicks;
        int staggeredUntilTick;
        boolean dealingStaggerDamage;
        double adaptiveFactor = 1.0;
        int totalTicks;
    }

    private record Tuning(int telegraphTicks, int cooldownMin, int cooldownMax,
                          float damageScale, float leadFactor, float staggerThreshold,
                          int recoveryTicks) {
    }

    private static Tuning tuningFor(DragonBrainConfig config, DragonState state) {
        Tuning base = switch (config.difficultyMode()) {
            case ROOKIE -> new Tuning(30, 110, 170, 0.65f, 0.15f, 70.0f, 80);
            case NORMAL -> new Tuning(24, 85, 140, 1.0f, 0.45f, 100.0f, 60);
            case EXPERT -> new Tuning(18, 60, 105, 1.35f, 0.8f, 130.0f, 45);
            case ADAPTIVE -> {
                double a = state.adaptiveFactor; // 0.7 (gentle) .. 1.3 (hard)
                yield new Tuning((int) (27 - 6 * a), (int) (105 - 22 * a), (int) (160 - 30 * a),
                        (float) a, (float) (0.25 + 0.4 * a), (float) (85 + 30 * a), (int) (75 - 15 * a));
            }
        };
        return new Tuning(
                Math.max(8, Math.round(base.telegraphTicks() * config.telegraphDurationMultiplier)),
                Math.max(20, Math.round(base.cooldownMin() * config.attackCooldownMultiplier)),
                Math.max(30, Math.round(base.cooldownMax() * config.attackCooldownMultiplier)),
                base.damageScale() * config.attackDamageMultiplier,
                base.leadFactor(), base.staggerThreshold(), base.recoveryTicks());
    }

    // ------------------------------------------------------------------ wiring
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(DragonCombatController::onServerTick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(DragonCombatController::onAllowDamage);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof EnderDragonEntity dragon
                    && dragon.getEntityWorld() instanceof ServerWorld world) {
                STATES.remove(dragon.getUuid());
                int count = 2 + world.getRandom().nextInt(3);
                dragon.dropStack(world, new ItemStack(ModItems.CATACLYSM_SCALE, count));
                if (ConfigManager.get().customSounds) {
                    world.playSound(null, dragon.getBlockPos(), ModSounds.VICTORY,
                            SoundCategory.HOSTILE, 1.0f, 1.0f);
                    world.playSound(null, dragon.getBlockPos(), ModSounds.REWARD_FANFARE,
                            SoundCategory.PLAYERS, 0.7f, 1.0f);
                }
            } else if (entity instanceof ServerPlayerEntity
                    && entity.getEntityWorld().getRegistryKey() == World.END) {
                // ADAPTIVE mercy: a player death in the arena eases the fight.
                for (DragonState state : STATES.values()) {
                    state.adaptiveFactor = Math.max(0.7, state.adaptiveFactor - 0.12);
                }
            }
        });
    }

    private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
        DragonBrainConfig config = ConfigManager.get();
        if (!config.enabled || !config.dragonAttacksEnabled) {
            return true;
        }
        if (entity instanceof EnderDragonEntity dragon) {
            DragonState state = STATES.get(dragon.getUuid());
            if (state == null || state.dealingStaggerDamage) {
                return true;
            }
            if (!(source.getAttacker() instanceof PlayerEntity)) {
                return true; // only player damage builds stagger
            }
            if (isStaggered(state)) {
                return true;
            }
            state.staggerAccumulator += Math.min(amount, 30.0f);
            state.staggerDecayTicks = 200;
            Tuning tuning = tuningFor(config, state);
            if (state.staggerAccumulator >= tuning.staggerThreshold()
                    && dragon.getEntityWorld() instanceof ServerWorld world) {
                triggerStagger(world, dragon, state, tuning);
            }
            return true;
        }
        if (entity instanceof ServerPlayerEntity && source.getAttacker() instanceof EnderDragonEntity) {
            DragonState state = STATES.get(source.getAttacker().getUuid());
            if (state != null) {
                // ADAPTIVE: repeated hits taken push the fight gentler, not harder.
                state.adaptiveFactor = Math.max(0.7, state.adaptiveFactor - 0.015);
            }
        }
        return true;
    }

    private static boolean isStaggered(DragonState state) {
        return state.totalTicks < state.staggeredUntilTick;
    }

    private static void triggerStagger(ServerWorld world, EnderDragonEntity dragon,
                                       DragonState state, Tuning tuning) {
        state.staggerAccumulator = 0.0f;
        state.current = Attack.NONE;
        state.channelCrystal = null;
        state.staggeredUntilTick = state.totalTicks + 120; // 6 s poise break
        state.cooldownTicks = Math.max(state.cooldownTicks, tuning.recoveryTicks() + 60);
        if (ConfigManager.get().customSounds) {
            world.playSound(null, dragon.getBlockPos(), ModSounds.STAGGER,
                    SoundCategory.HOSTILE, 0.9f, 1.0f);
        }
        world.spawnParticles(ModParticles.RESONANCE_SPARK,
                dragon.getX(), dragon.getY() + 2.0, dragon.getZ(),
                30, 2.5, 1.2, 2.5, 0.06);
        // Bonus poise-break damage (4% max HP). Reentrancy-guarded so it
        // cannot recursively feed the stagger accumulator.
        state.dealingStaggerDamage = true;
        try {
            dragon.damage(world, world.getDamageSources().generic(),
                    dragon.getMaxHealth() * 0.04f);
        } finally {
            state.dealingStaggerDamage = false;
        }
    }

    // ------------------------------------------------------------------ tick
    private static void onServerTick(MinecraftServer server) {
        DragonBrainConfig config = ConfigManager.get();
        if (!config.enabled) {
            return;
        }
        ServerWorld end = server.getWorld(World.END);
        if (end == null) {
            return;
        }
        if (++refreshTick % 20 == 0) {
            for (EnderDragonEntity dragon : end.getEntitiesByType(
                    EntityType.ENDER_DRAGON,
                    new Box(-512, end.getBottomY(), -512, 512, end.getTopYInclusive() + 1, 512),
                    d -> true)) {
                STATES.computeIfAbsent(dragon.getUuid(), uuid -> {
                    DragonState s = new DragonState();
                    s.dragon = dragon;
                    return s;
                }).dragon = dragon;
            }
        }
        STATES.values().removeIf(state -> state.dragon == null
                || state.dragon.isRemoved() || !state.dragon.isAlive());
        for (DragonState state : STATES.values()) {
            tickDragon(end, state, config);
        }
    }

    private static void tickDragon(ServerWorld world, DragonState state, DragonBrainConfig config) {
        EnderDragonEntity dragon = state.dragon;
        state.totalTicks++;

        // -- phase tracking (drives client textures + sounds + aura) --
        float ratio = dragon.getMaxHealth() > 0 ? dragon.getHealth() / dragon.getMaxHealth() : 1.0f;
        BossPhase phase = BossPhase.forHealth(ratio,
                config.ragePhaseHealthThreshold, config.finalPhaseHealthThreshold);
        if (phase != state.phase) {
            state.phase = phase;
            onPhaseChanged(world, dragon, phase, config);
        }
        if (config.ambientParticles && phase != BossPhase.NORMAL && state.totalTicks % 20 == 0) {
            world.spawnParticles(ModParticles.RESONANCE_SPARK,
                    dragon.getX(), dragon.getY() + 1.5, dragon.getZ(), 3, 2.5, 1.0, 2.5, 0.01);
        }
        if (state.staggerDecayTicks > 0 && --state.staggerDecayTicks == 0) {
            state.staggerAccumulator = 0.0f;
        }
        if (!config.dragonAttacksEnabled || dragon.deathTime > 0) {
            state.current = Attack.NONE;
            return;
        }
        Tuning tuning = tuningFor(config, state);

        if (state.recoveryTicks > 0) {
            state.recoveryTicks--;
            return;
        }
        if (state.current != Attack.NONE) {
            state.attackTicks++;
            tickAttack(world, dragon, state, tuning, config);
            return;
        }
        if (state.cooldownTicks > 0) {
            state.cooldownTicks--;
            return;
        }
        if (isStaggered(state)) {
            return;
        }
        ServerPlayerEntity target = findTarget(world, dragon);
        if (target == null) {
            state.cooldownTicks = 20; // re-check shortly; no valid target, no attack
            return;
        }
        startAttack(world, dragon, state, chooseAttack(world, dragon, state, target, config),
                target, config, tuning);
    }

    private static ServerPlayerEntity findTarget(ServerWorld world, EnderDragonEntity dragon) {
        ServerPlayerEntity best = null;
        double bestSq = 100.0 * 100.0;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator() || player.isCreative() || !player.isAlive()) {
                continue;
            }
            double sq = player.squaredDistanceTo(dragon);
            if (sq < bestSq) {
                bestSq = sq;
                best = player;
            }
        }
        return best;
    }

    private static ServerPlayerEntity validateTarget(ServerWorld world, DragonState state) {
        if (state.targetUuid == null) {
            return null;
        }
        var entity = world.getEntity(state.targetUuid);
        if (entity instanceof ServerPlayerEntity player
                && player.isAlive() && !player.isSpectator() && !player.isCreative()
                && player.getEntityWorld() == world) {
            return player;
        }
        return null;
    }

    private static Attack chooseAttack(ServerWorld world, EnderDragonEntity dragon, DragonState state,
                                       ServerPlayerEntity target, DragonBrainConfig config) {
        var random = dragon.getRandom();
        double distSq = target.squaredDistanceTo(dragon);
        // Crystal overcharge: only past NORMAL phase, only with a live crystal nearby.
        if (config.crystalOverchargeEnabled && state.phase != BossPhase.NORMAL
                && state.lastAttack != Attack.CRYSTAL_OVERCHARGE
                && random.nextInt(4) == 0
                && findCrystal(world, dragon) != null) {
            return Attack.CRYSTAL_OVERCHARGE;
        }
        if (distSq < 14 * 14) {
            return random.nextBoolean() ? Attack.WING_GUST : Attack.VOID_PULSE;
        }
        if (distSq < 30 * 30 && random.nextInt(3) == 0) {
            return Attack.VOID_PULSE;
        }
        if ((config.difficultyMode() == DifficultyMode.EXPERT
                || config.difficultyMode() == DifficultyMode.ADAPTIVE)
                && state.phase == BossPhase.FINAL && random.nextInt(4) == 0) {
            return Attack.FAKEOUT;
        }
        return random.nextInt(3) == 0 ? Attack.DIVE : Attack.FIREBALL;
    }

    private static EndCrystalEntity findCrystal(ServerWorld world, EnderDragonEntity dragon) {
        EndCrystalEntity best = null;
        double bestSq = 70.0 * 70.0;
        for (EndCrystalEntity crystal : world.getEntitiesByType(EntityType.END_CRYSTAL,
                dragon.getBoundingBox().expand(70.0), c -> c.isAlive())) {
            double sq = crystal.squaredDistanceTo(dragon);
            if (sq < bestSq) {
                bestSq = sq;
                best = crystal;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ attacks
    private static void startAttack(ServerWorld world, EnderDragonEntity dragon, DragonState state,
                                    Attack attack, ServerPlayerEntity target,
                                    DragonBrainConfig config, Tuning tuning) {
        state.current = attack;
        state.attackTicks = 0;
        state.targetUuid = target.getUuid();
        state.snapshotPos = target.getEntityPos();
        state.secondPulsePending = state.phase == BossPhase.FINAL && attack == Attack.VOID_PULSE;
        state.channelCrystal = null;
        switch (attack) {
            case FIREBALL, FAKEOUT -> {
                play(config, world, dragon.getBlockPos(), ModSounds.FIREBALL_CHARGE, 0.8f);
            }
            case DIVE -> {
                play(config, world, dragon.getBlockPos(), ModSounds.DIVE_WARNING, 0.9f);
                ringParticles(world, state.snapshotPos, 6.0, 24);
            }
            case WING_GUST -> {
                world.spawnParticles(ModParticles.VOID_MOTE, dragon.getX(), dragon.getY() + 1.5,
                        dragon.getZ(), 14, 3.0, 0.6, 3.0, 0.03);
            }
            case VOID_PULSE -> {
                play(config, world, dragon.getBlockPos(), ModSounds.VOID_PULSE, 0.8f);
                ringParticles(world, dragon.getEntityPos(), 6.0, 20);
            }
            case CRYSTAL_OVERCHARGE -> {
                state.channelCrystal = findCrystal(world, dragon);
                if (state.channelCrystal == null) {
                    state.current = Attack.NONE;
                    state.cooldownTicks = 30;
                    return;
                }
                play(config, world, state.channelCrystal.getBlockPos(),
                        ModSounds.CRYSTAL_OVERCHARGE, 0.8f);
            }
            case NONE -> {
            }
        }
    }

    private static void tickAttack(ServerWorld world, EnderDragonEntity dragon, DragonState state,
                                   Tuning tuning, DragonBrainConfig config) {
        int telegraph = switch (state.current) {
            case WING_GUST -> Math.max(6, tuning.telegraphTicks() / 2);
            case CRYSTAL_OVERCHARGE -> 40;
            default -> tuning.telegraphTicks();
        };
        // ongoing telegraph visuals
        if (state.current == Attack.FIREBALL || state.current == Attack.FAKEOUT) {
            Vec3d head = dragon.getEyePos().add(dragon.getRotationVec(1.0f).multiply(3.0));
            world.spawnParticles(ModParticles.RESONANCE_SPARK, head.x, head.y, head.z,
                    2, 0.4, 0.4, 0.4, 0.01);
        } else if (state.current == Attack.DIVE && state.snapshotPos != null
                && state.attackTicks % 6 == 0) {
            ringParticles(world, state.snapshotPos, 6.0, 10);
        } else if (state.current == Attack.VOID_PULSE && state.attackTicks % 5 == 0) {
            ringParticles(world, dragon.getEntityPos(), 4.0 + state.attackTicks * 0.35, 14);
        } else if (state.current == Attack.CRYSTAL_OVERCHARGE) {
            if (state.channelCrystal == null || !state.channelCrystal.isAlive()) {
                finishAttack(state, tuning, true); // channel broken: long recovery, no payoff
                return;
            }
            beamParticles(world, dragon.getEyePos(), state.channelCrystal.getEyePos().add(0, 1.2, 0));
        }
        if (state.attackTicks < telegraph) {
            return;
        }

        switch (state.current) {
            case FIREBALL -> executeFireball(world, dragon, state, tuning, config);
            case FAKEOUT -> {
                // convert to a dive at the target's *current* position with a fresh mini-telegraph
                ServerPlayerEntity target = validateTarget(world, state);
                if (target != null) {
                    state.current = Attack.DIVE;
                    state.attackTicks = Math.max(0, telegraph - 10);
                    state.snapshotPos = target.getEntityPos();
                    play(config, world, dragon.getBlockPos(), ModSounds.DIVE_WARNING, 0.9f);
                    ringParticles(world, state.snapshotPos, 6.0, 24);
                    return;
                }
            }
            case DIVE -> executeDive(world, dragon, state, tuning, config);
            case WING_GUST -> executeGust(world, dragon, state, tuning, config);
            case VOID_PULSE -> {
                executePulse(world, dragon, state, tuning, config);
                if (state.secondPulsePending) {
                    state.secondPulsePending = false;
                    state.attackTicks = Math.max(0, telegraph - 16);
                    play(config, world, dragon.getBlockPos(), ModSounds.VOID_PULSE, 0.8f);
                    return;
                }
            }
            case CRYSTAL_OVERCHARGE -> executeOvercharge(world, dragon, state, tuning, config);
            case NONE -> {
                return;
            }
        }
        finishAttack(state, tuning, false);
    }

    private static void finishAttack(DragonState state, Tuning tuning, boolean interrupted) {
        state.lastAttack = state.current;
        state.current = Attack.NONE;
        state.channelCrystal = null;
        state.recoveryTicks = tuning.recoveryTicks();
        int base = interrupted ? tuning.cooldownMax() + 40
                : tuning.cooldownMin()
                + (tuning.cooldownMax() > tuning.cooldownMin()
                        ? (int) (Math.random() * (tuning.cooldownMax() - tuning.cooldownMin())) : 0);
        state.cooldownTicks = base;
    }

    private static void executeFireball(ServerWorld world, EnderDragonEntity dragon,
                                        DragonState state, Tuning tuning, DragonBrainConfig config) {
        ServerPlayerEntity target = validateTarget(world, state);
        if (target == null) {
            return;
        }
        play(config, world, dragon.getBlockPos(), ModSounds.FIREBALL_LAUNCH, 0.9f);
        Vec3d head = dragon.getEyePos().add(dragon.getRotationVec(1.0f).multiply(4.0));
        int count = state.phase == BossPhase.FINAL ? 3 : (state.phase == BossPhase.RAGE ? 2 : 1);
        for (int i = 0; i < count; i++) {
            Vec3d aim = target.getEyePos()
                    .add(target.getVelocity().multiply(8.0 * tuning.leadFactor()))
                    .add((dragon.getRandom().nextDouble() - 0.5) * i * 1.6,
                            (dragon.getRandom().nextDouble() - 0.5) * i * 0.8,
                            (dragon.getRandom().nextDouble() - 0.5) * i * 1.6);
            Vec3d velocity = aim.subtract(head);
            if (velocity.lengthSquared() < 1.0e-4) {
                velocity = dragon.getRotationVec(1.0f);
            }
            DragonFireballEntity fireball = new DragonFireballEntity(world, dragon, velocity.normalize());
            fireball.setPosition(head.x, head.y, head.z);
            world.spawnEntity(fireball);
        }
    }

    private static void executeDive(ServerWorld world, EnderDragonEntity dragon,
                                    DragonState state, Tuning tuning, DragonBrainConfig config) {
        if (state.snapshotPos == null) {
            return;
        }
        play(config, world, BlockPos.ofFloored(state.snapshotPos), ModSounds.WING_GUST, 0.9f);
        world.spawnParticles(ModParticles.RESONANCE_SPARK,
                state.snapshotPos.x, state.snapshotPos.y + 0.6, state.snapshotPos.z,
                36, 3.0, 0.8, 3.0, 0.08);
        DamageSource source = world.getDamageSources().mobAttack(dragon);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator() || player.isCreative() || !player.isAlive()) {
                continue;
            }
            double dist = player.getEntityPos().distanceTo(state.snapshotPos);
            if (dist > 6.5) {
                continue;
            }
            double falloff = 1.0 - (dist / 7.5);
            player.damage(world, source, (float) (11.0 * tuning.damageScale() * falloff));
            Vec3d away = player.getEntityPos().subtract(state.snapshotPos);
            double len = Math.max(0.3, Math.sqrt(away.x * away.x + away.z * away.z));
            double kb = player.isBlocking() ? 0.25 : 1.0;
            player.addVelocity(away.x / len * 0.7 * kb, 0.35 * kb, away.z / len * 0.7 * kb);
            player.velocityDirty = true;
        }
    }

    private static void executeGust(ServerWorld world, EnderDragonEntity dragon,
                                    DragonState state, Tuning tuning, DragonBrainConfig config) {
        play(config, world, dragon.getBlockPos(), ModSounds.WING_GUST, 1.0f);
        ringParticles(world, dragon.getEntityPos(), 8.0, 28);
        DamageSource source = world.getDamageSources().mobAttack(dragon);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator() || player.isCreative() || !player.isAlive()) {
                continue;
            }
            double dist = player.getEntityPos().distanceTo(dragon.getEntityPos());
            if (dist > 16.0) {
                continue;
            }
            player.damage(world, source, 3.0f * tuning.damageScale());
            Vec3d away = player.getEntityPos().subtract(dragon.getEntityPos());
            double len = Math.max(0.3, Math.sqrt(away.x * away.x + away.z * away.z));
            double kb = player.isBlocking() ? 0.3 : 1.15;
            player.addVelocity(away.x / len * kb, 0.3, away.z / len * kb);
            player.velocityDirty = true;
        }
    }

    private static void executePulse(ServerWorld world, EnderDragonEntity dragon,
                                     DragonState state, Tuning tuning, DragonBrainConfig config) {
        ringParticles(world, dragon.getEntityPos(), 16.0, 40);
        DamageSource source = world.getDamageSources().dragonBreath();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator() || player.isCreative() || !player.isAlive()) {
                continue;
            }
            double dist = player.getEntityPos().distanceTo(dragon.getEntityPos());
            if (dist > 18.0 || !player.isOnGround()) {
                continue; // jumping (or leaving the radius) fully avoids the pulse
            }
            player.damage(world, source, 8.0f * tuning.damageScale());
        }
    }

    private static void executeOvercharge(ServerWorld world, EnderDragonEntity dragon,
                                          DragonState state, Tuning tuning, DragonBrainConfig config) {
        if (state.channelCrystal == null || !state.channelCrystal.isAlive()) {
            return;
        }
        play(config, world, state.channelCrystal.getBlockPos(), ModSounds.CRYSTAL_OVERCHARGE, 1.0f);
        dragon.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 240, 1, false, true));
        state.cooldownTicks = 30; // empowered: the *next* attack comes sooner (set again on finish)
        world.spawnParticles(ModParticles.VOID_MOTE,
                state.channelCrystal.getX(), state.channelCrystal.getY() + 1.5,
                state.channelCrystal.getZ(), 24, 0.8, 1.2, 0.8, 0.03);
    }

    // ------------------------------------------------------------------ phase fx
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
                dragon.getX(), dragon.getY() + 2.0, dragon.getZ(), 40, 3.0, 1.5, 3.0, 0.05);
    }

    // ------------------------------------------------------------------ helpers
    private static void play(DragonBrainConfig config, ServerWorld world, BlockPos pos,
                             SoundEvent sound, float volume) {
        if (config.customSounds) {
            world.playSound(null, pos, sound, SoundCategory.HOSTILE, volume, 1.0f);
        }
    }

    private static void ringParticles(ServerWorld world, Vec3d center, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i) / points;
            world.spawnParticles(ModParticles.RESONANCE_SPARK,
                    center.x + Math.cos(angle) * radius, center.y + 0.4,
                    center.z + Math.sin(angle) * radius, 1, 0.0, 0.05, 0.0, 0.0);
        }
    }

    private static void beamParticles(ServerWorld world, Vec3d from, Vec3d to) {
        int steps = 10;
        for (int i = 0; i <= steps; i++) {
            Vec3d p = from.lerp(to, i / (double) steps);
            world.spawnParticles(ModParticles.VOID_MOTE, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    // ------------------------------------------------------------------ command API
    public static BossPhase phaseOf(EnderDragonEntity dragon) {
        DragonState state = STATES.get(dragon.getUuid());
        if (state != null) {
            return state.phase;
        }
        DragonBrainConfig config = ConfigManager.get();
        float ratio = dragon.getMaxHealth() > 0 ? dragon.getHealth() / dragon.getMaxHealth() : 1.0f;
        return BossPhase.forHealth(ratio, config.ragePhaseHealthThreshold, config.finalPhaseHealthThreshold);
    }

    public static String describe(EnderDragonEntity dragon) {
        DragonState state = STATES.get(dragon.getUuid());
        String hp = String.format("%.0f/%.0f", dragon.getHealth(), dragon.getMaxHealth());
        if (state == null) {
            return "HP " + hp + " | phase " + phaseOf(dragon) + " | controller: not yet tracking";
        }
        return "HP " + hp + " | phase " + state.phase
                + " | attack " + state.current + " (tick " + state.attackTicks + ")"
                + " | cooldown " + state.cooldownTicks + " | recovery " + state.recoveryTicks
                + " | stagger " + String.format("%.0f", state.staggerAccumulator)
                + " | adaptive " + String.format("%.2f", state.adaptiveFactor)
                + " | attacks enabled " + ConfigManager.get().dragonAttacksEnabled;
    }

    /**
     * Operator test hook: forces the next attack (optionally a specific one)
     * through the normal telegraph pipeline. Returns a human-readable result.
     * Never spawns a dragon.
     */
    public static String forceAttack(ServerWorld world, EnderDragonEntity dragon, Attack requested) {
        DragonState state = STATES.computeIfAbsent(dragon.getUuid(), uuid -> {
            DragonState s = new DragonState();
            s.dragon = dragon;
            return s;
        });
        state.dragon = dragon;
        DragonBrainConfig config = ConfigManager.get();
        if (!config.dragonAttacksEnabled) {
            return "dragon attacks are disabled in config (dragonAttacksEnabled=false)";
        }
        ServerPlayerEntity target = findTarget(world, dragon);
        if (target == null) {
            return "no valid survival-mode target within 100 blocks — attack not started";
        }
        Attack attack = requested != Attack.NONE ? requested
                : chooseAttack(world, dragon, state, target, config);
        Tuning tuning = tuningFor(config, state);
        state.current = Attack.NONE;
        state.recoveryTicks = 0;
        startAttack(world, dragon, state, attack, target, config, tuning);
        return "telegraphed " + attack + " against " + target.getName().getString()
                + " (phase " + state.phase + ", difficulty " + config.difficultyMode() + ")";
    }
}
