package com.dragonbrain.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.world.World;

import com.dragonbrain.registry.ModSounds;

/**
 * Rift Stalker: a quadruped End predator with a readable attack
 * telegraph. When it enters {@link AttackState#WINDUP} it plays the
 * wind-up snarl, lowers its head (model reads this state) and switches
 * to the glowing-gill "windup" texture, holds briefly, then lunges.
 * After a lunge it has a short {@link AttackState#RECOVERY} window in
 * which it is slow — the fair counter-play window.
 */
public class RiftStalkerEntity extends HostileEntity {
    public enum AttackState { IDLE, WINDUP, LUNGE, RECOVERY }

    private static final TrackedData<Integer> ATTACK_STATE =
            DataTracker.registerData(RiftStalkerEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private int stateTicks;

    public RiftStalkerEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.MAX_HEALTH, 36.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.32)
                .add(EntityAttributes.ATTACK_DAMAGE, 7.0)
                .add(EntityAttributes.FOLLOW_RANGE, 32.0)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 0.3);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(ATTACK_STATE, AttackState.IDLE.ordinal());
    }

    public AttackState getAttackState() {
        int i = this.dataTracker.get(ATTACK_STATE);
        AttackState[] values = AttackState.values();
        return values[Math.max(0, Math.min(i, values.length - 1))];
    }

    private void setAttackState(AttackState state) {
        this.dataTracker.set(ATTACK_STATE, state.ordinal());
        this.stateTicks = 0;
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(4, new LookAroundGoal(this));
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, EndWispEntity.class, true));
    }

    @Override
    public void tickMovement() {
        super.tickMovement();
        if (getEntityWorld().isClient()) {
            return;
        }
        this.stateTicks++;
        LivingEntity target = getTarget();
        switch (getAttackState()) {
            case IDLE -> {
                if (target != null && target.isAlive()) {
                    double distSq = squaredDistanceTo(target);
                    getNavigation().startMovingTo(target, 1.15);
                    if (distSq < 49.0) { // within 7 blocks: begin the telegraph
                        setAttackState(AttackState.WINDUP);
                        playSound(ModSounds.RIFT_STALKER_WINDUP, 0.8f, 1.0f);
                        getNavigation().stop();
                    }
                }
            }
            case WINDUP -> {
                if (target != null) {
                    getLookControl().lookAt(target, 30.0f, 30.0f);
                }
                if (this.stateTicks >= 14) { // ~0.7 s readable wind-up
                    setAttackState(AttackState.LUNGE);
                    if (target != null) {
                        double dx = target.getX() - getX();
                        double dz = target.getZ() - getZ();
                        double len = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
                        setVelocity(dx / len * 1.15, 0.32, dz / len * 1.15);
                        velocityDirty = true;
                    }
                }
            }
            case LUNGE -> {
                if (target != null && squaredDistanceTo(target) < 3.2) {
                    playSound(ModSounds.RIFT_STALKER_BITE, 0.8f, 1.0f);
                    if (getEntityWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
                        tryAttack(sw, target);
                    }
                    setAttackState(AttackState.RECOVERY);
                } else if (this.stateTicks >= 16) {
                    setAttackState(AttackState.RECOVERY);
                }
            }
            case RECOVERY -> {
                if (this.stateTicks >= 24) { // ~1.2 s vulnerable recovery
                    setAttackState(AttackState.IDLE);
                }
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.RIFT_STALKER_GROWL;
    }

    @Override
    public int getMinAmbientSoundDelay() {
        return 180;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.RIFT_STALKER_GROWL;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.RIFT_STALKER_WINDUP;
    }
}
