package com.teamlifebind.paper.api;

import java.util.UUID;

public interface TeamLifeBindPaperApi {

    boolean isBuiltinScoreboardEnabled();

    TeamLifeBindMatchSnapshot getMatchSnapshot();

    TeamLifeBindPlayerSnapshot getPlayerSnapshot(UUID playerId);

    TeamLifeBindSidebarSnapshot getSidebarSnapshot(UUID viewerId);
}
