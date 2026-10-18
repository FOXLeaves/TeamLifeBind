package com.teamlifebind.common;

import java.util.UUID;

/** Verifies optic meaning, interruptible poses and server-confirmation boundaries. */
final class GunPresentationRegressionTest {
    private static int assertions;
    private GunPresentationRegressionTest() {}

    static int run() {
        assertions = 0;
        UUID gun = new UUID(33L, 1L);
        GunMotion.State motion = new GunMotion.State();
        motion.select(gun, GunProfile.MARKSMAN_RIFLE);
        close(1.5D, motion.profile().ironMagnification());
        close(33.0D, motion.profile().modelAdsFov());
        close(90.0D, GunMotion.worldFov(90.0D, 0.0D, 2.0D));
        close(53.130102D, GunMotion.worldFov(90.0D, 1.0D, 2.0D));
        close(90.0D, GunMotion.worldFov(90.0D, Double.NaN, 2.0D));
        close(90.0D, GunMotion.worldFov(90.0D, 1.0D, Double.NaN));
        for (int i = 0; i < 8; i++) motion.step(true, i);
        yes(motion.frame(1.0D).aim() > 0.99D, "ADS reaches the configured aim-time target.");
        double aimed = motion.frame(1.0D).aim();
        motion.step(false, 8);
        yes(motion.frame(1.0D).aim() < aimed && motion.frame(1.0D).aim() > 0.0D, "Aim release reverses smoothly.");
        yes(!motion.confirm(gun, 0L, 0, 0, 0.0D, GunCombatState.Reason.EMPTY, false, 8L), "Empty gun cannot produce recoil.");
        close(0.0D, motion.frame(1.0D).recoil());
        yes(motion.confirm(gun, 1L, 4, 0, 0.0D, GunCombatState.Reason.FIRED, true, 9L), "Server confirmation starts one impulse.");
        yes(!motion.confirm(gun, 1L, 4, 0, 0.0D, GunCombatState.Reason.FIRED, true, 9L), "Duplicate confirmation cannot repeat recoil.");
        yes(!motion.confirm(gun, 0L, 5, 0, 0.0D, GunCombatState.Reason.READY, false, 9L), "Older weapon sequence cannot rewind visuals.");
        motion.step(false, 10L);
        double kick = motion.frame(1.0D).recoil();
        yes(kick > 0.0D, "Confirmed recoil is visible.");
        motion.step(false, 18L);
        yes(motion.frame(1.0D).recoil() < kick && motion.frame(1.0D).boltPose() > 0.0D, "Recoil recovers while the independent bolt action moves.");
        motion.confirm(gun, 1L, 4, 68, 0.0D, GunCombatState.Reason.RELOADING, false, 20L);
        motion.step(true, 40L);
        yes(motion.frame(1.0D).reloadPose() > 0.9D && motion.frame(1.0D).aim() < 0.01D, "Confirmed reload lowers ADS and lifts the reload pose.");
        motion.confirm(gun, 1L, 4, 0, 0.0D, GunCombatState.Reason.NO_WEAPON, false, 41L);
        motion.step(true, 41L);
        close(0.0D, motion.frame(1.0D).reloadPose());
        yes(!motion.allowed(), "Server cancellation removes use eligibility.");
        motion.select(new UUID(33L, 2L), GunProfile.TACTICAL_SMG);
        close(0.0D, motion.frame(1.0D).recoil());
        close(0.0D, motion.frame(1.0D).boltPose());
        yes(!motion.confirm(gun, 2L, 3, 0, 0.0D, GunCombatState.Reason.FIRED, true, 50L), "A transferred old-gun packet cannot affect the new gun.");
        for (GunProfile profile : GunProfile.values()) {
            motion.select(gun, profile);
            for (int tick = 0; tick < 100; tick++) {
                if (tick % 9 == 0) motion.confirm(gun, tick + 1L, 1, 0, 20.0D, GunCombatState.Reason.FIRED, true, tick);
                motion.step(tick % 12 < 6, tick);
                var frame = motion.frame(tick % 7 == 0 ? Double.NaN : 0.5D);
                yes(Double.isFinite(frame.aim()) && frame.aim() >= 0.0D && frame.aim() <= 1.0D, "Aim interpolation remains bounded.");
                yes(Double.isFinite(frame.recoil()) && frame.recoil() >= 0.0D && frame.recoil() <= 2.5D, "Automatic recoil remains bounded.");
                yes(Double.isFinite(frame.reloadPose()) && Double.isFinite(frame.boltPose()), "Mechanical poses remain finite.");
                yes(Double.isFinite(frame.rotorDegrees()) && frame.rotorDegrees() >= 0.0D && frame.rotorDegrees() < 360.0D, "Rotor integration remains bounded across wraps.");
            }
        }
        motion.select(new UUID(33L, 3L), GunProfile.SUPPORT_MACHINE_GUN);
        motion.confirm(motion.serial(), 0L, 120, 96, 30.0D, GunCombatState.Reason.WINDING_UP, false, 1L);
        motion.step(true, 2L);
        close(0.0D, motion.frame(1.0D).reloadPose());
        yes(motion.frame(1.0D).spin() > 0.0D, "Rotary feed spools from server wind-up without a fake magazine reload.");
        double firstAngle = motion.frame(1.0D).rotorDegrees();
        motion.step(true, 3L);
        yes(motion.frame(1.0D).rotorDegrees() > firstAngle, "Spool acceleration integrates rotor movement without resetting the angle.");
        motion.confirm(motion.serial(), 0L, 120, 0, 30.0D, GunCombatState.Reason.READY, false, 4L);
        double beforeStop = motion.frame(1.0D).spin();
        motion.step(false, 4L);
        yes(motion.frame(1.0D).spin() < beforeStop && motion.frame(1.0D).spin() > 0.0D, "Rotor slows with inertia after server wind-up ends.");
        System.out.println("Gun presentation scenarios passed (" + assertions + " assertions).");
        return assertions;
    }

    private static void close(double expected, double actual) {
        assertions++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > 0.00001D) throw new AssertionError("Gun presentation expected " + expected + ", got " + actual);
    }
    private static void yes(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }
}
