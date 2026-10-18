package com.teamlifebind.common;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-tick weapon authorization. An adapter submits one sample per eligible player per tick;
 * it executes the returned shot and persists the returned ammunition on that weapon's item.
 * World collision, player eligibility and damage belong to the adapters, not this module.
 */
public final class GunCombatState {
    public static final int MAX_WEAPONS = 256;
    public static final int MAX_PLAYERS = 128;
    public static final double HEAT_PER_SHOT = 2.0D;
    public static final double MAX_HEAT = 100.0D;
    public static final double HEAT_UNLOCK = 40.0D;
    public static final double HEAT_COOLING_PER_TICK = 0.7D;

    public enum Reason {
        READY, FIRED, NO_WEAPON, INVALID_WEAPON, LIMIT_REACHED, WEAPON_BUSY,
        RELOADING, COOLDOWN, WINDING_UP, EMPTY, OVERHEATED, DUPLICATE_TICK, CLOCK_REWIND
    }

    public record WeaponRef(UUID serial, GunProfile profile, int savedAmmo) { }
    public record Shot(UUID shooter, UUID weaponSerial, GunProfile profile, long sequence, boolean aimed, long tick) { }
    public record Snapshot(int ammo, int capacity, long reloadRemainingTicks, double heat, boolean overheated, Reason reason) { }
    public record Update(List<Shot> shots, Snapshot snapshot, Reason reason) {
        public Update { shots = List.copyOf(shots); }
    }

    private final Map<UUID, WeaponState> weapons = new HashMap<>();
    private final Map<UUID, PlayerState> players = new HashMap<>();
    private final boolean nativeReferenceRules;

    public GunCombatState() { this(false); }

    /** Native reference capacity/timings and rotary feed; the default preserves Paper's rules. */
    public GunCombatState(boolean nativeReferenceRules) { this.nativeReferenceRules = nativeReferenceRules; }

    private boolean usesInfiniteFeed(GunProfile profile) { return nativeReferenceRules && profile.usesHeat(); }

    public int capacity(GunProfile profile) {
        if (!nativeReferenceRules) return profile.capacity();
        return switch (profile) {
            case TACTICAL_SMG -> 20;
            case BREACH_SHOTGUN -> 5;
            default -> profile.capacity();
        };
    }

    public int intervalTicks(GunProfile profile) {
        if (!nativeReferenceRules) return profile.intervalTicks();
        return switch (profile) {
            case BATTLE_PISTOL -> 10;
            case BREACH_SHOTGUN -> 11;
            case MARKSMAN_RIFLE -> 29;
            default -> profile.intervalTicks();
        };
    }

    public int reloadTicks(GunProfile profile, int ammo) {
        if (!nativeReferenceRules) return profile.reloadTicks();
        return switch (profile) {
            case BATTLE_PISTOL -> 55;
            case TACTICAL_SMG -> ammo <= 0 ? 56 : 51;
            case ASSAULT_RIFLE -> ammo <= 0 ? 51 : 41;
            case BREACH_SHOTGUN -> (ammo <= 0 ? 49 : 12) + Math.max(0, capacity(profile) - Math.max(0, ammo)) * 13 + 12;
            case MARKSMAN_RIFLE -> ammo <= 0 ? 105 : 82;
            case SUPPORT_MACHINE_GUN -> 0;
            case SIEGE_LAUNCHER -> 64;
        };
    }

    private boolean usesShellReload(GunProfile profile) { return nativeReferenceRules && profile == GunProfile.BREACH_SHOTGUN; }

    /** Registers a created/loaded gun once. Re-registering never refills or changes its profile. */
    public synchronized boolean registerWeapon(UUID serial, GunProfile profile, int savedAmmo) {
        if (serial == null || profile == null) return false;
        WeaponState existing = weapons.get(serial);
        if (existing != null) return existing.profile == profile;
        if (weapons.size() >= MAX_WEAPONS) return false;
        weapons.put(serial, new WeaponState(profile, usesInfiniteFeed(profile) ? capacity(profile) : Math.max(0, Math.min(capacity(profile), savedAmmo))));
        return true;
    }

