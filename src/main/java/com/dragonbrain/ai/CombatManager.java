package com.dragonbrain.ai;

import com.dragonbrain.config.ConfigManager;
import com.dragonbrain.core.AttackType;
import com.dragonbrain.core.CombatState;
import com.dragonbrain.core.DragonBrainConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.phase.PhaseType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

public final class CombatManager {
   private static final Map<UUID, DragonCombatController> CONTROLLERS = new HashMap();
   private static final Map<UUID, Integer> ATTACK_PHASE_ALLOWANCE = new HashMap();
   private static final List<CombatManager.Cinematic> CINEMATICS = new ArrayList();
   private static int cleanupTimer;

   private CombatManager() {
   }

   public static void addCinematic(CombatManager.Cinematic cinematic) {
      if (CINEMATICS.size() < 8) {
         CINEMATICS.add(cinematic);
      }
   }

   public static void tickDragon(EnderDragonEntity dragon, ServerWorld world) {
      DragonBrainConfig config = ConfigManager.get();
      if (!config.enabled) {
         DragonCombatController existing = (DragonCombatController)CONTROLLERS.get(dragon.getUuid());
         if (existing != null) {
            existing.dispose(world);
            CONTROLLERS.remove(dragon.getUuid());
         }
      } else {
         DragonCombatController controller = (DragonCombatController)CONTROLLERS.computeIfAbsent(dragon.getUuid(), id -> new DragonCombatController(dragon));
         controller.tick(world);
      }
   }

   public static void onServerTick(MinecraftServer server) {
      cleanupTimer++;
      Iterator<CombatManager.Cinematic> iterator = CINEMATICS.iterator();

      while (iterator.hasNext()) {
         CombatManager.Cinematic cinematic = (CombatManager.Cinematic)iterator.next();
         cinematic.ticksLeft--;
         if (cinematic.ticksLeft > 0) {
            Vec3d pos = cinematic.pos;
            cinematic.world
               .spawnParticles(
                  ParticleTypes.END_ROD,
                  pos.x + (cinematic.world.random.nextDouble() - 0.5) * 6.0,
                  pos.y + 1.0 + cinematic.world.random.nextDouble() * 4.0,
                  pos.z + (cinematic.world.random.nextDouble() - 0.5) * 6.0,
                  2,
                  0.1,
                  0.1,
                  0.1,
                  0.01
               );
         } else {
            iterator.remove();
         }
      }

      ATTACK_PHASE_ALLOWANCE.replaceAll((id, ticks) -> ticks - 1);
      ATTACK_PHASE_ALLOWANCE.values().removeIf(ticks -> ticks <= 0);
      if (cleanupTimer % 100 == 0) {
         CONTROLLERS.entrySet().removeIf(entry -> {
            DragonCombatController controller = (DragonCombatController)entry.getValue();
            EnderDragonEntity dragon = controller.dragon();
            if (!controller.isDisposed() && !dragon.isRemoved() && !dragon.isDead()) {
               return false;
            }

            controller.dispose(dragon.getEntityWorld() instanceof ServerWorld world ? world : null);
            return true;
         });
      }
   }

   public static void onServerStopped() {
      CONTROLLERS.clear();
      ATTACK_PHASE_ALLOWANCE.clear();
      CINEMATICS.clear();
   }

   @Nullable
   public static DragonCombatController get(EnderDragonEntity dragon) {
      return (DragonCombatController)CONTROLLERS.get(dragon.getUuid());
   }

   public static List<DragonCombatController> controllers() {
      return new ArrayList(CONTROLLERS.values());
   }

   @Nullable
   public static DragonCombatController findNearest(ServerWorld world, Vec3d pos, double range) {
      DragonCombatController best = null;
      double bestDistance = range * range;

      for (DragonCombatController controller : CONTROLLERS.values()) {
         EnderDragonEntity dragon = controller.dragon();
         if (dragon.getEntityWorld() == world) {
            double distance = dragon.getEntityPos().squaredDistanceTo(pos);
            if (distance <= bestDistance) {
               bestDistance = distance;
               best = controller;
            }
         }
      }

      return best;
   }

   public static void allowVanillaAttackPhase(EnderDragonEntity dragon, int ticks) {
      ATTACK_PHASE_ALLOWANCE.put(dragon.getUuid(), ticks);
   }

