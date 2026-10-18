package com.teamlifebind.common;

/** Public optic scalars inform an original pose system; no foreign keyframes are used. */
public record GunPresentationProfile(double ironMagnification, double modelAdsFov,
        double aimTimeSeconds, double pitchKickDegrees, double modelKick, int boltTicks,
        double aimHeight, double aimInset, boolean rotary) {
    public static GunPresentationProfile forGun(GunProfile gun) {
        return switch (gun) {
            case BATTLE_PISTOL -> new GunPresentationProfile(2.0D, 45.0D, 0.18D, 2.2D, 0.8D, 0, 0.025D, 0.12D, false);
            case TACTICAL_SMG -> new GunPresentationProfile(1.2D, 70.0D, 0.08D, 0.45D, 0.24D, 0, 0.055D, 0.18D, false);
            case ASSAULT_RIFLE -> new GunPresentationProfile(1.33D, 45.0D, 0.21D, 0.8D, 0.35D, 0, 0.045D, 0.17D, false);
            case BREACH_SHOTGUN -> new GunPresentationProfile(1.5D, 70.0D, 0.21D, 2.6D, 0.8D, 11, 0.045D, 0.12D, false);
            case MARKSMAN_RIFLE -> new GunPresentationProfile(1.5D, 33.0D, 0.23D, 4.0D, 1.0D, 29, 0.065D, 0.17D, false);
            case SUPPORT_MACHINE_GUN -> new GunPresentationProfile(2.0D, 55.0D, 0.25D, 0.35D, 0.25D, 0, -0.025D, 0.05D, true);
            case SIEGE_LAUNCHER -> new GunPresentationProfile(1.2D, 70.0D, 0.20D, 2.2D, 0.8D, 0, 0.055D, 0.14D, false);
        };
    }
}
