package com.teamlifebind.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class TeamLifeBindEngine {

    private final Random random;
    private final Map<UUID, Integer> teamByPlayer = new HashMap<>();

    private boolean running;
    private int configuredTeamCount;
    private Integer winnerTeam;

    public TeamLifeBindEngine() {
        this(new Random());
    }

    public TeamLifeBindEngine(Random random) {
        this.random = random;
    }

    public synchronized StartResult start(List<UUID> playerIds, GameOptions options) {
        if (playerIds == null || playerIds.isEmpty()) {
            throw new IllegalArgumentException("playerIds must not be empty");
        }

        List<UUID> uniquePlayers = playerIds.stream().distinct().collect(Collectors.toCollection(ArrayList::new));
        if (uniquePlayers.size() < options.teamCount()) {
            throw new IllegalArgumentException("Player count must be >= team count");
        }

        this.teamByPlayer.clear();
        this.running = true;
        this.configuredTeamCount = options.teamCount();
        this.winnerTeam = null;

        Collections.shuffle(uniquePlayers, random);
        for (int i = 0; i < uniquePlayers.size(); i++) {
            int team = (i % configuredTeamCount) + 1;
            teamByPlayer.put(uniquePlayers.get(i), team);
        }

        return new StartResult(teamByPlayer, getAllTeamsInternal());
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

        this.teamByPlayer.clear();
        this.running = true;
        this.configuredTeamCount = options.teamCount();
        this.winnerTeam = null;
        this.teamByPlayer.putAll(normalized);

        return new StartResult(teamByPlayer, getAllTeamsInternal());
    }

    public synchronized DeathResult onPlayerDeath(UUID playerId) {
        if (!running) {
            return DeathResult.none();
        }

        Integer team = teamByPlayer.get(playerId);
        if (team == null) {
            return DeathResult.none();
        }

        List<UUID> victims = teamByPlayer.entrySet()
            .stream()
            .filter(entry -> entry.getValue().equals(team))
            .map(Map.Entry::getKey)
            .toList();

        return new DeathResult(true, team, victims);
    }

    public synchronized Integer onObjectiveCompleted(UUID playerId) {
        if (!running) {
            return null;
        }

        Integer team = teamByPlayer.get(playerId);
        if (team == null) {
            return null;
        }

        winnerTeam = team;
        running = false;
        return winnerTeam;
    }

    public synchronized void stop() {
        running = false;
        winnerTeam = null;
        teamByPlayer.clear();
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
        return teamByPlayer.get(playerId);
    }

    public synchronized Map<UUID, Integer> assignments() {
        return Map.copyOf(teamByPlayer);
    }

    private Set<Integer> getAllTeamsInternal() {
        Set<Integer> active = new HashSet<>();
        for (int i = 1; i <= configuredTeamCount; i++) {
            active.add(i);
        }
        return active;
    }
}
