package com.teamlifebind.clientui;

import com.mojang.blaze3d.platform.InputConstants;
import com.teamlifebind.aui.client.gui.ApricityGuiLayers;
import com.teamlifebind.aui.element.Item;
import com.teamlifebind.aui.event.MouseEvent;
import com.teamlifebind.aui.init.Document;
import com.teamlifebind.aui.init.Element;
import com.teamlifebind.aui.layout.Position;
import com.teamlifebind.aui.render.Operation;
import com.teamlifebind.aui.render.ScreenDocumentCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/** AUI document and interaction state, shared by all loaders and the runtime fixture. */
public final class AuiMenuPage implements AutoCloseable {
    private static AuiMenuPage active;
    public record Entry(int slot, ItemStack stack) {}
    private final Document document;
    private final String kind;
    private final Component title;
    private final BiConsumer<Entry, Integer> activate;
    private final Runnable dismiss;
    private final AuiInkTransition ink;
    private final Map<Integer, Element> cards = new LinkedHashMap<>();
    private List<Entry> entries = List.of(), filtered = List.of(), navigation = List.of();
    private static final List<String> GROUPS = List.of("all", "match", "rules", "display");
    private int selected = -1;
    private long generation = -1;
    private boolean inspecting;
    private boolean keyboardSelection;
    private boolean navigationOpen, navigationOpenedOnHover, closing;
    private int navigationFocus;
    private long navigationHideAt;
    private double lastMouseX = Double.NaN, lastMouseY = Double.NaN;
    private String group = "all";

