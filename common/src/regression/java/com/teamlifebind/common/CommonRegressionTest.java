package com.teamlifebind.common;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dependency-free regression suite, run by the common:regressionTest task. */
public final class CommonRegressionTest {

    private static int assertions;

    private CommonRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        testGameOptions();
        testMatchPace();
        testTotemBalance();
        testTeamTotemCooldown();
        testSupplyDropRules();
        testTeamUtilityMarks();
        testTeamUtilityChannels();
        testTeamUtilityClaimCompetition();
        testTeamUtilityConcurrency();
        testRandomAssignments();
        testAssignmentValidationAndState();
        testExperience();
        testCombatAndTracking();
        assertions += CampaignRegressionTest.run();
        assertions += PlayModeRegressionTest.run();
        assertions += RogueRegressionTest.run();
        assertions += GunRegressionTest.run();
        assertions += GunPresentationRegressionTest.run();
        assertions += MagicRegressionTest.run();
        assertions += LandmarkRegressionTest.run();
        testLanguageFallbacks();
        System.out.println("Shared rules regression tests passed (" + assertions + " assertions).");
    }

    private static void testGameOptions() {
        assertEquals(HealthPreset.ONE_HEART, new GameOptions(2, null).healthPreset());
        assertEquals(32, new GameOptions(32, HealthPreset.ONE_ROW).teamCount());
        assertEquals(1, new GameOptions(1, HealthPreset.ONE_HEART).teamCount());
        assertThrows(IllegalArgumentException.class, () -> new GameOptions(0, HealthPreset.ONE_HEART));
        assertThrows(IllegalArgumentException.class, () -> new GameOptions(33, HealthPreset.ONE_HEART));
        assertEquals(HealthPreset.HALF_ROW, HealthPreset.fromString(" half_row "));
        assertEquals(HealthPreset.ONE_HEART, HealthPreset.fromString("unknown"));
    }

    private static void testMatchPace() {
        assertEquals(MatchPace.CLASSIC, MatchPace.parse(null));
        assertEquals(MatchPace.CLASSIC, MatchPace.parse("unknown"));
        assertEquals(MatchPace.CLASSIC, MatchPace.parse(" "));
        assertEquals(MatchPace.CLASSIC, MatchPace.parse(" cLaSsIc "));
        assertEquals(MatchPace.FAST, MatchPace.parse(" fAsT "));
        assertEquals(MatchPace.FAST, MatchPace.CLASSIC.next());
        assertEquals(MatchPace.CLASSIC, MatchPace.FAST.next());
        assertEquals(18_000L, MatchPace.CLASSIC.autoPortalDelayTicks());
        assertEquals(24_000L, MatchPace.CLASSIC.supplyDropDelayTicks());
        assertEquals(36_000L, MatchPace.CLASSIC.strongholdHintDelayTicks());
        assertEquals(3_600L, MatchPace.CLASSIC.strongholdHintIntervalTicks());
        assertEquals(9_600L, MatchPace.FAST.autoPortalDelayTicks());
        assertEquals(12_000L, MatchPace.FAST.supplyDropDelayTicks());
        assertEquals(18_000L, MatchPace.FAST.strongholdHintDelayTicks());
        assertEquals(2_400L, MatchPace.FAST.strongholdHintIntervalTicks());

        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(MatchPace.CLASSIC, MatchPace.parse("classic"));
            assertEquals(TotemBalance.CLASSIC, TotemBalance.parse("classic"));
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    private static void testTotemBalance() {
        assertEquals(TotemBalance.CLASSIC, TotemBalance.parse(null));
        assertEquals(TotemBalance.CLASSIC, TotemBalance.parse("unknown"));
        assertEquals(TotemBalance.CLASSIC, TotemBalance.parse(" "));
        assertEquals(TotemBalance.CLASSIC, TotemBalance.parse(" cLaSsIc "));
        assertEquals(TotemBalance.BALANCED, TotemBalance.parse(" bAlAnCeD "));
        assertEquals(TotemBalance.BALANCED, TotemBalance.CLASSIC.next());
        assertEquals(TotemBalance.CLASSIC, TotemBalance.BALANCED.next());
        assertEquals(1_400, TotemBalance.CLASSIC.upgradedRegenerationTicks());
        assertEquals(400, TotemBalance.BALANCED.upgradedRegenerationTicks());
        assertEquals(0L, TotemBalance.CLASSIC.teamCooldownTicks());
        assertEquals(900L, TotemBalance.BALANCED.teamCooldownTicks());
        assertClose(20.0D, TotemBalance.CLASSIC.upgradedAbsorption(2.0D, true));
        assertClose(16.0D, TotemBalance.CLASSIC.upgradedAbsorption(2.0D, false));
        assertEquals(3, TotemBalance.CLASSIC.upgradedAbsorptionAmplifier(2.0D));
        assertEquals(3, TotemBalance.CLASSIC.upgradedAbsorptionAmplifier(2.0D, false));
        assertEquals(4, TotemBalance.CLASSIC.upgradedAbsorptionAmplifier(2.0D, true));

        // A one-heart preset must gain only half of its maximum health, not the old 16 HP.
        double[] maximumHealth = {2.0D, 8.0D, 8.01D, 10.0D, 16.0D, 20.0D, Double.MAX_VALUE};
        double[] expectedAbsorption = {1.0D, 4.0D, 4.005D, 5.0D, 8.0D, 8.0D, 8.0D};
        int[] expectedAmplifier = {0, 0, 1, 1, 1, 1, 1};
        for (int index = 0; index < maximumHealth.length; index++) {
            assertClose(expectedAbsorption[index], TotemBalance.BALANCED.upgradedAbsorption(maximumHealth[index], true));
            assertClose(expectedAbsorption[index], TotemBalance.BALANCED.upgradedAbsorption(maximumHealth[index], false));
            assertEquals(expectedAmplifier[index], TotemBalance.BALANCED.upgradedAbsorptionAmplifier(maximumHealth[index]));
        }
        for (double invalidHealth : new double[] {0.0D, -1.0D, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertClose(0.0D, TotemBalance.BALANCED.upgradedAbsorption(invalidHealth, true));
            assertClose(0.0D, TotemBalance.BALANCED.upgradedAbsorption(invalidHealth, false));
            assertEquals(0, TotemBalance.BALANCED.upgradedAbsorptionAmplifier(invalidHealth));
        }
        for (TotemBalance balance : TotemBalance.values()) {
            for (boolean outsideEnd : new boolean[] {false, true}) {
                for (double health : maximumHealth) {
                    double absorption = balance.upgradedAbsorption(health, outsideEnd);
                    int amplifier = balance.upgradedAbsorptionAmplifier(health, outsideEnd);
                    assertTrue(4.0D * (amplifier + 1) >= absorption,
                        "Absorption effect capacity must hold the exact rescue HP in each dimension.");
                    if (balance == TotemBalance.BALANCED) {
                        assertEquals(balance.upgradedAbsorptionAmplifier(health), amplifier);
                    }
                }
            }
        }
    }

    private static void testTeamTotemCooldown() throws Exception {
        TeamTotemCooldown cooldown = new TeamTotemCooldown();
        assertTrue(cooldown.isReady(1, 100L), "New rounds must allow rescue.");
        assertTrue(cooldown.tryAcquire(1, 100L, TotemBalance.CLASSIC.teamCooldownTicks()), "Classic rescues must remain unrestricted.");
        assertTrue(cooldown.tryAcquire(1, 100L, TotemBalance.CLASSIC.teamCooldownTicks()), "Classic allows multiple rescues at the same tick.");
        assertEquals(0L, cooldown.remainingTicks(1, 100L));
        assertTrue(cooldown.tryAcquire(1, 100L, 900L), "First balanced rescue must succeed.");
        assertTrue(!cooldown.tryAcquire(1, 100L, 900L), "One team cannot trigger twice in the same tick.");
        assertEquals(900L, cooldown.remainingTicks(1, 100L));
        assertTrue(!cooldown.isReady(1, 999L), "Cooldown must include its last tick.");
        assertEquals(1L, cooldown.remainingTicks(1, 999L));
        assertTrue(cooldown.tryAcquire(2, 999L, 900L), "Other teams must keep independent cooldowns.");
        assertTrue(cooldown.isReady(1, 1_000L), "Rescue must be ready exactly at the expiry tick.");
        assertEquals(0L, cooldown.remainingTicks(1, 1_000L));
        assertTrue(cooldown.tryAcquire(1, 1_000L, 900L), "A new rescue must start a fresh cooldown.");
        assertTrue(!cooldown.isReady(1, 0L), "Rewinding a clock must not accidentally erase the old cooldown.");
        cooldown.clear();
        assertTrue(cooldown.isReady(1, 0L) && cooldown.isReady(2, 0L), "Round transitions must clear all team cooldowns.");
        assertTrue(cooldown.tryAcquire(1, 0L, -1L), "Nonpositive cooldowns must not retain state.");
        assertEquals(0L, cooldown.remainingTicks(1, 0L));

        // Store elapsed time rather than overflowing a start + duration deadline.
        assertTrue(cooldown.tryAcquire(1, Long.MAX_VALUE, 900L), "A large tick must still accept the initial rescue.");
        assertTrue(!cooldown.tryAcquire(1, Long.MAX_VALUE, 900L), "Deadline overflow must not allow repeated rescue.");
        assertEquals(900L, cooldown.remainingTicks(1, Long.MAX_VALUE));
        cooldown.clear();
        assertTrue(cooldown.tryAcquire(1, Long.MIN_VALUE, Long.MAX_VALUE), "The full tick range must not overflow cooldown storage.");
        assertEquals(1L, cooldown.remainingTicks(1, -2L));
        assertTrue(cooldown.isReady(1, -1L), "The maximum cooldown must expire at the correct tick.");
        assertTrue(cooldown.isReady(1, Long.MAX_VALUE), "A subtraction overflow must still identify an expired cooldown.");
        cooldown.clear();

        int contenders = 8;
        var executor = Executors.newFixedThreadPool(contenders);
        CountDownLatch waiting = new CountDownLatch(contenders);
        CountDownLatch release = new CountDownLatch(1);
        List<Future<Boolean>> acquisitions = new ArrayList<>();
        try {
            for (int index = 0; index < contenders; index++) {
                acquisitions.add(executor.submit(() -> {
                    waiting.countDown();
                    if (!release.await(5L, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to race team rescue.");
                    }
                    return cooldown.tryAcquire(7, 100L, 900L);
                }));
            }
            assertTrue(waiting.await(5L, TimeUnit.SECONDS), "Concurrent rescue contenders must start.");
            release.countDown();
            int accepted = 0;
            for (Future<Boolean> acquisition : acquisitions) {
                if (acquisition.get(5L, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }
            assertEquals(1, accepted);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static void testSupplyDropRules() {
        assertEquals(1_200L, SupplyDropRules.ANNOUNCEMENT_LEAD_TICKS);
        assertEquals(160L, SupplyDropRules.CLAIM_TICKS);
        assertClose(3.0D, SupplyDropRules.CLAIM_RADIUS_BLOCKS);
        for (int disabledCount : new int[] {Integer.MIN_VALUE, -1, 0}) {
            for (boolean dynamic : new boolean[] {false, true}) {
                assertEquals(0, SupplyDropRules.resolveCount(disabledCount, dynamic, 8));
            }
        }
        int[] teams = {Integer.MIN_VALUE, -1, 0, 1, 2, 3, 7, 8, 9, Integer.MAX_VALUE};
        int[] counts = {2, 2, 2, 2, 2, 3, 7, 8, 8, 8};
        for (int index = 0; index < teams.length; index++) {
            assertEquals(counts[index], SupplyDropRules.resolveCount(1, true, teams[index]));
            assertEquals(counts[index], TeamUtilityState.supplyCountForTeams(teams[index]));
            assertEquals(13, SupplyDropRules.resolveCount(13, false, teams[index]));
        }
        assertEquals(Integer.MAX_VALUE, SupplyDropRules.resolveCount(Integer.MAX_VALUE, false, 1));
        for (long eventDelay : new long[] {Long.MIN_VALUE, -1L, 0L, 1L, 1_199L, 1_200L}) {
            assertEquals(0L, SupplyDropRules.announcementDelayTicks(eventDelay));
        }
        assertEquals(1L, SupplyDropRules.announcementDelayTicks(1_201L));
        assertEquals(10_800L, SupplyDropRules.announcementDelayTicks(MatchPace.FAST.supplyDropDelayTicks()));
        assertEquals(22_800L, SupplyDropRules.announcementDelayTicks(MatchPace.CLASSIC.supplyDropDelayTicks()));
        assertEquals(Long.MAX_VALUE - 1_200L, SupplyDropRules.announcementDelayTicks(Long.MAX_VALUE));
    }

    private static void testTeamUtilityMarks() {
        TeamUtilityState state = new TeamUtilityState();
        assertClose(64.0D, TeamUtilityState.WHISTLE_RANGE_BLOCKS);
        assertEquals(240L, TeamUtilityState.WHISTLE_MARK_TICKS);
        assertEquals(300L, TeamUtilityState.WHISTLE_COOLDOWN_TICKS);
        assertEquals(null, state.markForTeam(1, 0L));
        assertEquals(0L, TeamUtilityState.remainingMarkTicks(null, 0L));
        for (int invalidTeam : new int[] {-1, 0}) {
            assertEquals(null, state.tryMark(invalidTeam, "overworld", 1, 2, 3, 0L));
        }
        for (String invalidDimension : new String[] {null, "", " "}) {
            assertEquals(null, state.tryMark(1, invalidDimension, 1, 2, 3, 0L));
        }
        for (double invalidCoordinate : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertEquals(null, state.tryMark(1, "overworld", invalidCoordinate, 2, 3, 0L));
            assertEquals(null, state.tryMark(1, "overworld", 1, invalidCoordinate, 3, 0L));
            assertEquals(null, state.tryMark(1, "overworld", 1, 2, invalidCoordinate, 0L));
        }
        assertEquals(0L, state.whistleCooldownRemainingTicks(1, 0L));
        TeamUtilityState.TeamMark mark = state.tryMark(1, "nether", -12.5D, 64.5D, 13.0D, 100L);
        assertTrue(mark != null, "A valid mark must be accepted after invalid attempts.");
        assertEquals(1, mark.team());
        assertEquals("nether", mark.dimension());
        assertClose(-12.5D, mark.x());
        assertClose(64.5D, mark.y());
        assertClose(13.0D, mark.z());
        assertEquals(mark, state.markForTeam(1, 339L));
        assertEquals(1L, TeamUtilityState.remainingMarkTicks(mark, 339L));
        assertEquals(240L, TeamUtilityState.remainingMarkTicks(mark, 99L));
        assertEquals(null, state.tryMark(1, "end", 0, 0, 0, 339L));
        assertTrue(state.tryMark(2, "end", 0, 0, 0, 339L) != null, "Other teams can mark independently.");
        assertEquals(null, state.markForTeam(1, 340L));
        assertEquals(60L, state.whistleCooldownRemainingTicks(1, 340L));
        assertEquals(null, state.tryMark(1, "end", 0, 0, 0, 399L));
        assertTrue(state.tryMark(1, "end", 0, 0, 0, 400L) != null, "Mark cooldown expires exactly at 15s.");
        state.clear();
        assertEquals(null, state.markForTeam(1, 400L));
        assertEquals(null, state.markForTeam(2, 400L));
        assertEquals(0L, state.whistleCooldownRemainingTicks(1, 0L));
        assertTrue(state.tryMark(1, "overworld", 0, 0, 0, Long.MAX_VALUE) != null, "Mark deadlines must not overflow.");
        assertEquals(300L, state.whistleCooldownRemainingTicks(1, Long.MAX_VALUE));
        assertEquals(240L, TeamUtilityState.remainingMarkTicks(state.markForTeam(1, Long.MAX_VALUE), Long.MAX_VALUE));
        state.clear();
        TeamUtilityState.TeamMark ancient = state.tryMark(1, "overworld", 0, 0, 0, Long.MIN_VALUE);
        assertEquals(0L, TeamUtilityState.remainingMarkTicks(ancient, Long.MAX_VALUE));
        assertEquals(null, state.markForTeam(1, Long.MAX_VALUE));
    }

    private static void testTeamUtilityChannels() {
        TeamUtilityState state = new TeamUtilityState();
        UUID player = new UUID(1L, 1L);
        UUID teammate = new UUID(1L, 2L);
        UUID enemy = new UUID(2L, 1L);
        assertEquals(80L, TeamUtilityState.BANDAGE_CHANNEL_TICKS);
        assertEquals(600L, TeamUtilityState.BANDAGE_COOLDOWN_TICKS);
        assertClose(2.0D, TeamUtilityState.BANDAGE_HEAL_HP);
        assertEquals(SupplyDropRules.CLAIM_TICKS, TeamUtilityState.SUPPLY_CLAIM_TICKS);
        assertEquals(null, state.startBandage(null, 1, "overworld", "MAIN_HAND:0", 0L));
        assertEquals(null, state.startBandage(player, 0, "overworld", "MAIN_HAND:0", 0L));
        assertEquals(null, state.startBandage(player, 1, null, "MAIN_HAND:0", 0L));
        assertEquals(null, state.startBandage(player, 1, " ", "MAIN_HAND:0", 0L));
        for (String invalidSlot : new String[] {null, "", " "}) {
            assertEquals(null, state.startBandage(player, 1, "overworld", invalidSlot, 0L));
        }
        assertEquals(0L, TeamUtilityState.remainingChannelTicks(null, 0L));
        assertTrue(!state.cancel(player), "Cancelling absent channels is a no-op.");
        assertTrue(!state.cancel((TeamUtilityState.Channel) null), "A missing callback cannot cancel anything.");
        assertTrue(!state.completeBandage(null, 80L), "A missing callback cannot complete anything.");

        TeamUtilityState.Channel bandage = state.startBandage(player, 1, "overworld", "MAIN_HAND:0", 100L);
        assertTrue(bandage != null, "Bandage starts after invalid attempts without spending cooldown.");
        assertEquals(TeamUtilityState.ChannelKind.BANDAGE, bandage.kind());
        assertEquals("MAIN_HAND:0", bandage.slotSignature());
        assertEquals("", bandage.dropId());
        assertTrue(state.channelForPlayer(player) == bandage, "Adapters must obtain the original callback identity.");
        assertTrue(state.channelForTeam(1, TeamUtilityState.ChannelKind.BANDAGE) == bandage, "Team channel indexes must agree.");
        assertEquals(null, state.startBandage(teammate, 1, "nether", "OFF_HAND:40", 100L));
        assertEquals(null, state.startSupplyClaim(player, 1, "overworld", "crate-a", 100L));
        TeamUtilityState.Channel claim = state.startSupplyClaim(teammate, 1, "overworld", "crate-a", 100L);
        assertTrue(claim != null, "A different teammate may do the other kind of channel.");
        TeamUtilityState.Channel enemyBandage = state.startBandage(enemy, 2, "end", "MAIN_HAND:0", 100L);
        assertTrue(enemyBandage != null, "Bandage channel locks are independent between teams.");
        List<TeamUtilityState.Channel> snapshot = state.channels();
        assertEquals(3, snapshot.size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
        assertEquals(1L, TeamUtilityState.remainingChannelTicks(bandage, 179L));
        assertEquals(80L, TeamUtilityState.remainingChannelTicks(bandage, 99L));
        assertTrue(!state.completeBandage(bandage, 179L), "Bandage cannot complete before four seconds.");
        assertTrue(!state.completeBandage(claim, 260L), "A supply callback cannot spend bandage cooldown.");
        assertTrue(!state.completeSupplyClaim(bandage, 260L), "A bandage callback cannot unlock a crate.");
        TeamUtilityState.Channel copied = copyChannel(bandage);
        assertEquals(bandage, copied);
        assertTrue(!state.cancel(copied), "Equal but forged callbacks cannot cancel the original.");
        assertTrue(!state.completeBandage(copied, 180L), "Equal but forged callbacks cannot complete the original.");
        assertTrue(state.completeBandage(bandage, 180L), "Bandage completes at exactly four seconds.");
        assertEquals(null, state.channelForPlayer(player));
        assertEquals(null, state.channelForTeam(1, TeamUtilityState.ChannelKind.BANDAGE));
        assertEquals(600L, state.bandageCooldownRemainingTicks(1, 180L));
        assertTrue(!state.completeBandage(bandage, 180L), "Completed callbacks cannot heal twice.");
        assertEquals(3, snapshot.size());
        assertEquals(2, state.channels().size());
        assertEquals(null, state.startBandage(player, 1, "overworld", "MAIN_HAND:0", 779L));
        TeamUtilityState.Channel retry = state.startBandage(player, 1, "overworld", "OFF_HAND:40", 780L);
        assertTrue(retry != null, "Team bandage cooldown expires exactly at 30s.");
        assertTrue(state.cancel(retry), "Adapters can interrupt valid channels without spending cooldown.");
        assertEquals(0L, state.bandageCooldownRemainingTicks(1, 780L));
        assertTrue(state.channelForPlayer(teammate) == claim, "Cancelling a bandage preserves the same team's supply claim.");
        assertTrue(state.cancel(enemy), "Player-id interruption releases both channel indexes.");
        assertEquals(null, state.channelForTeam(2, TeamUtilityState.ChannelKind.BANDAGE));
        assertTrue(state.startBandage(enemy, 2, "end", "MAIN_HAND:0", 180L) != null, "Cancellation never starts a cooldown.");
        state.clear();
        assertEquals(0, state.channels().size());
        assertEquals(0L, state.bandageCooldownRemainingTicks(1, 0L));

        TeamUtilityState.Channel oldRound = state.startBandage(player, 1, "overworld", "MAIN_HAND:0", 0L);
        state.clear();
        TeamUtilityState.Channel newRound = state.startBandage(player, 1, "overworld", "MAIN_HAND:0", 0L);
        assertEquals(oldRound, newRound);
        assertTrue(!state.cancel(oldRound), "An old-round callback cannot cancel identical new-round state.");
        assertTrue(!state.completeBandage(oldRound, 80L), "An old-round callback cannot heal in a new round.");
        assertTrue(state.completeBandage(newRound, 80L), "A stale callback must leave the live channel usable.");
        state.clear();
        TeamUtilityState.Channel nearOverflow = state.startBandage(player, 1, "overworld", "MAIN_HAND:0", Long.MAX_VALUE);
        assertTrue(!state.completeBandage(nearOverflow, Long.MAX_VALUE), "A deadline addition overflow must not complete early.");
        assertEquals(80L, TeamUtilityState.remainingChannelTicks(nearOverflow, Long.MAX_VALUE));
        state.clear();
        TeamUtilityState.Channel ancient = state.startBandage(player, 1, "overworld", "MAIN_HAND:0", Long.MIN_VALUE);
        assertTrue(state.completeBandage(ancient, Long.MAX_VALUE), "Elapsed subtraction overflow still denotes a completed channel.");
    }

    private static void testTeamUtilityClaimCompetition() {
        TeamUtilityState state = new TeamUtilityState();
        UUID one = new UUID(3L, 1L);
        UUID two = new UUID(3L, 2L);
        UUID three = new UUID(3L, 3L);
        assertTrue(!state.completeSupplyClaim(null, 160L), "Missing claims cannot unlock supplies.");
        for (String invalidDrop : new String[] {null, "", " "}) {
            assertEquals(null, state.startSupplyClaim(one, 1, "overworld", invalidDrop, 0L));
        }
        assertEquals(null, state.startSupplyClaim(null, 1, "overworld", "crate-a", 0L));
        assertEquals(null, state.startSupplyClaim(one, 0, "overworld", "crate-a", 0L));
        assertEquals(null, state.startSupplyClaim(one, 1, "", "crate-a", 0L));
        TeamUtilityState.Channel first = state.startSupplyClaim(one, 1, "overworld", "crate-a", 100L);
        assertTrue(first != null, "Valid claims start after invalid attempts.");
        assertEquals(null, state.startSupplyClaim(two, 1, "overworld", "crate-b", 100L));
        TeamUtilityState.Channel competitor = state.startSupplyClaim(two, 2, "overworld", "crate-a", 100L);
        TeamUtilityState.Channel unrelated = state.startSupplyClaim(three, 3, "nether", "crate-b", 100L);
        assertTrue(competitor != null, "Different teams can contest the same locked crate.");
        assertTrue(unrelated != null, "Different crates can be unlocked independently.");
        assertEquals(1L, TeamUtilityState.remainingChannelTicks(first, 259L));
        assertTrue(!state.completeSupplyClaim(first, 259L), "Supplies cannot unlock before eight seconds.");
        assertTrue(!state.completeSupplyClaim(copyChannel(first), 260L), "Forged equal claim callbacks cannot win.");
        assertTrue(state.completeSupplyClaim(first, 260L), "The first completed claimant unlocks supplies.");
        assertTrue(state.isSupplyClaimed("crate-a"), "Completed supply state is retained for the rest of the round.");
        assertTrue(!state.isSupplyClaimed("crate-b"), "A different drop remains locked.");
        assertEquals(null, state.channelForPlayer(one));
        assertEquals(null, state.channelForPlayer(two));
        assertEquals(null, state.channelForTeam(2, TeamUtilityState.ChannelKind.SUPPLY_CLAIM));
        assertTrue(state.channelForPlayer(three) == unrelated, "Unlocking clears contenders only for that drop.");
        assertTrue(!state.completeSupplyClaim(competitor, 260L), "A losing claimant cannot authorize filling twice.");
        assertTrue(!state.completeSupplyClaim(first, 260L), "A winning callback also cannot be reused.");
        assertEquals(null, state.startSupplyClaim(two, 2, "overworld", "crate-a", 260L));
        assertTrue(state.startSupplyClaim(two, 2, "nether", "crate-c", 260L) != null, "Losing claimants can immediately contest a different crate.");
        state.clear();
        assertTrue(!state.isSupplyClaimed("crate-a"), "Round cleanup resets all drop ownership.");
        assertEquals(0, state.channels().size());
        TeamUtilityState.Channel nextRound = state.startSupplyClaim(one, 1, "overworld", "crate-a", 100L);
        assertEquals(first, nextRound);
        assertTrue(!state.completeSupplyClaim(first, 260L), "Old-round supply callbacks cannot unlock reused drop IDs.");
        assertTrue(!state.cancel(first), "Old-round supply callbacks cannot cancel reused drop IDs.");
        assertTrue(state.completeSupplyClaim(nextRound, 260L), "New-round claimants still win normally.");
    }

    private static void testTeamUtilityConcurrency() throws Exception {
        TeamUtilityState state = new TeamUtilityState();
        int contenders = 8;
        List<TeamUtilityState.TeamMark> marks = race(contenders,
            index -> state.tryMark(1, "overworld", index, 1, 2, 0L));
        assertEquals(1L, marks.stream().filter(Objects::nonNull).count());
        assertEquals(300L, state.whistleCooldownRemainingTicks(1, 0L));
        List<TeamUtilityState.Channel> bandages = race(contenders,
            index -> state.startBandage(new UUID(4L, index), 1, "overworld", "MAIN_HAND:0", 0L));
        assertEquals(1L, bandages.stream().filter(Objects::nonNull).count());
        TeamUtilityState.Channel bandage = bandages.stream().filter(Objects::nonNull).findFirst().orElseThrow();
        assertEquals(1L, race(contenders, index -> state.completeBandage(bandage, 80L)).stream().filter(Boolean::booleanValue).count());
        assertEquals(600L, state.bandageCooldownRemainingTicks(1, 80L));
        assertEquals(0, state.channels().size());
        state.clear();
        List<TeamUtilityState.Channel> claims = race(contenders,
            index -> state.startSupplyClaim(new UUID(5L, index), index + 1, "overworld", "contested", 0L));
        assertEquals((long) contenders, claims.stream().filter(Objects::nonNull).count());
        assertEquals(1L, race(contenders, index -> state.completeSupplyClaim(claims.get(index), 160L)).stream().filter(Boolean::booleanValue).count());
        assertEquals(0, state.channels().size());
        assertTrue(state.isSupplyClaimed("contested"), "Concurrent unlocks must retain exactly one completed drop.");
        for (int index = 0; index < contenders; index++) {
            assertEquals(null, state.channelForTeam(index + 1, TeamUtilityState.ChannelKind.SUPPLY_CLAIM));
        }
        state.clear();
        TeamUtilityState.Channel interrupted = state.startBandage(new UUID(6L, 1L), 1, "overworld", "MAIN_HAND:0", 0L);
        List<Boolean> interruptionRace = race(2, index -> index == 0
            ? state.cancel(interrupted) : state.completeBandage(interrupted, 80L));
        assertEquals(1L, interruptionRace.stream().filter(Boolean::booleanValue).count());
        assertEquals(0, state.channels().size());
        assertTrue(state.bandageCooldownRemainingTicks(1, 80L) == (interruptionRace.get(1) ? 600L : 0L),
            "A cancellation race may spend cooldown only when completion wins.");
    }

    private static TeamUtilityState.Channel copyChannel(TeamUtilityState.Channel channel) {
        return new TeamUtilityState.Channel(channel.playerId(), channel.team(), channel.dimension(), channel.kind(),
            channel.slotSignature(), channel.dropId(), channel.startTick());
    }

    private static <T> List<T> race(int count, java.util.function.IntFunction<T> action) throws Exception {
        var executor = Executors.newFixedThreadPool(count);
        CountDownLatch waiting = new CountDownLatch(count);
        CountDownLatch release = new CountDownLatch(1);
        List<Future<T>> attempts = new ArrayList<>();
        try {
            for (int index = 0; index < count; index++) {
                final int contender = index;
                attempts.add(executor.submit(() -> {
                    waiting.countDown();
                    if (!release.await(5L, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting for concurrent utility attempts.");
                    }
                    return action.apply(contender);
                }));
            }
            assertTrue(waiting.await(5L, TimeUnit.SECONDS), "All utility contenders must reach the starting barrier.");
            release.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> attempt : attempts) {
                results.add(attempt.get(5L, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static void testRandomAssignments() {
        List<UUID> players = players(11);
        List<UUID> repeatedPlayers = new ArrayList<>(players);
        repeatedPlayers.addAll(players);
        GameOptions options = new GameOptions(3, HealthPreset.ONE_HEART);
        TeamLifeBindEngine engine = new TeamLifeBindEngine(new Random(1234L));
        StartResult result = engine.start(repeatedPlayers, options);
        assertEquals(Set.of(1, 2, 3), result.activeTeams());
        assertEquals(new HashSet<>(players), result.teamByPlayer().keySet());
        assertEquals(result.teamByPlayer(), new TeamLifeBindEngine(new Random(1234L)).start(repeatedPlayers, options).teamByPlayer());

        int minimumTeamSize = Integer.MAX_VALUE;
        int maximumTeamSize = 0;
        for (int team : result.activeTeams()) {
            int teamSize = 0;
            for (int assignedTeam : result.teamByPlayer().values()) {
                if (assignedTeam == team) {
                    teamSize++;
                }
            }
            minimumTeamSize = Math.min(minimumTeamSize, teamSize);
            maximumTeamSize = Math.max(maximumTeamSize, teamSize);
        }
        assertTrue(maximumTeamSize - minimumTeamSize <= 1, "Random teams must stay balanced.");
        assertThrows(UnsupportedOperationException.class, () -> result.teamByPlayer().put(UUID.randomUUID(), 1));
        assertThrows(UnsupportedOperationException.class, () -> result.activeTeams().add(4));
    }

    private static void testAssignmentValidationAndState() {
        List<UUID> players = players(4);
        GameOptions options = new GameOptions(2, HealthPreset.ONE_HEART);
        Map<UUID, Integer> input = new HashMap<>();
        input.put(players.get(0), 1);
        input.put(players.get(1), 1);
        input.put(players.get(2), 2);
        input.put(players.get(3), 2);
        TeamLifeBindEngine engine = new TeamLifeBindEngine(new Random(42L));
        StartResult result = engine.startWithAssignments(input, options);
        Map<UUID, Integer> snapshot = engine.assignments();
        input.clear();
        assertEquals(result.teamByPlayer(), snapshot);
        assertEquals(4, snapshot.size());
        assertThrows(IllegalArgumentException.class, () -> engine.start(Arrays.asList(players.get(0), null, players.get(1)), options));
        assertThrows(IllegalArgumentException.class, () -> engine.start(players, null));
        assertThrows(IllegalArgumentException.class, () -> engine.start(List.of(players.get(0), players.get(0)), options));
        assertThrows(IllegalArgumentException.class, () -> engine.startWithAssignments(Map.of(players.get(0), 0, players.get(1), 2), options));
        assertThrows(IllegalArgumentException.class, () -> engine.startWithAssignments(Map.of(players.get(0), 1, players.get(1), 3), options));
        assertThrows(IllegalArgumentException.class, () -> engine.startWithAssignments(Map.of(players.get(0), 1), options));
        Map<UUID, Integer> nullAssignment = new HashMap<>();
        nullAssignment.put(players.get(0), null);
        assertThrows(IllegalArgumentException.class, () -> engine.startWithAssignments(nullAssignment, options));
        assertEquals(snapshot, engine.assignments());
        assertTrue(engine.isRunning(), "Rejected starts must preserve a running match.");
        assertEquals(2, engine.configuredTeamCount());

        DeathResult death = engine.onPlayerDeath(players.get(0));
        assertTrue(death.triggered(), "A participant death must trigger shared death.");
        assertEquals(1, death.team());
        assertEquals(Set.of(players.get(0), players.get(1)), new HashSet<>(death.victims()));
        assertThrows(UnsupportedOperationException.class, () -> death.victims().clear());
        assertEquals(snapshot, engine.assignments());
        assertTrue(engine.isRunning(), "Shared death must keep the existing respawn contract.");
        assertEquals(DeathResult.none(), engine.onPlayerDeath(UUID.randomUUID()));
        assertEquals(DeathResult.none(), engine.onPlayerDeath(null));
        assertEquals(null, engine.teamForPlayer(null));
        assertEquals(null, engine.onObjectiveCompleted(UUID.randomUUID()));
        assertEquals(null, engine.onObjectiveCompleted(null));
        assertEquals(2, engine.onObjectiveCompleted(players.get(2)));
        assertEquals(2, engine.winnerTeam());
        assertTrue(!engine.isRunning(), "A completed objective must stop the match.");
        assertEquals(null, engine.onObjectiveCompleted(players.get(0)));
        assertEquals(2, engine.winnerTeam());
        assertEquals(DeathResult.none(), engine.onPlayerDeath(players.get(0)));

        engine.start(players, options);
        assertEquals(null, engine.winnerTeam());
        engine.stop();
        assertEquals(Map.of(), engine.assignments());
        assertEquals(0, engine.configuredTeamCount());
        assertEquals(null, engine.winnerTeam());
        assertEquals(DeathResult.none(), engine.onPlayerDeath(players.get(0)));
        assertEquals(result.teamByPlayer(), snapshot);
    }

    private static void testExperience() {
        assertEquals(0, TeamLifeBindCombatMath.totalExperienceForLevel(-1));
        assertEquals(7, TeamLifeBindCombatMath.expToNextLevel(-1));
        assertEquals(0, TeamLifeBindCombatMath.resolveExperienceState(-10).totalExperience());
        assertEquals(0, TeamLifeBindCombatMath.clampTotalExperience(Long.MIN_VALUE));
        assertEquals(Integer.MAX_VALUE, TeamLifeBindCombatMath.clampTotalExperience(Long.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, TeamLifeBindCombatMath.clampTotalExperience((long) Integer.MAX_VALUE + 1L));
        assertEquals(Integer.MAX_VALUE, TeamLifeBindCombatMath.totalExperienceForLevel(Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, TeamLifeBindCombatMath.expToNextLevel(Integer.MAX_VALUE));

        int accumulatedExperience = 0;
        for (int level = 0; level <= 500; level++) {
            int toNextLevel = referenceExpToNextLevel(level);
            assertEquals(accumulatedExperience, TeamLifeBindCombatMath.totalExperienceForLevel(level));
            assertEquals(toNextLevel, TeamLifeBindCombatMath.expToNextLevel(level));
            TeamLifeBindCombatMath.ExperienceState atStart = TeamLifeBindCombatMath.resolveExperienceState(accumulatedExperience);
            assertEquals(level, atStart.level());
            assertEquals(0, atStart.intoCurrentLevel());
            assertClose(0.0D, atStart.progress());
            TeamLifeBindCombatMath.ExperienceState atEnd = TeamLifeBindCombatMath.resolveExperienceState(accumulatedExperience + toNextLevel - 1);
            assertEquals(level, atEnd.level());
            assertEquals(toNextLevel - 1, atEnd.intoCurrentLevel());
            assertTrue(atEnd.progress() >= 0.0F && atEnd.progress() < 1.0F, "Progress must remain inside the current level.");
            accumulatedExperience += toNextLevel;
        }

        // This input previously never returned because saturated int thresholds stayed <= MAX_VALUE.
        int expectedLevel = 0;
        long expectedBase = 0L;
        while (expectedBase + referenceExpToNextLevel(expectedLevel) <= Integer.MAX_VALUE) {
            expectedBase += referenceExpToNextLevel(expectedLevel++);
        }
        TeamLifeBindCombatMath.ExperienceState maximum = TeamLifeBindCombatMath.resolveExperienceState(Integer.MAX_VALUE);
        assertEquals(expectedLevel, maximum.level());
        assertEquals((int) (Integer.MAX_VALUE - expectedBase), maximum.intoCurrentLevel());
        assertEquals(referenceExpToNextLevel(expectedLevel), maximum.toNextLevel());
        assertTrue(maximum.progress() >= 0.0F && maximum.progress() < 1.0F, "Maximum experience must resolve to a finite level.");
    }

    private static int referenceExpToNextLevel(int level) {
        if (level < 15) {
            return 7 + 2 * level;
        }
        return level < 30 ? 5 * level - 38 : 9 * level - 158;
    }

    private static void testCombatAndTracking() {
        assertClose(2.0D, TeamLifeBindCombatMath.sharedMaxHealth(null, -10));
        assertClose(4.0D, TeamLifeBindCombatMath.sharedMaxHealth(HealthPreset.ONE_HEART, 10));
        assertClose(12.0D, TeamLifeBindCombatMath.sharedMaxHealth(HealthPreset.HALF_ROW, 10));
        assertClose(20.0D, TeamLifeBindCombatMath.sharedMaxHealth(HealthPreset.ONE_HEART, Integer.MAX_VALUE));
        assertClose(20.0D, TeamLifeBindCombatMath.sharedMaxHealth(HealthPreset.ONE_ROW, 0));
        assertEquals(2, TeamLifeBindCombatMath.presetResistanceAmplifier(HealthPreset.ONE_HEART, -1L));
        assertEquals(2, TeamLifeBindCombatMath.presetResistanceAmplifier(HealthPreset.ONE_HEART, 11_999L));
        assertEquals(1, TeamLifeBindCombatMath.presetResistanceAmplifier(HealthPreset.ONE_HEART, 12_000L));
        assertEquals(0, TeamLifeBindCombatMath.presetResistanceAmplifier(HealthPreset.ONE_HEART, 24_000L));
        assertEquals(-1, TeamLifeBindCombatMath.presetResistanceAmplifier(HealthPreset.ONE_HEART, 36_000L));
        assertEquals(-1, TeamLifeBindCombatMath.presetResistanceAmplifier(HealthPreset.HALF_ROW, 24_000L));
        assertEquals(-1, TeamLifeBindCombatMath.presetResistanceAmplifier(null));
        assertEquals(-1, TeamLifeBindCombatMath.presetResistanceAmplifier(HealthPreset.ONE_ROW));
        assertClose(0.0D, TeamLifeBindCombatMath.applyExtraArmorReduction(-1.0F, 10.0D, 0.0D));
        assertClose(100.0D, TeamLifeBindCombatMath.applyExtraArmorReduction(100.0F, -1.0D, -1.0D));
        assertClose(90.0D, TeamLifeBindCombatMath.applyExtraArmorReduction(100.0F, 10.0D, 0.0D));
        assertClose(75.0D, TeamLifeBindCombatMath.applyExtraArmorReduction(100.0F, 20.0D, 8.0D));
        assertEquals("\u2022", TeamLifeBindCombatMath.trackingArrow(0.0D, 4.0D, 8.0D, 4.0D, 8.0D));
        assertEquals("\u2191", TeamLifeBindCombatMath.trackingArrow(0.0D, 0.0D, 0.0D, 0.0D, 1.0D));
        assertEquals("\u2192", TeamLifeBindCombatMath.trackingArrow(0.0D, 0.0D, 0.0D, -1.0D, 0.0D));
        assertEquals("\u2190", TeamLifeBindCombatMath.trackingArrow(0.0D, 0.0D, 0.0D, 1.0D, 0.0D));
        assertEquals("\u2193", TeamLifeBindCombatMath.trackingArrow(0.0D, 0.0D, 0.0D, 0.0D, -1.0D));
        assertEquals("\u2191", TeamLifeBindCombatMath.trackingArrow(90.0D, 0.0D, 0.0D, -1.0D, 0.0D));
        assertEquals("\u2191", TeamLifeBindCombatMath.trackingArrow(-90.0D, 0.0D, 0.0D, 1.0D, 0.0D));
        assertEquals("\u2191", TeamLifeBindCombatMath.trackingArrow(720.0D, 0.0D, 0.0D, 0.0D, 1.0D));
    }

    private static void testLanguageFallbacks() throws IOException {
        testBundledLanguageParity();
        Path directory = Files.createTempDirectory("teamlifebind-language-test-");
        Locale originalLocale = Locale.getDefault();
        try {
            List<String> warnings = new ArrayList<>();
            TeamLifeBindLanguage initial = TeamLifeBindLanguage.load(directory, warnings::add);
            assertEquals("Team #3", initial.teamLabel(3));
            assertEquals("[missing.key]", initial.text("missing.key"));
            assertTrue(warnings.isEmpty(), "Bundled languages must load without warnings.");
            testPresetTranslationContracts(initial);
            testCampaignTranslationContracts(initial);
            testGunTranslationContracts(initial);
            testPresetTranslationContracts(TeamLifeBindLanguage.load(directory, "zh_cn", warnings::add));
            testCampaignTranslationContracts(TeamLifeBindLanguage.load(directory, "zh_cn", warnings::add));
            testGunTranslationContracts(TeamLifeBindLanguage.load(directory, "zh_cn", warnings::add));
            Files.writeString(directory.resolve("lang/fi.properties"), "team.label=Custom {0}\n", StandardCharsets.UTF_8);
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("Custom 3", TeamLifeBindLanguage.load(directory, "FI", warnings::add).teamLabel(3));
            assertEquals("enabled", TeamLifeBindLanguage.load(directory, "fi", warnings::add).state(true));
            TeamLifeBindLanguage customLanguage = TeamLifeBindLanguage.load(directory, "fi", warnings::add);
            assertEquals("Emergency Bandage", customLanguage.text("item.emergency_bandage.name"));
            assertEquals("Resonance Whistle", customLanguage.text("item.resonance_whistle.name"));
            assertEquals("team.label=Custom {0}\n", Files.readString(directory.resolve("lang/fi.properties"), StandardCharsets.UTF_8));

            // Malformed Unicode escapes are a Properties parse error, even with UTF-8 readers.
            Files.writeString(directory.resolve("lang/fi.properties"), "team.label=" + "\\" + "uZZZZ\n", StandardCharsets.UTF_8);
            assertEquals("Team #3", TeamLifeBindLanguage.load(directory, "fi", warnings::add).teamLabel(3));
            assertTrue(warnings.stream().anyMatch(warning -> warning.contains("Failed to parse language file")), "Malformed overrides must report an error and keep fallback text.");
            Files.writeString(directory.resolve("language.properties"), "language=" + "\\" + "uZZZZ\n", StandardCharsets.UTF_8);
            assertEquals("Team #3", TeamLifeBindLanguage.load(directory, warnings::add).teamLabel(3));
            assertTrue(warnings.stream().anyMatch(warning -> warning.contains("Failed to read language config")), "Malformed selectors must preserve the default language.");

            Files.writeString(directory.resolve("outside.properties"), "team.label=Outside {0}\n", StandardCharsets.UTF_8);
            assertEquals("Team #3", TeamLifeBindLanguage.load(directory, "../outside", warnings::add).teamLabel(3));
            assertEquals("Team #3", TeamLifeBindLanguage.load(directory, "does_not_exist", warnings::add).teamLabel(3));
        } finally {
            Locale.setDefault(originalLocale);
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void testPresetTranslationContracts(TeamLifeBindLanguage language) {
        Map<Integer, List<String>> keysByArgumentCount = Map.of(
            0, List.of("pace.classic", "pace.fast", "totem_balance.classic", "totem_balance.balanced",
                "command.help.rules", "menu.button.rules", "menu.button.pace", "menu.button.totem_balance",
                "config.presets.running", "rules.title", "rules.win", "rules.death", "rules.bed",
                "rules.totem.balanced", "rules.totem.classic", "item.resonance_whistle.name",
                "item.resonance_whistle.lore", "item.emergency_bandage.name", "item.emergency_bandage.lore",
                "utility.match_only", "utility.team_required", "utility.channel_busy", "utility.cancelled",
                "utility.bandage.full_health", "supply.claim.complete", "supply.claim.locked",
                "supply.claim.too_far", "supply.claim.claimed", "rules.utilities", "menu.button.supply_dynamic", "book.recipes.page3"),
            1, List.of("menu.value.pace", "menu.value.totem_balance", "config.pace.updated",
                "config.totem_balance.updated", "rules.norespawn", "rules.shared", "rules.end_totems", "totem.cooldown",
                "utility.cooldown", "utility.bandage.started", "utility.bandage.progress", "utility.bandage.complete",
                "supply.claim.started", "supply.claim.progress", "match.finale.preview", "rules.supply.dynamic",
                "rules.supply.fixed", "config.supply.dynamic.updated", "menu.value.supply_dynamic"),
            2, List.of("supply.preview"),
            3, List.of("rules.events", "rules.totem", "item.death_exemption_totem.balance_lore", "supply.preview.location"),
            4, List.of("match.rules.summary", "utility.whistle.mark"),
            5, List.of("rules.pace", "utility.whistle.other_dimension", "utility.whistle.actionbar.other_dimension"),
            6, List.of("utility.whistle.actionbar")
        );
        for (var group : keysByArgumentCount.entrySet()) {
            Object[] arguments = new Object[group.getKey()];
            for (int index = 0; index < arguments.length; index++) {
                arguments[index] = "value" + index;
            }
            for (String key : group.getValue()) {
                String template = language.text(key);
                assertTrue(!template.equals("[" + key + "]"), "Missing preset translation: " + key);
                for (int index = 0; index < arguments.length; index++) {
                    assertTrue(template.contains("{" + index + "}"), "Missing parameter " + index + " in translation " + key);
                }
                String formatted = language.text(key, arguments);
                assertTrue(!formatted.matches("(?s).*\\{\\d+\\}.*"), "Unexpanded preset parameter: " + key);
            }
        }
    }

    private static void testCampaignTranslationContracts(TeamLifeBindLanguage language) {
        Map<Integer, List<String>> contracts = Map.of(
            0, List.of("item.silent_sigil.name", "item.silent_sigil.lore", "item.scout_rocket.name", "item.scout_rocket.lore",
                "campaign.mode.dragon_hunt", "campaign.mode.limited_lives", "campaign.dragon.last_hit", "campaign.dragon.contribution",
                "rules.reconnect", "rules.recon", "rules.point", "rules.border", "rules.win.limited", "rules.win.contribution",
                "campaign.win_reason.last_team", "campaign.win_reason.dragon_contribution", "campaign.win_reason.dragon",
                "campaign.draw", "campaign.reconnect.pending", "campaign.silence.interference", "campaign.scout.none",
                "campaign.point.contested", "campaign.point.empty", "campaign.point.disabled", "campaign.point.no_supply",
                "campaign.task.capture_point", "campaign.task.claim_supply", "campaign.task.enter_end", "campaign.border.started",
                "campaign.bed.none", "campaign.bed.destroyed", "campaign.potion.none", "campaign.potion.milk_cleared",
                "campaign.potion.name.resonance", "campaign.potion.name.disconnect", "campaign.potion.lore.resonance",
                "campaign.potion.lore.disconnect", "config.campaign.active_round", "menu.campaign.title", "menu.button.campaign",
                "menu.button.match_mode", "menu.button.dragon_victory", "menu.button.team_lives", "menu.button.control_point",
                "menu.button.border", "book.recipes.page4", "command.help.campaign", "command.help.campaign_options"),
            1, List.of("rules.death.limited", "campaign.lives.eliminated", "campaign.lives.hud", "campaign.victory",
                "campaign.reconnect.safe", "campaign.reconnect.returned", "campaign.reconnect.expired", "campaign.silence.active",
                "campaign.scout.started", "campaign.scout.hud", "campaign.scout.none_hud", "campaign.point.reward",
                "campaign.border.warning", "campaign.border.hud", "campaign.bed.other_dimension", "campaign.potion.resonance",
                "campaign.potion.disconnect", "campaign.potion.hud", "menu.value.match_mode", "menu.value.dragon_victory",
                "menu.value.team_lives", "menu.value.control_point", "menu.value.border", "campaign.config.invalid"),
            2, List.of("campaign.lives.remaining", "campaign.reconnect.combat", "campaign.silence.used", "campaign.task.complete",
                "campaign.bed.ready", "campaign.bed.hud", "campaign.bed.active", "config.campaign.updated"),
            3, List.of("campaign.scout.target", "campaign.scout.target_hud", "campaign.scout.warning_hud", "campaign.point.hud"),
            4, List.of("campaign.scout.warning", "campaign.point.location"),
            5, List.of("rules.campaign"),
            6, List.of("match.rules.summary.campaign")
        );
        for (var group : contracts.entrySet()) {
            Object[] args = new Object[group.getKey()];
            for (int index = 0; index < args.length; index++) args[index] = "value" + index;
            for (String key : group.getValue()) {
                String template = language.text(key);
                assertTrue(!template.equals("[" + key + "]"), "Missing campaign translation: " + key);
                Set<String> expected = new HashSet<>();
                for (int index = 0; index < args.length; index++) expected.add("{" + index + "}");
                assertEquals(expected, placeholders(template));
                assertTrue(placeholders(language.text(key, args)).isEmpty(), "Unexpanded campaign translation: " + key);
            }
        }
        assertTrue(language.text("book.recipes.page4").contains("\n"), "Recipe page four must contain usable line breaks.");
        assertTrue(!language.text("book.recipes.page4").contains("\\n"), "Recipe page four must not show literal backslash-n text.");
    }

    private static void testGunTranslationContracts(TeamLifeBindLanguage language) {
        Map<Integer, List<String>> contracts = Map.of(
            0, List.of("gun.airdrop_only", "gun.infinite_reserve", "gun.aiming", "gun.match_only", "gun.unavailable",
                "gun.invalid", "gun.limit_reached", "gun.weapon_busy", "gun.reload.not_needed",
                "dev.menu.gun.test", "dev.menu.info.guns"),
            1, List.of("gun.reload.started", "gun.controls", "rules.guns", "book.weapons.page1",
                "gun.controls.mod", "rules.guns.mod", "book.weapons.page1.mod"),
            2, List.of("gun.hud.reload", "gun.hud.overheated", "gun.hud.windup", "gun.hud.cooldown", "gun.hud.feed_ready"),
            3, List.of("gun.hud.ready")
        );
        for (var group : contracts.entrySet()) {
            Object[] args = new Object[group.getKey()];
            Set<String> expected = new HashSet<>();
            for (int index = 0; index < args.length; index++) { args[index] = "value" + index; expected.add("{" + index + "}"); }
            for (String key : group.getValue()) {
                String template = language.text(key);
                assertTrue(!template.equals("[" + key + "]"), "Missing weapon translation: " + key);
                assertEquals(expected, placeholders(template));
                assertTrue(placeholders(language.text(key, args)).isEmpty(), "Unexpanded weapon translation: " + key);
            }
        }
        for (GunProfile profile : GunProfile.values()) {
            String nameKey = "item." + profile.id() + ".name";
            String loreKey = "item." + profile.id() + ".lore";
            assertTrue(!language.text(nameKey).equals("[" + nameKey + "]"), "Missing gun name: " + nameKey);
            assertTrue(language.text(loreKey).contains("\n"), "Gun lore must split into usable lines.");
            assertTrue(!language.text(loreKey).contains("\\n"), "Gun lore must not show literal backslash-n.");
            assertEquals(Set.of("{0}"), placeholders(language.text(loreKey)));
            for (String reloadKey : List.of("R", "F")) {
                String lore = language.text(loreKey, reloadKey);
                assertTrue(placeholders(lore).isEmpty() && lore.contains(reloadKey), "Gun lore must name its platform reload key.");
            }
            String nativeName = language.text("item." + profile.id() + ".native_name");
            String nativeLoreKey = "item." + profile.id() + ".native_lore";
            assertTrue(!nativeName.startsWith("[item."), "Every native replica has a recognizable name.");
            assertEquals(profile.usesHeat() ? Set.of() : Set.of("{0}"), placeholders(language.text(nativeLoreKey)));
            assertTrue(placeholders(language.text(nativeLoreKey, "R")).isEmpty(), "Native gun lore expands its reload control.");
            assertTrue(language.text(nativeLoreKey, "R").contains("\n"), "Native lore supplies actual separate tooltip lines.");
        }
        assertTrue(language.text("book.weapons.page1").contains("\n"), "Weapon book page uses actual line breaks.");
    }

    private static void testBundledLanguageParity() throws IOException {
        Properties english = bundledProperties("en_us");
        Properties chinese = bundledProperties("zh_cn");
        assertEquals(english.stringPropertyNames(), chinese.stringPropertyNames());
        for (String key : english.stringPropertyNames()) {
            boolean optionalSubtitle = key.equals("match.notice.start.subtitle");
            assertTrue(optionalSubtitle || !english.getProperty(key).isBlank(), "Empty English translation: " + key);
            assertTrue(optionalSubtitle || !chinese.getProperty(key).isBlank(), "Empty Chinese translation: " + key);
            // Spell definitions carry both languages. These templates deliberately select the
            // appropriate field instead of displaying both names/descriptions in every tooltip.
            if (Set.of("magic.book.name", "magic.spell.name", "magic.book.description").contains(key)) {
                assertEquals(Set.of("{1}"), placeholders(english.getProperty(key)));
                assertEquals(Set.of("{0}"), placeholders(chinese.getProperty(key)));
            } else {
                assertTrue(placeholders(english.getProperty(key)).equals(placeholders(chinese.getProperty(key))),
                    "English/Chinese parameter mismatch: " + key);
            }
        }
    }

    private static Properties bundledProperties(String code) throws IOException {
        Properties result = new Properties();
        try (var stream = CommonRegressionTest.class.getClassLoader().getResourceAsStream("teamlifebind/lang/" + code + ".properties")) {
            if (stream == null) throw new AssertionError("Missing bundled language " + code);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                result.load(reader);
            }
        }
        return result;
    }

    private static Set<String> placeholders(String text) {
        Matcher matcher = Pattern.compile("\\{\\d+\\}").matcher(text);
        Set<String> placeholders = new HashSet<>();
        while (matcher.find()) placeholders.add(matcher.group());
        return placeholders;
    }

    private static List<UUID> players(int count) {
        List<UUID> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(new UUID(0L, index + 1L));
        }
        return result;
    }

    private static void assertEquals(Object expected, Object actual) {
        assertions++;
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }

    private static void assertClose(double expected, double actual) {
        assertions++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > 0.00001D) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertThrows(Class<? extends Throwable> expected, Runnable action) {
        assertions++;
        try {
            action.run();
        } catch (Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return;
            }
            throw new AssertionError("Expected " + expected.getSimpleName() + " but caught " + throwable, throwable);
        }
        throw new AssertionError("Expected " + expected.getSimpleName() + " but no exception was thrown.");
    }
}
