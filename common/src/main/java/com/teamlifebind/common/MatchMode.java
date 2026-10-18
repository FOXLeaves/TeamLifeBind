package com.teamlifebind.common;

import java.util.Locale;

public enum MatchMode {
    DRAGON_HUNT,
    LIMITED_LIVES,
    RANDOM_GRIMOIRE;

    public static MatchMode parse(String value) {
        if (value != null) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return DRAGON_HUNT;
    }

    public MatchMode next() {
        return switch (this) {
            case DRAGON_HUNT -> LIMITED_LIVES;
            case LIMITED_LIVES -> RANDOM_GRIMOIRE;
            case RANDOM_GRIMOIRE -> DRAGON_HUNT;
        };
    }
}
