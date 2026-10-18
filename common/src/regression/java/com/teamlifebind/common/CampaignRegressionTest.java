package com.teamlifebind.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Multiplayer campaign scenarios exercise the public round-state contract without platform stubs. */
final class CampaignRegressionTest {
    private static final UUID A = new UUID(7L, 1L);
    private static final UUID B = new UUID(7L, 2L);
    private static final UUID C = new UUID(7L, 3L);
    private static final UUID D = new UUID(7L, 4L);
    private static final UUID E = new UUID(7L, 5L);
    private static final UUID F = new UUID(7L, 6L);
    private static final UUID OUTSIDER = new UUID(7L, 99L);
    private static final Map<UUID, Integer> ROSTER = Map.of(A, 1, B, 1, C, 2, D, 2, E, 3, F, 3);
    private static int assertions;

    private CampaignRegressionTest() {
    }

    static int run() throws Exception {
        assertions = 0;
        rulesAndSnapshots();
        creditsAndVictory();
        combatAndIndividualReconnect();
        acknowledgedReconnectRespawn();
        offlineTeamForfeit();
        dragonContributions();
        reconnaissance();
        controlPointAndTasks();
        borderAndReset();
        offlineEngineWinner();
        concurrentAuthorizations();
        System.out.println("Campaign multiplayer scenarios passed (" + assertions + " assertions).");
        return assertions;
    }

    private static RoundCampaignState round(MatchMode mode, int credits, DragonVictoryMode dragon, boolean point, boolean border) {
        RoundCampaignState state = new RoundCampaignState();
        state.start(ROSTER, new CampaignRules(mode, credits, dragon, point, border), 0L);
        return state;
    }

    private static RoundCampaignState limited() {
        return round(MatchMode.LIMITED_LIVES, 3, DragonVictoryMode.LAST_HIT, false, false);
    }

