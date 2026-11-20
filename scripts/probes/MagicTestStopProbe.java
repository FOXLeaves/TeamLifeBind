package com.teamlifebind.probe;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.teamlifebind.common.MagicState;
import com.teamlifebind.fabric.MagicSyncPayload;
import com.teamlifebind.fabric.TeamLifeBindFabric;
import com.teamlifebind.fabric.TeamMagicController;
import com.teamlifebind.fabric.TeamMagicItem;
import io.netty.channel.ChannelFutureListener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/** Real command tree, controller, menu and Fabric HUD packets in an existing isolated server. */
public final class MagicTestStopProbe implements ModInitializer {
    private static final String MARKER = "TLB_MAGIC_TESTSTOP_PROBE";
    private final List<MagicSyncPayload> hud = new ArrayList<>();
    private MinecraftServer server;
    private Object manager;
    private TeamMagicController magic;
    private ServerPlayer player;
    private boolean administrator = true;
    private int waited, assertions;

    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(value -> server = value);
        ServerTickEvents.END_SERVER_TICK.register(value -> {
            if (server != null && ++waited == 20) run();
        });
    }

    @SuppressWarnings("unchecked") private void run() {
        List<ServerPlayer> players = null;
        Map<UUID, ServerPlayer> byId = null;
        Path persistence = server.getServerDirectory().resolve("config/teamlifebind-magic.properties");
        byte[] saved = null;
        boolean existed = false, passed = false;
        long oldClock = 0;
        try {
            check(persistence.toAbsolutePath().normalize().toString().contains(".tools"), "isolated fixture path");
            existed = Files.exists(persistence);
            if (existed) saved = Files.readAllBytes(persistence);
            manager = field(TeamLifeBindFabric.class, null, "MANAGER");
            magic = (TeamMagicController) method(manager.getClass(), "magic").invoke(manager);
            oldClock = clock();
            players = (List<ServerPlayer>) field(PlayerList.class, server.getPlayerList(), "players");
            byId = (Map<UUID, ServerPlayer>) field(PlayerList.class, server.getPlayerList(), "playersByUUID");
            check(players.isEmpty(), "fixture has no connected players");
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION,
                Identifier.fromNamespaceAndPath("teamlifebind", "lobby_overworld")));
            check(level != null, "existing native lobby loaded");
            player = createPlayer(level);
            players.add(player);
            byId.put(player.getUUID(), player);
            regression();
            restartRegression(persistence);
            legitimateCapRegression();
            roundOnlyGrowthRegression(persistence);
            barehandRegression();
            passed = true;
        } catch (Throwable error) {
            System.out.println(MARKER + " FAIL " + error);
            error.printStackTrace();
        } finally {
            try {
                if (magic != null && player != null) {
                    if (magic.developerTesting(player.getUUID())) magic.stopDeveloperTest(player);
                    magic.state().remove(player.getUUID());
                    ((Map<UUID, ?>) field(magic.getClass(), magic, "exploration")).remove(player.getUUID());
                }
                if (player != null) {
                    player.closeContainer();
                    if (players != null) players.remove(player);
                    if (byId != null) byId.remove(player.getUUID());
                }
                if (manager != null) set(manager.getClass(), manager, "sharedStateTick", oldClock);
                if (existed) Files.write(persistence, saved);
                else Files.deleteIfExists(persistence);
            } catch (Throwable error) {
                passed = false;
                System.out.println(MARKER + " FAIL cleanup " + error);
                error.printStackTrace();
            }
        }
        if (passed) System.out.println(MARKER + " PASS assertions=" + assertions
            + " hudPackets=" + hud.size() + " realCommandTree=true realMenus=true originalController=true");
    }

    private void regression() throws Exception {
        UUID id = player.getUUID();
        MagicState state = magic.state();
        check(state.learn(id, "fire_bolt").accepted(), "normal profile learns one spell");
        check(state.spendMana(id, 37, clock()), "normal profile pays mana before testing");
        MagicState.SavedPlayer initial = state.exportPlayer(id, clock());
        check(initial.mana() == 63 && initial.maxMana() == 100, "initial 63/100 profile");
        start();
        check(command("tlb magic teststop") == 1, "documented stop command succeeds");
        restored(initial, "documented stop");
        afterTicks(initial);

        // Obtain the larger cap through actual companion items, rather than assuming every user starts at 100.
        for (int index = 0; index < 2; index++) {
            step();
            ItemStack apple = magic.createCompanion(TeamMagicItem.Kind.MANA_APPLE, player);
            check(magic.useItem(player, apple, TeamMagicItem.Kind.MANA_APPLE) && apple.isEmpty(),
                "real mana apple is consumed " + index);
        }
        check(state.drainMana(id, 20, clock()) == 20, "spent mana remains paid after apples");
        MagicState.SavedPlayer withApples = state.exportPlayer(id, clock());
        check(withApples.mana() == 63 && withApples.maxMana() == 120, "apple profile is 63/120");
        start();
        check(command("tlb magic teststop") == 1, "documented stop succeeds with apple cap");
        restored(withApples, "apple profile stop");
        afterTicks(withApples);

        MagicState.SavedPlayer beforeRepeat = state.exportPlayer(id, clock());
        check(command("tlb magic teststop") == 0, "repeated stop reports no active test");
        check(state.exportPlayer(id, clock()).equals(beforeRepeat), "repeated stop leaves normal profile unchanged");

        start();
        check(command("tlb magic test stop") == 1, "nested stop command remains compatible");
        restored(withApples, "nested stop");

        administrator = false;
        denied("tlb magic test ice_beam 1");
        check(!magic.developerTesting(id) && state.exportPlayer(id, clock()).equals(withApples),
            "ordinary player cannot start a boosted test");
        administrator = true;
        start();
        administrator = false;
        denied("tlb magic teststop");
        check(magic.developerTesting(id) && state.maxMana(id) == 300, "stop command keeps its administrator guard");
        administrator = true;
        check(command("tlb magic teststop") == 1, "administrator can end the guarded test");
        restored(withApples, "permission control cleanup");

        start();
        method(manager.getClass(), "openMagicDevCategories", ServerPlayer.class).invoke(manager, player);
        player.containerMenu.clicked(48, 0, ContainerInput.PICKUP, player);
        restored(withApples, "explicit menu stop button");
    }
    private void barehandRegression()throws Exception{
        UUID id=player.getUUID();var state=magic.state();
        check(magic.testSpell(player,"fire_bolt",0),"prepare first learned spell");
        check(magic.testSpell(player,"ice_channel",1),"prepare second learned spell");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);step();
        magic.acceptInput(player,new com.teamlifebind.fabric.MagicInputPayload(1,com.teamlifebind.fabric.MagicInputPayload.SELECT));
        check(state.snapshot(id,clock()).selectedSlot()==1&&latestHud().selected()==1,"bare hands select second learned spell and receive authoritative HUD confirmation");
        step();magic.acceptInput(player,new com.teamlifebind.fabric.MagicInputPayload(1,com.teamlifebind.fabric.MagicInputPayload.CAST));
        @SuppressWarnings("unchecked") var pending=(Map<UUID,?>)field(magic.getClass(),magic,"pending");
        check(pending.containsKey(id),"bare-hand cast starts through real native input handler");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(net.minecraft.world.item.Items.STICK));step();
        check(pending.containsKey(id),"changing held item no longer interrupts a valid spell");
        int castTicks=com.teamlifebind.common.SpellCatalogue.byId("ice_channel").castTicks();
        for(int i=0;i<castTicks+2;i++)step();
        check(!pending.containsKey(id)&&magic.activeEffectCount()>0,"spell completes and its channel remains active without a grimoire");
        magic.stopDeveloperTest(player);
        System.out.println("TLB_MAGIC_BAREHAND_PASS realInput=true selectedSecond=true heldItemIndependent=true channel=true");
    }

    private void start() throws Exception {
        check(command("tlb magic test ice_beam 1") == 1, "real test command starts temporary profile");
        check(magic.developerTesting(player.getUUID()) && magic.state().mana(player.getUUID(), clock()) == 300
            && magic.state().maxMana(player.getUUID()) == 300, "temporary profile is 300/300");
        MagicSyncPayload sync = latestHud();
        check(sync.mana() == 300 && sync.maximum() == 300, "HUD receives the temporary 300/300 state");
    }

    /** A fresh controller models lost process memory; the real join handler covers stale session recovery. */
    private void restartRegression(Path persistence) throws Exception {
        UUID id = player.getUUID();
        MagicState.SavedPlayer original = magic.state().exportPlayer(id, clock());
        start();
        java.util.Properties saved = new java.util.Properties();
        if (Files.exists(persistence)) try (var input = Files.newInputStream(persistence)) { saved.load(input); }
        String raw = saved.getProperty(id.toString(), "");
        String[] parts = raw.split("\\|", -1);
        MagicState.SavedPlayer exploration = new MagicState.SavedPlayer(original.learned(), original.selectedSlot(), 63, 100, original.cooldownRemaining());
        boolean checkpoint = parts.length == 6 && parts[1].equals("63") && parts[2].equals("100");
        System.out.println("MANA_RESTART_CHECKPOINT correct=" + checkpoint + " expected=63/100"
            + " stored=" + (parts.length == 6 ? parts[1] + "/" + parts[2] : "missing"));
        TeamMagicController fresh = (TeamMagicController) magic.getClass().getConstructor(manager.getClass()).newInstance(manager);
        fresh.load(server);
        boolean diskRestored = fresh.state().exportPlayer(id, clock()).equals(exploration);
        System.out.println("MANA_RESTART_FRESH_CONTROLLER mana=" + fresh.state().mana(id, clock()) + " max=" + fresh.state().maxMana(id));
        for (int slot = 30; slot <= 32; slot++) check(player.getInventory().getItem(slot).isEmpty(), "inventory fixture slot " + slot);
        ItemStack orphan = magic.createCompanion(TeamMagicItem.Kind.GRIMOIRE, player);
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            orphan, tag -> tag.putString("tlb_magic_test_carrier", UUID.randomUUID().toString()));
        ItemStack normalFocus = magic.createCompanion(TeamMagicItem.Kind.GRIMOIRE, player);
        ItemStack unrelatedBook = magic.createBook("fire_bolt", player);
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            unrelatedBook, tag -> tag.putString("tlb_magic_test_carrier", UUID.randomUUID().toString()));
        player.getInventory().setItem(30, orphan);
        player.getInventory().setItem(31, normalFocus);
        player.getInventory().setItem(32, unrelatedBook);
        method(manager.getClass(), "onPlayerJoin", ServerPlayer.class).invoke(manager, player);
        boolean joinedRestored = !magic.developerTesting(id) && magic.state().exportPlayer(id, clock()).equals(original);
        System.out.println("MANA_RESTART_JOIN testing=" + magic.developerTesting(id) + " mana=" + magic.state().mana(id, clock())
            + " max=" + magic.state().maxMana(id));
        if (magic.developerTesting(id)) magic.stopDeveloperTest(player);
        check(checkpoint, "persistent checkpoint keeps paid mana and clears round-only apple growth");
        check(diskRestored, "lost process memory reloads knowledge with the base mana cap");
        check(joinedRestored, "joining a world ends a leftover temporary test before the first HUD sync");
        check(player.getInventory().getItem(30).isEmpty(), "login removes an orphaned generated test focus");
        check(player.getInventory().getItem(31) == normalFocus, "login preserves an unmarked normal grimoire");
        check(player.getInventory().getItem(32) == unrelatedBook, "login does not delete other item types");
        player.getInventory().setItem(31, ItemStack.EMPTY);
        player.getInventory().setItem(32, ItemStack.EMPTY);
        restored(original, "restart/rejoin");

        start();
        magic.shutdown(server);
        magic.load(server);
        magic.sync(player);
        restored(exploration, "normal shutdown/load clears apple growth");

        start();
        @SuppressWarnings("unchecked") var players = (List<ServerPlayer>) field(PlayerList.class, server.getPlayerList(), "players");
        @SuppressWarnings("unchecked") var byId = (Map<UUID, ServerPlayer>) field(PlayerList.class, server.getPlayerList(), "playersByUUID");
        players.remove(player); byId.remove(id);
        try { magic.disconnect(id); }
        finally { players.add(player); byId.put(id, player); }
        magic.sync(player);
        restored(exploration, "exploration disconnect after player index removal");
    }

    private void legitimateCapRegression() throws Exception {
        for (int index = 0; index < 20; index++) {
            step();
            ItemStack apple = magic.createCompanion(TeamMagicItem.Kind.MANA_APPLE, player);
            check(magic.useItem(player, apple, TeamMagicItem.Kind.MANA_APPLE) && apple.isEmpty(),
                "legitimate cap growth consumes real apple " + index);
        }
        MagicState.SavedPlayer original = magic.state().exportPlayer(player.getUUID(), clock());
        check(original.maxMana() == 300 && original.mana() < 300, "legitimate 300 cap is distinguishable from a boosted test by session state");
        start();
        method(manager.getClass(), "onPlayerJoin", ServerPlayer.class).invoke(manager, player);
        restored(original, "legitimate 300 cap after login");
    }

    private void roundOnlyGrowthRegression(Path persistence) throws Exception {
        UUID id = player.getUUID();
        magic.beginMatch(server);
        check(magic.state().maxMana(id) == 100 && magic.state().mana(id, clock()) == 100,
            "new match starts with base mana");
        check(magic.state().learn(id, "fire_bolt").accepted(), "learn a spell inside the match");
        for (int index = 0; index < 2; index++) {
            step();
            ItemStack apple = magic.createCompanion(TeamMagicItem.Kind.MANA_APPLE, player);
            check(magic.useItem(player, apple, TeamMagicItem.Kind.MANA_APPLE) && apple.isEmpty(), "match apple is consumed");
        }
        MagicState.SavedPlayer duringMatch = magic.state().exportPlayer(id, clock());
        check(duringMatch.maxMana() == 120, "apple growth works during its match");
        start();
        check(command("tlb magic teststop") == 1, "end developer test during a match");
        restored(duringMatch, "developer test retains the current match's growth");
        magic.disconnect(id);
        method(manager.getClass(), "onPlayerJoin", ServerPlayer.class).invoke(manager, player);
        restored(duringMatch, "same match reconnect retains growth");
        magic.endMatch(server);
        check(magic.state().maxMana(id) == 100 && magic.state().mana(id, clock()) <= 100,
            "ending a match clears all apple growth");
        check(magic.state().learned(id).equals(List.of("fire_bolt")), "exploration learning survives the round boundary");
        magic.beginMatch(server);
        check(magic.state().maxMana(id) == 100, "next match cannot inherit a previous apple cap");
        magic.endMatch(server);

        java.util.Properties legacy = new java.util.Properties();
        try (var input = Files.newInputStream(persistence)) { legacy.load(input); }
        legacy.setProperty(id.toString(), "0|243|300|" + System.currentTimeMillis() + "|fire_bolt|fire_bolt:20");
        try (var output = Files.newOutputStream(persistence)) { legacy.store(output, "legacy grown exploration fixture"); }
        TeamMagicController migrated = (TeamMagicController) magic.getClass().getConstructor(manager.getClass()).newInstance(manager);
        migrated.load(server);
        MagicState.SavedPlayer migratedState = migrated.state().exportPlayer(id, clock());
        check(migratedState.maxMana() == 100 && migratedState.mana() == 100, "legacy 243/300 exploration data resets to 100/100");
        check(migratedState.learned().equals(List.of("fire_bolt")) && migratedState.selectedSlot() == 0
            && migratedState.cooldownRemaining().getOrDefault("fire_bolt", 0L) > 0,
            "legacy cap migration retains learning, selection and remaining cooldown");
        System.out.println("MANA_ROUND_ONLY_GROWTH_PASS reconnectCap=120 endedCap=100 nextCap=100 migratedCap=100");
    }

    private void restored(MagicState.SavedPlayer expected, String label) throws Exception {
        check(!magic.developerTesting(player.getUUID()), label + " ends temporary session");
        check(magic.state().exportPlayer(player.getUUID(), clock()).equals(expected), label + " restores exact original profile");
        MagicSyncPayload sync = latestHud();
        check(sync.mana() == expected.mana() && sync.maximum() == expected.maxMana()
            && sync.learned().equals(expected.learned()) && sync.selected() == expected.selectedSlot(),
            label + " sends original mana, cap and knowledge to HUD");
    }

    private void afterTicks(MagicState.SavedPlayer expected) throws Exception {
        int before = hud.size();
        for (int index = 0; index < 35; index++) step();
        check(!magic.developerTesting(player.getUUID()) && magic.state().maxMana(player.getUUID()) == expected.maxMana()
            && magic.state().mana(player.getUUID(), clock()) == expected.mana(), "later controller ticks retain original mana and cap");
        check(hud.size() > before && hud.subList(before, hud.size()).stream()
            .allMatch(sync -> sync.maximum() == expected.maxMana() && sync.mana() <= expected.maxMana()),
            "subsequent HUD heartbeats cannot reapply the temporary 300 cap");
    }

    private int command(String text) throws CommandSyntaxException {
        return server.getCommands().getDispatcher().execute(text, player.createCommandSourceStack());
    }

    private void denied(String text) throws Exception {
        boolean rejected = false;
        try { command(text); }
        catch (CommandSyntaxException denied) { rejected = true; }
        check(rejected, "command requires administrator permission: " + text);
    }

    @SuppressWarnings("unchecked") private ServerPlayer createPlayer(ServerLevel level) throws Exception {
        ServerPlayer value = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "MagicStopProbe"),
            ClientInformation.createDefault()) {
            @Override public PermissionSet permissions() {
                return administrator ? PermissionSet.ALL_PERMISSIONS : PermissionSet.NO_PERMISSIONS;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND) {
            @Override public void send(Packet<?> packet) { observe(packet); }
            @Override public void send(Packet<?> packet, ChannelFutureListener listener) { observe(packet); }
            @Override public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) { observe(packet); }
        };
        value.connection = new ServerGamePacketListenerImpl(server, connection, value,
            CommonListenerCookie.createInitial(value.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) { observe(packet); }
        };
        Object addon = net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl.getAddon(value.connection);
        Set<Identifier> channels = (Set<Identifier>) field(
            net.fabricmc.fabric.impl.networking.AbstractChanneledNetworkAddon.class, addon, "sendableChannels");
        channels.add(MagicSyncPayload.TYPE.id());
        value.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        value.setPos(64.5, level.getMaxY() - 20, 64.5);
        value.setHealth(20);
        return value;
    }

    private void observe(Packet<?> packet) {
        if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof MagicSyncPayload sync)
            hud.add(sync);
    }

    private MagicSyncPayload latestHud() {
        check(!hud.isEmpty(), "real Fabric magic HUD channel receives a payload");
        return hud.getLast();
    }

    private void step() throws Exception {
        set(manager.getClass(), manager, "sharedStateTick", clock() + 1);
        magic.tick(server);
    }

    private long clock() throws Exception { return (Long) field(manager.getClass(), manager, "sharedStateTick"); }
    private void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
    private static Object field(Class<?> type, Object target, String name) throws Exception {
        Field value = type.getDeclaredField(name); value.setAccessible(true); return value.get(target);
    }
    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static Method method(Class<?> type, String name, Class<?>... signature) throws Exception {
        Method value = type.getDeclaredMethod(name, signature); value.setAccessible(true); return value;
    }
}
