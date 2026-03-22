package com.teamlifebind.common;

public final class TeamLifeBindCombatMath {

    public static final double SHARED_MAX_HEALTH_CAP = 20.0D;
    private static final int BONUS_HEALTH_LEVEL_INTERVAL = 10;
    private static final double BONUS_HEALTH_PER_INTERVAL = 2.0D;
    private static final double ARMOR_REDUCTION_PER_POINT = 0.01D;
    private static final double TOUGHNESS_REDUCTION_PER_POINT = 0.015D;
    private static final double EXTRA_ARMOR_REDUCTION_CAP = 0.25D;

    private TeamLifeBindCombatMath() {
    }

    public static double sharedMaxHealth(HealthPreset preset, int level) {
        double baseHealth = preset == null ? HealthPreset.ONE_HEART.maxHealth() : preset.maxHealth();
        if (baseHealth >= SHARED_MAX_HEALTH_CAP) {
            return SHARED_MAX_HEALTH_CAP;
        }
        int bonusIntervals = Math.max(0, level) / BONUS_HEALTH_LEVEL_INTERVAL;
        double bonusHealth = bonusIntervals * BONUS_HEALTH_PER_INTERVAL;
        return Math.min(SHARED_MAX_HEALTH_CAP, baseHealth + bonusHealth);
    }

    public static float applyExtraArmorReduction(float damage, double armor, double toughness) {
        if (damage <= 0.0F) {
            return 0.0F;
        }
        double extraReduction = Math.min(
            EXTRA_ARMOR_REDUCTION_CAP,
            Math.max(0.0D, armor) * ARMOR_REDUCTION_PER_POINT + Math.max(0.0D, toughness) * TOUGHNESS_REDUCTION_PER_POINT
        );
        return (float) Math.max(0.0D, damage * (1.0D - extraReduction));
    }

    public static int expToNextLevel(int level) {
        int clampedLevel = Math.max(0, level);
        if (clampedLevel >= 30) {
            return 112 + ((clampedLevel - 30) * 9);
        }
        if (clampedLevel >= 15) {
            return 37 + ((clampedLevel - 15) * 5);
        }
        return 7 + (clampedLevel * 2);
    }

    public static int totalExperienceForLevel(int level) {
        int clampedLevel = Math.max(0, level);
        if (clampedLevel <= 16) {
            return (clampedLevel * clampedLevel) + (6 * clampedLevel);
        }
        if (clampedLevel <= 31) {
            return (int) Math.floor((2.5D * clampedLevel * clampedLevel) - (40.5D * clampedLevel) + 360.0D);
        }
        return (int) Math.floor((4.5D * clampedLevel * clampedLevel) - (162.5D * clampedLevel) + 2220.0D);
    }

    public static ExperienceState resolveExperienceState(int totalExperience) {
        int clampedTotal = Math.max(0, totalExperience);
        int level = 0;
        while (totalExperienceForLevel(level + 1) <= clampedTotal) {
            level++;
        }
        int baseExperience = totalExperienceForLevel(level);
        int intoCurrentLevel = clampedTotal - baseExperience;
        int toNextLevel = expToNextLevel(level);
        float progress = toNextLevel <= 0 ? 0.0F : (float) intoCurrentLevel / (float) toNextLevel;
        return new ExperienceState(clampedTotal, level, intoCurrentLevel, toNextLevel, Math.max(0.0F, Math.min(1.0F, progress)));
    }

    public record ExperienceState(int totalExperience, int level, int intoCurrentLevel, int toNextLevel, float progress) {
    }
}
