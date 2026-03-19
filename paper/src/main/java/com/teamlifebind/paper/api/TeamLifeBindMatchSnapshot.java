package com.teamlifebind.paper.api;

import com.teamlifebind.common.HealthPreset;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record TeamLifeBindMatchSnapshot(
    boolean running,
    int configuredTeamCount,
    HealthPreset healthPreset,
    int onlinePlayerCount,
    Set<UUID> readyPlayers,
    Map<UUID, Integer> teamAssignments,
    Set<Integer> bedTeams,
    boolean noRespawnEnabled,
    Set<String> noRespawnDimensions,
    String activeMatchWorldName
) {

    public TeamLifeBindMatchSnapshot {
        healthPreset = Objects.requireNonNull(healthPreset, "healthPreset");
        readyPlayers = Set.copyOf(readyPlayers);
        teamAssignments = Map.copyOf(teamAssignments);
        bedTeams = Set.copyOf(bedTeams);
        noRespawnDimensions = Set.copyOf(noRespawnDimensions);
    }
}
