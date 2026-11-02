package com.teamlifebind.common;

import java.util.ArrayList;
import java.util.List;

/** Immutable encounter plans. Adapters create ordinary, non-griefing hostile entities for each slot. */
public final class PveWaves {
    public static final int TOTAL_WAVES = 10;
    public static final int MAX_PLAYERS = 16;
    public static final int MAX_ENEMIES = 48;

    public enum Kind { ZOMBIE, SKELETON, SPIDER, HUSK, STRAY, VINDICATOR, RAVAGER }
    public record Spawn(int slot, Kind kind, boolean boss, double healthMultiplier, double damageMultiplier) { }
    public record Wave(int number, boolean boss, List<Spawn> spawns, int rewardTier) {
        public Wave { spawns = List.copyOf(spawns); }
    }
    private record Group(Kind kind, int count) { }

    private PveWaves() { }

    public static Wave wave(int number, int requestedPlayers) {
        return wave(number, requestedPlayers, PveDifficulty.NORMAL);
    }

    public static String nameKey(int number) {
        if (number < 1 || number > TOTAL_WAVES) throw new IllegalArgumentException("PVE wave must be 1..10");
        return "play.pve.stage." + number;
    }

    public static Wave wave(int number, int requestedPlayers, PveDifficulty difficulty) {
        if (number < 1 || number > TOTAL_WAVES) throw new IllegalArgumentException("PVE wave must be 1..10");
        difficulty = java.util.Objects.requireNonNullElse(difficulty, PveDifficulty.NORMAL);
        int players = Math.max(1, Math.min(MAX_PLAYERS, requestedPlayers));
        boolean boss = number == 5 || number == TOTAL_WAVES;
        List<Group> groups = switch (number) {
            case 1 -> List.of(new Group(Kind.ZOMBIE, 4));
            case 2 -> List.of(new Group(Kind.ZOMBIE, 3), new Group(Kind.SPIDER, 2));
            case 3 -> List.of(new Group(Kind.SKELETON, 3), new Group(Kind.SPIDER, 3));
            case 4 -> List.of(new Group(Kind.HUSK, 4), new Group(Kind.SKELETON, 3));
            case 5 -> List.of(new Group(Kind.HUSK, 4), new Group(Kind.VINDICATOR, 2));
            case 6 -> List.of(new Group(Kind.STRAY, 3), new Group(Kind.SPIDER, 4));
            case 7 -> List.of(new Group(Kind.VINDICATOR, 3), new Group(Kind.HUSK, 4));
            case 8 -> List.of(new Group(Kind.STRAY, 4), new Group(Kind.SKELETON, 4), new Group(Kind.VINDICATOR, 2));
            case 9 -> List.of(new Group(Kind.VINDICATOR, 4), new Group(Kind.STRAY, 4), new Group(Kind.SPIDER, 4));
            default -> List.of(new Group(Kind.VINDICATOR, 4), new Group(Kind.STRAY, 4), new Group(Kind.HUSK, 4));
        };
        List<Spawn> spawns = new ArrayList<>();
        if (boss) spawns.add(new Spawn(0, Kind.RAVAGER, true,
            ((number == 5 ? 3.0D : 4.0D) + (players - 1) * 0.2D) * difficulty.healthScale(),
            (number == 5 ? 1.3D : 1.5D) * difficulty.damageScale()));
        double health = (1.0D + (number - 1) * 0.08D + (players - 1) * 0.07D) * difficulty.healthScale();
        double damage = (1.0D + (number - 1) * 0.04D + (players - 1) * 0.02D) * difficulty.damageScale();
        int extra = (players - 1) * (number <= 5 ? 1 : 2);
        // Round-robin admission keeps every advertised enemy group present even at the global cap.
        int rounds = groups.stream().mapToInt(Group::count).max().orElse(0) + extra;
        for (int index = 0; index < rounds && spawns.size() < MAX_ENEMIES; index++) {
            for (Group group : groups) if (index < group.count() + extra && spawns.size() < MAX_ENEMIES) {
                spawns.add(new Spawn(spawns.size(), group.kind(), false, health, damage));
            }
        }
        int rewardTier = boss ? (number == TOTAL_WAVES ? 8 : 5) : Math.min(5, 1 + (number - 1) / 2);
        return new Wave(number, boss, spawns, rewardTier);
    }
}
