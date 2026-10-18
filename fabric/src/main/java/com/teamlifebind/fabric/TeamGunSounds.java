package com.teamlifebind.fabric;

import com.teamlifebind.common.GunProfile;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Original short sound samples use direct holders, so the packet carries their resource IDs. */
final class TeamGunSounds {
    private static final Map<GunProfile, SoundEvent> FIRE = new EnumMap<>(GunProfile.class);
    static final SoundEvent RELOAD_START = event("reload_start", 16);
    static final SoundEvent RELOAD_END = event("reload_end", 16);
    static final SoundEvent SHELL_INSERT = event("shell_insert", 16);
    static final SoundEvent BOLT = event("bolt", 16);
    static final SoundEvent SPIN_START = event("spin_start", 24);
    static final SoundEvent SPIN_STOP = event("spin_stop", 24);

    static {
        for (GunProfile profile : GunProfile.values()) {
            int range = switch (profile) {
                case BATTLE_PISTOL -> 48;
                case TACTICAL_SMG, BREACH_SHOTGUN -> 40;
                case ASSAULT_RIFLE, SUPPORT_MACHINE_GUN, SIEGE_LAUNCHER -> 64;
                case MARKSMAN_RIFLE -> 80;
            };
            FIRE.put(profile, event("fire." + profile.id(), range));
        }
    }

    private TeamGunSounds() {}

    private static SoundEvent event(String id, int range) {
        return SoundEvent.createFixedRangeEvent(Identifier.fromNamespaceAndPath("teamlifebind", "gun." + id), range);
    }

    static void fire(ServerPlayer player, GunProfile profile) { play(player, FIRE.get(profile), profile.isLauncher() ? 1.2F : 0.8F); }

    static void play(ServerPlayer player, SoundEvent event) { play(player, event, 0.65F); }

    private static void play(ServerPlayer player, SoundEvent event, float volume) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), event, SoundSource.PLAYERS, volume, 1.0F);
    }
}
