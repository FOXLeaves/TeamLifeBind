package com.teamlifebind.common;

import java.util.Objects;

/** Captured at round start; credits count respawns, so zero still permits the just-paid respawn. */
public record CampaignRules(MatchMode matchMode, int respawnCredits, DragonVictoryMode dragonVictoryMode,
                            boolean controlPointEnabled, boolean borderEnabled) {
    public CampaignRules {
        matchMode = Objects.requireNonNullElse(matchMode, MatchMode.DRAGON_HUNT);
        respawnCredits = Math.max(1, Math.min(10, respawnCredits));
        dragonVictoryMode = Objects.requireNonNullElse(dragonVictoryMode, DragonVictoryMode.LAST_HIT);
    }

    public static CampaignRules defaults() {
        return new CampaignRules(MatchMode.DRAGON_HUNT, 3, DragonVictoryMode.LAST_HIT, false, false);
    }
}
