package com.teamlifebind.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.teamlifebind.common.GunCombatState.Reason;
import com.teamlifebind.common.GunCombatState.Update;
import com.teamlifebind.common.GunCombatState.WeaponRef;

/** State-machine scenarios cover the same tick/input interface used by all four adapters. */
final class GunRegressionTest {
    private static final UUID A = new UUID(99L, 1L);
    private static final UUID B = new UUID(99L, 2L);
    private static int assertions;

    private GunRegressionTest() { }

    static int run() throws Exception {
        assertions = 0;
        profilesAndRegistration();
        semiChargeAndRelease();
        releasesBetweenTicks();
        automaticAndTickGuards();
        switchAndTransfer();
        sameProfileSwitchCancelsReload();
        finiteMagazineAndReload();
        heatAndCooling();
        nativeRotaryFeed();
        nativeReferenceReloads();
        scatterAndDamage();
        simultaneousInputs();
        System.out.println("Gun state and ballistics scenarios passed (" + assertions + " assertions).");
        return assertions;
    }

    private static WeaponRef weapon(int id, GunProfile profile, int ammo) { return new WeaponRef(new UUID(100L, id), profile, ammo); }
    private static Update input(GunCombatState state, UUID player, WeaponRef ref, boolean trigger, boolean reload, long tick) {
        return state.advance(player, ref, trigger, reload, false, tick);
    }
    private static Update fire(GunCombatState state, WeaponRef ref, long tick) { return input(state, A, ref, true, false, tick); }

    private static void profilesAndRegistration() {
        eq(7, GunProfile.values().length);
        eq(null, GunProfile.byId(null));
        eq(null, GunProfile.byId("invalid"));
        for (GunProfile profile : GunProfile.values()) {
            eq(profile, GunProfile.byId(profile.id()));
            yes(profile.intervalTicks() >= 1 && profile.capacity() >= 1 && profile.reloadTicks() >= 1, "Finite magazines and tick intervals.");
            yes(profile.aimSpreadDegrees() < profile.hipSpreadDegrees(), "Aiming improves every weapon's spread.");
        }
        eq(8, GunProfile.BREACH_SHOTGUN.pellets());
        close(2.4D, GunProfile.SIEGE_LAUNCHER.rocketSpeed());
        close(0.025D, GunProfile.SIEGE_LAUNCHER.rocketGravity());
        eq(40, GunProfile.SIEGE_LAUNCHER.rocketLifetimeTicks());
        close(3.5D, GunProfile.SIEGE_LAUNCHER.blastRadius());
        GunCombatState state = new GunCombatState();
        WeaponRef smg = weapon(1, GunProfile.TACTICAL_SMG, Integer.MAX_VALUE);
        yes(!state.registerWeapon(null, smg.profile(), 30), "Null serial is rejected.");
        yes(!state.registerWeapon(smg.serial(), null, 30), "Null profile is rejected.");
        yes(state.registerWeapon(smg.serial(), smg.profile(), smg.savedAmmo()), "Register original gun.");
        eq(30, state.snapshot(smg.serial(), 0).ammo());
        fire(state, smg, 0);
        yes(state.registerWeapon(smg.serial(), smg.profile(), Integer.MAX_VALUE), "Registration is idempotent.");
        eq(29, state.snapshot(smg.serial(), 0).ammo());
        yes(!state.registerWeapon(smg.serial(), GunProfile.BATTLE_PISTOL, 5), "Serial cannot change its profile.");
        WeaponRef empty = weapon(2, GunProfile.ASSAULT_RIFLE, Integer.MIN_VALUE);
        yes(state.registerWeapon(empty.serial(), empty.profile(), empty.savedAmmo()), "Negative saved ammunition clamps.");
        eq(0, state.snapshot(empty.serial(), 0).ammo());
        for (int id = 3; id <= GunCombatState.MAX_WEAPONS; id++) {
            WeaponRef extra = weapon(id, GunProfile.ASSAULT_RIFLE, 20);
            yes(state.registerWeapon(extra.serial(), extra.profile(), extra.savedAmmo()), "Bounded gun registration.");
        }
        eq(256, state.registeredWeaponCount());
        yes(!state.registerWeapon(weapon(257, smg.profile(), 30).serial(), smg.profile(), 30), "Creation cap rejects the next gun.");
        state.clear();
        eq(0, state.registeredWeaponCount());
        eq(0, state.trackedPlayerCount());
        for (int id = 1; id <= GunCombatState.MAX_PLAYERS; id++) {
            WeaponRef extra = weapon(id, GunProfile.TACTICAL_SMG, 30);
            eq(Reason.READY, input(state, new UUID(101L, id), extra, false, false, 0).reason());
        }
        eq(128, state.trackedPlayerCount());
        eq(Reason.LIMIT_REACHED, input(state, new UUID(101L, 129), weapon(129, smg.profile(), 30), true, false, 0).reason());
        eq(128, state.registeredWeaponCount());
        eq(Reason.INVALID_WEAPON, state.advance(null, smg, true, false, false, 0).reason());
        eq(Reason.INVALID_WEAPON, input(state, A, smg, true, false, -1).reason());
    }

