package com.teamlifebind.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative, bounded knowledge/mana/cooldown state. World eligibility and collisions
 * belong to platform adapters. A rejected learn never consumes a book. Cancelling a paid cast
 * never refunds mana or cooldown, preventing cancel/restart exploits.
 */
public final class MagicState {
    public static final int MAX_PLAYERS = 128;
    public static final int MAX_LEARNED = 3;
    public static final int INITIAL_MANA = 100;
    public static final int MAX_MANA = 300;
    public static final int APPLE_INCREMENT = 10;
    public static final int REGEN_INTERVAL_TICKS = 20;
    public static final int REGEN_DELAY_TICKS = 60;

    public enum Reason {
        LEARNED, ALREADY_KNOWN, MAX_LEARNED, UNKNOWN_SPELL, LIMIT_REACHED,
        CAST_STARTED, CAST_READY, NOT_LEARNED, NO_MANA, COOLDOWN, ALREADY_CASTING,
        DUPLICATE_TICK, CLOCK_REWIND, INVALID_PLAYER, INELIGIBLE, NOT_READY, CANCELLED
    }

    public record LearnResult(Reason reason, SpellDefinition spell) {
        public boolean accepted() { return reason == Reason.LEARNED; }
    }
    public record CastToken(UUID playerId, String spellId, long sequence, long startedAt, long readyAt) { }
    public record CastResult(Reason reason, SpellDefinition spell, CastToken token) {
        public boolean accepted() { return reason == Reason.CAST_STARTED || reason == Reason.CAST_READY; }
    }
    public record Snapshot(List<String> learned, int selectedSlot, String selectedSpell, int mana,
                           int maxMana, long cooldownRemainingTicks, long castRemainingTicks,
                           String castingSpell) {
        public Snapshot { learned = List.copyOf(learned); }
    }
    /** Stable save contract: pending casts are cancelled, while paid mana and cooldowns survive. */
    public record SavedPlayer(List<String> learned, int selectedSlot, int mana, int maxMana,
                              Map<String, Long> cooldownRemaining) {
        public SavedPlayer {
            learned = List.copyOf(learned);
            cooldownRemaining = Map.copyOf(cooldownRemaining);
        }
    }

    private final Map<UUID, PlayerState> players = new HashMap<>();
    private long nextSequence = 1L;

    public synchronized LearnResult learn(UUID playerId, String spellId) {
        SpellDefinition spell = SpellCatalogue.byId(spellId);
        if (playerId == null) return new LearnResult(Reason.INVALID_PLAYER, spell);
        if (spell == null) return new LearnResult(Reason.UNKNOWN_SPELL, null);
        PlayerState player = getOrCreate(playerId);
        if (player == null) return new LearnResult(Reason.LIMIT_REACHED, spell);
        if (player.learned.contains(spell.id())) return new LearnResult(Reason.ALREADY_KNOWN, spell);
        if (player.learned.size() >= MAX_LEARNED) return new LearnResult(Reason.MAX_LEARNED, spell);
        player.learned.add(spell.id());
        return new LearnResult(Reason.LEARNED, spell);
    }

    /** The adapter requires administrator authorization or consumes a forgetting companion item. */
    public synchronized boolean forget(UUID playerId, int slot) {
        PlayerState player = players.get(playerId);
        if (player == null || slot < 0 || slot >= player.learned.size()) return false;
        String removed = player.learned.remove(slot);
        if (player.pending != null && player.pending.spellId().equals(removed)) player.pending = null;
        if (player.selectedSlot > slot) player.selectedSlot--;
        player.selectedSlot = Math.max(0, Math.min(player.selectedSlot, player.learned.size() - 1));
        // Keep this spell's cooldown until reset: forgetting/relearning cannot bypass it.
        return true;
    }

    public synchronized boolean selectSlot(UUID playerId, int slot) {
        PlayerState player = players.get(playerId);
        if (player == null || slot < 0 || slot >= player.learned.size()) return false;
        player.selectedSlot = slot;
        return true;
    }

    public synchronized String selected(UUID playerId) {
        PlayerState player = players.get(playerId);
        return player == null || player.learned.isEmpty() ? null : player.learned.get(player.selectedSlot);
    }
    public synchronized List<String> learned(UUID playerId) {
        PlayerState player = players.get(playerId);
        return player == null ? List.of() : List.copyOf(player.learned);
    }
    public synchronized int mana(UUID playerId, long tick) { return snapshot(playerId, tick).mana(); }
    public synchronized int maxMana(UUID playerId) {
        PlayerState player = players.get(playerId);
        return player == null ? INITIAL_MANA : player.maxMana;
    }

