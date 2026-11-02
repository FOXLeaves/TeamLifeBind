package com.teamlifebind.common;

import java.util.List;

/** One layout and palette for all native clients. Canvas measures the actual client font. */
public final class NativeHud {
    public static final int TEXT = 0xFFE7EEF5;
    public static final int MUTED = 0xFF9AADBF;
    public static final int QUIET = 0xFF687C91;
    public static final int CYAN = 0xFF77D8EC;
    public static final int GOLD = 0xFFE9C183;
    public static final int WARNING = 0xFFFFA27B;
    public static final int PANEL = 0xE0101926;
    private NativeHud() { }

    public interface Canvas {
        int width(String text);
        void rect(int x, int y, int width, int height, int color);
        void text(String text, int x, int y, int color, float scale);
        default void text(String text, int x, int y, int color) { text(text, x, y, color, 1); }
    }

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double px, double py) { return px >= x && px < x + width && py >= y && py < y + height; }
    }
    public record Line(String text, int color) { }
    public record Slot(String name, String state, int color, int tier, int manaCost,
                       int cooldown, int cooldownTotal, boolean learned, boolean affordable) { }
    public record Magic(String title, String manaLabel, String footer, String castLabel,
                        double mana, int maximum, int selected, List<Slot> slots, int castRemaining, int castTotal) {
        public Magic { slots = List.copyOf(slots); }
    }
    public record Weapon(String name, String mode, String state, String hint, String reserve, String heatLabel,
                         int ammo, int capacity, int reloadRemaining, int reloadTotal, double heat,
                         boolean infinite, boolean warning, boolean confirmed) { }

    /** Keep the full-width bottom centre free for vanilla health, armour, air and the hotbar. */
    public static Rect combatBounds(int width, int height, int panelHeight) {
        int margin = width < 400 ? 6 : 10;
        int panelWidth = Math.max(96, Math.min(184, width / 2 - margin * 2));
        // The offhand slot extends farther left than the nine-slot hotbar itself.
        boolean sideOfHotbar = width / 2 - 126 >= margin + panelWidth;
        int top = sideOfHotbar ? height - margin - panelHeight : Math.min(55, Math.max(margin, height - 82 - panelHeight));
        return new Rect(margin, Math.max(margin, top), panelWidth, panelHeight);
    }

    public static String fit(Canvas canvas, String text, int width) {
        if (text == null || width <= 0) return "";
        if (canvas.width(text) <= width) return text;
        String ellipsis = "…";
        if (canvas.width(ellipsis) > width) return "";
        int count = text.codePointCount(0, text.length());
        int low = 0, high = count;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (canvas.width(text.substring(0, text.offsetByCodePoints(0, middle)) + ellipsis) <= width) low = middle;
            else high = middle - 1;
        }
        return text.substring(0, text.offsetByCodePoints(0, low)) + ellipsis;
    }

    public static void magic(Canvas canvas, int width, int height, Magic magic) {
        boolean compact = height < 260;
        Rect box = combatBounds(width, height, compact ? 90 : 112);
        panel(canvas, box, CYAN);
        int left = box.x + 9, right = box.x + box.width - 9, top = box.y;
        canvas.text(fit(canvas, magic.manaLabel, Math.max(0, box.width - 99)), left, top + 9, MUTED);
        rightText(canvas, Math.max(0, Math.round(magic.mana)) + " / " + Math.max(1, magic.maximum), right, top + 9, TEXT);
        bar(canvas, left, top + 23, right - left, 5, ratio(magic.mana, magic.maximum), CYAN);
        for (int index = 0; index < 3; index++) {
            Slot slot = index < magic.slots.size() ? magic.slots.get(index) : null;
            int y = top + (compact ? 30 : 35) + index * (compact ? 14 : 19);
            boolean chosen = index == magic.selected;
            int accent = slot == null ? QUIET : readable(slot.color);
            if (chosen) {
                canvas.rect(box.x + 5, y - 3, box.width - 10, compact ? 14 : 18, 0x243B6179);
                canvas.rect(box.x + 5, y - 3, 2, compact ? 14 : 18, accent);
            }
            canvas.rect(left + 1, y - 1, 12, 12, chosen ? 0xFF283D51 : 0xFF1B2A3B);
            canvas.text(Integer.toString(index + 1), left + 4, y + 1, chosen ? TEXT : QUIET);
            String state = slot == null ? "" : slot.state;
            int stateWidth = Math.min(canvas.width(state), (right - left) / 3);
            rightText(canvas, fit(canvas, state, stateWidth), right, y + 1,
                slot != null && !slot.affordable && slot.cooldown == 0 ? WARNING : slot != null && slot.cooldown > 0 ? GOLD : MUTED);
            String name = slot == null ? "—" : slot.name;
            canvas.text(fit(canvas, name, right - left - 25 - stateWidth), left + 21, y + 1,
                slot == null || !slot.learned ? QUIET : chosen ? TEXT : MUTED);
            if (slot != null && slot.cooldown > 0) {
                bar(canvas, left + 21, y + (compact ? 11 : 13), right - left - 21, 1,
                    1 - ratio(slot.cooldown, slot.cooldownTotal), accent);
            }
        }
        canvas.rect(left, top + (compact ? 73 : 94), right - left, 1, 0x244F6B86);
        String footer = magic.castRemaining > 0 ? magic.castLabel + "  " + seconds(magic.castRemaining) : magic.footer;
        canvas.text(fit(canvas, footer, right - left), left, top + (compact ? 79 : 100), magic.castRemaining > 0 ? GOLD : MUTED);
        if (magic.castRemaining > 0) bar(canvas, box.x + 2, top + box.height - 2, box.width - 4, 2,
            1 - ratio(magic.castRemaining, magic.castTotal), GOLD);
    }

    public static void weapon(Canvas canvas, int width, int height, Weapon weapon) {
        Rect box = combatBounds(width, height, 91);
        int accent = weapon.warning ? WARNING : GOLD;
        panel(canvas, box, accent);
        int left = box.x + 9, right = box.x + box.width - 9, top = box.y;
        int modeWidth = Math.min(canvas.width(weapon.mode), 42);
        canvas.text(fit(canvas, weapon.name, right - left - modeWidth - 8), left, top + 9, TEXT);
        rightText(canvas, fit(canvas, weapon.mode, modeWidth), right, top + 10, MUTED);
        String ammo = !weapon.confirmed ? "--" : weapon.infinite ? "∞" : Integer.toString(Math.max(0, weapon.ammo));
        canvas.text(ammo, left, top + 29, accent, 2);
        int afterAmmo = left + canvas.width(ammo) * 2 + 7;
        if (!weapon.infinite) canvas.text("/ " + Math.max(1, weapon.capacity), afterAmmo, top + 38, MUTED);
        canvas.text(fit(canvas, weapon.reserve, right - left), left, top + 53, QUIET);
        rightText(canvas, fit(canvas, weapon.state, Math.max(0, right - afterAmmo - 8)), right, top + 29,
            weapon.warning ? WARNING : TEXT);
        if (weapon.infinite) {
            bar(canvas, left, top + 68, right - left, 4, ratio(weapon.heat, GunCombatState.MAX_HEAT), accent);
            canvas.text(fit(canvas, weapon.heatLabel, right - left - 30), left, top + 78, MUTED);
            rightText(canvas, Math.round(Math.clamp(weapon.heat, 0, GunCombatState.MAX_HEAT)) + "%", right, top + 78, accent);
        } else {
            double progress = weapon.reloadRemaining > 0 ? 1 - ratio(weapon.reloadRemaining, weapon.reloadTotal) : ratio(weapon.ammo, weapon.capacity);
            bar(canvas, left, top + 68, right - left, 3, weapon.confirmed ? progress : 0, accent);
            canvas.text(fit(canvas, weapon.hint, right - left), left, top + 78, MUTED);
        }
    }

    /** Small GUI scales can temporarily expand the complete list with the rebound player-list key. */
    public static Rect sidebar(Canvas canvas, int width, int height, String title, List<Line> lines) {
        return sidebar(canvas, width, height, title, lines, false, "…");
    }

    public static Rect sidebar(Canvas canvas, int width, int height, String title, List<Line> lines, boolean expanded, String moreLabel) {
        int margin = width < 400 ? 6 : 10;
        int maxWidth = Math.max(96, Math.min(202, width / 2 - margin * 2));
        int natural = Math.max(118, canvas.width(title) + 26);
        for (Line line : lines) natural = Math.max(natural, canvas.width(line.text) + 22);
        int panelWidth = Math.min(maxWidth, natural);
        int total = Math.min(15, lines.size());
        int available = Math.max(60, height - margin - 82);
        int count = total;
        if (!expanded && 27 + count * 12 > available) count = Math.max(1, Math.min(total, (available - 41) / 12));
        boolean more = count < total;
        float scale = expanded && 27 + count * 12 > available ? 0.75F : 1;
        int rowHeight = scale == 1 ? 12 : 9;
        int panelHeight = 27 + count * rowHeight + (more ? 14 : 0);
        int top = Math.min(56, Math.max(margin, height - panelHeight - 82));
        Rect box = new Rect(width - margin - panelWidth, top, panelWidth, panelHeight);
        panel(canvas, box, GOLD);
        int left = box.x + 9;
        canvas.rect(left, top + 9, 3, 9, GOLD);
        canvas.text(fit(canvas, title, panelWidth - 31), left + 9, top + 9, TEXT);
        canvas.rect(left, top + 23, panelWidth - 18, 1, 0x304F6B86);
        for (int index = 0; index < count; index++) {
            Line line = lines.get(index);
            int y = top + 29 + index * rowHeight;
            if ((index & 1) == 1) canvas.rect(box.x + 5, y - 2, panelWidth - 10, rowHeight, 0x123C536A);
            canvas.text(fit(canvas, line.text, (int) ((panelWidth - 18) / scale)), left, y, readable(line.color), scale);
        }
        if (more) canvas.text(fit(canvas, "+" + (total - count) + "  " + moreLabel, panelWidth - 18), left, top + panelHeight - 10, GOLD);
        return box;
    }

    public static List<Rect> wheelBounds(int width, int height) {
        int margin = width < 400 ? 6 : 12;
        int cardWidth = Math.max(92, Math.min(176, (width - margin * 3) / 2));
        int cardHeight = height < 240 ? 40 : 46;
        int cx = width / 2, cy = height / 2;
        int radiusY = Math.min(82, Math.max(38, (int) (height * 0.28)));
        int radiusX = Math.min(112, (width - cardWidth - margin * 2) / 2);
        return List.of(new Rect(cx - cardWidth / 2, cy - radiusY - cardHeight / 2, cardWidth, cardHeight),
            new Rect(cx + radiusX - cardWidth / 2, cy + (int) (radiusY * 0.65) - cardHeight / 2, cardWidth, cardHeight),
            new Rect(cx - radiusX - cardWidth / 2, cy + (int) (radiusY * 0.65) - cardHeight / 2, cardWidth, cardHeight));
    }

    public static int wheelHover(int width, int height, double mouseX, double mouseY, int previous) {
        List<Rect> boxes = wheelBounds(width, height);
        for (int index = 0; index < boxes.size(); index++) if (boxes.get(index).contains(mouseX, mouseY)) return index;
        double dx = mouseX - width / 2.0, dy = mouseY - height / 2.0;
        if (Math.hypot(dx, dy) < 18) return previous;
        return Math.floorMod((int) Math.floor((Math.atan2(dy, dx) + Math.PI / 2 + Math.PI / 3 + Math.PI * 2) / (Math.PI * 2 / 3)), 3);
    }

    public static void wheel(Canvas canvas, int width, int height, Magic magic, int hovered, String title, String instruction) {
        canvas.rect(0, 0, width, height, 0xA00A101C);
        int cx = width / 2, cy = height / 2;
        centered(canvas, title, cx, Math.max(4, height / 2 - 136), TEXT);
        // Sparse, static reticle keeps the aim point readable while choosing a spell.
        canvas.rect(cx - 20, cy - 18, 8, 1, 0x805D829F);
        canvas.rect(cx + 12, cy - 18, 8, 1, 0x805D829F);
        canvas.rect(cx - 20, cy + 19, 8, 1, 0x805D829F);
        canvas.rect(cx + 12, cy + 19, 8, 1, 0x805D829F);
        centered(canvas, Math.max(0, Math.round(magic.mana)) + " / " + Math.max(1, magic.maximum), cx, cy - 10, CYAN);
        centered(canvas, magic.manaLabel, cx, cy + 4, MUTED);
        List<Rect> boxes = wheelBounds(width, height);
        for (int index = 0; index < 3; index++) {
            Rect box = boxes.get(index);
            Slot slot = index < magic.slots.size() ? magic.slots.get(index) : null;
            int accent = slot == null ? QUIET : readable(slot.color);
            panel(canvas, box, index == hovered ? accent : 0xFF3B4D62);
            if (index == hovered) canvas.rect(box.x + 2, box.y + 2, box.width - 4, box.height - 4, 0x242E6783);
            canvas.rect(box.x + 7, box.y + 9, 13, 13, 0xFF273A50);
            canvas.text(Integer.toString(index + 1), box.x + 11, box.y + 12, index == hovered ? TEXT : MUTED);
            canvas.text(fit(canvas, slot == null ? "—" : slot.name, box.width - 34), box.x + 27, box.y + 10,
                slot == null || !slot.learned ? QUIET : index == hovered ? TEXT : MUTED);
            String detail = slot == null || !slot.learned ? "—" : "T" + slot.tier + "  ·  " + slot.manaCost + " MP";
            canvas.text(fit(canvas, detail, box.width - 16), box.x + 8, box.y + box.height - 14, accent);
            if (slot != null && slot.learned) rightText(canvas, fit(canvas, slot.state, box.width / 3), box.x + box.width - 8,
                box.y + box.height - 14, slot.cooldown > 0 ? GOLD : slot.affordable ? MUTED : WARNING);
        }
        centered(canvas, fit(canvas, instruction, width - 16), cx, height - 15, MUTED);
    }

    private static void panel(Canvas canvas, Rect box, int accent) {
        canvas.rect(box.x + 2, box.y + 3, box.width, box.height, 0x40000000);
        canvas.rect(box.x + 1, box.y, box.width - 2, box.height, PANEL);
        canvas.rect(box.x, box.y + 1, box.width, box.height - 2, PANEL);
        canvas.rect(box.x + 2, box.y + 1, box.width - 4, 19, 0x301C3044);
        canvas.rect(box.x + 2, box.y, box.width - 4, 1, 0x60526D86);
        canvas.rect(box.x + 2, box.y, Math.min(28, box.width - 4), 1, accent);
        canvas.rect(box.x + box.width - 2, box.y + 3, 1, box.height - 6, 0x203D5975);
    }

    private static void bar(Canvas canvas, int x, int y, int width, int height, double amount, int color) {
        if (width <= 0 || height <= 0) return;
        canvas.rect(x, y, width, height, 0xFF253447);
        int filled = (int) Math.round(width * Math.clamp(Double.isFinite(amount) ? amount : 0, 0, 1));
        if (filled > 0) {
            canvas.rect(x, y, filled, height, color);
            if (height > 2) canvas.rect(x, y, filled, 1, 0x70FFFFFF);
        }
    }

    private static double ratio(double value, double maximum) { return Double.isFinite(value) && maximum > 0 ? Math.clamp(value / maximum, 0, 1) : 0; }
    private static int readable(int color) {
        int red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255;
        return 0xFF000000 | Math.max(105, red) << 16 | Math.max(120, green) << 8 | Math.max(140, blue);
    }
    public static String seconds(int ticks) {
        if (ticks <= 0) return "0.0s";
        int tenths = (ticks + 1) / 2;
        return tenths >= 100 ? (ticks + 19) / 20 + "s" : tenths / 10 + "." + tenths % 10 + "s";
    }
    private static void rightText(Canvas canvas, String text, int right, int y, int color) { canvas.text(text, right - canvas.width(text), y, color); }
    private static void centered(Canvas canvas, String text, int centre, int y, int color) { canvas.text(text, centre - canvas.width(text) / 2, y, color); }
}