    /**
     * Null held cancels active use. Reload requests are input edges, not a held key.
     * Repeated/rewound ticks authorize no shot; elapsed ticks never produce catch-up fire.
     * Unknown serials are registered from savedAmmo, subject to the same creation limit.
     */
    public synchronized Update advance(UUID playerId, WeaponRef held, boolean triggerHeld,
                                       boolean reloadRequested, boolean aimed, long tick) {
        if (playerId == null || tick < 0L) return empty(Reason.INVALID_WEAPON);
        PlayerState player = players.get(playerId);
        if (player != null && tick <= player.lastTick) {
            Reason reason = tick == player.lastTick ? Reason.DUPLICATE_TICK : Reason.CLOCK_REWIND;
            return noShot(player.weapon == null ? null : weapons.get(player.weapon), tick, reason);
        }
        if (held == null) {
            if (player != null) {
                cancelActive(playerId, player);
                player.lastTick = tick;
            }
            return empty(Reason.NO_WEAPON);
        }
        if (held.serial == null || held.profile == null) {
            if (player != null) cancelActive(playerId, player);
            return empty(Reason.INVALID_WEAPON);
        }
        WeaponState weapon = weapons.get(held.serial);
        if (weapon != null && weapon.profile != held.profile) {
            if (player != null) cancelActive(playerId, player);
            return noShot(weapon, tick, Reason.INVALID_WEAPON);
        }
        if (player == null) {
            if (players.size() >= MAX_PLAYERS) return empty(Reason.LIMIT_REACHED);
            player = new PlayerState();
            players.put(playerId, player);
        }
        if (weapon == null) {
            if (!registerWeapon(held.serial, held.profile, held.savedAmmo)) {
                cancelActive(playerId, player);
                player.lastTick = tick;
                return empty(Reason.LIMIT_REACHED);
            }
            weapon = weapons.get(held.serial);
        }
        if (weapon.heatTick != Long.MIN_VALUE && tick < weapon.heatTick) return noShot(weapon, tick, Reason.CLOCK_REWIND);
        player.lastTick = tick;
        if (!Objects.equals(player.weapon, held.serial)) {
            cancelActive(playerId, player);
            if (weapon.activeOwner != null && !weapon.activeOwner.equals(playerId)) return noShot(weapon, tick, Reason.WEAPON_BUSY);
            player.weapon = held.serial;
            weapon.activeOwner = playerId;
        }
        if (weapon.lastAdvanceTick == tick) return noShot(weapon, tick, Reason.DUPLICATE_TICK);
        weapon.lastAdvanceTick = tick;

        // Exclude this tick until we know whether it fired: consecutive automatic shots never cool.
        coolBeforeCurrentTick(weapon, tick);
        finishReloadSteps(weapon, tick);
        if (triggerHeld && !player.triggerHeld) {
            player.triggerStart = nativeReferenceRules && weapon.profile == GunProfile.BATTLE_PISTOL
                ? Math.max(tick, Math.max(player.nextShotTick, weapon.nextShotTick)) : tick;
            player.semiFired = false;
        } else if (!triggerHeld) {
            player.triggerStart = -1L;
            player.semiFired = false;
        }
        player.triggerHeld = triggerHeld;

        if (weapon.reloadOwner != null && usesShellReload(weapon.profile) && weapon.ammo > 0 && triggerHeld && !player.semiFired) {
            weapon.reloadOwner = null;
            weapon.reloadEnd = 0L;
            weapon.reloadClosing = false;
        }
        if (weapon.reloadOwner != null) return idle(weapon, tick, Reason.RELOADING);
        if (reloadRequested && !usesInfiniteFeed(weapon.profile)) {
            if (weapon.ammo < capacity(weapon.profile)) startReload(weapon, playerId, tick);
            return idle(weapon, tick, weapon.reloadOwner == null ? Reason.READY : Reason.RELOADING);
        }
        if (!triggerHeld || (weapon.profile.fireMode() == GunProfile.FireMode.SEMI && player.semiFired)) return idle(weapon, tick, Reason.READY);
        if (weapon.ammo == 0) {
            startReload(weapon, playerId, tick);
            // An empty semi-automatic click requests reload, never a delayed unsolicited shot.
            if (weapon.profile.fireMode() == GunProfile.FireMode.SEMI) player.semiFired = true;
            return idle(weapon, tick, Reason.RELOADING);
        }
        if (tick - player.triggerStart < weapon.profile.windupTicks()) return idle(weapon, tick, Reason.WINDING_UP);
        if (weapon.overheated) return idle(weapon, tick, Reason.OVERHEATED);
        if (tick < player.nextShotTick || tick < weapon.nextShotTick || tick == weapon.lastShotTick) return idle(weapon, tick, Reason.COOLDOWN);

        if (!usesInfiniteFeed(weapon.profile)) weapon.ammo--;
        weapon.lastShotTick = tick;
        weapon.nextShotTick = deadline(tick, intervalTicks(weapon.profile));
        player.nextShotTick = weapon.nextShotTick;
        player.semiFired = true;
        weapon.sequence++;
        if (weapon.profile.usesHeat()) {
            weapon.heat = Math.min(MAX_HEAT, weapon.heat + HEAT_PER_SHOT);
            if (weapon.heat >= MAX_HEAT) weapon.overheated = true;
        }
        weapon.heatTick = tick;
        weapon.reason = Reason.FIRED;
        Shot shot = new Shot(playerId, held.serial, weapon.profile, weapon.sequence, aimed, tick);
        return new Update(List.of(shot), snapshotOf(weapon, tick, Reason.FIRED), Reason.FIRED);
    }