    /** Immediate authorization for adapters which execute the complete cast themselves. */
    public synchronized CastResult tryCast(UUID playerId, String spellId, long tick) {
        return authorize(playerId, spellId, tick, false, true);
    }
    public synchronized CastResult tryCast(UUID playerId, String spellId, long tick, boolean eligible) {
        return authorize(playerId, spellId, tick, false, eligible);
    }
    /** Debits once and returns an unforgeable-within-this-state sequence token for a delayed cast. */
    public synchronized CastResult beginCast(UUID playerId, String spellId, long tick) {
        return authorize(playerId, spellId, tick, true, true);
    }
    public synchronized CastResult beginCast(UUID playerId, String spellId, long tick, boolean eligible) {
        return authorize(playerId, spellId, tick, true, eligible);
    }

    public synchronized CastResult completeCast(UUID playerId, CastToken token, long tick) {
        PlayerState player = players.get(playerId);
        if (playerId == null || player == null || token == null || !playerId.equals(token.playerId())
            || player.pending == null || !player.pending.equals(token)) return result(Reason.CANCELLED, null, null);
        SpellDefinition spell = SpellCatalogue.byId(token.spellId());
        if (tick < 0 || tick < player.lastClock) return result(Reason.CLOCK_REWIND, spell, token);
        advance(player, tick);
        if (tick < token.readyAt()) return result(Reason.NOT_READY, spell, token);
        player.pending = null;
        return result(Reason.CAST_READY, spell, token);
    }

    public synchronized CastResult completeCast(UUID playerId, long tick) {
        PlayerState player = players.get(playerId);
        return completeCast(playerId, player == null ? null : player.pending, tick);
    }

    public synchronized void cancel(UUID playerId) {
        PlayerState player = players.get(playerId);
        if (player != null) player.pending = null;
    }

    public synchronized Snapshot tick(UUID playerId, long tick) { return snapshot(playerId, tick); }
    public synchronized Snapshot snapshot(UUID playerId, long tick) {
        PlayerState player = players.get(playerId);
        if (player == null) return new Snapshot(List.of(), 0, null, INITIAL_MANA, INITIAL_MANA, 0, 0, null);
        if (tick >= 0 && tick >= player.lastClock) advance(player, tick);
        long safeTick = Math.max(0L, Math.max(tick, player.lastClock));
        String selected = player.learned.isEmpty() ? null : player.learned.get(player.selectedSlot);
        long cooldown = selected == null ? 0 : remaining(player.cooldowns.getOrDefault(selected, 0L), safeTick);
        return new Snapshot(player.learned, player.selectedSlot, selected, player.mana, player.maxMana,
            cooldown, player.pending == null ? 0 : remaining(player.pending.readyAt(), safeTick),
            player.pending == null ? null : player.pending.spellId());
    }

    public synchronized long cooldownRemaining(UUID playerId, String spellId, long tick) {
        PlayerState player = players.get(playerId);
        SpellDefinition spell = SpellCatalogue.byId(spellId);
        if (player == null || spell == null) return 0L;
        long safeTick = Math.max(0L, Math.max(tick, player.lastClock));
        return remaining(player.cooldowns.getOrDefault(spell.id(), 0L), safeTick);
    }

    /** Returns actual restored mana; adapters consume a potion only when this is positive. */
    public synchronized int restoreMana(UUID playerId, int amount, long tick) {
        if (playerId == null || amount <= 0 || tick < 0) return 0;
        PlayerState player = getOrCreate(playerId);
        if (player == null || tick < player.lastClock) return 0;
        advance(player, tick);
        int restored = (int) Math.min((long) amount, (long) player.maxMana - player.mana);
        player.mana += restored;
        return restored;
    }

    /** Secondary channel costs are atomic; an insufficient balance pays nothing. */
    public synchronized boolean spendMana(UUID playerId, int amount, long tick) {
        PlayerState player = players.get(playerId);
        if (playerId == null || player == null || amount <= 0 || tick < 0 || tick < player.lastClock) return false;
        advance(player, tick);
        if (player.mana < amount) return false;
        player.mana -= amount;
        delayRegeneration(player, tick);
        return true;
    }

