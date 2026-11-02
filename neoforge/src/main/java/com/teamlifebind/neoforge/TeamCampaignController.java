package com.teamlifebind.neoforge;

import com.teamlifebind.common.CampaignRules;
import com.teamlifebind.common.DragonVictoryMode;
import com.teamlifebind.common.MatchMode;
import com.teamlifebind.common.PlayMode;
import com.teamlifebind.common.PveDifficulty;
import com.teamlifebind.common.RoundCampaignState;
import com.teamlifebind.common.RoundCampaignState.StageTask;
import com.teamlifebind.common.TeamLifeBindCombatMath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;

/** Round rules are shared; this adapter owns only Minecraft entities, menus and world effects. */
final class TeamCampaignController {
    private final TeamLifeBindNeoForgeManager host;
    private final RoundCampaignState state = new RoundCampaignState();
    private CampaignRules configured = CampaignRules.defaults();
    private final Set<Integer> destroyedBeds = new HashSet<>();
    private final Map<ResourceKey<Level>, BorderSnapshot> savedBorders = new HashMap<>();
    private final Set<Integer> pendingDisconnectWipes = new HashSet<>();
    private final Map<UUID, Long> interactionTicks = new HashMap<>();
    private Anchor controlPoint;
    private long lastHudTick = -1L;
    private long startTick;
    private double borderStartRadius;
    private boolean borderStarted;

    TeamCampaignController(TeamLifeBindNeoForgeManager host) { this.host = host; }
    CampaignRules rules() { return state.isActive() ? state.rules() : configured; }
    boolean survivalAdmission() { return state.isActive() && rules().playMode() == PlayMode.SUR && state.eligibleTeams().contains(1); }
    void load(Properties properties) {
        if (host.campaignSessionActive()) return;
        int credits;
        try { credits = Integer.parseInt(properties.getProperty("team-lives", "3")); }
        catch (NumberFormatException ignored) { credits = 3; }
        configured = new CampaignRules(MatchMode.parse(properties.getProperty("match-mode")), credits,
                DragonVictoryMode.parse(properties.getProperty("dragon-victory")),
                Boolean.parseBoolean(properties.getProperty("control-point-enabled", "false")),
                Boolean.parseBoolean(properties.getProperty("border-enabled", "false")), PlayMode.parse(properties.getProperty("play-mode")),
                PveDifficulty.parse(properties.getProperty("pve-difficulty")));
    }
    void save(Properties properties) {
        properties.setProperty("play-mode", configured.playMode().name());
        properties.setProperty("pve-difficulty", configured.pveDifficulty().name());
        properties.setProperty("match-mode", configured.matchMode().name());
        properties.setProperty("team-lives", Integer.toString(configured.respawnCredits()));
        properties.setProperty("dragon-victory", configured.dragonVictoryMode().name());
        properties.setProperty("control-point-enabled", Boolean.toString(configured.controlPointEnabled()));
        properties.setProperty("border-enabled", Boolean.toString(configured.borderEnabled()));
    }
    String change(ServerPlayer viewer, String key, String value) {
        if (host.campaignSessionActive()) return host.text(viewer, "config.campaign.active_round");
        CampaignRules old = configured;
        try {
            configured = switch (key) {
                case "pve" -> new CampaignRules(old.matchMode(), old.respawnCredits(), old.dragonVictoryMode(), old.controlPointEnabled(), old.borderEnabled(), old.playMode(), PveDifficulty.valueOf(value.toUpperCase(Locale.ROOT)));
                case "play" -> new CampaignRules(old.matchMode(), old.respawnCredits(), old.dragonVictoryMode(), old.controlPointEnabled(), old.borderEnabled(), PlayMode.valueOf(value.toUpperCase(Locale.ROOT)), old.pveDifficulty());
                case "mode" -> {
                    String requested = value.toUpperCase(Locale.ROOT);
                    if (requested.equals("SUR") || requested.equals("PVE") || requested.equals("PVP") || requested.equals("RG"))
                        yield new CampaignRules(old.matchMode(), old.respawnCredits(), old.dragonVictoryMode(), old.controlPointEnabled(), old.borderEnabled(), PlayMode.valueOf(requested), old.pveDifficulty());
                    yield new CampaignRules(MatchMode.valueOf(requested), old.respawnCredits(), old.dragonVictoryMode(), old.controlPointEnabled(), old.borderEnabled(), old.playMode(), old.pveDifficulty());
                }
                case "dragon" -> new CampaignRules(old.matchMode(), old.respawnCredits(), DragonVictoryMode.valueOf(value.toUpperCase(Locale.ROOT)), old.controlPointEnabled(), old.borderEnabled(), old.playMode(), old.pveDifficulty());
                case "objectives" -> new CampaignRules(old.matchMode(), old.respawnCredits(), old.dragonVictoryMode(), parseToggle(value), old.borderEnabled(), old.playMode(), old.pveDifficulty());
                case "border" -> new CampaignRules(old.matchMode(), old.respawnCredits(), old.dragonVictoryMode(), old.controlPointEnabled(), parseToggle(value), old.playMode(), old.pveDifficulty());
                case "lives" -> new CampaignRules(old.matchMode(), Math.max(1, Math.min(10, Integer.parseInt(value))), old.dragonVictoryMode(), old.controlPointEnabled(), old.borderEnabled(), old.playMode(), old.pveDifficulty());
                default -> throw new IllegalArgumentException("Unknown campaign option");
            };
        } catch (IllegalArgumentException invalid) { return host.text(viewer, "campaign.config.invalid", key); }
        host.campaignSaveSettings();
        return host.text(viewer, "config.campaign.updated", key, value.toUpperCase(Locale.ROOT));
    }
    private static boolean parseToggle(String value) {
        if ("on".equalsIgnoreCase(value)) return true;
        if ("off".equalsIgnoreCase(value)) return false;
        throw new IllegalArgumentException("Expected on or off");
    }

