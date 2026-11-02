import com.teamlifebind.common.NativeHud;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

/** Exports the production NativeHud layout through its public Canvas; these are layout previews, not game screenshots. */
public final class ExportNativeHud {
    private static final Font[] HUD_FONTS = {
        new Font("Segoe UI", Font.PLAIN, 9),
        new Font("Microsoft YaHei", Font.PLAIN, 9),
        new Font(Font.DIALOG, Font.PLAIN, 9)
    };
    private static final Font LABEL_FONT = new Font("Microsoft YaHei", Font.PLAIN, 15);
    private static final Font TITLE_FONT = new Font("Microsoft YaHei", Font.BOLD, 23);
    private static final Path SCENES = Path.of(".tools", "native-hud-preview");
    private static final List<NativeHud.Slot> SLOTS = List.of(
        new NativeHud.Slot("霜境·绝对零度永恒禁咒与群星终末之诗", "34.5s", 0xFF7ECDE9, 8, 95, 690, 960, true, false),
        new NativeHud.Slot("星河坠落·天穹大崩解", "魔力不足", 0xFFAE9DE4, 8, 120, 0, 0, true, false),
        new NativeHud.Slot("流风回响", "就绪", 0xFF8BD8B3, 3, 20, 0, 0, true, true)
    );
    private static final NativeHud.Magic MAGIC = new NativeHud.Magic("魔法", "魔力", "V 施法   B 魔法轮盘", "吟唱中", 44, 160, 1, SLOTS, 0, 0);
    private static final List<NativeHud.Line> SIDEBAR = List.of(
        new NativeHud.Line("快速节奏 · 魔法开局", NativeHud.GOLD),
        new NativeHud.Line("阶段  第三轮 · 缩圈中", NativeHud.TEXT),
        new NativeHud.Line("存活  12 / 24", NativeHud.TEXT),
        new NativeHud.Line("队伍  星辉小队", NativeHud.CYAN),
        new NativeHud.Line("生命绑定  3 / 4", NativeHud.TEXT),
        new NativeHud.Line("空投  01:28", NativeHud.GOLD),
        new NativeHud.Line("边境  640 → 480", NativeHud.MUTED),
        new NativeHud.Line("队友  同一世界 · 86m", NativeHud.MUTED)
    );
    private static int rectangles;
    private static int texts;
    private static int textComparisons;
    private static int combatChecks;

