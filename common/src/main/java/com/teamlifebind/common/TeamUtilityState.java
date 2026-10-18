package com.teamlifebind.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Round-local team marks and interruptible channels. Platform adapters validate the player,
 * dimension, held item, and claim distance before completion, then apply the successful effect.
 * Clear this state at every round transition. Cancellation never consumes an item or cooldown.
 */
public final class TeamUtilityState {
    public static final double WHISTLE_RANGE_BLOCKS = 64.0D;
    public static final long WHISTLE_MARK_TICKS = 240L;
    public static final long WHISTLE_COOLDOWN_TICKS = 300L;
    public static final long BANDAGE_CHANNEL_TICKS = 80L;
    public static final long BANDAGE_COOLDOWN_TICKS = 600L;
    public static final double BANDAGE_HEAL_HP = 2.0D;
    public static final long SUPPLY_CLAIM_TICKS = SupplyDropRules.CLAIM_TICKS;

    private final Map<Integer, TeamMark> marks = new HashMap<>();
    private final TeamTotemCooldown whistleCooldown = new TeamTotemCooldown();
    private final TeamTotemCooldown bandageCooldown = new TeamTotemCooldown();
    private final Map<UUID, Channel> channelsByPlayer = new HashMap<>();
    private final Map<TeamChannelKey, UUID> channelPlayersByTeam = new HashMap<>();
    private final Set<String> claimedDrops = new HashSet<>();

    public synchronized TeamMark tryMark(int team, String dimension, double x, double y, double z, long tick) {
        if (!validTeamDimension(team, dimension) || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
            || !whistleCooldown.tryAcquire(team, tick, WHISTLE_COOLDOWN_TICKS)) {
            return null;
        }
        TeamMark mark = new TeamMark(team, dimension, x, y, z, tick);
        marks.put(team, mark);
        return mark;
    }

    public synchronized TeamMark markForTeam(int team, long tick) {
        TeamMark mark = marks.get(team);
        if (mark != null && remainingMarkTicks(mark, tick) == 0L) {
            marks.remove(team);
            return null;
        }
        return mark;
    }

    public synchronized long whistleCooldownRemainingTicks(int team, long tick) {
        return whistleCooldown.remainingTicks(team, tick);
    }

    public synchronized Channel startBandage(UUID playerId, int team, String dimension, String slotSignature, long tick) {
        if (slotSignature == null || slotSignature.isBlank() || !bandageCooldown.isReady(team, tick)) {
            return null;
        }
        return startChannel(playerId, team, dimension, ChannelKind.BANDAGE, slotSignature, "", tick);
    }

    public synchronized Channel startSupplyClaim(UUID playerId, int team, String dimension, String dropId, long tick) {
        if (dropId == null || dropId.isBlank() || claimedDrops.contains(dropId)) {
            return null;
        }
        return startChannel(playerId, team, dimension, ChannelKind.SUPPLY_CLAIM, "", dropId, tick);
    }

    /** Call only after the adapter confirms healing is still needed and the held item is unchanged. */
    public synchronized boolean completeBandage(Channel channel, long tick) {
        if (!canComplete(channel, ChannelKind.BANDAGE, tick)
            || !bandageCooldown.tryAcquire(channel.team(), tick, BANDAGE_COOLDOWN_TICKS)) {
            return false;
        }
        removeChannel(channel);
        return true;
    }

    /** Exactly one claimant can authorize filling a drop, even when competing teams finish together. */
    public synchronized boolean completeSupplyClaim(Channel channel, long tick) {
        if (!canComplete(channel, ChannelKind.SUPPLY_CLAIM, tick) || !claimedDrops.add(channel.dropId())) {
            return false;
        }
        for (Channel pending : new ArrayList<>(channelsByPlayer.values())) {
            if (pending.kind() == ChannelKind.SUPPLY_CLAIM && pending.dropId().equals(channel.dropId())) {
                removeChannel(pending);
            }
        }
        return true;
    }

