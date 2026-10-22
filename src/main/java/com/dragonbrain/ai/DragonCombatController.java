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
   // --- DragonBrain 3.0 ability runtimes (all bounded, server-authoritative) ---
   private Vec3d riftCenter = Vec3d.ZERO;
   private double riftYaw;
   private int riftLaneCount = 3;
   private int riftResidualTicks;
   private Vec3d wellCenter = Vec3d.ZERO;
   private int wellAge = -1;
   private int wellDuration;
   private final List<DragonCombatController.Meteor> meteors = new ArrayList();
   private int meteorsLeftToSpawn;
   private int meteorSpawnCooldown;
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
         this.clearAbilityRuntimes();
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
         this.tickTelegraphVisuals(world, config);
         this.tickRiftResidual(world, config);
         this.tickMeteors(world, config);
         this.tickWell(world, config);
         this.tickCataclysmAura(world, config);
         this.tickOvercharge(world, config);
         this.tickStagger(world);
         this.music.tick(world, this.dragon, this.phase, this.target != null, config);
         this.tickAmbient(world, config);
         this.adaptive.tick();
         this.staggerCooldown = Math.max(0, this.staggerCooldown - 1);
         // DragonBrain 3.0 bugfix: in 2.0 this cooldown was set but never
         // decremented, so only the first phase transition of a fight ever
         // played its sound/particles or paused the scheduler. Later
         // transitions (e.g. Final after Rage) were silently skipped.
         this.transitionCooldown = Math.max(0, this.transitionCooldown - 1);
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
      // DragonBrain 3.0 fix: 2.0 re-added VOID_PULSE near the ground even when
      // mechanicVoidPulse was disabled in the config. The toggle is now honored;
      // behavior is unchanged while the mechanic is enabled.
      if (config.mechanicVoidPulse && Math.abs(this.dragon.getY() - floorY) < 48.0) {
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
      float cataclysmThreshold = config.mechanicCataclysmPhase ? config.cataclysmHealthThreshold : -1.0F;
      BossPhase desired = BossPhase.forHealth(
         ratio, Math.max(0.02F, rageThreshold), Math.max(0.01F, finalThreshold), Math.max(0.005F, cataclysmThreshold)
      );
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
            if (desired == BossPhase.CATACLYSM) {
               // One dramatic, bounded sequence. The scheduler's
               // PHASE_TRANSITION state pauses attacks while it plays, and the
               // desired != phase edge trigger above (plus this cooldown)
               // guarantees it cannot retrigger while health stays low.
               this.playSound(world, ModSounds.CATACLYSM_TRANSITION, 1.0F, 1.0F, pos);
               this.playSound(world, ModSounds.DREAD_ROAR, 0.7F, 0.7F, pos);
               this.clearAbilityRuntimes();
               this.ring(world, ParticleTypes.END_ROD, pos, 3.0, this.scaledParticles(40, config));
               this.ring(world, ParticleTypes.PORTAL, pos.add(0.0, 0.5, 0.0), 5.0, this.scaledParticles(60, config));
               this.ring(world, ParticleTypes.SOUL_FIRE_FLAME, pos.add(0.0, 1.0, 0.0), 7.0, this.scaledParticles(80, config));
               this.burst(world, ParticleTypes.DRAGON_BREATH, pos.add(0.0, 1.0, 0.0), this.scaledParticles(40, config), 2.5);
            } else if (desired == BossPhase.RAGE && previous == BossPhase.NORMAL) {
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
               break;
            case VOID_RIFT:
               // The lane layout is decided now and drawn faithfully for the
               // whole telegraph window: damage zones never move mid-charge.
               this.riftCenter = this.groundPoint(this.target);
               this.riftYaw = this.random.nextDouble() * Math.PI;
               this.riftLaneCount = switch (this.tuning.mode()) {
                  case ROOKIE -> 2;
                  case EXPERT -> 4;
                  default -> 3;
               };
               if (this.phase == BossPhase.CATACLYSM) {
                  this.riftLaneCount = Math.min(5, this.riftLaneCount + 1);
               }

               this.playSound(world, ModSounds.VOID_RIFT_CHARGE, 1.0F, 1.0F, this.riftCenter);
               break;
            case METEOR_STORM:
               // A sky-facing scream is the warning; every meteor then gets
               // its own ground glyph + whistle while it falls.
               this.playSound(world, ModSounds.DREAD_ROAR, 0.8F, 0.6F, pos);
               this.ring(world, ParticleTypes.SOUL_FIRE_FLAME, pos.add(0.0, 1.0, 0.0), 4.0, 30);
               break;
            case DREAD_ROAR:
               this.playSound(world, ModSounds.VOID_RIFT_CHARGE, 0.8F, 0.5F, pos);
               this.burst(world, ParticleTypes.SOUL, pos.add(0.0, 1.0, 0.0), 16, 1.5);
               break;
            case GRAVITY_WELL:
               this.wellCenter = this.groundPoint(this.target);
               this.playSound(world, ModSounds.GRAVITY_WELL, 0.8F, 1.4F, this.wellCenter);
               this.ring(world, ParticleTypes.REVERSE_PORTAL, this.wellCenter.add(0.0, 0.2, 0.0), config.gravityWellRadius, 40);
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
               break;
            case VOID_RIFT:
               this.eruptRift(world, config);
               break;
            case METEOR_STORM:
               this.startMeteorStorm(config);
               break;
            case DREAD_ROAR:
               this.dreadRoar(world, config);
               break;
            case GRAVITY_WELL:
               this.wellAge = 0;
               // The release pulse fires a few ticks before the ATTACK state
               // ends so damage always lands inside the active window.
               this.wellDuration = Math.max(12, this.tuning.activeTicks(AttackType.GRAVITY_WELL) - 4);
               this.playSound(world, ModSounds.GRAVITY_WELL, 1.0F, 1.0F, this.wellCenter);
         }
      }
   }

   private void endAttack(ServerWorld world) {
      this.pulseAge = this.pulseAge >= 0 && this.pulseAge < this.pulseDuration ? this.pulseAge : -1;
      AttackType attack = this.scheduler.getCurrentAttack();
      if (attack == AttackType.METEOR_STORM) {
         // The storm must not leak fresh meteors past its active window;
         // already-falling meteors still land with their own glyphs.
         this.meteorsLeftToSpawn = 0;
      }

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

         if (this.phase == BossPhase.CATACLYSM) {
            count += 2;
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
                  // DragonBrain 3.0 bugfix: LivingEntity.takeKnockback pushes
                  // along the NEGATIVE of (x, z), which must point from the
                  // victim toward the knockback source. 2.0 passed the outward
                  // direction here, so Wing Gust pulled players INTO the
                  // dragon instead of blasting them away.
                  player.takeKnockback(strength * 1.6, -dir.x, -dir.z);
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

                  // 3.0 bugfix: same inverted knockback direction as Wing
                  // Gust - the pulse ring now pushes players outward.
                  player.takeKnockback(0.6, -dx, -dz);
               }
            }
         }

         if (this.pulseAge >= this.pulseDuration) {
            this.pulseAge = -1;
            this.pulseHitPlayers.clear();
         }
      }
   }

   // ---------------------------------------------------------------------------
   // DragonBrain 3.0 abilities. Shared fairness rules:
   //  * layouts/positions are fixed at telegraph start and never track the
   //    player afterwards, so moving out of the marked area always works;
   //  * damage is applied once per player per impact, only while the
   //    scheduler is in its ATTACK window (checked via allowsDamage());
   //  * every loop is bounded (meteors, particle steps and lifetimes are
   //    capped) and no blocks are ever modified.
   // ---------------------------------------------------------------------------

   private static final DustParticleEffect RIFT_GLYPH = new DustParticleEffect(0xFF9D4DFF, 1.5F);
   private static final DustParticleEffect ABYSS_DUST = new DustParticleEffect(0xFFC77DFF, 1.2F);
   private static final double RIFT_HALF_LENGTH = 12.0;
   private static final double RIFT_HALF_WIDTH = 1.2;
   private static final int METEOR_MAX_ACTIVE = 4;
   private static final int METEOR_SPAWN_INTERVAL_TICKS = 7;
   private static final int METEOR_FALL_TICKS = 17;

   private void clearAbilityRuntimes() {
      this.riftResidualTicks = 0;
      this.wellAge = -1;
      this.meteors.clear();
      this.meteorsLeftToSpawn = 0;
      this.meteorSpawnCooldown = 0;
   }

   private int scaledParticles(int base, DragonBrainConfig config) {
      return Math.max(1, Math.round(base * config.particleDensityMultiplier));
   }

   /** Per-tick telegraph rendering for the 3.0 ground-targeted abilities. */
   private void tickTelegraphVisuals(ServerWorld world, DragonBrainConfig config) {
      if (this.scheduler.getState() != CombatState.TELEGRAPH || this.age % 2 != 0) {
         return;
      }
      AttackType attack = this.scheduler.getCurrentAttack();
      if (attack == null) {
         return;
      }
      int planned = Math.max(1, this.scheduler.getPlannedStateTicks());
      float progress = Math.min(1.0F, (float)this.scheduler.getTicksInState() / planned);
      switch (attack) {
         case VOID_RIFT -> this.drawRiftLanes(world, config, progress);
         case GRAVITY_WELL -> {
            this.ring(world, ParticleTypes.PORTAL, this.wellCenter.add(0.0, 0.2, 0.0), config.gravityWellRadius, this.scaledParticles(40, config));
            this.drawInwardSpiral(world, this.wellCenter, config.gravityWellRadius, config);
         }
         case DREAD_ROAR -> {
            // The warning ring expands toward the true blast radius so the
            // safe distance is readable before the roar lands.
            Vec3d pos = this.dragon.getEntityPos();
            double r = config.dreadRoarRadius * (0.35 + 0.65 * progress);
            this.ring(world, ABYSS_DUST, pos.add(0.0, 0.3, 0.0), r, this.scaledParticles(44, config));
         }
         case METEOR_STORM -> {
            Vec3d pos = this.dragon.getEntityPos();
            this.burst(world, ParticleTypes.REVERSE_PORTAL, pos.add(0.0, 3.0, 0.0), this.scaledParticles(3, config), 3.0);
         }
         default -> {
         }
      }
   }

   private double[] riftLaneOffsets() {
      // Lane centre offsets along the fissure's perpendicular axis. The gaps
      // between lanes are always >= 2 blocks wide: the safe lanes.
      return switch (this.riftLaneCount) {
         case 2 -> new double[]{-2.6, 2.6};
         case 4 -> new double[]{-6.6, -2.2, 2.2, 6.6};
         default -> new double[]{-4.4, 0.0, 4.4};
      };
   }

   private boolean inRiftLane(double x, double z) {
      double dx = x - this.riftCenter.x;
      double dz = z - this.riftCenter.z;
      double cos = Math.cos(this.riftYaw);
      double sin = Math.sin(this.riftYaw);
      double u = dx * cos + dz * sin;
      if (Math.abs(u) > RIFT_HALF_LENGTH) {
         return false;
      }
      double v = -dx * sin + dz * cos;
      for (double offset : this.riftLaneOffsets()) {
         if (Math.abs(v - offset) <= RIFT_HALF_WIDTH) {
            return true;
         }
      }
      return false;
   }

   private void drawRiftLanes(ServerWorld world, DragonBrainConfig config, float intensity) {
      double cos = Math.cos(this.riftYaw);
      double sin = Math.sin(this.riftYaw);
      for (double offset : this.riftLaneOffsets()) {
         for (double u = -RIFT_HALF_LENGTH; u <= RIFT_HALF_LENGTH; u += 1.6) {
            for (double edge : new double[]{offset - RIFT_HALF_WIDTH, offset + RIFT_HALF_WIDTH}) {
               double x = this.riftCenter.x + u * cos - edge * sin;
               double z = this.riftCenter.z + u * sin + edge * cos;
               world.spawnParticles(RIFT_GLYPH, x, this.riftCenter.y + 0.15, z, 1, 0.0, 0.02, 0.0, 0.0);
            }
         }
      }
      if (intensity > 0.6F) {
         // Final moments: the fissure starts venting so the eruption reads
         // as a release, not a surprise.
         this.burst(world, ParticleTypes.PORTAL, this.riftCenter.add(0.0, 0.3, 0.0), this.scaledParticles(6, config), 5.0);
      }
   }

   private void eruptRift(ServerWorld world, DragonBrainConfig config) {
      this.playSound(world, ModSounds.VOID_RIFT_ERUPT, 1.0F, 1.0F, this.riftCenter);
      this.riftResidualTicks = 6;
      double cos = Math.cos(this.riftYaw);
      double sin = Math.sin(this.riftYaw);
      for (double offset : this.riftLaneOffsets()) {
         for (double u = -RIFT_HALF_LENGTH; u <= RIFT_HALF_LENGTH; u += 1.3) {
            double x = this.riftCenter.x + u * cos - offset * sin;
            double z = this.riftCenter.z + u * sin + offset * cos;
            world.spawnParticles(ParticleTypes.DRAGON_BREATH, x, this.riftCenter.y + 0.4, z, 1, 0.15, 0.7, 0.15, 0.05);
            world.spawnParticles(ABYSS_DUST, x, this.riftCenter.y + 0.2, z, 1, 0.1, 0.5, 0.1, 0.03);
         }
      }

      float damage = this.tuning.clampDamage(config.voidRiftDamage);
      for (ServerPlayerEntity player : world.getPlayers()) {
         if (player.isSpectator() || player.isCreative()) {
            continue;
         }
         if (!this.inRiftLane(player.getX(), player.getZ())
            || player.getY() > this.riftCenter.y + 2.6
            || player.getY() < this.riftCenter.y - 3.0) {
            continue;
         }
         this.currentAttackHit = true;
         if (damage > 0.01F && this.scheduler.allowsDamage()) {
            if (player.damage(world, this.dragon.getDamageSources().magic(), damage)) {
               this.scheduler.onDamageDealt(AttackType.VOID_RIFT.pressureWeight());
               this.adaptive.onAttackLanded();
            }
         }
         // Launch up and out of the fissure so a surviving player is not
         // trapped standing in the visual residue.
         player.addVelocity(0.0, 0.42, 0.0);
         player.velocityDirty = true;
      }
   }

   private void tickRiftResidual(ServerWorld world, DragonBrainConfig config) {
      if (this.riftResidualTicks <= 0) {
         return;
      }
      this.riftResidualTicks--;
      // Purely visual fading vents; no damage after the eruption tick.
      this.burst(world, ParticleTypes.PORTAL, this.riftCenter.add(0.0, 0.3, 0.0), this.scaledParticles(4, config), 6.0);
   }

   private void startMeteorStorm(DragonBrainConfig config) {
      float scale = switch (this.tuning.mode()) {
         case ROOKIE -> 0.7F;
         case EXPERT -> 1.3F;
         default -> 1.0F;
      };
      int count = Math.round(config.meteorCount * scale);
      if (this.phase == BossPhase.CATACLYSM) {
         count += 2; // storm surge
      }
      this.meteorsLeftToSpawn = Math.max(1, Math.min(16, count));
      this.meteorSpawnCooldown = 0;
   }

   private void tickMeteors(ServerWorld world, DragonBrainConfig config) {
      // Spawning stops early enough that every meteor impacts inside the
      // scheduler's ATTACK window (fairness: no damage outside the window).
      boolean maySpawn = this.meteorsLeftToSpawn > 0
         && this.meteors.size() < METEOR_MAX_ACTIVE
         && this.scheduler.getState() == CombatState.ATTACK
         && this.scheduler.getCurrentAttack() == AttackType.METEOR_STORM
         && this.scheduler.getTicksInState() + METEOR_FALL_TICKS + 6 <= this.scheduler.getPlannedStateTicks();
      if (maySpawn) {
         if (--this.meteorSpawnCooldown <= 0) {
            this.meteorSpawnCooldown = METEOR_SPAWN_INTERVAL_TICKS;
            this.meteorsLeftToSpawn--;
            this.spawnMeteor(world);
         }
      }

      for (int i = this.meteors.size() - 1; i >= 0; i--) {
         DragonCombatController.Meteor meteor = (DragonCombatController.Meteor)this.meteors.get(i);
         meteor.ticksToImpact--;
         // Ground marker ring drawn every tick for the whole fall.
         this.ring(world, RIFT_GLYPH, meteor.impact.add(0.0, 0.15, 0.0), config.meteorRadius, this.scaledParticles(14, config));
         double height = meteor.ticksToImpact * 1.7;
         world.spawnParticles(
            ParticleTypes.SOUL_FIRE_FLAME, meteor.impact.x, meteor.impact.y + height, meteor.impact.z, 2, 0.2, 0.4, 0.2, 0.01
         );
         world.spawnParticles(
            ParticleTypes.REVERSE_PORTAL, meteor.impact.x, meteor.impact.y + height + 1.0, meteor.impact.z, 1, 0.15, 0.3, 0.15, 0.01
         );
         if (meteor.ticksToImpact <= 0) {
            this.meteors.remove(i);
            this.impactMeteor(world, config, meteor);
         }
      }
   }

   private void spawnMeteor(ServerWorld world) {
      Vec3d impact = this.meteorAimPoint(world);
      if (impact == null) {
         return;
      }
      DragonCombatController.Meteor meteor = new DragonCombatController.Meteor();
      meteor.impact = impact;
      meteor.ticksToImpact = METEOR_FALL_TICKS + this.random.nextInt(5);
      this.meteors.add(meteor);
      // Whistle from the sky at the impact column; capped by the active cap.
      this.playSound(world, ModSounds.METEOR_WHISTLE, 0.8F, 0.9F + this.random.nextFloat() * 0.25F, impact.add(0.0, 24.0, 0.0));
   }

   @Nullable
   private Vec3d meteorAimPoint(ServerWorld world) {
      this.scratchIds.clear();
      ServerPlayerEntity chosen = null;
      for (ServerPlayerEntity player : world.getPlayers()) {
         if (player.isAlive() && !player.isSpectator() && !player.isCreative() && player.squaredDistanceTo(this.dragon) <= 128.0 * 128.0) {
            if (chosen == null || this.random.nextBoolean()) {
               chosen = player;
            }
         }
      }
      if (chosen == null && this.target != null) {
         return this.groundPoint(this.target);
      }
      if (chosen == null) {
         return null;
      }
      // Imperfect aim, same philosophy as AimSolver: partial prediction plus
      // a bounded random offset, so a moving player walks out of the circle.
      Vec3d velocity = chosen.getVelocity();
      double lead = METEOR_FALL_TICKS * this.tuning.predictionStrength() * 0.6;
      double x = chosen.getX() + velocity.x * lead + AimSolver.gaussian(this.random) * 3.0;
      double z = chosen.getZ() + velocity.z * lead + AimSolver.gaussian(this.random) * 3.0;
      double floor = this.dragon.getFightOrigin().getY() + 1.0;
      return new Vec3d(x, Math.max(floor, chosen.getY()), z);
   }

   private void impactMeteor(ServerWorld world, DragonBrainConfig config, DragonCombatController.Meteor meteor) {
      Vec3d impact = meteor.impact;
      this.playSound(world, ModSounds.METEOR_IMPACT, 1.0F, 0.95F + this.random.nextFloat() * 0.1F, impact);
      this.ring(world, ParticleTypes.SOUL_FIRE_FLAME, impact.add(0.0, 0.2, 0.0), config.meteorRadius, this.scaledParticles(30, config));
      this.ring(world, ABYSS_DUST, impact.add(0.0, 0.35, 0.0), config.meteorRadius * 0.6, this.scaledParticles(20, config));
      this.burst(world, ParticleTypes.DRAGON_BREATH, impact.add(0.0, 0.5, 0.0), this.scaledParticles(14, config), 1.2);

      float baseDamage = this.tuning.clampDamage(config.meteorDamage);
      for (ServerPlayerEntity player : world.getPlayers()) {
         if (player.isSpectator() || player.isCreative()) {
            continue;
         }
         double dx = player.getX() - impact.x;
         double dz = player.getZ() - impact.z;
         double distance = Math.sqrt(dx * dx + dz * dz);
         if (distance > config.meteorRadius || player.getY() > impact.y + 3.0 || player.getY() < impact.y - 3.0) {
            continue;
         }
         // One instantaneous AoE check per meteor: a player can never be hit
         // twice by the same impact.
         this.currentAttackHit = true;
         double falloff = 1.0 - 0.55 * (distance / config.meteorRadius);
         float damage = (float)(baseDamage * falloff);
         if (damage > 0.01F && this.scheduler.allowsDamage()) {
            // mobAttack routes through the Fabric ALLOW_DAMAGE interval guard
            // in CombatManager, which registers the hit for pressure/adaptive
            // bookkeeping only when the damage is actually allowed through.
            player.damage(world, this.dragon.getDamageSources().mobAttack(this.dragon), damage);
         }
         player.takeKnockback(0.75 * falloff, -dx, -dz);
         player.addVelocity(0.0, 0.25 * falloff, 0.0);
      }
   }

   private void dreadRoar(ServerWorld world, DragonBrainConfig config) {
      Vec3d origin = this.dragon.getEntityPos();
      this.playSound(world, ModSounds.DREAD_ROAR, 1.0F, 1.0F, origin);
      Vec3d head = new Vec3d(this.dragon.head.getX(), this.dragon.head.getBodyY(0.5) + 0.5, this.dragon.head.getZ());
      world.spawnParticles(ParticleTypes.SONIC_BOOM, head.x, head.y, head.z, 1, 0.0, 0.0, 0.0, 0.0);
      this.ring(world, ABYSS_DUST, origin.add(0.0, 0.4, 0.0), config.dreadRoarRadius * 0.55, this.scaledParticles(40, config));
      this.ring(world, ParticleTypes.SOUL, origin.add(0.0, 0.6, 0.0), config.dreadRoarRadius, this.scaledParticles(56, config));

      float yaw = this.dragon.getYaw();
      Vec3d forward = new Vec3d(-Math.sin(Math.toRadians(yaw)), 0.0, Math.cos(Math.toRadians(yaw))).normalize();
      float baseDamage = this.tuning.clampDamage(config.dreadRoarDamage);
      for (ServerPlayerEntity player : world.getPlayers()) {
         if (player.isSpectator() || player.isCreative()) {
            continue;
         }
         double dx = player.getX() - origin.x;
         double dz = player.getZ() - origin.z;
         double distance = Math.sqrt(dx * dx + dz * dz);
         if (distance > config.dreadRoarRadius || distance < 0.01 || Math.abs(player.getY() - origin.y) > 9.0) {
            continue;
         }
         double dot = (dx / distance) * forward.x + (dz / distance) * forward.z;
         // Frontal cone takes the full shockwave; directly behind the dragon
         // only the inner half-ring connects. Distance and pillar cover are
         // the other two counterplays: no line of sight halves the effect.
         boolean inCone = dot > 0.1;
         if (!inCone && distance > config.dreadRoarRadius * 0.45) {
            continue;
         }
         double falloff = 1.0 - distance / config.dreadRoarRadius;
         double scale = (inCone ? 1.0 : 0.5) * (0.35 + 0.65 * falloff);
         if (!this.dragon.canSee(player)) {
            scale *= 0.5;
         }
         this.currentAttackHit = true;
         float damage = (float)(baseDamage * scale);
         if (damage > 0.01F && this.scheduler.allowsDamage()) {
            player.damage(world, this.dragon.getDamageSources().mobAttack(this.dragon), damage);
         }
         player.takeKnockback(1.15 * scale, -dx, -dz);
         player.addVelocity(0.0, 0.28 * scale, 0.0);
      }
   }

   private void drawInwardSpiral(ServerWorld world, Vec3d center, double radius, DragonBrainConfig config) {
      int points = this.scaledParticles(10, config);
      double shrink = 1.0 - (this.age % 12) / 12.0;
      for (int i = 0; i < points; i++) {
         double angle = (Math.PI * 2) * i / points + this.age * 0.15;
         double r = radius * shrink;
         world.spawnParticles(
            ParticleTypes.PORTAL, center.x + Math.cos(angle) * r, center.y + 0.3, center.z + Math.sin(angle) * r, 1, 0.0, 0.02, 0.0, 0.0
         );
      }
   }

   private void tickWell(ServerWorld world, DragonBrainConfig config) {
      if (this.wellAge < 0) {
         return;
      }
      this.wellAge++;
      double radius = config.gravityWellRadius;
      if (this.wellAge % 4 == 0) {
         this.ring(world, ABYSS_DUST, this.wellCenter.add(0.0, 0.2, 0.0), radius, this.scaledParticles(36, config));
      }
      this.drawInwardSpiral(world, this.wellCenter, radius, config);

      if (this.wellAge >= this.wellDuration) {
         this.releaseWell(world, config);
         this.wellAge = -1;
         return;
      }

      // Gentle pull only: strictly weaker than walking speed, applied as a
      // small per-tick acceleration with no snapping, teleporting or rooting.
      float difficultyScale = switch (this.tuning.mode()) {
         case ROOKIE -> 0.8F;
         case EXPERT -> 1.15F;
         default -> 1.0F;
      };
      double pull = config.gravityWellPullStrength * difficultyScale;
      if (pull <= 0.001) {
         return;
      }
      for (ServerPlayerEntity player : world.getPlayers()) {
         if (player.isSpectator() || player.isCreative()) {
            continue;
         }
         double dx = this.wellCenter.x - player.getX();
         double dz = this.wellCenter.z - player.getZ();
         double distance = Math.sqrt(dx * dx + dz * dz);
         if (distance > radius || distance < 0.5 || Math.abs(player.getY() - this.wellCenter.y) > 4.0) {
            continue;
         }
         double edgeTaper = 0.45 + 0.55 * (1.0 - distance / radius);
         player.addVelocity(dx / distance * pull * edgeTaper, 0.0, dz / distance * pull * edgeTaper);
         player.velocityDirty = true;
      }
   }

   private void releaseWell(ServerWorld world, DragonBrainConfig config) {
      double radius = config.gravityWellRadius;
      this.playSound(world, ModSounds.METEOR_IMPACT, 0.8F, 0.7F, this.wellCenter);
      this.ring(world, ParticleTypes.SOUL_FIRE_FLAME, this.wellCenter.add(0.0, 0.3, 0.0), radius * 0.7, this.scaledParticles(40, config));
      this.ring(world, ParticleTypes.PORTAL, this.wellCenter.add(0.0, 0.3, 0.0), radius, this.scaledParticles(48, config));
      this.burst(world, ParticleTypes.DRAGON_BREATH, this.wellCenter.add(0.0, 0.6, 0.0), this.scaledParticles(20, config), 2.0);

      float damage = this.tuning.clampDamage(config.gravityWellDamage);
      for (ServerPlayerEntity player : world.getPlayers()) {
         if (player.isSpectator() || player.isCreative()) {
            continue;
         }
         double dx = player.getX() - this.wellCenter.x;
         double dz = player.getZ() - this.wellCenter.z;
         double distance = Math.sqrt(dx * dx + dz * dz);
         if (distance > radius * 1.05 || Math.abs(player.getY() - this.wellCenter.y) > 4.0) {
            continue;
         }
         this.currentAttackHit = true;
         if (damage > 0.01F && this.scheduler.allowsDamage()) {
            if (player.damage(world, this.dragon.getDamageSources().magic(), damage)) {
               this.scheduler.onDamageDealt(AttackType.GRAVITY_WELL.pressureWeight());
               this.adaptive.onAttackLanded();
            }
         }
         // The release always throws survivors clear of the well.
         player.takeKnockback(0.9, -dx, -dz);
         player.addVelocity(0.0, 0.32, 0.0);
      }
   }

   /** Cataclysm aura + attack trails. Strictly bounded spawn rates. */
   private void tickCataclysmAura(ServerWorld world, DragonBrainConfig config) {
      if (this.phase != BossPhase.CATACLYSM || !config.cataclysmAura || !config.ambientParticles) {
         return;
      }
      Vec3d pos = this.dragon.getEntityPos();
      if (this.age % 2 == 0) {
         int count = this.scaledParticles(2, config);
         for (int i = 0; i < count; i++) {
            double x = pos.x + (this.random.nextDouble() - 0.5) * 7.0;
            double y = pos.y + this.random.nextDouble() * 2.5;
            double z = pos.z + (this.random.nextDouble() - 0.5) * 7.0;
            ParticleEffect particle = (this.age + i) % 3 == 0 ? ABYSS_DUST : ParticleTypes.PORTAL;
            world.spawnParticles(particle, x, y, z, 1, 0.0, 0.05, 0.0, 0.01);
         }
      }
      if (this.scheduler.getState() == CombatState.ATTACK) {
         world.spawnParticles(
            ParticleTypes.SOUL_FIRE_FLAME, this.dragon.head.getX(), this.dragon.head.getBodyY(0.5), this.dragon.head.getZ(), 1, 0.2, 0.1, 0.2, 0.01
         );
      }
   }

   private static final class Meteor {
      Vec3d impact = Vec3d.ZERO;
      int ticksToImpact;
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
               this.clearAbilityRuntimes();
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
         this.music.stop(world, this.dragon);
         if (world != null && ConfigManager.get().mechanicVictorySequence && this.dragon.isDead()) {
            VictorySequence.play(world, this.dragon.getEntityPos());
            if (this.phase == BossPhase.CATACLYSM) {
               // Short abyssal flourish layered over the untouched vanilla
               // death sequence (XP, portal and egg are vanilla's business).
               Vec3d pos = this.dragon.getEntityPos();
               this.ring(world, ParticleTypes.END_ROD, pos.add(0.0, 1.0, 0.0), 6.0, 60);
               this.ring(world, ParticleTypes.PORTAL, pos.add(0.0, 2.0, 0.0), 9.0, 80);
               this.burst(world, ABYSS_DUST, pos.add(0.0, 2.0, 0.0), 40, 3.0);
            }
         }
      }
   }

   private void resetForDisabled() {
      if (this.scheduler.getState() != CombatState.IDLE) {
         this.scheduler.reset();
         this.pulseAge = -1;
         this.clearAbilityRuntimes();
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
      ) + " " + this.abilityLine();
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
