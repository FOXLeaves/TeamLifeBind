import com.mojang.blaze3d.platform.InputConstants;
import com.teamlifebind.common.NativeUi;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import sun.misc.Unsafe;

/** Real MC menus, SDL input callbacks and the actual production screen. No rendered client is claimed. */
public final class NativeUiProbe {
    private static Unsafe unsafe;
    private static int assertions;
    private static final List<String> clicks = new ArrayList<>();
    private static void check(boolean ok, String why) { assertions++; if (!ok) throw new AssertionError(why); }
    private static void put(Object object, Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        unsafe.putObject(object, unsafe.objectFieldOffset(field), value);
    }
    private static Object read(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static MouseButtonEvent mouse(double x, double y, int button) { return new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0)); }
    private static KeyEvent key(int key) { return new KeyEvent(key, 0, 0); }
    private static final class InputSink extends MultiPlayerGameMode {
        InputSink(Minecraft client) { super(client, null); }
        @Override public void handleContainerInput(int id, int slot, int button, ContainerInput input, Player player) {
            clicks.add(id + ":" + slot + ":" + button + ":" + input.name());
            player.containerMenu.clicked(slot, button, input, player);
        }
    }
    public static void main(String[] args) throws Exception {
        String platform = args[0];
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true); unsafe = (Unsafe) field.get(null);
        Minecraft client = (Minecraft) unsafe.allocateInstance(Minecraft.class);
        LocalPlayer player = (LocalPlayer) unsafe.allocateInstance(LocalPlayer.class);
        Inventory inventory = new Inventory(player, new EntityEquipment());
        put(player, Player.class, "inventory", inventory); client.player = player;
        Options options = (Options) unsafe.allocateInstance(Options.class);
        put(options, Options.class, "keyInventory", new KeyMapping("probe.inventory", InputConstants.Type.KEYBOARD, InputConstants.KEY_E, KeyMapping.Category.GAMEPLAY));
        put(client, Minecraft.class, "options", options); client.gameMode = new InputSink(client);
        Field singleton = Minecraft.class.getDeclaredField("instance"); singleton.setAccessible(true); singleton.set(null, client);
        Items.BOOK.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder().addAll(DataComponents.COMMON_ITEM_COMPONENTS)
            .set(DataComponents.ITEM_NAME, Component.literal("Probe book")).build());
        Class<?> titles = Class.forName("com.teamlifebind." + platform + ".NativeMenuTitle");
        Method kind = titles.getMethod("kind", Component.class), of = titles.getMethod("of", String.class, String.class);
        for (String value : List.of("lobby", "campaign", "dev", "magic", "routes", "expedition")) {
            Component title = (Component) of.invoke(null, value, "标题 / title");
            check(value.equals(kind.invoke(null, title)) && title.getString().equals("标题 / title"), "marker preserves visible title and kind");
        }
        check(kind.invoke(null, Component.literal("TeamLifeBind")) == null, "plain text cannot opt an ordinary container into custom UI");
        check(kind.invoke(null, Component.literal("Title").withStyle(style -> style.withInsertion("teamlifebind:ui/unknown"))) == null, "unknown markers are ignored");
        Class<?> type = Class.forName("com.teamlifebind." + platform + ".NativeMenuScreen");
        Method create = type.getMethod("createMenu", MenuType.class, int.class, Inventory.class);
        Method open = type.getMethod("open", MenuType.class, Minecraft.class, int.class, Component.class);
        check(!(Boolean) open.invoke(null, MenuType.GENERIC_9x3, client, 1, Component.literal("A normal chest")), "ordinary chest retains its vanilla screen");
        check(!(Boolean) open.invoke(null, MenuType.HOPPER, client, 1, of.invoke(null, "dev", "Dev")), "unrelated menu type retains its own screen");
        // The real lobby fills its border with named glass panes, not ItemStack.EMPTY.
        // Reproduce the exact createMenuFrameItem payload in the production client screen.
        Items.STAINED_GLASS_PANE.gray().builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
            .addAll(DataComponents.COMMON_ITEM_COMPONENTS).set(DataComponents.ITEM_NAME, Component.literal("Glass pane")).build());
        ItemStack frame = new ItemStack(Items.STAINED_GLASS_PANE.gray());
        frame.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        ChestMenu framedMenu = (ChestMenu) create.invoke(null, MenuType.GENERIC_9x6, 72, inventory);
        framedMenu.getSlot(0).set(frame); framedMenu.getSlot(11).set(new ItemStack(Items.BOOK));
        Screen framed = (Screen) type.getConstructor(ChestMenu.class, Inventory.class, Component.class, String.class)
            .newInstance(framedMenu, inventory, Component.literal("Lobby"), "lobby");
        framed.width = 640; framed.height = 378;
        Method layoutMethod = type.getDeclaredMethod("layout"); layoutMethod.setAccessible(true);
        NativeUi.MenuLayout framedLayout = (NativeUi.MenuLayout) layoutMethod.invoke(framed);
        check(framedLayout.slots().equals(List.of(11)), "real lobby decoration must not produce blank cards: visible=" + framedLayout.slots());
        check((int) read(framed, "selected") == 11, "initial selection is meaningful before the first render");
        check(framedLayout.details().width() == 0, "lobby never reserves a permanently empty sidebar");
        ItemStack namedGlass = frame.copy(); namedGlass.set(DataComponents.CUSTOM_NAME, Component.literal("A glass button"));
        framedMenu.getSlot(1).set(namedGlass);
        ItemStack describedGlass = frame.copy(); describedGlass.set(DataComponents.LORE,
            new net.minecraft.world.item.component.ItemLore(List.of(Component.literal("Meaningful information"))));
        framedMenu.getSlot(2).set(describedGlass);
        framedLayout = (NativeUi.MenuLayout) layoutMethod.invoke(framed);
        check(framedLayout.slots().containsAll(List.of(1, 2, 11)) && !framedLayout.slots().contains(0), "glass with a label or description stays visible");
        // Replay the actual 54-slot server menu captured by PlayModesProbe.
        var captured = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(args[1]))).getAsJsonObject();
        ChestMenu capturedMenu = (ChestMenu) create.invoke(null, MenuType.GENERIC_9x6, 73, inventory);
        Map<Integer, com.google.gson.JsonObject> capturedEntries = new HashMap<>();
        for (var value : captured.getAsJsonArray("entries")) {
            var entry = value.getAsJsonObject(); int slot = entry.get("slot").getAsInt();
            Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(entry.get("item").getAsString()));
            item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder().addAll(DataComponents.COMMON_ITEM_COMPONENTS)
                .set(DataComponents.ITEM_NAME, Component.literal(entry.get("name").getAsString())).build());
            ItemStack stack = new ItemStack(item); stack.set(DataComponents.CUSTOM_NAME, Component.literal(entry.get("name").getAsString()));
            var lines = new ArrayList<Component>(); for (var line : entry.getAsJsonArray("lore")) lines.add(Component.literal(line.getAsString()));
            stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lines));
            var tag = new net.minecraft.nbt.CompoundTag(); tag.putString("tlb_menu_action", entry.get("action").getAsString());
            stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
            capturedMenu.getSlot(slot).set(stack); capturedEntries.put(slot, entry);
        }
        Screen actual = (Screen) type.getConstructor(ChestMenu.class, Inventory.class, Component.class, String.class)
            .newInstance(capturedMenu, inventory, Component.literal(captured.get("title").getAsString()), "lobby");
        put(actual, Screen.class, "minecraft", client); player.containerMenu = capturedMenu;
        actual.width = 640; actual.height = 378;
        NativeUi.MenuLayout actualLayout = (NativeUi.MenuLayout) layoutMethod.invoke(actual);
        List<Integer> visible = capturedEntries.entrySet().stream().filter(entry -> !entry.getValue().get("name").getAsString().isBlank()).map(Map.Entry::getKey).sorted().toList();
        check(actualLayout.slots().stream().sorted().toList().equals(visible), "all real lobby controls remain and every decorative pane disappears");
        check(actualLayout.slots().stream().limit(4).map(slot -> capturedEntries.get(slot).get("action").getAsString()).toList()
            .equals(List.of("ready", "start", "campaign", "status")), "primary lobby actions appear together first");
        check(actualLayout.details().width() == 0 && actualLayout.panel().height() <= 270, "screenshot-sized lobby stays compact without the unused details column");
        var clientEntries = new com.google.gson.JsonArray();
        for (int slot : actualLayout.slots()) clientEntries.add(capturedEntries.get(slot));
        var preview = captured.deepCopy(); preview.add("entries", clientEntries);
        java.nio.file.Files.writeString(java.nio.file.Path.of("lobby-visible.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(preview));
        for (int[] size : new int[][] {{320, 180}, {400, 240}, {640, 378}, {1280, 720}}) {
            actual.width = size[0]; actual.height = size[1]; actualLayout = (NativeUi.MenuLayout) layoutMethod.invoke(actual);
            var box = actualLayout.panel();
            check(box.x() >= 0 && box.y() >= 0 && box.x() + box.width() <= size[0] && box.y() + box.height() <= size[1], "actual lobby fits each GUI size");
            for (int slot : actualLayout.slots()) {
                var tile = actualLayout.tile(slot); clicks.clear();
                actual.mouseClicked(mouse(tile.x() + 3, tile.y() + 3, InputConstants.MOUSE_BUTTON_LEFT), false);
                check(clicks.equals(List.of("73:" + slot + ":0:PICKUP")), "reordered real lobby button retains its original server slot");
            }
        }
        System.out.println("NATIVE_LOBBY_LAYOUT_PASS platform=" + platform + " original=" + capturedEntries.size() + " visible=" + visible.size()
            + " blankCards=0 sidebar=false realMenuCapture=true");
        for (MenuType<?> menuType : List.of(MenuType.GENERIC_9x3, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6)) {
            for (int[] size : new int[][] {{320, 180}, {400, 240}, {640, 360}, {1280, 720}}) {
                ChestMenu menu = (ChestMenu) create.invoke(null, menuType, 72, inventory); player.containerMenu = menu;
                int count = menu.getRowCount() * 9;
                List<Integer> shown = List.of(0, 10, count - 1);
                for (int slot : shown) menu.getSlot(slot).set(new ItemStack(Items.BOOK));
                Screen screen = (Screen) type.getConstructor(ChestMenu.class, Inventory.class, Component.class, String.class)
                    .newInstance(menu, inventory, Component.literal("UI probe"), "magic");
                put(screen, Screen.class, "minecraft", client); screen.width = size[0]; screen.height = size[1];
                var layout = NativeUi.menuLayout(size[0], size[1], shown);
                for (int slot : shown) {
                    var tile = layout.tile(slot); double x = tile.x() + tile.width() / 2D, y = tile.y() + tile.height() / 2D;
                    clicks.clear(); screen.mouseClicked(mouse(x, y, InputConstants.MOUSE_BUTTON_LEFT), false); screen.mouseReleased(mouse(x, y, InputConstants.MOUSE_BUTTON_LEFT));
                    check(clicks.equals(List.of("72:" + slot + ":0:PICKUP")), "SDL left click addresses original server slot once");
                    check(!menu.getSlot(slot).getItem().isEmpty() && menu.getCarried().isEmpty() && inventory.isEmpty(), "button prediction never removes icons or fills inventory/cursor");
                    clicks.clear(); screen.mouseClicked(mouse(x, y, InputConstants.MOUSE_BUTTON_RIGHT), false);
                    check(clicks.equals(List.of("72:" + slot + ":1:PICKUP")), "SDL right click retains server alternate action");
                }
                clicks.clear(); screen.mouseClicked(mouse(1, 1, InputConstants.MOUSE_BUTTON_LEFT), false);
                screen.mouseDragged(mouse(1, 1, InputConstants.MOUSE_BUTTON_LEFT), 20, 20);
                check(clicks.isEmpty(), "outside clicks and dragging cannot drop or move icons");
                var first = layout.tile(0); screen.mouseClicked(mouse(first.x() + 4, first.y() + 4, InputConstants.MOUSE_BUTTON_LEFT), false);
                clicks.clear(); screen.keyPressed(key(InputConstants.KEY_RIGHT)); screen.keyPressed(key(InputConstants.KEY_RETURN));
                check(clicks.equals(List.of("72:10:0:PICKUP")), "keyboard selection follows visible cards, not removed empty slots");
                screen.keyPressed(key(InputConstants.KEY_D)); check(Boolean.TRUE.equals(read(screen, "expandedDetails")), "D expands full-width details");
                clicks.clear(); screen.mouseClicked(mouse(first.x() + 4, first.y() + 4, InputConstants.MOUSE_BUTTON_LEFT), false);
                check(clicks.isEmpty(), "details overlay blocks clicks to hidden controls");
                screen.mouseScrolled(0, 0, 0, -1); check((int) read(screen, "scroll") == 3, "expanded details support wheel scrolling");
                screen.keyPressed(key(InputConstants.KEY_ESCAPE)); check(Boolean.FALSE.equals(read(screen, "expandedDetails")), "Escape returns from details before closing the menu");
                clicks.clear(); screen.mouseClicked(mouse(first.x() + 4, first.y() + 4, InputConstants.MOUSE_BUTTON_MIDDLE), false);
                check(clicks.isEmpty(), "middle click cannot clone menu icons");
            }
        }
        verifyAuiBridge(client, player, inventory, create, frame);
        System.out.println("AUI_SERVER_MENU_BRIDGE_PASS platform=" + platform + " originalSlots=true staleButtonsRejected=true prediction=false");
        System.out.println("NATIVE_UI_INPUT_PASS platform=" + platform + " assertions=" + assertions + " actualMcMenus=true actualSdlInputs=true readOnlyPrediction=true clientRendered=false");
    }

    private static void verifyAuiBridge(Minecraft client, LocalPlayer player, Inventory inventory, Method create, ItemStack frame) throws Exception {
        var gui = (net.minecraft.client.gui.Gui) unsafe.allocateInstance(net.minecraft.client.gui.Gui.class);
        put(client, Minecraft.class, "gui", gui);
        Class<?> page = Class.forName("com.teamlifebind.clientui.AuiMenuPage");
        Class<?> entryType = Class.forName("com.teamlifebind.clientui.AuiMenuPage$Entry");
        var entry = entryType.getConstructor(int.class, ItemStack.class);
        Method visible = page.getMethod("visible", ItemStack.class);
        check(!(Boolean) visible.invoke(null, frame), "AUI filters the real blank glass frame");
        check(!(Boolean) visible.invoke(null, ItemStack.EMPTY), "AUI filters empty slots");
        Class<?> screenType = Class.forName("com.teamlifebind.clientui.AuiMenuScreen");
        Method activate = screenType.getDeclaredMethod("activate", entryType, int.class); activate.setAccessible(true);
        for (MenuType<?> menuType : List.of(MenuType.GENERIC_9x3, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6)) {
            ChestMenu menu = (ChestMenu) create.invoke(null, menuType, 94, inventory);
            player.containerMenu = menu;
            Screen screen = (Screen) screenType.getConstructor(ChestMenu.class, Inventory.class, Component.class, String.class)
                    .newInstance(menu, inventory, Component.literal("AUI bridge"), "dev");
            put(screen, Screen.class, "minecraft", client); put(gui, net.minecraft.client.gui.Gui.class, "screen", screen);
            int slot = menu.getRowCount() * 9 - 1;
            ItemStack stack = new ItemStack(Items.BOOK); menu.getSlot(slot).set(stack);
            Object snapshot = entry.newInstance(slot, stack.copy());
            for (int button : new int[] {0, 1}) {
                clicks.clear(); activate.invoke(screen, snapshot, button);
                check(clicks.equals(List.of("94:" + slot + ":" + button + ":PICKUP")), "AUI preserves original server slot and mouse action");
                check(menu.getCarried().isEmpty() && inventory.isEmpty() && ItemStack.matches(menu.getSlot(slot).getItem(), stack), "AUI performs no predicted reward or inventory mutation");
            }
            clicks.clear();
            activate.invoke(screen, entry.newInstance(-1, stack), 0);
            activate.invoke(screen, entry.newInstance(menu.getRowCount() * 9, stack), 0);
            activate.invoke(screen, snapshot, 2);
            check(clicks.isEmpty(), "AUI rejects invalid slots and middle-click cloning");
            var changed = stack.copy(); changed.set(DataComponents.CUSTOM_NAME, Component.literal("New server action")); menu.getSlot(slot).set(changed);
            activate.invoke(screen, snapshot, 0); check(clicks.isEmpty(), "AUI rejects the pre-update button snapshot");
            menu.getSlot(slot).set(ItemStack.EMPTY); activate.invoke(screen, snapshot, 0); check(clicks.isEmpty(), "AUI rejects a removed action");
            menu.getSlot(slot).set(stack); player.containerMenu = (ChestMenu) create.invoke(null, menuType, 95, inventory);
            activate.invoke(screen, snapshot, 0); check(clicks.isEmpty(), "AUI rejects clicks after the server switches containers");
            player.containerMenu = menu; put(gui, net.minecraft.client.gui.Gui.class, "screen", null);
            activate.invoke(screen, snapshot, 0); check(clicks.isEmpty(), "AUI rejects a closed screen");
        }
    }
}
