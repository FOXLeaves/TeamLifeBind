import com.teamlifebind.common.LandmarkStructures;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Actual common blueprint geometry rendered as technical diagrams, not Minecraft screenshots. */
public final class ExportLandmarks {
    private static final int WIDTH = 2200;
    private static final int HEIGHT = 1400;
    private static final Font UI = new Font("Microsoft YaHei", Font.PLAIN, 24);
    private static final Color INK = new Color(228, 237, 246);
    private static final Color MUTED = new Color(140, 159, 179);
    private static final Color GOLD = new Color(255, 197, 93);
    private static final Color CYAN = new Color(93, 219, 231);
    private static final Map<LandmarkStructures.Kind, String> TITLES = Map.of(
            LandmarkStructures.Kind.FORTRESS, "边境要塞", LandmarkStructures.Kind.TOWER, "星辉高塔", LandmarkStructures.Kind.DEPOT, "远行补给仓");

    private ExportLandmarks() { }

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args.length == 0 ? "docs/images" : args[0]);
        Files.createDirectories(directory);
        List<BufferedImage> rendered = new ArrayList<>();
        for (LandmarkStructures.Blueprint blueprint : LandmarkStructures.all()) {
            BufferedImage image = render(blueprint);
            Path output = directory.resolve("landmark-" + blueprint.kind().name().toLowerCase(Locale.ROOT) + ".png");
            ImageIO.write(image, "PNG", output.toFile());
            rendered.add(image);
            System.out.println(output + ": " + blueprint.blocks().size() + " real blueprint positions, " + blueprint.lootPoints().size() + " loot points");
        }
        BufferedImage overview = new BufferedImage(WIDTH, 1470, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(overview);
        background(g, WIDTH, overview.getHeight());
        text(g, "新增探索建筑 · 蓝图实测总览", 70, 88, 46, INK);
        text(g, "取自 Common 实际方块与战利品坐标；程序投影示意，不是游戏截图", 72, 135, 23, MUTED);
        for (int i = 0; i < rendered.size(); i++) {
            // Use the actual isometric region of each detailed sheet, never a hand-drawn replacement.
            BufferedImage detail = rendered.get(i);
            g.drawImage(detail, 40 + i * 720, 185, 40 + i * 720 + 700, 1045, 0, 0, 1300, 1250, null);
        }
        int y = 1130;
        for (int i = 0; i < LandmarkStructures.all().size(); i++) {
            LandmarkStructures.Blueprint b = LandmarkStructures.all().get(i);
            int x = 90 + i * 720;
            text(g, TITLES.get(b.kind()), x, y, 34, INK);
            text(g, b.width() + " × " + b.depth() + " 占地 · " + b.height() + " 格高", x, y + 52, 25, MUTED);
            text(g, "6 个分散物资点 · 徒步可达上层", x, y + 96, 25, GOLD);
        }
        text(g, "基础 / 军需 / 奥术 / 宝库    ·    普通建筑不产枪    ·    魔法书沿用自然箱稀有度", 72, 1360, 26, INK);
        g.dispose();
        ImageIO.write(overview, "PNG", directory.resolve("landmarks-overview.png").toFile());
    }

    private static BufferedImage render(LandmarkStructures.Blueprint blueprint) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(image);
        background(g, WIDTH, HEIGHT);
        text(g, TITLES.get(blueprint.kind()), 70, 90, 50, INK);
        text(g, blueprint.width() + " × " + blueprint.depth() + " 占地 / " + blueprint.height() + " 格高 / "
                + blueprint.blocks().size() + " 个最终位置（含清理空气）", 72, 140, 23, MUTED);
        g.setColor(new Color(47, 64, 81));
        g.drawLine(1300, 180, 1300, 1230);
        iso(g, blueprint, 70, 190, 1170, 990);
        text(g, "等距外观 · 东南视角", 90, 1205, 23, INK);
        text(g, "依照实际方块状态投影；灯、楼梯与栏杆使用简化几何", 90, 1242, 20, MUTED);
        int[] levels = switch (blueprint.kind()) {
            case FORTRESS -> new int[] {1, 8};
            case TOWER -> new int[] {1, 8, 15, 22, 29, 36};
            case DEPOT -> new int[] {1, 6};
        };
        text(g, "逐层通行与物资点", 1360, 215, 32, INK);
        int rows = (levels.length + 1) / 2;
        int mapSize = levels.length > 2 ? 300 : 340;
        int rowHeight = levels.length > 2 ? 325 : 460;
        int top = levels.length > 2 ? 270 : 340;
        for (int i = 0; i < levels.length; i++) plan(g, blueprint, levels[i], 1355 + (i % 2) * 405, top + (i / 2) * rowHeight, mapSize);
        int legendY = levels.length > 2 ? 1290 : 940;
        text(g, "数字 = 实际桶坐标；箭头 = 楼梯上行；青色 = 入口", 1358, legendY, 19, MUTED);
        text(g, "深色留空为无楼板 / 平台外部，亮灰为剖切墙体", 1358, legendY + 32, 19, MUTED);
        if (rows == 1) {
            int y = legendY + 112;
            for (int i = 0; i < blueprint.lootPoints().size(); i++) {
                LandmarkStructures.LootPoint p = blueprint.lootPoints().get(i);
                String tier = switch (p.tier()) { case BASIC -> "基础"; case MILITARY -> "军需"; case ARCANE -> "奥术"; case TREASURE -> "宝库"; };
                text(g, (i + 1) + "  " + tier + "  ·  (" + p.x() + ", " + p.y() + ", " + p.z() + ")", 1360 + (i % 2) * 405, y + (i / 2) * 43, 22, INK);
            }
        }
        text(g, "SOURCE  LandmarkStructures.blueprint(" + blueprint.kind() + ")    ·    坐标以建筑基础层 y=0 为准", 72, 1348, 20, MUTED);
        g.dispose();
        return image;
    }

    private static void iso(Graphics2D g, LandmarkStructures.Blueprint blueprint, int left, int top, int width, int height) {
        Map<Pos, LandmarkStructures.BlockSpec> index = index(blueprint);
        double spanX = (blueprint.width() + blueprint.depth()) * .8660254;
        double spanY = (blueprint.width() + blueprint.depth()) * .5 + blueprint.height();
        double scale = Math.min((width - 45) / spanX, (height - 80) / spanY);
        Projection projection = new Projection(left + width * .5 - (blueprint.width() - blueprint.depth()) * .4330127 * scale,
                top + height - 45 - (blueprint.width() + blueprint.depth()) * .5 * scale, scale);
        List<Face> faces = new ArrayList<>();
        for (LandmarkStructures.Block block : blueprint.blocks()) {
            LandmarkStructures.BlockSpec spec = block.spec();
            if (spec.id().equals("minecraft:air")) continue;
            int x = block.x(), y = block.y(), z = block.z();
            Color color = material(spec.id());
            if (spec.id().endsWith("_stairs")) {
                box(faces, x, y, z, x + 1, y + .5, z + 1, color, true, true, true);
                switch (spec.properties().getOrDefault("facing", "south")) {
                    case "south" -> box(faces, x, y + .5, z + .5, x + 1, y + 1, z + 1, color, true, true, true);
                    case "north" -> box(faces, x, y + .5, z, x + 1, y + 1, z + .5, color, true, true, true);
                    case "east" -> box(faces, x + .5, y + .5, z, x + 1, y + 1, z + 1, color, true, true, true);
                    default -> box(faces, x, y + .5, z, x + .5, y + 1, z + 1, color, true, true, true);
                }
            } else if (spec.id().equals("minecraft:iron_chain") || spec.id().equals("minecraft:lightning_rod")) {
                box(faces, x + .43, y, z + .43, x + .57, y + 1, z + .57, color, true, true, true);
            } else if (spec.id().equals("minecraft:lantern")) {
                box(faces, x + .25, y + .12, z + .25, x + .75, y + .75, z + .75, color, true, true, true);
            } else if (spec.id().equals("minecraft:oak_fence")) {
                box(faces, x + .375, y, z + .375, x + .625, y + 1.5, z + .625, color, true, true, true);
                for (double rail : new double[] {.45, 1.0}) box(faces, x, y + rail, z + .43, x + 1, y + rail + .2, z + .57, color, true, true, true);
            } else {
                box(faces, x, y, z, x + 1, y + (spec.id().equals("minecraft:water") ? .9 : 1), z + 1, color,
                        !opaque(index.get(new Pos(x, y + 1, z))), !opaque(index.get(new Pos(x + 1, y, z))), !opaque(index.get(new Pos(x, y, z + 1))));
            }
        }
        faces.sort(Comparator.comparingDouble(Face::depth));
        for (Face face : faces) {
            Path2D polygon = new Path2D.Double();
            for (int i = 0; i < face.points.length; i++) {
                double[] p = projection.point(face.points[i][0], face.points[i][1], face.points[i][2]);
                if (i == 0) polygon.moveTo(p[0], p[1]); else polygon.lineTo(p[0], p[1]);
            }
            polygon.closePath();
            g.setColor(face.color); g.fill(polygon);
            g.setColor(new Color(7, 13, 20, 60)); g.setStroke(new BasicStroke(.45F)); g.draw(polygon);
        }
    }

    private static void plan(Graphics2D g, LandmarkStructures.Blueprint b, int level, int left, int top, int size) {
        Map<Pos, LandmarkStructures.BlockSpec> blocks = index(b);
        double cell = Math.min(size / (double) b.width(), size / (double) b.depth());
        int mapHeight = (int) Math.ceil(b.depth() * cell);
        text(g, "行走层 y=" + level, left, top - 18, 22, INK);
        for (int z = 0; z < b.depth(); z++) for (int x = 0; x < b.width(); x++) {
            LandmarkStructures.BlockSpec feet = blocks.get(new Pos(x, level, z));
            LandmarkStructures.BlockSpec floor = blocks.get(new Pos(x, level - 1, z));
            Color color = opaque(feet) ? new Color(151, 166, 179) : floor != null && !floor.id().equals("minecraft:air") ? shade(material(floor.id()), .75) : new Color(13, 22, 31);
            if (feet != null && feet.id().equals("minecraft:water")) color = material(feet.id());
            g.setColor(color);
            int px = left + (int) Math.round(x * cell), pz = top + (int) Math.round(z * cell);
            int nextX = left + (int) Math.round((x + 1) * cell), nextZ = top + (int) Math.round((z + 1) * cell);
            g.fillRect(px, pz, nextX - px, nextZ - pz);
            if (feet != null && feet.id().endsWith("_stairs")) { g.setColor(GOLD); g.fillRect(px + 1, pz + 1, Math.max(1, nextX - px - 2), Math.max(1, nextZ - pz - 2)); }
            // Stair runs are projected onto every intersected walking layer, including rising steps.
            for (int y = level; y <= Math.min(level + 6, b.height() - 1); y++) {
                LandmarkStructures.BlockSpec stair = blocks.get(new Pos(x, y, z));
                if (stair != null && stair.id().endsWith("_stairs")) {
                    g.setColor(GOLD); g.setStroke(new BasicStroke(1.4F));
                    g.drawRect(px + 1, pz + 1, Math.max(1, nextX - px - 2), Math.max(1, nextZ - pz - 2));
                }
            }
        }
        g.setColor(new Color(75, 96, 117)); g.setStroke(new BasicStroke(1)); g.drawRect(left, top, (int) Math.round(b.width() * cell), mapHeight);
        for (int i = 0; i < b.lootPoints().size(); i++) {
            LandmarkStructures.LootPoint loot = b.lootPoints().get(i);
            if (loot.y() != level) continue;
            int px = left + (int) Math.round((loot.x() + .5) * cell), pz = top + (int) Math.round((loot.z() + .5) * cell);
            g.setColor(new Color(11, 18, 27)); g.fillOval(px - 13, pz - 13, 26, 26);
            g.setColor(GOLD); g.setStroke(new BasicStroke(2)); g.drawOval(px - 13, pz - 13, 26, 26);
            g.setFont(UI.deriveFont(Font.BOLD, 18F)); String label = Integer.toString(i + 1);
            g.drawString(label, px - g.getFontMetrics().stringWidth(label) / 2, pz + 7);
        }
        if (level == 1) {
            int entrance = switch (b.kind()) { case FORTRESS -> 22; case TOWER -> 16; case DEPOT -> 12; };
            int px = left + (int) Math.round((entrance + .5) * cell);
            g.setColor(CYAN); g.fillPolygon(new int[] {px - 9, px + 9, px}, new int[] {top - 12, top - 12, top + 3}, 3);
        }
    }

    private static void box(List<Face> output, double x1, double y1, double z1, double x2, double y2, double z2, Color c, boolean top, boolean east, boolean south) {
        if (top) output.add(face(new double[][] {{x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}}, shade(c, 1.15)));
        if (east) output.add(face(new double[][] {{x2, y1, z1}, {x2, y1, z2}, {x2, y2, z2}, {x2, y2, z1}}, shade(c, .82)));
        if (south) output.add(face(new double[][] {{x1, y1, z2}, {x2, y1, z2}, {x2, y2, z2}, {x1, y2, z2}}, shade(c, .64)));
    }
    private static Face face(double[][] points, Color color) {
        double depth = 0;
        for (double[] p : points) depth += p[0] + p[1] + p[2];
        return new Face(points, color, depth / points.length);
    }
    private record Face(double[][] points, Color color, double depth) { }
    private record Projection(double x, double y, double scale) {
        double[] point(double px, double py, double pz) { return new double[] {x + (px - pz) * .8660254 * scale, y + ((px + pz) * .5 - py) * scale}; }
    }
    private record Pos(int x, int y, int z) { }
    private static Map<Pos, LandmarkStructures.BlockSpec> index(LandmarkStructures.Blueprint b) {
        Map<Pos, LandmarkStructures.BlockSpec> result = new HashMap<>();
        for (LandmarkStructures.Block block : b.blocks()) result.put(new Pos(block.x(), block.y(), block.z()), block.spec());
        return result;
    }
    private static boolean opaque(LandmarkStructures.BlockSpec block) {
        if (block == null) return false;
        String id = block.id();
        return !id.equals("minecraft:air") && !id.equals("minecraft:water") && !id.contains("glass") && !id.endsWith("_stairs")
                && !id.endsWith("_fence") && !id.endsWith("_chain") && !id.endsWith("lantern") && !id.endsWith("lightning_rod");
    }
    private static Color material(String id) {
        if (id.contains("barrel")) return new Color(234, 171, 79);
        if (id.contains("lantern")) return new Color(255, 212, 114);
        if (id.contains("iron_chain")) return new Color(104, 119, 139);
        if (id.contains("lightning_rod")) return new Color(204, 130, 77);
        if (id.contains("amethyst")) return new Color(164, 118, 225);
        if (id.contains("glass")) return new Color(101, 192, 215, 160);
        if (id.contains("water")) return new Color(61, 143, 196, 210);
        if (id.contains("deepslate")) return new Color(88, 105, 127);
        if (id.contains("mossy")) return new Color(111, 133, 106);
        if (id.contains("stripped_oak_log")) return new Color(172, 134, 86);
        if (id.contains("spruce")) return new Color(139, 102, 66);
        if (id.contains("oak") || id.contains("crafting")) return new Color(179, 142, 91);
        if (id.contains("furnace")) return new Color(118, 125, 130);
        return new Color(148, 164, 176);
    }
    private static Color shade(Color color, double scale) { return new Color((int) Math.min(255, color.getRed() * scale), (int) Math.min(255, color.getGreen() * scale), (int) Math.min(255, color.getBlue() * scale), color.getAlpha()); }
    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }
    private static void background(Graphics2D g, int width, int height) {
        g.setPaint(new GradientPaint(0, 0, new Color(23, 37, 53), width, height, new Color(8, 16, 25)));
        g.fillRect(0, 0, width, height);
    }
    private static void text(Graphics2D g, String value, int x, int y, int size, Color color) { g.setFont(UI.deriveFont((float) size)); g.setColor(color); g.drawString(value, x, y); }
}
