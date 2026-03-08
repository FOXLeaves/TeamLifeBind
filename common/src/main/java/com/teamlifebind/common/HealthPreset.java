package com.teamlifebind.common;

import java.util.Locale;

public enum HealthPreset {
    ONE_HEART(2.0D),
    HALF_ROW(10.0D),
    ONE_ROW(20.0D);

    private final double maxHealth;

    HealthPreset(double maxHealth) {
        this.maxHealth = maxHealth;
    }

    public double maxHealth() {
        return maxHealth;
    }

    public static HealthPreset fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return ONE_HEART;
        }

        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (HealthPreset value : values()) {
            if (value.name().equals(normalized)) {
                return value;
            }
        }
        return ONE_HEART;
    }
}
