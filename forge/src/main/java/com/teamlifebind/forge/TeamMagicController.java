package com.teamlifebind.forge;

import com.teamlifebind.common.MagicLootRules;
import com.teamlifebind.common.MagicState;
import com.teamlifebind.common.SpellCatalogue;
import com.teamlifebind.common.SpellDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Properties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;

/** Knowledge and payment live on the server. Books only name immutable catalogue entries. */
public final class TeamMagicController {
    public static final String SPELL_ID = "tlb_spell";
    private final TeamLifeBindForgeManager host;
    private final MagicState state = new MagicState();
    private final TeamMagicRuntime runtime = new TeamMagicRuntime(this);
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, Long> lastInput = new HashMap<>();
    private final Map<UUID, Long> lastUse = new HashMap<>();
    private final Map<UUID, Long> lastFocusRecovery = new HashMap<>();
    private final Map<RandomizableContainer, NaturalCapture> natural = new java.util.WeakHashMap<>();
    private final java.util.random.RandomGenerator random = new java.util.Random();
    private final Map<UUID, StoredKnowledge> exploration = new HashMap<>();
    private final Map<UUID, StoredKnowledge> survivalPlayers = new HashMap<>();
    private MinecraftServer loadedServer;
    private Path persistence;
    private boolean matchSession;
    private boolean survivalSession;
    private final Map<UUID, DeveloperTest> developerTests = new HashMap<>();
    private static final String TEST_CARRIER = "tlb_magic_test_carrier";

