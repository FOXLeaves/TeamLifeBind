package com.teamlifebind.fabric;

import com.teamlifebind.common.TeamUtilityState;
import com.teamlifebind.common.TeamLifeBindCombatMath;
import com.teamlifebind.common.SupplyDropRules;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Platform effects and validation; round-local timers and ownership live in common. */
final class TeamUtilityController {
    private final TeamLifeBindFabricManager host;
    private final TeamUtilityState state = new TeamUtilityState();
    private final Map<UUID, HeldBandage> heldBandages = new HashMap<>();
    private final Map<String, SupplyDrop> drops = new HashMap<>();
    private final Map<ResourceKey<Level>, Long2ObjectMap<SupplyDrop>> lockedByDimension = new HashMap<>();

    TeamUtilityController(TeamLifeBindFabricManager host) { this.host = host; }

    ItemStack item(ServerPlayer viewer, boolean whistle) {
        ItemStack stack = new ItemStack(whistle ? TeamLifeBindFabric.RESONANCE_WHISTLE_ITEM : TeamLifeBindFabric.EMERGENCY_BANDAGE_ITEM);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(host.text(viewer, whistle ? "item.resonance_whistle.name" : "item.emergency_bandage.name")));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(host.text(viewer, whistle ? "item.resonance_whistle.lore" : "item.emergency_bandage.lore")))));
        refreshLore(stack, viewer);
        return stack;
    }

    boolean refreshLore(ItemStack stack, ServerPlayer viewer) {
        boolean whistle = stack.is(TeamLifeBindFabric.RESONANCE_WHISTLE_ITEM);
        if (!whistle && !stack.is(TeamLifeBindFabric.EMERGENCY_BANDAGE_ITEM)) return false;
        String locale = host.utilityLocale(viewer);
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag data = custom == null ? new CompoundTag() : custom.copyTag();
        if (locale.equals(data.getString("tlb_utility_locale").orElse("")) && stack.has(DataComponents.LORE)) return false;
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(host.text(viewer, whistle ? "item.resonance_whistle.lore" : "item.emergency_bandage.lore")))));
        data.putString("tlb_utility_locale", locale);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return true;
    }

    InteractionResult use(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean whistle = stack.is(TeamLifeBindFabric.RESONANCE_WHISTLE_ITEM);
        if (!whistle && !stack.is(TeamLifeBindFabric.EMERGENCY_BANDAGE_ITEM)) return InteractionResult.PASS;
        if (!host.utilityParticipant(player)) { message(player, "utility.match_only"); return InteractionResult.FAIL; }
        Integer team = host.utilityTeam(player);
        if (team == null) { message(player, "utility.team_required"); return InteractionResult.FAIL; }
        long tick = host.utilityTick();
        if (whistle) {
            HitResult hit = player.pick(TeamUtilityState.WHISTLE_RANGE_BLOCKS, 1.0F, false);
            Vec3 target = hit.getType() == HitResult.Type.MISS
                    ? player.getEyePosition().add(player.getLookAngle().scale(TeamUtilityState.WHISTLE_RANGE_BLOCKS)) : hit.getLocation();
            TeamUtilityState.TeamMark mark = state.tryMark(team, dimension(player.level()), target.x, target.y, target.z, tick);
            if (mark == null) { message(player, "utility.cooldown", seconds(state.whistleCooldownRemainingTicks(team, tick))); return InteractionResult.SUCCESS; }
            for (ServerPlayer mate : host.utilityTeamPlayers(team)) {
                if (dimension(mate.level()).equals(mark.dimension())) message(mate, "utility.whistle.mark", player.getScoreboardName(), coordinate(mark.x()), coordinate(mark.y()), coordinate(mark.z()));
                else message(mate, "utility.whistle.other_dimension", player.getScoreboardName(), mark.dimension(), coordinate(mark.x()), coordinate(mark.y()), coordinate(mark.z()));
            }
            return InteractionResult.SUCCESS;
        }
        TeamUtilityState.Channel current = state.channelForPlayer(player.getUUID());
        if (current != null) { cancel(player.getUUID(), true); return InteractionResult.SUCCESS; }
        if (!host.utilityCanHeal(player)) { message(player, "utility.bandage.full_health"); return InteractionResult.SUCCESS; }
        long remaining = state.bandageCooldownRemainingTicks(team, tick);
        if (remaining > 0L) { message(player, "utility.cooldown", seconds(remaining)); return InteractionResult.SUCCESS; }
        String signature = hand == InteractionHand.MAIN_HAND ? "main:" + player.getInventory().getSelectedSlot() : "off:40";
        TeamUtilityState.Channel channel = state.startBandage(player.getUUID(), team, dimension(player.level()), signature, tick);
        if (channel == null) { message(player, "utility.channel_busy"); return InteractionResult.SUCCESS; }
        heldBandages.put(player.getUUID(), new HeldBandage(hand, player.getInventory().getSelectedSlot(), stack.copy()));
        message(player, "utility.bandage.started", seconds(TeamUtilityState.BANDAGE_CHANNEL_TICKS));
        return InteractionResult.SUCCESS;
    }

    InteractionResult useSupply(ServerPlayer player, BlockPos pos) {
        String id = dropId(player.level(), pos);
        SupplyDrop drop = drops.get(id);
        if (drop == null || state.isSupplyClaimed(id)) return InteractionResult.PASS;
        if (!validDrop(drop)) { message(player, "supply.claim.locked"); return InteractionResult.FAIL; }
        if (!player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) { message(player, "supply.claim.locked"); return InteractionResult.FAIL; }
        if (!host.utilityParticipant(player)) { message(player, "supply.claim.locked"); return InteractionResult.FAIL; }
        Integer team = host.utilityTeam(player);
        if (team == null) { message(player, "utility.team_required"); return InteractionResult.FAIL; }
        if (player.position().distanceToSqr(Vec3.atCenterOf(pos)) > SupplyDropRules.CLAIM_RADIUS_BLOCKS * SupplyDropRules.CLAIM_RADIUS_BLOCKS) {
            message(player, "supply.claim.too_far"); return InteractionResult.FAIL;
        }
        TeamUtilityState.Channel current = state.channelForPlayer(player.getUUID());
        if (current != null) { cancel(player.getUUID(), true); return InteractionResult.SUCCESS; }
        if (state.startSupplyClaim(player.getUUID(), team, dimension(player.level()), id, host.utilityTick()) == null) message(player, "utility.channel_busy");
        else message(player, "supply.claim.started", seconds(TeamUtilityState.SUPPLY_CLAIM_TICKS));
        return InteractionResult.SUCCESS;
    }

    void registerSupply(ServerLevel level, BlockPos pos, int index) {
        SupplyDrop drop = new SupplyDrop(level, pos.immutable(), index);
        drops.put(dropId(level, pos), drop);
        lockedByDimension.computeIfAbsent(level.dimension(), ignored -> new Long2ObjectOpenHashMap<>()).put(pos.asLong(), drop);
    }

    boolean locked(Level level, BlockPos pos) {
        if (lockedByDimension.isEmpty()) return false;
        Long2ObjectMap<SupplyDrop> dimensionDrops = lockedByDimension.get(level.dimension());
        return dimensionDrops != null && validDrop(dimensionDrops.get(pos.asLong()));
    }

    void tick(MinecraftServer server) {
        long tick = host.utilityTick();
        for (TeamUtilityState.Channel channel : state.channels()) {
            if (state.channelForPlayer(channel.playerId()) != channel) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(channel.playerId());
            if (player == null || !host.utilityParticipant(player) || !Integer.valueOf(channel.team()).equals(host.utilityTeam(player))
                    || !channel.dimension().equals(dimension(player.level()))) { cancel(channel.playerId(), player != null); continue; }
            if (channel.kind() == TeamUtilityState.ChannelKind.BANDAGE) {
                HeldBandage held = heldBandages.get(channel.playerId());
                if (held == null || (held.hand() == InteractionHand.MAIN_HAND && held.slot() != player.getInventory().getSelectedSlot())
                        || !ItemStack.isSameItemSameComponents(held.stack(), player.getItemInHand(held.hand())) || player.getItemInHand(held.hand()).isEmpty()) {
                    cancel(channel.playerId(), true); continue;
                }
                if (TeamUtilityState.remainingChannelTicks(channel, tick) == 0L) {
                    if (!host.utilityCanHeal(player)) { cancel(channel.playerId(), false); message(player, "utility.bandage.full_health"); continue; }
                    if (state.completeBandage(channel, tick)) {
                        player.getItemInHand(held.hand()).shrink(1);
                        heldBandages.remove(player.getUUID());
                        float restored = host.utilityHeal(player, (float) TeamUtilityState.BANDAGE_HEAL_HP);
                        message(player, "utility.bandage.complete", restored);
                    }
                } else if (tick % 20L == 0L) message(player, "utility.bandage.progress", seconds(TeamUtilityState.remainingChannelTicks(channel, tick)));
            } else {
                SupplyDrop drop = drops.get(channel.dropId());
                if (drop == null || !locked(drop.level(), drop.pos())
                        || player.position().distanceToSqr(Vec3.atCenterOf(drop.pos())) > SupplyDropRules.CLAIM_RADIUS_BLOCKS * SupplyDropRules.CLAIM_RADIUS_BLOCKS) {
                    cancel(channel.playerId(), true); continue;
                }
                if (TeamUtilityState.remainingChannelTicks(channel, tick) == 0L) {
                    if (finishSupplyClaim(channel, tick)) message(player, "supply.claim.complete");
                } else if (tick % 20L == 0L) message(player, "supply.claim.progress", seconds(TeamUtilityState.remainingChannelTicks(channel, tick)));
            }
        }
        if (tick % 20L != 0L) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!host.utilityParticipant(player) || state.channelForPlayer(player.getUUID()) != null) continue;
            Integer team = host.utilityTeam(player);
            TeamUtilityState.TeamMark mark = team == null ? null : state.markForTeam(team, tick);
            if (mark == null || host.campaignScoutHud(player)) continue;
            long remaining = seconds(TeamUtilityState.remainingMarkTicks(mark, tick));
            if (!dimension(player.level()).equals(mark.dimension())) message(player, "utility.whistle.actionbar.other_dimension", mark.dimension(), coordinate(mark.x()), coordinate(mark.y()), coordinate(mark.z()), remaining);
            else {
                double dx = mark.x() - player.getX(), dy = mark.y() - player.getY(), dz = mark.z() - player.getZ();
                String arrow = TeamLifeBindCombatMath.trackingArrow(player.getYRot(), player.getX(), player.getZ(), mark.x(), mark.z());
                message(player, "utility.whistle.actionbar", arrow, Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)), coordinate(mark.x()), coordinate(mark.y()), coordinate(mark.z()), remaining);
            }
        }
    }

    private boolean finishSupplyClaim(TeamUtilityState.Channel channel, long tick) {
        SupplyDrop drop = drops.get(channel.dropId());
        if (drop == null || !locked(drop.level(), drop.pos())) return false;
        List<TeamUtilityState.Channel> competing = state.channels().stream()
                .filter(other -> other.kind() == TeamUtilityState.ChannelKind.SUPPLY_CLAIM && other.dropId().equals(channel.dropId()) && other != channel).toList();
        if (!state.completeSupplyClaim(channel, tick)) return false;
        Long2ObjectMap<SupplyDrop> dimensionDrops = lockedByDimension.get(drop.level().dimension());
        dimensionDrops.remove(drop.pos().asLong());
        if (dimensionDrops.isEmpty()) lockedByDimension.remove(drop.level().dimension());
        drops.remove(channel.dropId());
        host.utilityFillSupply(drop.level(), drop.pos());
        host.campaignSupplyClaim(channel.team());
        for (TeamUtilityState.Channel loser : competing) {
            ServerPlayer claimant = host.utilityServer().getPlayerList().getPlayer(loser.playerId());
            if (claimant != null) message(claimant, "supply.claim.claimed");
        }
        return true;
    }

    boolean hasChannel(ServerPlayer player) { return state.channelForPlayer(player.getUUID()) != null; }

    boolean hasHud(ServerPlayer player) {
        if (!host.utilityParticipant(player)) return false;
        if (state.channelForPlayer(player.getUUID()) != null) return true;
        Integer team = host.utilityTeam(player);
        return team != null && state.markForTeam(team, host.utilityTick()) != null;
    }
    void consumed(ServerPlayer player, InteractionHand hand) {
        HeldBandage held = heldBandages.get(player.getUUID());
        if (held != null && held.hand() == hand && (hand == InteractionHand.OFF_HAND || held.slot() == player.getInventory().getSelectedSlot())) cancel(player.getUUID(), true);
    }
    void swapHands(ServerPlayer player) {
        if (heldBandages.containsKey(player.getUUID())) cancel(player.getUUID(), true);
    }
    void slotChanged(ServerPlayer player) {
        HeldBandage held = heldBandages.get(player.getUUID());
        if (held != null && held.hand() == InteractionHand.MAIN_HAND && held.slot() != player.getInventory().getSelectedSlot()) cancel(player.getUUID(), true);
    }
    void cancel(UUID playerId, boolean notify) {
        boolean removed = state.cancel(playerId);
        heldBandages.remove(playerId);
        ServerPlayer player = host.utilityServer() == null ? null : host.utilityServer().getPlayerList().getPlayer(playerId);
        if (removed && notify && player != null) message(player, "utility.cancelled");
    }

    private boolean validDrop(SupplyDrop drop) {
        return drop != null && drop.level().getBlockState(drop.pos()).is(Blocks.BARREL)
                && drop.level().getBlockEntity(drop.pos()) instanceof BaseContainerBlockEntity;
    }
    void clear() { state.clear(); heldBandages.clear(); drops.clear(); lockedByDimension.clear(); }
    private void message(ServerPlayer player, String key, Object... arguments) { player.sendSystemMessage(Component.literal(host.campaignOverlay(player, host.text(player, key, arguments))), true); }
    private static String dimension(Level level) { return level.dimension().identifier().toString(); }
    private static String dropId(Level level, BlockPos pos) { return dimension(level) + ":" + pos.asLong(); }
    private static long seconds(long ticks) { return (ticks + 19L) / 20L; }
    private static int coordinate(double value) { return (int) Math.floor(value); }
    private record HeldBandage(InteractionHand hand, int slot, ItemStack stack) { }
    private record SupplyDrop(ServerLevel level, BlockPos pos, int index) { }
}
