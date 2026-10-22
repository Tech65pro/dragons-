package com.dragonbrain.ai;

import com.dragonbrain.config.ConfigManager;
import com.dragonbrain.core.AdaptiveDifficulty;
import com.dragonbrain.core.AimSolver;
import com.dragonbrain.core.AttackScheduler;
import com.dragonbrain.core.AttackType;
import com.dragonbrain.core.BossPhase;
import com.dragonbrain.core.CombatState;
import com.dragonbrain.core.DifficultyMode;
import com.dragonbrain.core.DifficultyTuning;
import com.dragonbrain.core.DragonBrainConfig;
import com.dragonbrain.core.TuningFactory;
import com.dragonbrain.core.Vec;
import com.dragonbrain.registry.ModSounds;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.phase.ChargingPlayerPhase;
import net.minecraft.entity.boss.dragon.phase.PhaseType;
import net.minecraft.entity.projectile.DragonFireballEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundCategory;
import org.jetbrains.annotations.Nullable;

public final class DragonCombatController {
   private static final int TARGET_REFRESH_TICKS = 10;
   private static final int MAX_TARGET_RANGE = 128;
   private static final long TUNING_REFRESH_TICKS = 40L;
   private static final int STAGGER_THRESHOLD = 26;
   private final EnderDragonEntity dragon;
   private final Random random = new Random();
   private final AttackScheduler scheduler;
   private final AdaptiveDifficulty adaptive;
   private final MusicDirector music;
   private final EnumSet<AttackType> available = EnumSet.noneOf(AttackType.class);
   private final Set<UUID> pulseHitPlayers = new HashSet();
   private final List<UUID> scratchIds = new ArrayList();
   private DifficultyTuning tuning;
   private long tuningRevision = Long.MIN_VALUE;
   private int tuningAge;
   private BossPhase phase = BossPhase.NORMAL;
   @Nullable
   private LivingEntity target;
   private int targetAge;
   private double targetDistance;
   private boolean targetVisible;
   private int age;
   private int lastAllowedDamageTick = -10000;
   private boolean currentAttackHit;
   private int telegraphCapturedTick = -1;
   private Vec3d diveAim = Vec3d.ZERO;
   private Vec3d pulseCenter = Vec3d.ZERO;
   private int pulseAge = -1;
   private int pulseDuration;
   private float pulseRadius;
   private int staggerMeter;
   private int staggerCooldown;
   private int overchargeTicks;
   private int ambientCooldown = 300;
   private boolean disposed;
   private int transitionCooldown;

   public DragonCombatController(EnderDragonEntity dragon) {
      this.dragon = dragon;
      this.scheduler = new AttackScheduler(this.random);
      this.adaptive = new AdaptiveDifficulty(dragon.getMaxHealth());
      this.music = new MusicDirector();
      this.refreshTuning(true);
   }

   public void tick(ServerWorld world) {
      DragonBrainConfig config = ConfigManager.get();
      if (this.disposed || this.dragon.isRemoved() || this.dragon.isDead()) {
         this.dispose(world);
      } else if (!config.enabled) {
         this.resetForDisabled();
      } else if (this.dragon.getPhaseManager().getCurrent().getType() == PhaseType.DYING) {
         this.music.onDying(world, this.dragon);
         this.pulseAge = -1;
      } else {
         this.age++;
         this.refreshTuning(false);
         this.updateTarget(world);
         this.updatePhase(world, config);
         this.scheduler.setPhase(this.phase);
         AttackScheduler.Result result = this.scheduler
            .tick(this.tuning, this.target != null, this.targetDistance, this.targetVisible, this.collectAvailable(config));
         switch (result.action()) {
            case START_APPROACH:
               this.dragon.getPhaseManager().setPhase(PhaseType.HOLDING_PATTERN);
               break;
            case START_TELEGRAPH:
               this.beginTelegraph(world, result.attack(), config);
               break;
            case START_ATTACK:
               this.executeAttack(world, result.attack(), config);
               break;
            case ENTER_RECOVERY:
               this.endAttack(world);
               break;
            case ENTER_REPOSITION:
               this.endAttack(world);
               this.dragon.getPhaseManager().setPhase(PhaseType.HOLDING_PATTERN);
               break;
            case BECOME_IDLE:
               this.enterIdle(world);
            case PHASE_TRANSITION:
         }

         this.tickPulse(world, config);
         this.tickOvercharge(world, config);
         this.tickStagger(world);
         this.music.tick(world, this.dragon, this.phase, this.target != null, config);
         this.tickAmbient(world, config);
         this.adaptive.tick();
         this.staggerCooldown = Math.max(0, this.staggerCooldown - 1);
      }
   }

