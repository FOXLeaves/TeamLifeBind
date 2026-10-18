package com.teamlifebind.common;

import java.util.HashMap;
import java.util.Map;

/** Tick-based team rescue cooldown. Clear this state whenever a round starts or stops. */
public final class TeamTotemCooldown {

    private final Map<Integer, Cooldown> cooldowns = new HashMap<>();

    public synchronized boolean isReady(int team, long tick) {
        return remainingTicks(team, tick) == 0L;
    }

    public synchronized boolean tryAcquire(int team, long tick, long cooldownTicks) {
        if (cooldownTicks <= 0L) {
            return true;
        }
        if (!isReady(team, tick)) {
            return false;
        }
        cooldowns.put(team, new Cooldown(tick, cooldownTicks));
        return true;
    }

    public synchronized long remainingTicks(int team, long tick) {
        Cooldown cooldown = cooldowns.get(team);
        if (cooldown == null) {
            return 0L;
        }
        if (tick < cooldown.startTick()) {
            return cooldown.durationTicks();
        }
        long elapsed = tick - cooldown.startTick();
        if (Long.compareUnsigned(elapsed, cooldown.durationTicks()) >= 0) {
            return 0L;
        }
        return cooldown.durationTicks() - elapsed;
    }

    public synchronized void clear() {
        cooldowns.clear();
    }

    private record Cooldown(long startTick, long durationTicks) {
    }
}
