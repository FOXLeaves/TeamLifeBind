package com.teamlifebind.common;

import java.util.UUID;

/** Deterministic geometry and damage; collision and ordinary damage remain platform concerns. */
public final class GunMath {
    private GunMath() { }

    public record Vec3(double x, double y, double z) {
        public Vec3 {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Gun vectors must be finite.");
            }
        }
        public Vec3 add(Vec3 other) { return new Vec3(x + other.x, y + other.y, z + other.z); }
        public Vec3 scale(double value) { return new Vec3(x * value, y * value, z * value); }
        public double length() { return Math.hypot(Math.hypot(x, y), z); }
        public double dot(Vec3 other) { return x * other.x + y * other.y + z * other.z; }
        public Vec3 cross(Vec3 other) { return new Vec3(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x); }
        public Vec3 normalized() {
            double length = length();
            if (!(length > 0.0D) || !Double.isFinite(length)) throw new IllegalArgumentException("Gun direction must be non-zero.");
            return scale(1.0D / length);
        }
    }

    /** Uniform solid-angle cone; a shot/pellet produces the same direction on all adapters. */
    public static Vec3 scatter(Vec3 forward, UUID serial, long sequence, int pellet, double spreadDegrees) {
        if (forward == null || serial == null || pellet < 0 || !Double.isFinite(spreadDegrees)) {
            throw new IllegalArgumentException("Invalid gun scatter input.");
        }
        Vec3 direction = forward.normalized();
        double angle = Math.toRadians(Math.max(0.0D, Math.min(89.0D, spreadDegrees)));
        if (angle == 0.0D) return direction;
        long seed = mix(serial.getMostSignificantBits() ^ Long.rotateLeft(serial.getLeastSignificantBits(), 23)
            ^ sequence * 0x9E3779B97F4A7C15L ^ (long) pellet * 0xD1B54A32D192ED03L);
        double cosTheta = 1.0D - unit(seed) * (1.0D - Math.cos(angle));
        double sinTheta = Math.sqrt(Math.max(0.0D, 1.0D - cosTheta * cosTheta));
        double phi = unit(mix(seed + 0x9E3779B97F4A7C15L)) * Math.PI * 2.0D;
        Vec3 reference = Math.abs(direction.y) < 0.99D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 right = direction.cross(reference).normalized();
        Vec3 up = right.cross(direction).normalized();
        return direction.scale(cosTheta).add(right.scale(sinTheta * Math.cos(phi))).add(up.scale(sinTheta * Math.sin(phi))).normalized();
    }

    /** Full damage to 25% of range, then linear reduction to 45%; nothing beyond range. */
    public static double damageFalloff(double distance, double range) {
        if (!Double.isFinite(distance) || !Double.isFinite(range) || distance < 0.0D || range <= 0.0D || distance > range) return 0.0D;
        double ratio = distance / range;
        return ratio <= 0.25D ? 1.0D : 1.0D - (ratio - 0.25D) * (0.55D / 0.75D);
    }

    /** Per pellet. Adapters add pellets by target and call ordinary damage once per target. */
    public static double bulletDamage(GunProfile profile, boolean playerTarget, double maxHealth, double distance, boolean head) {
        if (profile == null || (playerTarget && (!Double.isFinite(maxHealth) || maxHealth <= 0.0D))) return 0.0D;
        double base = playerTarget ? maxHealth * profile.playerDamageFraction() : profile.mobDamage();
        double multiplier = head ? profile.headMultiplier() : 1.0D;
        if (playerTarget) base = Math.min(maxHealth * 0.9D, base * multiplier);
        else base *= multiplier;
        return base * damageFalloff(distance, profile.range()) / profile.pellets();
    }

    /** The adapter separately checks line of sight. A direct hit and splash remain one aggregate hit. */
    public static double splashDamage(GunProfile profile, boolean playerTarget, double maxHealth, double distance) {
        if (profile == null || !profile.isLauncher() || !Double.isFinite(distance) || distance < 0.0D
            || distance >= profile.blastRadius() || (playerTarget && (!Double.isFinite(maxHealth) || maxHealth <= 0.0D))) return 0.0D;
        double base = playerTarget ? maxHealth * profile.splashPlayerDamageFraction() : profile.splashMobDamage();
        return base * (1.0D - distance / profile.blastRadius());
    }

    /** Vanilla dragon body reduction, once for the aggregate body portion of a mixed-part hit. */
    public static double dragonBodyDamage(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0D) return 0.0D;
        return amount / 4.0D + Math.min(amount, 1.0D);
    }

    private static double unit(long value) { return (value >>> 11) * 0x1.0p-53; }
    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
