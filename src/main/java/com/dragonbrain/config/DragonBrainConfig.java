package com.dragonbrain.config;

import com.dragonbrain.core.DifficultyMode;

/**
 * Configuration. Field names for the visual/audio toggles are preserved
 * from DragonBrain 2.0 (customTexture, emissiveEyes, customSounds,
 * ambientParticles, finalPhaseHealthThreshold, ...).
 */
public class DragonBrainConfig {
    public boolean enabled = true;
    public boolean debug = false;
    public String difficulty = DifficultyMode.NORMAL.name();

    // boss phases
    public float ragePhaseHealthThreshold = 0.55f;
    public float finalPhaseHealthThreshold = 0.25f;

    // visuals (client-facing; read on the logical client)
    public boolean customTexture = true;
    public boolean emissiveEyes = true;
    public boolean ambientParticles = true;

    // audio
    public boolean customSounds = true;
    public boolean ambientEndAudio = true;
    public float ambientVolume = 0.35f;
    public float musicVolume = 0.55f;

    public DifficultyMode difficultyMode() {
        return DifficultyMode.parse(difficulty);
    }

    public void clamp() {
        ragePhaseHealthThreshold = clamp01(ragePhaseHealthThreshold);
        finalPhaseHealthThreshold = clamp01(finalPhaseHealthThreshold);
        if (finalPhaseHealthThreshold >= ragePhaseHealthThreshold) {
            finalPhaseHealthThreshold = Math.max(0.05f, ragePhaseHealthThreshold - 0.1f);
        }
        ambientVolume = clamp01(ambientVolume);
        musicVolume = clamp01(musicVolume);
    }

    private static float clamp01(float v) {
        if (Float.isNaN(v)) {
            return 0.5f;
        }
        return Math.max(0.0f, Math.min(1.0f, v));
    }
}