   private EnumSet<AttackType> collectAvailable(DragonBrainConfig config) {
      this.available.clear();

      for (AttackType type : AttackType.values()) {
         if (config.mechanicEnabled(type)) {
            this.available.add(type);
         }
      }

      double floorY = this.dragon.getFightOrigin().getY();
      if (Math.abs(this.dragon.getY() - floorY) < 48.0) {
         this.available.add(AttackType.VOID_PULSE);
      }

      return this.available;
   }

   private void refreshTuning(boolean force) {
      DragonBrainConfig config = ConfigManager.get();
      long revision = ConfigManager.revision();
      this.tuningAge++;
      if (force || this.tuning == null || revision != this.tuningRevision || this.tuningAge >= 40L) {
         DifficultyMode mode = config.mode();
         float adaptiveness = mode == DifficultyMode.ADAPTIVE && config.adaptiveDifficulty ? this.adaptive.value() : 0.5F;
         this.tuning = TuningFactory.create(mode, adaptiveness, config, this.phase);
         this.tuningRevision = revision;
         this.tuningAge = 0;
      }
   }

   public DifficultyTuning tuning() {
      return this.tuning;
   }

   private void updateTarget(ServerWorld world) {
      this.targetAge++;
      if (this.target != null
         && (!this.target.isAlive() || this.target.isRemoved() || this.target.isSpectator() || this.target.squaredDistanceTo(this.dragon) > 16384.0)) {
         this.target = null;
      }

      if (this.targetAge < 10 && this.target != null) {
         this.targetDistance = Math.sqrt(this.target.squaredDistanceTo(this.dragon));
         this.targetVisible = this.dragon.canSee(this.target);
      } else {
         this.targetAge = 0;
         if (!ConfigManager.get().debugTargetAnyLivingEntity) {
            List<ServerPlayerEntity> players = world.getPlayers();
            ServerPlayerEntity best = null;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (int i = 0; i < players.size(); i++) {
               ServerPlayerEntity player = (ServerPlayerEntity)players.get(i);
               if (player.isAlive() && !player.isSpectator() && !player.isCreative()) {
                  double distance = Math.sqrt(player.squaredDistanceTo(this.dragon));
                  if (!(distance > 128.0)) {
                     double score = 1.0 / (1.0 + distance / 16.0);
                     if (!this.dragon.canSee(player)) {
                        score *= 0.5;
                     }

                     if (player.getHealth() < 8.0F) {
                        score += 0.15;
                     }

                     if (score > bestScore) {
                        bestScore = score;
                        best = player;
                     }
                  }
               }
            }

            this.target = best;
            if (best != null) {
               this.targetDistance = Math.sqrt(best.squaredDistanceTo(this.dragon));
               this.targetVisible = this.dragon.canSee(best);
            } else {
               this.targetDistance = Double.MAX_VALUE;
               this.targetVisible = false;
            }
         } else {
            LivingEntity candidate = null;
            double bestDistance = 128.0;

            for (LivingEntity entity : world.getEntitiesByClass(
               LivingEntity.class, this.dragon.getBoundingBox().expand(128.0), e -> !(e instanceof EnderDragonEntity) && e.isAlive() && !e.isRemoved()
            )) {
               double distance = Math.sqrt(entity.squaredDistanceTo(this.dragon));
               if (distance < bestDistance) {
                  bestDistance = distance;
                  candidate = entity;
               }
            }

            this.target = candidate;
            this.targetDistance = candidate == null ? Double.MAX_VALUE : bestDistance;
            this.targetVisible = candidate != null && this.dragon.canSee(candidate);
         }
      }
   }

