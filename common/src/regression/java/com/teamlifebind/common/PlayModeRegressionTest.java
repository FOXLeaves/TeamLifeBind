package com.teamlifebind.common;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Cooperative rounds and the encounter lifecycle, exercised without substituting platform behavior. */
final class PlayModeRegressionTest {
    private static int assertions;
    private static long entitySequence;
    private static final UUID PLAYER = new UUID(50L, 1L);
    private static final UUID PARTNER = new UUID(50L, 2L);

    static int run() {
        assertions = 0;
        parsingAndCompatibility();
        survivalJoining();
        cooperativeRoundOutcomes();
        encounterPlans();
        spawnAndMissingLifecycle();
        allTenWaves();
        failureAndEpochs();
        difficultyAndSummary();
        System.out.println("Survival, cooperative PVE and PVP compatibility scenarios passed (" + assertions + " assertions).");
        return assertions;
    }

    private static void parsingAndCompatibility() {
        for (String value : new String[] {null, "", "bad", " sur ", "survival", "cooperative"}) eq(PlayMode.SUR, PlayMode.parse(value));
        eq(PlayMode.PVE, PlayMode.parse(" PvE "));
        eq(PlayMode.PVE, PlayMode.parse("dungeon"));
        eq(PlayMode.PVP, PlayMode.parse(" PVP "));
        eq(PlayMode.PVP, PlayMode.parse("competitive"));
        Locale original = Locale.getDefault();
        try { Locale.setDefault(Locale.forLanguageTag("tr-TR")); eq(PlayMode.PVP, PlayMode.parse("COMPETITIVE")); }
        finally { Locale.setDefault(original); }
        eq(PlayMode.PVE, PlayMode.SUR.next());
        eq(PlayMode.RG, PlayMode.PVE.next());
        eq(PlayMode.PVP, PlayMode.RG.next());
        eq(PlayMode.SUR, PlayMode.PVP.next());
        eq(1, PlayMode.SUR.minPlayers());
        eq(1, PlayMode.PVE.minPlayers());
        eq(2, PlayMode.PVP.minPlayers());
        yes(PlayMode.SUR.cooperative() && PlayMode.PVE.cooperative() && !PlayMode.PVP.cooperative());
        eq(PlayMode.SUR, CampaignRules.defaults().playMode());
        eq(PlayMode.SUR, new CampaignRules(null, 3, null, false, false, null).playMode());
        eq(PlayMode.PVP, new CampaignRules(MatchMode.LIMITED_LIVES, 3, null, false, false).playMode());
        TeamLifeBindEngine engine = new TeamLifeBindEngine();
        StartResult started = engine.start(List.of(PLAYER), new GameOptions(1, HealthPreset.ONE_ROW));
        eq(Map.of(PLAYER, 1), started.teamByPlayer());
        yes(engine.isRunning() && engine.winnerTeam() == null);
        eq(List.of(PLAYER), engine.onPlayerDeath(PLAYER).victims());
        yes(engine.isRunning() && engine.winnerTeam() == null);
    }

    private static void cooperativeRoundOutcomes() {
        for (PlayMode mode : List.of(PlayMode.SUR, PlayMode.PVE, PlayMode.RG)) {
            RoundCampaignState state = new RoundCampaignState();
            state.start(Map.of(PLAYER, 1, PARTNER, 2), new CampaignRules(MatchMode.LIMITED_LIVES, 1,
                DragonVictoryMode.CONTRIBUTION, true, true, mode), 0L);
            UUID dragon = new UUID(50L, 3L);
            state.recordDragonDamage(1, dragon, 20.0D, 200.0D, 1L);
            eq(null, state.chooseDragonWinner(dragon, 1, 1L));
            yes(state.applyWipe(1, false, 2L).applied());
            eq(0, state.remainingRespawnCredits(1));
            yes(!state.isEliminated(1));
            yes(state.applyWipe(1, false, 3L).eliminated());
            eq(null, state.pollVictory(4L));
            yes(state.applyWipe(2, true, 5L).eliminated());
            eq(null, state.pollVictory(5L));
            eq(new RoundCampaignState.Victory(null, true), state.pollVictory(6L));
            eq(new RoundCampaignState.Victory(null, true), state.pollVictory(100L));
        }
        RoundCampaignState survival = new RoundCampaignState();
        survival.start(Map.of(PLAYER, 1), CampaignRules.defaults(), 0L);
        for (int tick = 1; tick <= 8; tick++) {
            yes(!survival.applyWipe(1, false, tick).eliminated());
            eq(null, survival.pollVictory(tick + 1L));
        }
        survival.applyWipe(1, true, 20L);
        eq(new RoundCampaignState.Victory(null, true), survival.pollVictory(21L));
    }