    public synchronized Channel channelForPlayer(UUID playerId) {
        return channelsByPlayer.get(playerId);
    }

    public synchronized Channel channelForTeam(int team, ChannelKind kind) {
        UUID playerId = channelPlayersByTeam.get(new TeamChannelKey(team, kind));
        return playerId == null ? null : channelsByPlayer.get(playerId);
    }

    public synchronized List<Channel> channels() {
        return List.copyOf(channelsByPlayer.values());
    }

    public synchronized boolean cancel(UUID playerId) {
        Channel channel = channelsByPlayer.get(playerId);
        if (channel == null) {
            return false;
        }
        removeChannel(channel);
        return true;
    }

    public synchronized boolean cancel(Channel channel) {
        if (channel == null || channelsByPlayer.get(channel.playerId()) != channel) {
            return false;
        }
        removeChannel(channel);
        return true;
    }

    public synchronized long bandageCooldownRemainingTicks(int team, long tick) {
        return bandageCooldown.remainingTicks(team, tick);
    }

    public synchronized boolean isSupplyClaimed(String dropId) {
        return claimedDrops.contains(dropId);
    }

    public synchronized void clear() {
        marks.clear();
        whistleCooldown.clear();
        bandageCooldown.clear();
        channelsByPlayer.clear();
        channelPlayersByTeam.clear();
        claimedDrops.clear();
    }

    public static int supplyCountForTeams(int activeTeams) {
        return SupplyDropRules.resolveCount(1, true, activeTeams);
    }

    public static long remainingChannelTicks(Channel channel, long tick) {
        if (channel == null) {
            return 0L;
        }
        long duration = channel.kind() == ChannelKind.BANDAGE ? BANDAGE_CHANNEL_TICKS : SUPPLY_CLAIM_TICKS;
        return remainingTicks(channel.startTick(), duration, tick);
    }

    public static long remainingMarkTicks(TeamMark mark, long tick) {
        return mark == null ? 0L : remainingTicks(mark.startTick(), WHISTLE_MARK_TICKS, tick);
    }

    private Channel startChannel(UUID playerId, int team, String dimension, ChannelKind kind, String slotSignature, String dropId, long tick) {
        TeamChannelKey teamKey = new TeamChannelKey(team, kind);
        if (playerId == null || !validTeamDimension(team, dimension) || channelsByPlayer.containsKey(playerId)
            || channelPlayersByTeam.containsKey(teamKey)) {
            return null;
        }
        Channel channel = new Channel(playerId, team, dimension, kind, slotSignature, dropId, tick);
        channelsByPlayer.put(playerId, channel);
        channelPlayersByTeam.put(teamKey, playerId);
        return channel;
    }

    private boolean canComplete(Channel channel, ChannelKind kind, long tick) {
        return channel != null && channel.kind() == kind && channelsByPlayer.get(channel.playerId()) == channel
            && remainingChannelTicks(channel, tick) == 0L;
    }

    private void removeChannel(Channel channel) {
        channelsByPlayer.remove(channel.playerId());
        channelPlayersByTeam.remove(new TeamChannelKey(channel.team(), channel.kind()));
    }

    private static boolean validTeamDimension(int team, String dimension) {
        return team > 0 && dimension != null && !dimension.isBlank();
    }

    private static long remainingTicks(long startTick, long durationTicks, long tick) {
        if (tick < startTick) {
            return durationTicks;
        }
        long elapsed = tick - startTick;
        return Long.compareUnsigned(elapsed, durationTicks) >= 0 ? 0L : durationTicks - elapsed;
    }

    public enum ChannelKind {
        BANDAGE,
        SUPPLY_CLAIM
    }

    public record TeamMark(int team, String dimension, double x, double y, double z, long startTick) {
    }

    public record Channel(UUID playerId, int team, String dimension, ChannelKind kind, String slotSignature, String dropId, long startTick) {
    }

    private record TeamChannelKey(int team, ChannelKind kind) {
    }
}
