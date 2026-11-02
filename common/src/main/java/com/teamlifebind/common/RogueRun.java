package com.teamlifebind.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** Seeded party routes and run-only relics. Platform adapters own menus, entities and attribute cleanup. */
public final class RogueRun {
    public enum Relic {
        BLADE(3), ARMOR(3), STRIDE(3), FURY(3), POISE(3), MANA_SPRING(3), PROVISIONER(2), SCHOLAR(2);
        private final int maxStacks;
        Relic(int maxStacks) { this.maxStacks = maxStacks; }
        public int maxStacks() { return maxStacks; }
        public String key() { return "rg.relic." + name().toLowerCase(Locale.ROOT); }
    }
    public enum Theme {
        PLAGUE(PveWaves.Kind.ZOMBIE, PveWaves.Kind.HUSK, PveWaves.Kind.SPIDER),
        HUNT(PveWaves.Kind.SKELETON, PveWaves.Kind.ZOMBIE, PveWaves.Kind.SPIDER),
        RAID(PveWaves.Kind.HUSK, PveWaves.Kind.VINDICATOR, PveWaves.Kind.SKELETON),
        NEST(PveWaves.Kind.SPIDER, PveWaves.Kind.SPIDER, PveWaves.Kind.ZOMBIE),
        FROST(PveWaves.Kind.STRAY, PveWaves.Kind.ZOMBIE, PveWaves.Kind.SPIDER),
        RUINS(PveWaves.Kind.HUSK, PveWaves.Kind.SKELETON, PveWaves.Kind.VINDICATOR);
        private final List<PveWaves.Kind> enemies;
        Theme(PveWaves.Kind... enemies) { this.enemies = List.of(enemies); }
        public String key() { return "rg.theme." + name().toLowerCase(Locale.ROOT); }
    }
    public enum Threat {
        SWARM(1.25D, .8D, 1D, 1D), ELITE(.75D, 1.5D, 1.1D, 1D), SWIFT(1D, 1D, 1D, 1.18D);
        private final double count, health, damage, speed;
        Threat(double count, double health, double damage, double speed) {
            this.count = count; this.health = health; this.damage = damage; this.speed = speed;
        }
        public String key() { return "rg.threat." + name().toLowerCase(Locale.ROOT); }
        public double speed() { return speed; }
    }
    public record Route(int slot, long token, Theme theme, Threat threat, Relic relic) { }

    private final EnumMap<Relic, Integer> relics = new EnumMap<>(Relic.class);
    private long sequence;
    private long epoch;
    private long seed;
    private int floor;
    private int participants;
    private boolean active;
    private boolean completed;
    private PveDifficulty difficulty = PveDifficulty.NORMAL;
    private List<Route> offers = List.of();
    private Route selected;

    public synchronized void start(long seed, int participants, PveDifficulty difficulty) {
        if (participants < 1) throw new IllegalArgumentException("RG needs participants");
        clear();
        epoch = sequence;
        this.seed = seed;
        this.participants = Math.min(PveWaves.MAX_PLAYERS, participants);
        this.difficulty = java.util.Objects.requireNonNullElse(difficulty, PveDifficulty.NORMAL);
        active = true;
        prepare(1);
    }

    public synchronized boolean active() { return active; }
    public synchronized boolean completed() { return completed; }
    public synchronized boolean waitingForChoice() { return active && !offers.isEmpty(); }
    public synchronized int floor() { return floor; }
    public synchronized long seed() { return seed; }
    public synchronized long epoch() { return epoch; }
    public synchronized List<Route> offers() { return offers; }
    public synchronized Route selected() { return selected; }
    public synchronized int level(Relic relic) { return relics.getOrDefault(relic, 0); }
    public synchronized Map<Relic, Integer> relics() { return Map.copyOf(relics); }
    public synchronized int totalRelics() { return relics.values().stream().mapToInt(Integer::intValue).sum(); }

    /** Consume a resolved party selection once; stale menus and repeated packets cannot grant another relic. */
    public synchronized Route choose(long token, int slot) {
        if (!waitingForChoice() || slot < 1 || slot > offers.size()) return null;
        Route route = offers.get(slot - 1);
        if (route.token() != token || level(route.relic()) >= route.relic().maxStacks()) return null;
        relics.merge(route.relic(), 1, Integer::sum);
        selected = route;
        offers = List.of();
        return route;
    }

    public synchronized boolean advanceAfterWave(int clearedFloor) {
        if (!active || completed || selected == null || clearedFloor != floor) return false;
        if (floor == PveWaves.TOTAL_WAVES) completed = true;
        else prepare(floor + 1);
        return true;
    }

    public synchronized PveWaves.Wave plan() {
        if (!active || completed || selected == null) throw new IllegalStateException("Choose a route before spawning");
        PveWaves.Wave base = PveWaves.wave(floor, participants, difficulty);
        Threat threat = selected.threat();
        int bossCount = base.boss() ? 1 : 0;
        int normalCount = Math.max(3, (int) Math.ceil((base.spawns().size() - bossCount) * threat.count));
        normalCount = Math.min(PveWaves.MAX_ENEMIES - bossCount, normalCount);
        List<PveWaves.Spawn> enemies = new ArrayList<>();
        if (base.boss()) {
            PveWaves.Spawn boss = base.spawns().getFirst();
            enemies.add(new PveWaves.Spawn(0, boss.kind(), true, boss.healthMultiplier() * threat.health, boss.damageMultiplier() * threat.damage));
        }
        PveWaves.Spawn normal = base.spawns().get(bossCount);
        for (int index = 0; index < normalCount; index++) {
            PveWaves.Kind kind = selected.theme().enemies.get(index % selected.theme().enemies.size());
            enemies.add(new PveWaves.Spawn(enemies.size(), kind, false, normal.healthMultiplier() * threat.health, normal.damageMultiplier() * threat.damage));
        }
        return new PveWaves.Wave(floor, base.boss(), enemies, base.rewardTier());
    }

    public synchronized int rewardTier(PveWaveState.Reward reward) {
        return Math.min(reward.completed() ? 8 : 7, reward.tier() + level(Relic.SCHOLAR));
    }

    public synchronized void clear() {
        sequence++;
        active = false; completed = false; epoch = 0; seed = 0; floor = 0; participants = 0;
        difficulty = PveDifficulty.NORMAL; offers = List.of(); selected = null; relics.clear();
    }

    private void prepare(int floor) {
        this.floor = floor;
        selected = null;
        long token = ++sequence;
        Random random = new Random(seed ^ (0x9E3779B97F4A7C15L * floor));
        List<Theme> themes = new ArrayList<>(Arrays.asList(Theme.values()));
        List<Threat> threats = new ArrayList<>(Arrays.asList(Threat.values()));
        List<Relic> available = new ArrayList<>(Arrays.stream(Relic.values()).filter(relic -> level(relic) < relic.maxStacks()).toList());
        java.util.Collections.shuffle(themes, random);
        java.util.Collections.shuffle(threats, random);
        java.util.Collections.shuffle(available, random);
        List<Route> generated = new ArrayList<>();
        for (int index = 0; index < 3; index++) generated.add(new Route(index + 1, token, themes.get(index), threats.get(index), available.get(index)));
        offers = List.copyOf(generated);
    }
}
