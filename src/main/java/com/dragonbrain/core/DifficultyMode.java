package com.dragonbrain.core;

import java.util.Locale;

public enum DifficultyMode {
   ROOKIE,
   NORMAL,
   EXPERT,
   ADAPTIVE;

   public static DifficultyMode parse(String value, DifficultyMode fallback) {
      if (value == null) {
         return fallback;
      }

      try {
         return valueOf(value.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
         return fallback;
      }
   }

   public String id() {
      return this.name().toLowerCase(Locale.ROOT);
   }
}
