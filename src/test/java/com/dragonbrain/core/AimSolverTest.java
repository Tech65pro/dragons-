package com.dragonbrain.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class AimSolverTest {
   private static DifficultyTuning tuning(float prediction) {
      return new DifficultyTuning(
         DifficultyMode.NORMAL, 100, 0, 1.0F, 1.0F, 1.0F, 8, 10, 0.0F, prediction, 0.0F, 0, 1.0F, 1.0F, 3, 50, 0.0F, false
      );
   }

   @Test
   void vecBasics() {
      assertEquals(Vec.ZERO, new Vec(0, 0, 0).normalize());
      assertEquals(5.0, new Vec(3, 4, 0).length(), 1e-9);
      assertEquals(1.0, new Vec(0, 3, 0).normalize().y(), 1e-9);
   }

   @Test
   void zeroErrorAimIsUnchanged() {
      Vec dir = new Vec(1, 2, 3).normalize();
      Vec out = AimSolver.applyAimError(dir, 0.0F, new Random(1L));
      assertEquals(dir.x(), out.x(), 1e-9);
      assertEquals(dir.y(), out.y(), 1e-9);
      assertEquals(dir.z(), out.z(), 1e-9);
   }

   @Test
   void predictionOfStationaryTargetIsExact() {
      Vec predicted = AimSolver.predict(new Vec(0, 0, 0), new Vec(10, 5, -3), Vec.ZERO, 20.0, tuning(0.5F), new Random(2L));
      assertEquals(10.0, predicted.x(), 1e-9);
      assertEquals(5.0, predicted.y(), 1e-9);
      assertEquals(-3.0, predicted.z(), 1e-9);
   }

   @Test
   void predictionLeadsMovingTargetBoundedly() {
      Vec predicted = AimSolver.predict(new Vec(0, 0, 0), new Vec(0, 0, 0), new Vec(0.2, 0, 0), 20.0, tuning(1.0F), new Random(3L));
      // Full prediction leads by ~4 blocks; imperfection must keep it bounded.
      assertTrue(predicted.x() > 2.0 && predicted.x() < 6.0, "lead was " + predicted.x());
   }

   @Test
   void gaussianIsClamped() {
      Random random = new Random(4L);
      for (int i = 0; i < 10_000; i++) {
         double g = AimSolver.gaussian(random);
         assertTrue(g >= -2.0 && g <= 2.0);
      }
   }
}
