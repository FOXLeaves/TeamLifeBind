package com.teamlifebind.common;

import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** A chest rolls once when its natural loot is generated; adapters must persist that decision. */
public final class MagicLootRules {
    public static final int NATURAL_BOOK_CHANCE = 1_200;
    public static final int WEIGHT_TOTAL = 10_000;
    private static final List<Integer> NATURAL_WEIGHTS = List.of(5_000, 2_600, 1_400, 600, 250, 120, 29, 1);
    private static final List<Integer> AIRDROP_WEIGHTS = List.of(2_300, 2_100, 1_800, 1_500, 1_100, 700, 450, 50);

    private MagicLootRules() { }
    public static List<Integer> naturalWeights() { return NATURAL_WEIGHTS; }
    public static List<Integer> airdropWeights() { return AIRDROP_WEIGHTS; }

    /** Twelve percent of fresh natural chests contain a book; a missed roll returns null. */
    public static SpellDefinition pickNatural(RandomGenerator random) {
        Objects.requireNonNull(random);
        if (random.nextInt(WEIGHT_TOTAL) >= NATURAL_BOOK_CHANCE) return null;
        return pickTier(random, NATURAL_WEIGHTS);
    }

    /** Airdrops always contain one book, with a 0.5% conditional chance of super tier. */
    public static SpellDefinition pickAirdrop(RandomGenerator random) { return pickTier(Objects.requireNonNull(random), AIRDROP_WEIGHTS); }
    public static SpellDefinition pickStarter(RandomGenerator random) {
        List<SpellDefinition> starters = SpellCatalogue.starterPool();
        return starters.get(Objects.requireNonNull(random).nextInt(starters.size()));
    }

    public static double naturalProbability(int tier) {
        return tier < 1 || tier > 8 ? 0D : NATURAL_BOOK_CHANCE / (double) WEIGHT_TOTAL * NATURAL_WEIGHTS.get(tier - 1) / WEIGHT_TOTAL;
    }
    public static double airdropProbability(int tier) {
        return tier < 1 || tier > 8 ? 0D : AIRDROP_WEIGHTS.get(tier - 1) / (double) WEIGHT_TOTAL;
    }

    private static SpellDefinition pickTier(RandomGenerator random, List<Integer> weights) {
        int roll = random.nextInt(WEIGHT_TOTAL);
        int tier = 1;
        for (int weight : weights) {
            if (roll < weight) break;
            roll -= weight;
            tier++;
        }
        List<SpellDefinition> pool = SpellCatalogue.tier(tier);
        return pool.get(random.nextInt(pool.size()));
    }
}