   private void updatePhase(ServerWorld world, DragonBrainConfig config) {
      float ratio = this.dragon.getHealth() / this.dragon.getMaxHealth();
      float rageThreshold = config.mechanicRagePhase ? config.rageHealthThreshold : -1.0F;
      float finalThreshold = config.mechanicFinalPhase ? config.finalPhaseHealthThreshold : -1.0F;
      BossPhase desired = BossPhase.forHealth(ratio, Math.max(0.02F, rageThreshold), Math.max(0.01F, finalThreshold));
      if (desired != this.phase) {
         BossPhase previous = this.phase;
         this.phase = desired;
         this.scheduler.setPhase(desired);
         this.refreshTuning(true);
         if (this.transitionCooldown <= 0) {
            this.transitionCooldown = 200;
            this.scheduler.beginPhaseTransition();
            this.adaptive.reset();
            Vec3d pos = this.dragon.getEntityPos();
            if (desired == BossPhase.RAGE && previous == BossPhase.NORMAL) {
               this.playSound(world, ModSounds.RAGE, 1.0F, 0.85F, pos);
               this.ring(world, ParticleTypes.SOUL_FIRE_FLAME, pos, 3.0, 40);
            } else if (desired == BossPhase.FINAL) {
               this.playSound(world, ModSounds.PHASE_TRANSITION, 1.0F, 0.7F, pos);
               this.ring(world, ParticleTypes.END_ROD, pos, 4.0, 60);
            }
         }
      }
   }

   private void beginTelegraph(ServerWorld world, @Nullable AttackType attack, DragonBrainConfig config) {
      if (attack != null) {
         this.currentAttackHit = false;
         this.telegraphCapturedTick = this.age;
         Vec3d pos = this.dragon.getEntityPos();
         switch (attack) {
            case DIVE:
               this.diveAim = this.aimPoint(this.dragon.getEntityPos(), 12.0);
               this.playSound(world, ModSounds.DIVE_WARNING, 1.0F, 1.0F, pos);
               this.ring(world, ParticleTypes.SOUL, pos.add(0.0, -1.0, 0.0), 5.0, 26);
               break;
            case FIREBALL:
               this.playSound(world, ModSounds.FIREBALL_CHARGE, 0.9F, 1.0F, pos);
               this.burst(world, ParticleTypes.PORTAL, pos.add(0.0, 1.0, 0.0), 12, 1.5);
               break;
            case WING_GUST:
               this.playSound(world, ModSounds.WING_GUST, 0.85F, 0.75F, pos);
               this.ring(world, ParticleTypes.CLOUD, pos.add(0.0, 0.5, 0.0), 6.0, 24);
               break;
            case VOID_PULSE:
               this.pulseCenter = this.groundPoint(this.target);
               this.pulseDuration = 40;
               this.playSound(world, ModSounds.VOID_PULSE, 0.9F, 1.35F, pos);
               this.ring(world, ParticleTypes.PORTAL, this.pulseCenter.add(0.0, 0.2, 0.0), 1.5, 20);
         }
      }
   }

   private void executeAttack(ServerWorld world, @Nullable AttackType attack, DragonBrainConfig config) {
      if (attack != null) {
         Vec3d pos = this.dragon.getEntityPos();
         switch (attack) {
            case DIVE:
               this.startVanillaAttackPhase();
               this.dragon.getPhaseManager().setPhase(PhaseType.CHARGING_PLAYER);
               ChargingPlayerPhase charge = (ChargingPlayerPhase)this.dragon.getPhaseManager().create(PhaseType.CHARGING_PLAYER);
               charge.setPathTarget(this.diveAim);
               this.playSound(world, ModSounds.DRAGON_ROAR, 0.8F, 1.2F, pos);
               break;
            case FIREBALL:
               this.shootFireballs(world, config);
               break;
            case WING_GUST:
               this.wingGust(world, config);
               break;
            case VOID_PULSE:
               this.pulseAge = 0;
               this.pulseRadius = 1.0F;
               this.pulseHitPlayers.clear();
               this.playSound(world, ModSounds.VOID_PULSE, 1.0F, 0.9F, pos);
         }
      }
   }