    private record DrawnText(String text, Rectangle bounds) { }
    private record Scene(String id, String title, String subtitle, int width, int height, BufferedImage image) { }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(SCENES);
        List<Scene> scenes = new ArrayList<>();
        scenes.add(scene("magic", "魔法 HUD / 640 × 360", "长名称压力样例 · 独立冷却列 · 魔力不足", 640, 360, canvas -> {
            NativeHud.magic(canvas, 640, 360, MAGIC);
            NativeHud.sidebar(canvas, 640, 360, "TEAM LIFE BIND", SIDEBAR);
        }));
        scenes.add(scene("weapon-reload", "枪械 HUD / 640 × 360", "服务端确认弹量 · 连续换弹进度 · 无限备弹", 640, 360, canvas -> {
            NativeHud.weapon(canvas, 640, 360, new NativeHud.Weapon("Vector · 战术冲锋枪", "AUTO", "换弹 1.7s", "R 换弹  ·  鼠标右键 瞄准", "无限备弹", "热量", 7, 20, 34, 51, 0, false, false, true));
            NativeHud.sidebar(canvas, 640, 360, "TEAM LIFE BIND", SIDEBAR);
        }));
        scenes.add(scene("weapon-heat", "机枪 HUD / 640 × 360", "无限供弹 · 热量条 · 过热状态", 640, 360, canvas -> {
            NativeHud.weapon(canvas, 640, 360, new NativeHud.Weapon("Minigun · 支援机枪", "AUTO", "武器过热", "等待冷却", "无限供弹", "核心热量", 120, 120, 0, 0, 92, true, true, true));
            NativeHud.sidebar(canvas, 640, 360, "TEAM LIFE BIND", SIDEBAR);
        }));
        scenes.add(scene("wheel", "魔法轮盘 / 640 × 360", "三种法术 · 学派色彩 · 魔力与冷却状态", 640, 360, canvas ->
            NativeHud.wheel(canvas, 640, 360, MAGIC, 2, "选择魔法", "松开 B 确认选择")));
        scenes.add(scene("compact-hud", "紧凑 HUD / 320 × 180", "以下画面以 2× 展示 · 使用真实最小 GUI 布局", 320, 180, canvas -> {
            NativeHud.magic(canvas, 320, 180, MAGIC);
            NativeHud.sidebar(canvas, 320, 180, "TEAM LIFE BIND", SIDEBAR, false, "更多 · 按 TAB 查看");
        }));
        scenes.add(scene("compact-wheel", "紧凑轮盘 / 320 × 180", "以下画面以 2× 展示 · 卡片均在画面边界内", 320, 180, canvas ->
            NativeHud.wheel(canvas, 320, 180, MAGIC, 1, "选择魔法", "松开 B 确认选择")));
        for (int[] size : new int[][] {{320, 180}, {400, 240}, {640, 360}, {960, 540}, {1280, 720}}) {
            validateWheelCards(size[0], size[1]);
            validateCombatArea(size[0], size[1], size[1] < 260 ? 90 : 112, "magic");
            validateCombatArea(size[0], size[1], 91, "weapon");
        }
        BufferedImage overview = new BufferedImage(1352, 1474, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = overview.createGraphics();
        configure(graphics);
        graphics.setColor(new Color(0x101722));
        graphics.fillRect(0, 0, overview.getWidth(), overview.getHeight());
        graphics.setFont(TITLE_FONT);
        graphics.setColor(new Color(0xE7EEF5));
        graphics.drawString("模组端 HUD · 真实布局预览", 24, 38);
        graphics.setFont(LABEL_FONT);
        graphics.setColor(new Color(0x9AADBF));
        graphics.drawString("调用 NativeHud 公共 Canvas 绘制；名称含压力样例，背景与原版物品栏为占位示意，不是游戏截图。", 25, 66);
        for (int index = 0; index < scenes.size(); index++) {
            Scene scene = scenes.get(index);
            int x = 24 + index % 2 * 664;
            int y = 99 + index / 2 * 451;
            graphics.setFont(LABEL_FONT.deriveFont(Font.BOLD, 16));
            graphics.setColor(new Color(0xE7EEF5));
            graphics.drawString(scene.title, x, y);
            graphics.setFont(LABEL_FONT.deriveFont(13F));
            graphics.setColor(new Color(0x9AADBF));
            graphics.drawString(scene.subtitle, x, y + 23);
            graphics.drawImage(scene.image, x, y + 38, 640, 360, null);
        }
        graphics.dispose();
        Path output = Path.of("docs", "images", "native-hud-preview.png");
        Files.createDirectories(output.getParent());
        ImageIO.write(overview, "PNG", output.toFile());
        System.out.printf("Exported %d scenes; validated %d rectangles, %d text draws, %d text pairs and %d combat exclusion checks. Preview: %s%n", scenes.size(), rectangles, texts, textComparisons, combatChecks, output);
    }

