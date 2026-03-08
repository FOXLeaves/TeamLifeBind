package com.teamlifebind.common;

import java.util.List;
import java.util.UUID;

public record DeathResult(boolean triggered, int team, List<UUID> victims) {

    public DeathResult {
        victims = List.copyOf(victims);
    }

    public static DeathResult none() {
        return new DeathResult(false, -1, List.of());
    }
}
