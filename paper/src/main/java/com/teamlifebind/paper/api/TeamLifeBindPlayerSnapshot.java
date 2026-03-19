package com.teamlifebind.paper.api;

import java.util.UUID;

public record TeamLifeBindPlayerSnapshot(
    UUID playerId,
    Integer team,
    boolean online,
    boolean ready,
    boolean spectator,
    boolean respawnLocked,
    int respawnSecondsRemaining,
    String worldName
) {}