    private static void semiChargeAndRelease() {
        GunCombatState state = new GunCombatState();
        WeaponRef pistol = weapon(1, GunProfile.BATTLE_PISTOL, 5);
        for (long tick = 0; tick < 5; tick++) eq(Reason.WINDING_UP, fire(state, pistol, tick).reason());
        Update first = state.advance(A, pistol, true, false, true, 5);
        eq(1, first.shots().size());
        eq(4, first.snapshot().ammo());
        eq(1L, first.shots().getFirst().sequence());
        yes(first.shots().getFirst().aimed(), "The shot records aim state.");
        eq(Reason.READY, fire(state, pistol, 40).reason());
        eq(4, state.snapshot(pistol.serial(), 40).ammo());
        input(state, A, pistol, false, false, 41);
        eq(Reason.WINDING_UP, fire(state, pistol, 42).reason());
        eq(Reason.FIRED, fire(state, pistol, 47).reason());
        eq(3, state.snapshot(pistol.serial(), 47).ammo());
        input(state, A, pistol, false, false, 48);
        eq(Reason.WINDING_UP, fire(state, pistol, 49).reason());
        input(state, A, pistol, false, false, 50);
        eq(Reason.WINDING_UP, fire(state, pistol, 51).reason());
        eq(Reason.WINDING_UP, fire(state, pistol, 55).reason());
        eq(Reason.COOLDOWN, fire(state, pistol, 56).reason());
        eq(Reason.FIRED, fire(state, pistol, 59).reason());
        WeaponRef sniper = weapon(2, GunProfile.MARKSMAN_RIFLE, 5);
        eq(Reason.COOLDOWN, input(state, A, sniper, true, false, 60).reason());
        eq(Reason.FIRED, input(state, A, sniper, true, false, 71).reason());
        eq(Reason.READY, input(state, A, sniper, true, false, 99).reason());
        input(state, A, sniper, false, false, 100);
        eq(Reason.FIRED, input(state, A, sniper, true, false, 101).reason());
    }

