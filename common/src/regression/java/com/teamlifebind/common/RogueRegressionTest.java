package com.teamlifebind.common;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Route replay protection, seeded variety, bounded encounters and the real shared wave state. */
final class RogueRegressionTest {
    private static int assertions;
    static int run() {
        assertions = 0;
        eq(PlayMode.RG, PlayMode.parse(" rG "));
        eq(PlayMode.RG, PlayMode.parse("roguelike"));
        yes(PlayMode.RG.encounter() && PlayMode.RG.cooperative() && PlayMode.RG.minPlayers() == 1);
        yes(!PlayMode.SUR.encounter() && !PlayMode.PVP.encounter() && PlayMode.PVE.encounter());
        seededEncounters();
        lifecycle();
        delayedSelection();
        lifeBudget();
        partyVoting();
        System.out.println("Roguelike routes, relics and encounter integration passed (" + assertions + " assertions).");
        return assertions;
    }

    private static void seededEncounters() {
        Set<RogueRun.Theme> themes = new HashSet<>();
        Set<RogueRun.Threat> threats = new HashSet<>();
        Set<RogueRun.Relic> relics = new HashSet<>();
        Set<List<PveWaves.Spawn>> firstEncounters = new HashSet<>();
        for (long seed = 0; seed < 32; seed++) for (PveDifficulty difficulty : PveDifficulty.values()) {
            RogueRun first = new RogueRun(), replay = new RogueRun(), capped = new RogueRun();
            first.start(seed, 16, difficulty); replay.start(seed, 16, difficulty); capped.start(seed, Integer.MAX_VALUE, difficulty);
            for (int floor = 1; floor <= PveWaves.TOTAL_WAVES; floor++) {
                eq(first.offers(), replay.offers()); eq(first.offers(), capped.offers());
                eq(3, first.offers().size());
                eq(3L, first.offers().stream().map(RogueRun.Route::theme).distinct().count());
                eq(3L, first.offers().stream().map(RogueRun.Route::threat).distinct().count());
                eq(3L, first.offers().stream().map(RogueRun.Route::relic).distinct().count());
                var choice = first.offers().get((int) ((seed + floor) % 3));
                themes.add(choice.theme()); threats.add(choice.threat()); relics.add(choice.relic());
                eq(null, first.choose(choice.token() - 1, choice.slot()));
                eq(null, first.choose(choice.token(), 0));
                eq(null, first.choose(choice.token(), 4));
                eq(choice, first.choose(choice.token(), choice.slot()));
                eq(choice, replay.choose(choice.token(), choice.slot()));
                eq(choice, capped.choose(choice.token(), choice.slot()));
                eq(null, first.choose(choice.token(), choice.slot()));
                eq(floor, first.totalRelics()); yes(!first.waitingForChoice());
                for (RogueRun.Relic relic : RogueRun.Relic.values()) yes(first.level(relic) <= relic.maxStacks());
                PveWaves.Wave encounter = first.plan();
                eq(encounter, first.plan()); eq(encounter, replay.plan()); eq(encounter, capped.plan());
                eq(floor, encounter.number()); yes(encounter.spawns().size() <= PveWaves.MAX_ENEMIES);
                eq(floor == 5 || floor == 10, encounter.boss());
                eq(encounter.boss() ? 1L : 0L, encounter.spawns().stream().filter(PveWaves.Spawn::boss).count());
                for (int slot = 0; slot < encounter.spawns().size(); slot++) {
                    var spawn = encounter.spawns().get(slot);
                    eq(slot, spawn.slot()); yes(Double.isFinite(spawn.healthMultiplier()) && spawn.healthMultiplier() > 0);
                    yes(Double.isFinite(spawn.damageMultiplier()) && spawn.damageMultiplier() > 0);
                    if (spawn.boss()) eq(PveWaves.Kind.RAVAGER, spawn.kind());
                }
                if (floor == 1) firstEncounters.add(encounter.spawns());
                var reward = new PveWaveState.Reward(floor, encounter.boss(), floor == 10, encounter.rewardTier());
                yes(first.rewardTier(reward) >= reward.tier());
                yes(first.rewardTier(reward) <= (floor == 10 ? 8 : 7));
                yes(first.advanceAfterWave(floor)); yes(replay.advanceAfterWave(floor)); yes(capped.advanceAfterWave(floor));
                yes(!first.advanceAfterWave(floor));
            }
            yes(first.completed() && !first.waitingForChoice());
        }
        eq(Set.of(RogueRun.Theme.values()), themes); eq(Set.of(RogueRun.Threat.values()), threats); eq(Set.of(RogueRun.Relic.values()), relics);
        yes(firstEncounters.size() > 9);
    }