    private static void survivalJoining() {
        TeamLifeBindEngine engine = new TeamLifeBindEngine();
        yes(!engine.addCooperativePlayer(PLAYER));
        engine.start(List.of(PLAYER), new GameOptions(1, HealthPreset.ONE_ROW));
        Map<UUID, Integer> oldRoster = engine.assignments();
        yes(engine.addCooperativePlayer(PARTNER));
        eq(Map.of(PLAYER, 1), oldRoster);
        eq(Map.of(PLAYER, 1, PARTNER, 1), engine.assignments());
        eq(List.of(PLAYER, PARTNER), engine.onPlayerDeath(PARTNER).victims());
        yes(!engine.addCooperativePlayer(PARTNER));
        yes(!engine.addCooperativePlayer(null));
        engine.stop();
        yes(!engine.addCooperativePlayer(PARTNER));
        engine.start(List.of(PLAYER, PARTNER), new GameOptions(2, HealthPreset.ONE_ROW));
        yes(!engine.addCooperativePlayer(new UUID(50L, 4L)));

        RoundCampaignState survival = new RoundCampaignState();
        yes(!survival.addCooperativePlayer(PLAYER, 0L));
        survival.start(Map.of(PLAYER, 1), CampaignRules.defaults(), 10L);
        yes(survival.completeTask(1, RoundCampaignState.StageTask.CLAIM_SUPPLY));
        yes(survival.tryActivateSilence(PLAYER, 11L));
        long before = survival.silenceCooldownRemainingTicks(1, 12L);
        yes(!survival.addCooperativePlayer(PARTNER, 9L));
        yes(survival.addCooperativePlayer(PARTNER, 12L));
        eq(1, survival.teamForPlayer(PARTNER));
        eq(Set.of(RoundCampaignState.StageTask.CLAIM_SUPPLY), survival.completedTasks(1));
        eq(before, survival.silenceCooldownRemainingTicks(1, 12L));
        yes(!survival.tryActivateSilence(PARTNER, 12L));
        yes(!survival.addCooperativePlayer(PARTNER, 12L));
        yes(!survival.addCooperativePlayer(null, 12L));
        survival.applyWipe(1, true, 13L);
        yes(!survival.addCooperativePlayer(new UUID(50L, 5L), 14L));
        for (PlayMode mode : List.of(PlayMode.PVP, PlayMode.PVE)) {
            survival.start(Map.of(PLAYER, 1), new CampaignRules(MatchMode.DRAGON_HUNT, 3,
                DragonVictoryMode.LAST_HIT, false, false, mode), 0L);
            yes(!survival.addCooperativePlayer(PARTNER, 1L));
        }
    }