    private static void releasesBetweenTicks() {
        GunCombatState state = new GunCombatState(true);
        WeaponRef sniper = weapon(950, GunProfile.MARKSMAN_RIFLE, 5);
        eq(Reason.FIRED, fire(state, sniper, 0).reason());
        state.releaseTrigger(A, sniper.serial());
        eq(Reason.DUPLICATE_TICK, fire(state, sniper, 0).reason());
        eq(Reason.COOLDOWN, fire(state, sniper, 1).reason());
        eq(4, state.snapshot(sniper.serial(), 1).ammo());
        eq(Reason.FIRED, fire(state, sniper, 29).reason());
        state.releaseTrigger(B, sniper.serial());
        state.releaseTrigger(A, weapon(951, GunProfile.MARKSMAN_RIFLE, 5).serial());
        state.releaseTrigger(A, null);
        eq(Reason.READY, fire(state, sniper, 100).reason());
        eq(3, state.snapshot(sniper.serial(), 100).ammo());
        state.releaseTrigger(A, sniper.serial());
        eq(Reason.WEAPON_BUSY, input(state, B, sniper, true, false, 101).reason());
        eq(Reason.CLOCK_REWIND, fire(state, sniper, 99).reason());
        eq(Reason.FIRED, fire(state, sniper, 101).reason());

        GunCombatState reload = new GunCombatState(true);
        WeaponRef smg = weapon(952, GunProfile.TACTICAL_SMG, 5);
        eq(Reason.RELOADING, input(reload, A, smg, false, true, 0).reason());
        long remaining = reload.snapshot(smg.serial(), 0).reloadRemainingTicks();
        reload.releaseTrigger(A, smg.serial());
        reload.releaseTrigger(null, smg.serial());
        eq(remaining, reload.snapshot(smg.serial(), 0).reloadRemainingTicks());
        eq(5, reload.snapshot(smg.serial(), 0).ammo());
        eq(Reason.READY, input(reload, A, smg, false, false, remaining).reason());
        eq(20, reload.snapshot(smg.serial(), remaining).ammo());

        GunCombatState charge = new GunCombatState(true);
        WeaponRef pistol = weapon(953, GunProfile.BATTLE_PISTOL, 5);
        eq(Reason.WINDING_UP, fire(charge, pistol, 0).reason());
        charge.releaseTrigger(A, pistol.serial());
        eq(Reason.WINDING_UP, fire(charge, pistol, 5).reason());
        eq(Reason.WINDING_UP, fire(charge, pistol, 9).reason());
        eq(Reason.FIRED, fire(charge, pistol, 10).reason());
        eq(4, charge.snapshot(pistol.serial(), 10).ammo());

        GunCombatState rotary = new GunCombatState(true);
        WeaponRef minigun = weapon(954, GunProfile.SUPPORT_MACHINE_GUN, 1);
        fire(rotary, minigun, 0);
        eq(Reason.FIRED, fire(rotary, minigun, 8).reason());
        double heat = rotary.snapshot(minigun.serial(), 8).heat();
        rotary.releaseTrigger(A, minigun.serial());
        close(heat, rotary.snapshot(minigun.serial(), 8).heat());
        eq(Reason.WINDING_UP, fire(rotary, minigun, 9).reason());
    }

    private static void automaticAndTickGuards() {
        GunCombatState state = new GunCombatState();
        WeaponRef smg = weapon(1, GunProfile.TACTICAL_SMG, 30);
        eq(1, fire(state, smg, 0).shots().size());
        eq(Reason.DUPLICATE_TICK, fire(state, smg, 0).reason());
        eq(29, state.snapshot(smg.serial(), 0).ammo());
        eq(1, fire(state, smg, 1000).shots().size());
        eq(28, state.snapshot(smg.serial(), 1000).ammo());
        eq(Reason.CLOCK_REWIND, fire(state, smg, 999).reason());
        eq(28, state.snapshot(smg.serial(), 1000).ammo());
        Update third = fire(state, smg, 1001);
        eq(3L, third.shots().getFirst().sequence());
        expect(UnsupportedOperationException.class, () -> third.shots().clear());
        eq(Reason.NO_WEAPON, input(state, A, null, true, false, 1002).reason());
        eq(27, state.snapshot(smg.serial(), 1002).ammo());
        eq(Reason.FIRED, fire(state, new WeaponRef(smg.serial(), smg.profile(), 30), 1003).reason());
        eq(26, state.snapshot(smg.serial(), 1003).ammo());
        state.clear();
        eq(Reason.FIRED, fire(state, smg, 0).reason());
    }

