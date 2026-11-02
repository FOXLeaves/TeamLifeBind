package com.teamlifebind.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class TeamLifeBindEngine {

    private final Random random;
    private Map<UUID, Integer> teamByPlayer = Map.of();
    private Map<Integer, List<UUID>> playersByTeam = Map.of();

    private boolean running;
    private int configuredTeamCount;
    private Integer winnerTeam;

    public TeamLifeBindEngine() {
        this(new Random());
    }

    public TeamLifeBindEngine(Random random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public synchronized StartResult start(List<UUID> playerIds, GameOptions options) {
        if (playerIds == null || playerIds.isEmpty()) {
            throw new IllegalArgumentException("playerIds must not be empty");
        }
        if (options == null) {
            throw new IllegalArgumentException("options must not be null");
        }

        List<UUID> uniquePlayers = new ArrayList<>(new LinkedHashSet<>(playerIds));
        if (uniquePlayers.contains(null)) {
            throw new IllegalArgumentException("playerIds must not contain null players");
        }
        if (uniquePlayers.size() < options.teamCount()) {
            throw new IllegalArgumentException("Player count must be >= team count");
        }

        Collections.shuffle(uniquePlayers, random);
        Map<UUID, Integer> assignments = new HashMap<>();
        for (int i = 0; i < uniquePlayers.size(); i++) {
            int team = (i % options.teamCount()) + 1;
            assignments.put(uniquePlayers.get(i), team);
        }

        return beginRound(assignments, options.teamCount());
    }

    public synchronized StartResult startWithAssignments(Map<UUID, Integer> assignments, GameOptions options) {
        if (assignments == null || assignments.isEmpty()) {
            throw new IllegalArgumentException("assignments must not be empty");
        }
        if (options == null) {
            throw new IllegalArgumentException("options must not be null");
        }

        Map<UUID, Integer> normalized = new HashMap<>();
        for (Map.Entry<UUID, Integer> entry : assignments.entrySet()) {
            UUID playerId = entry.getKey();
            Integer team = entry.getValue();
            if (playerId == null || team == null) {
                throw new IllegalArgumentException("assignments must not contain null players or teams");
            }
            if (team < 1 || team > options.teamCount()) {
                throw new IllegalArgumentException("team assignment out of range: " + team);
            }
            normalized.put(playerId, team);
        }
        if (normalized.size() < options.teamCount()) {
            throw new IllegalArgumentException("Player count must be >= team count");
        }

        return beginRound(normalized, options.teamCount());
    }

    public synchronized DeathResult onPlayerDeath(UUID playerId) {
        if (!running || playerId == null) {
            return DeathResult.none();
        }

        Integer team = teamByPlayer.get(playerId);
        if (team == null) {
            return DeathResult.none();
        }

        return new DeathResult(true, team, playersByTeam.get(team));
    }

    /** Extends an existing cooperative roster without restarting its shared-life round. */
    public synchronized boolean addCooperativePlayer(UUID playerId) {
        if (!running || configuredTeamCount != 1 || playerId == null || teamByPlayer.containsKey(playerId)) return false;
        Map<UUID, Integer> assignments = new HashMap<>(teamByPlayer);
        assignments.put(playerId, 1);
        List<UUID> members = new ArrayList<>(playersByTeam.getOrDefault(1, List.of()));
        members.add(playerId);
        teamByPlayer = Map.copyOf(assignments);
        playersByTeam = Map.of(1, List.copyOf(members));
        return true;
    }

    public synchronized Integer onObjectiveCompleted(UUID playerId) {
        if (!running || playerId == null) {
            return null;
        }

        Integer team = teamByPlayer.get(playerId);
        if (team == null) {
            return null;
        }

        return onTeamObjectiveCompleted(team);
    }

    /** Allows contribution and survival objectives to finish for a team whose members are offline. */
    public synchronized Integer onTeamObjectiveCompleted(int team) {
        if (!running || !playersByTeam.containsKey(team)) {
            return null;
        }
        winnerTeam = team;
        running = false;
        return winnerTeam;
    }

    public synchronized void stop() {
        running = false;
        winnerTeam = null;
        teamByPlayer = Map.of();
        playersByTeam = Map.of();
        configuredTeamCount = 0;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized int configuredTeamCount() {
        return configuredTeamCount;
    }

    public synchronized Integer winnerTeam() {
        return winnerTeam;
    }

    public synchronized Integer teamForPlayer(UUID playerId) {
        return playerId == null ? null : teamByPlayer.get(playerId);
    }

    public synchronized Map<UUID, Integer> assignments() {
        return teamByPlayer;
    }

    private StartResult beginRound(Map<UUID, Integer> assignments, int teamCount) {
        Map<UUID, Integer> assignmentSnapshot = Map.copyOf(assignments);
        Map<Integer, List<UUID>> members = new HashMap<>();
        assignmentSnapshot.forEach((playerId, team) -> members.computeIfAbsent(team, ignored -> new ArrayList<>()).add(playerId));
        members.replaceAll((team, playerIds) -> List.copyOf(playerIds));
        Map<Integer, List<UUID>> memberSnapshot = Map.copyOf(members);
        Set<Integer> active = new HashSet<>();
        for (int i = 1; i <= teamCount; i++) {
            active.add(i);
        }
        StartResult result = new StartResult(assignmentSnapshot, active);

        teamByPlayer = assignmentSnapshot;
        playersByTeam = memberSnapshot;
        configuredTeamCount = teamCount;
        winnerTeam = null;
        running = true;
        return result;
    }
}