   private void endAttack(ServerWorld world) {
      this.pulseAge = this.pulseAge >= 0 && this.pulseAge < this.pulseDuration ? this.pulseAge : -1;
      AttackType attack = this.scheduler.getCurrentAttack();
      if (attack != null && attack.isDamaging() && !this.currentAttackHit && this.scheduler.getState() == CombatState.RECOVERY) {
         this.scheduler.onAttackWhiffed();
      }

      this.dragon.getPhaseManager().setPhase(PhaseType.HOLDING_PATTERN);
   }

   private void enterIdle(ServerWorld world) {
      if (this.dragon.getPhaseManager().getCurrent().getType() == PhaseType.CHARGING_PLAYER) {
         this.dragon.getPhaseManager().setPhase(PhaseType.HOLDING_PATTERN);
      }
   }

   private void shootFireballs(ServerWorld world, DragonBrainConfig config) {
      if (this.target != null) {
         int count = 1;
         if (config.mechanicCrystalOvercharge && this.overchargeTicks > 0 && config.crystalOverchargeExtraFireball) {
            count++;
         }

         if (this.phase == BossPhase.FINAL) {
            count++;
         }

         Vec3d head = new Vec3d(
            this.dragon.head.getX(), this.dragon.head.getBodyY(0.5) + 0.5, this.dragon.head.getZ()
         );
         Vec3d targetPos = new Vec3d(this.target.getX(), this.target.getBodyY(0.5), this.target.getZ());
         Vec3d velocity = this.target.getVelocity();
         this.playSound(world, ModSounds.FIREBALL_LAUNCH, 0.9F, 1.0F, head);

         for (int i = 0; i < count; i++) {
            double travel = Math.max(6.0, head.distanceTo(targetPos) / Math.max(0.4, this.tuning.projectileSpeed()));
            Vec predicted = AimSolver.predict(toVec(head), toVec(targetPos), toVec(velocity), travel, this.tuning, this.random);
            Vec direction = new Vec(predicted.x() - head.x, predicted.y() - head.y, predicted.z() - head.z).normalize();
            direction = AimSolver.applyAimError(direction, this.tuning.aimErrorDegrees(), this.random);
            if (count > 1) {
               direction = AimSolver.applySpread(direction, this.tuning.projectileSpreadDegrees() * 1.5F, this.random);
            }

            Vec3d vec = new Vec3d(direction.x(), direction.y(), direction.z());
            DragonFireballEntity fireball = new DragonFireballEntity(world, this.dragon, vec);
            fireball.refreshPositionAndAngles(head.x, head.y, head.z, 0.0F, 0.0F);
            fireball.setVelocity(vec.multiply(Math.max(0.6, this.tuning.projectileSpeed())));
            world.spawnEntity(fireball);
         }

         this.burst(world, ParticleTypes.PORTAL, head, 10, 1.0);
      }
   }