    private static void switchAndTransfer() {
        GunCombatState state = new GunCombatState();
        WeaponRef rifle = weapon(1, GunProfile.ASSAULT_RIFLE, 20);
        WeaponRef smg = weapon(2, GunProfile.TACTICAL_SMG, 30);
        eq(Reason.FIRED, fire(state, rifle, 0).reason());
        state.cancelPlayer(A);
        eq(Reason.COOLDOWN, fire(state, smg, 1).reason());
        eq(Reason.FIRED, fire(state, smg, 2).reason());
        eq(Reason.FIRED, input(state, B, rifle, true, false, 3).reason());
        eq(18, state.snapshot(rifle.serial(), 3).ammo());
        eq(Reason.WEAPON_BUSY, input(state, A, rifle, true, false, 4).reason());
        state.cancelPlayer(B);
        eq(Reason.FIRED, input(state, A, rifle, true, false, 5).reason());
        // Fresh round isolates the per-gun transfer cooldown from the per-player cooldown.
        state.clear();
        fire(state, rifle, 0);
        state.cancelPlayer(A);
        eq(Reason.COOLDOWN, input(state, B, rifle, true, false, 1).reason());
        eq(Reason.FIRED, input(state, B, rifle, true, false, 2).reason());
        state.cancelPlayer(B);
        eq(Reason.INVALID_WEAPON, input(state, A, new WeaponRef(rifle.serial(), GunProfile.BATTLE_PISTOL, 5), true, false, 3).reason());
        eq(18, state.snapshot(rifle.serial(), 3).ammo());
        eq(Reason.COOLDOWN, fire(state, rifle, 3).reason());
        eq(Reason.FIRED, fire(state, rifle, 4).reason());
    }

    private static void finiteMagazineAndReload() {
        GunCombatState state = new GunCombatState();
        WeaponRef smg = weapon(1, GunProfile.TACTICAL_SMG, 1);
        eq(0, fire(state, smg, 0).snapshot().ammo());
        eq(Reason.RELOADING, fire(state, smg, 1).reason());
        eq(44L, state.snapshot(smg.serial(), 1).reloadRemainingTicks());
        eq(Reason.RELOADING, input(state, A, smg, true, true, 2).reason());
        eq(43L, state.snapshot(smg.serial(), 2).reloadRemainingTicks());
        eq(0, fire(state, smg, 44).snapshot().ammo());
        eq(1L, state.snapshot(smg.serial(), 44).reloadRemainingTicks());
        eq(30, input(state, A, smg, false, false, 45).snapshot().ammo());
        eq(Reason.READY, input(state, A, smg, false, true, 46).reason());
        eq(0L, state.snapshot(smg.serial(), 46).reloadRemainingTicks());
        eq(29, fire(state, smg, 47).snapshot().ammo());
        eq(Reason.RELOADING, input(state, A, smg, false, true, 48).reason());
        state.cancelPlayer(A);
        eq(29, input(state, A, smg, false, false, 100).snapshot().ammo());
        eq(0L, state.snapshot(smg.serial(), 100).reloadRemainingTicks());
        eq(Reason.RELOADING, input(state, A, smg, false, true, 101).reason());
        eq(30, input(state, A, smg, false, false, 145).snapshot().ammo());
        // No extra ammunition stacks are involved; a reload always refills precisely capacity.
        state.clear();
        WeaponRef launcher = weapon(2, GunProfile.SIEGE_LAUNCHER, 1);
        eq(Reason.FIRED, fire(state, launcher, 0).reason());
        input(state, A, launcher, false, false, 1);
        eq(Reason.RELOADING, fire(state, launcher, 2).reason());
        eq(1, fire(state, launcher, 66).snapshot().ammo());
        eq(0, fire(state, launcher, 67).shots().size());
        input(state, A, launcher, false, false, 68);
        eq(Reason.FIRED, fire(state, launcher, 69).reason());
        state.cancelPlayer(A);
        eq(0, state.snapshot(launcher.serial(), 200).ammo());
    }

