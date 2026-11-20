package com.teamlifebind.neoforge;

import com.teamlifebind.common.NativeHud;
import com.teamlifebind.common.NativeUi;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A presentation of server-issued slots, never a second source of menu permissions or rewards. */
public final class NativeMenuScreen extends AbstractContainerScreen<ChestMenu> {
    private final String kind;
    private int selected = -1, scroll;
    private double lastMouseX = Double.NaN, lastMouseY = Double.NaN;
    private int pressed = -1;
    private boolean keyboardFocus, expandedDetails;
    private ItemStack inspected = ItemStack.EMPTY;
    private int inspectedWidth;
    private List<FormattedCharSequence> detailLines = List.of();

    public NativeMenuScreen(ChestMenu menu, Inventory inventory, Component title, String kind) {
        super(menu, inventory, title); this.kind = kind;
    }
    public static boolean open(MenuType<?> type, Minecraft client, int id, Component title) {
        String kind = NativeMenuTitle.kind(title);
        if (kind == null || client.player == null || type != MenuType.GENERIC_9x3 && type != MenuType.GENERIC_9x5 && type != MenuType.GENERIC_9x6) return false;
        ChestMenu menu = createMenu(type, id, client.player.getInventory());
        client.player.containerMenu = menu;
        client.gui.setScreen(Boolean.getBoolean("teamlifebind.ui.native")
            ? new NativeMenuScreen(menu, client.player.getInventory(), title, kind)
            : new com.teamlifebind.clientui.AuiMenuScreen(menu, client.player.getInventory(), title, kind));
        return true;
    }
    @SuppressWarnings("unchecked")
    public static ChestMenu createMenu(MenuType<?> type, int id, Inventory inventory) {
        int rows = type == MenuType.GENERIC_9x3 ? 3 : type == MenuType.GENERIC_9x5 ? 5 : type == MenuType.GENERIC_9x6 ? 6 : 0;
        if (rows == 0) throw new IllegalArgumentException("Unsupported TLB menu type");
        return new ReadOnlyMenu((MenuType<ChestMenu>) type, id, inventory, rows);
    }
    /** Buttons have no predicted inventory transaction; the server still receives every input. */
    private static final class ReadOnlyMenu extends ChestMenu {
        ReadOnlyMenu(MenuType<ChestMenu> type, int id, Inventory inventory, int rows) { super(type, id, inventory, new SimpleContainer(rows * 9), rows); }
        @Override public void clicked(int slot, int button, ContainerInput input, Player player) { }
        @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    }
    private NativeUi.MenuLayout layout() {
        List<Integer> slots = new ArrayList<>();
        if (kind.equals("routes")) for (int slot : new int[] {10, 13, 16}) if (visible(menu.getSlot(slot).getItem())) slots.add(slot);
        for (int slot = 0; slot < count(); slot++) if (visible(menu.getSlot(slot).getItem()) && !slots.contains(slot)) slots.add(slot);
        if (kind.equals("lobby")) {
            int[] priorities = new int[count()];
            for (int slot : slots) priorities[slot] = lobbyPriority(action(menu.getSlot(slot).getItem()));
            slots.sort(java.util.Comparator.comparingInt(slot -> priorities[slot]));
        }
        if (!slots.contains(selected)) select(slots.isEmpty() ? -1 : slots.getFirst());
        return NativeUi.menuLayout(width, height, slots, !controls());
    }
    private boolean controls() { return kind.equals("lobby") || kind.equals("campaign"); }
    private static String action(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getStringOr("tlb_menu_action", "");
    }
    /** The legacy lobby border is an actual glass item with a blank custom name. */
    private static boolean visible(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!stack.is(Items.STAINED_GLASS_PANE.gray())) return true;
        var name = stack.get(DataComponents.CUSTOM_NAME);
        var lore = stack.get(DataComponents.LORE);
        return name == null || !name.getString().isBlank() || !action(stack).isEmpty()
            || lore != null && lore.lines().stream().anyMatch(line -> !line.getString().isBlank());
    }
    private static int lobbyPriority(String action) {
        return switch (action) {
            case "ready", "unready" -> 0; case "start", "stop" -> 1; case "campaign" -> 2; case "status" -> 3;
            case "rules" -> 4; case "help" -> 5; case "teams" -> 6; case "health", "special_health" -> 7;
            case "healthsync", "special_health_sync" -> 8; case "norespawn", "special_norespawn" -> 9;
            case "match_pace" -> 10; case "totem_balance" -> 11; case "supply_drops" -> 12; case "supply_dynamic" -> 13;
            case "auto_portals", "special_auto_portals" -> 14; case "stronghold_hints" -> 15; case "end_totems" -> 16;
            case "announce_teams" -> 17; case "scoreboard" -> 18; case "tab" -> 19; case "advancements" -> 20;
            case "language" -> 21; case "" -> 22; case "reload" -> 23; default -> 24;
        };
    }
    private int count() { return menu.getRowCount() * 9; }
    private int accent() { return switch (kind) { case "magic" -> 0xFFC0AEF1; case "routes", "expedition", "dev" -> NativeHud.GOLD; default -> NativeHud.CYAN; }; }
    private static String text(String key) { return Component.translatable("ui.teamlifebind." + key).getString(); }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        var layout = layout();
        var canvas = new NativeHudCanvas(graphics, font);
        graphics.fillGradient(0, 0, width, height, 0xA00B1119, 0xD0050A11);
        NativeUi.menuFrame(canvas, layout, title.getString(), text("category." + kind), text(layout.compact() ? "controls.compact" : "controls"), accent());
        int hover = layout.hit(mouseX, mouseY);
        if (mouseX != lastMouseX || mouseY != lastMouseY) {
            keyboardFocus = false;
            if (!expandedDetails && hover >= 0 && !menu.getSlot(hover).getItem().isEmpty()) select(hover);
            lastMouseX = mouseX; lastMouseY = mouseY;
        }
        if (!expandedDetails) for (int slot : layout.slots()) {
            var tile = layout.tile(slot); ItemStack stack = menu.getSlot(slot).getItem();
            NativeUi.menuTile(canvas, tile, !stack.isEmpty(), slot == selected, accent());
            if (stack.isEmpty()) continue;
            boolean named = tile.width() >= 66;
            int x = named ? tile.x() + 6 : tile.x() + (tile.width() - 16) / 2, y = tile.y() + (tile.height() - 16) / 2;
            if (pressed == slot) y++;
            graphics.item(stack, x, y); graphics.itemDecorations(font, stack, x, y);
            if (named) {
                String name = stack.getHoverName().getString();
                if (controls() && name.startsWith("[") && name.endsWith("]")) name = name.substring(1, name.length() - 1);
                String label = NativeHud.fit(canvas, name, tile.width() - 33);
                boolean twoLines = tile.height() >= (controls() ? 28 : 40);
                canvas.text(label, tile.x() + 28, tile.y() + (twoLines ? controls() ? 4 : 10 : (tile.height() - 9) / 2), slot == selected ? NativeHud.TEXT : NativeHud.MUTED);
                var lore = stack.get(DataComponents.LORE);
                if (twoLines && lore != null && !lore.lines().isEmpty())
                    canvas.text(NativeHud.fit(canvas, lore.lines().getFirst().getString(), tile.width() - 33), tile.x() + 28, tile.y() + (controls() ? 17 : 25), NativeHud.QUIET);
            }
        }
        if (expandedDetails) {
            var box = layout.panel();
            var detail = new NativeHud.Rect(box.x() + 12, box.y() + layout.header(), box.width() - 24, box.height() - layout.header() - (layout.compact() ? 19 : 29));
            NativeUi.rounded(canvas, detail.x(), detail.y(), detail.width(), detail.height(), 4, 0xFF151E28);
            details(graphics, detail);
        } else if (layout.details().width() > 0) details(graphics, layout.details());
        else if (selected >= 0 && (hover == selected || keyboardFocus)) {
            ItemStack stack = menu.getSlot(selected).getItem();
            var tile = layout.tile(selected);
            graphics.setTooltipForNextFrame(font, stack, hover == selected ? mouseX : tile.x() + tile.width(), hover == selected ? mouseY : tile.y());
        }
    }
    private void details(GuiGraphicsExtractor graphics, NativeHud.Rect box) {
        int x = box.x() + 10, y = box.y() + 10, availableWidth = box.width() - 20;
        if (selected < 0) { graphics.text(font, text("empty"), x, y, NativeHud.MUTED, false); return; }
        ItemStack stack = menu.getSlot(selected).getItem();
        if (inspectedWidth != availableWidth || !ItemStack.matches(inspected, stack)) {
            inspected = stack.copy(); inspectedWidth = availableWidth; scroll = 0;
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.addAll(font.split(stack.getHoverName().copy().withStyle(style -> style.withColor(NativeHud.TEXT & 0xFFFFFF)), availableWidth));
            lines.add(FormattedCharSequence.EMPTY);
            var lore = stack.get(DataComponents.LORE);
            if (lore != null) for (Component line : lore.lines()) lines.addAll(font.split(line, availableWidth));
            detailLines = List.copyOf(lines);
        }
        int visible = Math.max(1, (box.height() - 33) / 11);
        scroll = Math.clamp(scroll, 0, Math.max(0, detailLines.size() - visible));
        graphics.enableScissor(box.x(), box.y(), box.x() + box.width(), box.y() + box.height());
        try {
            for (int index = 0; index < visible && index + scroll < detailLines.size(); index++)
                graphics.text(font, detailLines.get(index + scroll), x, y + index * 11, NativeHud.MUTED, false);
            if (detailLines.size() > visible) {
                int track = box.height() - 26, thumb = Math.max(10, track * visible / detailLines.size());
                graphics.fill(box.x() + box.width() - 4, y, box.x() + box.width() - 3, y + track, 0xFF293744);
                int top = y + (track - thumb) * scroll / Math.max(1, detailLines.size() - visible);
                graphics.fill(box.x() + box.width() - 4, top, box.x() + box.width() - 3, top + thumb, accent());
                graphics.text(font, NativeHud.fit(new NativeHudCanvas(graphics, font), text("scroll"), availableWidth), x, box.y() + box.height() - 15, NativeHud.QUIET, false);
            }
        } finally { graphics.disableScissor(); }
    }
    private void select(int slot) { if (selected != slot) { selected = slot; scroll = 0; } }
    private void activate(int slot, int button) {
        if (slot >= 0 && slot < count() && visible(menu.getSlot(slot).getItem()))
            slotClicked(menu.getSlot(slot), slot, button, ContainerInput.PICKUP);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (layout().close().contains(event.x(), event.y()) && event.button() == InputConstants.MOUSE_BUTTON_LEFT) { onClose(); return true; }
        if (layout().inspect().contains(event.x(), event.y()) && event.button() == InputConstants.MOUSE_BUTTON_LEFT) { expandedDetails = !expandedDetails; scroll = 0; return true; }
        if (expandedDetails) return true;
        int slot = layout().hit(event.x(), event.y());
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            select(slot); pressed = slot;
            activate(slot, event.button() == InputConstants.MOUSE_BUTTON_LEFT ? 0 : 1);
        }
        return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) { pressed = -1; return true; }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) { return true; }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (expandedDetails || layout().details().width() > 0) scroll += dy > 0 ? -3 : dy < 0 ? 3 : 0;
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) { if (expandedDetails) expandedDetails = false; else onClose(); return true; }
        if (minecraft.options.keyInventory.matches(event)) { onClose(); return true; }
        if (event.key() == InputConstants.KEY_D) { expandedDetails = !expandedDetails; scroll = 0; return true; }
        if (event.isConfirmation()) { activate(selected, 0); return true; }
        if (event.key() == InputConstants.KEY_PAGEDOWN || event.key() == InputConstants.KEY_PAGEUP) { scroll += event.key() == InputConstants.KEY_PAGEDOWN ? 8 : -8; return true; }
        var layout = layout();
        int step = switch (event.key()) { case InputConstants.KEY_LEFT -> -1; case InputConstants.KEY_RIGHT -> 1; case InputConstants.KEY_TAB -> event.hasShiftDown() ? -1 : 1; case InputConstants.KEY_UP -> -layout.columns(); case InputConstants.KEY_DOWN -> layout.columns(); default -> 0; };
        if (step != 0) {
            keyboardFocus = true; expandedDetails = false;
            var slots = layout.slots();
            if (!slots.isEmpty()) {
                int start = slots.indexOf(selected);
                select(slots.get(start < 0 ? 0 : Math.floorMod(start + step, slots.size())));
            }
            return true;
        }
        return false;
    }
}