   private void wingGust(ServerWorld world, DragonBrainConfig config) {
      Vec3d origin = this.dragon.getEntityPos();
      float yaw = this.dragon.getYaw();
      Vec3d forward = new Vec3d(-Math.sin(Math.toRadians(yaw)), 0.0, Math.cos(Math.toRadians(yaw))).normalize();
      double radius = 16.0;
      List<ServerPlayerEntity> players = world.getPlayers();

      for (int i = 0; i < players.size(); i++) {
         ServerPlayerEntity player = (ServerPlayerEntity)players.get(i);
         if (!player.isSpectator() && !player.isCreative()) {
            Vec3d delta = new Vec3d(player.getX() - origin.x, 0.0, player.getZ() - origin.z);
            double distance = delta.length();
            if (!(distance > radius) && !(distance < 0.01)) {
               Vec3d dir = delta.normalize();
               double dot = dir.dotProduct(forward);
               if (!(dot < -0.15)) {
                  double strength = (1.0 - distance / radius) * (0.6 + Math.max(0.0, dot) * 0.9);
                  player.takeKnockback(strength * 1.6, dir.x, dir.z);
                  player.addVelocity(0.0, 0.35 * strength, 0.0);
                  this.scheduler.onDamageDealt(0.25F);
                  this.adaptive.onAttackLanded();
                  this.currentAttackHit = true;
                  if (config.wingGustDamage > 0.01F) {
                     float damage = this.tuning.clampDamage(config.wingGustDamage);
                     if (damage > 0.01F) {
                        player.damage(world, this.dragon.getDamageSources().mobAttack(this.dragon), damage);
                     }
                  }
               }
            }
         }
      }

      this.playSound(world, ModSounds.WING_GUST, 1.0F, 0.9F, origin);
      this.ring(world, ParticleTypes.CLOUD, origin, radius * 0.6, 80);
   }