    private static void sameProfileSwitchCancelsReload() {
        GunCombatState state = new GunCombatState();
        WeaponRef original = weapon(1, GunProfile.ASSAULT_RIFLE, 20);
        WeaponRef replacement = weapon(2, GunProfile.ASSAULT_RIFLE, 2);
        eq(19, fire(state, original, 0).snapshot().ammo());
        eq(Reason.RELOADING, input(state, A, original, false, true, 1).reason());
        eq(50L, state.snapshot(original.serial(), 1).reloadRemainingTicks());
        eq(1, fire(state, replacement, 2).snapshot().ammo());
        eq(0L, state.snapshot(original.serial(), 2).reloadRemainingTicks());
        eq(19, state.snapshot(original.serial(), 2).ammo());
        eq(Reason.COOLDOWN, fire(state, original, 3).reason());
        eq(18, fire(state, original, 4).snapshot().ammo());
        input(state, A, original, false, true, 5);
        state.cancelPlayer(A);
        eq(18, input(state, B, original, false, false, 6).snapshot().ammo());
        eq(0L, state.snapshot(original.serial(), 6).reloadRemainingTicks());
        eq(Reason.RELOADING, input(state, B, original, false, true, 7).reason());
        eq(Reason.WEAPON_BUSY, input(state, A, original, false, true, 8).reason());
        eq(49L, state.snapshot(original.serial(), 8).reloadRemainingTicks());
        eq(20, input(state, B, original, false, false, 57).snapshot().ammo());
        input(state, B, original, true, false, 58);
        input(state, B, original, false, true, 59);
        eq(Reason.NO_WEAPON, input(state, B, null, false, false, 60).reason());
        eq(19, input(state, B, original, false, false, 1000).snapshot().ammo());
        eq(0L, state.snapshot(original.serial(), 1000).reloadRemainingTicks());
        eq(1, state.snapshot(replacement.serial(), 1000).ammo());
    }

    private static void heatAndCooling() {
        GunCombatState state = new GunCombatState();
        WeaponRef machine = weapon(1, GunProfile.SUPPORT_MACHINE_GUN, 120);
        for (long tick = 0; tick < 8; tick++) eq(Reason.WINDING_UP, fire(state, machine, tick).reason());
        for (long tick = 8; tick <= 57; tick++) {
            Update shot = fire(state, machine, tick);
            eq(1, shot.shots().size());
            close((tick - 7) * 2.0D, shot.snapshot().heat());
        }
        eq(70, state.snapshot(machine.serial(), 57).ammo());
        yes(state.snapshot(machine.serial(), 57).overheated(), "The fiftieth continuous shot overheats.");
        for (long tick = 58; tick <= 143; tick++) eq(Reason.OVERHEATED, fire(state, machine, tick).reason());
        yes(!state.snapshot(machine.serial(), 143).overheated(), "Cooling unlocks only at 40 heat or less.");
        close(39.8D, state.snapshot(machine.serial(), 143).heat());
        eq(Reason.FIRED, fire(state, machine, 144).reason());
        close(41.8D, state.snapshot(machine.serial(), 144).heat());
        eq(69, state.snapshot(machine.serial(), 144).ammo());
        eq(Reason.DUPLICATE_TICK, fire(state, machine, 144).reason());
        close(41.8D, state.snapshot(machine.serial(), 144).heat());
        state.cancelPlayer(A);
        eq(Reason.WINDING_UP, fire(state, machine, 145).reason());
        close(41.1D, state.snapshot(machine.serial(), 145).heat());
        eq(Reason.READY, input(state, A, machine, false, false, 1000).reason());
        close(0.0D, state.snapshot(machine.serial(), 1000).heat());
        eq(69, state.snapshot(machine.serial(), 1000).ammo());
    }

