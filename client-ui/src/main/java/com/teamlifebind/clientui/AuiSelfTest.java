package com.teamlifebind.clientui;

import com.mojang.blaze3d.platform.InputConstants;
import com.teamlifebind.aui.event.Event;
import com.teamlifebind.aui.init.Document;
import com.teamlifebind.aui.layout.Position;
import com.teamlifebind.aui.screen.AuiLinkedScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Opt-in full-client render/input fixture; never reads a world or connects to a server. */
final class AuiSelfTest {
    private static final String[] KINDS = {"lobby", "dev", "magic", "routes", "campaign", "expedition"};
    private static final String[] SIZES = {"wide", "medium", "compact"};
    private static final int[][] WINDOWS = {{1920, 1080, 2}, {1440, 900, 2}, {900, 600, 3}};
    private static final List<String> evidence = new ArrayList<>();
    private static int ticks, stage, opened, phase, inspectedSlot;
    private static int readyTicks;
    private static long testClock;
    private static AuiInkSelfTest inkProbe;
    private static boolean inkChecked;
    private static Screen initialScreen;
    private static boolean started, capturing, done;
    private static volatile boolean captured;
    private static volatile Throwable captureFailure;
    private static Fixture screen;
    private static final Path OUTPUT = Path.of(System.getProperty("teamlifebind.aui.output", "tlb-aui-selftest"));
    private AuiSelfTest() {}
    static void tick() {
        if (done) return;
        ticks++; if (inkProbe == null) testClock += 50_000_000L;
        try (var context = Document.withContext(screen == null || screen.page == null ? null : screen.page.document())) {
            if (ticks > 3000) throw new IllegalStateException("AUI selftest timed out");
            Minecraft mc = Minecraft.getInstance();
            if (!started) {
                if (ticks < 30 || mc.gui.overlay() != null || mc.gui.screen() == null) { readyTicks = 0; return; }
                // Startup replaces the first Screen after resources finish. Wait until that
                // sequence is stable so it cannot dispose our fixture before its first paint.
                if (initialScreen != mc.gui.screen()) { initialScreen = mc.gui.screen(); readyTicks = 0; }
                if (++readyTicks < 40) return;
                Files.createDirectories(OUTPUT);
                resizeWindow();
                started = true; open(); return;
            }
            if (capturing) {
                if (!captured) return;
                if (captureFailure != null) throw new IllegalStateException("AUI capture failed", captureFailure);
                capturing = false;
                if (phase == 0) interactions();
                else {
                    if (screen.kind.equals("lobby")) {
                        var lore = screen.page.document().getElementById("detail-scroll");
                        require(lore.scrollHeight > lore.getClientHeight() + 20, "Long rules are not scrollable: " + lore.scrollHeight + "/" + lore.getClientHeight());
                        screen.page.key(new KeyEvent(InputConstants.KEY_DOWN, 0, 0));
                        require(lore.getTargetScrollTop() > 0, "Keyboard cannot scroll long rules");
                    }
                    screen.page.key(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0));
                    require(mc.gui.screen() == screen, "Escape closed the menu instead of leaving details");
                    require(screen.ink.frame().effect(),"Keyboard detail dismissal skipped its exit animation");
                }
                phase++; opened = ticks; return;
            }
            // Style changes commit on the next AUI tick, as they do during real input.
            if (ticks - opened < (phase == 0 ? 45 : 12)) return;
            if (phase == 2) {
                if (stage == 0 && !inkChecked) {
                    if (inkProbe == null) inkProbe = new AuiInkSelfTest(screen.page, screen.ink, OUTPUT, evidence, value -> testClock += value);
                    if (!inkProbe.tick()) return;
                    inkProbe = null; inkChecked = true;
                }
                closeInteractions(); mc.gui.setScreen(null);
                require(Document.getAll().isEmpty(), "Document leak after close");
                stage++;
                if (stage >= KINDS.length * SIZES.length) { finish(null); return; }
                if (stage % KINDS.length == 0) resizeWindow();
                open(); return;
            }
            if (phase == 0) checkLayout();
            else {
                require(screen.page.selectedSlot() == inspectedSlot, "Stationary pointer changed the keyboard selection while opening details");
                var detail = screen.page.document().getElementById("detail").getBoundingClientRect();
                require(detail.width > 100 && detail.height > 100, "Details cannot be opened");
                require(screen.page.document().querySelector(".catalog").getBoundingClientRect().width == 0,
                    "Expanded detail overlaps catalog");
            }
            capturing = true; captured = false; captureFailure = null;
            Path png = OUTPUT.resolve(SIZES[stage / KINDS.length] + "-" + KINDS[stage % KINDS.length] + (phase == 1 ? "-detail" : "") + ".png");
            Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
                try (image) {
                    int[] pixels = image.getPixels();
                    int detail = 0, accent = 0;
                    for (int color : pixels) {
                        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
                        if (r > 100 && g > 100 && b > 100) detail++;
                        if (r > 140 && g > 110 && r > g * 1.04 && g > b * 1.16) accent++;
                    }
                    image.writeToFile(png);
                    require(detail > 600, "Rendered UI has no text/icon detail: " + detail);
                    require(accent > 200, "Rendered UI has no accent/button pixels: " + accent);
                    evidence.add(png.getFileName() + " textPixels=" + detail + " accentPixels=" + accent);
                } catch (Throwable failure) { captureFailure = failure; }
                captured = true;
            });
        } catch (Throwable failure) { finish(failure); }
    }
    private static void resizeWindow() {
        var mc = Minecraft.getInstance(); int[] size = WINDOWS[stage / KINDS.length];
        mc.getWindow().setWindowed(size[0], size[1]); mc.options.guiScale().set(size[2]); mc.resizeGui();
    }
    private static void open() {
        screen = new Fixture(KINDS[stage % KINDS.length]);
        Minecraft.getInstance().gui.setScreen(screen); opened = ticks; phase = 0;
    }
    private static void checkLayout() {
        require(com.teamlifebind.aui.resource.Font.isRegistered("TLBArchive"), "Bundled heading font did not load");
        var doc = screen.page.document();
        var shell = doc.getElementById("shell").getBoundingClientRect();
        var viewport = doc.getViewportSize();
        require(shell.width > 200 && shell.height > 200, "Shell is missing");
        require(shell.left >= -1 && shell.top >= -1 && shell.right <= viewport.width() + 1 && shell.bottom <= viewport.height() + 1, "Shell exceeds viewport");
        int rendered = doc.querySelectorAll(".card").size() + doc.querySelectorAll(".nav-action").size();
        long expected = screen.entries.stream().filter(e -> AuiMenuPage.visible(e.stack())).count();
        require(rendered == expected, "Entry filtering lost valid items: " + rendered + "/" + expected);
        var first = doc.querySelector(".card").getBoundingClientRect();
        require(first.width > 80 && first.height >= 40, "Cards are not laid out");
        require(doc.getElementById("search")==null&&doc.getElementById("browse-search")==null,"Removed search controls remain in menu");
        var toolbar = doc.querySelector(".toolbar").getBoundingClientRect();
        require(first.top >= toolbar.bottom - 1, "Cards overlap their count row");
        var pane = doc.getElementById("cards").getBoundingClientRect();
        var catalog = doc.querySelector(".catalog").getBoundingClientRect();
        require(pane.height >= 60 && pane.bottom <= catalog.bottom + 1, "Catalog has no usable scrolling area");
        for (var action : doc.querySelectorAll(".nav-action")) {
            var box = action.getBoundingClientRect();
            require(box.top >= pane.bottom - 1 && box.bottom <= catalog.bottom + 1 && box.right <= catalog.right + 1,
                "Navigation overlaps or overflows the catalog: " + action.getAttribute("id"));
        }
    }
    private static void interactions() {
        var page = screen.page; var doc = page.document();
        var motion = doc.getElementById("motion");
        motion.dispatchEvent(new Event(motion, "click", true));
        require("off".equals(doc.body.getAttribute("data-motion")), "Static presentation preference failed");
        motion.dispatchEvent(new Event(motion, "click", true));
        require("on".equals(doc.body.getAttribute("data-motion")), "Dynamic presentation preference failed");
        var navigation = doc.getElementById("navigation-menu"); var browse = doc.getElementById("browse");
        navigation.dispatchEvent(new Event(navigation, "mouseenter", true));
        require("true".equals(browse.getAttribute("aria-expanded")), "Hover navigation did not open");
        browse.dispatchEvent(new Event(browse, "click", true));
        require("true".equals(browse.getAttribute("aria-expanded")), "Hover followed by click incorrectly closed navigation");
        navigation.dispatchEvent(new Event(navigation, "mouseleave", true));
        require("false".equals(browse.getAttribute("aria-expanded")), "Mouse exit did not close navigation");
        page.key(new KeyEvent(InputConstants.KEY_M, 0, InputConstants.MOD_CONTROL));
        require("true".equals(browse.getAttribute("aria-expanded")), "Keyboard navigation did not open");
        page.key(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0));
        require("false".equals(browse.getAttribute("aria-expanded")), "Escape did not leave navigation");
        require(doc.getElementById("navigation-menu").getAttribute("class").contains("closing"),"Keyboard navigation dismissal skipped its exit state");
        int old = page.selectedSlot();
        page.key(new KeyEvent(InputConstants.KEY_RIGHT, 0, 0));
        require("keyboard".equals(doc.body.getAttribute("data-input")), "Keyboard input kept mouse motion feedback");
        require(page.selectedSlot() != old, "Keyboard selection failed");
        int selected = page.selectedSlot();
        page.key(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
        require(screen.lastSlot == selected && screen.lastButton == 0, "Enter dispatched wrong slot/button");
        var firstCard = doc.querySelector(".card");
        int firstSlot = Integer.parseInt(firstCard.getAttribute("id").substring("slot-".length()));
        var first = firstCard.getBoundingClientRect();
        Position point = doc.documentToScreenPosition(new Position(first.x + first.width / 2, first.y + first.height / 2));
        var right = new MouseButtonEvent(point.x, point.y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_RIGHT, 0));
        page.mouse(right, true); page.mouse(right, false);
        var hit = doc.hitTest(new Position(first.x + first.width / 2, first.y + first.height / 2));
        require(screen.lastSlot == firstSlot && screen.lastButton == 1,
                "Right click dispatched wrong slot/button actual=" + screen.lastSlot + ":" + screen.lastButton
                + " box=" + first.x + "," + first.y + "," + first.width + "," + first.height
                + " gui=" + point + " scale=" + doc.getViewportScaleX()
                + " hit=" + (hit == null ? "null" : hit.tagName + "#" + hit.getAttribute("id")));
        if (screen.kind.equals("magic")) {
            // Rapid key repeats must traverse the offscreen books before the pinned footer.
            for (int i = 0; i < 45; i++) {
                int before = page.selectedSlot();
                var box = doc.getElementById("slot-" + before).getBoundingClientRect();
                boolean booksBelow = doc.querySelectorAll(".card").stream().anyMatch(e -> e.getBoundingClientRect().top > box.top + 1);
                page.key(new KeyEvent(InputConstants.KEY_DOWN, 0, 0));
                if (!booksBelow) break;
                require(page.selectedSlot() < 45 && page.selectedSlot() != before, "Down skipped offscreen books for the pinned footer");
            }
        }
        int total = page.visibleCount();
        page.key(new KeyEvent(InputConstants.KEY_F,0,InputConstants.MOD_CONTROL));page.character('X');
        require(page.visibleCount()==total,"Removed search shortcut or typed text still filters entries");
        page.update(List.of());
        require(page.visibleCount() == 0 && page.selectedSlot() == -1, "Empty catalog retained an actionable selection");
        int clicks = screen.clicks; page.key(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
        require(screen.clicks == clicks, "Empty catalog dispatched a click");
        page.update(screen.entries);
        require(page.visibleCount()==total,"Catalog update lost entries");
        if (screen.kind.equals("lobby")) {
            page.key(new KeyEvent(InputConstants.KEY_2, 0, InputConstants.MOD_CONTROL));
            require(page.visibleCount() > 0 && page.visibleCount() < total && doc.getElementById("slot-11") != null,
                "Match group lost the real ready control");
            require(doc.getElementById("slot-21") == null, "Rules leaked into match group");
            var rules = doc.getElementById("group-rules"); rules.dispatchEvent(new Event(rules, "click", true));
            require(doc.getElementById("slot-21") != null && doc.getElementById("slot-11") == null, "Rules group is incorrect");
            page.key(new KeyEvent(InputConstants.KEY_1, 0, InputConstants.MOD_CONTROL));
            require(page.visibleCount() == total, "All group lost an entry");
        }
        // Arrow movement must follow the rendered grid, not a hard-coded width threshold.
        var chosen = doc.getElementById("slot-" + page.selectedSlot()).getBoundingClientRect();
        boolean nextRow = doc.querySelectorAll(".card").stream().anyMatch(e -> e.getBoundingClientRect().top > chosen.top + 1);
        if (nextRow) {
            page.key(new KeyEvent(InputConstants.KEY_DOWN, 0, 0));
            var moved = doc.getElementById("slot-" + page.selectedSlot()).getBoundingClientRect();
            require(moved.top > chosen.top + 1, "Down arrow did not follow the actual next row");
        }
        int target = screen.kind.equals("lobby") ? 4 : screen.kind.equals("magic") ? 0 : firstSlot;
        for (int i = 0; page.selectedSlot() != target && i < screen.entries.size() + 1; i++)
            page.key(new KeyEvent(InputConstants.KEY_RIGHT, 0, 0));
        require(page.selectedSlot() == target, "Cannot reach detail target with keyboard");
        inspectedSlot = target;
        page.key(new KeyEvent(InputConstants.KEY_D, 0, 0));
    }
    private static void closeInteractions() {
        var page = screen.page; var doc = page.document();
        require(doc.querySelector(".catalog").getBoundingClientRect().width > 100, "Catalog did not return after details");
        page.key(new KeyEvent(InputConstants.KEY_F, 0, InputConstants.MOD_CONTROL));
        require(doc.getElementById("search")==null&&Minecraft.getInstance().gui.screen()==screen,"Removed search was reopened by Ctrl+F");
        page.key(new KeyEvent(InputConstants.KEY_E, 0, 0));
        require(Minecraft.getInstance().gui.screen() != screen, "Inventory key did not close menu");
        evidence.add("PASS " + screen.kind + " layout, keyboard rows, right-click, search removed, groups/navigation, details, empty-selection, inventory-key close");
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static void finish(Throwable error) {
        done = true;
        if (error != null) { error.printStackTrace(); evidence.add("FAIL " + error); }
        else evidence.add("PASS all 18 runtime scenarios; no worlds opened");
        try { Files.createDirectories(OUTPUT); Files.writeString(OUTPUT.resolve("result.txt"), String.join("\n", evidence)); }
        catch (Exception failure) { failure.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
    private static final class Fixture extends Screen implements AuiLinkedScreen {
        final String kind;
        final List<AuiMenuPage.Entry> entries = new ArrayList<>();
        AuiMenuPage page;
        final AuiInkTransition ink = new AuiInkTransition(() -> testClock);
        int lastSlot = -1, lastButton = -1, clicks;
        Fixture(String kind) {
            super(Component.literal(switch (kind) {
                case "lobby" -> "TLB 控制面板"; case "dev" -> "开发者物品";
                case "magic" -> "魔法目录 · 第 1 页"; case "routes" -> "第 3 层 · 选择路线";
                case "campaign" -> "比赛赛制设置"; default -> "本局远征与遗物";
            })); this.kind = kind;
        }
        @Override protected void init() {
            if (page != null) { page.resize(); return; }
            populate();
            page = new AuiMenuPage(kind, title, (entry, button) -> { lastSlot = entry.slot(); lastButton = button; clicks++; }, this::onClose,
                ink);
            page.update(entries);
        }
        private void populate() {
            switch (kind) {
                case "lobby" -> {
                    try {
                        var json = com.google.gson.JsonParser.parseString(Files.readString(Path.of(System.getProperty("teamlifebind.aui.lobby")))).getAsJsonObject();
                        for (var value : json.getAsJsonArray("entries")) {
                            var entry = value.getAsJsonObject(); var lines = new ArrayList<String>();
                            for (var line : entry.getAsJsonArray("lore")) lines.add(line.getAsString());
                            var stack = add(entry.get("slot").getAsInt(), entry.get("item").getAsString(), entry.get("name").getAsString(), lines.toArray(String[]::new));
                            var tag = new net.minecraft.nbt.CompoundTag(); tag.putString("tlb_menu_action", entry.get("action").getAsString());
                            stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
                        }
                    } catch (Exception error) { throw new IllegalStateException("Cannot read captured lobby fixture", error); }
                }
                case "magic" -> {
                    int slot = 0;
                    for (var spell : com.teamlifebind.common.SpellCatalogue.all().subList(0, 45))
                        add(slot++, "teamlifebind:spell_book", spell.zhName(), "位阶 " + spell.tier() + " · 魔力 " + spell.manaCost(),
                            spell.zhDescription(), spell.school().zhEffect(), "点击打开领取与测试选项。");
                    add(45, "minecraft:arrow", "返回分类"); add(48, "minecraft:redstone", "结束魔法测试");
                    add(49, "minecraft:nether_star", "测试状态", "查看已学习魔法与当前魔力。");
                    add(51, "minecraft:barrier", "关闭"); add(53, "minecraft:arrow", "下一页");
                }
                case "dev" -> {
                    String[] ids = {"team_bed", "tracking_wheel", "death_exemption_totem", "life_curse_potion", "resonance_whistle", "emergency_bandage", "silent_sigil", "scout_rocket", "assault_rifle", "battle_pistol", "tactical_smg", "breach_shotgun", "marksman_rifle", "support_machine_gun", "siege_launcher"};
                    for (int i = 0; i < ids.length; i++) add(i, "teamlifebind:" + ids[i], Component.translatable("item.teamlifebind." + ids[i]).getString(), "点击领取一个物品用于开发测试。");
                    add(36, "minecraft:enchanted_book", "魔法开发测试", "打开魔法分类、领取与测试选项。");
                }
                case "routes" -> {
                    add(10, "minecraft:iron_sword", "荒野 · 精锐", "混合近战与远程敌人。", "精锐敌人获得额外生命。", "遗物：锋刃 · 攻击伤害提高", "当前票数：1 / 3", "全队共同选择下一条路线。");
                    add(13, "minecraft:amethyst_shard", "遗迹 · 魔潮", "探索被魔力浸染的遗迹。", "法术威胁增强。", "遗物：魔力泉 · 恢复魔力", "当前票数：0 / 3");
                    add(16, "minecraft:feather", "高地 · 疾行", "在高地迎击快速敌人。", "敌人移动速度提高。", "遗物：轻步 · 移动速度提高", "当前票数：0 / 3");
                    add(4, "minecraft:clock", "等待队伍投票", "多数票决定路线，平票按已有规则处理。");
                    add(22, "minecraft:book", "查看本局遗物", "所有遗物成长仅在本局生效。");
                }
                case "campaign" -> {
                    add(4, "minecraft:grass_block", "玩法模式", "当前：生存 SUR");
                    add(6, "minecraft:diamond_sword", "PVE 难度", "当前：标准");
                    add(10, "minecraft:iron_sword", "比赛模式", "当前：团队竞速");
                    add(12, "minecraft:dragon_head", "屠龙胜利", "当前：最后一击");
                    add(14, "minecraft:beacon", "契约据点", "当前：关闭");
                    add(16, "minecraft:compass", "终局缩圈", "当前：关闭");
                    add(22, "minecraft:totem_of_undying", "队伍复活次数", "当前：3", "左键增加，右键减少。");
                }
                default -> {
                    add(4, "minecraft:compass", "远征进度 · 第 3 层", "准备下一场遭遇。", "成长仅在本局生效。");
                    add(13, "minecraft:clock", "队伍正在选择路线", "全队投票后进入下一场战斗。");
                    add(18, "minecraft:iron_sword", "锋刃 · 等级 2", "提升攻击伤害。");
                    add(19, "minecraft:iron_chestplate", "护甲 · 等级 1", "提高护甲值。");
                    add(20, "minecraft:amethyst_shard", "魔力泉 · 等级 1", "恢复魔力，支持持续作战。");
                    add(40, "minecraft:arrow", "返回选路");
                }
            }
        }
        private ItemStack add(int slot, String model, String name, String... lore) {
            var id = Identifier.parse(model);
            var type = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);
            var map = DataComponentMap.builder().set(DataComponents.ITEM_MODEL, id)
                .set(DataComponents.MAX_STACK_SIZE, 64).set(DataComponents.ITEM_NAME, Component.literal(name)).build();
            var stack = new ItemStack(Holder.direct(type == null || type == Items.AIR ? Items.PAPER : type, map), 1);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
            stack.set(DataComponents.LORE, new ItemLore(java.util.Arrays.stream(lore).map(Component::literal).map(c -> (Component)c).toList()));
            entries.add(new AuiMenuPage.Entry(slot, stack)); return stack;
        }
        @Override public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float tick) {}
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float tick) { page.render(graphics, x, y); }
        @Override public Document getLinkedDocument() { return page == null ? null : page.document(); }
        @Override public void removed() { if (page != null) { page.close(); page = null; } }
    }
}
