package com.teamlifebind.common;

import java.util.Objects;

public record GameOptions(int teamCount, HealthPreset healthPreset) {

    public GameOptions {
        if (teamCount < 2) {
            throw new IllegalArgumentException("teamCount must be at least 2");
        }
        if (teamCount > 32) {
            throw new IllegalArgumentException("teamCount must be at most 32");
        }

        healthPreset = Objects.requireNonNullElse(healthPreset, HealthPreset.ONE_HEART);
    }
}
