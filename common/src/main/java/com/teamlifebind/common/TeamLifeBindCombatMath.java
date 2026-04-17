package com.teamlifebind.common;

public final class TeamLifeBindCombatMath {

    public static final double SHARED_MAX_HEALTH_CAP = 20.0D;
    private static final int BONUS_HEALTH_LEVEL_INTERVAL = 10;
    private static final double BONUS_HEALTH_PER_INTERVAL = 2.0D;
    private static final double ARMOR_REDUCTION_PER_POINT = 0.01D;
    private static final double TOUGHNESS_REDUCTION_PER_POINT = 0.015D;
    private static final double EXTRA_ARMOR_REDUCTION_CAP = 0.25D;
    private static final long PRESET_RESISTANCE_DECAY_INTERVAL_TICKS = 12_000L;
    private static final long PRESET_RESISTANCE_EXPIRE_TICKS = 36_000L;

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

    public static int presetResistanceAmplifier(HealthPreset preset) {
        return presetResistanceAmplifier(preset, 0L);
    }

    public static int presetResistanceAmplifier(HealthPreset preset, long elapsedTicks) {
        if (preset == null) {
            return -1;
        }
        int baseAmplifier = switch (preset) {
            case ONE_HEART -> 2;
            case HALF_ROW -> 0;
            case ONE_ROW -> -1;
        };
        if (baseAmplifier < 0) {
            return -1;
        }
        long clampedElapsedTicks = Math.max(0L, elapsedTicks);
        if (clampedElapsedTicks >= PRESET_RESISTANCE_EXPIRE_TICKS) {
            return -1;
        }
        int decaySteps = (int) (clampedElapsedTicks / PRESET_RESISTANCE_DECAY_INTERVAL_TICKS);
        return baseAmplifier - decaySteps;
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

    public static String trackingArrow(double viewerYawDegrees, double viewerX, double viewerZ, double targetX, double targetZ) {
        double dx = targetX - viewerX;
        double dz = targetZ - viewerZ;
        if ((dx * dx) + (dz * dz) < 0.0001D) {
            return "\u2022";
        }

        double targetAngle = Math.toDegrees(Math.atan2(-dx, dz));
        double normalizedYaw = normalizeDegrees(viewerYawDegrees);
        double relative = normalizeDegrees(targetAngle - normalizedYaw);
        int index = (int) Math.round(relative / 45.0D) & 7;
        return switch (index) {
            case 0 -> "\u2191";
            case 1 -> "\u2197";
            case 2 -> "\u2192";
            case 3 -> "\u2198";
            case 4 -> "\u2193";
            case 5 -> "\u2199";
            case 6 -> "\u2190";
            default -> "\u2196";
        };
    }

    private static double normalizeDegrees(double degrees) {
        double normalized = degrees % 360.0D;
        if (normalized <= -180.0D) {
            normalized += 360.0D;
        } else if (normalized > 180.0D) {
            normalized -= 360.0D;
        }
        return normalized;
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