    private static void nativeRotaryFeed() {
        GunCombatState nativeState = new GunCombatState(true);
        WeaponRef rotary = weapon(700, GunProfile.SUPPORT_MACHINE_GUN, 0);
        yes(nativeState.registerWeapon(rotary.serial(), rotary.profile(), 0), "Native rotary feed initializes from infinite reserves.");
        eq(rotary.profile().capacity(), nativeState.snapshot(rotary.serial(), 0).ammo());
        int shots = 0;
        long tick = 0;
        for (int burst = 0; burst < 4; burst++) {
            for (int step = 0; step < rotary.profile().windupTicks() + 50; step++) {
                Update update = input(nativeState, A, rotary, true, step == 10, tick++);
                shots += update.shots().size();
                eq(rotary.profile().capacity(), update.snapshot().ammo());
                eq(0L, update.snapshot().reloadRemainingTicks());
            }
            yes(nativeState.snapshot(rotary.serial(), tick).overheated(), "Infinite feed still overheats after 50 shots.");
            tick += 150;
            Update cooled = input(nativeState, A, rotary, false, true, tick++);
            yes(!cooled.snapshot().overheated(), "Stopping cools native rotary feed.");
            eq(0L, cooled.snapshot().reloadRemainingTicks());
        }
        eq(200, shots);
        yes(shots > rotary.profile().capacity(), "Heat-limited native feed can exceed one finite magazine.");
        nativeState.cancelPlayer(A);
        Update resumed = input(nativeState, B, rotary, false, true, tick);
        eq(rotary.profile().capacity(), resumed.snapshot().ammo());
        eq(0L, resumed.snapshot().reloadRemainingTicks());
        GunCombatState paperState = new GunCombatState();
        Update paperEmpty = input(paperState, A, rotary, true, false, 0);
        eq(Reason.RELOADING, paperEmpty.reason());
        eq((long) rotary.profile().reloadTicks(), paperEmpty.snapshot().reloadRemainingTicks());
        WeaponRef ordinary = weapon(701, GunProfile.ASSAULT_RIFLE, 20);
        eq(19, input(nativeState, A, ordinary, true, false, tick + 1).snapshot().ammo());
    }

    private static void nativeReferenceReloads() {
        GunCombatState rules = new GunCombatState(true);
        eq(20, rules.capacity(GunProfile.TACTICAL_SMG));
        eq(5, rules.capacity(GunProfile.BREACH_SHOTGUN));
        eq(30, new GunCombatState().capacity(GunProfile.TACTICAL_SMG));
        eq(8, new GunCombatState().capacity(GunProfile.BREACH_SHOTGUN));
        eq(51, rules.reloadTicks(GunProfile.TACTICAL_SMG, 19));
        eq(56, rules.reloadTicks(GunProfile.TACTICAL_SMG, 0));
        eq(82, rules.reloadTicks(GunProfile.MARKSMAN_RIFLE, 1));
        eq(105, rules.reloadTicks(GunProfile.MARKSMAN_RIFLE, 0));
        eq(10, rules.intervalTicks(GunProfile.BATTLE_PISTOL));
        eq(29, rules.intervalTicks(GunProfile.MARKSMAN_RIFLE));
        WeaponRef shells = weapon(800, GunProfile.BREACH_SHOTGUN, 0);
        Update start = input(rules, A, shells, false, true, 0);
        eq(5, start.snapshot().capacity());
        eq(126L, start.snapshot().reloadRemainingTicks());
        eq(0, input(rules, A, shells, false, false, 61).snapshot().ammo());
        Update firstShell = input(rules, A, shells, false, false, 62);
        eq(1, firstShell.snapshot().ammo());
        eq(64L, firstShell.snapshot().reloadRemainingTicks());
        eq(63L, input(rules, A, shells, false, true, 63).snapshot().reloadRemainingTicks());
        eq(2, input(rules, A, shells, false, false, 75).snapshot().ammo());
        Update interrupted = input(rules, A, shells, true, false, 76);
        eq(1, interrupted.shots().size());
        eq(1, interrupted.snapshot().ammo());
        eq(0L, interrupted.snapshot().reloadRemainingTicks());
        eq(76L, input(rules, A, shells, false, true, 77).snapshot().reloadRemainingTicks());
        Update completed = input(rules, A, shells, false, false, 200);
        eq(5, completed.snapshot().ammo());
        eq(0L, completed.snapshot().reloadRemainingTicks());
        yes(completed.shots().isEmpty(), "Elapsed reload time never authorizes catch-up shots.");
        GunCombatState transfer = new GunCombatState(true);
        input(transfer, A, shells, false, true, 0);
        input(transfer, A, shells, false, false, 62);
        transfer.cancelPlayer(A);
        Update carried = input(transfer, B, shells, false, false, 200);
        eq(1, carried.snapshot().ammo());
        eq(0L, carried.snapshot().reloadRemainingTicks());
        WeaponRef pistol = weapon(801, GunProfile.BATTLE_PISTOL, 5);
        GunCombatState charge = new GunCombatState(true);
        input(charge, A, pistol, true, false, 0);
        eq(1, input(charge, A, pistol, true, false, 5).shots().size());
        input(charge, A, pistol, false, false, 6);
        input(charge, A, pistol, true, false, 7);
        yes(input(charge, A, pistol, true, false, 15).shots().isEmpty(), "Native revolver cannot pre-charge during cooldown.");
        eq(1, input(charge, A, pistol, true, false, 20).shots().size());
    }