    /** A read-only view. Reload commits and heat cooling occur when advance authorizes the next tick. */
    public synchronized Snapshot snapshot(UUID serial, long tick) {
        WeaponState weapon = weapons.get(serial);
        return weapon == null ? emptySnapshot(Reason.INVALID_WEAPON) : snapshotOf(weapon, tick, weapon.reason);
    }

    /** Records a release between tick samples without cancelling reload or resetting shot limits. */
    public synchronized void releaseTrigger(UUID playerId, UUID serial) {
        PlayerState player = players.get(playerId);
        if (player == null || serial == null || !serial.equals(player.weapon)) return;
        player.triggerHeld = false;
        player.triggerStart = -1L;
        player.semiFired = false;
    }

    /** Cancels use/reload and releases ownership; cooldown, ammunition, sequence and heat survive. */
    public synchronized void cancelPlayer(UUID playerId) {
        PlayerState player = players.get(playerId);
        if (player != null) cancelActive(playerId, player);
    }

    public synchronized void clear() {
        weapons.clear();
        players.clear();
    }

    public synchronized int registeredWeaponCount() { return weapons.size(); }
    public synchronized int trackedPlayerCount() { return players.size(); }

    private void cancelActive(UUID playerId, PlayerState player) {
        WeaponState previous = weapons.get(player.weapon);
        if (previous != null) {
            if (playerId.equals(previous.reloadOwner)) {
                previous.reloadOwner = null;
                previous.reloadEnd = 0L;
                previous.reloadClosing = false;
            }
            if (playerId.equals(previous.activeOwner)) previous.activeOwner = null;
        }
        player.weapon = null;
        player.triggerHeld = false;
        player.triggerStart = -1L;
        player.semiFired = false;
    }

    private void startReload(WeaponState weapon, UUID playerId, long tick) {
        weapon.reloadOwner = playerId;
        weapon.reloadClosing = false;
        weapon.reloadEnd = deadline(tick, usesShellReload(weapon.profile) ? (weapon.ammo <= 0 ? 49 : 12) + 13 : reloadTicks(weapon.profile, weapon.ammo));
    }