    private static void encounterPlans() {
        Set<PveWaves.Kind> kinds = new HashSet<>();
        for (int number = 1; number <= PveWaves.TOTAL_WAVES; number++) {
            PveWaves.Wave solo = PveWaves.wave(number, 1);
            PveWaves.Wave pair = PveWaves.wave(number, 2);
            PveWaves.Wave capped = PveWaves.wave(number, Integer.MAX_VALUE);
            eq(PveWaves.wave(number, PveWaves.MAX_PLAYERS), capped);
            eq(solo, PveWaves.wave(number, Integer.MIN_VALUE));
            yes(pair.spawns().size() >= solo.spawns().size());
            yes(capped.spawns().size() <= 48 && capped.spawns().size() >= pair.spawns().size());
            for (PveWaves.Wave wave : List.of(solo, pair, capped)) {
                eq(number, wave.number());
                eq(number == 5 || number == 10, wave.boss());
                eq(wave.boss() ? 1L : 0L, wave.spawns().stream().filter(PveWaves.Spawn::boss).count());
                yes(wave.rewardTier() >= 1 && wave.rewardTier() <= 8);
                for (int index = 0; index < wave.spawns().size(); index++) {
                    PveWaves.Spawn spawn = wave.spawns().get(index);
                    eq(index, spawn.slot());
                    yes(Double.isFinite(spawn.healthMultiplier()) && spawn.healthMultiplier() >= 1.0D);
                    yes(Double.isFinite(spawn.damageMultiplier()) && spawn.damageMultiplier() >= 1.0D);
                    eq(spawn.boss(), spawn.kind() == PveWaves.Kind.RAVAGER);
                    kinds.add(spawn.kind());
                }
            }
            throwsType(UnsupportedOperationException.class, () -> solo.spawns().clear());
        }
        eq(Set.of(PveWaves.Kind.values()), kinds);
        throwsType(IllegalArgumentException.class, () -> PveWaves.wave(0, 1));
        throwsType(IllegalArgumentException.class, () -> PveWaves.wave(11, 1));
    }

    private static void spawnAndMissingLifecycle() {
        PveWaveState run = new PveWaveState();
        run.start(1, 100L);
        eq(PveWaveState.Phase.WAITING, run.phase());
        eq(300L, run.nextWaveTick());
        eq(null, run.beginWave(299L));
        PveWaveState.WaveStart ticket = run.beginWave(300L);
        yes(ticket != null);
        eq(null, run.beginWave(300L));
        eq(ticket.wave().spawns(), run.pendingSpawns());
        yes(!run.registerSpawn(ticket.epoch() - 1L, 0, nextEntity()));
        yes(!run.registerSpawn(ticket.epoch(), -1, nextEntity()));
        yes(!run.registerSpawn(ticket.epoch(), 0, null));
        Map<Integer, UUID> entities = spawnAll(run, ticket.epoch());
        UUID first = entities.get(0);
        yes(!run.registerSpawn(ticket.epoch(), 0, nextEntity()));
        yes(run.finishSpawning(ticket.epoch(), 300L));
        eq(PveWaveState.Phase.FIGHTING, run.phase());
        eq(List.of(), run.pendingSpawns());
        yes(!run.defeated(ticket.epoch(), nextEntity(), 301L));
        yes(!run.defeated(ticket.epoch(), first, 299L));
        int count = run.remaining();
        yes(run.missing(ticket.epoch(), first));
        eq(count, run.remaining());
        eq(1, run.pendingSpawns().size());
        eq(0, run.pendingSpawns().get(0).slot());
        eq(null, run.claimReward());
        yes(!run.defeated(ticket.epoch(), first, 302L));
        UUID replacement = nextEntity();
        yes(!run.registerSpawn(ticket.epoch(), 0, first));
        yes(run.registerSpawn(ticket.epoch(), 0, replacement));
        entities.put(0, replacement);
        yes(run.finishSpawning(ticket.epoch(), 302L));
        for (UUID id : entities.values()) {
            yes(run.defeated(ticket.epoch(), id, 303L));
            yes(!run.defeated(ticket.epoch(), id, 303L));
        }
        eq(PveWaveState.Phase.WAITING, run.phase());
        eq(0, run.remaining());
        eq(703L, run.nextWaveTick());
        eq(new PveWaveState.Reward(1, false, false, 1), run.claimReward());
        eq(null, run.claimReward());
        eq(null, run.beginWave(702L));
        yes(run.beginWave(703L) != null);
    }