   public static boolean vetoPhaseChange(EnderDragonEntity dragon, PhaseType<?> type) {
      DragonBrainConfig config = ConfigManager.get();
      if (!config.enabled) {
         return false;
      }

      boolean isAttackPhase = type == PhaseType.STRAFE_PLAYER || type == PhaseType.CHARGING_PLAYER;
      if (!isAttackPhase) {
         return false;
      }

      Integer allowance = (Integer)ATTACK_PHASE_ALLOWANCE.get(dragon.getUuid());
      return allowance == null || allowance <= 0;
   }

   public static float scaleDragonDamage(DamageSource source, float amount) {
      DragonBrainConfig config = ConfigManager.get();
      if (!config.enabled) {
         return amount;
      }

      if (source.getAttacker() instanceof EnderDragonEntity dragon) {
         DragonCombatController controller = (DragonCombatController)CONTROLLERS.get(dragon.getUuid());
         if (controller != null) {
            double scale = controller.damageScale();
            return (float)Math.max(0.0, amount * scale);
         }
      }

      return amount;
   }

   public static boolean allowDragonDamage(LivingEntity victim, DamageSource source, float amount) {
      DragonBrainConfig config = ConfigManager.get();
      if (!config.enabled || !config.enforceMinimumDamageInterval) {
         return true;
      }

      if (!(victim instanceof ServerPlayerEntity)) {
         return true;
      }

      if (source.getAttacker() instanceof EnderDragonEntity dragon) {
         if (dragon.getEntityWorld() instanceof ServerWorld world) {
            DragonCombatController controller = (DragonCombatController)CONTROLLERS.get(dragon.getUuid());
            return controller == null ? true : controller.allowDamageToPlayer(world, amount);
         } else {
            return true;
         }
      } else {
         return true;
      }
   }

   public static void onDamageDealt(LivingEntity entity, DamageSource source, float dealt) {
      if (entity instanceof EnderDragonEntity dragon) {
         if (source.getAttacker() instanceof PlayerEntity) {
            DragonCombatController controller = (DragonCombatController)CONTROLLERS.get(dragon.getUuid());
            if (controller != null && dragon.getEntityWorld() instanceof ServerWorld world) {
               controller.onDragonDamaged(world, dealt);
            }
         }
      }
   }

   public static void onDragonDeath(EnderDragonEntity dragon) {
      DragonCombatController controller = (DragonCombatController)CONTROLLERS.remove(dragon.getUuid());
      if (controller != null && dragon.getEntityWorld() instanceof ServerWorld world) {
         controller.dispose(world);
      }
   }

   public static String describeAll() {
      if (CONTROLLERS.isEmpty()) {
         return "No active DragonBrain controllers.";
      }

      StringBuilder builder = new StringBuilder();

      for (DragonCombatController controller : CONTROLLERS.values()) {
         builder.append("- ").append(controller.debugLine()).append('\n');
      }

      return builder.toString().stripTrailing();
   }

   public static boolean forceMechanic(ServerWorld world, Vec3d pos, String mechanic) {
      DragonCombatController controller = findNearest(world, pos, 256.0);
      if (controller == null) {
         return false;
      }

      EnderDragonEntity dragon = controller.dragon();
      switch (mechanic.toLowerCase(Locale.ROOT)) {
         case "dive":
            controller.forceAttack(AttackType.DIVE);
            break;
         case "fireball":
            controller.forceAttack(AttackType.FIREBALL);
            break;
         case "gust":
         case "winggust":
            controller.forceAttack(AttackType.WING_GUST);
            break;
         case "pulse":
         case "voidpulse":
            controller.forceAttack(AttackType.VOID_PULSE);
            break;
         case "stagger":
            controller.onDragonDamaged(world, 999.0F);
            break;
         case "rage":
            dragon.setHealth(dragon.getMaxHealth() * 0.45F);
            break;
         case "final":
            dragon.setHealth(dragon.getMaxHealth() * 0.2F);
            break;
         case "cataclysm":
            dragon.setHealth(dragon.getMaxHealth() * 0.08F);
            break;
         case "victory":
            VictorySequence.play(world, dragon.getEntityPos());
            return true;
         case "reset":
            controller.scheduler().reset();
            return true;
         default:
            return false;
      }

      return true;
   }

   public static boolean isAttacking(EnderDragonEntity dragon) {
      DragonCombatController controller = (DragonCombatController)CONTROLLERS.get(dragon.getUuid());
      return controller != null && controller.scheduler().getState() == CombatState.ATTACK;
   }

   public static final class Cinematic {
      final ServerWorld world;
      final Vec3d pos;
      int ticksLeft;

      Cinematic(ServerWorld world, Vec3d pos, int ticks) {
         this.world = world;
         this.pos = pos;
         this.ticksLeft = ticks;
      }
   }
}