    public TeamMagicController(TeamLifeBindForgeManager host) { this.host = host; }
    public MagicState state() { return state; }
    public int activeEffectCount() { return runtime.effectCount(); }
    public boolean allowed(ServerPlayer player) { return host.magicUserAllowed(player); }
    public boolean pvpAllowed(ServerPlayer caster, ServerPlayer target) { return host.magicCombatAllowed(caster) && host.magicCombatAllowed(target) && allowed(target) && !host.shouldCancelFriendlyFire(caster, target); }
    public boolean holdsFocus(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof TeamMagicItem magic) || magic.kind() != TeamMagicItem.Kind.GRIMOIRE) return false;
        CompoundTag tag = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.getBoolean("tlb_magic_preview").orElse(false)) return false;
        String carrier = tag.getString(TEST_CARRIER).orElse("");
        DeveloperTest test = developerTests.get(player.getUUID());
        return carrier.isEmpty() || test != null && carrier.equals(test.carrier.toString());
    }
    void recordAttack(ServerPlayer caster, LivingEntity target) { host.recordCampaignAttack(caster, target); }
    void interrupt(ServerPlayer player) { runtime.cancelPreparing(player.getUUID()); boolean interrupted = pending.remove(player.getUUID()) != null; state.cancel(player.getUUID()); interrupted |= runtime.cancelChannel(player.getUUID()); if (interrupted) sync(player); }
    public void interruptCasting(ServerPlayer player) { interrupt(player); }
    boolean spendChannelMana(ServerPlayer player, int amount) { return state.spendMana(player.getUUID(), amount, host.magicTick()); }
    void drainMana(ServerPlayer caster, ServerPlayer victim, int amount) { int removed = state.drainMana(victim.getUUID(), amount, host.magicTick()); state.restoreMana(caster.getUUID(), removed, host.magicTick()); sync(victim); sync(caster); }
    void accelerate(ServerPlayer caster, int ticks) { state.accelerateCooldowns(caster.getUUID(), ticks, host.magicTick()); }

    private boolean testAdministrator(ServerPlayer player) { return player != null && player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER); }
    public boolean developerTesting(UUID player) { return developerTests.containsKey(player); }

    /** A developer profile is temporary and never enters the exploration save file. */
    public boolean testSpell(ServerPlayer player, String id, int requestedSlot) {
        if (!testAdministrator(player) || !allowed(player)) { message(player, "magic.dev.test.denied"); return false; }
        SpellDefinition spell = SpellCatalogue.byId(id);
        if (spell == null || requestedSlot < 0 || requestedSlot >= MagicState.MAX_LEARNED) return false;
        restoreExploration(player);
        long now = host.magicTick();
        MagicState.SavedPlayer current = state.exportPlayer(player.getUUID(), now);
        if (requestedSlot > current.learned().size()) { message(player, "magic.dev.test.slot_unavailable", requestedSlot + 1); return false; }
        DeveloperTest test = developerTests.get(player.getUUID());
        if (test == null) {
            if (developerTests.size() >= MagicState.MAX_PLAYERS) { message(player, "magic.reason.LIMIT_REACHED"); return false; }
            // Persist the normal profile before temporary 300/300 state can replace it.
            if (!matchSession) { remember(player.getUUID()); save(); }
            test = new DeveloperTest(current, now, System.currentTimeMillis(), UUID.randomUUID());
            developerTests.put(player.getUUID(), test);

        }
        List<String> learned = new ArrayList<>(current.learned());
        int existing = learned.indexOf(spell.id());
        if (requestedSlot == learned.size()) {
            if (existing >= 0) requestedSlot = existing;
            else learned.add(spell.id());
        } else if (existing >= 0 && existing != requestedSlot) {
            String displaced = learned.get(requestedSlot); learned.set(requestedSlot, spell.id()); learned.set(existing, displaced);
        } else learned.set(requestedSlot, spell.id());
        cancel(player.getUUID()); state.remove(player.getUUID());
        boolean restored = state.restorePlayer(player.getUUID(), new MagicState.SavedPlayer(learned, requestedSlot, MagicState.MAX_MANA, MagicState.MAX_MANA, Map.of()), now);
        if (!restored) { stopDeveloperTest(player, false); message(player, "magic.reason.LIMIT_REACHED"); return false; }
        lastInput.remove(player.getUUID()); lastUse.remove(player.getUUID());
        sync(player); message(player, "magic.dev.test.started", local(player, "magic.spell.name", spell.zhName(), spell.enName()), requestedSlot + 1);
        return true;
    }
    public boolean stopDeveloperTest(ServerPlayer player) { return stopDeveloperTest(player, true); }
    private boolean stopDeveloperTest(ServerPlayer player, boolean feedback) {
        if (player == null) return false;
        return stopDeveloperTest(player.getUUID(), player, feedback);
    }
    private boolean stopDeveloperTest(UUID playerId, ServerPlayer player, boolean feedback) {
        DeveloperTest test = developerTests.remove(playerId);
        if (test == null) { if (feedback) message(player, "magic.dev.test.none"); return false; }
        long now = host.magicTick();
        long elapsed = Math.max(Math.max(0, now - test.started), Math.max(0, System.currentTimeMillis() - test.wallTime) / 50);
        Map<String, Long> remaining = new HashMap<>();
        test.original.cooldownRemaining().forEach((id, ticks) -> remaining.put(id, Math.max(0, ticks - elapsed)));
        cancel(playerId); state.remove(playerId);
        state.restorePlayer(playerId, new MagicState.SavedPlayer(test.original.learned(), test.original.selectedSlot(), test.original.mana(), test.original.maxMana(), remaining), now);
        if (player != null) for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString(TEST_CARRIER).orElse("").equals(test.carrier.toString())) player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
        lastInput.remove(playerId); lastUse.remove(playerId);
        if (player != null) { sync(player); if (feedback) message(player, "magic.dev.test.stopped"); }
        return true;
    }
    private void stopAllDeveloperTests(MinecraftServer server) {
        for (UUID id : List.copyOf(developerTests.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            stopDeveloperTest(id, player, false);
        }
    }

    public ItemStack createBook(SpellDefinition spell, ServerPlayer viewer) {
        if (spell == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(TeamLifeBindForge.magicItem(TeamMagicItem.Kind.SPELL_BOOK));
        CompoundTag data = new CompoundTag(); data.putString(SPELL_ID, spell.id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        refreshLore(stack, viewer); return stack;
    }
    public ItemStack createBook(String spellId, ServerPlayer viewer) { return createBook(SpellCatalogue.byId(spellId), viewer); }
    public ItemStack createCompanion(TeamMagicItem.Kind kind, ServerPlayer viewer) {
        if (kind == TeamMagicItem.Kind.SPELL_BOOK) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(TeamLifeBindForge.magicItem(kind)); refreshLore(stack, viewer); return stack;
    }
    public static SpellDefinition spell(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof TeamMagicItem item) || item.kind() != TeamMagicItem.Kind.SPELL_BOOK) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        return tag.getBoolean("tlb_magic_preview").orElse(false) ? null : SpellCatalogue.byId(tag.getString(SPELL_ID).orElse(""));
    }
    public void refreshLore(ItemStack stack, ServerPlayer viewer) {
        if (stack == null || !(stack.getItem() instanceof TeamMagicItem item)) return;
        SpellDefinition spell = spell(stack); List<Component> lore = new ArrayList<>();
        if (item.kind() == TeamMagicItem.Kind.SPELL_BOOK && spell != null) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of((float) spell.tier()), List.of(), List.of(), List.of(TeamMagicRuntime.color(spell.school()))));
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(local(viewer, "magic.book.name", spell.zhName(), spell.enName())));
            lore.add(Component.literal(local(viewer, "magic.book.stats", spell.tier(), spell.manaCost(), spell.cooldownTicks() / 20.0)));
            lore.add(Component.literal(local(viewer, "magic.book.description", spell.zhDescription(), spell.enDescription())));
            lore.add(Component.literal(local(viewer, "magic.book.learn")));
        } else {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(local(viewer, "item." + item.kind().id() + ".name")));
            for (String line : local(viewer, "item." + item.kind().id() + ".lore").split("\n")) lore.add(Component.literal(line));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
    }
    private String local(ServerPlayer viewer, String key, Object... args) { return viewer == null ? host.text(key, args) : host.text(viewer, key, args); }
    private void message(ServerPlayer player, String key, Object... args) { player.sendSystemMessage(Component.literal(host.campaignOverlay(player, local(player, key, args))), true); }

    public boolean useItem(ServerPlayer player, ItemStack stack, TeamMagicItem.Kind kind) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof TeamMagicItem item) || item.kind() != kind) return false;
        CustomData itemData = stack.get(DataComponents.CUSTOM_DATA);
        if (itemData != null && itemData.copyTag().getBoolean("tlb_magic_preview").orElse(false)) return false;
        if (!allowed(player)) { message(player, "magic.unavailable"); return false; }
        if (developerTesting(player.getUUID()) && kind != TeamMagicItem.Kind.GRIMOIRE) { message(player, "magic.dev.test.stop_first"); return false; }
        restoreExploration(player);
        long now = host.magicTick();
        if (lastUse.getOrDefault(player.getUUID(), -1L) == now) return false;
        if (lastUse.size() >= MagicState.MAX_PLAYERS && !lastUse.containsKey(player.getUUID())) return false;
        lastUse.put(player.getUUID(), now);
        boolean consumed = false;
        switch (kind) {
            case SPELL_BOOK -> {
                if (!player.isShiftKeyDown()) { message(player, "magic.book.learn"); return true; }
                SpellDefinition spell = spell(stack);
                MagicState.LearnResult result = state.learn(player.getUUID(), spell == null ? "" : spell.id());
                if (!result.accepted()) { message(player, "magic.reason." + result.reason().name()); sync(player); return false; }
                consumed = true;
                message(player, "magic.learned", local(player, "magic.spell.name", spell.zhName(), spell.enName()));
            }
            case GRIMOIRE -> { message(player, "magic.controls.native"); sync(player); return true; }
            case MANA_POTION, GREATER_MANA_POTION -> {
                int restored = state.restoreMana(player.getUUID(), kind == TeamMagicItem.Kind.MANA_POTION ? 40 : 100, now);
                consumed = restored > 0; message(player, consumed ? "magic.mana.restored" : "magic.mana.full", restored);
            }
            case MANA_APPLE -> { consumed = state.increaseMaxMana(player.getUUID(), now); message(player, consumed ? "magic.mana.increased" : "magic.mana.cap", state.maxMana(player.getUUID())); }
            case AMNESIA -> { if (!player.isShiftKeyDown()) { message(player, "magic.amnesia.confirm"); return true; } consumed = state.forget(player.getUUID(), state.snapshot(player.getUUID(), now).selectedSlot()); if (consumed) { interrupt(player); message(player, "magic.forgotten"); } else message(player, "magic.no_spell"); }
        }
        if (consumed) {
            stack.shrink(1);
            if (kind == TeamMagicItem.Kind.MANA_POTION || kind == TeamMagicItem.Kind.GREATER_MANA_POTION)
                WorldVfx.player(player, WorldVfxPayload.Kind.MANA, 1.4F);
            else if (kind == TeamMagicItem.Kind.MANA_APPLE) WorldVfx.player(player, WorldVfxPayload.Kind.MANA_GROWTH, 1.5F);
            else if (kind == TeamMagicItem.Kind.SPELL_BOOK) WorldVfx.player(player, WorldVfxPayload.Kind.SPELL_LEARN, 1.5F);
        }
        if (consumed && !matchSession) { remember(player.getUUID()); save(); }
        sync(player); return consumed;
    }
    private void give(ServerPlayer player, ItemStack stack) { if (!stack.isEmpty() && !player.getInventory().add(stack)) player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY); }
    public boolean recoverFocus(ServerPlayer player) {
        if (developerTesting(player.getUUID())) { message(player, "magic.dev.test.stop_first"); return false; }
        restoreExploration(player);
        if (!allowed(player) || state.learned(player.getUUID()).isEmpty()) { message(player, "magic.no_spell"); return false; }
        if (player.getInventory().contains(stack -> stack.getItem() instanceof TeamMagicItem item && item.kind() == TeamMagicItem.Kind.GRIMOIRE)) { message(player, "magic.focus.exists"); return false; }
        long now = host.magicTick();
        if (lastFocusRecovery.getOrDefault(player.getUUID(), -200L) + 200L > now || lastFocusRecovery.size() >= MagicState.MAX_PLAYERS && !lastFocusRecovery.containsKey(player.getUUID())) return false;
        lastFocusRecovery.put(player.getUUID(), now); give(player, createCompanion(TeamMagicItem.Kind.GRIMOIRE, player)); message(player, "magic.focus.recovered"); return true;
    }

    public boolean forgetSelected(ServerPlayer player) {
        if (!player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)) return false;
        stopDeveloperTest(player, false);
        restoreExploration(player);
        boolean forgotten = state.forget(player.getUUID(), state.snapshot(player.getUUID(), host.magicTick()).selectedSlot());
        if (forgotten) { interrupt(player); message(player, "magic.forgotten"); if (!matchSession) { remember(player.getUUID()); save(); } }
        else message(player, "magic.no_spell");
        sync(player); return forgotten;
    }

    public void acceptInput(ServerPlayer player, MagicInputPayload input) {
        if (developerTesting(player.getUUID()) && !testAdministrator(player)) stopDeveloperTest(player, false);
        if (input.action() == MagicInputPayload.CANCEL) { interrupt(player); return; }
        if (input.slot() < 0 || input.slot() >= MagicState.MAX_LEARNED || input.action() < MagicInputPayload.SELECT || input.action() > MagicInputPayload.CAST) return;
        long now = host.magicTick();
        if (lastInput.getOrDefault(player.getUUID(), -1L) == now) return;
        if (lastInput.size() >= MagicState.MAX_PLAYERS && !lastInput.containsKey(player.getUUID())) return;
        lastInput.put(player.getUUID(), now);
        if (!allowed(player)) { cancel(player.getUUID()); message(player, "magic.unavailable"); return; }
        restoreExploration(player);
        if (!state.selectSlot(player.getUUID(), input.slot())) { message(player, "magic.no_spell"); sync(player); return; }
        if (input.action() == MagicInputPayload.SELECT) { interruptCasting(player); sync(player); return; }
        SpellDefinition spell = SpellCatalogue.byId(state.selected(player.getUUID()));
        if (spell == null) { message(player, "magic.no_spell"); return; }
        if (!runtime.canLaunch(spell)) { message(player, "magic.reason.LIMIT_REACHED"); return; }
        MagicState.CastResult result = state.beginCast(player.getUUID(), spell.id(), now, allowed(player));
        if (!result.accepted()) { message(player, "magic.reason." + result.reason().name()); sync(player); return; }
        pending.put(player.getUUID(), new Pending(result.token(), player.level(), player.position()));
        sync(player);
    }
    public void tick(MinecraftServer server) {
        if (loadedServer != server) load(server);
        long now = host.magicTick();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (developerTesting(player.getUUID())) {
                if (!testAdministrator(player) || !allowed(player)) stopDeveloperTest(player, false);
                else { state.restoreMana(player.getUUID(), MagicState.MAX_MANA, now); state.accelerateCooldowns(player.getUUID(), 120000, now); }
            }
        }
        runtime.tick(server, now);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Pending cast = pending.get(player.getUUID());
            if (cast != null) {
                if (!allowed(player) || player.level() != cast.level) cancel(player.getUUID());
                else if (player.position().distanceToSqr(cast.position) > 9) interruptCasting(player);
                else if (now >= cast.token.readyAt()) {
                    MagicState.CastResult result = state.completeCast(player.getUUID(), cast.token, now);
                    pending.remove(player.getUUID());
                    if (result.accepted() && runtime.canLaunch(result.spell())) runtime.launch(player, result.spell());
                } else if (now % 2 == 0) runtime.previewCast(player, SpellCatalogue.byId(cast.token.spellId()), (int) Math.max(0, now - cast.token.startedAt()));
            }
            if (!allowed(player)) cancel(player.getUUID());
            state.tick(player.getUUID(), now);
            if (now % 10 == 0 && (!state.learned(player.getUUID()).isEmpty() || holdsFocus(player))) sync(player);
        }
    }
    public void sync(ServerPlayer player) {
        restoreExploration(player);
        MagicState.Snapshot snapshot = state.snapshot(player.getUUID(), host.magicTick());
        List<Integer> cooldowns = snapshot.learned().stream().map(id -> (int) Math.min(12000, state.cooldownRemaining(player.getUUID(), id, host.magicTick()))).toList();
        TeamMagicNetwork.sendSync(player, new MagicSyncPayload(snapshot.mana(), snapshot.maxMana(), snapshot.selectedSlot(), (int) Math.min(12000, snapshot.castRemainingTicks()), snapshot.learned(), cooldowns));
    }
    public void cancel(UUID player) { pending.remove(player); state.cancel(player); runtime.cancel(player); }
    /** A new connection never resumes a temporary developer profile or its generated focus. */
    public void login(ServerPlayer player) {
        stopDeveloperTest(player, false);
        StoredKnowledge reconnect = survivalPlayers.remove(player.getUUID());
        if (survivalSession && reconnect != null) restoreStored(player, reconnect);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item.getItem() instanceof TeamMagicItem magic && magic.kind() == TeamMagicItem.Kind.GRIMOIRE
                && !item.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString(TEST_CARRIER).orElse("").isEmpty()) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
        sync(player);
    }
    public void disconnect(UUID player) {
        stopDeveloperTest(player, loadedServer == null ? null : loadedServer.getPlayerList().getPlayer(player), false);
        cancel(player); lastInput.remove(player); lastUse.remove(player); lastFocusRecovery.remove(player);
        if (!matchSession) {
            if (survivalSession && (survivalPlayers.size() < 4096 || survivalPlayers.containsKey(player)))
                survivalPlayers.put(player, new StoredKnowledge(state.exportPlayer(player, host.magicTick()), System.currentTimeMillis()));
            remember(player); state.remove(player); save();
        }
    }
    public void clearSession() { if (loadedServer != null) stopAllDeveloperTests(loadedServer); pending.clear(); lastInput.clear(); lastUse.clear(); lastFocusRecovery.clear(); survivalPlayers.clear(); natural.clear(); runtime.clear(); state.clearSession(); }
    public void giveStarter(ServerPlayer player) { give(player, createBook(MagicLootRules.pickStarter(random), player)); }
    public ItemStack airdropBook() { return createBook(MagicLootRules.pickAirdrop(random), null); }
    public void captureNatural(RandomizableContainer source, ResourceKey<LootTable> table) {
        if (!(source instanceof BlockEntity block) || !(block.getLevel() instanceof ServerLevel)) return;
        NaturalCapture capture = natural.get(source);
        if (capture != null) { capture.depth++; return; }
        if (table != null && natural.size() < 128) natural.put(source, new NaturalCapture(table));
    }
    public void finishNatural(RandomizableContainer source, net.minecraft.world.entity.player.Player opener) {
        NaturalCapture capture = natural.get(source);
        if (capture == null || --capture.depth > 0) return;
        natural.remove(source);
        if (source.getLootTable() == null) injectNatural(source, opener, capture.table);
    }
    public void injectNatural(RandomizableContainer source, net.minecraft.world.entity.player.Player opener, ResourceKey<LootTable> original) {
        if (original == null || !original.identifier().getNamespace().equals("minecraft") || !original.identifier().getPath().startsWith("chests/") || !(source instanceof Container container) || !(source instanceof BlockEntity block) || !(block.getLevel() instanceof ServerLevel)) return;
        ServerPlayer viewer = opener instanceof ServerPlayer player ? player : null;
        SpellDefinition selected = MagicLootRules.pickNatural(random);
        if (selected != null) insertNatural(container, block, createBook(selected, viewer));
        int roll = random.nextInt(10000);
        TeamMagicItem.Kind companion = roll < 1600 ? TeamMagicItem.Kind.MANA_POTION : roll < 2100 ? TeamMagicItem.Kind.GREATER_MANA_POTION : roll < 2500 ? TeamMagicItem.Kind.MANA_APPLE : roll < 2530 ? TeamMagicItem.Kind.AMNESIA : null;
        if (companion != null) insertNatural(container, block, createCompanion(companion, viewer));
        if (random.nextInt(5) == 0) insertNatural(container, block, com.teamlifebind.gear.GunModuleItem.randomPart(random));
    }
    private void insertNatural(Container container, BlockEntity block, ItemStack stack) { for (int slot = 0; slot < container.getContainerSize(); slot++) if (container.getItem(slot).isEmpty()) { container.setItem(slot, stack); block.setChanged(); return; } }
    public List<ItemStack> airdropLoot() {
        List<ItemStack> loot = new ArrayList<>(); loot.add(airdropBook());
        loot.add(createCompanion(random.nextBoolean() ? TeamMagicItem.Kind.MANA_POTION : TeamMagicItem.Kind.GREATER_MANA_POTION, null));
        if (random.nextInt(4) == 0) { ItemStack apple = createCompanion(TeamMagicItem.Kind.MANA_APPLE, null); apple.setCount(1 + random.nextInt(3)); loot.add(apple); }
        if (random.nextInt(20) == 0) loot.add(createCompanion(TeamMagicItem.Kind.AMNESIA, null));
        return List.copyOf(loot);
    }
    public void load(MinecraftServer server) {
        if (loadedServer == server) return;
        loadedServer = server; persistence = server.getServerDirectory().resolve("config/teamlifebind-magic.properties");
        exploration.clear();
        if (Files.isRegularFile(persistence)) try {
            if (Files.size(persistence) > 2097152) throw new IOException("Magic knowledge file is too large");
            Properties data = new Properties(); try (var input = Files.newInputStream(persistence)) { data.load(input); }
            for (String key : data.stringPropertyNames()) {
                if (exploration.size() >= 4096) break;
                try { UUID id = UUID.fromString(key); StoredKnowledge knowledge = decodeStored(data.getProperty(key)); if (knowledge != null) exploration.put(id, knowledge); }
                catch (IllegalArgumentException invalid) { TeamLifeBindForge.LOGGER.debug("Ignoring invalid magic knowledge entry {}", key); }
            }
        } catch (IOException error) { TeamLifeBindForge.LOGGER.error("Cannot read magic knowledge {}", persistence, error); }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) restoreExploration(player);
    }
    public void beginMatch(MinecraftServer server) {
        survivalSession = false;
        load(server);
        stopAllDeveloperTests(server);
        if (!matchSession) { for (ServerPlayer player : server.getPlayerList().getPlayers()) remember(player.getUUID()); save(); }
        clearSession(); matchSession = true;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sync(player);
    }
    public void endMatch(MinecraftServer server) {
        if (survivalSession) {
            stopAllDeveloperTests(server);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) remember(player.getUUID());
            save();
        }
        survivalSession = false;
        clearSession(); matchSession = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) { restoreExploration(player); sync(player); }
    }
    public void shutdown(MinecraftServer server) {
        stopAllDeveloperTests(server);
        if (!matchSession) for (ServerPlayer player : server.getPlayerList().getPlayers()) remember(player.getUUID());
        save(); clearSession(); survivalSession = false; loadedServer = null;
    }
    public void beginSurvival(MinecraftServer server) {
        load(server);
        stopAllDeveloperTests(server);
        matchSession = false;
        survivalPlayers.clear();
        survivalSession = true;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sync(player);
    }
    private void remember(UUID id) {
        if (developerTesting(id)) return;
        if (exploration.size() < 4096 || exploration.containsKey(id)) exploration.put(id, new StoredKnowledge(state.exportPlayer(id, host.magicTick()).withoutManaGrowth(), System.currentTimeMillis()));
    }
    private void restoreExploration(ServerPlayer player) {
        if (matchSession) return;
        StoredKnowledge stored = exploration.get(player.getUUID());
        if (stored == null) return;
        restoreStored(player, stored);
    }
    private void restoreStored(ServerPlayer player, StoredKnowledge stored) {
        long elapsed = Math.max(0, System.currentTimeMillis() - stored.savedAt) / 50;
        Map<String, Long> remaining = new HashMap<>(); stored.state.cooldownRemaining().forEach((id, ticks) -> remaining.put(id, Math.max(0, ticks - elapsed)));
        state.restorePlayer(player.getUUID(), new MagicState.SavedPlayer(stored.state.learned(), stored.state.selectedSlot(), stored.state.mana(), stored.state.maxMana(), remaining), host.magicTick());
    }
    private void save() {
        if (persistence == null) return;
        try {
            Files.createDirectories(persistence.getParent()); Properties data = new Properties();
            exploration.forEach((id, knowledge) -> data.setProperty(id.toString(), encodeStored(knowledge)));
            Path pendingFile = persistence.resolveSibling(persistence.getFileName() + ".tmp");
            try (var output = Files.newOutputStream(pendingFile)) { data.store(output, "TeamLifeBind exploration magic knowledge; match knowledge is isolated"); }
            replaceKnowledge(pendingFile, persistence);
        } catch (IOException error) { TeamLifeBindForge.LOGGER.error("Cannot save magic knowledge {}", persistence, error); }
    }
    /** Windows file scanners can briefly deny replacement after a valid staged write. */
    private static void replaceKnowledge(Path staged, Path target) throws IOException {
        for (int attempt = 0; ; attempt++) {
            try {
                try { Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (java.nio.file.AtomicMoveNotSupportedException unsupported) { Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING); }
                return;
            } catch (java.nio.file.AccessDeniedException denied) {
                if (attempt >= 3) throw denied;
                try { Thread.sleep(10L << attempt); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while retrying magic knowledge replacement", interrupted);
                }
            }
        }
    }
    private String encodeStored(StoredKnowledge stored) {
        MagicState.SavedPlayer value = stored.state;
        String cooldown = value.cooldownRemaining().entrySet().stream().map(entry -> entry.getKey() + ":" + entry.getValue()).sorted().collect(java.util.stream.Collectors.joining(","));
        return value.selectedSlot() + "|" + value.mana() + "|" + value.maxMana() + "|" + stored.savedAt + "|" + String.join(",", value.learned()) + "|" + cooldown;
    }
    private StoredKnowledge decodeStored(String raw) {
        String[] parts = raw.split("\\|", -1); if (parts.length != 6) return null;
        int selected = Integer.parseInt(parts[0]), mana = Integer.parseInt(parts[1]), maximum = Integer.parseInt(parts[2]); long savedAt = Long.parseLong(parts[3]);
        List<String> learned = parts[4].isEmpty() ? List.of() : List.of(parts[4].split(",")); Map<String, Long> cooldowns = new HashMap<>();
        if (!parts[5].isEmpty()) for (String entry : parts[5].split(",")) { String[] values = entry.split(":", -1); if (values.length != 2 || cooldowns.put(values[0], Long.parseLong(values[1])) != null) return null; }
        MagicState.SavedPlayer value = new MagicState.SavedPlayer(learned, selected, mana, maximum, cooldowns);
        MagicState validator = new MagicState(); return savedAt < 0 || !validator.restorePlayer(new UUID(0, 1), value, 0) ? null : new StoredKnowledge(value.withoutManaGrowth(), savedAt);
    }
    private record DeveloperTest(MagicState.SavedPlayer original, long started, long wallTime, UUID carrier) { }
    private record Pending(MagicState.CastToken token, ServerLevel level, net.minecraft.world.phys.Vec3 position) { }
    private static final class NaturalCapture { final ResourceKey<LootTable> table; int depth = 1; NaturalCapture(ResourceKey<LootTable> table) { this.table = table; } }
    private record StoredKnowledge(MagicState.SavedPlayer state, long savedAt) { }
}