    private static void allTenWaves() {
        PveWaveState run = new PveWaveState();
        run.start(16, 0L);
        long tick = 200L;
        for (int wave = 1; wave <= 10; wave++) {
            PveWaveState.WaveStart plan = run.beginWave(tick);
            yes(plan != null);
            eq(wave, plan.wave().number());
            Map<Integer, UUID> spawned = spawnAll(run, plan.epoch());
            yes(run.finishSpawning(plan.epoch(), tick));
            int previousRemaining = run.remaining();
            for (UUID entity : spawned.values()) {
                yes(run.defeated(plan.epoch(), entity, tick + 1L));
                eq(--previousRemaining, run.remaining());
                yes(!run.defeated(plan.epoch(), entity, tick + 1L));
            }
            PveWaveState.Reward reward = run.claimReward();
            eq(wave, reward.wave());
            eq(wave == 10, reward.completed());
            eq(plan.wave().boss(), reward.boss());
            eq(plan.wave().rewardTier(), reward.tier());
            eq(null, run.claimReward());
            tick += 401L;
        }
        yes(run.completed() && !run.failed());
        eq(PveWaveState.Phase.COMPLETED, run.phase());
        eq(null, run.beginWave(tick + 500L));
        eq(0, run.remaining());
        run.clear();
        eq(PveWaveState.Phase.IDLE, run.phase());
        eq(0, run.wave());
        eq(null, run.claimReward());
    }

    private static void failureAndEpochs() {
        PveWaveState run = new PveWaveState();
        throwsType(IllegalArgumentException.class, () -> run.start(0, 0L));
        throwsType(IllegalArgumentException.class, () -> run.start(1, -1L));
        run.start(1, 0L);
        PveWaveState.WaveStart old = run.beginWave(200L);
        UUID entity = nextEntity();
        yes(run.registerSpawn(old.epoch(), 0, entity));
        yes(!run.finishSpawning(old.epoch(), 200L));
        yes(run.failed() && !run.completed());
        eq(null, run.claimReward());
        eq(null, run.beginWave(50_000L));
        run.start(1, 60_000L);
        PveWaveState.WaveStart fresh = run.beginWave(60_200L);
        yes(fresh.epoch() != old.epoch());
        yes(!run.registerSpawn(old.epoch(), 0, nextEntity()));
        yes(!run.defeated(old.epoch(), entity, 60_201L));
        yes(!run.missing(old.epoch(), entity));
        yes(!run.finishSpawning(old.epoch(), 60_201L));
        spawnAll(run, fresh.epoch());
        yes(run.finishSpawning(fresh.epoch(), 60_201L));
        run.abort();
        yes(run.failed());
        eq(List.of(), run.pendingSpawns());
        eq(null, run.claimReward());
        run.start(1, Long.MAX_VALUE - 1L);
        eq(Long.MAX_VALUE, run.nextWaveTick());
        eq(null, run.beginWave(Long.MAX_VALUE - 1L));
        yes(run.beginWave(Long.MAX_VALUE) != null);
    }

