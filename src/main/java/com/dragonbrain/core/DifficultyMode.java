package com.dragonbrain.core;

/** Preserved from DragonBrain 2.0 (ROOKIE / NORMAL / EXPERT / ADAPTIVE). */
public enum DifficultyMode {
    ROOKIE,
    NORMAL,
    EXPERT,
    ADAPTIVE;

    public static DifficultyMode parse(String name) {
        for (DifficultyMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return NORMAL;
    }
}