    private static void scatterAndDamage() {
        close(0, GunMath.dragonBodyDamage(0));
        close(0, GunMath.dragonBodyDamage(-1));
        close(0, GunMath.dragonBodyDamage(Double.NaN));
        close(0, GunMath.dragonBodyDamage(Double.POSITIVE_INFINITY));
        close(0.625D, GunMath.dragonBodyDamage(0.5D));
        close(1.25D, GunMath.dragonBodyDamage(1));
        close(3.5D, GunMath.dragonBodyDamage(10));
        close(6.0D, GunMath.dragonBodyDamage(20));
        close(13.5D, 10 + GunMath.dragonBodyDamage(10));
        GunMath.Vec3[] forwards = {new GunMath.Vec3(0, 0, 1), new GunMath.Vec3(0, 1, 0), new GunMath.Vec3(0, -1, 0), new GunMath.Vec3(1, 2, -3)};
        UUID serial = new UUID(100L, 500L);
        for (GunProfile profile : GunProfile.values()) {
            for (GunMath.Vec3 forward : forwards) {
                for (long sequence = 1; sequence <= 8; sequence++) {
                    for (int pellet = 0; pellet < profile.pellets(); pellet++) {
                        GunMath.Vec3 scattered = GunMath.scatter(forward, serial, sequence, pellet, profile.hipSpreadDegrees());
                        eq(scattered, GunMath.scatter(forward, serial, sequence, pellet, profile.hipSpreadDegrees()));
                        close(1.0D, scattered.length());
                        yes(scattered.dot(forward.normalized()) >= Math.cos(Math.toRadians(profile.hipSpreadDegrees())) - 0.0000001D, "Scatter stays in the declared cone.");
                    }
                }
                eq(forward.normalized(), GunMath.scatter(forward, serial, 1, 0, 0.0D));
            }
            double totalBody = GunMath.bulletDamage(profile, true, 2.0D, 0, false) * profile.pellets();
            close(2.0D * profile.playerDamageFraction(), totalBody);
            close(totalBody * 10.0D, GunMath.bulletDamage(profile, true, 20.0D, 0, false) * profile.pellets());
            yes(GunMath.bulletDamage(profile, true, 2.0D, 0, true) * profile.pellets() < 2.0D, "Every gun gives one-heart players a chance before mitigation.");
            close(profile.mobDamage(), GunMath.bulletDamage(profile, false, 0, 0, false) * profile.pellets());
            close(0.0D, GunMath.bulletDamage(profile, true, 2, profile.range() + 0.01D, true));
            close(profile.playerDamageFraction() * 2 * 0.45D / profile.pellets(), GunMath.bulletDamage(profile, true, 2, profile.range(), false));
        }
        yes(!GunMath.scatter(forwards[0], serial, 1, 0, 5).equals(GunMath.scatter(forwards[0], serial, 1, 1, 5)), "Pellets get different deterministic directions.");
        yes(!GunMath.scatter(forwards[0], serial, 1, 0, 5).equals(GunMath.scatter(forwards[0], serial, 2, 0, 5)), "Shots get different deterministic directions.");
        expect(IllegalArgumentException.class, () -> new GunMath.Vec3(Double.NaN, 0, 1));
        expect(IllegalArgumentException.class, () -> GunMath.scatter(new GunMath.Vec3(0, 0, 0), serial, 1, 0, 1));
        expect(IllegalArgumentException.class, () -> GunMath.scatter(forwards[0], serial, 1, -1, 1));
        close(0, GunMath.bulletDamage(null, true, 20, 0, false));
        close(0, GunMath.bulletDamage(GunProfile.ASSAULT_RIFLE, true, Double.POSITIVE_INFINITY, 0, false));
        close(0, GunMath.bulletDamage(GunProfile.ASSAULT_RIFLE, true, 20, Double.NaN, false));
        close(0, GunMath.damageFalloff(-1, 50));
        close(0, GunMath.damageFalloff(1, 0));
        close(1, GunMath.damageFalloff(10, 40));
        close(1.1D, GunMath.splashDamage(GunProfile.SIEGE_LAUNCHER, true, 2, 0));
        close(0.55D, GunMath.splashDamage(GunProfile.SIEGE_LAUNCHER, true, 2, 1.75D));
        close(24, GunMath.splashDamage(GunProfile.SIEGE_LAUNCHER, false, 0, 0));
        close(0, GunMath.splashDamage(GunProfile.SIEGE_LAUNCHER, true, 2, 3.5D));
        close(0, GunMath.splashDamage(GunProfile.SIEGE_LAUNCHER, true, 2, Double.NaN));
        close(0, GunMath.splashDamage(GunProfile.BATTLE_PISTOL, true, 20, 0));
    }