    private static void difficultyAndSummary() {
        eq(PveDifficulty.NORMAL, PveDifficulty.parse(null));
        eq(PveDifficulty.NORMAL, PveDifficulty.parse("invalid"));
        eq(PveDifficulty.EASY, PveDifficulty.parse(" EaSy "));
        eq(PveDifficulty.HARD, PveDifficulty.parse("HARD"));
        eq(PveDifficulty.NORMAL, CampaignRules.defaults().pveDifficulty());
        eq(PveDifficulty.NORMAL, new CampaignRules(null, 3, null, false, false, PlayMode.PVE).pveDifficulty());
        eq(PveDifficulty.EASY, PveDifficulty.HARD.next());
        Set<String> names = new HashSet<>();
        for (int wave = 1; wave <= 10; wave++) {
            names.add(PveWaves.nameKey(wave));
            var baseline = PveWaves.wave(wave, 16);
            eq(baseline, PveWaves.wave(wave, 16, PveDifficulty.NORMAL));
            var casual = PveWaves.wave(wave, 16, PveDifficulty.EASY);
            var hard = PveWaves.wave(wave, 16, PveDifficulty.HARD);
            eq(baseline.spawns().size(), casual.spawns().size());
            eq(baseline.spawns().size(), hard.spawns().size());
            eq(baseline.rewardTier(), hard.rewardTier());
            for (int slot = 0; slot < baseline.spawns().size(); slot++) {
                var regular = baseline.spawns().get(slot);
                yes(casual.spawns().get(slot).healthMultiplier() < regular.healthMultiplier());
                yes(hard.spawns().get(slot).healthMultiplier() > regular.healthMultiplier());
                yes(casual.spawns().get(slot).damageMultiplier() < regular.damageMultiplier());
                yes(hard.spawns().get(slot).damageMultiplier() > regular.damageMultiplier());
                eq(regular.kind(), hard.spawns().get(slot).kind());
                eq(regular.boss(), casual.spawns().get(slot).boss());
            }
        }
        eq(10, names.size());
        PveWaveState run = new PveWaveState();
        run.start(1, 100L, PveDifficulty.HARD);
        var first = run.beginWave(300L);
        eq(PveWaves.wave(1, 1, PveDifficulty.HARD), first.wave());
        Map<Integer, UUID> initial = spawnAll(run, first.epoch());
        yes(run.finishSpawning(first.epoch(), 300L));
        UUID absent = initial.remove(0);
        yes(run.missing(first.epoch(), absent));
        eq(0, run.summary(310L).defeats());
        yes(!run.defeated(first.epoch(), absent, 310L));
        initial.putAll(spawnAll(run, first.epoch()));
        yes(run.finishSpawning(first.epoch(), 310L));
        for (UUID mob : initial.values()) {
            yes(run.defeated(first.epoch(), mob, 320L));
            yes(!run.defeated(first.epoch(), mob, 320L));
        }
        eq(1, run.summary(320L).clearedWaves());
        eq(initial.size(), run.summary(320L).defeats());
        eq("0:11", run.summary(320L).elapsedLabel());
        run.abort(400L);
        eq(new PveWaveState.Summary(PveDifficulty.HARD, 1, initial.size(), 300L), run.summary(1_000L));
        run.clear();
        eq(new PveWaveState.Summary(PveDifficulty.NORMAL, 0, 0, 0L), run.summary(1000L));
        run.start(1, 1000L, PveDifficulty.EASY);
        int kills = 0;
        long tick = 1200L;
        for (int wave = 1; wave <= 10; wave++) {
            var ticket = run.beginWave(tick);
            var spawned = spawnAll(run, ticket.epoch());
            yes(run.finishSpawning(ticket.epoch(), tick));
            for (UUID mob : spawned.values()) yes(run.defeated(ticket.epoch(), mob, tick + 20L));
            kills += spawned.size();
            tick += 420L;
        }
        var result = run.summary(tick);
        eq(10, result.clearedWaves()); eq(kills, result.defeats()); eq(PveDifficulty.EASY, result.difficulty());
        eq(result, run.summary(tick + 400L)); // The result excludes the final reward presentation.
        run.abort(tick + 400L);
        yes(run.completed()); eq(result, run.summary(tick + 1000L));
    }

    private static Map<Integer, UUID> spawnAll(PveWaveState run, long epoch) {
        Map<Integer, UUID> values = new java.util.LinkedHashMap<>();
        for (PveWaves.Spawn spawn : run.pendingSpawns()) {
            UUID entity = nextEntity();
            yes(run.registerSpawn(epoch, spawn.slot(), entity));
            values.put(spawn.slot(), entity);
        }
        return values;
    }

    private static UUID nextEntity() { return new UUID(51L, ++entitySequence); }
    private static void yes(boolean condition) { assertions++; if (!condition) throw new AssertionError("Play mode scenario failed"); }
    private static void eq(Object expected, Object actual) {
        assertions++;
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
    private static void throwsType(Class<? extends Throwable> type, Runnable action) {
        assertions++;
        try { action.run(); } catch (Throwable error) { if (type.isInstance(error)) return; throw new AssertionError(error); }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
}
