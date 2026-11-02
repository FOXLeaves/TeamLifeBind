package com.teamlifebind.common;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One replaceable vote per eligible player; adapters provide the live electorate and consume the result. */
public final class RogueBallot {
    public static final long DURATION_TICKS = 20L * 20L;
    private long token = -1L;
    private long deadline = -1L;
    private long clock;
    private int decision;
    private Set<UUID> electorate = Set.of();
    private final Map<UUID, Integer> votes = new HashMap<>();

    public synchronized void update(long token, Set<UUID> eligible, long tick) {
        if (this.token != token) {
            clear();
            this.token = token;
        }
        clock = Math.max(clock, tick);
        if (decision != 0) return;
        electorate = Set.copyOf(eligible);
        votes.keySet().retainAll(electorate);
        if (votes.isEmpty()) deadline = -1L;
        resolve();
    }

    public synchronized boolean cast(long token, UUID player, int slot, long tick) {
        if (this.token != token || decision != 0 || slot < 1 || slot > 3 || !electorate.contains(player)) return false;
        clock = Math.max(clock, tick);
        resolve();
        if (decision != 0) return false;
        if (deadline < 0L) deadline = clock + DURATION_TICKS;
        votes.put(player, slot);
        resolve();
        return true;
    }

    public synchronized long token() { return token; }
    public synchronized int decision() { return decision; }
    public synchronized int participants() { return electorate.size(); }
    public synchronized int submitted() { return votes.size(); }
    public synchronized int voteOf(UUID player) { return votes.getOrDefault(player, 0); }
    public synchronized int votesFor(int slot) { return (int) votes.values().stream().filter(vote -> vote == slot).count(); }
    /** -1 means nobody has voted, so the countdown has not started. */
    public synchronized long secondsRemaining(long tick) {
        return deadline < 0L ? -1L : Math.max(0L, (deadline - Math.max(clock, tick) + 19L) / 20L);
    }
    public synchronized void clear() {
        token = -1L; deadline = -1L; clock = 0L; decision = 0;
        electorate = Set.of(); votes.clear();
    }

    private void resolve() {
        if (decision != 0 || votes.isEmpty()) return;
        // Equal totals favor the smaller displayed route number, independent of packet or iteration order.
        int winner = 1;
        for (int slot = 2; slot <= 3; slot++) if (votesFor(slot) > votesFor(winner)) winner = slot;
        if (votesFor(winner) > electorate.size() / 2 || votes.size() == electorate.size()
            || deadline >= 0L && clock >= deadline) decision = winner;
    }
}
