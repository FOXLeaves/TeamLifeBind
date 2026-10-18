package com.teamlifebind.paper;

import com.teamlifebind.common.MagicLootRules;
import com.teamlifebind.common.MagicState;
import com.teamlifebind.common.MagicSchool;
import com.teamlifebind.common.MatchMode;
import com.teamlifebind.common.SpellCatalogue;
import com.teamlifebind.common.SpellDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Paper transport for immutable books, three learned slots and server-authoritative mana. */
final class TeamMagicController {
    private static final NamespacedKey KIND = new NamespacedKey("teamlifebind", "magic_kind");
    private static final NamespacedKey SPELL = new NamespacedKey("teamlifebind", "tlb_spellid");
    private static final NamespacedKey PREVIEW = new NamespacedKey("teamlifebind", "magic_preview");
    private static final NamespacedKey ACTION = new NamespacedKey("teamlifebind", "magic_action");
    private static final NamespacedKey TEST_FOCUS = new NamespacedKey("teamlifebind", "magic_test_focus");
    private static final NamespacedKey NATURAL = new NamespacedKey("teamlifebind", "magic_natural_rolled");
    private static final List<String> COMPANIONS = List.of("arcane_grimoire", "mana_potion", "greater_mana_potion", "mana_apple", "amnesia_tome");
    private static final int PAGE_SIZE = 45;
    private final TeamLifeBindPaperPlugin plugin;
    private final MagicState state = new MagicState();
    private final TeamMagicRuntime runtime = new TeamMagicRuntime(this);
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, Long> lastUse = new HashMap<>();
    private final Map<UUID, Long> lastSelect = new HashMap<>();
    private final Map<UUID, Long> lastFocusRecovery = new HashMap<>();
    private final Map<UUID, SavedSession> roundPlayers = new HashMap<>();
    private final Map<UUID, SavedSession> testPlayers = new HashMap<>();
    private final java.util.Random random = new java.util.Random();
    private final Properties persisted = new Properties();
    private final Path playerFile;
    private boolean round;