    /** Enemy mana drain returns the amount actually removed; it never creates a negative balance. */
    public synchronized int drainMana(UUID playerId, int amount, long tick) {
        if (playerId == null || amount <= 0 || tick < 0) return 0;
        PlayerState player = getOrCreate(playerId);
        if (player == null || tick < player.lastClock) return 0;
        advance(player, tick);
        int drained = Math.min(amount, player.mana);
        if (drained == 0) return 0;
        player.mana -= drained;
        delayRegeneration(player, tick);
        return drained;
    }

    /** Temporal acceleration reduces existing cooldowns, but never bypasses the per-tick cast guard. */
    public synchronized void accelerateCooldowns(UUID playerId, int ticks, long tick) {
        PlayerState player = players.get(playerId);
        if (player == null || ticks <= 0 || tick < 0 || tick < player.lastClock) return;
        advance(player, tick);
        player.cooldowns.replaceAll((spell, deadline) -> Math.max(tick, deadline - ticks));
    }

    /** Returns false at the cap, so the adapter keeps the apple. It grants ten maximum/current mana. */
    public synchronized boolean increaseMaxMana(UUID playerId, long tick) {
        if (playerId == null || tick < 0) return false;
        PlayerState player = getOrCreate(playerId);
        if (player == null || tick < player.lastClock || player.maxMana >= MAX_MANA) return false;
        advance(player, tick);
        int gained = Math.min(APPLE_INCREMENT, MAX_MANA - player.maxMana);
        player.maxMana += gained;
        player.mana = Math.min(player.maxMana, player.mana + gained);
        return true;
    }

    /** Logout releases bounded state; death/world changes should cancel, rather than erase learning. */
    public synchronized void remove(UUID playerId) { players.remove(playerId); }
    public synchronized void resetAll() { players.clear(); }
    public synchronized void clearSession() { resetAll(); }
    public synchronized int playerCount() { return players.size(); }

    /** Export before logout/remove. Adapters can subtract offline wall time from remaining cooldowns. */
    public synchronized SavedPlayer exportPlayer(UUID playerId, long tick) {
        PlayerState player = players.get(playerId);
        if (player == null) return new SavedPlayer(List.of(), 0, INITIAL_MANA, INITIAL_MANA, Map.of());
        Snapshot snapshot = snapshot(playerId, tick);
        long safeTick = Math.max(0L, Math.max(tick, player.lastClock));
        Map<String, Long> cooldowns = new HashMap<>();
        player.cooldowns.forEach((id, deadline) -> {
            long left = remaining(deadline, safeTick);
            if (left > 0) cooldowns.put(id, left);
        });
        return new SavedPlayer(snapshot.learned(), snapshot.selectedSlot(), snapshot.mana(), snapshot.maxMana(), cooldowns);
    }

    /**
     * Restores an unloaded UUID atomically. Invalid data, a loaded UUID or the player capacity
     * returns false without modifying state. Never overwrite a live caster from saved data.
     */
    public synchronized boolean restorePlayer(UUID playerId, SavedPlayer saved, long tick) {
        if (playerId == null || saved == null || tick < 0 || players.containsKey(playerId)
            || players.size() >= MAX_PLAYERS || saved.learned().size() > MAX_LEARNED
            || saved.maxMana() < INITIAL_MANA || saved.maxMana() > MAX_MANA
            || (saved.maxMana() - INITIAL_MANA) % APPLE_INCREMENT != 0
            || saved.mana() < 0 || saved.mana() > saved.maxMana()
            || saved.selectedSlot() < 0
            || (saved.learned().isEmpty() ? saved.selectedSlot() != 0 : saved.selectedSlot() >= saved.learned().size())
            || saved.cooldownRemaining().size() > SpellCatalogue.TOTAL_COUNT) return false;
        if (saved.learned().stream().distinct().count() != saved.learned().size()) return false;
        for (String id : saved.learned()) {
            SpellDefinition spell = SpellCatalogue.byId(id);
            if (spell == null || !spell.id().equals(id)) return false;
        }
        for (Map.Entry<String, Long> entry : saved.cooldownRemaining().entrySet()) {
            SpellDefinition spell = SpellCatalogue.byId(entry.getKey());
            if (spell == null || !spell.id().equals(entry.getKey()) || entry.getValue() < 0
                || entry.getValue() > spell.cooldownTicks()) return false;
        }
        PlayerState player = new PlayerState();
        player.learned.addAll(saved.learned());
        player.selectedSlot = saved.selectedSlot();
        player.mana = saved.mana();
        player.maxMana = saved.maxMana();
        player.lastClock = tick;
        player.regenAnchor = tick;
        player.regenAllowed = deadline(tick, REGEN_DELAY_TICKS);
        player.regenAnchor = player.regenAllowed;
        saved.cooldownRemaining().forEach((id, left) -> {
            if (left > 0) player.cooldowns.put(id, deadline(tick, left.intValue()));
        });
        players.put(playerId, player);
        return true;
    }

