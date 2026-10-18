package com.teamlifebind.common;

import java.util.UUID;

/** Deterministic, renderer-independent presentation driven by server-confirmed shots. */
public final class GunMotion {
    private GunMotion() {}

    public record Frame(double aim, double recoil, double reloadPose, double boltPose,
                        double heat, double spin, double rotorDegrees, double timeTicks) {}

    public static double worldFov(double original, double aim, double magnification) {
        if (!Double.isFinite(original) || original <= 0.0D || original >= 180.0D) return original;
        double factor = Double.isFinite(magnification) ? Math.max(1.0D, magnification) : 1.0D;
        double zoom = 1.0D + clamp(aim, 0.0D, 1.0D) * (factor - 1.0D);
        return Math.toDegrees(2.0D * Math.atan(Math.tan(Math.toRadians(original) * 0.5D) / zoom));
    }

    private static double clamp(double value, double min, double max) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }

    private static double lerp(double from, double to, double alpha) { return from + (to - from) * alpha; }
    private static double smooth(double value) { double x = clamp(value, 0.0D, 1.0D); return x * x * (3.0D - 2.0D * x); }

    public static final class State {
        private UUID serial;
        private GunProfile gun;
        private GunPresentationProfile profile;
        private long sequence;
        private long tick;
        private long previousTick = Long.MIN_VALUE;
        private long lastFire = Long.MIN_VALUE;
        private long ackTick;
        private int reloadRemaining;
        private int reloadDuration;
        private double heat;
        private double aim;
        private double oldAim;
        private double recoil;
        private double oldRecoil;
        private double spin;
        private double oldSpin;
        private double rotor;
        private double oldRotor;
        private boolean allowed;
        private GunCombatState.Reason reason = GunCombatState.Reason.NO_WEAPON;

        public void select(UUID nextSerial, GunProfile nextGun) {
            if (nextSerial != null && nextSerial.equals(serial) && nextGun == gun) return;
            serial = nextSerial;
            gun = nextGun;
            profile = nextGun == null ? null : GunPresentationProfile.forGun(nextGun);
            sequence = 0L;
            previousTick = Long.MIN_VALUE;
            lastFire = Long.MIN_VALUE;
            ackTick = 0L;
            reloadRemaining = 0;
            reloadDuration = 0;
            heat = aim = oldAim = recoil = oldRecoil = spin = oldSpin = rotor = oldRotor = 0.0D;
            allowed = nextSerial != null && nextGun != null;
            reason = GunCombatState.Reason.NO_WEAPON;
        }

        /** Returns true once for a new confirmed shot; ordinary snapshots never kick the camera. */
        public boolean confirm(UUID weaponSerial, long shotSequence, int ammo, int remaining,
                               double weaponHeat, GunCombatState.Reason nextReason, boolean fired, long clientTick) {
            if (serial == null || !serial.equals(weaponSerial) || profile == null || shotSequence < sequence
                    || shotSequence < 0L || ammo < 0 || remaining < 0 || !Double.isFinite(weaponHeat) || nextReason == null) return false;
            boolean newShot = fired && nextReason == GunCombatState.Reason.FIRED && shotSequence > sequence;
            sequence = shotSequence;
            reason = nextReason;
            allowed = switch (nextReason) {
                case NO_WEAPON, INVALID_WEAPON, LIMIT_REACHED, WEAPON_BUSY, CLOCK_REWIND -> false;
                default -> true;
            };
            if (profile.rotary() || remaining == 0) {
                reloadRemaining = reloadDuration = 0;
            } else {
                if (remainingAt(clientTick) == 0L) reloadDuration = 0;
                reloadRemaining = Math.min(remaining, 7200);
                reloadDuration = Math.max(reloadDuration, reloadRemaining);
            }
            ackTick = Math.max(0L, clientTick);
            heat = clamp(weaponHeat, 0.0D, GunCombatState.MAX_HEAT);
            if (newShot) {
                recoil = Math.min(2.5D, recoil + profile.modelKick());
                lastFire = Math.max(0L, clientTick);
            }
            return newShot;
        }

        public void step(boolean desiredAim, long clientTick) {
            if (clientTick < 0L || clientTick <= previousTick) return;
            long elapsed = previousTick == Long.MIN_VALUE ? 1L : Math.min(20L, clientTick - previousTick);
            previousTick = tick = clientTick;
            oldAim = aim;
            oldRecoil = recoil;
            oldSpin = spin;
            oldRotor = rotor % 360.0D;
            double targetAim = allowed && desiredAim && remainingAt(clientTick) == 0L ? 1.0D : 0.0D;
            double response = profile == null ? 0.12D : profile.aimTimeSeconds() / 3.0D;
            aim += (targetAim - aim) * (1.0D - Math.exp(-0.05D * elapsed / response));
            recoil *= Math.exp(-0.05D * elapsed / 0.12D);
            boolean turning = profile != null && profile.rotary() && allowed
                    && (reason == GunCombatState.Reason.WINDING_UP || ageSinceFire(clientTick) <= 3L);
            spin += ((turning ? 1.0D : 0.0D) - spin) * (1.0D - Math.exp(-0.05D * elapsed / (turning ? 0.18D : 0.35D)));
            rotor = oldRotor + (oldSpin + spin) * 0.5D * 60.0D * elapsed;
        }

        public Frame frame(double partialTick) {
            double partial = clamp(partialTick, 0.0D, 1.0D);
            double remaining = Math.max(0.0D, remainingAt(tick) - partial);
            double reload = 0.0D;
            if (gun != null && remaining > 0.0D && !profile.rotary()) {
                double phase = clamp(1.0D - remaining / Math.max(1, reloadDuration), 0.0D, 1.0D);
                reload = smooth(phase * 4.0D) * smooth((1.0D - phase) * 4.0D);
            }
            double bolt = 0.0D;
            if (profile != null && profile.boltTicks() > 0 && lastFire != Long.MIN_VALUE) {
                double phase = (ageSinceFire(tick) + partial - 2.0D) / Math.max(1, profile.boltTicks() - 2);
                if (phase > 0.0D && phase < 1.0D) bolt = Math.sin(Math.PI * phase);
            }
            return new Frame(lerp(oldAim, aim, partial), lerp(oldRecoil, recoil, partial), reload, bolt,
                    heat / GunCombatState.MAX_HEAT, lerp(oldSpin, spin, partial),
                    lerp(oldRotor, rotor, partial) % 360.0D, tick + partial);
        }

        public boolean allowed() { return allowed; }
        public GunPresentationProfile profile() { return profile; }
        public UUID serial() { return serial; }
        public long sequence() { return sequence; }

        private long remainingAt(long atTick) { return Math.max(0L, reloadRemaining - Math.max(0L, atTick - ackTick)); }
        private long ageSinceFire(long atTick) { return lastFire == Long.MIN_VALUE ? Long.MAX_VALUE : Math.max(0L, atTick - lastFire); }
    }
}
