package com.teamlifebind.common;

/** Timing and quantities shared by the platform supply adapters. */
public final class SupplyDropRules {
    public static final long ANNOUNCEMENT_LEAD_TICKS = 1_200L;
    public static final long CLAIM_TICKS = 160L;
    public static final double CLAIM_RADIUS_BLOCKS = 3.0D;

    private SupplyDropRules() {
    }

    public static int resolveCount(int configuredCount, boolean dynamic, int activeTeams) {
        if (configuredCount <= 0) {
            return 0;
        }
        return dynamic ? Math.max(2, Math.min(8, activeTeams)) : configuredCount;
    }

    public static long announcementDelayTicks(long eventDelayTicks) {
        return eventDelayTicks <= ANNOUNCEMENT_LEAD_TICKS ? 0L : eventDelayTicks - ANNOUNCEMENT_LEAD_TICKS;
    }
}
