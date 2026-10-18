package com.teamlifebind.paper;

import com.teamlifebind.common.CampaignRules;
import com.teamlifebind.common.DragonVictoryMode;
import com.teamlifebind.common.MatchMode;
import com.teamlifebind.common.RoundCampaignState;
import com.teamlifebind.common.TeamLifeBindCombatMath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Bukkit adapter for round-local competition, recon, reconnects and map objectives. */
final class TeamCampaignController {
    private final TeamLifeBindPaperPlugin plugin;
    private final RoundCampaignState state = new RoundCampaignState();
    private final NamespacedKey sigilKey, rocketKey, menuKey;
    private final Set<Integer> teamsWithBeds = new HashSet<>();
    private final Map<UUID, Long> interactions = new HashMap<>();
    private final Map<World, BorderPlan> borders = new HashMap<>();
    private final Set<Integer> pendingDisconnectWipes = new HashSet<>();
    private Location point;
    private long roundStart;
    private boolean borderStarted;

    TeamCampaignController(TeamLifeBindPaperPlugin plugin) {
        this.plugin = plugin;
        sigilKey = new NamespacedKey(plugin, "silent_sigil");
        rocketKey = new NamespacedKey(plugin, "scout_rocket");
        menuKey = new NamespacedKey(plugin, "campaign_action");
    }

    RoundCampaignState state() { return state; }

    CampaignRules configuredRules() {
        var config = plugin.getConfig();
        return new CampaignRules(MatchMode.parse(config.getString("gameplay.match-mode", "DRAGON_HUNT")),
            config.getInt("gameplay.team-lives", 3),
            DragonVictoryMode.parse(config.getString("gameplay.dragon-victory", "LAST_HIT")),
            config.getBoolean("gameplay.control-point-enabled", false), config.getBoolean("gameplay.border-enabled", false));
    }

    CampaignRules rules() { return state.isActive() ? state.rules() : configuredRules(); }

    void registerRecipes() {
        register("silent_sigil", createSigil(null), Material.AMETHYST_SHARD, Material.ECHO_SHARD);
        register("scout_rocket", createRocket(null), Material.FIREWORK_ROCKET, Material.REDSTONE);
    }

    private void register(String id, ItemStack result, Material... ingredients) {
        NamespacedKey key = new NamespacedKey(plugin, id);
        Bukkit.removeRecipe(key);
        ShapelessRecipe recipe = new ShapelessRecipe(key, result);
        for (Material ingredient : ingredients) recipe.addIngredient(ingredient);
        Bukkit.addRecipe(recipe);
    }

    ItemStack createSigil(Player viewer) { return item(viewer, Material.AMETHYST_SHARD, sigilKey, "silent_sigil"); }
    ItemStack createRocket(Player viewer) { return item(viewer, Material.FIREWORK_ROCKET, rocketKey, "scout_rocket"); }

