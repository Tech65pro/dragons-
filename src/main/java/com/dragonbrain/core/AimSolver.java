package com.dragonbrain.core;

import java.util.Random;

public final class AimSolver {
   private AimSolver() {
   }

   public static Vec predict(Vec shooter, Vec target, Vec velocity, double travelTicks, DifficultyTuning tuning, Random random) {
      double lag = Math.max(0, tuning.reactionDelayTicks()) * 0.35;
      double leadTicks = Math.max(0.0, travelTicks - lag);
      Vec lead = velocity.multiply(leadTicks * tuning.predictionStrength());
      Vec predicted = target.add(lead);
      double speed = velocity.horizontalLength();
      double sloppiness = speed * 0.35 * (1.0 - tuning.predictionStrength());
      return predicted.add(randomOffset(random, sloppiness));
   }

   public static Vec applyAimError(Vec direction, float errorDegrees, Random random) {
      if (errorDegrees <= 0.01F) {
         return direction.normalize();
      }

      double yawError = gaussian(random) * errorDegrees;
      double pitchError = gaussian(random) * errorDegrees * 0.6;
      return rotate(direction.normalize(), yawError, pitchError);
   }

   public static Vec applySpread(Vec direction, float spreadDegrees, Random random) {
      if (spreadDegrees <= 0.01F) {
         return direction.normalize();
      }

      double yaw = (random.nextDouble() * 2.0 - 1.0) * spreadDegrees;
      double pitch = (random.nextDouble() * 2.0 - 1.0) * spreadDegrees;
      return rotate(direction.normalize(), yaw, pitch);
   }

   public static Vec rotate(Vec dir, double yawDegrees, double pitchDegrees) {
      double yaw = Math.toRadians(yawDegrees);
      double pitch = Math.toRadians(pitchDegrees);
      double cosYaw = Math.cos(yaw);
      double sinYaw = Math.sin(yaw);
      double x = dir.x() * cosYaw + dir.z() * sinYaw;
      double z = -dir.x() * sinYaw + dir.z() * cosYaw;
      double y = dir.y();
      double cosPitch = Math.cos(pitch);
      double sinPitch = Math.sin(pitch);
      double newY = y * cosPitch - z * sinPitch;
      double newZ = y * sinPitch + z * cosPitch;
      return new Vec(x, newY, newZ).normalize();
   }

   public static double gaussian(Random random) {
      double u1 = Math.max(1.0E-9, random.nextDouble());
      double u2 = random.nextDouble();
      double g = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos((Math.PI * 2) * u2);
      return Math.max(-2.0, Math.min(2.0, g));
   }

   private static Vec randomOffset(Random random, double magnitude) {
      return magnitude <= 1.0E-6 ? Vec.ZERO : new Vec(gaussian(random), gaussian(random) * 0.5, gaussian(random)).multiply(magnitude);
   }
}
