package com.teamlifebind.common;

import java.util.Locale;

public enum TotemBalance {
    CLASSIC(1_400, 0L),
    BALANCED(400, 900L);

    private final int upgradedRegenerationTicks;
    private final long teamCooldownTicks;

    TotemBalance(int upgradedRegenerationTicks, long teamCooldownTicks) {
        this.upgradedRegenerationTicks = upgradedRegenerationTicks;
        this.teamCooldownTicks = teamCooldownTicks;
    }

    public static TotemBalance parse(String raw) {
        if (raw != null) {
            String normalized = raw.trim().toUpperCase(Locale.ROOT);
            for (TotemBalance value : values()) {
                if (value.name().equals(normalized)) {
                    return value;
                }
            }
        }
        return CLASSIC;
    }

    public TotemBalance next() {
        return this == CLASSIC ? BALANCED : CLASSIC;
    }

    public int upgradedRegenerationTicks() {
        return upgradedRegenerationTicks;
    }

    public long teamCooldownTicks() {
        return teamCooldownTicks;
    }

    /** The total absorption after rescue, replacing any previous totem absorption. */
    public double upgradedAbsorption(double maxHealth, boolean outsideEnd) {
        if (this == CLASSIC) {
            return outsideEnd ? 20.0D : 16.0D;
        }
        if (!Double.isFinite(maxHealth) || maxHealth <= 0.0D) {
            return 0.0D;
        }
        return Math.min(8.0D, maxHealth * 0.5D);
    }

    public int upgradedAbsorptionAmplifier(double maxHealth) {
        return upgradedAbsorptionAmplifier(maxHealth, false);
    }

    /** Ensure the effect capacity can hold the exact total absorption for this rescue. */
    public int upgradedAbsorptionAmplifier(double maxHealth, boolean outsideEnd) {
        if (this == CLASSIC) {
            return outsideEnd ? 4 : 3;
        }
        return Math.max(0, (int) Math.ceil(upgradedAbsorption(maxHealth, outsideEnd) / 4.0D) - 1);
    }
}