    private static void lifecycle() {
        RogueRun run = new RogueRun();
        run.start(42, 1, PveDifficulty.NORMAL);
        long oldToken = run.offers().getFirst().token();
        yes(!run.advanceAfterWave(1)); eq(0, run.totalRelics());
        try { run.plan(); throw new AssertionError("Unchosen route produced a wave"); } catch (IllegalStateException expected) { assertions++; }
        var original = run.offers();
        run.choose(oldToken, 1); eq(1, run.totalRelics());
        run.clear(); yes(!run.active() && run.relics().isEmpty() && run.offers().isEmpty());
        eq(null, run.choose(oldToken, 2));
        run.start(42, 1, PveDifficulty.NORMAL);
        yes(oldToken != run.offers().getFirst().token()); eq(null, run.choose(oldToken, 1));
        for (int slot = 0; slot < 3; slot++) {
            eq(original.get(slot).theme(), run.offers().get(slot).theme());
            eq(original.get(slot).threat(), run.offers().get(slot).threat());
            eq(original.get(slot).relic(), run.offers().get(slot).relic());
        }
    }

    private static void delayedSelection() {
        RogueRun run = new RogueRun(); PveWaveState waves = new PveWaveState();
        run.start(123, 1, PveDifficulty.HARD); waves.start(1, 0L, PveDifficulty.HARD);
        long tick = 10000;
        int deaths = 0;
        for (int floor = 1; floor <= 10; floor++) {
            var route = run.offers().getFirst(); yes(run.choose(route.token(), route.slot()) != null);
            yes(waves.postponeNextWave(tick, 200)); eq(tick + 200L, waves.nextWaveTick());
            eq(null, waves.beginWave(tick + 199, run.plan()));
            tick += 200;
            var started = waves.beginWave(tick, run.plan()); yes(started != null);
            List<UUID> ids = started.wave().spawns().stream().map(spawn -> new UUID(floorKey(started), spawn.slot() + 1)).toList();
            for (int slot = 0; slot < ids.size(); slot++) yes(waves.registerSpawn(started.epoch(), slot, ids.get(slot)));
            yes(waves.finishSpawning(started.epoch(), tick));
            for (UUID id : ids) { yes(waves.defeated(started.epoch(), id, ++tick)); deaths++; }
            eq(deaths, waves.summary(tick).defeats());
            var reward = waves.claimReward(); yes(reward != null); eq(floor, reward.wave()); eq(null, waves.claimReward());
            yes(run.advanceAfterWave(reward.wave()));
            tick += 500;
        }
        yes(run.completed() && waves.completed()); eq(10, waves.summary(tick).clearedWaves());
        yes(!waves.postponeNextWave(tick, 200));
    }
    private static long floorKey(PveWaveState.WaveStart started) { return started.epoch(); }
    private static void lifeBudget() {
        for (MatchMode variant : MatchMode.values()) {
            CampaignRules rules = new CampaignRules(variant, 3, DragonVictoryMode.LAST_HIT, false, false, PlayMode.RG);
            yes(rules.limitedRespawns());
            RoundCampaignState campaign = new RoundCampaignState();
            campaign.start(java.util.Map.of(new UUID(70, 1), 1), rules, 0);
            for (int wipe = 1; wipe <= 3; wipe++) {
                var result = campaign.applyWipe(1, false, wipe * 20L);
                yes(result.applied() && !result.eliminated()); eq(3 - wipe, result.remainingRespawnCredits());
                yes(!campaign.applyWipe(1, false, wipe * 20L).applied());
                eq(null, campaign.pollVictory(wipe * 20L + 1));
            }
            yes(campaign.applyWipe(1, false, 80).eliminated());
            eq(new RoundCampaignState.Victory(null, true), campaign.pollVictory(81));
            campaign.clear(); eq(Set.of(), campaign.eligibleTeams());
        }
        yes(!CampaignRules.defaults().limitedRespawns());
        yes(!new CampaignRules(MatchMode.DRAGON_HUNT, 3, null, false, false, PlayMode.PVE).limitedRespawns());
    }
    private static void partyVoting() {
        UUID a = new UUID(8, 1), b = new UUID(8, 2), c = new UUID(8, 3), d = new UUID(8, 4);
        RogueBallot ballot = new RogueBallot();
        ballot.update(1, Set.of(a), 10);
        ballot.update(1, Set.of(a), 10000);
        eq(0, ballot.decision()); eq(-1L, ballot.secondsRemaining(10000));
        yes(ballot.cast(1, a, 2, 10000)); eq(2, ballot.decision());
        yes(!ballot.cast(1, a, 1, 10001)); eq(1, ballot.submitted());

        ballot.update(2, Set.of(a, b), 11000);
        eq(0, ballot.submitted()); eq(0, ballot.decision());
        yes(ballot.cast(2, a, 3, 11000)); eq(0, ballot.decision());
        yes(ballot.cast(2, a, 2, 11200)); eq(1, ballot.submitted()); eq(0, ballot.votesFor(3));
        eq(10L, ballot.secondsRemaining(11200));
        yes(!ballot.cast(1, b, 1, Long.MAX_VALUE)); // Old windows cannot advance the live clock.
        yes(!ballot.cast(2, c, 1, Long.MAX_VALUE));
        yes(!ballot.cast(2, b, 0, Long.MAX_VALUE)); yes(!ballot.cast(2, b, 4, Long.MAX_VALUE));
        eq(10L, ballot.secondsRemaining(11200));
        yes(ballot.cast(2, b, 1, 11201)); eq(1, ballot.decision()); // Complete 1:1 tie.

        ballot.update(3, Set.of(a, b, c), 12000);
        yes(ballot.cast(3, a, 3, 12000)); yes(ballot.cast(3, b, 3, 12001));
        eq(3, ballot.decision()); eq(2, ballot.submitted()); // Majority without the abstainer.
        ballot.update(4, Set.of(a, b, c), 13000);
        yes(ballot.cast(4, a, 2, 13000));
        ballot.update(4, Set.of(a, b, c), 13399); eq(0, ballot.decision()); eq(1L, ballot.secondsRemaining(13399));
        ballot.update(4, Set.of(a, b, c), 13400); eq(2, ballot.decision());

        ballot.update(5, Set.of(a, b, c, d), 14000);
        yes(ballot.cast(5, a, 1, 14000)); yes(ballot.cast(5, b, 2, 14001)); yes(ballot.cast(5, c, 1, 14002));
        eq(0, ballot.decision());
        ballot.update(5, Set.of(b, c, d), 14020); eq(0, ballot.voteOf(a)); eq(1, ballot.votesFor(1));
        ballot.update(5, Set.of(a, b, c, d), 14040); eq(0, ballot.voteOf(a));
        yes(ballot.cast(5, a, 2, 14040)); eq(0, ballot.decision());
        yes(ballot.cast(5, c, 2, 14041)); eq(2, ballot.decision());

        ballot.update(6, Set.of(a, b, c), 15000); yes(ballot.cast(6, a, 2, 15000));
        ballot.update(6, Set.of(b, c), 15020); eq(0, ballot.submitted()); eq(-1L, ballot.secondsRemaining(16000));
        ballot.update(6, Set.of(), 16000); eq(0, ballot.decision());
        ballot.update(6, Set.of(a, b), 17000); yes(ballot.cast(6, b, 3, 17000));
        eq(20L, ballot.secondsRemaining(17000));
        ballot.update(6, Set.of(b), 17001); eq(3, ballot.decision()); // A disconnect removes the last abstainer.

        for (int combination = 0; combination < 256; combination++) {
            int encoded = combination;
            int[] totals = new int[4];
            long token = 100L + combination;
            ballot.update(token, Set.of(a, b, c, d), 0);
            for (UUID voter : List.of(a, b, c, d)) {
                int slot = encoded % 4; encoded /= 4;
                totals[slot]++;
                if (slot != 0) ballot.cast(token, voter, slot, 1);
            }
            ballot.update(token, Set.of(a, b, c, d), 401);
            int expected = 0;
            for (int slot = 1; slot <= 3; slot++) if (totals[slot] > (expected == 0 ? 0 : totals[expected])) expected = slot;
            eq(expected, ballot.decision());
            yes(ballot.submitted() <= 4);
            eq(ballot.submitted(), ballot.votesFor(1) + ballot.votesFor(2) + ballot.votesFor(3));
        }
        ballot.clear(); eq(0, ballot.participants()); eq(0, ballot.submitted()); eq(0, ballot.decision());
        yes(!ballot.cast(-1L, a, 1, 0));

        RogueRun run = new RogueRun(); run.start(77, 2, PveDifficulty.NORMAL);
        long epoch = run.epoch(), token = run.offers().getFirst().token();
        ballot.update(token, Set.of(a, b), 0);
        yes(ballot.cast(token, a, 1, 0)); eq(0, run.totalRelics());
        yes(ballot.cast(token, b, 1, 1)); yes(run.choose(ballot.token(), ballot.decision()) != null);
        eq(1, run.totalRelics()); eq(null, run.choose(ballot.token(), ballot.decision()));
        run.start(77, 2, PveDifficulty.NORMAL); yes(epoch != run.epoch());
        eq(null, run.choose(token, 1)); run.clear(); eq(0L, run.epoch());
    }
    private static void yes(boolean value) { assertions++; if (!value) throw new AssertionError("RG invariant failed at " + assertions); }
    private static void eq(Object expected, Object actual) { assertions++; if (!Objects.equals(expected, actual)) throw new AssertionError(expected + " != " + actual); }
}