    private static void rulesAndSnapshots() {
        eq(new CampaignRules(MatchMode.DRAGON_HUNT, 3, DragonVictoryMode.LAST_HIT, false, false), CampaignRules.defaults());
        eq(MatchMode.DRAGON_HUNT, new CampaignRules(null, 0, null, true, true).matchMode());
        eq(DragonVictoryMode.LAST_HIT, new CampaignRules(null, 0, null, true, true).dragonVictoryMode());
        eq(1, new CampaignRules(null, Integer.MIN_VALUE, null, false, false).respawnCredits());
        eq(10, new CampaignRules(null, Integer.MAX_VALUE, null, false, false).respawnCredits());
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            eq(MatchMode.LIMITED_LIVES, MatchMode.parse(" limited_lives "));
            eq(DragonVictoryMode.CONTRIBUTION, DragonVictoryMode.parse(" contribution "));
        } finally {
            Locale.setDefault(previous);
        }
        for (String invalid : new String[] {null, "", " ", "invalid"}) {
            eq(MatchMode.DRAGON_HUNT, MatchMode.parse(invalid));
            eq(DragonVictoryMode.LAST_HIT, DragonVictoryMode.parse(invalid));
        }
        eq(MatchMode.LIMITED_LIVES, MatchMode.DRAGON_HUNT.next());
        eq(MatchMode.RANDOM_GRIMOIRE, MatchMode.LIMITED_LIVES.next());
        eq(MatchMode.DRAGON_HUNT, MatchMode.RANDOM_GRIMOIRE.next());
        eq(MatchMode.RANDOM_GRIMOIRE, MatchMode.parse(" random_grimoire "));
        eq(DragonVictoryMode.CONTRIBUTION, DragonVictoryMode.LAST_HIT.next());
        eq(DragonVictoryMode.LAST_HIT, DragonVictoryMode.CONTRIBUTION.next());
        RoundCampaignState state = limited();
        Map<UUID, Integer> mutable = new HashMap<>(ROSTER);
        state.start(mutable, CampaignRules.defaults(), 40L);
        mutable.clear();
        eq(1, state.teamForPlayer(A));
        eq(null, state.teamForPlayer(OUTSIDER));
        eq(0, state.remainingRespawnCredits(99));
        eq(Set.of(1, 2, 3), state.eligibleTeams());
        expect(UnsupportedOperationException.class, () -> state.eligibleTeams().clear());
        expect(IllegalArgumentException.class, () -> state.start(null, CampaignRules.defaults(), 0L));
        expect(IllegalArgumentException.class, () -> state.start(Map.of(), CampaignRules.defaults(), 0L));
        expect(IllegalArgumentException.class, () -> state.start(ROSTER, null, 0L));
        expect(IllegalArgumentException.class, () -> state.start(Map.of(A, 0), CampaignRules.defaults(), 0L));
        mutable.put(A, null);
        expect(IllegalArgumentException.class, () -> state.start(mutable, CampaignRules.defaults(), 0L));
        mutable.clear();
        mutable.put(null, 1);
        expect(IllegalArgumentException.class, () -> state.start(mutable, CampaignRules.defaults(), 0L));
        yes(state.isActive() && state.teamForPlayer(A) == 1, "Rejected start must preserve the existing round.");
    }

    private static void creditsAndVictory() {
        // Each allowed setting denotes paid respawns, not total deaths.
        for (int allowance = 1; allowance <= 10; allowance++) {
            RoundCampaignState state = round(MatchMode.LIMITED_LIVES, allowance, DragonVictoryMode.LAST_HIT, false, false);
            for (int death = 1; death <= allowance; death++) {
                RoundCampaignState.WipeResult paid = state.applyWipe(1, false, death);
                yes(paid.applied(), "A distinct wipe spends exactly one credit.");
                eq(allowance - death, paid.remainingRespawnCredits());
                yes(!paid.eliminated() && !state.isEliminated(1), "The final paid respawn still participates.");
                eq(allowance - death, state.remainingRespawnCredits(1));
                yes(!state.applyWipe(1, false, death).applied(), "Same-tick teammates cannot spend additional credits.");
            }
            RoundCampaignState.WipeResult exhausted = state.applyWipe(1, false, allowance + 1L);
            yes(exhausted.applied() && exhausted.eliminated(), "The next ordinary wipe after zero credits eliminates.");
            eq(0, exhausted.remainingRespawnCredits());
            yes(!state.applyWipe(1, false, allowance + 2L).applied(), "An eliminated team cannot be wiped twice.");
            eq(Set.of(2, 3), state.eligibleTeams());
            eq(null, state.pollVictory(allowance + 2L));
        }
        RoundCampaignState state = limited();
        yes(state.applyWipe(1, true, 10L).eliminated(), "Permanent lock eliminates without spending respawn credits.");
        eq(3, state.remainingRespawnCredits(1));
        yes(!state.applyWipe(99, true, 10L).applied(), "Outsiders cannot alter victory state.");
        state.applyWipe(2, true, 10L);
        eq(null, state.pollVictory(10L));
        eq(new RoundCampaignState.Victory(3, false), state.pollVictory(11L));
        eq(new RoundCampaignState.Victory(3, false), state.pollVictory(100L));
        state = limited();
        for (int team : Set.of(1, 2, 3)) state.applyWipe(team, true, 50L);
        eq(null, state.pollVictory(50L));
        eq(new RoundCampaignState.Victory(null, true), state.pollVictory(51L));
        RoundCampaignState classic = round(MatchMode.DRAGON_HUNT, 3, DragonVictoryMode.LAST_HIT, false, false);
        for (int death = 1; death <= 20; death++) {
            yes(!classic.applyWipe(1, false, death).eliminated(), "Classic mode retains unrestricted ordinary respawns.");
        }
        eq(3, classic.remainingRespawnCredits(1));
        classic.applyWipe(1, true, 30L);
        classic.applyWipe(2, true, 30L);
        eq(null, classic.pollVictory(31L));
        yes(!classic.eligibleTeams().contains(1), "Adapters may mark an entirely locked classic team ineligible for contributions.");
        state = limited();
        state.start(ROSTER, state.rules(), Long.MIN_VALUE);
        yes(state.applyWipe(1, false, Long.MIN_VALUE).applied(), "A valid first tick cannot collide with a missing wipe sentinel.");
    }

    private static void combatAndIndividualReconnect() {
        RoundCampaignState state = limited();
        state.recordDamage(A, 100L);
        yes(!state.inCombat(A, 99L), "Future hits cannot put a rewound clock in combat.");
        yes(state.inCombat(A, 100L) && state.inCombat(A, 299L), "Combat includes the preceding 199 ticks.");
        yes(!state.inCombat(A, 300L), "Combat ends exactly after 200 ticks.");
        state.recordDamage(OUTSIDER, 100L);
        yes(!state.inCombat(OUTSIDER, 100L), "Spectators and outsiders never receive campaign combat state.");
        state.disconnect(A, 150L, false);
        eq(600L, state.reconnectGraceRemainingTicks(A, 150L));
        eq(1L, state.reconnectGraceRemainingTicks(A, 749L));
        eq(Set.of(), state.expiredDisconnectTeams(749L));
        RoundCampaignState.ReconnectResult timely = state.join(A, 749L);
        yes(!timely.penaltyDue() && !timely.pendingRespawn() && !timely.locked(), "Returning inside grace avoids a penalty.");
        eq(Set.of(), state.expiredDisconnectTeams(800L));
        eq(3, state.remainingRespawnCredits(1));
        state.disconnect(A, 1_000L, true);
        state.disconnect(A, 1_200L, true);
        eq(1L, state.reconnectGraceRemainingTicks(A, 1_599L));
        eq(Set.of(1), state.expiredDisconnectTeams(1_600L));
        yes(!state.disconnectForfeit(1), "An individual disconnect is an ordinary wipe while a teammate remains connected.");
        eq(Set.of(), state.expiredDisconnectTeams(1_600L));
        eq(Set.of(), state.expiredDisconnectTeams(1_800L));
        RoundCampaignState.ReconnectResult due = state.join(A, 1_600L);
        yes(due.penaltyDue(), "A consumed deadline remains pending until the adapter has applied the wipe.");
        yes(state.applyWipe(1, false, 1_600L).applied(), "Timeout authorizes one ordinary team wipe.");
        RoundCampaignState.ReconnectResult restored = state.join(A, 1_600L);
        yes(restored.pendingRespawn() && !restored.penaltyDue(), "The late returner receives the pending respawn after its penalty is applied.");
        eq(2, state.remainingRespawnCredits(1));
        yes(!state.join(A, 1_601L).pendingRespawn(), "Pending respawn is consumed once.");

        state = limited();
        state.disconnect(A, 0L, true);
        state.applyWipe(1, false, 600L);
        eq(Set.of(), state.expiredDisconnectTeams(600L));
        yes(state.join(A, 601L).pendingRespawn(), "An offline teammate keeps the ordinary wipe even when no disconnect timeout fires.");
        eq(2, state.remainingRespawnCredits(1));
        state = limited();
        state.disconnect(A, 0L, true);
        RoundCampaignState.ReconnectResult unpolled = state.join(A, 600L);
        yes(unpolled.penaltyDue(), "Joining exactly at expiry discovers a timeout before the server poll.");
        yes(state.join(A, 601L).penaltyDue(), "Repeated joins cannot clear an unapplied timeout.");
        yes(state.applyWipe(1, false, 602L).applied(), "A deferred adapter wipe still applies once.");
        yes(state.join(A, 603L).pendingRespawn(), "The adapter's deferred wipe retains the reconnect respawn.");
        eq(2, state.remainingRespawnCredits(1));

        state = limited();
        state.disconnect(A, 0L, true);
        state.disconnect(B, 1L, true);
        eq(Set.of(1), state.expiredDisconnectTeams(600L));
        state.applyWipe(1, false, 600L);
        // Already offline member's processed disconnect cannot repeatedly penalize the team.
        eq(Set.of(1), state.expiredDisconnectTeams(601L));
        yes(state.disconnectForfeit(1), "Once the complete team also exceeds its grace it is a forfeit.");
        state.applyWipe(1, true, 601L);
        yes(state.join(A, 602L).locked(), "Elimination outranks a previously pending paid respawn.");
        yes(!state.join(B, 602L).pendingRespawn(), "An eliminated offline teammate cannot receive a paid respawn.");

        RoundCampaignState classic = round(MatchMode.DRAGON_HUNT, 3, DragonVictoryMode.LAST_HIT, false, false);
        classic.disconnect(A, 0L, false);
        classic.applyWipe(1, false, 50L);
        yes(classic.join(A, 51L).pendingRespawn(), "Classic also retains offline teammate wipe state.");
        classic.disconnect(A, 100L, false);
        classic.applyWipe(1, true, 101L);
        yes(classic.join(A, 102L).locked(), "Entirely permanent classic teams remain permanently locked.");
    }

    private static void offlineTeamForfeit() {
        RoundCampaignState state = limited();
        state.disconnect(A, 0L, false);
        eq(Set.of(), state.expiredDisconnectTeams(10_000L));
        state.disconnect(B, 100L, false);
        eq(Set.of(), state.expiredDisconnectTeams(699L));
        eq(Set.of(1), state.expiredDisconnectTeams(700L));
        yes(state.disconnectForfeit(1), "Whole-team grace starts when the final connected teammate leaves.");
        eq(Set.of(), state.expiredDisconnectTeams(700L));
        yes(state.join(A, 700L).penaltyDue(), "A team cannot escape a queued forfeit by reconnecting.");
        yes(state.disconnectForfeit(1), "The adapter can still identify the forfeit after a late join.");
        yes(state.applyWipe(1, state.disconnectForfeit(1), 700L).eliminated(), "Whole-team expiry permanently eliminates without spending three ordinary credits.");
        yes(!state.disconnectForfeit(1), "Applied forfeits are consumed.");
        yes(state.join(A, 701L).locked() && state.join(B, 701L).locked(), "Every forfeited member returns locked.");
        eq(3, state.remainingRespawnCredits(1));
        eq(Set.of(), state.expiredDisconnectTeams(10_000L));

        state = limited();
        state.disconnect(A, 0L, false);
        state.disconnect(B, 0L, false);
        yes(!state.join(A, 599L).penaltyDue(), "One timely team member cancels the whole-team offline deadline.");
        eq(Set.of(), state.expiredDisconnectTeams(600L));
        state.disconnect(A, 700L, false);
        eq(Set.of(), state.expiredDisconnectTeams(1_299L));
        eq(Set.of(1), state.expiredDisconnectTeams(1_300L));
        state = limited();
        state.disconnect(A, 0L, false);
        state.disconnect(B, 0L, false);
        yes(state.join(A, 600L).penaltyDue() && state.disconnectForfeit(1), "Unpolled late joins also discover whole-team forfeits.");
        state.applyWipe(1, true, 600L);
        RoundCampaignState.ReconnectResult locked = state.join(A, 601L);
        yes(locked.locked() && !locked.pendingRespawn(), "Forfeit clears the returning player's provisional respawn.");

        state = limited();
        state.disconnect(A, 0L, true);
        state.disconnect(B, 0L, true);
        eq(Set.of(1), state.expiredDisconnectTeams(600L));
        yes(state.disconnectForfeit(1), "Safe and combat whole-team departures share the same forfeit rule.");
        RoundCampaignState classic = round(MatchMode.DRAGON_HUNT, 3, DragonVictoryMode.LAST_HIT, false, false);
        classic.disconnect(A, 0L, false);
        classic.disconnect(B, 0L, false);
        eq(Set.of(), classic.expiredDisconnectTeams(10_000L));
        yes(!classic.disconnectForfeit(1), "Classic retains its prior safe-disconnect behavior.");
        state = limited();
        state.disconnect(OUTSIDER, 0L, true);
        eq(Set.of(), state.expiredDisconnectTeams(600L));
        eq(new RoundCampaignState.ReconnectResult(null, false, false, false), state.join(OUTSIDER, 600L));
    }

    private static void acknowledgedReconnectRespawn() {
        RoundCampaignState state = limited();
        state.disconnect(A, 0L, true);
        eq(Set.of(1), state.expiredDisconnectTeams(600L));
        RoundCampaignState.ReconnectResult late = state.join(A, 600L);
        yes(late.penaltyDue() && !late.pendingRespawn(), "A late return requests the unapplied team penalty.");
        // The platform guard deferred the wipe, but it really queued the returner's respawn now.
        yes(state.acknowledgePendingRespawn(A), "Queued reconnect respawns acknowledge their provisional shared state.");
        yes(!state.acknowledgePendingRespawn(A), "Acknowledgment consumes one pending respawn only.");
        yes(!state.acknowledgePendingRespawn(OUTSIDER), "Acknowledging an outsider cannot mutate a team deadline.");
        yes(state.applyWipe(1, false, 601L).applied(), "Acknowledgment does not discard the deferred team penalty.");
        eq(2, state.remainingRespawnCredits(1));
        state.disconnect(A, 700L, false);
        RoundCampaignState.ReconnectResult safe = state.join(A, 701L);
        yes(!safe.pendingRespawn() && !safe.penaltyDue() && !safe.locked(), "A later safe reconnect cannot repeat the acknowledged death or item loss.");
        eq(2, state.remainingRespawnCredits(1));
        state.disconnect(C, 710L, false);
        state.applyWipe(2, false, 711L);
        yes(!state.acknowledgePendingRespawn(A), "One player's acknowledgment cannot consume another team's pending respawn.");
        yes(state.join(C, 712L).pendingRespawn(), "Offline teammates keep their own unacknowledged wipe until return.");
        state.clear();
        yes(!state.acknowledgePendingRespawn(C), "Stopped rounds retain no acknowledgment state.");
    }

    private static void dragonContributions() {
        UUID dragon = new UUID(9L, 1L);
        UUID otherDragon = new UUID(9L, 2L);
        RoundCampaignState state = round(MatchMode.DRAGON_HUNT, 3, DragonVictoryMode.CONTRIBUTION, false, false);
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0D, 0.0D}) {
            state.recordDragonDamage(1, dragon, invalid, 200.0D, 0L);
            state.recordDragonDamage(1, dragon, 2.0D, invalid, 0L);
        }
        state.recordDragonDamage(99, dragon, 10.0D, 200.0D, 0L);
        state.recordDragonDamage(1, null, 10.0D, 200.0D, 0L);
        eq(Map.of(), state.dragonContributions(dragon, 0L));
        eq(2, state.chooseDragonWinner(dragon, 2, 0L));
        eq(null, state.chooseDragonWinner(dragon, 99, 0L));
        state.recordDragonDamage(1, dragon, 50.0D, 5.0D, 100L);
        state.recordDragonDamage(1, dragon, 7.0D, 200.0D, 200L);
        state.recordDragonDamage(2, dragon, 12.0D, 200.0D, 200L);
        state.recordDragonDamage(3, otherDragon, 100.0D, 200.0D, 200L);
        eq(Map.of(1, 12.0D, 2, 12.0D), state.dragonContributions(dragon, 200L));
        eq(2, state.chooseDragonWinner(dragon, 2, 200L));
        eq(1, state.chooseDragonWinner(dragon, 3, 200L));
        eq(1, state.chooseDragonWinner(dragon, null, 200L));
        eq(3, state.chooseDragonWinner(otherDragon, 1, 200L));
        expect(UnsupportedOperationException.class, () -> state.dragonContributions(dragon, 200L).clear());
        eq(Map.of(), state.dragonContributions(dragon, 99L));
        eq(Map.of(1, 12.0D, 2, 12.0D), state.dragonContributions(dragon, 1_299L));
        eq(Map.of(1, 7.0D, 2, 12.0D), state.dragonContributions(dragon, 1_300L));
        eq(2, state.chooseDragonWinner(dragon, 1, 1_300L));
        state.applyWipe(2, true, 1_301L);
        eq(Map.of(1, 7.0D), state.dragonContributions(dragon, 1_301L));
        eq(1, state.chooseDragonWinner(dragon, 2, 1_301L));
        state.recordDragonDamage(2, dragon, 100.0D, 200.0D, 1_302L);
        eq(Map.of(), state.dragonContributions(dragon, 1_400L));
        eq(3, state.chooseDragonWinner(dragon, 3, 1_400L));
        eq(null, state.chooseDragonWinner(dragon, 2, 1_400L));
        RoundCampaignState lastHit = round(MatchMode.DRAGON_HUNT, 3, DragonVictoryMode.LAST_HIT, false, false);
        lastHit.recordDragonDamage(1, dragon, 100.0D, 200.0D, 0L);
        eq(2, lastHit.chooseDragonWinner(dragon, 2, 0L));
        eq(null, lastHit.chooseDragonWinner(dragon, null, 0L));
        state.clear();
        eq(null, state.chooseDragonWinner(dragon, 1, 1_401L));
        eq(Map.of(), state.dragonContributions(dragon, 1_401L));
    }

    private static void reconnaissance() {
        RoundCampaignState state = limited();
        yes(!state.tryActivateSilence(OUTSIDER, 0L), "Outsiders cannot spend silence cooldown.");
        yes(state.tryActivateSilence(A, 100L), "First silence activation succeeds.");
        yes(state.isSilenced(A, 100L) && state.isSilenced(B, 499L), "Silence covers the whole team for 400 ticks.");
        yes(!state.isSilenced(C, 100L), "Enemy teams remain visible.");
        yes(!state.isSilenced(B, 500L), "Silence expires exactly after 20 seconds.");
        yes(!state.tryActivateSilence(B, 1_899L), "A teammate shares the same 90-second cooldown.");
        eq(1L, state.silenceCooldownRemainingTicks(1, 1_899L));
        yes(state.tryActivateSilence(B, 1_900L), "Team silence becomes ready exactly at expiry.");
        yes(state.tryActivateSilence(C, 1_900L), "Enemies keep independent silence cooldowns.");
        state.applyWipe(1, true, 1_901L);
        yes(!state.isSilenced(A, 1_902L) && !state.tryActivateSilence(A, 5_000L), "Eliminated teams cannot retain or reactivate silence.");
        state = limited();
        for (String dimension : new String[] {null, "", " "}) {
            eq(null, state.tryScout(A, dimension, 1, 2, 3, 0L));
        }
        for (double coordinate : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            eq(null, state.tryScout(A, "end", coordinate, 2, 3, 0L));
            eq(null, state.tryScout(A, "end", 1, coordinate, 3, 0L));
            eq(null, state.tryScout(A, "end", 1, 2, coordinate, 0L));
        }
        eq(null, state.tryScout(OUTSIDER, "end", 1, 2, 3, 0L));
        RoundCampaignState.ScoutPulse pulse = state.tryScout(A, "nether", -5.5D, 64.0D, 20.5D, 100L);
        yes(pulse != null, "Invalid reconnaissance requests do not consume the later valid request.");
        eq("nether", pulse.dimension());
        close(-5.5D, pulse.x());
        close(64.0D, pulse.y());
        close(20.5D, pulse.z());
        eq(1, pulse.team());
        eq(List.of(pulse), state.activeScouts(199L));
        RoundCampaignState scoutState = state;
        expect(UnsupportedOperationException.class, () -> scoutState.activeScouts(199L).clear());
        eq(List.of(), state.activeScouts(200L));
        eq(null, state.tryScout(B, "overworld", 0, 64, 0, 999L));
        eq(1L, state.scoutCooldownRemainingTicks(1, 999L));
        yes(state.tryScout(B, "overworld", 0, 64, 0, 1_000L) != null, "Shared scouting cooldown expires exactly at 45 seconds.");
        yes(state.tryScout(C, "end", 0, 64, 0, 1_000L) != null, "Scouts for separate teams and dimensions coexist.");
        eq(2, state.activeScouts(1_000L).size());
        state.applyWipe(1, true, 1_001L);
        eq(1, state.activeScouts(1_001L).size());
        eq(null, state.tryScout(A, "end", 0, 64, 0, 5_000L));
        close(48.0D, RoundCampaignState.SCOUT_RADIUS);
    }

    private static void controlPointAndTasks() {
        RoundCampaignState state = round(MatchMode.LIMITED_LIVES, 3, DragonVictoryMode.LAST_HIT, true, false);
        eq(null, state.tickControlPoint(Set.of(1), 0L));
        for (long tick = 1; tick <= 450; tick++) state.tickControlPoint(Set.of(1), tick);
        eq(450L, state.controlPointProgress().ticks());
        for (long tick = 451; tick <= 650; tick++) state.tickControlPoint(Set.of(1, 2), tick);
        eq(450L, state.controlPointProgress().ticks());
        yes(state.controlPointProgress().contested(), "Contested occupation pauses instead of resetting the owner.");
        for (long tick = 651; tick <= 800; tick++) state.tickControlPoint(Set.of(), tick);
        eq(450L, state.controlPointProgress().ticks());
        yes(!state.controlPointProgress().present(), "Empty occupation pauses without awarding a reward.");
        for (long tick = 801; tick < 1_250; tick++) {
            if (state.tickControlPoint(Set.of(1), tick) != null) throw new AssertionError("Early control-point payout.");
        }
        eq(899L, state.controlPointProgress().ticks());
        eq(new RoundCampaignState.ControlPointReward(1, 0L), state.tickControlPoint(Set.of(1), 1_250L));
        eq(0L, state.controlPointProgress().ticks());
        eq(null, state.tickControlPoint(Set.of(1), 1_250L));
        eq(null, state.tickControlPoint(Set.of(2), 1_251L));
        eq(0L, state.controlPointProgress().ticks());
        eq(2, state.controlPointProgress().team());
        for (long tick = 1_252; tick < 2_151; tick++) state.tickControlPoint(Set.of(2), tick);
        eq(new RoundCampaignState.ControlPointReward(2, 0L), state.tickControlPoint(Set.of(2), 2_151L));
        for (long tick = 2_152; tick < 6_000; tick++) {
            if (state.tickControlPoint(Set.of(1), tick) != null) throw new AssertionError("Repeated payout in the same five-minute cycle.");
        }
        eq(null, state.tickControlPoint(Set.of(1), 6_000L));
        eq(0L, state.controlPointProgress().ticks());
        for (long tick = 6_001; tick < 6_900; tick++) state.tickControlPoint(Set.of(1), tick);
        eq(new RoundCampaignState.ControlPointReward(1, 1L), state.tickControlPoint(Set.of(1), 6_900L));
        state.tickControlPoint(Set.of(2), 6_901L);
        state.tickControlPoint(Set.of(2), 7_000L);
        eq(99L, state.controlPointProgress().ticks());
        state.tickControlPoint(Set.of(3), 7_001L);
        eq(0L, state.controlPointProgress().ticks());
        eq(3, state.controlPointProgress().team());
        state.applyWipe(3, true, 7_002L);
        state.tickControlPoint(Set.of(3, 99), 7_002L);
        yes(!state.controlPointProgress().present(), "Eliminated or unknown teams never occupy a control point.");
        state.tickControlPoint(null, 7_003L);
        eq(null, state.tickControlPoint(Set.of(1), 7_002L));
        close(6.0D, RoundCampaignState.CONTROL_POINT_RADIUS);
        close(4.0D, RoundCampaignState.CONTROL_POINT_HEIGHT);
        for (RoundCampaignState.StageTask task : RoundCampaignState.StageTask.values()) {
            yes(state.completeTask(1, task), "Each new stage task completes once per team.");
            yes(!state.completeTask(1, task), "A repeated objective does not issue repeated stage notices.");
            yes(state.completeTask(2, task), "Stage task progress is independent per team.");
        }
        eq(Set.of(RoundCampaignState.StageTask.values()), state.completedTasks(1));
        expect(UnsupportedOperationException.class, () -> state.completedTasks(1).clear());
        yes(!state.completeTask(3, RoundCampaignState.StageTask.ENTER_END), "Eliminated teams cannot complete new tasks.");
        yes(!state.completeTask(99, RoundCampaignState.StageTask.ENTER_END), "Unknown teams cannot complete tasks.");
        yes(!state.completeTask(1, null), "Missing tasks cannot alter progress.");
        RoundCampaignState disabled = limited();
        eq(null, disabled.tickControlPoint(Set.of(1), 100_000L));
        eq(0L, disabled.controlPointProgress().ticks());
    }

    private static void borderAndReset() {
        RoundCampaignState state = round(MatchMode.LIMITED_LIVES, 3, DragonVictoryMode.CONTRIBUTION, true, true);
        state.start(ROSTER, state.rules(), 100L);
        yes(!state.borderWarningDue(28_899L), "The border warning waits until exactly minute 24.");
        yes(state.borderWarningDue(28_900L), "The final shrink is announced 60 seconds before it begins.");
        yes(!state.borderWarningDue(28_900L) && !state.borderWarningDue(30_100L), "The border warning is emitted once.");
        close(750.0D, state.borderRadius(30_100L));
        close(500.0D, state.borderRadius(36_100L));
        close(250.0D, state.borderRadius(42_100L));
        close(250.0D, state.borderRadius(Long.MAX_VALUE));
        state.tryActivateSilence(A, 100L);
        state.tryScout(A, "end", 0, 64, 0, 100L);
        state.recordDamage(A, 100L);
        state.disconnect(B, 100L, true);
        state.applyWipe(2, true, 100L);
        state.completeTask(1, RoundCampaignState.StageTask.ENTER_END);
        state.clear();
        yes(!state.isActive(), "A stopped round cannot accept gameplay operations.");
        eq(CampaignRules.defaults(), state.rules());
        eq(Set.of(), state.eligibleTeams());
        eq(List.of(), state.activeScouts(100L));
        eq(0L, state.silenceCooldownRemainingTicks(1, 100L));
        eq(0L, state.scoutCooldownRemainingTicks(1, 100L));
        eq(Set.of(), state.completedTasks(1));
        eq(Set.of(), state.expiredDisconnectTeams(10_000L));
        eq(null, state.pollVictory(101L));
        yes(!state.tryActivateSilence(A, 100L) && !state.isSilenced(A, 100L), "Stopped silence does not survive into the lobby.");
        yes(!state.applyWipe(1, false, 100L).applied(), "Stopped rounds cannot spend respawns.");
        eq(new RoundCampaignState.ReconnectResult(null, false, false, false), state.join(B, 100L));
        close(750.0D, state.borderRadius(100_000L));
        state.start(ROSTER, new CampaignRules(MatchMode.LIMITED_LIVES, 3, DragonVictoryMode.LAST_HIT, true, true), 0L);
        eq(3, state.remainingRespawnCredits(2));
        yes(state.tryActivateSilence(A, 0L), "A new round resets reconnaissance cooldowns.");
        yes(state.borderWarningDue(28_800L), "A new round can announce its own border warning.");
        RoundCampaignState disabled = limited();
        yes(!disabled.borderWarningDue(Long.MAX_VALUE), "Disabled shrinking borders never warn.");
        close(750.0D, disabled.borderRadius(Long.MAX_VALUE));
    }

    private static void offlineEngineWinner() {
        TeamLifeBindEngine engine = new TeamLifeBindEngine();
        eq(null, engine.onTeamObjectiveCompleted(1));
        engine.startWithAssignments(ROSTER, new GameOptions(3, HealthPreset.ONE_HEART));
        eq(null, engine.onTeamObjectiveCompleted(99));
        yes(engine.isRunning(), "Invalid objective teams do not stop a match.");
        eq(2, engine.onTeamObjectiveCompleted(2));
        eq(2, engine.winnerTeam());
        yes(!engine.isRunning(), "Team objectives settle without requiring an online player UUID.");
        eq(null, engine.onTeamObjectiveCompleted(1));
        eq(2, engine.winnerTeam());
        engine.stop();
        eq(null, engine.winnerTeam());
    }

    private static void concurrentAuthorizations() throws Exception {
        RoundCampaignState state = limited();
        eq(1, race(() -> state.applyWipe(1, false, 100L).applied()));
        eq(2, state.remainingRespawnCredits(1));
        eq(1, race(() -> state.tryActivateSilence(A, 100L)));
        eq(1, race(() -> state.tryScout(A, "overworld", 0, 64, 0, 100L) != null));
        eq(1, state.activeScouts(100L).size());
    }

    private static int race(BooleanSupplier operation) throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch release = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int index = 0; index < 8; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!release.await(5L, TimeUnit.SECONDS)) throw new AssertionError("Campaign race did not start.");
                    return operation.getAsBoolean();
                }));
            }
            yes(ready.await(5L, TimeUnit.SECONDS), "Concurrent campaign callers must reach the authorization race.");
            release.countDown();
            int accepted = 0;
            for (Future<Boolean> result : results) if (result.get(5L, TimeUnit.SECONDS)) accepted++;
            return accepted;
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static void eq(Object expected, Object actual) {
        assertions++;
        if (!Objects.equals(expected, actual)) throw new AssertionError("Campaign: expected " + expected + " but was " + actual);
    }

    private static void close(double expected, double actual) {
        assertions++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > 0.00001D) {
            throw new AssertionError("Campaign: expected " + expected + " but was " + actual);
        }
    }

    private static void yes(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void expect(Class<? extends Throwable> expected, Runnable operation) {
        assertions++;
        try {
            operation.run();
        } catch (Throwable failure) {
            if (expected.isInstance(failure)) return;
            throw new AssertionError("Expected " + expected.getSimpleName() + " but caught " + failure, failure);
        }
        throw new AssertionError("Expected " + expected.getSimpleName() + " but no exception was thrown.");
    }
}
