package com.teamlifebind.common;

import java.util.Locale;

public enum MatchPace {
    CLASSIC(18_000L, 24_000L, 36_000L, 3_600L),
    FAST(9_600L, 12_000L, 18_000L, 2_400L);

    private final long autoPortalDelayTicks;
    private final long supplyDropDelayTicks;
    private final long strongholdHintDelayTicks;
    private final long strongholdHintIntervalTicks;

    MatchPace(long autoPortalDelayTicks, long supplyDropDelayTicks, long strongholdHintDelayTicks, long strongholdHintIntervalTicks) {
        this.autoPortalDelayTicks = autoPortalDelayTicks;
        this.supplyDropDelayTicks = supplyDropDelayTicks;
        this.strongholdHintDelayTicks = strongholdHintDelayTicks;
        this.strongholdHintIntervalTicks = strongholdHintIntervalTicks;
    }

    public static MatchPace parse(String raw) {
        if (raw != null) {
            String normalized = raw.trim().toUpperCase(Locale.ROOT);
            for (MatchPace value : values()) {
                if (value.name().equals(normalized)) {
                    return value;
                }
            }
        }
        return CLASSIC;
    }

    public MatchPace next() {
        return this == CLASSIC ? FAST : CLASSIC;
    }

    public long autoPortalDelayTicks() {
        return autoPortalDelayTicks;
    }

    public long supplyDropDelayTicks() {
        return supplyDropDelayTicks;
    }

    public long strongholdHintDelayTicks() {
        return strongholdHintDelayTicks;
    }

    public long strongholdHintIntervalTicks() {
        return strongholdHintIntervalTicks;
    }
}
