package com.teamlifebind.common;

import java.util.Locale;

/** Top-level activity; existing MatchMode variants remain independent round modifiers. */
public enum PlayMode {
    SUR,
    PVE,
    RG,
    PVP;

    public static PlayMode parse(String value) {
        if (value == null) return SUR;
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "pve", "dungeon" -> PVE;
            case "rg", "rogue", "roguelike" -> RG;
            case "pvp", "competitive" -> PVP;
            case "sur", "survival", "cooperative" -> SUR;
            default -> SUR;
        };
    }

    public PlayMode next() { return values()[(ordinal() + 1) % values().length]; }
    public boolean cooperative() { return this != PVP; }
    public boolean encounter() { return this == PVE || this == RG; }
    public int minPlayers() { return cooperative() ? 1 : 2; }
}