    void start() {
        clear();
        state.start(host.campaignAssignments(), configured, host.utilityTick());
        startTick = host.utilityTick();
        borderStartRadius = host.campaignInitialBorderRadius();
        controlPoint = host.campaignCenter();
        if (rules().controlPointEnabled() && controlPoint != null) {
            broadcast("campaign.point.location", controlPoint.dimension().identifier(), controlPoint.pos().getX(), controlPoint.pos().getY(), controlPoint.pos().getZ());
        }
        if (rules().borderEnabled()) {
            for (Anchor anchor : host.campaignBorderCenters()) {
                ServerLevel world = host.utilityServer().getLevel(anchor.dimension());
                if (world == null) continue;
                WorldBorder border = world.getWorldBorder();
                savedBorders.put(anchor.dimension(), new BorderSnapshot(border.getCenterX(), border.getCenterZ(), border.getSize(), border.getDamagePerBlock(), border.getSafeZone(), border.getWarningTime(), border.getWarningBlocks(), anchor));
            }
        }
    }
    void clear() {
        MinecraftServer server = host.utilityServer();
        if (server != null) savedBorders.forEach((dimension, snapshot) -> {
            ServerLevel world = server.getLevel(dimension);
            if (world == null) return;
            WorldBorder border = world.getWorldBorder();
            border.setCenter(snapshot.x(), snapshot.z()); border.setSize(snapshot.size());
            border.setDamagePerBlock(snapshot.damage()); border.setSafeZone(snapshot.safeZone());
            border.setWarningTime(snapshot.warningTime()); border.setWarningBlocks(snapshot.warningBlocks());
        });
        savedBorders.clear(); destroyedBeds.clear(); pendingDisconnectWipes.clear(); interactionTicks.clear(); state.clear(); controlPoint = null; lastHudTick = -1L; borderStarted = false;
    }
    void disconnect(UUID id) {
        boolean combat = state.inCombat(id, host.utilityTick());
        state.disconnect(id, host.utilityTick(), combat);
        Integer team = state.teamForPlayer(id);
        ServerPlayer player = host.utilityServer() == null ? null : host.utilityServer().getPlayerList().getPlayer(id);
        if (team != null && state.isActive()) teamChat(team, combat ? "campaign.reconnect.combat" : "campaign.reconnect.safe", player == null ? id : player.getScoreboardName(), seconds(RoundCampaignState.RECONNECT_GRACE_TICKS));
    }
    void join(ServerPlayer player) {
        state.addCooperativePlayer(player.getUUID(), host.utilityTick());
        RoundCampaignState.ReconnectResult reconnect = state.join(player.getUUID(), host.utilityTick());
        if (reconnect.penaltyDue() && reconnect.team() != null && !host.campaignTeamWipe(reconnect.team(), state.disconnectForfeit(reconnect.team()))) pendingDisconnectWipes.add(reconnect.team());
        if (reconnect.locked() || eliminated(player.getUUID())) host.campaignLockPlayer(player);
        else if (reconnect.pendingRespawn() || reconnect.penaltyDue()) host.campaignPendingOfflineRespawn(player);
        if (reconnect.team() != null && state.isActive()) chat(player, "campaign.reconnect.returned", player.getScoreboardName());
    }
    void acknowledgePendingRespawn(UUID id) { state.acknowledgePendingRespawn(id); }
    boolean eliminated(UUID id) {
        Integer team = state.teamForPlayer(id);
        return team != null && state.isEliminated(team);
    }
    void damaged(ServerPlayer player, DamageSource source, float amount) {
        if (amount <= 0.0F || !host.utilityParticipant(player)) return;
        state.recordDamage(player.getUUID(), host.utilityTick());
        if (source.getEntity() instanceof ServerPlayer attacker && host.utilityParticipant(attacker)) state.recordDamage(attacker.getUUID(), host.utilityTick());
    }
    void attack(ServerPlayer attacker, net.minecraft.world.entity.Entity target) {
        if (!host.utilityParticipant(attacker) || !(target instanceof net.minecraft.world.entity.LivingEntity)) return;
        if (target instanceof ServerPlayer victim && host.utilityTeam(attacker).equals(host.utilityTeam(victim))) return;
        state.recordDamage(attacker.getUUID(), host.utilityTick());
    }
    void wipe(int team, boolean permanent) {
        RoundCampaignState.WipeResult result = state.applyWipe(team, permanent, host.utilityTick());
        if (!result.applied()) return;
        if (result.eliminated()) {
            host.campaignLockTeam(team);
            broadcast("campaign.lives.eliminated", team);
        } else if (rules().limitedRespawns()) teamChat(team, "campaign.lives.remaining", team, result.remainingRespawnCredits());
    }
    void dragonDamage(EnderDragon dragon, DamageSource source, float healthBefore, float actualDamage) {
        if (!state.isActive() || !(source.getEntity() instanceof ServerPlayer player) || !host.utilityParticipant(player)) return;
        Integer team = host.utilityTeam(player);
        if (team != null && actualDamage > 0.0F) {
            state.recordDamage(player.getUUID(), host.utilityTick());
            state.recordDragonDamage(team, dragon.getUUID(), actualDamage, healthBefore, host.utilityTick());
        }
    }
    Integer dragonWinner(EnderDragon dragon, DamageSource source) {
        Integer lastHit = source.getEntity() instanceof ServerPlayer player && host.utilityParticipant(player) ? host.utilityTeam(player) : null;
        return state.chooseDragonWinner(dragon.getUUID(), lastHit, host.utilityTick());
    }

