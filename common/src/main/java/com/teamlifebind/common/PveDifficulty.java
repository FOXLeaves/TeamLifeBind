package com.teamlifebind.common;

import java.util.Locale;

/** Encounter tuning captured at launch; world difficulty, enemy counts and loot odds are independent. */
public enum PveDifficulty {
    EASY(0.75D, 0.75D),
    NORMAL(1.0D, 1.0D),
    HARD(1.35D, 1.25D);

    private final double healthScale;
    private final double damageScale;

    PveDifficulty(double healthScale, double damageScale) {
        this.healthScale = healthScale;
        this.damageScale = damageScale;
    }

    public double healthScale() { return healthScale; }
    public double damageScale() { return damageScale; }
    public String languageKey() { return "play.pve.difficulty." + name().toLowerCase(Locale.ROOT); }
    public PveDifficulty next() { return values()[(ordinal() + 1) % values().length]; }

    public static PveDifficulty parse(String value) {
        if (value == null) return NORMAL;
        try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { return NORMAL; }
    }
}
