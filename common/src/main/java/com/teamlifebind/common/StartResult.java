package com.teamlifebind.common;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record StartResult(Map<UUID, Integer> teamByPlayer, Set<Integer> activeTeams) {

    public StartResult {
        teamByPlayer = Map.copyOf(teamByPlayer);
        activeTeams = Set.copyOf(activeTeams);
    }
}