    TeamMagicController(TeamLifeBindPaperPlugin plugin) {
        this.plugin = plugin;
        playerFile = plugin.getDataFolder().toPath().resolve("magic-players.properties");
        if (Files.isRegularFile(playerFile)) try (var input = Files.newBufferedReader(playerFile)) { persisted.load(input); }
        catch (IOException error) { plugin.getLogger().warning("Could not read magic player data: " + error.getMessage()); }
    }
    MagicState state() { return state; }
    int activeEffectCount() { return runtime.effectCount(); }
    boolean allowed(Player player) {
        return player != null && player.isOnline() && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR
            && (!plugin.hasActiveMatchSession() || plugin.isUtilityParticipant(player));
    }
    boolean pvpAllowed(Player caster, Player target) {
        return allowed(caster) && allowed(target) && plugin.isUtilityParticipant(caster) && plugin.isUtilityParticipant(target)
            && !plugin.shouldCancelFriendlyFire(caster, target);
    }
    boolean holdsFocus(Player player) { return "arcane_grimoire".equals(kind(player.getInventory().getItemInMainHand())); }
    private static String kind(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return null;
        var data = stack.getItemMeta().getPersistentDataContainer();
        if (data.has(PREVIEW, PersistentDataType.BYTE)) return null;
        String kind = data.get(KIND, PersistentDataType.STRING);
        return kind != null && ("spell_book".equals(kind) || COMPANIONS.contains(kind)) ? kind : null;
    }
    static boolean isMagicItem(ItemStack stack) {
        return stack != null && stack.hasItemMeta() && stack.getItemMeta().getPersistentDataContainer().has(KIND, PersistentDataType.STRING);
    }
    static boolean isTestFocus(ItemStack stack) { return stack != null && stack.hasItemMeta() && stack.getItemMeta().getPersistentDataContainer().has(TEST_FOCUS, PersistentDataType.STRING); }
    static SpellDefinition spell(ItemStack stack) {
        if (!"spell_book".equals(kind(stack)) || stack.getType() != Material.BOOK) return null;
        return SpellCatalogue.byId(stack.getItemMeta().getPersistentDataContainer().get(SPELL, PersistentDataType.STRING));
    }
    ItemStack createBook(SpellDefinition spell, Player viewer) {
        if (spell == null) return null;
        ItemStack item = new ItemStack(Material.BOOK); ItemMeta meta = item.getItemMeta();
        meta.setMaxStackSize(1); meta.setItemModel(new NamespacedKey("teamlifebind", "spell_book"));
        var model = meta.getCustomModelDataComponent(); model.setFloats(List.of((float) spell.tier()));
        model.setColors(List.of(org.bukkit.Color.fromRGB(TeamMagicRuntime.color(spell.school())))); meta.setCustomModelDataComponent(model);
        meta.getPersistentDataContainer().set(KIND, PersistentDataType.STRING, "spell_book");
        meta.getPersistentDataContainer().set(SPELL, PersistentDataType.STRING, spell.id());
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + plugin.text(viewer, "magic.book.name", spell.zhName(), spell.enName()));
        meta.setLore(List.of(ChatColor.GOLD + plugin.text(viewer, "magic.book.stats", spell.tier(), spell.manaCost(), spell.cooldownTicks() / 20.0),
            ChatColor.GRAY + plugin.text(viewer, "magic.book.description", spell.zhDescription(), spell.enDescription()),
            ChatColor.AQUA + plugin.text(viewer, "magic.book.learn")));
        item.setItemMeta(meta); return item;
    }
    ItemStack createCompanion(String kind, Player viewer) {
        if (kind == null || !COMPANIONS.contains(kind)) return null;
        Material material = switch (kind) { case "mana_potion", "greater_mana_potion" -> Material.POTION; case "mana_apple" -> Material.APPLE; default -> Material.BOOK; };
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("teamlifebind", kind)); meta.setMaxStackSize("arcane_grimoire".equals(kind) || "amnesia_tome".equals(kind) ? 1 : 16);
        meta.getPersistentDataContainer().set(KIND, PersistentDataType.STRING, kind);
        meta.setDisplayName(ChatColor.AQUA + plugin.text(viewer, "item." + kind + ".name"));
        meta.setLore(java.util.Arrays.stream(plugin.text(viewer, "item." + kind + ".lore").split("\n")).map(line -> ChatColor.GRAY + line).toList());
        item.setItemMeta(meta); return item;
    }
    private void give(Player player, ItemStack item) {
        if (item == null) return;
        for (ItemStack leftover : player.getInventory().addItem(item).values()) player.getWorld().dropItemNaturally(player.getLocation(), leftover);
    }
    private void message(Player player, String key, Object... args) { plugin.utilityActionBar(player, key, args); }

    boolean use(Player player, EquipmentSlot hand, ItemStack eventItem) {
        if (!isMagicItem(eventItem)) return false;
        if (hand != EquipmentSlot.HAND) return true;
        ItemStack stack = player.getInventory().getItemInMainHand(); String kind = kind(stack);
        if (kind == null || !stack.isSimilar(eventItem)) return true;
        if (testPlayers.containsKey(player.getUniqueId()) && !"arcane_grimoire".equals(kind)) stopTest(player, true);
        if (!allowed(player)) { message(player, "magic.unavailable"); return true; }
        long now = plugin.utilityTick(); UUID id = player.getUniqueId();
        if (lastUse.getOrDefault(id, -1L) == now || (lastUse.size() >= MagicState.MAX_PLAYERS && !lastUse.containsKey(id))) return true;
        lastUse.put(id, now);
        boolean consumed = false;
        switch (kind) {
            case "spell_book" -> {
                if (!player.isSneaking()) { message(player, "magic.book.learn"); return true; }
                SpellDefinition spell = spell(stack);
                MagicState.LearnResult result = state.learn(id, spell == null ? "" : spell.id());
                if (!result.accepted()) { message(player, "magic.reason." + result.reason().name()); return true; }
                consumed = true;
                if (state.learned(id).size() == 1 && java.util.Arrays.stream(player.getInventory().getContents()).noneMatch(item -> "arcane_grimoire".equals(kind(item))))
                    give(player, createCompanion("arcane_grimoire", player));
                message(player, "magic.learned", plugin.text(player, "magic.spell.name", spell.zhName(), spell.enName()));
            }
            case "arcane_grimoire" -> { if (player.isSneaking()) openSelection(player); else cast(player); }
            case "mana_potion", "greater_mana_potion" -> {
                int recovered = state.restoreMana(id, kind.equals("mana_potion") ? 40 : 100, now);
                consumed = recovered > 0; message(player, consumed ? "magic.mana.restored" : "magic.mana.full", recovered);
            }
            case "mana_apple" -> { consumed = state.increaseMaxMana(id, now); message(player, consumed ? "magic.mana.increased" : "magic.mana.cap", state.maxMana(id)); }
            case "amnesia_tome" -> {
                if (!player.isSneaking()) { message(player, "magic.amnesia.confirm"); return true; }
                consumed = state.forget(id, state.snapshot(id, now).selectedSlot());
                if (consumed) { interrupt(player); message(player, "magic.forgotten"); } else message(player, "magic.no_spell");
            }
            default -> { }
        }
        if (consumed) {
            stack.setAmount(stack.getAmount() - 1);
            player.getInventory().setItemInMainHand(stack.getAmount() > 0 ? stack : null);
            if (!round) { saveExploration(player); saveFile(); }
        }
        showHud(player); return true;
    }
    boolean swap(Player player) {
        if (!holdsFocus(player)) return false;
        if (!allowed(player)) { message(player, "magic.unavailable"); return true; }
        long now = plugin.utilityTick(); UUID id = player.getUniqueId();
        if (lastSelect.getOrDefault(id, -1L) == now || (lastSelect.size() >= MagicState.MAX_PLAYERS && !lastSelect.containsKey(id))) return true;
        lastSelect.put(id, now);
        List<String> learned = state.learned(id);
        if (learned.isEmpty()) { message(player, "magic.no_spell"); return true; }
        select(player, (state.snapshot(id, now).selectedSlot() + 1) % learned.size()); return true;
    }
    boolean select(Player player, int slot) {
        if (!allowed(player) || !state.selectSlot(player.getUniqueId(), slot)) { message(player, "magic.no_spell"); return false; }
        interruptCasting(player); SpellDefinition spell = SpellCatalogue.byId(state.selected(player.getUniqueId()));
        message(player, "magic.selected", plugin.text(player, "magic.spell.name", spell.zhName(), spell.enName())); return true;
    }
    void cast(Player player) {
        if (testPlayers.containsKey(player.getUniqueId()) && !player.hasPermission("teamlifebind.admin")) stopTest(player, false);
        if (!allowed(player) || !holdsFocus(player)) { message(player, "magic.unavailable"); return; }
        SpellDefinition spell = SpellCatalogue.byId(state.selected(player.getUniqueId()));
        if (spell == null) { message(player, "magic.no_spell"); return; }
        if (!runtime.canLaunch(spell)) { message(player, "magic.reason.LIMIT_REACHED"); return; }
        MagicState.CastResult result = state.beginCast(player.getUniqueId(), spell.id(), plugin.utilityTick(), allowed(player));
        if (!result.accepted()) { message(player, "magic.reason." + result.reason().name()); return; }
        plugin.utilities().interrupt(player); plugin.guns().cancel(player.getUniqueId());
        pending.put(player.getUniqueId(), new Pending(result.token(), player.getWorld(), player.getLocation().toVector()));
        showHud(player);
    }
    void interrupt(Player player) { if (player != null) cancel(player.getUniqueId()); }
    void interruptCasting(Player player) {
        if (player == null) return;
        pending.remove(player.getUniqueId()); state.cancel(player.getUniqueId()); runtime.cancelChannels(player.getUniqueId());
    }
    void cancel(UUID id) { pending.remove(id); state.cancel(id); runtime.cancel(id); }
    boolean spendChannelMana(Player player, int amount) { return state.spendMana(player.getUniqueId(), amount, plugin.utilityTick()); }
    void drainMana(Player caster, Player victim, int amount) { int removed = state.drainMana(victim.getUniqueId(), amount, plugin.utilityTick()); state.restoreMana(caster.getUniqueId(), removed, plugin.utilityTick()); }
    void accelerate(Player caster, int ticks) { state.accelerateCooldowns(caster.getUniqueId(), ticks, plugin.utilityTick()); }
    void tick(long now) {
        runtime.tick(now);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (testPlayers.containsKey(player.getUniqueId()) && (!player.hasPermission("teamlifebind.admin") || !allowed(player))) stopTest(player, false);
            if (testPlayers.containsKey(player.getUniqueId())) { state.restoreMana(player.getUniqueId(), MagicState.MAX_MANA, now); state.accelerateCooldowns(player.getUniqueId(), Integer.MAX_VALUE, now); }
            Pending cast = pending.get(player.getUniqueId());
            if (cast != null) {
                if (!allowed(player) || player.getWorld() != cast.world) cancel(player.getUniqueId());
                else if (!holdsFocus(player) || player.getLocation().toVector().distanceSquared(cast.position) > 9
                    || !cast.token.spellId().equals(state.selected(player.getUniqueId()))) interruptCasting(player);
                else if (now >= cast.token.readyAt()) {
                    MagicState.CastResult result = state.completeCast(player.getUniqueId(), cast.token, now); pending.remove(player.getUniqueId());
                    if (result.accepted() && runtime.canLaunch(result.spell())) runtime.launch(player, result.spell());
                } else if (now % 3 == 0) {
                    var spell = SpellCatalogue.byId(cast.token.spellId());
                    if (spell != null) runtime.preparing(player, spell);
                }
            }
            if (!allowed(player)) cancel(player.getUniqueId());
            state.tick(player.getUniqueId(), now);
            if (now % 10 == 0 && holdsFocus(player) && !plugin.utilities().hasHud(player)) showHud(player);
        }
    }
    private void showHud(Player player) {
        MagicState.Snapshot snapshot = state.snapshot(player.getUniqueId(), plugin.utilityTick());
        SpellDefinition spell = SpellCatalogue.byId(snapshot.selectedSpell());
        message(player, "magic.hud", snapshot.mana(), snapshot.maxMana(), spell == null ? "-" : plugin.text(player, "magic.spell.name", spell.zhName(), spell.enName()),
            (snapshot.cooldownRemainingTicks() + 19) / 20, (snapshot.castRemainingTicks() + 19) / 20);
    }

    void startRound() {
        for (Player player : Bukkit.getOnlinePlayers()) stopTest(player, false);
        for (Player player : Bukkit.getOnlinePlayers()) saveExploration(player);
        saveFile(); round = true; roundPlayers.clear(); pending.clear(); lastUse.clear(); lastSelect.clear(); lastFocusRecovery.clear(); runtime.clear(); state.clearSession();
    }
    void resetRound() {
        for (Player player : Bukkit.getOnlinePlayers()) stopTest(player, false);
        if (round) { pending.clear(); lastUse.clear(); lastSelect.clear(); lastFocusRecovery.clear(); runtime.clear(); state.clearSession(); roundPlayers.clear(); round = false; for (Player player : Bukkit.getOnlinePlayers()) login(player); }
        else { pending.clear(); runtime.clear(); }
    }
    void giveStarter(Player player) {
        if (plugin.campaigns().rules().matchMode() == MatchMode.RANDOM_GRIMOIRE) give(player, createBook(MagicLootRules.pickStarter(random), player));
    }
    List<ItemStack> airdropLoot() {
        List<ItemStack> result = new ArrayList<>(); result.add(createBook(MagicLootRules.pickAirdrop(random), null));
        result.add(createCompanion(random.nextBoolean() ? "mana_potion" : "greater_mana_potion", null));
        if (random.nextInt(4) == 0) { ItemStack apple = createCompanion("mana_apple", null); apple.setAmount(1 + random.nextInt(3)); result.add(apple); }
        if (random.nextInt(20) == 0) result.add(createCompanion("amnesia_tome", null));
        return result;
    }
    void naturalLoot(LootGenerateEvent event) {
        if (event.isPlugin() || !(event.getInventoryHolder() instanceof Container container)) return;
        NamespacedKey key = event.getLootTable().getKey();
        if (!"minecraft".equals(key.getNamespace()) || !key.getKey().startsWith("chests/") || container.getPersistentDataContainer().has(NATURAL, PersistentDataType.BYTE)) return;
        container.getPersistentDataContainer().set(NATURAL, PersistentDataType.BYTE, (byte) 1); container.update(false, false);
        SpellDefinition spell = MagicLootRules.pickNatural(random);
        List<ItemStack> loot = new ArrayList<>(event.getLoot());
        if (spell != null) loot.add(createBook(spell, event.getEntity() instanceof Player player ? player : null));
        int roll = random.nextInt(10000);
        String companion = roll < 1600 ? "mana_potion" : roll < 2100 ? "greater_mana_potion" : roll < 2500 ? "mana_apple" : roll < 2530 ? "amnesia_tome" : null;
        if (companion != null) loot.add(createCompanion(companion, null)); event.setLoot(loot);
    }
    void placedContainer(org.bukkit.block.Block block) {
        if (!(block.getState() instanceof Container container)) return;
        container.getPersistentDataContainer().set(NATURAL, PersistentDataType.BYTE, (byte) 1); container.update(false, false);
    }

    ItemStack devEntry(Player viewer) { return actionItem(Material.ENCHANTED_BOOK, plugin.text(viewer, "magic.dev.entry"), "dev"); }
    boolean isDevEntry(ItemStack item) { return "dev".equals(action(item)); }
    private boolean developer(Player player) {
        if (player.hasPermission("teamlifebind.admin")) return true;
        message(player, "magic.dev.test.denied"); return false;
    }
    void openDevCategories(Player player) {
        if (!developer(player)) return;
        MagicMenu holder = new MagicMenu(player.getUniqueId(), true, 0);
        Inventory inventory = Bukkit.createInventory(holder, 54, plugin.text(player, "magic.dev.categories")); holder.inventory = inventory;
        for (int i = 0; i < MagicSchool.values().length; i++) {
            MagicSchool school = MagicSchool.values()[i];
            SpellDefinition representative = SpellCatalogue.all().stream().filter(spell -> !spell.superTier() && spell.school() == school).findFirst().orElseThrow();
            ItemStack icon = preview(createBook(representative, player), "category:" + school.name());
            ItemMeta meta = icon.getItemMeta(); meta.setDisplayName(ChatColor.GOLD + plugin.text(player, "magic.dev.category", schoolName(player, school))); icon.setItemMeta(meta);
            inventory.setItem(10 + i + (i > 6 ? 2 : 0), icon);
        }
        inventory.setItem(38, actionItem(Material.NETHER_STAR, plugin.text(player, "magic.dev.super"), "category:super"));
        inventory.setItem(42, actionItem(Material.POTION, plugin.text(player, "magic.dev.companions"), "category:companions"));
        inventory.setItem(49, actionItem(Material.BARRIER, plugin.text(player, "magic.menu.close"), "close"));
        inventory.setItem(51, actionItem(Material.REDSTONE, plugin.text(player, "magic.dev.test_stop"), "teststop")); player.openInventory(inventory);
    }
    private String schoolName(Player player, MagicSchool school) { return plugin.text(player, "magic.spell.name", school.zhName(), school.enName()); }
    private ItemStack preview(ItemStack item, String clickAction) {
        ItemMeta meta = item.getItemMeta(); meta.getPersistentDataContainer().set(PREVIEW, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(ACTION, PersistentDataType.STRING, clickAction); item.setItemMeta(meta); return item;
    }
    private void openDevCategory(Player player, String category) {
        if (!developer(player)) return;
        MagicSchool school = null;
        if (!category.equals("super") && !category.equals("companions")) {
            try { school = MagicSchool.valueOf(category); } catch (IllegalArgumentException invalid) { return; }
        }
        String title = school != null ? plugin.text(player, "magic.dev.category", schoolName(player, school)) : plugin.text(player, "magic.dev." + category);
        MagicMenu holder = new MagicMenu(player.getUniqueId(), true, 0, category);
        Inventory inventory = Bukkit.createInventory(holder, 54, title); holder.inventory = inventory;
        if (category.equals("companions")) for (int i = 0; i < COMPANIONS.size(); i++) inventory.setItem(i + 10, preview(createCompanion(COMPANIONS.get(i), player), "detail:" + COMPANIONS.get(i)));
        else {
            MagicSchool selectedSchool = school;
            List<SpellDefinition> spells = SpellCatalogue.all().stream().filter(spell -> category.equals("super") ? spell.superTier() : !spell.superTier() && spell.school() == selectedSchool).toList();
            for (int i = 0; i < spells.size(); i++) inventory.setItem(i, preview(createBook(spells.get(i), player), "detail:" + spells.get(i).id()));
        }
        inventory.setItem(45, actionItem(Material.ARROW, plugin.text(player, "magic.dev.back"), "categories"));
        inventory.setItem(49, actionItem(Material.BARRIER, plugin.text(player, "magic.menu.close"), "close")); player.openInventory(inventory);
    }
    private void openDevDetail(Player player, String id, String category) {
        if (!developer(player)) return;
        SpellDefinition spell = SpellCatalogue.byId(id); ItemStack item = spell == null ? createCompanion(id, player) : createBook(spell, player); if (item == null) return;
        MagicMenu holder = new MagicMenu(player.getUniqueId(), true, 0, category);
        Inventory inventory = Bukkit.createInventory(holder, 54, plugin.text(player, "magic.dev.detail", item.getItemMeta().getDisplayName())); holder.inventory = inventory;
        inventory.setItem(13, preview(item, "none"));
        inventory.setItem(22, actionItem(Material.CHEST, plugin.text(player, "magic.dev.give_one"), "give:" + id));
        if (spell != null) for (int i = 0; i < MagicState.MAX_LEARNED; i++) inventory.setItem(29 + i * 2, actionItem(Material.ENCHANTED_BOOK, plugin.text(player, "magic.dev.test_slot", i + 1), "test:" + i + ":" + id));
        inventory.setItem(45, actionItem(Material.ARROW, plugin.text(player, "magic.dev.back"), category == null ? "categories" : "category:" + category));
        inventory.setItem(49, actionItem(Material.BARRIER, plugin.text(player, "magic.menu.close"), "close"));
        inventory.setItem(51, actionItem(Material.REDSTONE, plugin.text(player, "magic.dev.test_stop"), "teststop")); player.openInventory(inventory);
    }
    private boolean giveOne(Player player, ItemStack item) {
        if (item == null) return false;
        int empty = storageEmpty(player);
        if (empty < 0) { message(player, "magic.dev.full"); return false; }
        item.setAmount(1); player.getInventory().setItem(empty, item); return true;
    }
    private static int storageEmpty(Player player) {
        ItemStack[] items = player.getInventory().getStorageContents();
        for (int i = 0; i < items.length; i++) if (items[i] == null || items[i].getType().isAir()) return i;
        return -1;
    }
    boolean startTest(Player player, String spellId, int slot) {
        if (!developer(player) || !allowed(player)) { message(player, "magic.dev.test.denied"); return false; }
        SpellDefinition spell = SpellCatalogue.byId(spellId); if (spell == null) { message(player, "magic.reason.UNKNOWN_SPELL"); return false; }
        UUID id = player.getUniqueId(); List<String> learned = new ArrayList<>(state.learned(id));
        if (slot < 0 || slot >= MagicState.MAX_LEARNED || slot > learned.size()) { message(player, "magic.dev.test.slot_unavailable", slot + 1); return false; }
        if (!testPlayers.containsKey(id) && testPlayers.size() >= MagicState.MAX_PLAYERS) { message(player, "magic.reason.LIMIT_REACHED"); return false; }
        boolean focus = java.util.Arrays.stream(player.getInventory().getContents()).anyMatch(item -> "arcane_grimoire".equals(kind(item)));
        if (!focus) {
            ItemStack temporaryFocus = createCompanion("arcane_grimoire", player); ItemMeta meta = temporaryFocus.getItemMeta();
            meta.getPersistentDataContainer().set(TEST_FOCUS, PersistentDataType.STRING, id.toString()); temporaryFocus.setItemMeta(meta);
            if (!giveOne(player, temporaryFocus)) { message(player, "magic.dev.test.focus_required"); return false; }
        }
        if (!testPlayers.containsKey(id)) {
            testPlayers.put(id, new SavedSession(state.exportPlayer(id, plugin.utilityTick()), plugin.utilityTick()));
        }
        int existing = learned.indexOf(spellId);
        if (slot == learned.size()) { if (existing < 0) learned.add(spellId); else slot = existing; }
        else if (existing >= 0 && existing != slot) { String previous = learned.get(slot); learned.set(slot, spellId); learned.set(existing, previous); }
        else learned.set(slot, spellId);
        cancel(id); state.remove(id);
        if (!state.restorePlayer(id, new MagicState.SavedPlayer(learned, slot, MagicState.MAX_MANA, MagicState.MAX_MANA, Map.of()), plugin.utilityTick())) throw new IllegalStateException("Valid developer magic state rejected");
        message(player, "magic.dev.test.started", plugin.text(player, "magic.spell.name", spell.zhName(), spell.enName()), slot + 1); return true;
    }
    boolean stopTest(Player player, boolean notify) {
        UUID id = player.getUniqueId(); SavedSession saved = testPlayers.remove(id);
        if (saved == null) { if (notify) message(player, "magic.dev.test.none"); return false; }
        cancel(id); state.remove(id); state.restorePlayer(id, elapsed(saved.data, Math.max(0, plugin.utilityTick() - saved.tick)), plugin.utilityTick());
        ItemStack[] items = player.getInventory().getContents();
        for (int i = 0; i < items.length; i++) if (items[i] != null && items[i].hasItemMeta() && id.toString().equals(items[i].getItemMeta().getPersistentDataContainer().get(TEST_FOCUS, PersistentDataType.STRING))) player.getInventory().setItem(i, null);
        ItemStack cursor = player.getItemOnCursor(); if (isTestFocus(cursor) && id.toString().equals(cursor.getItemMeta().getPersistentDataContainer().get(TEST_FOCUS, PersistentDataType.STRING))) player.setItemOnCursor(null);
        if (notify) message(player, "magic.dev.test.stopped"); return true;
    }
    void openDev(Player player, int page) {
        if (!player.hasPermission("teamlifebind.admin")) { player.sendMessage(plugin.text(player, "command.no_permission")); return; }
        int total = SpellCatalogue.all().size() + COMPANIONS.size(); int pages = (total + PAGE_SIZE - 1) / PAGE_SIZE; page = Math.clamp(page, 0, pages - 1);
        MagicMenu holder = new MagicMenu(player.getUniqueId(), true, page); Inventory inventory = Bukkit.createInventory(holder, 54, plugin.text(player, "magic.dev.title", page + 1, pages)); holder.inventory = inventory;
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = page * PAGE_SIZE + slot; if (index >= total) break;
            ItemStack item = index < SpellCatalogue.all().size() ? createBook(SpellCatalogue.all().get(index), player) : createCompanion(COMPANIONS.get(index - SpellCatalogue.all().size()), player);
            item = preview(item, "detail:" + (index < SpellCatalogue.all().size() ? SpellCatalogue.all().get(index).id() : COMPANIONS.get(index - SpellCatalogue.all().size())));
            ItemMeta meta = item.getItemMeta();
            List<String> lore = new ArrayList<>(meta.getLore()); lore.add(ChatColor.YELLOW + plugin.text(player, "magic.dev.preview")); meta.setLore(lore); item.setItemMeta(meta); inventory.setItem(slot, item);
        }
        for (int slot = 45; slot < 54; slot++) inventory.setItem(slot, actionItem(Material.GRAY_STAINED_GLASS_PANE, " ", "none"));
        if (page > 0) inventory.setItem(45, actionItem(Material.ARROW, plugin.text(player, "magic.menu.prev"), "prev"));
        if (page + 1 < pages) inventory.setItem(53, actionItem(Material.ARROW, plugin.text(player, "magic.menu.next"), "next"));
        inventory.setItem(49, actionItem(Material.BARRIER, plugin.text(player, "magic.menu.close"), "close")); player.openInventory(inventory);
    }
    void openSelection(Player player) {
        if (!allowed(player) || !holdsFocus(player)) { message(player, "magic.unavailable"); return; }
        interruptCasting(player); MagicMenu holder = new MagicMenu(player.getUniqueId(), false, 0);
        Inventory inventory = Bukkit.createInventory(holder, 27, plugin.text(player, "magic.menu.title")); holder.inventory = inventory;
        MagicState.Snapshot snapshot = state.snapshot(player.getUniqueId(), plugin.utilityTick());
        for (int slot = 0; slot < MagicState.MAX_LEARNED; slot++) {
            ItemStack icon;
            if (slot < snapshot.learned().size()) {
                SpellDefinition spell = SpellCatalogue.byId(snapshot.learned().get(slot)); icon = createBook(spell, player);
                ItemMeta meta = icon.getItemMeta(); meta.getPersistentDataContainer().set(ACTION, PersistentDataType.STRING, "select:" + slot);
                meta.getPersistentDataContainer().set(PREVIEW, PersistentDataType.BYTE, (byte) 1); icon.setItemMeta(meta);
            } else icon = actionItem(Material.GRAY_DYE, plugin.text(player, "magic.menu.empty", slot + 1), "none");
            inventory.setItem(11 + slot * 2, icon);
        }
        inventory.setItem(22, actionItem(Material.BARRIER, plugin.text(player, "magic.menu.close"), "close")); player.openInventory(inventory);
    }
    boolean isMenu(Inventory inventory) { return inventory != null && inventory.getHolder() instanceof MagicMenu; }
    boolean menuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MagicMenu menu)) return false;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !menu.owner.equals(player.getUniqueId())) return true;
        if (menu.developer && !player.hasPermission("teamlifebind.admin")) { player.closeInventory(); player.sendMessage(plugin.text(player, "command.no_permission")); return true; }
        if (event.getClickedInventory() != event.getView().getTopInventory() || event.getCurrentItem() == null
            || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)) return true;
        String action = action(event.getCurrentItem());
        if ("close".equals(action)) player.closeInventory();
        else if (menu.developer && "categories".equals(action)) openDevCategories(player);
        else if (menu.developer && "teststop".equals(action)) stopTest(player, true);
        else if (menu.developer && action != null && action.startsWith("category:")) openDevCategory(player, action.substring(9));
        else if (menu.developer && action != null && action.startsWith("detail:")) openDevDetail(player, action.substring(7), menu.category);
        else if (menu.developer && action != null && action.startsWith("give:")) { String id = action.substring(5); SpellDefinition spell = SpellCatalogue.byId(id); giveOne(player, spell == null ? createCompanion(id, player) : createBook(spell, player)); }
        else if (menu.developer && action != null && action.startsWith("test:")) { String[] parts = action.split(":", 3); if (parts.length == 3 && startTest(player, parts[2], Integer.parseInt(parts[1]))) player.closeInventory(); }
        else if (menu.developer && "prev".equals(action)) openDev(player, menu.page - 1);
        else if (menu.developer && "next".equals(action)) openDev(player, menu.page + 1);
        else if (!menu.developer && action != null && action.startsWith("select:")) {
            if (!holdsFocus(player)) { player.closeInventory(); return true; }
            select(player, Integer.parseInt(action.substring(7))); player.closeInventory();
        }
        return true;
    }
    private ItemStack actionItem(Material material, String name, String action) {
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta(); meta.setDisplayName(ChatColor.GOLD + name);
        meta.getPersistentDataContainer().set(ACTION, PersistentDataType.STRING, action); item.setItemMeta(meta); return item;
    }
    private static String action(ItemStack item) { return item == null || !item.hasItemMeta() ? null : item.getItemMeta().getPersistentDataContainer().get(ACTION, PersistentDataType.STRING); }
    List<String> completions(CommandSender sender, String[] args) {
        List<String> candidates = List.of();
        if (args.length == 2) candidates = sender.hasPermission("teamlifebind.admin") ? List.of("list", "select", "cast", "focus", "forget", "give", "test", "teststop") : List.of("list", "select", "cast", "focus");
        if (args.length == 3 && args[1].equalsIgnoreCase("select")) candidates = List.of("1", "2", "3");
        if (args.length == 3 && (args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("test")) && sender.hasPermission("teamlifebind.admin")) {
            candidates = new ArrayList<>(SpellCatalogue.all().stream().map(SpellDefinition::id).toList()); candidates.addAll(COMPANIONS);
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("test") && sender.hasPermission("teamlifebind.admin")) candidates = List.of("1", "2", "3");
        String token = args[args.length - 1].toLowerCase(Locale.ROOT); return candidates.stream().filter(value -> value.startsWith(token)).toList();
    }
    void command(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(plugin.text(sender, "command.player_only")); return; }
        String action = args.length < 2 ? "list" : args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> {
                var snapshot = state.snapshot(player.getUniqueId(), plugin.utilityTick()); player.sendMessage(plugin.text(player, "magic.list.header", snapshot.mana(), snapshot.maxMana()));
                for (int i = 0; i < snapshot.learned().size(); i++) { SpellDefinition spell = SpellCatalogue.byId(snapshot.learned().get(i)); player.sendMessage((i + 1) + ". " + plugin.text(player, "magic.spell.name", spell.zhName(), spell.enName())); }
            }
            case "select" -> { try { select(player, Integer.parseInt(args[2]) - 1); } catch (NumberFormatException | ArrayIndexOutOfBoundsException invalid) { player.sendMessage(plugin.text(player, "magic.command.usage")); } }
            case "cast" -> cast(player);
            case "focus" -> recoverFocus(player);
            case "test" -> {
                try { if (args.length < 3 || args.length > 4) throw new IllegalArgumentException(); startTest(player, args[2], args.length == 4 ? Integer.parseInt(args[3]) - 1 : state.snapshot(player.getUniqueId(), plugin.utilityTick()).selectedSlot()); }
                catch (IllegalArgumentException invalid) { message(player, "magic.dev.test.help"); }
            }
            case "teststop" -> stopTest(player, true);
            case "forget" -> {
                if (!player.hasPermission("teamlifebind.admin")) { player.sendMessage(plugin.text(player, "magic.command.forget_admin")); return; }
                stopTest(player, false);
                if (state.forget(player.getUniqueId(), state.snapshot(player.getUniqueId(), plugin.utilityTick()).selectedSlot())) {
                    interrupt(player); message(player, "magic.forgotten"); if (!round) { saveExploration(player); saveFile(); }
                }
                else message(player, "magic.no_spell");
            }
            case "give" -> {
                if (!player.hasPermission("teamlifebind.admin")) { player.sendMessage(plugin.text(player, "command.no_permission")); return; }
                if (args.length != 3) { player.sendMessage(plugin.text(player, "magic.command.usage")); return; }
                SpellDefinition spell = SpellCatalogue.byId(args[2]); ItemStack item = spell == null ? createCompanion(args[2], player) : createBook(spell, player);
                if (item == null) message(player, "magic.reason.UNKNOWN_SPELL"); else giveOne(player, item);
            }
            default -> player.sendMessage(plugin.text(player, "magic.command.usage"));
        }
    }

    boolean recoverFocus(Player player) {
        if (!allowed(player) || state.learned(player.getUniqueId()).isEmpty()) { message(player, "magic.no_spell"); return false; }
        if (java.util.Arrays.stream(player.getInventory().getContents()).anyMatch(item -> "arcane_grimoire".equals(kind(item)))) {
            message(player, "magic.focus.exists"); return false;
        }
        long now = plugin.utilityTick(); UUID id = player.getUniqueId();
        if (lastFocusRecovery.getOrDefault(id, -200L) + 200L > now || (lastFocusRecovery.size() >= MagicState.MAX_PLAYERS && !lastFocusRecovery.containsKey(id))) return false;
        lastFocusRecovery.put(id, now); give(player, createCompanion("arcane_grimoire", player)); message(player, "magic.focus.recovered"); return true;
    }

    void login(Player player) {
        UUID id = player.getUniqueId(); long now = plugin.utilityTick();
        if (round) {
            SavedSession saved = roundPlayers.remove(id);
            if (saved != null) state.restorePlayer(id, elapsed(saved.data, Math.max(0, now - saved.tick)), now);
            return;
        }
        String prefix = id + ".";
        if (!persisted.containsKey(prefix + "learned")) return;
        try {
            String ids = persisted.getProperty(prefix + "learned", ""); List<String> learned = ids.isEmpty() ? List.of() : List.of(ids.split(","));
            Map<String, Long> cooldowns = new HashMap<>(); String raw = persisted.getProperty(prefix + "cooldowns", "");
            if (!raw.isEmpty()) for (String entry : raw.split(",")) { String[] pair = entry.split(":", 2); if (pair.length != 2) throw new IllegalArgumentException("Malformed cooldown"); cooldowns.put(pair[0], Long.parseLong(pair[1])); }
            MagicState.SavedPlayer saved = new MagicState.SavedPlayer(learned, Integer.parseInt(persisted.getProperty(prefix + "selected", "0")),
                Integer.parseInt(persisted.getProperty(prefix + "mana", "100")), Integer.parseInt(persisted.getProperty(prefix + "max", "100")), cooldowns);
            long stamp = Long.parseLong(persisted.getProperty(prefix + "savedAt", "0")); long offline = stamp <= 0 ? 0 : Math.max(0, System.currentTimeMillis() - stamp) / 50;
            if (!state.restorePlayer(id, elapsed(saved, offline), now)) plugin.getLogger().warning("Rejected invalid or already loaded magic data for " + id);
        } catch (IllegalArgumentException invalid) { plugin.getLogger().warning("Rejected invalid magic data for " + id + ": " + invalid.getMessage()); }
    }
    private static MagicState.SavedPlayer elapsed(MagicState.SavedPlayer saved, long ticks) {
        Map<String, Long> cooldowns = new HashMap<>(); saved.cooldownRemaining().forEach((id, left) -> { long remaining = Math.max(0, left - ticks); if (remaining > 0) cooldowns.put(id, remaining); });
        return new MagicState.SavedPlayer(saved.learned(), saved.selectedSlot(), saved.mana(), saved.maxMana(), cooldowns);
    }
    private void saveExploration(Player player) {
        if (testPlayers.containsKey(player.getUniqueId())) return;
        UUID id = player.getUniqueId(); MagicState.SavedPlayer saved = state.exportPlayer(id, plugin.utilityTick()); String prefix = id + ".";
        persisted.setProperty(prefix + "learned", String.join(",", saved.learned())); persisted.setProperty(prefix + "selected", Integer.toString(saved.selectedSlot()));
        persisted.setProperty(prefix + "mana", Integer.toString(saved.mana())); persisted.setProperty(prefix + "max", Integer.toString(saved.maxMana()));
        persisted.setProperty(prefix + "cooldowns", saved.cooldownRemaining().entrySet().stream().map(entry -> entry.getKey() + ":" + entry.getValue()).collect(java.util.stream.Collectors.joining(",")));
        persisted.setProperty(prefix + "savedAt", Long.toString(System.currentTimeMillis()));
    }
    void disconnect(Player player) {
        stopTest(player, false);
        UUID id = player.getUniqueId();
        if (round) {
            if (roundPlayers.size() < MagicState.MAX_PLAYERS || roundPlayers.containsKey(id)) roundPlayers.put(id, new SavedSession(state.exportPlayer(id, plugin.utilityTick()), plugin.utilityTick()));
        } else { saveExploration(player); saveFile(); }
        cancel(id); lastUse.remove(id); lastSelect.remove(id); lastFocusRecovery.remove(id); state.remove(id);
    }
    void close() { for (Player player : Bukkit.getOnlinePlayers()) disconnect(player); runtime.clear(); pending.clear(); saveFile(); }
    private void saveFile() {
        try { Files.createDirectories(playerFile.getParent()); Path temporary = playerFile.resolveSibling("magic-players.properties.tmp");
            try (var writer = Files.newBufferedWriter(temporary)) { persisted.store(writer, "TeamLifeBind magic exploration state"); }
            try { Files.move(temporary, playerFile, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) { Files.move(temporary, playerFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException error) { plugin.getLogger().warning("Could not save magic player data: " + error.getMessage()); }
    }
    private record Pending(MagicState.CastToken token, World world, org.bukkit.util.Vector position) { }
    private record SavedSession(MagicState.SavedPlayer data, long tick) { }
    private static final class MagicMenu implements InventoryHolder {
        final UUID owner; final boolean developer; final int page; final String category; Inventory inventory;
        MagicMenu(UUID owner, boolean developer, int page) { this(owner, developer, page, null); }
        MagicMenu(UUID owner, boolean developer, int page, String category) { this.owner = owner; this.developer = developer; this.page = page; this.category = category; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
