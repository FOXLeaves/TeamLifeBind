package com.teamlifebind.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** Ordinary exploration supplies. Spell books always retain the existing natural-chest probability. */
public final class LandmarkLoot {
    private LandmarkLoot() { }

    public record LootItem(String id, int count) {
        public LootItem {
            Objects.requireNonNull(id);
            if (id.isBlank() || count < 1 || count > 64) throw new IllegalArgumentException("Invalid landmark loot");
        }
    }

    public static List<LootItem> roll(LandmarkStructures.LootTier tier, RandomGenerator random) {
        Objects.requireNonNull(tier);
        Objects.requireNonNull(random);
        List<LootItem> result = new ArrayList<>();
        switch (tier) {
            case BASIC -> {
                add(result, "minecraft:bread", 3 + random.nextInt(4));
                add(result, "minecraft:torch", 6 + random.nextInt(7));
                add(result, random.nextBoolean() ? "minecraft:leather" : "minecraft:iron_nugget", 2 + random.nextInt(5));
            }
            case MILITARY -> {
                add(result, "minecraft:iron_ingot", 2 + random.nextInt(4));
                add(result, "minecraft:arrow", 8 + random.nextInt(9));
                add(result, "minecraft:cooked_beef", 2 + random.nextInt(4));
                add(result, random.nextBoolean() ? "minecraft:shield" : "minecraft:crossbow", 1);
            }
            case ARCANE -> {
                add(result, "minecraft:lapis_lazuli", 4 + random.nextInt(7));
                add(result, "minecraft:amethyst_shard", 3 + random.nextInt(6));
                add(result, "tlb:mana_potion", 1 + random.nextInt(2));
                if (random.nextInt(4) == 0) add(result, "minecraft:experience_bottle", 1 + random.nextInt(3));
            }
            case TREASURE -> {
                add(result, "minecraft:gold_ingot", 3 + random.nextInt(4));
                add(result, "minecraft:diamond", 1 + random.nextInt(2));
                add(result, "tlb:greater_mana_potion", 1);
                if (random.nextInt(8) == 0) add(result, "tlb:mana_apple", 1);
                else add(result, "minecraft:golden_apple", 1);
            }
        }
        SpellDefinition book = MagicLootRules.pickNatural(random);
        if (book != null) add(result, "tlb:spell/" + book.id(), 1);
        return List.copyOf(result);
    }

    private static void add(List<LootItem> output, String id, int count) { output.add(new LootItem(id, count)); }
}
