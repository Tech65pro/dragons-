package com.dragonbrain.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.dragonbrain.config.ConfigManager;
import com.dragonbrain.registry.ModParticles;
import com.dragonbrain.registry.ModSounds;

import java.util.EnumSet;

/**
 * End Wisp: a passive, luminous flying ambient creature of the Void
 * Gardens. Unique silhouette (glowing core + crown spikes + trailing
 * tail), readable face on the core, and a restrained void-mote trail.
 */
public class EndWispEntity extends MobEntity {
    public EndWispEntity(EntityType<? extends MobEntity> type, World world) {
        super(type, world);
        this.moveControl = new FlightMoveControl(this, 20, true);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 10.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.FLYING_SPEED, 0.6)
                .add(EntityAttributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        BirdNavigation navigation = new BirdNavigation(this, world);
        navigation.setCanOpenDoors(false);
        navigation.setCanSwim(false);
        return navigation;
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new FollowGuideHolderGoal());
        this.goalSelector.add(1, new WanderInAirGoal());
    }

    /**
     * Exploration support: a wisp trails a nearby player holding Wisp
     * Essence or a Resonance Shard (a living lantern for the outer
     * islands). It drops nothing extra for this, so it cannot be farmed.
     */
    private class FollowGuideHolderGoal extends Goal {
        private net.minecraft.entity.player.PlayerEntity holder;

        FollowGuideHolderGoal() {
            setControls(EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            this.holder = findHolder();
            return this.holder != null;
        }

        @Override
        public boolean shouldContinue() {
            return this.holder != null && this.holder.isAlive()
                    && this.holder.squaredDistanceTo(EndWispEntity.this) < 32.0 * 32.0
                    && holdsGuideItem(this.holder);
        }

        @Override
        public void tick() {
            if (this.holder == null) {
                return;
            }
            Vec3d anchor = this.holder.getPos().add(0, 2.4, 0);
            if (squaredDistanceTo(anchor.x, anchor.y, anchor.z) > 3.0) {
                getNavigation().startMovingTo(anchor.x, anchor.y, anchor.z, 1.2);
            }
        }

        private net.minecraft.entity.player.PlayerEntity findHolder() {
            net.minecraft.entity.player.PlayerEntity best = null;
            double bestSq = 24.0 * 24.0;
            for (net.minecraft.entity.player.PlayerEntity player : getEntityWorld().getPlayers()) {
                if (!holdsGuideItem(player)) {
                    continue;
                }
                double sq = player.squaredDistanceTo(EndWispEntity.this);
                if (sq < bestSq) {
                    bestSq = sq;
                    best = player;
                }
            }
            return best;
        }

        private boolean holdsGuideItem(net.minecraft.entity.player.PlayerEntity player) {
            return player.getMainHandStack().isOf(com.dragonbrain.registry.ModItems.WISP_ESSENCE)
                    || player.getOffHandStack().isOf(com.dragonbrain.registry.ModItems.WISP_ESSENCE)
                    || player.getMainHandStack().isOf(com.dragonbrain.registry.ModItems.RESONANCE_SHARD)
                    || player.getOffHandStack().isOf(com.dragonbrain.registry.ModItems.RESONANCE_SHARD);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!getEntityWorld().isClient() && ConfigManager.get().ambientParticles
                && this.age % 7 == 0 && getEntityWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ModParticles.VOID_MOTE,
                    getX(), getY() + 0.35, getZ(), 1, 0.12, 0.12, 0.12, 0.005);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ConfigManager.get().customSounds ? ModSounds.END_WISP_AMBIENT : null;
    }

    @Override
    public int getMinAmbientSoundDelay() {
        return 140;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.END_WISP_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.END_WISP_DEATH;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    private class WanderInAirGoal extends Goal {
        private Vec3d target;

        WanderInAirGoal() {
            setControls(EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            return getNavigation().isIdle() && getRandom().nextInt(8) == 0;
        }

        @Override
        public void start() {
            BlockPos home = getBlockPos();
            target = new Vec3d(
                    home.getX() + getRandom().nextInt(15) - 7,
                    home.getY() + getRandom().nextInt(7) - 2,
                    home.getZ() + getRandom().nextInt(15) - 7);
            getNavigation().startMovingTo(target.x, target.y, target.z, 0.8);
        }

        @Override
        public boolean shouldContinue() {
            return !getNavigation().isIdle();
        }
    }
}
