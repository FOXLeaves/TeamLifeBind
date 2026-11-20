import com.teamlifebind.common.NativeHud;
import com.teamlifebind.common.NativeUi;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/** Real captured lobby contents through the production geometry; not a game screenshot. */
public final class ExportClientMenus {
    private record Entry(int slot, String icon, String name, List<String> lore) {
        Entry(int slot, String icon, String name) { this(slot, icon, name, List.of()); }
    }
    private static final Path OUT = Path.of(".tools/client-ui-preview");
    private static int assertions;
    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUT);
        var captured = com.google.gson.JsonParser.parseString(Files.readString(Path.of(".tools/client-ui-input/fabric/lobby-visible.json"))).getAsJsonObject();
        List<Entry> lobby = new ArrayList<>();
        for (var value : captured.getAsJsonArray("entries")) {
            var entry = value.getAsJsonObject(); var lore = new ArrayList<String>();
            for (var line : entry.getAsJsonArray("lore")) lore.add(line.getAsString());
            lobby.add(new Entry(entry.get("slot").getAsInt(), entry.get("item").getAsString().replace("minecraft:", ""), entry.get("name").getAsString(), lore));
        }
        var rogue = List.of(new Entry(4, "clock_00", "已投 2/4"), new Entry(10, "iron_sword", "锋刃纹章"), new Entry(13, "feather", "风行羽"),
            new Entry(16, "amethyst_shard", "回响泉眼"), new Entry(22, "book", "本局构筑"));
        List<BufferedImage> scenes = List.of(
            scene("menu-lobby", 640, 378, 6, captured.get("title").getAsString(), "大厅 · 队伍与准备", lobby, 11, List.of()),
            scene("menu-rg", 640, 378, 3, "肉鸽远征 · 第 4 层选路", "肉鸽远征 · 团队选路", rogue, 13, List.of("路线 2 · 寒霜边境 / 迅捷", "", "敌群：流浪者、僵尸、蜘蛛。", "所有敌人移动速度 +18%。", "", "风行羽 · 2/3 级", "每级移动速度 +6%。", "", "支持票数：2/4", "你的当前选择")),
            scene("menu-small", 320, 180, 6, captured.get("title").getAsString(), "大厅", lobby, 11, List.of()));
        BufferedImage image = new BufferedImage(1352, 980, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); g.setColor(new Color(0x101820)); g.fillRect(0, 0, 1352, 980);
        g.setFont(new Font("Microsoft YaHei", Font.BOLD, 24)); g.setColor(new Color(NativeHud.TEXT, true)); g.drawString("模组客户端 · 统一菜单", 24, 42);
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 15)); g.setColor(new Color(NativeHud.MUTED, true));
        g.drawString("大厅读取真实服务端菜单，经客户端过滤与排序后导出；贴图和背景为示意，不是实机截图。", 24, 70);
        g.drawImage(scenes.get(0), 24, 112, 640, 378, null); g.drawImage(scenes.get(1), 688, 112, 640, 378, null);
        g.drawString("真实大厅 24 项 / 640 × 378", 24, 100); g.drawString("团队投票与详情 / 640 × 378", 688, 100);
        g.drawString("完整大厅小窗口 / 320 × 180，以 2× 展示；D 或 i 按钮展开详情", 24, 542);
        g.drawImage(scenes.get(2), 24, 566, 640, 360, null); g.dispose();
        ImageIO.write(image, "PNG", Path.of("docs/images/client-ui-menus.png").toFile());
        for (int[] size : new int[][] {{320, 180}, {400, 240}, {640, 360}, {960, 540}, {1280, 720}})
            for (int rows : new int[] {3, 5, 6}) validate(size[0], size[1], rows);
        System.out.println("CLIENT_UI_LAYOUT_PASS assertions=" + assertions + " menus=3 guiSizes=5 slotMapping=true productionLayout=true");
    }
    private static BufferedImage scene(String id, int width, int height, int rows, String title, String category, List<Entry> entries, int selected, List<String> detail) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(new Color(0x25333D)); g.fillRect(0, 0, width, height);
        g.setColor(new Color(0x1C2A31)); g.fillRect(0, height / 2, width, height / 2);
        var canvas = new ExportNativeHud.AwtCanvas(g, width, height, id);
        var slots = entries.stream().map(Entry::slot).toList();
        if (rows == 3) slots = List.of(10, 13, 16, 4, 22);
        boolean controls = rows != 3;
        var layout = NativeUi.menuLayout(width, height, slots, !controls);
        int accent = rows == 3 ? NativeHud.GOLD : NativeHud.CYAN;
        NativeUi.menuFrame(canvas, layout, title, category, layout.compact() ? "Enter 确认 · D 详情 · Esc 关闭" : "方向键选择 · Enter 确认 · D 详情 · Esc 关闭", accent);
        try (ZipFile resources = new ZipFile(".tools/gradle-upgrade-home/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.3/minecraft-merged-deobf-26.3.jar")) {
            Map<Integer, Entry> bySlot = new HashMap<>(); for (Entry entry : entries) bySlot.put(entry.slot, entry);
            for (int slot : layout.slots()) {
                var tile = layout.tile(slot); Entry entry = bySlot.get(slot);
                NativeUi.menuTile(canvas, tile, entry != null, selected == slot, accent);
                if (entry == null) continue;
                String icon = switch (entry.icon) { case "clock", "compass" -> entry.icon + "_00"; default -> entry.icon; };
                var asset = resources.getEntry("assets/minecraft/textures/item/" + icon + ".png");
                if (asset == null) asset = resources.getEntry("assets/minecraft/textures/block/" + (icon.equals("lectern") ? "lectern_top" : icon) + ".png");
                if (asset == null) asset = resources.getEntry("assets/minecraft/textures/item/bundle.png");
                BufferedImage sprite = ImageIO.read(resources.getInputStream(asset));
                int y = tile.y() + (tile.height() - 16) / 2;
                boolean named = tile.width() >= 66;
                g.drawImage(sprite, named ? tile.x() + 6 : tile.x() + (tile.width() - 16) / 2, y, 16, 16, null);
                if (named) {
                    String label = entry.name;
                    if (controls && label.startsWith("[") && label.endsWith("]")) label = label.substring(1, label.length() - 1);
                    String name = NativeHud.fit(canvas, label, tile.width() - 33);
                    boolean twoLines = tile.height() >= (controls ? 28 : 40);
                    canvas.text(name, tile.x() + 28, tile.y() + (twoLines ? controls ? 4 : 10 : (tile.height() - 9) / 2), NativeHud.TEXT);
                    if (twoLines && !entry.lore.isEmpty()) canvas.text(NativeHud.fit(canvas, entry.lore.getFirst(), tile.width() - 33), tile.x() + 28, tile.y() + (controls ? 17 : 25), NativeHud.QUIET);
                }
            }
        }
        if (layout.details().width() > 0) {
            int y = layout.details().y() + 10;
            for (int index = 0; index < detail.size(); index++) {
                canvas.text(NativeHud.fit(canvas, detail.get(index), layout.details().width() - 20), layout.details().x() + 10, y + index * 11, index == 0 ? NativeHud.TEXT : NativeHud.MUTED);
            }
        }
        canvas.validateTextColumns(); g.dispose(); ImageIO.write(image, "PNG", OUT.resolve(id + ".png").toFile()); return image;
    }
    private static void validate(int width, int height, int rows) {
        var layout = NativeUi.menuLayout(width, height, rows); var box = layout.panel();
        check(box.x() >= 0 && box.y() >= 0 && box.x() + box.width() <= width && box.y() + box.height() <= height, "menu inside viewport");
        for (int slot = 0; slot < rows * 9; slot++) {
            var tile = layout.tile(slot);
            check(layout.hit(tile.x() + tile.width() / 2D, tile.y() + tile.height() / 2D) == slot, "client tile maps to original server slot");
            check(layout.hit(tile.x() + tile.width() + 1D, tile.y() + 2D) == -1, "gutter is not a clickable neighbor");
            check(tile.x() >= box.x() && tile.y() >= box.y() && tile.x() + tile.width() <= box.x() + box.width() && tile.y() + tile.height() <= box.y() + box.height(), "tile inside panel");
        }
        check(layout.hit(layout.close().x() + 3, layout.close().y() + 3) == -1, "close action never targets a slot");
    }
    private static void check(boolean ok, String message) { assertions++; if (!ok) throw new AssertionError(message); }
}
