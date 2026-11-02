package com.teamlifebind.paper;

import com.teamlifebind.common.PlayMode;
import com.teamlifebind.common.PveWaveState;
import com.teamlifebind.common.PveWaves;
import com.teamlifebind.common.RogueBallot;
import com.teamlifebind.common.RogueRun;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Party route menus and explicitly owned modifiers. Persisted Bukkit modifiers are retired on login. */
final class TeamRogueController {
    private final TeamLifeBindPaperPlugin plugin;
    private final TeamPveController encounter;
    private final RogueRun run = new RogueRun();
    private final RogueBallot ballot = new RogueBallot();
    private final java.util.Map<java.util.UUID, OfferNotice> announcedOffers = new java.util.HashMap<>();
    private long lastPulse = -1L;

    TeamRogueController(TeamLifeBindPaperPlugin plugin, TeamPveController encounter) { this.plugin = plugin; this.encounter = encounter; }
    RogueRun state() { return run; }
    RogueBallot ballot() { return ballot; }
    void start(int participants) {
        ballot.clear(); announcedOffers.clear(); lastPulse = -1L;
        run.start(java.util.concurrent.ThreadLocalRandom.current().nextLong(), participants, plugin.campaigns().rules().pveDifficulty());
        offer();
    }
    void afterWave(int wave) { if (run.advanceAfterWave(wave) && run.waitingForChoice()) offer(); }
    private boolean eligible(Player player) {
        return run.active() && plugin.playMode() == PlayMode.RG && plugin.isUtilityParticipant(player) && encounter.inArena(player);
    }
    private boolean canView(Player player) {
        return run.active() && plugin.playMode() == PlayMode.RG && plugin.campaignAssignments().containsKey(player.getUniqueId());
    }
    boolean open(Player player) {
        if (!canView(player)) { message(player, "rg.unavailable"); return false; }
        updateBallot();
        if (!run.waitingForChoice() || !eligible(player)) return openDetails(player);
        List<RogueRun.Route> offers = run.offers();
        RunMenu holder = new RunMenu(run.epoch(), offers.getFirst().token(), false);
        announcedOffers.put(player.getUniqueId(), new OfferNotice(holder.token, player));
        Inventory menu = Bukkit.createInventory(holder, 27, plugin.text(player, "rg.menu.title", run.floor()));
        holder.inventory = menu;
        fillRoutes(player, menu);
        player.openInventory(menu);
        return true;
    }
    private void fillRoutes(Player player, Inventory menu) {
        for (RogueRun.Route route : run.offers()) {
            List<String> lore = new ArrayList<>();
            lore.add(plugin.text(player, route.theme().key() + ".desc"));
            lore.add(plugin.text(player, route.threat().key() + ".desc"));
            lore.add(plugin.text(player, "rg.relic.gain", plugin.text(player, route.relic().key()), run.level(route.relic()) + 1, route.relic().maxStacks()));
            lore.add(plugin.text(player, route.relic().key() + ".desc"));
            lore.add(plugin.text(player, "rg.vote.count", ballot.votesFor(route.slot()), ballot.participants()));
            if (ballot.voteOf(player.getUniqueId()) == route.slot()) lore.add(plugin.text(player, "rg.vote.yours"));
            lore.add(plugin.text(player, "rg.party_choice"));
            menu.setItem(7 + route.slot() * 3, item(icon(route.relic()), plugin.text(player, "rg.route.title", route.slot(),
                plugin.text(player, route.theme().key()), plugin.text(player, route.threat().key())), lore));
        }
        menu.setItem(4, item(Material.CLOCK, voteStatus(player), List.of(plugin.text(player, "rg.vote.rules"), plugin.text(player, "rg.vote.tiebreak"))));
        List<String> owned = new ArrayList<>();
        for (RogueRun.Relic relic : RogueRun.Relic.values()) if (run.level(relic) > 0)
            owned.add(plugin.text(player, "rg.relic.owned", plugin.text(player, relic.key()), run.level(relic)));
        owned.add(plugin.text(player, "rg.run_only"));
        owned.add(plugin.text(player, "rg.details.open"));
        menu.setItem(22, item(Material.BOOK, plugin.text(player, "rg.relics", run.totalRelics()), owned));
    }
    boolean chooseCurrent(Player player, int slot) {
        List<RogueRun.Route> offers = run.offers();
        return choose(player, offers.isEmpty() ? -1L : offers.getFirst().token(), slot);
    }
    boolean choose(Player player, long token, int slot) {
        if (!eligible(player) || encounter.state().phase() != PveWaveState.Phase.WAITING) { message(player, "rg.unavailable"); return false; }
        updateBallot();
        if (!run.waitingForChoice() || !ballot.cast(token, player.getUniqueId(), slot, plugin.utilityTick())) { message(player, "rg.stale"); return false; }
        message(player, "rg.vote.recorded", slot);
        finishChoice();
        refreshMenus();
        return true;
    }
    private void updateBallot() {
        if (!run.waitingForChoice()) return;
        var voters = Bukkit.getOnlinePlayers().stream().filter(this::eligible).map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet());
        ballot.update(run.offers().getFirst().token(), voters, plugin.utilityTick());
        finishChoice();
    }
    private void finishChoice() {
        if (!run.waitingForChoice() || ballot.decision() == 0) return;
        RogueRun.Route choice = run.choose(ballot.token(), ballot.decision());
        if (choice == null) return;
        encounter.state().postponeNextWave(plugin.utilityTick(), PveWaveState.INITIAL_DELAY_TICKS);
        for (Player viewer : Bukkit.getOnlinePlayers()) if (eligible(viewer)) {
            apply(viewer, true);
            message(viewer, "rg.chosen", choice.slot(), plugin.text(viewer, choice.theme().key()),
                plugin.text(viewer, choice.threat().key()), plugin.text(viewer, choice.relic().key()), run.level(choice.relic()));
        }
        // Only retire the resolved offer. A delayed callback must not close a newer menu.
        for (Player player : Bukkit.getOnlinePlayers()) if (player.getOpenInventory().getTopInventory().getHolder() instanceof RunMenu menu
            && !menu.details && menu.token == choice.token()) closeLater(player, menu);
    }
    boolean menuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RunMenu menu)) return false;
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player && event.getClickedInventory() == event.getView().getTopInventory()
            && (event.getClick() == org.bukkit.event.inventory.ClickType.LEFT || event.getClick() == org.bukkit.event.inventory.ClickType.RIGHT)) {
            if (!valid(player, menu)) return true;
            int slot = event.getRawSlot();
            if (!menu.details && (slot == 10 || slot == 13 || slot == 16)) choose(player, menu.token, (slot - 7) / 3);
            else if (!menu.details && slot == 22 || menu.details && slot == 40) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.getOpenInventory().getTopInventory().getHolder() != menu || !valid(player, menu)) return;
                    if (!menu.details) openDetails(player);
                    else if (run.waitingForChoice() && eligible(player)) open(player);
                    else player.closeInventory();
                });
            }
        }
        return true;
    }
    boolean isMenu(Inventory inventory) { return inventory.getHolder() instanceof RunMenu; }
    private void offer() {
        for (Player player : Bukkit.getOnlinePlayers()) if (eligible(player)) { message(player, "rg.offer", run.floor()); open(player); }
    }
    void tick(long tick) {
        if (!run.active() || tick % 20L != 0L || tick <= lastPulse) return;
        lastPulse = tick;
        updateBallot();
        announcedOffers.keySet().retainAll(Bukkit.getOnlinePlayers().stream().filter(this::eligible)
            .map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean eligible = eligible(player);
            apply(player, eligible);
            var notice = announcedOffers.get(player.getUniqueId());
            if (eligible && run.waitingForChoice() && (notice == null || notice.player != player || notice.token != run.offers().getFirst().token())) {
                message(player, "rg.offer", run.floor()); open(player);
            }
            if (eligible && !run.completed() && run.level(RogueRun.Relic.MANA_SPRING) > 0)
                plugin.magic().state().restoreMana(player.getUniqueId(), run.level(RogueRun.Relic.MANA_SPRING) * 2, tick);
        }
        refreshMenus();
    }
    void clear() {
        run.clear(); ballot.clear(); lastPulse = -1L; announcedOffers.clear();
        for (Player player : Bukkit.getOnlinePlayers()) cleanupPlayer(player);
        closeMenus();
    }
    void cleanupPlayer(Player player) { apply(player, false); announcedOffers.remove(player.getUniqueId()); }
    private void closeMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) if (isMenu(player.getOpenInventory().getTopInventory())) player.closeInventory();
    }
    String choiceHud(Player player) { return plugin.text(player, "rg.hud.vote", run.floor(), voteStatus(player)); }
    private String voteStatus(Player player) {
        long seconds = ballot.secondsRemaining(plugin.utilityTick());
        return seconds < 0L ? plugin.text(player, "rg.vote.ready", ballot.submitted(), ballot.participants())
            : plugin.text(player, "rg.vote.timer", ballot.submitted(), ballot.participants(), seconds);
    }
    private boolean openDetails(Player player) {
        if (!canView(player)) return false;
        RunMenu holder = new RunMenu(run.epoch(), -1L, true);
        Inventory menu = Bukkit.createInventory(holder, 45, plugin.text(player, "rg.details.title"));
        holder.inventory = menu;
        fillDetails(player, menu);
        player.openInventory(menu);
        return true;
    }
    private void fillDetails(Player player, Inventory menu) {
        long tick = plugin.utilityTick();
        var waves = encounter.state();
        String phase = run.waitingForChoice() ? plugin.text(player, "rg.details.wait")
            : run.completed() ? plugin.text(player, "rg.details.completed")
            : waves.phase() == PveWaveState.Phase.WAITING
                ? plugin.text(player, "rg.details.preparing", Math.max(0L, (waves.nextWaveTick() - tick + 19L) / 20L))
                : plugin.text(player, "rg.details.fighting", waves.remaining());
        var summary = waves.summary(tick);
        menu.setItem(4, item(Material.COMPASS, plugin.text(player, "rg.details.progress", run.floor(), PveWaves.TOTAL_WAVES, phase), List.of(
            plugin.text(player, "rg.details.difficulty", plugin.text(player, waves.difficulty().languageKey())),
            plugin.text(player, "rg.details.stats", summary.clearedWaves(), summary.defeats(), summary.elapsedTicks() / 20L),
            plugin.text(player, "rg.details.seed", Long.toUnsignedString(run.seed(), 16)), plugin.text(player, "rg.details.live"))));
        RogueRun.Route route = run.selected();
        menu.setItem(13, route == null ? item(Material.CLOCK, voteStatus(player), List.of(plugin.text(player, "rg.vote.rules")))
            : item(Material.MAP, plugin.text(player, "rg.route.title", route.slot(), plugin.text(player, route.theme().key()), plugin.text(player, route.threat().key())),
                List.of(plugin.text(player, route.theme().key() + ".desc"), plugin.text(player, route.threat().key() + ".desc"))));
        int slot = 19;
        for (RogueRun.Relic relic : RogueRun.Relic.values()) {
            List<String> lore = new ArrayList<>();
            lore.add(plugin.text(player, relic.key() + ".desc"));
            if (run.level(relic) == 0) lore.add(plugin.text(player, "rg.details.unowned"));
            lore.add(plugin.text(player, "rg.run_only"));
            menu.setItem(slot++, item(icon(relic), plugin.text(player, "rg.details.level", plugin.text(player, relic.key()), run.level(relic), relic.maxStacks()), lore));
        }
        boolean voting = run.waitingForChoice() && eligible(player);
        menu.setItem(40, item(voting ? Material.ARROW : Material.BARRIER, plugin.text(player, voting ? "rg.details.back" : "rg.details.close"), List.of()));
    }
    private boolean valid(Player player, RunMenu menu) {
        return canView(player) && menu.epoch == run.epoch() && (menu.details
            || eligible(player) && run.waitingForChoice() && menu.token == run.offers().getFirst().token());
    }
    private void closeLater(Player player, RunMenu menu) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder() == menu) player.closeInventory();
        });
    }
    private void refreshMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) if (player.getOpenInventory().getTopInventory().getHolder() instanceof RunMenu menu) {
            if (!valid(player, menu)) closeLater(player, menu);
            else if (menu.details) fillDetails(player, menu.inventory);
            else fillRoutes(player, menu.inventory);
        }
    }
    private void apply(Player player, boolean enabled) {
        modifier(player, Attribute.ATTACK_DAMAGE, RogueRun.Relic.BLADE, enabled ? run.level(RogueRun.Relic.BLADE) * .12D : 0D, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        modifier(player, Attribute.ARMOR, RogueRun.Relic.ARMOR, enabled ? run.level(RogueRun.Relic.ARMOR) * 2D : 0D, AttributeModifier.Operation.ADD_NUMBER);
        modifier(player, Attribute.MOVEMENT_SPEED, RogueRun.Relic.STRIDE, enabled ? run.level(RogueRun.Relic.STRIDE) * .06D : 0D, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        modifier(player, Attribute.ATTACK_SPEED, RogueRun.Relic.FURY, enabled ? run.level(RogueRun.Relic.FURY) * .1D : 0D, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        modifier(player, Attribute.KNOCKBACK_RESISTANCE, RogueRun.Relic.POISE, enabled ? run.level(RogueRun.Relic.POISE) * .1D : 0D, AttributeModifier.Operation.ADD_NUMBER);
    }
    private void modifier(Player player, Attribute type, RogueRun.Relic relic, double amount, AttributeModifier.Operation operation) {
        var attribute = player.getAttribute(type);
        if (attribute == null) return;
        NamespacedKey key = new NamespacedKey(plugin, "rg_" + relic.name().toLowerCase(Locale.ROOT));
        var old = attribute.getModifiers().stream().filter(modifier -> modifier.getKey().equals(key)).findFirst().orElse(null);
        if (old != null && old.getAmount() == amount && old.getOperation() == operation) return;
        if (old != null) attribute.removeModifier(old);
        if (amount != 0D) attribute.addModifier(new AttributeModifier(key, amount, operation, EquipmentSlotGroup.ANY));
    }
    private static ItemStack item(Material material, String title, List<String> lore) {
        ItemStack result = new ItemStack(material); var meta = result.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + title); meta.setLore(lore); result.setItemMeta(meta); return result;
    }
    private static Material icon(RogueRun.Relic relic) {
        return switch (relic) {
            case BLADE -> Material.IRON_SWORD; case ARMOR -> Material.IRON_CHESTPLATE;
            case STRIDE -> Material.FEATHER; case FURY -> Material.GOLDEN_SWORD; case POISE -> Material.SHIELD;
            case MANA_SPRING -> Material.AMETHYST_SHARD; case PROVISIONER -> Material.CHEST; case SCHOLAR -> Material.ENCHANTED_BOOK;
        };
    }
    private void message(Player player, String key, Object... args) { if (player != null) player.sendMessage(ChatColor.GOLD + plugin.text(player, key, args)); }
    private record OfferNotice(long token, Player player) { }
    private static final class RunMenu implements InventoryHolder {
        private final long epoch;
        private final long token;
        private final boolean details;
        private Inventory inventory;
        RunMenu(long epoch, long token, boolean details) { this.epoch = epoch; this.token = token; this.details = details; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
