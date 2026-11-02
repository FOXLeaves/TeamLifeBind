package com.teamlifebind.common;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Owns encounter authorization only; entities, placement, targeting and rewards belong to adapters. */
public final class PveWaveState {
    public static final long INITIAL_DELAY_TICKS = 200L;
    public static final long INTERMISSION_TICKS = 400L;
    public enum Phase { IDLE, WAITING, SPAWNING, FIGHTING, COMPLETED, FAILED }
    public record WaveStart(long epoch, PveWaves.Wave wave) { }
    public record Reward(int wave, boolean boss, boolean completed, int tier) { }
    public record Summary(PveDifficulty difficulty, int clearedWaves, int defeats, long elapsedTicks) {
        public String elapsedLabel() {
            long seconds = elapsedTicks / 20L;
            return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
        }
    }

    private final Map<Integer, UUID> alive = new HashMap<>();
    private final Map<UUID, Integer> slots = new HashMap<>();
    private final Set<Integer> defeated = new HashSet<>();
    private final Set<UUID> usedEntities = new HashSet<>();
    private final ArrayDeque<Reward> rewards = new ArrayDeque<>();
    private Phase phase = Phase.IDLE;
    private PveWaves.Wave encounter;
    private int players;
    private long epoch;
    private long clock;
    private long nextWaveTick;
    private PveDifficulty difficulty = PveDifficulty.NORMAL;
    private long startedAt;
    private long finishedAt = -1L;
    private int clearedWaves;
    private int totalDefeats;

    public synchronized void start(int participants, long tick) {
        start(participants, tick, PveDifficulty.NORMAL);
    }

    public synchronized void start(int participants, long tick, PveDifficulty difficulty) {
        if (participants < 1 || tick < 0) throw new IllegalArgumentException("PVE needs players and a nonnegative tick");
        clear();
        this.difficulty = java.util.Objects.requireNonNullElse(difficulty, PveDifficulty.NORMAL);
        startedAt = tick;
        players = Math.min(PveWaves.MAX_PLAYERS, participants);
        phase = Phase.WAITING;
        clock = tick;
        nextWaveTick = deadline(tick, INITIAL_DELAY_TICKS);
    }

    /** Delivers a plan once. A spawn failure cannot accidentally turn an empty list into a cleared wave. */
    public synchronized WaveStart beginWave(long tick) {
        return beginWave(tick, null);
    }

    /** A custom encounter still uses the same death, replacement and one-time reward authorization. */
    public synchronized WaveStart beginWave(long tick, PveWaves.Wave planned) {
        if (!advance(tick) || phase != Phase.WAITING || tick < nextWaveTick) return null;
        if (planned != null) {
            if (planned.number() != wave() + 1 || planned.spawns().isEmpty() || planned.spawns().size() > PveWaves.MAX_ENEMIES)
                throw new IllegalArgumentException("Invalid next encounter");
            for (int slot = 0; slot < planned.spawns().size(); slot++)
                if (planned.spawns().get(slot).slot() != slot) throw new IllegalArgumentException("Encounter slots must be contiguous");
        }
        epoch++;
        encounter = planned == null ? PveWaves.wave(wave() + 1, players, difficulty) : planned;
        alive.clear(); slots.clear(); defeated.clear();
        phase = Phase.SPAWNING;
        return new WaveStart(epoch, encounter);
    }

    /** Route selection grants preparation time, even if the original intermission already elapsed. */
    public synchronized boolean postponeNextWave(long tick, long delay) {
        if (phase != Phase.WAITING || delay < 0 || !advance(tick)) return false;
        nextWaveTick = Math.max(nextWaveTick, deadline(tick, delay));
        return true;
    }

    public synchronized boolean registerSpawn(long run, int slot, UUID entity) {
        if (run != epoch || phase != Phase.SPAWNING || encounter == null || entity == null
            || slot < 0 || slot >= encounter.spawns().size() || alive.containsKey(slot)
            || defeated.contains(slot) || !usedEntities.add(entity)) return false;
        alive.put(slot, entity);
        slots.put(entity, slot);
        return true;
    }