    private void finishReloadSteps(WeaponState weapon, long tick) {
        // At most one step per missing shell and one closing step; fire never catches up.
        while (weapon.reloadOwner != null && tick >= weapon.reloadEnd) {
            if (!usesShellReload(weapon.profile)) {
                weapon.ammo = capacity(weapon.profile);
                weapon.reloadOwner = null;
                weapon.reloadEnd = 0L;
            } else if (weapon.reloadClosing) {
                weapon.reloadOwner = null;
                weapon.reloadEnd = 0L;
                weapon.reloadClosing = false;
            } else {
                weapon.ammo = Math.min(capacity(weapon.profile), weapon.ammo + 1);
                weapon.reloadClosing = weapon.ammo == capacity(weapon.profile);
                weapon.reloadEnd = deadline(weapon.reloadEnd, weapon.reloadClosing ? 12 : 13);
            }
        }
    }

    private static void coolBeforeCurrentTick(WeaponState weapon, long tick) {
        if (weapon.heatTick == Long.MIN_VALUE) {
            weapon.heatTick = tick;
            return;
        }
        long elapsed = tick - weapon.heatTick;
        if (elapsed > 1L) cool(weapon, elapsed - 1L);
        weapon.heatTick = tick;
    }

    private static void cool(WeaponState weapon, long ticks) {
        if (weapon.profile.usesHeat() && ticks > 0L) {
            weapon.heat = Math.max(0.0D, weapon.heat - ticks * HEAT_COOLING_PER_TICK);
            if (weapon.overheated && weapon.heat <= HEAT_UNLOCK + 0.0000001D) weapon.overheated = false;
        }
    }

    private Update idle(WeaponState weapon, long tick, Reason reason) {
        cool(weapon, 1L);
        weapon.reason = reason;
        return noShot(weapon, tick, reason);
    }

    private Update noShot(WeaponState weapon, long tick, Reason reason) {
        return new Update(List.of(), weapon == null ? emptySnapshot(reason) : snapshotOf(weapon, tick, reason), reason);
    }

    private static Update empty(Reason reason) { return new Update(List.of(), emptySnapshot(reason), reason); }
    private static Snapshot emptySnapshot(Reason reason) { return new Snapshot(0, 0, 0L, 0.0D, false, reason); }
    private Snapshot snapshotOf(WeaponState weapon, long tick, Reason reason) {
        long remaining = weapon.reloadOwner == null ? 0L : Math.max(0L, weapon.reloadEnd - Math.max(0L, tick));
        if (weapon.reloadOwner != null && usesShellReload(weapon.profile) && !weapon.reloadClosing) {
            remaining = deadline(remaining, Math.max(0, capacity(weapon.profile) - weapon.ammo - 1) * 13 + 12);
        }
        return new Snapshot(weapon.ammo, capacity(weapon.profile), remaining, weapon.heat, weapon.overheated, reason);
    }
    private static long deadline(long tick, int duration) { return tick > Long.MAX_VALUE - duration ? Long.MAX_VALUE : tick + duration; }

    private static final class WeaponState {
        private final GunProfile profile;
        private int ammo;
        private long sequence;
        private long nextShotTick;
        private long lastShotTick = Long.MIN_VALUE;
        private long lastAdvanceTick = Long.MIN_VALUE;
        private UUID activeOwner;
        private UUID reloadOwner;
        private long reloadEnd;
        private boolean reloadClosing;
        private double heat;
        private boolean overheated;
        private long heatTick = Long.MIN_VALUE;
        private Reason reason = Reason.READY;
        private WeaponState(GunProfile profile, int ammo) { this.profile = profile; this.ammo = ammo; }
    }

    private static final class PlayerState {
        private UUID weapon;
        private long lastTick = Long.MIN_VALUE;
        private long nextShotTick;
        private boolean triggerHeld;
        private long triggerStart = -1L;
        private boolean semiFired;
    }
}
