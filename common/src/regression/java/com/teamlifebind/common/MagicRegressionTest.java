package com.teamlifebind.common;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.random.RandomGenerator;

/** Public gameplay contracts, probability boundaries, and paid-cast cancellation regression. */
public final class MagicRegressionTest {
    private static int assertions;
    private MagicRegressionTest() { }

    public static int run() throws Exception {
        assertions = 0;
        assertions += SpellBehaviourRegressionTest.run();
        assertions += SpellIdentityRegressionTest.run();
        catalogue();
        lootBoundaries();
        learnAndSelection();
        manaAndTime();
        secondaryCostsAndAcceleration();
        paidChannelAndCancellation();
        allSpellsAreCastable();
        int beforeSaves = assertions;
        savesAndRestore();
        System.out.println("Magic knowledge save/restore scenarios passed (" + (assertions - beforeSaves) + " assertions).");
        capacityAndConcurrency();
        System.out.println("Magic catalogue, loot and state scenarios passed (" + assertions + " assertions).");
        return assertions;
    }

    private static void catalogue() {
        equal(300, SpellCatalogue.all().size());
        equal(280L, SpellCatalogue.all().stream().filter(spell -> !spell.superTier()).count());
        equal(20L, SpellCatalogue.all().stream().filter(SpellDefinition::superTier).count());
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        Set<String> englishNames = new HashSet<>();
        Set<String> fingerprints = new HashSet<>();
        for (SpellDefinition spell : SpellCatalogue.all()) {
            truth(ids.add(spell.id()));
            truth(names.add(spell.zhName()));
            truth(englishNames.add(spell.enName()));
            truth(fingerprints.add(spell.mechanicFingerprint()));
            equal(spell, SpellCatalogue.byId(spell.id()));
            truth(spell.manaCost() <= MagicState.MAX_MANA);
            truth(spell.zhDescription().length() > 20);
            truth(spell.enDescription().length() > 40);
            truth(spell.castTicks() > 0);
            truth(spell.damage() > 0);
            if (spell.superTier()) {
                truth(spell.mechanics().size() >= 3);
                truth(spell.manaCost() > MagicState.INITIAL_MANA);
            }
            fails(() -> spell.mechanics().clear());
        }
        for (MagicSchool school : MagicSchool.values()) {
            List<SpellDefinition> spells = SpellCatalogue.all().stream().filter(spell -> spell.school() == school && !spell.superTier()).toList();
            equal(20, spells.size());
            equal(Set.of(MagicMechanic.values()), new HashSet<>(spells.stream().map(SpellDefinition::primaryMechanic).toList()));
            truth(!school.zhEffect().isBlank());
        }
        for (int tier = 1; tier < 8; tier++) {
            truth(!SpellCatalogue.tier(tier).isEmpty());
            final int highTier = tier + 1;
            double strongest = SpellCatalogue.tier(tier).stream().mapToDouble(SpellDefinition::damage).max().orElseThrow();
            truth(SpellCatalogue.tier(highTier).stream().allMatch(spell -> spell.damage() > strongest));
        }
        equal(28, SpellCatalogue.starterPool().size());
        truth(SpellCatalogue.starterPool().stream().allMatch(spell -> spell.tier() == 1));
        equal(List.of(), SpellCatalogue.tier(0));
        equal(null, SpellCatalogue.byId(null));
        equal(null, SpellCatalogue.byId("not_a_spell"));
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            equal(SpellCatalogue.byId("fire_bolt"), SpellCatalogue.byId(" FIRE_BOLT "));
        } finally { Locale.setDefault(previous); }
        fails(() -> SpellCatalogue.all().clear());
        fails(() -> SpellCatalogue.starterPool().clear());
        SpellDefinition example = SpellCatalogue.all().getFirst();
        fails(() -> new SpellDefinition(example.id(), example.zhName(), example.enName(), example.school(),
            9, 1, 1, 0, 1, 1, 1, List.of(MagicMechanic.BOLT), "description", "description"));
        fails(() -> new SpellDefinition(example.id(), example.zhName(), example.enName(), example.school(),
            1, 301, 1, 0, 1, 1, 1, List.of(MagicMechanic.BOLT), "description", "description"));
        fails(() -> new SpellDefinition(example.id(), example.zhName(), example.enName(), example.school(),
            1, 1, 1, 0, Double.NaN, 1, 1, List.of(MagicMechanic.BOLT), "description", "description"));
        fails(() -> new SpellDefinition(example.id(), example.zhName(), example.enName(), example.school(),
            1, 1, 1, 0, 1, 1, 1, List.of(MagicMechanic.BOLT, MagicMechanic.BOLT), "description", "description"));
    }

    private static void lootBoundaries() {
        equal(10_000, MagicLootRules.naturalWeights().stream().mapToInt(Integer::intValue).sum());
        equal(10_000, MagicLootRules.airdropWeights().stream().mapToInt(Integer::intValue).sum());
        equal(null, MagicLootRules.pickNatural(new Rolls(1_200)));
        equal(null, MagicLootRules.pickNatural(new Rolls(9_999)));
        equal(1, MagicLootRules.pickNatural(new Rolls(1_199, 0, 0)).tier());
        int natural = 0;
        int airdrop = 0;
        for (int tier = 1; tier <= 8; tier++) {
            int nw = MagicLootRules.naturalWeights().get(tier - 1);
            int aw = MagicLootRules.airdropWeights().get(tier - 1);
            equal(tier, MagicLootRules.pickNatural(new Rolls(0, natural, 0)).tier());
            equal(tier, MagicLootRules.pickNatural(new Rolls(0, natural + nw - 1, 0)).tier());
            equal(tier, MagicLootRules.pickAirdrop(new Rolls(airdrop, 0)).tier());
            equal(tier, MagicLootRules.pickAirdrop(new Rolls(airdrop + aw - 1, 0)).tier());
            natural += nw;
            airdrop += aw;
            if (tier > 1) {
                truth(MagicLootRules.naturalProbability(tier) < MagicLootRules.naturalProbability(tier - 1));
                truth(MagicLootRules.airdropProbability(tier) < MagicLootRules.airdropProbability(tier - 1));
            }
            if (tier >= 3) truth(MagicLootRules.airdropProbability(tier) > MagicLootRules.naturalProbability(tier));
        }
        near(0.000012, MagicLootRules.naturalProbability(8));
        near(0.005, MagicLootRules.airdropProbability(8));
        truth(MagicLootRules.airdropProbability(8) > MagicLootRules.naturalProbability(8));
        near(0, MagicLootRules.naturalProbability(9));
        near(0, MagicLootRules.airdropProbability(0));
        fails(() -> MagicLootRules.naturalWeights().clear());
        Random random = new Random(873465L);
        int naturalBooks = 0;
        for (int index = 0; index < 20_000; index++) {
            SpellDefinition spell = MagicLootRules.pickNatural(random);
            if (spell != null) naturalBooks++;
            truth(MagicLootRules.pickAirdrop(random) != null);
            equal(1, MagicLootRules.pickStarter(random).tier());
        }
        truth(naturalBooks > 2_200 && naturalBooks < 2_600);
    }

    private static void learnAndSelection() {
        MagicState state = new MagicState();
        UUID player = UUID.randomUUID();
        equal(MagicState.Reason.UNKNOWN_SPELL, state.learn(player, "bogus").reason());
        equal(0, state.playerCount());
        equal(MagicState.Reason.INVALID_PLAYER, state.learn(null, "fire_bolt").reason());
        equal(MagicState.Reason.NOT_LEARNED, state.tryCast(player, "fire_bolt", 0).reason());
        truth(state.learn(player, "fire_bolt").accepted());
        equal("fire_bolt", state.selected(player));
        equal(MagicState.Reason.ALREADY_KNOWN, state.learn(player, "fire_bolt").reason());
        truth(state.learn(player, "ice_beam").accepted());
        truth(state.learn(player, "lightning_chain").accepted());
        equal(MagicState.Reason.MAX_LEARNED, state.learn(player, "water_bolt").reason());
        equal(List.of("fire_bolt", "ice_beam", "lightning_chain"), state.learned(player));
        truth(!state.selectSlot(player, -1));
        truth(!state.selectSlot(player, 3));
        truth(state.selectSlot(player, 2));
        equal("lightning_chain", state.selected(player));
        truth(state.forget(player, 1));
        equal(1, state.snapshot(player, 0).selectedSlot());
        equal("lightning_chain", state.selected(player));
        truth(state.learn(player, "water_bolt").accepted());
        truth(!state.forget(player, 3));
        fails(() -> state.learned(player).clear());
        fails(() -> state.snapshot(player, 0).learned().clear());
        List<String> oldSnapshot = state.snapshot(player, 0).learned();
        state.forget(player, 0);
        equal(3, oldSnapshot.size());
    }

    private static void manaAndTime() {
        MagicState state = new MagicState();
        UUID player = UUID.randomUUID();
        state.learn(player, "fire_bolt");
        state.learn(player, "water_bolt");
        SpellDefinition bolt = SpellCatalogue.byId("fire_bolt");
        equal(100, state.mana(player, 0));
        equal(MagicState.Reason.CAST_READY, state.tryCast(player, bolt.id(), 0).reason());
        equal(100 - bolt.manaCost(), state.mana(player, 0));
        equal(MagicState.Reason.DUPLICATE_TICK, state.tryCast(player, "water_bolt", 0).reason());
        equal(MagicState.Reason.COOLDOWN, state.tryCast(player, bolt.id(), 1).reason());
        equal((long) bolt.cooldownTicks() - 1, state.cooldownRemaining(player, bolt.id(), 1));
        equal(100 - bolt.manaCost(), state.mana(player, 79));
        equal(101 - bolt.manaCost(), state.mana(player, 80));
        equal(102 - bolt.manaCost(), state.mana(player, 100));
        equal(102 - bolt.manaCost(), state.mana(player, 80));
        equal(MagicState.Reason.CLOCK_REWIND, state.tryCast(player, "water_bolt", 80).reason());
        equal(0, state.restoreMana(player, 30, 80));
        equal(18, state.restoreMana(player, Integer.MAX_VALUE, 100));
        equal(0, state.restoreMana(player, 1, 100));
        truth(state.tryCast(player, bolt.id(), 101).accepted());
        equal(80, state.mana(player, 101));
        equal(20, state.restoreMana(player, 30, 101));
        for (int index = 0; index < 20; index++) truth(state.increaseMaxMana(player, 102));
        equal(300, state.maxMana(player));
        equal(300, state.mana(player, 102));
        truth(!state.increaseMaxMana(player, 102));
        truth(!state.increaseMaxMana(player, 101));
        equal(0, state.restoreMana(null, 30, 102));
        equal(0, state.restoreMana(player, -1, 102));

        MagicState high = new MagicState();
        high.learn(player, "apocalypse_lotus");
        equal(MagicState.Reason.NO_MANA, high.beginCast(player, "apocalypse_lotus", 0).reason());
        equal(100, high.mana(player, 0));
        for (int index = 0; index < 6; index++) truth(high.increaseMaxMana(player, 0));
        truth(high.beginCast(player, "apocalypse_lotus", 0).accepted());
        equal(0, high.mana(player, 0));
        equal(MagicState.Reason.ALREADY_CASTING, high.tryCast(player, "apocalypse_lotus", 1).reason());

        MagicState extreme = new MagicState();
        extreme.learn(player, "fire_bolt");
        truth(extreme.tryCast(player, "fire_bolt", Long.MAX_VALUE - 1).accepted());
        equal(1L, extreme.cooldownRemaining(player, "fire_bolt", Long.MAX_VALUE - 1));
        equal(0L, extreme.cooldownRemaining(player, "fire_bolt", Long.MAX_VALUE));
        equal(80, extreme.mana(player, Long.MAX_VALUE));
    }

    private static void paidChannelAndCancellation() {
        MagicState state = new MagicState();
        UUID player = UUID.randomUUID();
        state.learn(player, "fire_bolt");
        state.learn(player, "water_bolt");
        MagicState.CastResult started = state.beginCast(player, "fire_bolt", 10);
        equal(MagicState.Reason.CAST_STARTED, started.reason());
        truth(started.token() != null);
        equal(80, state.mana(player, 10));
        equal(MagicState.Reason.NOT_READY, state.completeCast(player, started.token(), started.token().readyAt() - 1).reason());
        equal(MagicState.Reason.ALREADY_CASTING, state.beginCast(player, "water_bolt", 15).reason());
        truth(state.completeCast(player, started.token(), started.token().readyAt()).accepted());
        equal(MagicState.Reason.CANCELLED, state.completeCast(player, started.token(), 20).reason());
        equal(80, state.mana(player, 20));
        MagicState.CastResult second = state.beginCast(player, "water_bolt", 21);
        truth(second.accepted());
        state.cancel(player);
        equal(60, state.mana(player, 21));
        equal(MagicState.Reason.CANCELLED, state.completeCast(player, second.token(), 100).reason());
        truth(state.cooldownRemaining(player, "water_bolt", 21) > 0);
        MagicState.CastResult third = state.beginCast(player, "fire_bolt", 100);
        truth(third.accepted());
        equal(MagicState.Reason.INELIGIBLE, state.beginCast(player, "water_bolt", 101, false).reason());
        equal(MagicState.Reason.CANCELLED, state.completeCast(player, third.token(), 150).reason());
        truth(state.beginCast(player, "water_bolt", 200).accepted());
        MagicState.CastToken pending = state.beginCast(player, "fire_bolt", 201).token();
        state.forget(player, 1);
        equal(MagicState.Reason.CANCELLED, state.completeCast(player, 220).reason());
        truth(pending != null); // rejected attempts report the current token, which remains bounded.
        state.resetAll();
        equal(List.of(), state.learned(player));
        equal(100, state.mana(player, 300));
        equal(MagicState.Reason.CANCELLED, state.completeCast(player, third.token(), 300).reason());
    }

    private static void secondaryCostsAndAcceleration() {
        MagicState state = new MagicState();
        UUID player = UUID.randomUUID();
        UUID victim = UUID.randomUUID();
        state.learn(player, "fire_bolt");
        truth(!state.spendMana(victim, 1, 0));
        truth(!state.spendMana(player, -1, 0));
        truth(!state.spendMana(player, 101, 0));
        equal(100, state.mana(player, 0));
        truth(state.spendMana(player, 10, 0));
        equal(90, state.mana(player, 0));
        equal(90, state.mana(player, 79));
        equal(91, state.mana(player, 80));
        truth(!state.spendMana(player, 1, 79));
        equal(91, state.mana(player, 80));
        equal(100, state.drainMana(victim, Integer.MAX_VALUE, 0));
        equal(0, state.mana(victim, 0));
        equal(0, state.drainMana(victim, 1, 0));
        equal(0, state.drainMana(null, 1, 0));
        equal(1, state.mana(victim, 80));
        equal(0, state.drainMana(victim, 1, 79));
        truth(state.tryCast(player, "fire_bolt", 81).accepted());
        state.accelerateCooldowns(player, Integer.MAX_VALUE, 81);
        equal(0L, state.cooldownRemaining(player, "fire_bolt", 81));
        equal(MagicState.Reason.DUPLICATE_TICK, state.tryCast(player, "fire_bolt", 81).reason());
        truth(state.tryCast(player, "fire_bolt", 82).accepted());
        long previous = state.cooldownRemaining(player, "fire_bolt", 82);
        state.accelerateCooldowns(player, 5, 81);
        equal(previous, state.cooldownRemaining(player, "fire_bolt", 82));
        state.accelerateCooldowns(player, 5, 82);
        equal(previous - 5, state.cooldownRemaining(player, "fire_bolt", 82));
    }

    private static void allSpellsAreCastable() {
        for (SpellDefinition spell : SpellCatalogue.all()) {
            MagicState state = new MagicState();
            UUID player = UUID.randomUUID();
            truth(state.learn(player, spell.id()).accepted());
            for (int count = 0; count < 20; count++) state.increaseMaxMana(player, 0);
            MagicState.CastResult cast = state.beginCast(player, spell.id(), 0);
            truth(cast.accepted());
            equal(spell, cast.spell());
            equal(300 - spell.manaCost(), state.mana(player, 0));
            equal((long) spell.castTicks(), cast.token().readyAt());
            truth(state.completeCast(player, cast.token(), cast.token().readyAt()).accepted());
            equal(MagicState.Reason.CANCELLED, state.completeCast(player, cast.token(), cast.token().readyAt()).reason());
        }
    }

    private static void capacityAndConcurrency() throws Exception {
        MagicState state = new MagicState();
        for (int index = 0; index < MagicState.MAX_PLAYERS; index++) truth(state.learn(new UUID(0, index), "fire_bolt").accepted());
        equal(MagicState.MAX_PLAYERS, state.playerCount());
        equal(MagicState.Reason.LIMIT_REACHED, state.learn(new UUID(1, 1), "fire_bolt").reason());
        state.remove(new UUID(0, 0));
        truth(state.learn(new UUID(1, 1), "fire_bolt").accepted());
        state.clearSession();
        equal(0, state.playerCount());

        UUID shared = UUID.randomUUID();
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<Boolean>> learns = new ArrayList<>();
            for (SpellDefinition spell : SpellCatalogue.all()) learns.add(() -> state.learn(shared, spell.id()).accepted());
            long succeeded = 0;
            for (var future : executor.invokeAll(learns)) if (future.get()) succeeded++;
            equal(3L, succeeded);
            equal(3, state.learned(shared).size());
            String selected = state.selected(shared);
            for (int index = 0; index < 20; index++) state.increaseMaxMana(shared, 0);
            List<Callable<Boolean>> casts = new ArrayList<>();
            for (int index = 0; index < 100; index++) casts.add(() -> state.tryCast(shared, selected, 0).accepted());
            long authorized = 0;
            for (var future : executor.invokeAll(casts)) if (future.get()) authorized++;
            equal(1L, authorized);
            equal(300 - SpellCatalogue.byId(selected).manaCost(), state.mana(shared, 0));
        }
    }

    private static void savesAndRestore() {
        MagicState original = new MagicState();
        UUID player = UUID.randomUUID();
        original.learn(player, "fire_bolt");
        original.learn(player, "ice_beam");
        original.learn(player, "lightning_chain");
        original.selectSlot(player, 2);
        for (int index = 0; index < 10; index++) original.increaseMaxMana(player, 0);
        MagicState.CastResult cast = original.beginCast(player, "fire_bolt", 0);
        original.cancel(player);
        MagicState.SavedPlayer saved = original.exportPlayer(player, 1);
        equal(List.of("fire_bolt", "ice_beam", "lightning_chain"), saved.learned());
        equal(2, saved.selectedSlot());
        equal(180, saved.mana());
        equal(200, saved.maxMana());
        equal(27L, saved.cooldownRemaining().get("fire_bolt"));
        fails(() -> saved.learned().clear());
        fails(() -> saved.cooldownRemaining().clear());
        MagicState.SavedPlayer exploration = saved.withoutManaGrowth();
        equal(100, exploration.maxMana());
        equal(100, exploration.mana());
        equal(saved.learned(), exploration.learned());
        equal(saved.selectedSlot(), exploration.selectedSlot());
        equal(saved.cooldownRemaining(), exploration.cooldownRemaining());
        equal(200, saved.maxMana());
        MagicState.SavedPlayer spent = new MagicState.SavedPlayer(saved.learned(), 2, 63, 200, saved.cooldownRemaining()).withoutManaGrowth();
        equal(63, spent.mana());
        equal(100, spent.maxMana());
        equal(spent, spent.withoutManaGrowth());
        MagicState explorationState = new MagicState();
        truth(explorationState.restorePlayer(player, spent, 1));
        equal(63, explorationState.mana(player, 1));
        equal(100, explorationState.maxMana(player));
        equal(27L, explorationState.cooldownRemaining(player, "fire_bolt", 1));
        truth(!original.restorePlayer(player, saved, 100));
        MagicState restored = new MagicState();
        truth(restored.restorePlayer(player, saved, 100));
        equal(original.learned(player), restored.learned(player));
        equal("lightning_chain", restored.selected(player));
        equal(180, restored.mana(player, 100));
        equal(200, restored.maxMana(player));
        equal(27L, restored.cooldownRemaining(player, "fire_bolt", 100));
        equal(MagicState.Reason.COOLDOWN, restored.tryCast(player, "fire_bolt", 100).reason());
        equal(MagicState.Reason.CANCELLED, restored.completeCast(player, cast.token(), 100).reason());
        truth(restored.tryCast(player, "fire_bolt", 127).accepted());
        truth(restored.forget(player, 0));
        MagicState.SavedPlayer forgottenCooldown = restored.exportPlayer(player, 128);
        truth(forgottenCooldown.cooldownRemaining().containsKey("fire_bolt"));
        restored.remove(player);
        truth(restored.restorePlayer(player, forgottenCooldown, 200));
        truth(restored.learn(player, "fire_bolt").accepted());
        equal(MagicState.Reason.COOLDOWN, restored.tryCast(player, "fire_bolt", 200).reason());
        equal(Map.of(), original.exportPlayer(player, 1000).cooldownRemaining());
        equal(100, original.exportPlayer(UUID.randomUUID(), 1000).mana());

        List<MagicState.SavedPlayer> invalid = List.of(
            new MagicState.SavedPlayer(List.of("fire_bolt", "ice_bolt", "water_bolt", "light_bolt"), 0, 100, 100, Map.of()),
            new MagicState.SavedPlayer(List.of("not_a_spell"), 0, 100, 100, Map.of()),
            new MagicState.SavedPlayer(List.of(" FIRE_BOLT "), 0, 100, 100, Map.of()),
            new MagicState.SavedPlayer(List.of("fire_bolt", "fire_bolt"), 0, 100, 100, Map.of()),
            new MagicState.SavedPlayer(List.of("fire_bolt"), 1, 100, 100, Map.of()),
            new MagicState.SavedPlayer(List.of(), 1, 100, 100, Map.of()),
            new MagicState.SavedPlayer(List.of("fire_bolt"), -1, 100, 100, Map.of()),
            new MagicState.SavedPlayer(List.of("fire_bolt"), 0, -1, 100, Map.of()),
            new MagicState.SavedPlayer(List.of("fire_bolt"), 0, 101, 100, Map.of()),
            new MagicState.SavedPlayer(List.of("fire_bolt"), 0, 50, 90, Map.of()),
            new MagicState.SavedPlayer(List.of("fire_bolt"), 0, 50, 310, Map.of()),
            new MagicState.SavedPlayer(List.of("fire_bolt"), 0, 50, 101, Map.of()),
            new MagicState.SavedPlayer(List.of("fire_bolt"), 0, 50, 100, Map.of("not_a_spell", 1L)),
            new MagicState.SavedPlayer(List.of("fire_bolt"), 0, 50, 100, Map.of("fire_bolt", -1L)),
            new MagicState.SavedPlayer(List.of("fire_bolt"), 0, 50, 100, Map.of("fire_bolt", 29L))
        );
        MagicState empty = new MagicState();
        for (MagicState.SavedPlayer malformed : invalid) {
            truth(!empty.restorePlayer(player, malformed, 0));
            equal(0, empty.playerCount());
        }
        truth(!empty.restorePlayer(null, saved, 0));
        truth(!empty.restorePlayer(player, null, 0));
        truth(!empty.restorePlayer(player, saved, -1));
        for (int index = 0; index < MagicState.MAX_PLAYERS; index++) truth(empty.restorePlayer(new UUID(0, index), saved, 0));
        truth(!empty.restorePlayer(new UUID(1, 0), saved, 0));
        equal(MagicState.MAX_PLAYERS, empty.playerCount());
    }

    private static void truth(boolean value) {
        assertions++;
        if (!value) throw new AssertionError("Magic assertion failed at " + assertions);
    }
    private static void equal(Object expected, Object actual) {
        assertions++;
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
    private static void near(double expected, double actual) {
        assertions++;
        if (Math.abs(expected - actual) > 1e-10) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
    private static void fails(Runnable task) {
        assertions++;
        try { task.run(); } catch (IllegalArgumentException | UnsupportedOperationException expected) { return; }
        throw new AssertionError("Expected rejection");
    }
    private static final class Rolls implements RandomGenerator {
        private final int[] values;
        private int index;
        Rolls(int... values) { this.values = values; }
        @Override public long nextLong() { throw new AssertionError("Unexpected random primitive"); }
        @Override public int nextInt(int bound) {
            if (index == values.length) throw new AssertionError("Unexpected reroll");
            int value = values[index++];
            if (value < 0 || value >= bound) throw new AssertionError("Out of range random fixture");
            return value;
        }
    }
}
