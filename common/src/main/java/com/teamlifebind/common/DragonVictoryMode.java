package com.teamlifebind.common;

import java.util.Locale;

public enum DragonVictoryMode {
    LAST_HIT,
    CONTRIBUTION;

    public static DragonVictoryMode parse(String value) {
        if (value != null) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return LAST_HIT;
    }

    public DragonVictoryMode next() {
        return this == LAST_HIT ? CONTRIBUTION : LAST_HIT;
    }
}