    private ItemStack item(Player viewer, Material material, NamespacedKey key, String id) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + plugin.text(viewer, "item." + id + ".name"));
        meta.setLore(List.of(ChatColor.GRAY + plugin.text(viewer, "item." + id + ".lore")));
        meta.setItemModel(new NamespacedKey("teamlifebind", id));
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean marked(ItemStack stack, NamespacedKey key) {
        return stack != null && !stack.getType().isAir() && stack.hasItemMeta()
            && stack.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    boolean isItem(ItemStack stack) { return marked(stack, sigilKey) || marked(stack, rocketKey); }
    ItemStack localizedItem(Player viewer, ItemStack stack) {
        return marked(stack, sigilKey) ? createSigil(viewer) : marked(stack, rocketKey) ? createRocket(viewer) : null;
    }

    boolean use(Player player, EquipmentSlot hand, ItemStack stack) {
        if (!isItem(stack)) return false;
        if (!participant(player)) { message(player, "utility.match_only"); return true; }
        long tick = plugin.utilityTick();
        if (Long.valueOf(tick).equals(interactions.put(player.getUniqueId(), tick))) return true;
        Integer team = plugin.utilityTeam(player);
        if (marked(stack, sigilKey)) {
            if (!state.tryActivateSilence(player.getUniqueId(), tick)) {
                message(player, "utility.cooldown", seconds(state.silenceCooldownRemainingTicks(team, tick)));
                return true;
            }
            consume(player, hand, stack);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 0.6F);
            teamMessage(team, "campaign.silence.used", player.getName(), seconds(RoundCampaignState.SILENCE_TICKS));
        } else {
            Location location = player.getLocation();
            if (state.tryScout(player.getUniqueId(), dimension(player.getWorld()), location.getX(), location.getY(), location.getZ(), tick) == null) {
                message(player, "utility.cooldown", seconds(state.scoutCooldownRemainingTicks(team, tick)));
                return true;
            }
            consume(player, hand, stack);
            player.getWorld().playSound(location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0F, 1.0F);
            teamMessage(team, "campaign.scout.started", seconds(RoundCampaignState.SCOUT_TICKS));
        }
        return true;
    }

    private void consume(Player player, EquipmentSlot hand, ItemStack stack) {
        if (plugin.utilities().usesInventorySlot(player, hand == EquipmentSlot.OFF_HAND ? 40 : player.getInventory().getHeldItemSlot())) plugin.utilities().changedInventory(player);
        stack.setAmount(stack.getAmount() - 1);
        player.getInventory().setItem(hand == EquipmentSlot.OFF_HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND,
            stack.getAmount() == 0 ? null : stack);
    }

    boolean participant(Player player) {
        Integer team = plugin.utilityTeam(player);
        return plugin.isUtilityParticipant(player) && team != null && !state.isEliminated(team);
    }

    boolean silenced(Player player) {
        return state.isSilenced(player.getUniqueId(), plugin.utilityTick());
    }

    void startRound() {
        clear();
        roundStart = plugin.utilityTick();
        state.start(plugin.campaignAssignments(), configuredRules(), roundStart);
        World world = plugin.utilityMatchWorld();
        List<Location> spawns = plugin.campaignSpawns();
        if (world == null) return;
        double centerX = spawns.stream().mapToDouble(Location::getX).average().orElse(world.getSpawnLocation().getX());
        double centerZ = spawns.stream().mapToDouble(Location::getZ).average().orElse(world.getSpawnLocation().getZ());
        point = plugin.utilitySafeSurface(world, (int) Math.floor(centerX), (int) Math.floor(centerZ));
        if (rules().controlPointEnabled() && point != null) {
            broadcast("campaign.point.location", dimension(world), point.getBlockX(), point.getBlockY(), point.getBlockZ());
        }
        if (rules().borderEnabled()) {
            double radius = 750.0D;
            for (Location spawn : spawns) radius = Math.max(radius, Math.hypot(spawn.getX() - centerX, spawn.getZ() - centerZ) + 32.0D);
            for (World arena : plugin.campaignWorlds()) {
                boolean nether = arena.getEnvironment() == World.Environment.NETHER;
                boolean end = arena.getEnvironment() == World.Environment.THE_END;
                double scale = nether ? 0.125D : 1.0D;
                WorldBorder border = arena.getWorldBorder();
                borders.put(arena, new BorderPlan(border.getCenter(), border.getSize(), border.getDamageAmount(),
                    border.getDamageBuffer(), border.getWarningDistance(), border.getWarningTime(),
                    end ? 0.5D : centerX * scale, end ? 0.5D : centerZ * scale, end ? 750.0D : radius * scale, 250.0D * scale));
            }
        }
    }

    void clear() {
        for (var entry : borders.entrySet()) {
            if (Bukkit.getWorld(entry.getKey().getUID()) == null) continue;
            WorldBorder border = entry.getKey().getWorldBorder();
            BorderPlan old = entry.getValue();
            border.setCenter(old.center());
            border.setSize(old.size());
            border.setDamageAmount(old.damage());
            border.setDamageBuffer(old.buffer());
            border.setWarningDistance(old.warningDistance());
            border.setWarningTime(old.warningTime());
        }
        borders.clear();
        pendingDisconnectWipes.clear();
        state.clear();
        teamsWithBeds.clear();
        interactions.clear();
        point = null;
        roundStart = 0L;
        borderStarted = false;
    }

    void bedPlaced(int team) { teamsWithBeds.add(team); }
    void recordDamage(Player player) {
        if (participant(player)) state.recordDamage(player.getUniqueId(), plugin.utilityTick());
    }

    void disconnect(Player player) {
        if (!state.isActive() || player == null || state.teamForPlayer(player.getUniqueId()) == null) return;
        long tick = plugin.utilityTick();
        boolean combat = state.inCombat(player.getUniqueId(), tick);
        state.disconnect(player.getUniqueId(), tick, combat);
        teamMessage(plugin.utilityTeam(player), combat ? "campaign.reconnect.combat" : "campaign.reconnect.safe",
            player.getName(), seconds(RoundCampaignState.RECONNECT_GRACE_TICKS));
        interactions.remove(player.getUniqueId());
    }

    void reconnect(Player player) {
        if (!state.isActive()) return;
        var result = state.join(player.getUniqueId(), plugin.utilityTick());
        if (result.team() == null) return;
        boolean penaltyDue = result.penaltyDue();
        if (penaltyDue) {
            if (!plugin.campaignForceWipe(result.team())) pendingDisconnectWipes.add(result.team());
            result = state.join(player.getUniqueId(), plugin.utilityTick());
        }
        if (result.locked() || state.isEliminated(result.team())) plugin.campaignLockTeam(result.team());
        else if (result.pendingRespawn() || penaltyDue) {
            plugin.campaignQueueRespawn(player);
            state.acknowledgePendingRespawn(player.getUniqueId());
        }
        teamMessage(result.team(), "campaign.reconnect.returned", player.getName());
    }

    void wipe(int team, boolean permanent) {
        if (!state.isActive()) return;
        var result = state.applyWipe(team, permanent && rules().matchMode() == MatchMode.LIMITED_LIVES, plugin.utilityTick());
        if (!result.applied()) return;
        if (result.eliminated()) {
            plugin.campaignLockTeam(team);
            broadcast("campaign.lives.eliminated", plugin.campaignTeamLabel(null, team));
        } else if (rules().matchMode() == MatchMode.LIMITED_LIVES) {
            broadcast("campaign.lives.remaining", plugin.campaignTeamLabel(null, team), result.remainingRespawnCredits());
        }
    }

    void dragonDamage(Player attacker, EnderDragon dragon, double damage) {
        if (!participant(attacker)) return;
        state.recordDragonDamage(plugin.utilityTeam(attacker), dragon.getUniqueId(), damage, dragon.getHealth(), plugin.utilityTick());
        recordDamage(attacker);
    }

    boolean dragonKilled(EnderDragon dragon) {
        if (!state.isActive()) return false;
        if (rules().matchMode() != MatchMode.DRAGON_HUNT) return true;
        Player killer = dragon.getKiller();
        Integer last = killer == null || !participant(killer) ? null : plugin.utilityTeam(killer);
        Integer winner = state.chooseDragonWinner(dragon.getUniqueId(), last, plugin.utilityTick());
        if (winner != null) plugin.campaignFinish(winner, false);
        return true;
    }

    void enteredWorld(Player player) {
        if (participant(player) && player.getWorld().getEnvironment() == World.Environment.THE_END) task(plugin.utilityTeam(player), RoundCampaignState.StageTask.ENTER_END);
    }

    void claimedSupply(Player player) {
        if (participant(player)) task(plugin.utilityTeam(player), RoundCampaignState.StageTask.CLAIM_SUPPLY);
    }

    private void task(int team, RoundCampaignState.StageTask task) {
        if (state.completeTask(team, task)) teamMessage(team, "campaign.task.complete", plugin.campaignTeamLabel(null, team),
            plugin.text("campaign.task." + task.name().toLowerCase(Locale.ROOT)));
    }

    void tick(long tick) {
        if (!state.isActive()) return;
        for (int team : state.eligibleTeams()) {
            if (plugin.campaignAllLocked(team)) state.applyWipe(team, true, tick);
        }
        for (int team : state.expiredDisconnectTeams(tick)) {
            broadcast("campaign.reconnect.expired", plugin.campaignTeamLabel(null, team));
            pendingDisconnectWipes.add(team);
        }
        for (int team : List.copyOf(pendingDisconnectWipes)) {
            if (plugin.campaignForceWipe(team)) pendingDisconnectWipes.remove(team);
        }
        var victory = state.pollVictory(tick);
        if (victory != null) { plugin.campaignFinish(victory.winnerTeam(), victory.draw()); return; }
        if (point != null && rules().controlPointEnabled()) {
            Set<Integer> occupants = new HashSet<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!participant(player) || player.getWorld() != point.getWorld()) continue;
                Location at = player.getLocation();
                if (Math.abs(at.getY() - point.getY()) <= RoundCampaignState.CONTROL_POINT_HEIGHT
                    && Math.hypot(at.getX() - point.getX(), at.getZ() - point.getZ()) <= RoundCampaignState.CONTROL_POINT_RADIUS) {
                    occupants.add(plugin.utilityTeam(player));
                }
            }
            var reward = state.tickControlPoint(occupants, tick);
            if (reward != null) {
                task(reward.team(), RoundCampaignState.StageTask.CAPTURE_POINT);
                List<Player> members = plugin.utilityTeamPlayers(reward.team());
                if (!members.isEmpty()) {
                    Player recipient = members.get(0);
                    ItemStack bandages = plugin.utilities().createBandage(recipient);
                    bandages.setAmount(2);
                    give(recipient, bandages);
                    give(recipient, createRocket(recipient));
                    String hint = plugin.utilities().supplyHint(recipient);
                    teamMessage(reward.team(), "campaign.point.reward", plugin.campaignTeamLabel(null, reward.team()));
                    for (Player member : members) member.sendMessage(hint);
                }
            }
            if (tick % 20L == 0L) point.getWorld().spawnParticle(Particle.END_ROD, point.clone().add(0.0D, 1.0D, 0.0D), 8, 1.0D, 0.8D, 1.0D, 0.0D);
        }
        if (state.borderWarningDue(tick)) broadcast("campaign.border.warning", 60);
        if (rules().borderEnabled() && tick % 20L == 0L && tick - roundStart >= RoundCampaignState.BORDER_START_TICKS) {
            if (!borderStarted) {
                borderStarted = true;
                broadcast("campaign.border.started");
            }
            double fraction = Math.min(1.0D, Math.max(0.0D, (tick - roundStart - RoundCampaignState.BORDER_START_TICKS) / (double) RoundCampaignState.BORDER_SHRINK_TICKS));
            for (var entry : borders.entrySet()) {
                BorderPlan plan = entry.getValue();
                WorldBorder border = entry.getKey().getWorldBorder();
                border.setCenter(plan.x(), plan.z());
                border.setSize(2.0D * (plan.initial() + (plan.target() - plan.initial()) * fraction));
                border.setDamageBuffer(5.0D);
                border.setDamageAmount(0.2D);
                border.setWarningDistance(10);
            }
        }
        if (tick % 20L == 0L) updateRecon(tick);
    }

    private void updateRecon(long tick) {
        List<RoundCampaignState.ScoutPulse> pulses = state.activeScouts(tick);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!participant(viewer) || plugin.utilities().hasChannel(viewer)) continue;
            Integer viewerTeam = plugin.utilityTeam(viewer);
            List<String> fragments = new ArrayList<>();
            for (var pulse : pulses) {
                if (!dimension(viewer.getWorld()).equals(pulse.dimension())) continue;
                long remainingSeconds = seconds(Math.max(0L, pulse.startTick() + RoundCampaignState.SCOUT_TICKS - tick));
                if (viewerTeam == pulse.team()) {
                    Map<Integer, Player> nearest = new HashMap<>();
                    for (Player enemy : Bukkit.getOnlinePlayers()) {
                        if (!participant(enemy) || enemy.getWorld() != viewer.getWorld() || plugin.utilityTeam(enemy).equals(viewerTeam) || silenced(enemy)) continue;
                        Location at = enemy.getLocation();
                        double distance = Math.sqrt(Math.pow(at.getX() - pulse.x(), 2) + Math.pow(at.getY() - pulse.y(), 2) + Math.pow(at.getZ() - pulse.z(), 2));
                        if (distance > RoundCampaignState.SCOUT_RADIUS) continue;
                        Player old = nearest.get(plugin.utilityTeam(enemy));
                        if (old == null || viewer.getLocation().distanceSquared(at) < viewer.getLocation().distanceSquared(old.getLocation())) nearest.put(plugin.utilityTeam(enemy), enemy);
                    }
                    for (var enemy : nearest.entrySet().stream().sorted(java.util.Comparator.comparingDouble(entry -> viewer.getLocation().distanceSquared(entry.getValue().getLocation()))).limit(4).toList()) {
                        Location at = enemy.getValue().getLocation(), origin = viewer.getLocation();
                        fragments.add(plugin.text(viewer, "campaign.scout.target_hud", plugin.campaignTeamLabel(viewer, enemy.getKey()),
                            TeamLifeBindCombatMath.trackingArrow(origin.getYaw(), origin.getX(), origin.getZ(), at.getX(), at.getZ()), remainingSeconds));
                    }
                    if (nearest.isEmpty()) fragments.add(plugin.text(viewer, "campaign.scout.none_hud", remainingSeconds));
                } else {
                    Location at = viewer.getLocation();
                    if (Math.sqrt(Math.pow(at.getX() - pulse.x(), 2) + Math.pow(at.getY() - pulse.y(), 2) + Math.pow(at.getZ() - pulse.z(), 2)) <= RoundCampaignState.SCOUT_RADIUS) {
                        fragments.add(plugin.text(viewer, "campaign.scout.warning_hud", plugin.campaignTeamLabel(viewer, pulse.team()),
                            TeamLifeBindCombatMath.trackingArrow(at.getYaw(), at.getX(), at.getZ(), pulse.x(), pulse.z()), remainingSeconds));
                    }
                }
            }
            if (!fragments.isEmpty()) plugin.utilityActionBar(viewer, "campaign.scout.hud", String.join(" | ", fragments));
        }
    }

    boolean hasHud(Player player) {
        if (!participant(player)) return false;
        Location at = player.getLocation();
        for (var pulse : state.activeScouts(plugin.utilityTick())) {
            if (dimension(player.getWorld()).equals(pulse.dimension())
                && (plugin.utilityTeam(player) == pulse.team() || Math.sqrt(Math.pow(at.getX() - pulse.x(), 2) + Math.pow(at.getY() - pulse.y(), 2) + Math.pow(at.getZ() - pulse.z(), 2)) <= RoundCampaignState.SCOUT_RADIUS)) return true;
        }
        return false;
    }

    String bedStatus(Player viewer) {
        Integer team = plugin.utilityTeam(viewer);
        if (team == null) return plugin.text(viewer, "campaign.bed.none");
        Location bed = plugin.campaignBed(team);
        if (bed == null) return plugin.text(viewer, teamsWithBeds.contains(team) ? "campaign.bed.destroyed" : "campaign.bed.none");
        if (viewer.getWorld() != bed.getWorld()) return plugin.text(viewer, "campaign.bed.other_dimension", dimension(bed.getWorld()));
        Location at = viewer.getLocation();
        return plugin.text(viewer, "campaign.bed.ready", bed.getBlockX(), bed.getBlockZ()) + " "
            + TeamLifeBindCombatMath.trackingArrow(at.getYaw(), at.getX(), at.getZ(), bed.getX(), bed.getZ());
    }

    void sidebar(Player viewer, List<String> lines) {
        if (!state.isActive() || viewer == null || plugin.utilityTeam(viewer) == null) return;
        int team = plugin.utilityTeam(viewer);
        for (ItemStack stack : viewer.getInventory().getContents()) refreshPotionLore(viewer, stack);
        lines.add(ChatColor.AQUA + modeLabel(viewer, rules().matchMode()));
        if (rules().matchMode() == MatchMode.LIMITED_LIVES) lines.add(ChatColor.GOLD + plugin.text(viewer, "campaign.lives.hud", state.remainingRespawnCredits(team)));
        if (state.isSilenced(viewer.getUniqueId(), plugin.utilityTick())) lines.add(ChatColor.DARK_PURPLE + plugin.text(viewer, "campaign.silence.active",
            seconds(Math.max(0L, state.silenceCooldownRemainingTicks(team, plugin.utilityTick()) - (RoundCampaignState.SILENCE_COOLDOWN_TICKS - RoundCampaignState.SILENCE_TICKS)))));
        long potion = plugin.campaignPotionTicks(viewer);
        if (potion > 0L) lines.add(ChatColor.LIGHT_PURPLE + plugin.text(viewer, plugin.campaignHealthSync() ? "campaign.potion.disconnect" : "campaign.potion.resonance", seconds(potion)));
        if (rules().controlPointEnabled()) {
            var progress = state.controlPointProgress();
            lines.add(ChatColor.YELLOW + (progress.contested() ? plugin.text(viewer, "campaign.point.contested")
                : !progress.present() || progress.team() == null ? plugin.text(viewer, "campaign.point.empty")
                : plugin.text(viewer, "campaign.point.hud", plugin.campaignTeamLabel(viewer, progress.team()), progress.ticks() / 20, RoundCampaignState.CONTROL_CAPTURE_TICKS / 20)));
        }
        BorderPlan border = borders.get(viewer.getWorld());
        if (borderStarted && border != null) lines.add(ChatColor.GOLD + plugin.text(viewer, "campaign.border.hud", Math.round(viewer.getWorld().getWorldBorder().getSize() / 2.0D)));
    }

    String ruleLine(Player viewer) {
        CampaignRules settings = rules();
        return plugin.text(viewer, "rules.campaign", modeLabel(viewer, settings.matchMode()), settings.respawnCredits(),
            dragonLabel(viewer, settings.dragonVictoryMode()), plugin.text(viewer, settings.controlPointEnabled() ? "state.enabled" : "state.disabled"),
            plugin.text(viewer, settings.borderEnabled() ? "state.enabled" : "state.disabled"));
    }

    String modeLabel(Player viewer, MatchMode mode) { return plugin.text(viewer, "campaign.mode." + mode.name().toLowerCase(Locale.ROOT)); }
    String dragonLabel(Player viewer, DragonVictoryMode mode) { return plugin.text(viewer, "campaign.dragon." + mode.name().toLowerCase(Locale.ROOT)); }

    void refreshPotionLore(Player viewer, ItemStack stack) {
        if (!plugin.isLifeCursePotionItem(stack)) return;
        ItemMeta meta = stack.getItemMeta();
        boolean sync = plugin.campaignHealthSync();
        String name = ChatColor.LIGHT_PURPLE + plugin.text(viewer, sync ? "campaign.potion.name.disconnect" : "campaign.potion.name.resonance");
        List<String> lore = List.of(ChatColor.GRAY + plugin.text(viewer, sync ? "campaign.potion.lore.disconnect" : "campaign.potion.lore.resonance"));
        if (name.equals(meta.getDisplayName()) && lore.equals(meta.getLore())) return;
        meta.setDisplayName(name);
        meta.setLore(lore);
        stack.setItemMeta(meta);
    }

    void openMenu(Player player) {
        if (!admin(player)) { message(player, "command.no_permission"); return; }
        CampaignMenu holder = new CampaignMenu();
        Inventory menu = Bukkit.createInventory(holder, 27, plugin.text(player, "menu.campaign.title"));
        holder.inventory = menu;
        CampaignRules settings = configuredRules();
        menu.setItem(10, button(player, "mode", Material.DRAGON_HEAD, "match_mode", modeLabel(player, settings.matchMode())));
        menu.setItem(12, button(player, "credits", Material.TOTEM_OF_UNDYING, "team_lives", String.valueOf(settings.respawnCredits())));
        menu.setItem(14, button(player, "dragon", Material.DRAGON_EGG, "dragon_victory", dragonLabel(player, settings.dragonVictoryMode())));
        menu.setItem(16, button(player, "point", Material.BEACON, "control_point", plugin.text(player, settings.controlPointEnabled() ? "state.enabled" : "state.disabled")));
        menu.setItem(22, button(player, "border", Material.BARRIER, "border", plugin.text(player, settings.borderEnabled() ? "state.enabled" : "state.disabled")));
        player.openInventory(menu);
    }

    private ItemStack button(Player viewer, String action, Material icon, String key, String value) {
        ItemStack stack = new ItemStack(icon);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + plugin.text(viewer, "menu.button." + key));
        meta.setLore(List.of(plugin.text(viewer, "menu.value." + key, value)));
        meta.getPersistentDataContainer().set(menuKey, PersistentDataType.STRING, action);
        stack.setItemMeta(meta);
        return stack;
    }

    boolean menuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CampaignMenu)) return false;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !admin(player) || event.getClickedInventory() != event.getView().getTopInventory()) return true;
        if (plugin.hasActiveMatchSession()) { message(player, "config.campaign.active_round"); return true; }
        ItemStack stack = event.getCurrentItem();
        if (stack == null || !stack.hasItemMeta()) return true;
        String action = stack.getItemMeta().getPersistentDataContainer().get(menuKey, PersistentDataType.STRING);
        if (action == null) return true;
        CampaignRules settings = configuredRules();
        switch (action) {
            case "mode" -> plugin.getConfig().set("gameplay.match-mode", settings.matchMode().next().name());
            case "credits" -> plugin.getConfig().set("gameplay.team-lives", settings.respawnCredits() % 10 + 1);
            case "dragon" -> plugin.getConfig().set("gameplay.dragon-victory", settings.dragonVictoryMode().next().name());
            case "point" -> plugin.getConfig().set("gameplay.control-point-enabled", !settings.controlPointEnabled());
            case "border" -> plugin.getConfig().set("gameplay.border-enabled", !settings.borderEnabled());
            default -> { return true; }
        }
        plugin.saveConfig();
        Bukkit.getScheduler().runTask(plugin, () -> { if (player.isOnline()) openMenu(player); });
        return true;
    }

    boolean isMenu(Inventory inventory) { return inventory.getHolder() instanceof CampaignMenu; }

    void command(CommandSender sender, String action, String[] args) {
        if (plugin.hasActiveMatchSession()) { sender.sendMessage(plugin.text(sender, "config.campaign.active_round")); return; }
        if (args.length < 2) { sender.sendMessage(ruleLine(sender instanceof Player player ? player : null)); return; }
        String value = args[1].toUpperCase(Locale.ROOT);
        try {
            switch (action) {
                case "mode" -> plugin.getConfig().set("gameplay.match-mode", MatchMode.valueOf(value).name());
                case "dragon" -> plugin.getConfig().set("gameplay.dragon-victory", DragonVictoryMode.valueOf(value).name());
                case "objectives", "border" -> {
                    if (!value.equals("ON") && !value.equals("OFF")) throw new IllegalArgumentException();
                    plugin.getConfig().set(action.equals("objectives") ? "gameplay.control-point-enabled" : "gameplay.border-enabled", value.equals("ON"));
                }
                default -> { return; }
            }
            plugin.saveConfig();
            sender.sendMessage(plugin.text(sender, "config.campaign.updated", action, value));
        } catch (IllegalArgumentException error) {
            sender.sendMessage("/tlb " + action + (action.equals("mode") ? " <DRAGON_HUNT|LIMITED_LIVES>" : action.equals("dragon") ? " <LAST_HIT|CONTRIBUTION>" : " <on|off>"));
        }
    }

    private static boolean admin(Player player) { return player.hasPermission("teamlifebind.admin"); }
    private void give(Player player, ItemStack stack) { for (ItemStack left : player.getInventory().addItem(stack).values()) player.getWorld().dropItemNaturally(player.getLocation(), left); }
    private void message(Player player, String key, Object... args) { player.sendMessage(ChatColor.AQUA + plugin.text(player, key, args)); }
    private void teamMessage(int team, String key, Object... args) { for (Player player : plugin.utilityTeamPlayers(team)) message(player, key, args); }
    private void broadcast(String key, Object... args) { for (Player player : Bukkit.getOnlinePlayers()) message(player, key, args); }
    private static long seconds(long ticks) { return (ticks + 19L) / 20L; }
    private static String dimension(World world) { return world.getKey().toString(); }
    private record BorderPlan(Location center, double size, double damage, double buffer, int warningDistance, int warningTime,
                              double x, double z, double initial, double target) { }
    private static final class CampaignMenu implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
}