    /** The adapter must create every pending slot or fail the run explicitly; it never earns a free clear. */
    public synchronized boolean finishSpawning(long run, long tick) {
        if (run != epoch || !advance(tick) || phase != Phase.SPAWNING) return false;
        if (alive.size() + defeated.size() != encounter.spawns().size()) { abort(); return false; }
        phase = Phase.FIGHTING;
        if (remaining() == 0) completeWave(tick);
        return true;
    }

    /** Call only for actual deaths. Unknown IDs, repeated events and callbacks from old runs do nothing. */
    public synchronized boolean defeated(long run, UUID entity, long tick) {
        if (run != epoch || !advance(tick) || (phase != Phase.FIGHTING && phase != Phase.SPAWNING)) return false;
        Integer slot = slots.remove(entity);
        if (slot == null) return false;
        alive.remove(slot);
        defeated.add(slot);
        totalDefeats++;
        if (phase == Phase.FIGHTING && remaining() == 0) completeWave(tick);
        return true;
    }

    /** Unloads, escapes or despawns require a replacement of this slot, never kill credit. */
    public synchronized boolean missing(long run, UUID entity) {
        if (run != epoch || (phase != Phase.FIGHTING && phase != Phase.SPAWNING)) return false;
        Integer slot = slots.remove(entity);
        if (slot == null) return false;
        alive.remove(slot);
        phase = Phase.SPAWNING;
        return true;
    }

    public synchronized List<PveWaves.Spawn> pendingSpawns() {
        if (phase != Phase.SPAWNING || encounter == null) return List.of();
        return encounter.spawns().stream().filter(spawn -> !alive.containsKey(spawn.slot()) && !defeated.contains(spawn.slot())).toList();
    }

    public synchronized Reward claimReward() { return rewards.pollFirst(); }
    public synchronized Phase phase() { return phase; }
    public synchronized long epoch() { return epoch; }
    public synchronized int wave() { return encounter == null ? 0 : encounter.number(); }
    public synchronized int remaining() { return encounter == null ? 0 : encounter.spawns().size() - defeated.size(); }
    public synchronized long nextWaveTick() { return nextWaveTick; }
    public synchronized boolean completed() { return phase == Phase.COMPLETED; }
    public synchronized boolean failed() { return phase == Phase.FAILED; }
    public synchronized PveDifficulty difficulty() { return difficulty; }
    public synchronized Summary summary(long tick) {
        long elapsed = phase == Phase.IDLE ? 0L : Math.max(0L, (finishedAt >= 0 ? finishedAt : Math.max(clock, tick)) - startedAt);
        return new Summary(difficulty, clearedWaves, totalDefeats, elapsed);
    }

    public synchronized void abort() {
        abort(clock);
    }

    public synchronized void abort(long tick) {
        if (phase == Phase.IDLE || phase == Phase.COMPLETED || phase == Phase.FAILED) return;
        finishedAt = Math.max(clock, tick);
        phase = Phase.FAILED;
        epoch++;
        alive.clear(); slots.clear(); defeated.clear(); usedEntities.clear(); rewards.clear();
        nextWaveTick = 0L;
    }

    public synchronized void clear() {
        phase = Phase.IDLE;
        epoch++;
        encounter = null;
        players = 0;
        clock = 0L;
        nextWaveTick = 0L;
        difficulty = PveDifficulty.NORMAL;
        startedAt = 0L; finishedAt = -1L; clearedWaves = 0; totalDefeats = 0;
        alive.clear(); slots.clear(); defeated.clear(); usedEntities.clear(); rewards.clear();
    }

    private void completeWave(long tick) {
        boolean finished = encounter.number() == PveWaves.TOTAL_WAVES;
        clearedWaves = encounter.number();
        if (finished) finishedAt = tick;
        rewards.addLast(new Reward(encounter.number(), encounter.boss(), finished, encounter.rewardTier()));
        phase = finished ? Phase.COMPLETED : Phase.WAITING;
        nextWaveTick = finished ? 0L : deadline(tick, INTERMISSION_TICKS);
    }

    private boolean advance(long tick) {
        if (tick < 0 || tick < clock) return false;
        clock = tick;
        return true;
    }

    private static long deadline(long tick, long delay) { return tick > Long.MAX_VALUE - delay ? Long.MAX_VALUE : tick + delay; }
}
