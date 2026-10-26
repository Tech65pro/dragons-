package com.dragonbrain.core;

/**
 * Preserved from DragonBrain 2.0 (same enum constants and health-ratio
 * semantics recovered from the compiled 2.0 jar). Phase visuals are only
 * ever driven by these real phases.
 */
public enum BossPhase {
    NORMAL,
    RAGE,
    FINAL;

    public static BossPhase forHealth(float healthRatio, float rageThreshold, float finalThreshold) {
        if (healthRatio <= finalThreshold) {
            return FINAL;
        }
        if (healthRatio <= rageThreshold) {
            return RAGE;
        }
        return NORMAL;
    }
}
