package com.teamlifebind.forge;

import com.teamlifebind.common.PlayMode;
import com.teamlifebind.common.PveWaveState;
import com.teamlifebind.common.PveWaves;
import com.teamlifebind.common.RogueBallot;
import com.teamlifebind.common.RogueRun;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/** Party route selection and transient relic effects, isolated from ordinary PVE and persistent SUR. */
final class TeamRogueController {
    private final TeamLifeBindForgeManager host;
    private final PveWaveState encounter;
    private final RogueRun run = new RogueRun();
    private final RogueBallot ballot = new RogueBallot();
    private final java.util.Map<java.util.UUID, OfferNotice> announcedOffers = new java.util.HashMap<>();
    private long lastPulse = -1L;

    TeamRogueController(TeamLifeBindForgeManager host, PveWaveState encounter) { this.host = host; this.encounter = encounter; }
    RogueRun state() { return run; }
    RogueBallot ballot() { return ballot; }
    void start(int participants) {
        ballot.clear(); announcedOffers.clear(); lastPulse = -1L;
        run.start(host.utilityServer().overworld().getRandom().nextLong(), participants, host.pveDifficulty());
        offer();
    }
    void afterWave(int wave) { if (run.advanceAfterWave(wave) && run.waitingForChoice()) offer(); }
    private boolean eligible(ServerPlayer player) {
        var center = host.campaignCenter();
        return run.active() && host.playMode() == PlayMode.RG && host.utilityParticipant(player)
            && center != null && player.level().dimension().equals(center.dimension());
    }
    private boolean canView(ServerPlayer player) {
        return run.active() && host.playMode() == PlayMode.RG && host.campaignAssignments().containsKey(player.getUUID());
    }
    boolean open(ServerPlayer player) {
        if (!canView(player)) { message(player, "rg.unavailable"); return false; }
        updateBallot();
        if (!run.waitingForChoice() || !eligible(player)) return openDetails(player);
        SimpleContainer contents = new SimpleContainer(27);
        List<RogueRun.Route> offers = run.offers();
        fillRoutes(player, contents);
        long token = offers.getFirst().token();
        announcedOffers.put(player.getUUID(), new OfferNotice(token, player));
        player.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> new RouteMenu(id, inventory, contents, token),
            Component.literal(host.text(player, "rg.menu.title", run.floor()))));
        return true;
    }
    private void fillRoutes(ServerPlayer player, SimpleContainer contents) {
        for (RogueRun.Route route : run.offers()) {
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal(host.text(player, route.theme().key() + ".desc")));
            lore.add(Component.literal(host.text(player, route.threat().key() + ".desc")));
            lore.add(Component.literal(host.text(player, "rg.relic.gain", host.text(player, route.relic().key()), run.level(route.relic()) + 1, route.relic().maxStacks())));
            lore.add(Component.literal(host.text(player, route.relic().key() + ".desc")));
            lore.add(Component.literal(host.text(player, "rg.vote.count", ballot.votesFor(route.slot()), ballot.participants())));
            if (ballot.voteOf(player.getUUID()) == route.slot()) lore.add(Component.literal(host.text(player, "rg.vote.yours")));
            lore.add(Component.literal(host.text(player, "rg.party_choice")));
            ItemStack icon = new ItemStack(icon(route.relic()));
            icon.set(DataComponents.CUSTOM_NAME, Component.literal(host.text(player, "rg.route.title", route.slot(),
                host.text(player, route.theme().key()), host.text(player, route.threat().key()))));
            icon.set(DataComponents.LORE, new ItemLore(lore));
            contents.setItem(7 + route.slot() * 3, icon);
        }
        contents.setItem(4, item(Items.CLOCK, voteStatus(player), List.of(
            Component.literal(host.text(player, "rg.vote.rules")), Component.literal(host.text(player, "rg.vote.tiebreak")))));
        List<Component> owned = new ArrayList<>();
        for (RogueRun.Relic relic : RogueRun.Relic.values()) if (run.level(relic) > 0)
            owned.add(Component.literal(host.text(player, "rg.relic.owned", host.text(player, relic.key()), run.level(relic))));
        owned.add(Component.literal(host.text(player, "rg.run_only")));
        owned.add(Component.literal(host.text(player, "rg.details.open")));
        ItemStack summary = new ItemStack(Items.BOOK);
        summary.set(DataComponents.CUSTOM_NAME, Component.literal(host.text(player, "rg.relics", run.totalRelics())));
        summary.set(DataComponents.LORE, new ItemLore(owned));
        contents.setItem(22, summary);
    }
    boolean chooseCurrent(ServerPlayer player, int slot) {
        List<RogueRun.Route> offers = run.offers();
        return choose(player, offers.isEmpty() ? -1L : offers.getFirst().token(), slot);
    }
    boolean choose(ServerPlayer player, long token, int slot) {
        if (!eligible(player) || encounter.phase() != PveWaveState.Phase.WAITING) { message(player, "rg.unavailable"); return false; }
        updateBallot();
        if (!run.waitingForChoice() || !ballot.cast(token, player.getUUID(), slot, host.utilityTick())) { message(player, "rg.stale"); return false; }
        message(player, "rg.vote.recorded", slot);
        finishChoice();
        refreshMenus();
        return true;
    }
    private void updateBallot() {
        if (!run.waitingForChoice()) return;
        var voters = host.utilityServer().getPlayerList().getPlayers().stream().filter(this::eligible)
            .map(ServerPlayer::getUUID).collect(java.util.stream.Collectors.toSet());
        ballot.update(run.offers().getFirst().token(), voters, host.utilityTick());
        finishChoice();
    }
    private void finishChoice() {
        if (!run.waitingForChoice() || ballot.decision() == 0) return;
        RogueRun.Route choice = run.choose(ballot.token(), ballot.decision());
        if (choice == null) return;
        encounter.postponeNextWave(host.utilityTick(), PveWaveState.INITIAL_DELAY_TICKS);
        for (ServerPlayer viewer : host.utilityServer().getPlayerList().getPlayers()) {
            if (viewer.containerMenu instanceof RouteMenu menu && menu.token == choice.token()) viewer.closeContainer();
            if (eligible(viewer)) {
                apply(viewer, true);
                message(viewer, "rg.chosen", choice.slot(), host.text(viewer, choice.theme().key()),
                    host.text(viewer, choice.threat().key()), host.text(viewer, choice.relic().key()), run.level(choice.relic()));
            }
        }
    }
    private void offer() {
        for (ServerPlayer player : host.utilityServer().getPlayerList().getPlayers()) if (eligible(player)) {
            message(player, "rg.offer", run.floor());
            open(player);
        }
    }
    void tick() {
        long tick = host.utilityTick();
        if (!run.active() || tick % 20L != 0L || tick <= lastPulse) return;
        lastPulse = tick;
        updateBallot();
        announcedOffers.keySet().retainAll(host.utilityServer().getPlayerList().getPlayers().stream().filter(this::eligible)
            .map(ServerPlayer::getUUID).collect(java.util.stream.Collectors.toSet()));
        for (ServerPlayer player : host.utilityServer().getPlayerList().getPlayers()) {
            boolean eligible = eligible(player);
            apply(player, eligible);
            var notice = announcedOffers.get(player.getUUID());
            if (eligible && run.waitingForChoice() && (notice == null || notice.player != player || notice.token != run.offers().getFirst().token())) {
                message(player, "rg.offer", run.floor()); open(player);
            }
            if (eligible && !run.completed() && run.level(RogueRun.Relic.MANA_SPRING) > 0) {
                host.magic().state().restoreMana(player.getUUID(), run.level(RogueRun.Relic.MANA_SPRING) * 2, host.magicTick());
                host.magic().sync(player);
            }
        }
        refreshMenus();
    }
    void clear() {
        run.clear(); ballot.clear(); lastPulse = -1L; announcedOffers.clear();
        if (host.utilityServer() != null) for (ServerPlayer player : host.utilityServer().getPlayerList().getPlayers()) {
            apply(player, false);
            if (player.containerMenu instanceof RouteMenu || player.containerMenu instanceof DetailsMenu) player.closeContainer();
        }
    }
    String choiceHud(ServerPlayer player) { return host.text(player, "rg.hud.vote", run.floor(), voteStatus(player)); }
    private String voteStatus(ServerPlayer player) {
        long seconds = ballot.secondsRemaining(host.utilityTick());
        return seconds < 0L ? host.text(player, "rg.vote.ready", ballot.submitted(), ballot.participants())
            : host.text(player, "rg.vote.timer", ballot.submitted(), ballot.participants(), seconds);
    }
    private boolean openDetails(ServerPlayer player) {
        if (!canView(player)) return false;
        SimpleContainer contents = new SimpleContainer(45);
        fillDetails(player, contents);
        long epoch = run.epoch();
        player.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> new DetailsMenu(id, inventory, contents, epoch),
            Component.literal(host.text(player, "rg.details.title"))));
        return true;
    }
    private void fillDetails(ServerPlayer player, SimpleContainer contents) {
        long tick = host.utilityTick();
        String phase = run.waitingForChoice() ? host.text(player, "rg.details.wait")
            : run.completed() ? host.text(player, "rg.details.completed")
            : encounter.phase() == PveWaveState.Phase.WAITING
                ? host.text(player, "rg.details.preparing", Math.max(0L, (encounter.nextWaveTick() - tick + 19L) / 20L))
                : host.text(player, "rg.details.fighting", encounter.remaining());
        var summary = encounter.summary(tick);
        contents.setItem(4, item(Items.COMPASS, host.text(player, "rg.details.progress", run.floor(), PveWaves.TOTAL_WAVES, phase), List.of(
            Component.literal(host.text(player, "rg.details.difficulty", host.text(player, encounter.difficulty().languageKey()))),
            Component.literal(host.text(player, "rg.details.stats", summary.clearedWaves(), summary.defeats(), summary.elapsedTicks() / 20L)),
            Component.literal(host.text(player, "rg.details.seed", Long.toUnsignedString(run.seed(), 16))),
            Component.literal(host.text(player, "rg.details.live")))));
        RogueRun.Route route = run.selected();
        contents.setItem(13, route == null ? item(Items.CLOCK, voteStatus(player), List.of(Component.literal(host.text(player, "rg.vote.rules"))))
            : item(Items.MAP, host.text(player, "rg.route.title", route.slot(), host.text(player, route.theme().key()), host.text(player, route.threat().key())),
                List.of(Component.literal(host.text(player, route.theme().key() + ".desc")), Component.literal(host.text(player, route.threat().key() + ".desc")))));
        int slot = 19;
        for (RogueRun.Relic relic : RogueRun.Relic.values()) {
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal(host.text(player, relic.key() + ".desc")));
            if (run.level(relic) == 0) lore.add(Component.literal(host.text(player, "rg.details.unowned")));
            lore.add(Component.literal(host.text(player, "rg.run_only")));
            contents.setItem(slot++, item(icon(relic), host.text(player, "rg.details.level", host.text(player, relic.key()), run.level(relic), relic.maxStacks()), lore));
        }
        boolean voting = run.waitingForChoice() && eligible(player);
        contents.setItem(40, item(voting ? Items.ARROW : Items.BARRIER, host.text(player, voting ? "rg.details.back" : "rg.details.close"), List.of()));
    }
    private void refreshMenus() {
        for (ServerPlayer player : host.utilityServer().getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof RouteMenu menu) {
                if (!menu.stillValid(player)) player.closeContainer();
                else { fillRoutes(player, menu.contents); menu.broadcastChanges(); }
            } else if (player.containerMenu instanceof DetailsMenu menu) {
                if (!menu.stillValid(player)) player.closeContainer();
                else { fillDetails(player, menu.contents); menu.broadcastChanges(); }
            }
        }
    }
    private static ItemStack item(Item type, String title, List<Component> lore) {
        ItemStack result = new ItemStack(type);
        result.set(DataComponents.CUSTOM_NAME, Component.literal(title));
        result.set(DataComponents.LORE, new ItemLore(lore));
        return result;
    }
    private void apply(ServerPlayer player, boolean enabled) {
        modifier(player, Attributes.ATTACK_DAMAGE, RogueRun.Relic.BLADE, enabled ? run.level(RogueRun.Relic.BLADE) * .12D : 0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modifier(player, Attributes.ARMOR, RogueRun.Relic.ARMOR, enabled ? run.level(RogueRun.Relic.ARMOR) * 2D : 0D, AttributeModifier.Operation.ADD_VALUE);
        modifier(player, Attributes.MOVEMENT_SPEED, RogueRun.Relic.STRIDE, enabled ? run.level(RogueRun.Relic.STRIDE) * .06D : 0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modifier(player, Attributes.ATTACK_SPEED, RogueRun.Relic.FURY, enabled ? run.level(RogueRun.Relic.FURY) * .1D : 0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modifier(player, Attributes.KNOCKBACK_RESISTANCE, RogueRun.Relic.POISE, enabled ? run.level(RogueRun.Relic.POISE) * .1D : 0D, AttributeModifier.Operation.ADD_VALUE);
    }
    private void modifier(ServerPlayer player, Holder<Attribute> type, RogueRun.Relic relic, double amount, AttributeModifier.Operation operation) {
        var attribute = player.getAttribute(type);
        if (attribute == null) return;
        Identifier id = Identifier.fromNamespaceAndPath("teamlifebind", "rg_" + relic.name().toLowerCase(java.util.Locale.ROOT));
        var old = attribute.getModifier(id);
        if (old != null && old.amount() == amount && old.operation() == operation) return;
        attribute.removeModifier(id);
        if (amount != 0D) attribute.addTransientModifier(new AttributeModifier(id, amount, operation));
    }
    private static Item icon(RogueRun.Relic relic) {
        return switch (relic) {
            case BLADE -> Items.IRON_SWORD; case ARMOR -> Items.IRON_CHESTPLATE;
            case STRIDE -> Items.FEATHER; case FURY -> Items.GOLDEN_SWORD; case POISE -> Items.SHIELD;
            case MANA_SPRING -> Items.AMETHYST_SHARD; case PROVISIONER -> Items.CHEST; case SCHOLAR -> Items.ENCHANTED_BOOK;
        };
    }
    private void message(ServerPlayer player, String key, Object... args) { if (player != null) player.sendSystemMessage(Component.literal(host.text(player, key, args))); }
    private record OfferNotice(long token, ServerPlayer player) { }
    private final class RouteMenu extends ChestMenu {
        private final long token;
        private final SimpleContainer contents;
        RouteMenu(int id, Inventory playerInventory, SimpleContainer inventory, long token) {
            super(MenuType.GENERIC_9x3, id, playerInventory, inventory, 3); this.token = token; this.contents = inventory;
        }
        @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
        @Override public void clicked(int slot, int button, ContainerInput input, Player player) {
            if (input != ContainerInput.PICKUP || !(player instanceof ServerPlayer viewer) || !stillValid(player)) return;
            if (slot == 10 || slot == 13 || slot == 16) choose(viewer, token, (slot - 7) / 3);
            else if (slot == 22) openDetails(viewer);
        }
        @Override public boolean stillValid(Player player) {
            return player instanceof ServerPlayer viewer && eligible(viewer) && run.waitingForChoice() && run.offers().getFirst().token() == token;
        }
    }
    private final class DetailsMenu extends ChestMenu {
        private final long epoch;
        private final SimpleContainer contents;
        DetailsMenu(int id, Inventory playerInventory, SimpleContainer inventory, long epoch) {
            super(MenuType.GENERIC_9x5, id, playerInventory, inventory, 5); this.epoch = epoch; this.contents = inventory;
        }
        @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
        @Override public void clicked(int slot, int button, ContainerInput input, Player player) {
            if (input == ContainerInput.PICKUP && slot == 40 && player instanceof ServerPlayer viewer && stillValid(player)) {
                if (run.waitingForChoice() && eligible(viewer)) open(viewer); else viewer.closeContainer();
            }
        }
        @Override public boolean stillValid(Player player) { return player instanceof ServerPlayer viewer && canView(viewer) && epoch == run.epoch(); }
    }
}