   private void tickPulse(ServerWorld world, DragonBrainConfig config) {
      if (this.pulseAge >= 0) {
         this.pulseAge++;
         float progress = Math.min(1.0F, (float)this.pulseAge / this.pulseDuration);
         this.pulseRadius = 1.0F + progress * 18.0F;
         int steps = 28;

         for (int i = 0; i < steps; i++) {
            double angle = (Math.PI * 2) / steps * i + this.pulseAge * 0.05;
            double x = this.pulseCenter.x + Math.cos(angle) * this.pulseRadius;
            double z = this.pulseCenter.z + Math.sin(angle) * this.pulseRadius;
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, this.pulseCenter.y + 0.15, z, 1, 0.0, 0.02, 0.0, 0.0);
         }

         List<ServerPlayerEntity> players = world.getPlayers();

         for (int i = 0; i < players.size(); i++) {
            ServerPlayerEntity player = (ServerPlayerEntity)players.get(i);
            if (!player.isSpectator() && !player.isCreative() && !this.pulseHitPlayers.contains(player.getUuid())) {
               double dx = player.getX() - this.pulseCenter.x;
               double dz = player.getZ() - this.pulseCenter.z;
               double distance = Math.sqrt(dx * dx + dz * dz);
               if (!(Math.abs(distance - this.pulseRadius) > 1.6)
                  && !(player.getY() > this.pulseCenter.y + 1.6)
                  && !(player.getY() < this.pulseCenter.y - 2.5)) {
                  this.pulseHitPlayers.add(player.getUuid());
                  this.currentAttackHit = true;
                  this.scheduler.onDamageDealt(1.0F);
                  this.adaptive.onAttackLanded();
                  float damage = this.tuning.clampDamage(config.voidPulseDamage);
                  if (damage > 0.01F) {
                     player.damage(world, this.dragon.getDamageSources().magic(), damage);
                  }

                  player.takeKnockback(0.6, dx, dz);
               }
            }
         }

         if (this.pulseAge >= this.pulseDuration) {
            this.pulseAge = -1;
            this.pulseHitPlayers.clear();
         }
      }
   }

   private void tickOvercharge(ServerWorld world, DragonBrainConfig config) {
      if (!config.mechanicCrystalOvercharge) {
         this.overchargeTicks = 0;
      } else {
         int crystals = 0;
         if (this.dragon.getFight() != null) {
            crystals = this.dragon.getFight().getAliveEndCrystals();
         }

         if (crystals > 0) {
            this.overchargeTicks = Math.max(this.overchargeTicks, 40);
         }

         if (this.overchargeTicks > 0) {
            this.overchargeTicks--;
            if (this.age % 20 == 0 && crystals > 0) {
               Vec3d pos = this.dragon.getEntityPos();
               world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y + 2.0, pos.z, 4, 1.6, 1.0, 1.6, 0.02);
            }

            if (this.age % 200 == 0 && crystals > 0) {
               this.playSound(world, ModSounds.CRYSTAL_OVERCHARGE, 0.35F, 1.4F, this.dragon.getEntityPos());
            }
         }
      }
   }

   private void tickStagger(ServerWorld world) {
      if (this.staggerMeter > 0 && this.age % 10 == 0) {
         this.staggerMeter = Math.max(0, this.staggerMeter - 1);
      }
   }

   public void onDragonDamaged(ServerWorld world, float amount) {
      DragonBrainConfig config = ConfigManager.get();
      if (config.enabled && config.mechanicStaggerWindow && this.staggerCooldown <= 0) {
         this.adaptive.onBossDamaged(amount);
         this.staggerMeter = this.staggerMeter + Math.round(amount * (amount > 8.0F ? 2.0F : 1.0F));
         if (this.staggerMeter >= 26) {
            this.staggerMeter = 0;
            this.staggerCooldown = 600;
            if (this.scheduler.interrupt(this.tuning, "stagger")) {
               this.pulseAge = -1;
               this.dragon.getPhaseManager().setPhase(PhaseType.HOVER);
               Vec3d pos = this.dragon.getEntityPos();
               this.playSound(world, ModSounds.STAGGER, 1.0F, 1.0F, pos);
               this.ring(world, ParticleTypes.CRIT, pos.add(0.0, 1.0, 0.0), 4.0, 40);
            }
         }
      }
   }

   private void tickAmbient(ServerWorld world, DragonBrainConfig config) {
      if (config.ambientEndAudio && !(config.ambientVolume <= 0.01F)) {
         this.ambientCooldown--;
         if (this.ambientCooldown <= 0) {
            this.ambientCooldown = 700 + this.random.nextInt(900);
            List<ServerPlayerEntity> players = world.getPlayers();

            for (int i = 0; i < players.size(); i++) {
               ServerPlayerEntity player = (ServerPlayerEntity)players.get(i);
               if (!(Math.abs(player.getX() - this.dragon.getX()) > 160.0)) {
                  world.playSound(
                     null,
                     player.getX() + (this.random.nextDouble() - 0.5) * 40.0,
                     40.0,
                     player.getZ() + (this.random.nextDouble() - 0.5) * 40.0,
                     ModSounds.AMBIENT_END,
                     SoundCategory.AMBIENT,
                     0.5F * config.ambientVolume,
                     0.85F
                  );
                  break;
               }
            }
         }
      }
   }

   private Vec3d aimPoint(Vec3d from, double travelTicks) {
      if (this.target == null) {
         return from.add(0.0, -8.0, 0.0);
      }

      Vec targetPos = toVec(new Vec3d(this.target.getX(), this.target.getY(), this.target.getZ()));
      Vec predicted = AimSolver.predict(
         toVec(from), targetPos, toVec(this.target.getVelocity()), travelTicks / Math.max(0.5, this.tuning.projectileSpeed()), this.tuning, this.random
      );
      Vec direction = predicted.subtract(toVec(from)).normalize();
      direction = AimSolver.applyAimError(direction, this.tuning.aimErrorDegrees() * 0.7F, this.random);
      double distance = Math.min(32.0, from.distanceTo(new Vec3d(this.target.getX(), this.target.getY(), this.target.getZ())));
      Vec point = toVec(from).add(direction.multiply(distance));
      double floor = this.dragon.getFightOrigin().getY() + 3.0;
      return new Vec3d(point.x(), Math.max(floor, point.y()), point.z());
   }

   private Vec3d groundPoint(@Nullable LivingEntity player) {
      Vec3d base = player == null ? this.dragon.getEntityPos() : new Vec3d(player.getX(), player.getY(), player.getZ());
      double floor = this.dragon.getFightOrigin().getY() + 1.0;
      return new Vec3d(base.x, Math.max(floor, base.y - 0.5), base.z);
   }

   private void ring(ServerWorld world, ParticleEffect particle, Vec3d center, double radius, int points) {
      for (int i = 0; i < points; i++) {
         double angle = (Math.PI * 2) / points * i;
         double x = center.x + Math.cos(angle) * radius;
         double z = center.z + Math.sin(angle) * radius;
         world.spawnParticles(particle, x, center.y, z, 1, 0.0, 0.05, 0.0, 0.0);
      }
   }

   private void burst(ServerWorld world, ParticleEffect particle, Vec3d center, int count, double spread) {
      world.spawnParticles(particle, center.x, center.y, center.z, count, spread, spread, spread, 0.02);
   }

   private void playSound(ServerWorld world, SoundEvent sound, float volume, float pitch, Vec3d pos) {
      DragonBrainConfig config = ConfigManager.get();
      if (config.customSounds && !(config.soundVolume <= 0.01F)) {
         world.playSound(null, pos.x, pos.y, pos.z, sound, SoundCategory.HOSTILE, volume * config.soundVolume, pitch);
      }
   }

   private void startVanillaAttackPhase() {
      CombatManager.allowVanillaAttackPhase(this.dragon, 20);
   }

   private static Vec toVec(Vec3d vec) {
      return new Vec(vec.x, vec.y, vec.z);
   }

   public boolean allowDamageToPlayer(ServerWorld world, float amount) {
      DragonBrainConfig config = ConfigManager.get();
      if (!config.enabled) {
         return true;
      }

      if (config.enforceMinimumDamageInterval && this.age - this.lastAllowedDamageTick < this.tuning.minDamageIntervalTicks()) {
         return false;
      }

      this.lastAllowedDamageTick = this.age;
      this.currentAttackHit = true;
      this.scheduler.onDamageDealt(0.8F);
      this.adaptive.onPlayerDamaged(amount);
      this.adaptive.onAttackLanded();
      return true;
   }

   public double damageScale() {
      return this.tuning == null ? 1.0 : this.tuning.damageMultiplier();
   }

   public boolean isDisposed() {
      return this.disposed;
   }

   public void dispose(ServerWorld world) {
      if (!this.disposed) {
         this.disposed = true;
         this.pulseAge = -1;
         this.pulseHitPlayers.clear();
         this.target = null;
         this.music.stop(world);
         if (world != null && ConfigManager.get().mechanicVictorySequence && this.dragon.isDead()) {
            VictorySequence.play(world, this.dragon.getEntityPos());
         }
      }
   }

   private void resetForDisabled() {
      if (this.scheduler.getState() != CombatState.IDLE) {
         this.scheduler.reset();
         this.pulseAge = -1;
         this.currentAttackHit = false;
      }
   }

   public String debugLine() {
      return String.format(
         "state=%s attack=%s phase=%s hp=%.1f target=%s dist=%.1f pressure=%.2f cons=%d sinceDmg=%d cd=%d/%d stats[l=%d,h=%d,f=%d,x=%d] mode=%s telegraph=%.2fx",
         new Object[]{
            this.scheduler.getState(),
            this.scheduler.getCurrentAttack(),
            this.phase,
            this.dragon.getHealth(),
            this.target == null ? "-" : this.target.getName().getString(),
            this.target == null ? -1.0 : this.targetDistance,
            this.scheduler.getPressure(),
            this.scheduler.getConsecutiveHits(),
            this.scheduler.getTicksSinceDamagingAttack(),
            this.scheduler.getTicksInState(),
            this.scheduler.getPlannedStateTicks(),
            this.scheduler.getStats().attacksLaunched,
            this.scheduler.getStats().attacksLanded,
            this.scheduler.getStats().fakeouts,
            this.scheduler.getStats().attacksCancelled,
            this.tuning.mode(),
            this.tuning.telegraphMultiplier()
         }
      );
   }

   public AdaptiveDifficulty adaptive() {
      return this.adaptive;
   }

   public AttackScheduler scheduler() {
      return this.scheduler;
   }

   public BossPhase phase() {
      return this.phase;
   }

   public EnderDragonEntity dragon() {
      return this.dragon;
   }

   public void forceAttack(AttackType type) {
      this.scheduler.queueAttack(type);
   }
}