    private CastResult authorize(UUID playerId, String spellId, long tick, boolean channel, boolean eligible) {
        SpellDefinition spell = SpellCatalogue.byId(spellId);
        if (playerId == null || tick < 0) return result(Reason.INVALID_PLAYER, spell, null);
        if (spell == null) return result(Reason.UNKNOWN_SPELL, null, null);
        PlayerState player = players.get(playerId);
        if (!eligible) {
            if (player != null) player.pending = null;
            return result(Reason.INELIGIBLE, spell, null);
        }
        if (player == null || !player.learned.contains(spell.id())) return result(Reason.NOT_LEARNED, spell, null);
        if (tick < player.lastClock) return result(Reason.CLOCK_REWIND, spell, null);
        advance(player, tick);
        if (player.pending != null) return result(Reason.ALREADY_CASTING, spell, player.pending);
        if (tick == player.lastCastTick) return result(Reason.DUPLICATE_TICK, spell, null);
        if (tick < player.cooldowns.getOrDefault(spell.id(), 0L)) return result(Reason.COOLDOWN, spell, null);
        if (player.mana < spell.manaCost()) return result(Reason.NO_MANA, spell, null);
        if (nextSequence == Long.MAX_VALUE) return result(Reason.LIMIT_REACHED, spell, null);
        player.mana -= spell.manaCost();
        player.lastCastTick = tick;
        delayRegeneration(player, tick);
        player.cooldowns.put(spell.id(), deadline(tick, spell.cooldownTicks()));
        if (!channel) return result(Reason.CAST_READY, spell, null);
        CastToken token = new CastToken(playerId, spell.id(), nextSequence++, tick, deadline(tick, spell.castTicks()));
        player.pending = token;
        return result(Reason.CAST_STARTED, spell, token);
    }

    private PlayerState getOrCreate(UUID playerId) {
        PlayerState player = players.get(playerId);
        if (player == null && players.size() < MAX_PLAYERS) {
            player = new PlayerState();
            players.put(playerId, player);
        }
        return player;
    }

    private static void advance(PlayerState player, long tick) {
        if (tick < player.lastClock) return;
        if (player.lastClock == -1) {
            player.lastClock = tick;
            player.regenAnchor = tick;
            return;
        }
        player.lastClock = tick;
        if (tick < player.regenAllowed || tick < player.regenAnchor) return;
        long available = (tick - player.regenAnchor) / REGEN_INTERVAL_TICKS;
        if (available <= 0) return;
        int recovered = (int) Math.min(available, (long) player.maxMana - player.mana);
        player.mana += recovered;
        // Advance over every elapsed whole interval even at cap; no banked regeneration.
        player.regenAnchor += available * REGEN_INTERVAL_TICKS;
    }

    private static CastResult result(Reason reason, SpellDefinition spell, CastToken token) {
        return new CastResult(reason, spell, token);
    }
    private static void delayRegeneration(PlayerState player, long tick) {
        player.regenAllowed = deadline(tick, REGEN_DELAY_TICKS);
        player.regenAnchor = player.regenAllowed;
    }
    private static long deadline(long tick, int duration) {
        return tick > Long.MAX_VALUE - duration ? Long.MAX_VALUE : tick + duration;
    }
    private static long remaining(long deadline, long tick) { return deadline <= tick ? 0 : deadline - tick; }

    private static final class PlayerState {
        final List<String> learned = new ArrayList<>(MAX_LEARNED);
        final Map<String, Long> cooldowns = new HashMap<>();
        int selectedSlot;
        int mana = INITIAL_MANA;
        int maxMana = INITIAL_MANA;
        long lastClock = -1L;
        long lastCastTick = -1L;
        long regenAllowed;
        long regenAnchor;
        CastToken pending;
    }
}