    private static void simultaneousInputs() throws Exception {
        GunCombatState state = new GunCombatState();
        WeaponRef smg = weapon(1, GunProfile.TACTICAL_SMG, 30);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> outputs = new ArrayList<>();
            for (int index = 0; index < 8; index++) outputs.add(executor.submit(() -> {
                ready.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Gun input release timed out.");
                return fire(state, smg, 0).shots().size();
            }));
            yes(ready.await(5, TimeUnit.SECONDS), "All simultaneous input callers are ready.");
            release.countDown();
            int shots = 0;
            for (Future<Integer> output : outputs) shots += output.get(5, TimeUnit.SECONDS);
            eq(1, shots);
            eq(29, state.snapshot(smg.serial(), 0).ammo());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static void eq(Object expected, Object actual) {
        assertions++;
        if (!Objects.equals(expected, actual)) throw new AssertionError("Gun: expected " + expected + " but was " + actual);
    }
    private static void close(double expected, double actual) {
        assertions++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > 0.00001D) throw new AssertionError("Gun: expected " + expected + " but was " + actual);
    }
    private static void yes(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private static void expect(Class<? extends Throwable> expected, Runnable operation) {
        assertions++;
        try { operation.run(); }
        catch (Throwable failure) {
            if (expected.isInstance(failure)) return;
            throw new AssertionError("Expected " + expected.getSimpleName() + " but caught " + failure, failure);
        }
        throw new AssertionError("Expected " + expected.getSimpleName() + " but no exception was thrown.");
    }
}
