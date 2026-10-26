package com.dragonbrain.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.world.World;

import com.dragonbrain.registry.ModSounds;

/**
 * Ancient Sentinel: a slow, heavily armoured ruin guardian.
 *
 * <p>Implemented states (and only these are animated/documented):
 * <ul>
 *   <li><b>Dormant/idle</b> — stands motionless until a target is acquired.</li>
 *   <li><b>Awakening</b> — first target acquisition plays the awaken grind
 *       and the model raises its head/arms (GUARD flag off, AWAKENED on).</li>
 *   <li><b>Guard</b> — periodically raises both arms; while guarding it
 *       takes 65% reduced damage and cannot move (clear visual cue).</li>
 *   <li><b>Stagger</b> — a single hit of >= 12 damage while not guarding
 *       interrupts it for ~1.5 s (head drops, clang plays).</li>
 *   <li><b>Attack</b> — heavy melee swing from the vanilla attack goal;
 *       the model swings the right arm using the hand-swing progress.</li>
 * </ul>
 */
public class AncientSentinelEntity extends HostileEntity {
    private static final TrackedData<Boolean> GUARDING =
            DataTracker.registerData(AncientSentinelEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> AWAKENED =
            DataTracker.registerData(AncientSentinelEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> STAGGERED =
            DataTracker.registerData(AncientSentinelEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private int guardCooldown = 100;
    private int guardTicks;
    private int staggerTicks;

    public AncientSentinelEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.MAX_HEALTH, 160.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.19)
                .add(EntityAttributes.ATTACK_DAMAGE, 14.0)
                .add(EntityAttributes.FOLLOW_RANGE, 28.0)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 0.95)
                .add(EntityAttributes.ARMOR, 12.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(GUARDING, false);
        builder.add(AWAKENED, false);
        builder.add(STAGGERED, false);
    }

    public boolean isGuarding() {
        return this.dataTracker.get(GUARDING);
    }

    public boolean isAwakened() {
        return this.dataTracker.get(AWAKENED);
    }

    public boolean isStaggered() {
        return this.dataTracker.get(STAGGERED);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.add(2, new LookAtEntityGoal(this, PlayerEntity.class, 10.0f));
        this.goalSelector.add(3, new LookAroundGoal(this));
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new net.minecraft.entity.ai.goal.ActiveTargetGoal<>(
                this, PlayerEntity.class, true));
    }

    @Override
    public void setTarget(LivingEntity target) {
        super.setTarget(target);
        if (target != null && !isAwakened() && !getEntityWorld().isClient()) {
            this.dataTracker.set(AWAKENED, true);
            playSound(ModSounds.ANCIENT_SENTINEL_AWAKEN, 1.0f, 1.0f);
        }
    }

    @Override
    public void tickMovement() {
        super.tickMovement();
        if (getEntityWorld().isClient()) {
            return;
        }
        if (this.staggerTicks > 0) {
            this.staggerTicks--;
            getNavigation().stop();
            if (this.staggerTicks == 0) {
                this.dataTracker.set(STAGGERED, false);
            }
            return;
        }
        if (isGuarding()) {
            getNavigation().stop();
            if (--this.guardTicks <= 0) {
                this.dataTracker.set(GUARDING, false);
                this.guardCooldown = 120 + getRandom().nextInt(80);
            }
        } else if (getTarget() != null && --this.guardCooldown <= 0) {
            this.dataTracker.set(GUARDING, true);
            this.guardTicks = 50; // 2.5 s guard window
            playSound(ModSounds.ANCIENT_SENTINEL_CLANG, 0.6f, 0.8f);
        }
        if (this.age % 14 == 0 && getVelocity().horizontalLengthSquared() > 0.001) {
            playSound(ModSounds.ANCIENT_SENTINEL_STEP, 0.35f, 0.9f + getRandom().nextFloat() * 0.2f);
        }
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        float effective = amount;
        if (isGuarding()) {
            effective *= 0.35f;
            playSound(ModSounds.ANCIENT_SENTINEL_CLANG, 0.7f, 1.1f);
        } else if (amount >= 12.0f && this.staggerTicks <= 0) {
            this.staggerTicks = 30;
            this.dataTracker.set(STAGGERED, true);
            playSound(ModSounds.ANCIENT_SENTINEL_CLANG, 0.9f, 0.7f);
        }
        return super.damage(world, source, effective);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.ANCIENT_SENTINEL_CLANG;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.ANCIENT_SENTINEL_AWAKEN;
    }
}