    private static Scene scene(String id, String title, String subtitle, int width, int height, Consumer<AwtCanvas> draw) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        configure(graphics);
        background(graphics, width, height);
        AwtCanvas canvas = new AwtCanvas(graphics, width, height, id);
        draw.accept(canvas);
        canvas.validateTextColumns();
        graphics.dispose();
        ImageIO.write(image, "PNG", SCENES.resolve(id + ".png").toFile());
        return new Scene(id, title, subtitle, width, height, image);
    }

    private static void configure(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    }

    private static void background(Graphics2D graphics, int width, int height) {
        graphics.setColor(new Color(0x778D9B));
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(new Color(0xB3BDC0));
        graphics.fillRect(width / 3, height / 5, width / 5, 12);
        graphics.fillRect(width * 3 / 5, height / 4, width / 4, 9);
        graphics.setColor(new Color(0x536A69));
        graphics.fillRect(0, height * 5 / 9, width, height);
        graphics.setColor(new Color(0x65786D));
        graphics.fillRect(width / 5, height / 2, width / 4, height / 8);
        graphics.setColor(new Color(0x405D58));
        graphics.fillRect(width * 4 / 5, height / 2 - 18, 28, height / 4);
        graphics.setColor(new Color(0x6B7476));
        graphics.fillRect(width * 3 / 5, height / 2 - 35, width / 7, height / 3);
        graphics.setColor(new Color(0x505E62));
        graphics.fillRect(width * 3 / 5 + 8, height / 2 - 23, 10, 18);
        graphics.fillRect(width * 3 / 5 + width / 7 - 19, height / 2 - 23, 10, 18);
        graphics.setColor(new Color(0x475B54));
        for (int row = 0; row < 4; row++) for (int column = 0; column < 9; column++) {
            if ((row + column) % 3 == 0) graphics.fillRect(column * (width / 8), height * 2 / 3 + row * 18, width / 12, 8);
        }
        // Vanilla hotbar and status placeholders retain their native GUI dimensions.
        int left = width / 2 - 91;
        graphics.setColor(new Color(0xC0171C21, true));
        graphics.fillRect(left, height - 24, 182, 22);
        for (int index = 0; index < 9; index++) {
            graphics.setColor(new Color(index == 2 ? 0xB6C1CB : 0x68717A));
            graphics.drawRect(left + 2 + index * 20, height - 22, 18, 18);
            graphics.setColor(new Color(index == 2 ? 0x8F718F : 0x7F8980));
            graphics.fillRect(left + 8 + index * 20, height - 17, 7, 9);
        }
        // Vanilla's offhand slot spans centre-120 .. centre-98, farther left than the main hotbar.
        int offhandLeft = width / 2 - 120;
        graphics.setColor(new Color(0xC0171C21, true));
        graphics.fillRect(offhandLeft, height - 24, 22, 22);
        graphics.setColor(new Color(0x87949F));
        graphics.drawRect(offhandLeft, height - 24, 21, 21);
        graphics.setColor(new Color(0x8A9298));
        graphics.fillRect(offhandLeft + 6, height - 20, 10, 12);
        graphics.setColor(new Color(0x5D6B75));
        graphics.fillRect(offhandLeft + 10, height - 20, 2, 12);
        graphics.setColor(new Color(0x656C58));
        graphics.fillRect(left, height - 27, 182, 2);
        graphics.setColor(new Color(0xA5AF66));
        graphics.fillRect(left, height - 27, 103, 2);
        for (int index = 0; index < 10; index++) {
            graphics.setColor(new Color(0xA35F67));
            graphics.fillRect(left + index * 8, height - 39, 6, 6);
            graphics.setColor(new Color(0xAD9376));
            graphics.fillRect(left + 104 + index * 8, height - 39, 6, 6);
        }
        graphics.setColor(new Color(0xD7DFE1));
        graphics.fillRect(width / 2 - 3, height / 2, 7, 1);
        graphics.fillRect(width / 2, height / 2 - 3, 1, 7);
    }

    private static void validateWheelCards(int width, int height) {
        for (NativeHud.Rect card : NativeHud.wheelBounds(width, height)) {
            if (card.x() < 0 || card.y() < 0 || card.x() + card.width() > width || card.y() + card.height() > height)
                throw new AssertionError("Wheel card outside " + width + "x" + height + ": " + card);
        }
    }

    private static void validateCombatArea(int width, int height, int panelHeight, String kind) {
        NativeHud.Rect box = NativeHud.combatBounds(width, height, panelHeight);
        Rectangle panel = new Rectangle(box.x(), box.y(), box.width(), box.height());
        Rectangle centre = new Rectangle(width / 2 - 94, height - 82, 188, 82);
        // Six GUI pixels of left clearance include the real offhand slot plus panel shadow.
        Rectangle offhand = new Rectangle(width / 2 - 126, height - 26, 34, 26);
        for (Rectangle protectedArea : List.of(centre, offhand)) {
            combatChecks++;
            if (panel.intersects(protectedArea)) throw new AssertionError(kind + " panel overlaps protected vanilla HUD in " + width + "x" + height + ": " + panel + " versus " + protectedArea);
        }
    }

    private static final class AwtCanvas implements NativeHud.Canvas {
        private final Graphics2D graphics;
        private final FontRenderContext context;
        private final int width;
        private final int height;
        private final String scene;
        private final List<DrawnText> drawnTexts = new ArrayList<>();

        private AwtCanvas(Graphics2D graphics, int width, int height, String scene) {
            this.graphics = graphics;
            this.context = graphics.getFontRenderContext();
            this.width = width;
            this.height = height;
            this.scene = scene;
        }

        private TextLayout layout(String text) {
            AttributedString attributed = new AttributedString(text);
            for (int offset = 0; offset < text.length();) {
                int codePoint = text.codePointAt(offset);
                int next = offset + Character.charCount(codePoint);
                Font font = HUD_FONTS[HUD_FONTS.length - 1];
                for (Font candidate : HUD_FONTS) if (candidate.canDisplay(codePoint)) { font = candidate; break; }
                attributed.addAttribute(TextAttribute.FONT, font, offset, next);
                offset = next;
            }
            return new TextLayout(attributed.getIterator(), context);
        }

        @Override public int width(String text) { return text == null || text.isEmpty() ? 0 : (int) Math.ceil(layout(text).getAdvance()); }

        @Override public void rect(int x, int y, int width, int height, int color) {
            if (width < 0 || height < 0) throw new AssertionError(scene + ": negative rectangle " + new Rectangle(x, y, width, height));
            // Production panels use a shadow offset of (+2,+3).
            checkBounds(new Rectangle(x, y, width, height), 3, "rectangle");
            rectangles++;
            graphics.setColor(new Color(color, true));
            graphics.fillRect(x, y, width, height);
        }

        @Override public void text(String text, int x, int y, int color, float scale) {
            if (text == null || text.isEmpty()) return;
            if (!Float.isFinite(scale) || scale <= 0) throw new AssertionError(scene + ": invalid text scale " + scale);
            TextLayout layout = layout(text);
            Rectangle pixel = layout.getPixelBounds(context, 0, 9);
            Rectangle bounds = new Rectangle(x + (int) Math.floor(pixel.x * scale), y + (int) Math.floor(pixel.y * scale),
                (int) Math.ceil(pixel.width * scale), (int) Math.ceil(pixel.height * scale));
            checkBounds(bounds, 0, "text '" + text + "'");
            drawnTexts.add(new DrawnText(text, bounds));
            texts++;
            Graphics2D layer = (Graphics2D) graphics.create();
            layer.translate(x, y);
            layer.scale(scale, scale);
            layer.setColor(new Color(color, true));
            layout.draw(layer, 0, 9);
            layer.dispose();
        }

        private void checkBounds(Rectangle box, int allowance, String kind) {
            if (box.x < -allowance || box.y < -allowance || box.x + box.width > width + allowance || box.y + box.height > height + allowance)
                throw new AssertionError(scene + ": " + kind + " outside " + width + "x" + height + ": " + box);
        }

        private void validateTextColumns() {
            for (int first = 0; first < drawnTexts.size(); first++) for (int second = first + 1; second < drawnTexts.size(); second++) {
                DrawnText a = drawnTexts.get(first), b = drawnTexts.get(second);
                textComparisons++;
                Rectangle2D intersection = a.bounds.createIntersection(b.bounds);
                if (intersection.getWidth() > 0 && intersection.getHeight() > 0)
                    throw new AssertionError(scene + ": text overlaps: '" + a.text + "' " + a.bounds + " with '" + b.text + "' " + b.bounds);
            }
        }
    }
}
