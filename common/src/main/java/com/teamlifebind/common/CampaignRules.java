package com.teamlifebind.common;

import java.util.Objects;

/** Captured at round start; credits count respawns, so zero still permits the just-paid respawn. */
public record CampaignRules(MatchMode matchMode, int respawnCredits, DragonVictoryMode dragonVictoryMode,
                            boolean controlPointEnabled, boolean borderEnabled, PlayMode playMode, PveDifficulty pveDifficulty) {
    public CampaignRules {
        matchMode = Objects.requireNonNullElse(matchMode, MatchMode.DRAGON_HUNT);
        respawnCredits = Math.max(1, Math.min(10, respawnCredits));
        dragonVictoryMode = Objects.requireNonNullElse(dragonVictoryMode, DragonVictoryMode.LAST_HIT);
        playMode = Objects.requireNonNullElse(playMode, PlayMode.SUR);
        pveDifficulty = Objects.requireNonNullElse(pveDifficulty, PveDifficulty.NORMAL);
    }

    public CampaignRules(MatchMode matchMode, int respawnCredits, DragonVictoryMode dragonVictoryMode,
                         boolean controlPointEnabled, boolean borderEnabled, PlayMode playMode) {
        this(matchMode, respawnCredits, dragonVictoryMode, controlPointEnabled, borderEnabled, playMode, PveDifficulty.NORMAL);
    }

    /** Existing explicit competition callers retain their pre-cooperative semantics. */
    public CampaignRules(MatchMode matchMode, int respawnCredits, DragonVictoryMode dragonVictoryMode,
                         boolean controlPointEnabled, boolean borderEnabled) {
        this(matchMode, respawnCredits, dragonVictoryMode, controlPointEnabled, borderEnabled, PlayMode.PVP);
    }

    public static CampaignRules defaults() {
        return new CampaignRules(MatchMode.DRAGON_HUNT, 3, DragonVictoryMode.LAST_HIT, false, false, PlayMode.SUR);
    }

    public boolean limitedRespawns() { return playMode == PlayMode.RG || matchMode == MatchMode.LIMITED_LIVES; }
}