    ItemStack item(ServerPlayer viewer, boolean silence) {
        ItemStack stack = new ItemStack(silence ? TeamLifeBindNeoForge.SILENT_SIGIL_ITEM.get() : TeamLifeBindNeoForge.SCOUT_ROCKET_ITEM.get());
        refreshLore(stack, viewer); return stack;
    }
    boolean refreshLore(ItemStack stack, ServerPlayer viewer) {
        String key;
        if (stack.is(TeamLifeBindNeoForge.SILENT_SIGIL_ITEM.get())) key = "silent_sigil";
        else if (stack.is(TeamLifeBindNeoForge.SCOUT_ROCKET_ITEM.get())) key = "scout_rocket";
        else if (stack.is(TeamLifeBindNeoForge.LIFE_CURSE_POTION_ITEM.get())) key = host.campaignHealthSync() ? "disconnect" : "resonance";
        else return false;
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag data = custom == null ? new CompoundTag() : custom.copyTag();
        String signature = host.utilityLocale(viewer) + ":" + key;
        if (signature.equals(data.getString("tlb_campaign_lore").orElse("")) && stack.has(DataComponents.LORE)) return false;
        boolean potion = stack.is(TeamLifeBindNeoForge.LIFE_CURSE_POTION_ITEM.get());
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(host.text(viewer, potion ? "campaign.potion.name." + key : "item." + key + ".name")));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(host.text(viewer, potion ? "campaign.potion.lore." + key : "item." + key + ".lore")))));
        data.putString("tlb_campaign_lore", signature); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return true;
    }
    InteractionResult use(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean silence = stack.is(TeamLifeBindNeoForge.SILENT_SIGIL_ITEM.get());
        if (!silence && !stack.is(TeamLifeBindNeoForge.SCOUT_ROCKET_ITEM.get())) return InteractionResult.PASS;
        if (!host.utilityParticipant(player) || host.utilityTeam(player) == null) { chat(player, "utility.match_only"); return InteractionResult.FAIL; }
        int team = host.utilityTeam(player);
        long tick = host.utilityTick();
        if (interactionTicks.getOrDefault(player.getUUID(), Long.MIN_VALUE) == tick) return InteractionResult.SUCCESS;
        interactionTicks.put(player.getUUID(), tick);
        if (silence) {
            if (!state.tryActivateSilence(player.getUUID(), tick)) { chat(player, "utility.cooldown", seconds(state.silenceCooldownRemainingTicks(team, tick))); return InteractionResult.SUCCESS; }
            host.campaignConsumedItem(player, hand); stack.shrink(1);
            player.level().playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 1.0F);
            teamChat(team, "campaign.silence.used", player.getScoreboardName(), seconds(RoundCampaignState.SILENCE_TICKS));
        } else {
            if (state.tryScout(player.getUUID(), dimension(player.level()), player.getX(), player.getY(), player.getZ(), tick) == null) {
                chat(player, "utility.cooldown", seconds(state.scoutCooldownRemainingTicks(team, tick))); return InteractionResult.SUCCESS;
            }
            host.campaignConsumedItem(player, hand); stack.shrink(1);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 1.0F);
            teamChat(team, "campaign.scout.started", seconds(RoundCampaignState.SCOUT_TICKS));
        }
        return InteractionResult.SUCCESS;
    }
    boolean hidden(ServerPlayer player) { return state.isSilenced(player.getUUID(), host.utilityTick()); }
    List<Component> sidebar(ServerPlayer viewer) {
        Integer team = host.utilityTeam(viewer);
        if (team == null) return List.of();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(host.text(viewer, "play.mode." + rules().playMode().name().toLowerCase(Locale.ROOT))));
        String pveHud = host.pveHud(viewer);
        if (!pveHud.isBlank()) lines.add(Component.literal(pveHud));
        lines.add(Component.literal(host.text(viewer, "campaign.mode." + rules().matchMode().name().toLowerCase(Locale.ROOT))));
        if (rules().limitedRespawns()) lines.add(Component.literal(host.text(viewer, "campaign.lives.hud", state.remainingRespawnCredits(team))));
        long potion = host.campaignPotionTicks(viewer);
        if (potion > 0) lines.add(Component.literal(host.text(viewer, host.campaignHealthSync() ? "campaign.potion.disconnect" : "campaign.potion.resonance", seconds(potion))));
        if (hidden(viewer)) lines.add(Component.literal(host.text(viewer, "campaign.silence.active", seconds(state.silenceCooldownRemainingTicks(team, host.utilityTick()) - (RoundCampaignState.SILENCE_COOLDOWN_TICKS - RoundCampaignState.SILENCE_TICKS)))));
        if (rules().controlPointEnabled()) {
            RoundCampaignState.ControlPointProgress progress = state.controlPointProgress();
            lines.add(Component.literal(host.text(viewer, progress.contested() ? "campaign.point.contested" : !progress.present() ? "campaign.point.empty" : "campaign.point.hud", progress.team(), seconds(progress.ticks()), seconds(RoundCampaignState.CONTROL_CAPTURE_TICKS))));
        }
        if (borderStarted) lines.add(Component.literal(host.text(viewer, "campaign.border.hud", Math.round(borderRadius(host.utilityTick(), viewer.level().dimension())))));
        return lines;
    }
    String bedStatus(ServerPlayer viewer, int team) {
        Anchor bed = host.campaignBed(team);
        if (bed == null) return host.text(viewer, destroyedBeds.contains(team) ? "campaign.bed.destroyed" : "campaign.bed.none");
        ServerLevel world = host.utilityServer().getLevel(bed.dimension());
        if (world == null || !world.getBlockState(bed.pos()).is(BlockTags.BEDS)) { destroyedBeds.add(team); return host.text(viewer, "campaign.bed.destroyed"); }
        if (!viewer.level().dimension().equals(bed.dimension())) return host.text(viewer, "campaign.bed.other_dimension", bed.dimension().identifier());
        return host.text(viewer, "campaign.bed.active", arrow(viewer, bed.pos().getX() + 0.5D, bed.pos().getZ() + 0.5D), Math.round(Math.sqrt(viewer.distanceToSqr(bed.pos().getX() + 0.5D, bed.pos().getY(), bed.pos().getZ() + 0.5D))));
    }
    void bedDestroyed(int team) { destroyedBeds.add(team); }
    void enterEnd(ServerPlayer player, ResourceKey<Level> to) {
        if ((to.equals(Level.END) || to.identifier().getPath().equals("battle_end")) && host.utilityParticipant(player)) completeTask(host.utilityTeam(player), StageTask.ENTER_END);
    }
    void supplyClaimed(int team) { completeTask(team, StageTask.CLAIM_SUPPLY); }
    private void completeTask(Integer team, StageTask task) {
        if (team != null && rules().controlPointEnabled() && state.completeTask(team, task)) {
            teamChat(team, "campaign.task.complete", team, host.text("campaign.task." + task.name().toLowerCase(Locale.ROOT)));
        }
    }

    void tick(MinecraftServer server) {
        if (!state.isActive() || !host.campaignRunning()) return;
        long tick = host.utilityTick();
        if (rules().matchMode() == MatchMode.DRAGON_HUNT) for (int team : state.eligibleTeams()) if (host.campaignAllLocked(team)) state.applyWipe(team, true, tick);
        Set<Integer> expired = state.expiredDisconnectTeams(tick);
        expired.forEach(team -> broadcast("campaign.reconnect.expired", team));
        pendingDisconnectWipes.addAll(expired);
        for (int team : Set.copyOf(pendingDisconnectWipes)) if (state.isEliminated(team) || host.campaignTeamWipe(team, state.disconnectForfeit(team))) pendingDisconnectWipes.remove(team);
        if (rules().controlPointEnabled() && controlPoint != null) {
            Set<Integer> occupants = new HashSet<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!host.utilityParticipant(player) || !player.level().dimension().equals(controlPoint.dimension())) continue;
                double dx = player.getX() - controlPoint.pos().getX() - 0.5D, dz = player.getZ() - controlPoint.pos().getZ() - 0.5D;
                if (dx * dx + dz * dz <= RoundCampaignState.CONTROL_POINT_RADIUS * RoundCampaignState.CONTROL_POINT_RADIUS
                        && Math.abs(player.getY() - controlPoint.pos().getY()) <= RoundCampaignState.CONTROL_POINT_HEIGHT) occupants.add(host.utilityTeam(player));
            }
            RoundCampaignState.ControlPointReward reward = state.tickControlPoint(occupants, tick);
            if (reward != null) {
                teamChat(reward.team(), "campaign.point.reward", reward.team()); completeTask(reward.team(), StageTask.CAPTURE_POINT); host.campaignSupplyIntel(reward.team());
            }
            if (tick % 20L == 0L) { ServerLevel world = server.getLevel(controlPoint.dimension()); if (world != null) world.sendParticles(ParticleTypes.END_ROD, controlPoint.pos().getX() + 0.5D, controlPoint.pos().getY() + 1.0D, controlPoint.pos().getZ() + 0.5D, 12, 0.5D, 1.0D, 0.5D, 0.01D); }
        }
        RoundCampaignState.Victory victory = state.pollVictory(tick);
        if (victory != null) { host.campaignFinish(victory.winnerTeam(), victory.draw()); return; }
        if (state.borderWarningDue(tick)) broadcast("campaign.border.warning", 60);
        if (tick % 20L == 0L && rules().borderEnabled() && tick - startTick >= RoundCampaignState.BORDER_START_TICKS) {
            if (!borderStarted) { borderStarted = true; broadcast("campaign.border.started"); }
            for (Map.Entry<ResourceKey<Level>, BorderSnapshot> saved : savedBorders.entrySet()) {
                ServerLevel world = server.getLevel(saved.getKey());
                if (world != null) {
                    Anchor center = saved.getValue().anchor();
                    WorldBorder border = world.getWorldBorder();
                    boolean nether = saved.getKey().identifier().getPath().contains("nether");
                    boolean end = saved.getKey().identifier().getPath().contains("end");
                    double x = end ? 0.5D : nether ? (controlPoint.pos().getX() + 0.5D) / 8.0D : center.pos().getX() + 0.5D;
                    double z = end ? 0.5D : nether ? (controlPoint.pos().getZ() + 0.5D) / 8.0D : center.pos().getZ() + 0.5D;
                    border.setCenter(x, z);
                    border.setSize(borderRadius(tick, saved.getKey()) * 2.0D); border.setDamagePerBlock(0.2D); border.setSafeZone(5.0D);
                }
            }
        }
        if (tick % 20L != 0L || lastHudTick == tick) return;
        lastHudTick = tick;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (host.utilityParticipant(player) && !host.campaignHasChannel(player) && (hasScoutHud(player) || !host.campaignPriorityHud(player))) player.sendSystemMessage(Component.literal(overlay(player, "")), true);
        }
    }

    String overlay(ServerPlayer viewer, String primary) {
        if (!host.utilityParticipant(viewer)) return primary;
        List<String> parts = new ArrayList<>();
        if (primary != null && !primary.isBlank()) parts.add(primary);
        int team = host.utilityTeam(viewer);
        Anchor bed = host.campaignBed(team);
        if (bed == null) parts.add(host.text(viewer, destroyedBeds.contains(team) ? "campaign.bed.destroyed" : "campaign.bed.none"));
        else {
            ServerLevel world = host.utilityServer().getLevel(bed.dimension());
            if (world == null || !world.getBlockState(bed.pos()).is(BlockTags.BEDS)) {
                destroyedBeds.add(team); parts.add(host.text(viewer, "campaign.bed.destroyed"));
            } else if (!viewer.level().dimension().equals(bed.dimension())) parts.add(host.text(viewer, "campaign.bed.other_dimension", bed.dimension().identifier()));
            else parts.add(host.text(viewer, "campaign.bed.active", arrow(viewer, bed.pos().getX() + 0.5D, bed.pos().getZ() + 0.5D), Math.round(Math.sqrt(viewer.distanceToSqr(bed.pos().getX() + 0.5D, bed.pos().getY(), bed.pos().getZ() + 0.5D)))));
        }
        long potionTicks = host.campaignPotionTicks(viewer);
        if (potionTicks > 0L) parts.add(host.text(viewer, host.campaignHealthSync() ? "campaign.potion.disconnect" : "campaign.potion.resonance", seconds(potionTicks)));
        String scout = scoutHud(viewer);
        if (!scout.isBlank()) parts.add(scout);
        if (rules().limitedRespawns()) parts.add(host.text(viewer, "campaign.lives.hud", state.remainingRespawnCredits(team)));
        if (hidden(viewer)) parts.add(host.text(viewer, "campaign.silence.active", seconds(state.silenceCooldownRemainingTicks(team, host.utilityTick()) - (RoundCampaignState.SILENCE_COOLDOWN_TICKS - RoundCampaignState.SILENCE_TICKS))));
        if (rules().controlPointEnabled()) {
            RoundCampaignState.ControlPointProgress progress = state.controlPointProgress();
            parts.add(host.text(viewer, progress.contested() ? "campaign.point.contested" : !progress.present() ? "campaign.point.empty" : "campaign.point.hud", progress.team(), seconds(progress.ticks()), seconds(RoundCampaignState.CONTROL_CAPTURE_TICKS)));
        }
        if (borderStarted) parts.add(host.text(viewer, "campaign.border.hud", Math.round(borderRadius(host.utilityTick(), viewer.level().dimension()))));
        return String.join(" | ", parts);
    }
    boolean hasScoutHud(ServerPlayer viewer) {
        if (!host.utilityParticipant(viewer)) return false;
        Integer team = host.utilityTeam(viewer);
        return state.activeScouts(host.utilityTick()).stream().anyMatch(pulse -> dimension(viewer.level()).equals(pulse.dimension())
                && (team == pulse.team() || (viewer.distanceToSqr(pulse.x(), pulse.y(), pulse.z()) <= RoundCampaignState.SCOUT_RADIUS * RoundCampaignState.SCOUT_RADIUS)));
    }
    private double borderRadius(long tick, ResourceKey<Level> dimension) {
        double factor = dimension.identifier().getPath().contains("nether") ? 0.125D : 1.0D;
        double start = dimension.identifier().getPath().contains("end") ? RoundCampaignState.BORDER_START_RADIUS : borderStartRadius;
        double progress = Math.max(0.0D, Math.min(1.0D, (double) (tick - startTick - RoundCampaignState.BORDER_START_TICKS) / RoundCampaignState.BORDER_SHRINK_TICKS));
        return (start + (RoundCampaignState.BORDER_FINAL_RADIUS - start) * progress) * factor;
    }
    private String scoutHud(ServerPlayer viewer) {
        int viewerTeam = host.utilityTeam(viewer);
        List<String> output = new ArrayList<>();
        for (RoundCampaignState.ScoutPulse pulse : state.activeScouts(host.utilityTick())) {
            if (!dimension(viewer.level()).equals(pulse.dimension())) continue;
            long seconds = seconds(RoundCampaignState.SCOUT_TICKS - (host.utilityTick() - pulse.startTick()));
            if (pulse.team() == viewerTeam) {
                Map<Integer, ServerPlayer> targets = new HashMap<>();
                for (ServerPlayer candidate : host.utilityServer().getPlayerList().getPlayers()) {
                    if (!host.utilityParticipant(candidate) || hidden(candidate) || !dimension(candidate.level()).equals(pulse.dimension())) continue;
                    Integer candidateTeam = host.utilityTeam(candidate);
                    if (candidateTeam == null || candidateTeam == viewerTeam || candidate.distanceToSqr(pulse.x(), pulse.y(), pulse.z()) > RoundCampaignState.SCOUT_RADIUS * RoundCampaignState.SCOUT_RADIUS) continue;
                    ServerPlayer previous = targets.get(candidateTeam);
                    if (previous == null || viewer.distanceToSqr(candidate) < viewer.distanceToSqr(previous)) targets.put(candidateTeam, candidate);
                }
                if (targets.isEmpty()) output.add(host.text(viewer, "campaign.scout.none_hud", seconds));
                else targets.entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(4).forEach(target -> output.add(host.text(viewer, "campaign.scout.target_hud", target.getKey(), arrow(viewer, target.getValue().getX(), target.getValue().getZ()), seconds)));
            } else if (viewer.distanceToSqr(pulse.x(), pulse.y(), pulse.z()) <= RoundCampaignState.SCOUT_RADIUS * RoundCampaignState.SCOUT_RADIUS) {
                output.add(host.text(viewer, "campaign.scout.warning_hud", pulse.team(), arrow(viewer, pulse.x(), pulse.z()), seconds));
            }
        }
        return String.join(" / ", output);
    }
    List<Component> rulesLore(ServerPlayer viewer) {
        CampaignRules rules = rules();
        List<Component> lines = new ArrayList<>(List.of(Component.literal(host.text(viewer, "rules.campaign", host.text(viewer, "campaign.mode." + rules.matchMode().name().toLowerCase(Locale.ROOT)), rules.respawnCredits(), host.text(viewer, "campaign.dragon." + rules.dragonVictoryMode().name().toLowerCase(Locale.ROOT)), host.text(viewer, rules.controlPointEnabled() ? "state.enabled" : "state.disabled"), host.text(viewer, rules.borderEnabled() ? "state.enabled" : "state.disabled"))),
                Component.literal(host.text(viewer, "rules.reconnect")), Component.literal(host.text(viewer, "rules.recon"))));
        lines.add(0, Component.literal(host.text(viewer, "play.mode." + rules.playMode().name().toLowerCase(Locale.ROOT))));
        if (rules.playMode() != PlayMode.PVP) lines.add(1, Component.literal(host.text(viewer, "play.rules." + rules.playMode().name().toLowerCase(Locale.ROOT))));
        if (rules.playMode().encounter()) lines.add(Component.literal(host.text(viewer, "play.pve.difficulty.rules", host.text(viewer, rules.pveDifficulty().languageKey()), Math.round(rules.pveDifficulty().healthScale() * 100), Math.round(rules.pveDifficulty().damageScale() * 100))));
        if (rules.controlPointEnabled()) lines.add(Component.literal(host.text(viewer, "rules.point")));
        if (rules.borderEnabled()) lines.add(Component.literal(host.text(viewer, "rules.border")));
        return List.copyOf(lines);
    }
    void openMenu(ServerPlayer viewer) {
        if (!host.campaignCanManage(viewer)) { chat(viewer, "command.no_permission"); return; }
        SimpleContainer inventory = new SimpleContainer(27);
        populateMenu(inventory, viewer);
        viewer.openMenu(new SimpleMenuProvider((syncId, playerInventory, player) -> new CampaignMenu(syncId, playerInventory, inventory), Component.literal(host.text(viewer, "campaign.menu.title"))));
    }
    private void populateMenu(SimpleContainer inventory, ServerPlayer viewer) {
        inventory.setItem(4, button(viewer, Items.GRASS_BLOCK, "play", host.text(viewer, "play.mode." + rules().playMode().name().toLowerCase(Locale.ROOT))));
        inventory.setItem(6, button(viewer, Items.DIAMOND_SWORD, "pve", host.text(viewer, rules().pveDifficulty().languageKey())));
        inventory.setItem(10, button(viewer, Items.IRON_SWORD, "mode", host.text(viewer, "campaign.mode." + rules().matchMode().name().toLowerCase(Locale.ROOT))));
        inventory.setItem(12, button(viewer, Items.DRAGON_HEAD, "dragon", host.text(viewer, "campaign.dragon." + rules().dragonVictoryMode().name().toLowerCase(Locale.ROOT))));
        inventory.setItem(14, button(viewer, Items.BEACON, "objectives", host.text(viewer, rules().controlPointEnabled() ? "state.enabled" : "state.disabled")));
        inventory.setItem(16, button(viewer, Items.COMPASS, "border", host.text(viewer, rules().borderEnabled() ? "state.enabled" : "state.disabled")));
        inventory.setItem(22, button(viewer, Items.TOTEM_OF_UNDYING, "lives", Integer.toString(rules().respawnCredits())));
    }
    private ItemStack button(ServerPlayer player, Item material, String action, String value) {
        String label = switch (action) { case "play" -> "play_mode"; case "pve" -> "pve_difficulty"; case "mode" -> "match_mode"; case "dragon" -> "dragon_victory"; case "objectives" -> "control_point"; case "lives" -> "team_lives"; default -> action; };
        ItemStack stack = new ItemStack(material); stack.set(DataComponents.CUSTOM_NAME, Component.literal(host.text(player, "menu.button." + label)));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(host.text(player, "menu.value." + label, value)))));
        CompoundTag tag = new CompoundTag(); tag.putString("tlb_campaign_action", action); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); return stack;
    }
    private final class CampaignMenu extends ChestMenu {
        private final SimpleContainer inventory;
        CampaignMenu(int id, Inventory playerInventory, SimpleContainer inventory) { super(MenuType.GENERIC_9x3, id, playerInventory, inventory, 3); this.inventory = inventory; }
        @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
        @Override public void clicked(int slot, int button, ContainerInput type, Player player) {
            if (!(player instanceof ServerPlayer viewer) || !host.campaignCanManage(viewer) || type != ContainerInput.PICKUP || slot < 0 || slot >= inventory.getContainerSize()) return;
            CustomData data = inventory.getItem(slot).get(DataComponents.CUSTOM_DATA);
            if (data == null) return;
            String action = data.copyTag().getString("tlb_campaign_action").orElse("");
            String value = switch (action) {
                case "play" -> configured.playMode().next().name();
                case "pve" -> configured.pveDifficulty().next().name();
                case "mode" -> configured.matchMode().next().name();
                case "dragon" -> configured.dragonVictoryMode().next().name();
                case "objectives" -> configured.controlPointEnabled() ? "off" : "on";
                case "border" -> configured.borderEnabled() ? "off" : "on";
                case "lives" -> Integer.toString(button == 1 ? (configured.respawnCredits() <= 1 ? 10 : configured.respawnCredits() - 1) : (configured.respawnCredits() >= 10 ? 1 : configured.respawnCredits() + 1));
                default -> null;
            };
            if (value != null) { viewer.sendSystemMessage(Component.literal(change(viewer, action, value)), false); populateMenu(inventory, viewer); broadcastChanges(); }
        }
        @Override public boolean stillValid(Player player) { return player instanceof ServerPlayer viewer && host.campaignCanManage(viewer); }
    }
    private void chat(ServerPlayer player, String key, Object... arguments) { player.sendSystemMessage(Component.literal(host.text(player, key, arguments)), false); }
    private void teamChat(int team, String key, Object... arguments) { for (ServerPlayer mate : host.utilityTeamPlayers(team)) chat(mate, key, arguments); }
    private void broadcast(String key, Object... arguments) { if (host.utilityServer() != null) for (ServerPlayer player : host.utilityServer().getPlayerList().getPlayers()) chat(player, key, arguments); }
    private static long seconds(long ticks) { return Math.max(0L, (ticks + 19L) / 20L); }
    private static String dimension(Level level) { return level.dimension().identifier().toString(); }
    private static String arrow(ServerPlayer player, double x, double z) { return TeamLifeBindCombatMath.trackingArrow(player.getYRot(), player.getX(), player.getZ(), x, z); }
    record Anchor(ResourceKey<Level> dimension, BlockPos pos) { }
    private record BorderSnapshot(double x, double z, double size, double damage, double safeZone, int warningTime, int warningBlocks, Anchor anchor) { }
}
