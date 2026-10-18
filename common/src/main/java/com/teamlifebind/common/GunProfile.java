package com.teamlifebind.common;

/** Original air-drop weapon tuning. Values are shared by every platform adapter. */
public enum GunProfile {
    BATTLE_PISTOL("battle_pistol", FireMode.SEMI, 5, 12, 44, 5, 1, 0.58D, 18.0D, 1.45D, 48.0D, 3.0D, 0.6D),
    TACTICAL_SMG("tactical_smg", FireMode.AUTO, 30, 1, 44, 0, 1, 0.12D, 4.0D, 1.25D, 40.0D, 4.0D, 1.0D),
    ASSAULT_RIFLE("assault_rifle", FireMode.AUTO, 20, 2, 50, 0, 1, 0.20D, 7.0D, 1.35D, 72.0D, 2.8D, 0.5D),
    BREACH_SHOTGUN("breach_shotgun", FireMode.SEMI, 8, 12, 60, 0, 8, 0.62D, 20.0D, 1.1D, 22.0D, 8.0D, 5.0D),
    MARKSMAN_RIFLE("marksman_rifle", FireMode.SEMI, 5, 28, 68, 0, 1, 0.56D, 32.0D, 1.5D, 100.0D, 5.0D, 0.08D),
    SUPPORT_MACHINE_GUN("support_machine_gun", FireMode.AUTO, 120, 1, 96, 8, 1, 0.10D, 4.0D, 1.2D, 56.0D, 4.5D, 1.4D),
    SIEGE_LAUNCHER("siege_launcher", FireMode.SEMI, 1, 20, 64, 0, 1, 0.25D, 12.0D, 1.0D, 72.0D, 2.0D, 0.4D);

    public enum FireMode { SEMI, AUTO }

    private final String id;
    private final FireMode fireMode;
    private final int capacity;
    private final int intervalTicks;
    private final int reloadTicks;
    private final int windupTicks;
    private final int pellets;
    private final double playerDamageFraction;
    private final double mobDamage;
    private final double headMultiplier;
    private final double range;
    private final double hipSpreadDegrees;
    private final double aimSpreadDegrees;

    GunProfile(String id, FireMode fireMode, int capacity, int intervalTicks, int reloadTicks, int windupTicks,
               int pellets, double playerDamageFraction, double mobDamage, double headMultiplier,
               double range, double hipSpreadDegrees, double aimSpreadDegrees) {
        this.id = id;
        this.fireMode = fireMode;
        this.capacity = capacity;
        this.intervalTicks = intervalTicks;
        this.reloadTicks = reloadTicks;
        this.windupTicks = windupTicks;
        this.pellets = pellets;
        this.playerDamageFraction = playerDamageFraction;
        this.mobDamage = mobDamage;
        this.headMultiplier = headMultiplier;
        this.range = range;
        this.hipSpreadDegrees = hipSpreadDegrees;
        this.aimSpreadDegrees = aimSpreadDegrees;
    }

    public String id() { return id; }
    public FireMode fireMode() { return fireMode; }
    public int capacity() { return capacity; }
    public int intervalTicks() { return intervalTicks; }
    public int reloadTicks() { return reloadTicks; }
    public int windupTicks() { return windupTicks; }
    public int pellets() { return pellets; }
    public double playerDamageFraction() { return playerDamageFraction; }
    public double mobDamage() { return mobDamage; }
    public double headMultiplier() { return headMultiplier; }
    public double range() { return range; }
    public double hipSpreadDegrees() { return hipSpreadDegrees; }
    public double aimSpreadDegrees() { return aimSpreadDegrees; }
    public boolean isLauncher() { return this == SIEGE_LAUNCHER; }
    public boolean usesHeat() { return this == SUPPORT_MACHINE_GUN; }
    public double spreadDegrees(boolean aimed) { return aimed ? aimSpreadDegrees : hipSpreadDegrees; }
    public double rocketSpeed() { return isLauncher() ? 2.4D : 0.0D; }
    public double rocketGravity() { return isLauncher() ? 0.025D : 0.0D; }
    public int rocketLifetimeTicks() { return isLauncher() ? 40 : 0; }
    public double blastRadius() { return isLauncher() ? 3.5D : 0.0D; }
    public double splashPlayerDamageFraction() { return isLauncher() ? 0.55D : 0.0D; }
    public double splashMobDamage() { return isLauncher() ? 24.0D : 0.0D; }

    public static GunProfile byId(String id) {
        if (id == null) return null;
        for (GunProfile profile : values()) if (profile.id.equals(id)) return profile;
        return null;
    }
}