    public AuiMenuPage(String kind, Component title, BiConsumer<Entry, Integer> activate, Runnable dismiss) {
        this(kind, title, activate, dismiss, new AuiInkTransition());
    }
    AuiMenuPage(String kind, Component title, BiConsumer<Entry, Integer> activate, Runnable dismiss, AuiInkTransition ink) {
        this.kind = kind; this.title = title; this.activate = activate; this.dismiss = dismiss;
        this.ink = ink;
        document = AuiClient.open("teamlifebind/menu.html");
        try (var ignored = Document.withContext(document)) { bind(); }
        active = this;
    }
    public static AuiInkTransition.Frame inkFrame() { return active == null ? AuiInkTransition.Frame.IDLE : active.ink.frame(AuiPageReadiness.ready(active.document)); }
    static boolean hasActivePage() { return active != null; }
    static void tickActive() {
        if (active == null) return;
        if (active.navigationHideAt != 0 && System.nanoTime() >= active.navigationHideAt) {
            try (var ignored = Document.withContext(active.document)) {
                active.navigationHideAt = 0; active.id("navigation-menu").setAttribute("class", "navigation-menu");
            }
        }
        if (active.ink.closingDone()) active.dismiss.run();
    }
    public Document document() { return document; }
    public int visibleCount() { return filtered.size(); }
    public int selectedSlot() { return selected; }
    private static String tr(String key) { return Component.translatable("ui.teamlifebind." + key).getString(); }
    private Element id(String id) {
        Element result = document.getElementById(id);
        if (result == null) throw new IllegalStateException("Missing TLB menu element: " + id);
        return result;
    }
    private void text(String id, String value) { id(id).setTextContent(value); }
    private void bind() {
        generation = document.getRefreshGeneration();
        document.body.setAttribute("data-kind", kind);
        document.body.setAttribute("data-input", "keyboard");
        text("title", title.getString()); text("category", tr("category." + kind));
        text("chapter-number", switch (kind) { case "lobby" -> "01"; case "campaign" -> "02"; case "dev" -> "03"; case "magic" -> "04"; case "routes" -> "05"; default -> "06"; });
        text("chapter-name", tr("web.chapter." + kind));
        text("subtitle", tr("web.subtitle." + kind));
        refreshMotion();
        text("inspect", tr(inspecting ? "web.back" : "web.inspect"));
        id("shell").setAttribute("class", inspecting ? "shell inspect" : "shell");
        text("detail-kicker", tr("web.detail")); text("activate", tr("web.activate"));
        text("alternate", tr("web.alternate")); text("hint", tr(kind.equals("lobby") ? "web.hint.lobby" : "web.hint"));
        text("browse", tr("web.browse")); text("flyout-title", tr("web.browse_title"));
        text("browse-detail", tr("web.browse_detail"));
        id("navigation-menu").addEventListener("mouseenter", event -> { navigationOpenedOnHover = true; setNavigation(true); });
        id("navigation-menu").addEventListener("mouseleave", event -> { navigationOpenedOnHover = false; setNavigation(false); });
        id("browse").addEventListener("click", event -> {
            if (navigationOpenedOnHover) { navigationOpenedOnHover = false; setNavigation(true); }
            else setNavigation(!navigationOpen);
        });
        id("browse-detail").addEventListener("click", event -> { setNavigation(false); inspect(); });
        id("close").addEventListener("click", event -> {
            AuiScreenTransitions.back(dismiss);
        });
        id("motion").addEventListener("click", event -> { AuiMenuSettings.toggleMotion(); refreshMotion(); });
        id("inspect").addEventListener("click", event -> inspect());
        id("activate").addEventListener("click", event -> activateSelected(0));
        id("alternate").addEventListener("click", event -> activateSelected(1));
        rebuild();
    }
    public void update(List<Entry> incoming) {
        try (var ignored = Document.withContext(document)) { updateInContext(incoming); }
    }
    private void updateInContext(List<Entry> incoming) {
        if (document.getRefreshGeneration() != generation) bind();
        boolean changed = incoming.size() != entries.size();
        if (!changed) for (int i = 0; i < incoming.size(); i++) {
            Entry old = entries.get(i), next = incoming.get(i);
            if (old.slot() != next.slot() || !ItemStack.matches(old.stack(), next.stack())) { changed = true; break; }
        }
        if (!changed) return;
        boolean wasEmpty = entries.isEmpty();
        entries = incoming.stream().filter(e -> visible(e.stack())).map(e -> new Entry(e.slot(), e.stack().copy())).toList();
        rebuild();
        if (wasEmpty && !entries.isEmpty() && AuiMenuSettings.motion() && !AuiScreenTransitions.active())
            ink.startWhenReady(AuiInkTransition.Mode.OPEN, AuiInkTransition.Region.FULL, false, 1, AuiInkTransition.OPEN_MILLIS);
    }
    private void rebuild() {
        navigation = entries.stream().filter(this::navigationEntry).toList();
        var next = new ArrayList<>(entries.stream().filter(e -> !navigationEntry(e))
            .filter(e -> !kind.equals("lobby") || group.equals("all") || groupOf(e).equals(group)).toList());
        if (kind.equals("lobby")) next.sort(java.util.Comparator.comparingInt(e -> priority(action(e.stack()))));
        if (kind.equals("routes")) next.sort(java.util.Comparator.comparingInt(e -> switch (e.slot()) { case 10 -> 0; case 13 -> 1; case 16 -> 2; default -> 3; }));
        filtered = List.copyOf(next);
        if (filtered.stream().noneMatch(e -> e.slot() == selected)) selected = filtered.isEmpty() ? -1 : filtered.getFirst().slot();
        Element container = id("cards"); container.setTextContent(""); cards.clear();
        for (Entry entry : filtered) {
            Element card = node("button", cardClass(entry), ""); card.setAttribute("id", "slot-" + entry.slot());
            card.setAttribute("tabindex", "-1");
            if (routeEntry(entry)) card.appendChild(node("div", "route-number", "0" + ((entry.slot() - 7) / 3)));
            Element icon = node("div", "card-icon", ""); icon.appendChild(item(entry.stack())); card.appendChild(icon);
            Element copy = node("div", "card-copy", "");
            copy.appendChild(node("div", "card-name", label(entry.stack())));
            var lore = entry.stack().get(DataComponents.LORE);
            String caption = lore == null ? "" : String.join("\n", lore.lines().stream().map(Component::getString)
                .filter(s -> !s.isBlank()).limit(routeEntry(entry) ? 3 : 1).toList());
            copy.appendChild(node("div", "card-caption", caption)); card.appendChild(copy);
            bindCard(card, entry);
            container.appendChild(card); cards.put(entry.slot(), card);
        }
        Element nav = id("navigation"); nav.setTextContent("");
        for (Entry entry : navigation) {
            Element button = node("button", "nav-action", ""); button.setAttribute("id", "slot-" + entry.slot());
            button.setAttribute("tabindex", "-1"); button.appendChild(item(entry.stack()));
            button.appendChild(node("span", "", label(entry.stack()))); bindCard(button, entry);
            nav.appendChild(button); cards.put(entry.slot(), button);
        }
        nav.setAttribute("style", "display:" + (navigation.isEmpty() ? "none" : "flex"));
        rebuildGroups();
        text("count", Component.translatable("ui.teamlifebind.web.count", filtered.size()).getString());
        text("empty", tr(entries.isEmpty() ? "web.waiting" : "web.no_results"));
        id("empty").setAttribute("style", "display:" + (filtered.isEmpty() ? "block" : "none"));
        refreshSelection();
    }
    private void refreshMotion() {
        boolean motion = AuiMenuSettings.motion();
        document.body.setAttribute("data-motion", motion ? "on" : "off");
        text("motion", tr(motion ? "web.motion_on" : "web.motion_off"));
        if (!motion) ink.finish();
    }
    private void bindCard(Element card, Entry entry) {
        card.addEventListener("mouseenter", event -> { if (!keyboardSelection && !inspecting && !ink.busy()) select(entry.slot()); });
        card.addEventListener("click", event -> { if (!ink.busy()) { select(entry.slot()); activate.accept(entry, 0); } });
        card.addEventListener("contextmenu", event -> { event.preventDefault(); if (!ink.busy()) { select(entry.slot()); activate.accept(entry, 1); } });
    }
    private void rebuildGroups() {
        Element tabs = id("groups"); tabs.setTextContent("");
        tabs.setAttribute("style", "display:" + (kind.equals("lobby") ? "flex" : "none"));
        if (!kind.equals("lobby")) return;
        text("browse", tr("web.browse") + " · " + tr("web.group." + group));
        for (String key : GROUPS) {
            long count = entries.stream().filter(e -> key.equals("all") || groupOf(e).equals(key)).count();
            Element tab = node("button", key.equals(group) ? "group selected" : "group",
                tr("web.group." + key) + "  " + count);
            tab.setAttribute("id", "group-" + key);
            tab.addEventListener("click", event -> changeGroup(key)); tabs.appendChild(tab);
        }
    }
    private void changeGroup(String key) {
        var region = region("cards"); int direction = GROUPS.indexOf(key) >= GROUPS.indexOf(group) ? 1 : -1;
        boolean changed = !group.equals(key); group = key; rebuild(); id("cards").scrollTo(0, 0);
        setNavigation(false);
        if (changed && AuiMenuSettings.motion()) ink.startWhenReady(AuiInkTransition.Mode.CONTENT, region, true, direction, AuiInkTransition.CONTENT_MILLIS);
    }
    private void setNavigation(boolean open) {
        boolean fading = !open && navigationOpen && AuiMenuSettings.motion();
        navigationOpen = open;
        navigationHideAt = fading ? System.nanoTime() + 220_000_000L : 0;
        id("navigation-menu").setAttribute("class", open ? "navigation-menu open" : fading ? "navigation-menu closing" : "navigation-menu");
        id("browse").setAttribute("aria-expanded", Boolean.toString(open));
    }
    private List<Element> navigationItems() {
        var items = new ArrayList<>(document.querySelectorAll(".group"));
        items.add(id("browse-detail")); return items;
    }
    private AuiInkTransition.Region region(String element) {
        var box = id(element).getBoundingClientRect(); var viewport = document.getViewportSize();
        return new AuiInkTransition.Region((float)(box.left / viewport.width()), (float)(box.top / viewport.height()),
            (float)(box.right / viewport.width()), (float)(box.bottom / viewport.height()));
    }
    private static String groupOf(Entry entry) {
        return switch (action(entry.stack())) {
            case "ready", "unready", "start", "stop", "campaign", "status", "teams" -> "match";
            case "health", "special_health", "healthsync", "special_health_sync", "norespawn", "special_norespawn",
                 "match_pace", "totem_balance", "supply_drops", "supply_dynamic", "auto_portals", "special_auto_portals",
                 "stronghold_hints", "end_totems", "rules" -> "rules";
            default -> "display";
        };
    }
    private boolean navigationEntry(Entry entry) {
        return kind.equals("magic") && entry.slot() >= 45
            || kind.equals("routes") && !routeEntry(entry)
            || kind.equals("expedition") && entry.slot() == 40;
    }
    private boolean routeEntry(Entry entry) { return kind.equals("routes") && (entry.slot() == 10 || entry.slot() == 13 || entry.slot() == 16); }
    private String cardClass(Entry entry) {
        if (navigationEntry(entry)) return "nav-action";
        if (routeEntry(entry)) return "card route";
        return kind.equals("lobby") && priority(action(entry.stack())) <= 2 ? "card featured" : "card";
    }
    private Element node(String tag, String css, String value) {
        Element element = document.createElement(tag); element.setAttribute("class", css); element.setTextContent(value); return element;
    }
    private Item item(ItemStack stack) {
        Item element = new Item(document); element.setIngredientStack(stack.copy()); return element;
    }
    private void select(int slot) {
        if (selected != slot) { selected = slot; id("detail-scroll").scrollTo(0, 0); refreshSelection(); }
    }
    private void refreshSelection() {
        for (Entry entry : entries) {
            Element card = cards.get(entry.slot());
            if (card != null) card.setAttribute("class", cardClass(entry) + (entry.slot() == selected ? " selected" : ""));
        }
        Entry entry = selectedEntry();
        int position = List.copyOf(cards.keySet()).indexOf(selected) + 1;
        text("detail-position", entry == null ? "—" : String.format(Locale.ROOT, "%02d / %02d", position, cards.size()));
        id("detail-icon").setTextContent("");
        if (entry != null) id("detail-icon").appendChild(item(entry.stack()));
        text("detail-title", entry == null ? tr("web.choose") : label(entry.stack()));
        var lore = entry == null ? null : entry.stack().get(DataComponents.LORE);
        text("detail-lore", lore == null ? "" : String.join("\n", lore.lines().stream().map(Component::getString).toList()));
        text("activate", primaryLabel(entry));
        boolean hasAction = entry != null && !(kind.equals("lobby") && action(entry.stack()).isEmpty())
            && !(kind.equals("routes") && entry.slot() == 4);
        id("activate").setAttribute("style", "display:" + (hasAction ? "block" : "none"));
        id("alternate").setAttribute("style", "display:" + (hasAction ? "block" : "none"));
    }
    private String primaryLabel(Entry entry) {
        if (entry == null) return tr("web.activate");
        if (navigationEntry(entry)) return label(entry.stack());
        if (routeEntry(entry)) return tr("web.vote");
        if (kind.equals("campaign")) return tr("web.adjust");
        if (kind.equals("lobby")) return switch (action(entry.stack())) {
            case "ready", "unready", "start", "stop", "reload" -> label(entry.stack());
            case "campaign", "status", "rules", "help" -> tr("web.open");
            case "healthsync", "norespawn", "supply_dynamic", "auto_portals", "stronghold_hints", "end_totems",
                 "announce_teams", "scoreboard", "tab", "advancements" -> tr("web.toggle");
            default -> tr("web.adjust");
        };
        return tr("web.activate");
    }
    private Entry selectedEntry() { return cards.containsKey(selected) ? entries.stream().filter(e -> e.slot() == selected).findFirst().orElse(null) : null; }
    private void activateSelected(int button) { Entry entry = selectedEntry(); if (entry != null && !ink.busy()) activate.accept(entry, button); }
    private void inspect() {
        boolean animate = AuiMenuSettings.motion();
        var region = region("shell");
        keyboardSelection = true;
        inspecting = !inspecting; id("shell").setAttribute("class", inspecting ? "shell inspect" : "shell");
        text("inspect", tr(inspecting ? "web.back" : "web.inspect"));
        if (animate) ink.startWhenReady(AuiInkTransition.Mode.CONTENT, region, true, inspecting ? -1 : 1, AuiInkTransition.CONTENT_MILLIS);
    }
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        try (var ignored = Document.withContext(document)) { renderInContext(graphics, mouseX, mouseY); }
    }
    private void renderInContext(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (document.getRefreshGeneration() != generation) bind();
        if (AuiPageReadiness.placeholder(graphics, document)) return;
        if (!Double.isNaN(lastMouseX) && (mouseX != lastMouseX || mouseY != lastMouseY)) {
            keyboardSelection = false;
        }
        lastMouseX = mouseX; lastMouseY = mouseY;
        Operation.onMouseMove(new Position(mouseX, mouseY));
        ScreenDocumentCache.attach(document);
        ApricityGuiLayers.submitOverlay(graphics);
    }
    public void resize() { ink.finish(); document.applyViewport(true); }
    public boolean mouse(MouseButtonEvent event, boolean down) {
        if(!AuiPageReadiness.ready(document)||AuiScreenTransitions.active())return true;
        if (down) { keyboardSelection = false; document.body.setAttribute("data-input", "mouse"); }
        int button = event.button() == InputConstants.MOUSE_BUTTON_LEFT ? 0 : event.button() == InputConstants.MOUSE_BUTTON_RIGHT ? 1 : 2;
        MouseEvent translated = new MouseEvent(down ? "mousedown" : "mouseup", new Position(event.x(), event.y()), button);
        translated.setTrusted(true); MouseEvent.tiggerEvent(translated, document); return true;
    }
    public boolean scroll(double x, double y, double dy) {
        try (var ignored = Document.withContext(document)) { return scrollInContext(x, y, dy); }
    }
    private boolean scrollInContext(double x, double y, double dy) {
        if (navigationOpen && kind.equals("lobby")) {
            var rect = id("flyout").getBoundingClientRect();
            var a = document.documentToScreenPosition(new Position(rect.left, rect.top));
            var b = document.documentToScreenPosition(new Position(rect.right, rect.bottom));
            if (x >= a.x && x <= b.x && y >= a.y && y <= b.y) {
                keyboardSelection = false;
                changeGroup(GROUPS.get(Math.floorMod(GROUPS.indexOf(group) + (dy < 0 ? 1 : -1), GROUPS.size()))); return true;
            }
        }
        MouseEvent event = new MouseEvent("wheel", new Position(x, y)); event.deltaY = -dy * 50; event.scrollDelta = event.deltaY;
        event.cancelable = true; event.setTrusted(true); MouseEvent.tiggerEvent(event, document); return true;
    }
    public boolean key(KeyEvent key) {
        try (var ignored = Document.withContext(document)) { return keyInContext(key); }
    }
    private boolean keyInContext(KeyEvent key) {
        if(key.hasControlDown()&&key.hasShiftDown()&&key.key()==InputConstants.KEY_M){AuiMenuSettings.toggleMotion();refreshMotion();return true;}
        ink.finish(); closing = false;
        keyboardSelection = true; document.body.setAttribute("data-input", "keyboard");
        if (key.isEscape()) { if (navigationOpen) setNavigation(false); else if (inspecting) inspect(); else AuiScreenTransitions.back(dismiss); return true; }
        if (Minecraft.getInstance().options.keyInventory.matches(key)) { AuiScreenTransitions.back(dismiss); return true; }
        if (key.key() == InputConstants.KEY_M && key.hasControlDown()) {
            setNavigation(!navigationOpen); navigationFocus = 0;
            if (navigationOpen) navigationItems().getFirst().focus(); return true;
        }
        if (navigationOpen) {
            var items = navigationItems();
            if (key.key() == InputConstants.KEY_UP || key.key() == InputConstants.KEY_DOWN || key.key() == InputConstants.KEY_TAB) {
                int step = key.key() == InputConstants.KEY_UP || key.hasShiftDown() ? -1 : 1;
                navigationFocus = Math.floorMod(navigationFocus + step, items.size()); items.get(navigationFocus).focus(); return true;
            }
            if (key.isConfirmation()) { var item = items.get(navigationFocus); item.dispatchEvent(new com.teamlifebind.aui.event.Event(item, "click", true)); return true; }
        }
        if (key.key() == InputConstants.KEY_D) { inspect(); return true; }
        if (key.isConfirmation()) { activateSelected(key.hasShiftDown() ? 1 : 0); return true; }
        if (key.key() == InputConstants.KEY_F && key.hasControlDown()) return true;
        if (kind.equals("lobby") && key.hasControlDown() && key.key() >= InputConstants.KEY_1 && key.key() <= InputConstants.KEY_4) {
            changeGroup(GROUPS.get(key.key() - InputConstants.KEY_1)); return true;
        }
        if (inspecting) {
            if (key.key() == InputConstants.KEY_DOWN || key.key() == InputConstants.KEY_UP) {
                id("detail-scroll").scrollBy(0, key.key() == InputConstants.KEY_DOWN ? 60 : -60); return true;
            }
            return false;
        }
        if (key.key() == InputConstants.KEY_UP || key.key() == InputConstants.KEY_DOWN) {
            int next = verticalNeighbor(key.key() == InputConstants.KEY_DOWN ? 1 : -1);
            if (next != -1) focusCard(next); return true;
        }
        int step = switch (key.key()) {
            case InputConstants.KEY_LEFT -> -1; case InputConstants.KEY_RIGHT -> 1;
            case InputConstants.KEY_TAB -> key.hasShiftDown() ? -1 : 1; default -> 0;
        };
        if (step != 0 && !cards.isEmpty()) {
            List<Integer> slots = List.copyOf(cards.keySet());
            int at = slots.indexOf(selected);
            focusCard(slots.get(Math.floorMod(at + step, slots.size())));
            return true;
        }
        return false;
    }
    /** Follow the actual rows, including wrapped footer actions and resource-pack layouts. */
    private int verticalNeighbor(int direction) {
        Element current = cards.get(selected);
        if (current == null) return cards.isEmpty() ? -1 : cards.keySet().iterator().next();
        boolean inNavigation = navigationEntry(selectedEntry());
        // The pinned footer can be physically nearer than a row below the scrollport.
        // Exhaust rows in the current region before crossing into the other region.
        List<Entry> region = inNavigation ? navigation : filtered;
        var box = current.getBoundingClientRect();
        double bestY = Double.MAX_VALUE, bestX = Double.MAX_VALUE; int next = -1;
        for (Entry candidate : region) {
            if (candidate.slot() == selected) continue;
            var rect = cards.get(candidate.slot()).getBoundingClientRect();
            double dy = (rect.top - box.top) * direction;
            double dx = Math.abs(rect.left + rect.width / 2 - box.left - box.width / 2);
            if (dy > 1 && (dy < bestY - 1 || Math.abs(dy - bestY) <= 1 && dx < bestX)) {
                bestY = dy; bestX = dx; next = candidate.slot();
            }
        }
        if (next == -1 && direction > 0 && !inNavigation && !navigation.isEmpty()) return navigation.getFirst().slot();
        if (next == -1 && direction < 0 && inNavigation && !filtered.isEmpty()) return filtered.getLast().slot();
        return next;
    }
    private void focusCard(int slot) {
        keyboardSelection = true;
        select(slot); Element card = cards.get(slot); card.focus();
        Entry entry = selectedEntry(); if (entry == null || navigationEntry(entry)) return;
        var box = card.getBoundingClientRect(); var pane = id("cards").getBoundingClientRect();
        if (box.bottom > pane.bottom) id("cards").scrollBy(0, box.bottom - pane.bottom + 8);
        else if (box.top < pane.top) id("cards").scrollBy(0, box.top - pane.top - 8);
    }
    public boolean character(int codePoint) {
        return true;
    }
    @Override public void close() { ink.finish(); if (active == this) active = null; document.remove(); com.teamlifebind.aui.style.Cursor.resetToDefault(); }
    public static boolean visible(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!stack.is(Items.STAINED_GLASS_PANE.gray())) return true;
        var name = stack.get(DataComponents.CUSTOM_NAME); var lore = stack.get(DataComponents.LORE);
        return name == null || !name.getString().isBlank() || !action(stack).isEmpty()
                || lore != null && lore.lines().stream().anyMatch(line -> !line.getString().isBlank());
    }
    private static String action(ItemStack stack) { var data = stack.get(DataComponents.CUSTOM_DATA); return data == null ? "" : data.copyTag().getStringOr("tlb_menu_action", ""); }
    private static String label(ItemStack stack) { String name = stack.getHoverName().getString(); return name.startsWith("[") && name.endsWith("]") ? name.substring(1, name.length() - 1) : name; }
    private static int priority(String action) {
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
}
