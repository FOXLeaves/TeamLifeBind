package com.teamlifebind.common;

import java.util.List;

/** One layout and palette for all native clients. Canvas measures the actual client font. */
public final class NativeHud {
    public static final int TEXT = 0xFFF0F2EF;
    public static final int MUTED = 0xFFAAB7C0;
    public static final int QUIET = 0xFF7C8C9B;
    public static final int CYAN = 0xFF86DDCF;
    public static final int GOLD = 0xFFE3C18A;
    public static final int WARNING = 0xFFF49B83;
    public static final int PANEL = 0xE8131C26;
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
        String current = Long.toString(Math.max(0, Math.round(magic.mana)));
        float numberScale = compact ? 1.3F : 1.7F;
        canvas.text(current, left, top + 7, TEXT, numberScale);
        int after = left + (int) Math.ceil(canvas.width(current) * numberScale) + 5;
        canvas.text("/ " + Math.max(1, magic.maximum), after, top + (compact ? 11 : 14), MUTED);
        rightText(canvas, fit(canvas, magic.manaLabel, Math.max(0, right - after - canvas.width("/ " + magic.maximum) - 8)), right, top + 11, CYAN);
        bar(canvas, left, top + (compact ? 23 : 28), right - left, 3, ratio(magic.mana, magic.maximum), CYAN);
        for (int index = 0; index < 3; index++) {
            Slot slot = index < magic.slots.size() ? magic.slots.get(index) : null;
            int y = top + (compact ? 30 : 35) + index * (compact ? 14 : 19);
            boolean chosen = index == magic.selected;
            int accent = slot == null ? QUIET : readable(slot.color);
            if (chosen) {
                NativeUi.rounded(canvas, box.x + 5, y - 2, box.width - 10, compact ? 14 : 18, 3, 0xFF263844);
                canvas.rect(box.x + 5, y + 1, 2, compact ? 8 : 11, accent);
            }
            NativeUi.rounded(canvas, left + 1, y - 1, 12, 12, 3, chosen ? 0xFF415666 : 0xFF202D39);
            canvas.text(Integer.toString(index + 1), left + 4, y + 1, chosen ? accent : QUIET);
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
        canvas.rect(left, top + (compact ? 73 : 94), right - left, 1, 0xFF2A3946);
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
        NativeUi.rounded(canvas, right - modeWidth - 4, top + 6, modeWidth + 8, 14, 3, 0xFF293844);
        canvas.text(fit(canvas, weapon.name, right - left - modeWidth - 8), left, top + 9, TEXT);
        rightText(canvas, fit(canvas, weapon.mode, modeWidth), right, top + 10, MUTED);
        String ammo = !weapon.confirmed ? "--" : weapon.infinite ? "∞" : Integer.toString(Math.max(0, weapon.ammo));
        canvas.text(ammo, left, top + 27, accent, 2.3F);
        int afterAmmo = left + (int) Math.ceil(canvas.width(ammo) * 2.3) + 7;
        if (!weapon.infinite) canvas.text("/ " + Math.max(1, weapon.capacity), afterAmmo, top + 40, MUTED);
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
        NativeUi.diamond(canvas, left + 3, top + 13, 4, GOLD);
        canvas.text(fit(canvas, title, panelWidth - 33), left + 12, top + 9, TEXT);
        canvas.rect(left, top + 23, panelWidth - 18, 1, 0x304F6B86);
        for (int index = 0; index < count; index++) {
            Line line = lines.get(index);
            int y = top + 29 + index * rowHeight;
            if ((index & 1) == 1) NativeUi.rounded(canvas, box.x + 5, y - 2, panelWidth - 10, rowHeight, 2, 0x352A3B49);
            canvas.text(fit(canvas, line.text, (int) ((panelWidth - 18) / scale)), left, y, readable(line.color), scale);
        }
        if (more) canvas.text(fit(canvas, "+" + (total - count) + "  " + moreLabel, panelWidth - 18), left, top + panelHeight - 10, GOLD);
        return box;
    }

    public static List<Rect> wheelBounds(int width, int height) {
        int margin = width < 400 ? 6 : 12;
        int cardWidth = Math.max(92, Math.min(184, (width - margin * 3) / 2));
        int cardHeight = height < 240 ? 44 : 58;
        int cx = width / 2, cy = height / 2;
        int radiusY = Math.min(98, Math.max(40, (int) (height * 0.28)));
        int radiusX = Math.min(144, (width - cardWidth - margin * 2) / 2);
        return List.of(new Rect(cx - cardWidth / 2, cy - radiusY - cardHeight / 2, cardWidth, cardHeight),
            new Rect(cx + radiusX - cardWidth / 2, cy + (int) (radiusY * 0.90) - cardHeight / 2, cardWidth, cardHeight),
            new Rect(cx - radiusX - cardWidth / 2, cy + (int) (radiusY * 0.90) - cardHeight / 2, cardWidth, cardHeight));
    }

    public static int wheelHover(int width, int height, double mouseX, double mouseY, int previous) {
        List<Rect> boxes = wheelBounds(width, height);
        for (int index = 0; index < boxes.size(); index++) if (boxes.get(index).contains(mouseX, mouseY)) return index;
        double dx = mouseX - width / 2.0, dy = mouseY - height / 2.0;
        if (Math.hypot(dx, dy) < 18) return previous;
        return Math.floorMod((int) Math.floor((Math.atan2(dy, dx) + Math.PI / 2 + Math.PI / 3 + Math.PI * 2) / (Math.PI * 2 / 3)), 3);
    }

    public static void wheel(Canvas canvas, int width, int height, Magic magic, int hovered, String title, String instruction) {
        canvas.rect(0, 0, width, height, 0xBD0B121B);
        int cx = width / 2, cy = height / 2;
        boolean compact = height < 240;
        Slot focused = hovered >= 0 && hovered < magic.slots.size() ? magic.slots.get(hovered) : null;
        centered(canvas, fit(canvas, compact && focused != null ? focused.name : title, width - 24), cx, compact ? 3 : Math.max(8, cy - 144), TEXT);
        List<Rect> boxes = wheelBounds(width, height);
        int radius = compact ? 20 : 42;
        for (int index = 0; index < 3; index++) {
            Rect box = boxes.get(index);
            NativeUi.line(canvas, cx, cy, box.x + box.width / 2, box.y + box.height / 2,
                index == hovered ? 0xA0609A9B : 0x40384D60);
        }
        NativeUi.rounded(canvas, cx - radius + 2, cy - radius + 2, radius * 2 - 3, radius * 2 - 3, radius, 0xF013202A);
        NativeUi.ring(canvas, cx, cy, radius, ratio(magic.mana, magic.maximum), CYAN);
        if (!compact) {
            NativeUi.ring(canvas, cx, cy, radius + 5, 0, CYAN);
            centered(canvas, magic.manaLabel, cx, cy - 27, MUTED);
        }
        String mana = Long.toString(Math.max(0, Math.round(magic.mana)));
        float scale = compact ? 1F : 1.8F;
        canvas.text(mana, cx - (int) (canvas.width(mana) * scale) / 2, cy - (compact ? 9 : 10), TEXT, scale);
        centered(canvas, "/ " + Math.max(1, magic.maximum), cx, cy + (compact ? 4 : 12), MUTED);
        for (int index = 0; index < 3; index++) {
            Rect box = boxes.get(index);
            Slot slot = index < magic.slots.size() ? magic.slots.get(index) : null;
            int accent = slot == null ? QUIET : readable(slot.color);
            NativeUi.surface(canvas, box, index == hovered ? 0xF0243542 : PANEL, index == hovered ? accent : NativeUi.BORDER);
            NativeUi.rounded(canvas, box.x + 8, box.y + 8, 16, 16, 4, index == hovered ? 0xFF405766 : 0xFF263442);
            canvas.text(Integer.toString(index + 1), box.x + 13, box.y + 12, index == hovered ? accent : MUTED);
            canvas.text(fit(canvas, slot == null ? "—" : slot.name, box.width - 40), box.x + 31, box.y + 11,
                slot == null || !slot.learned ? QUIET : index == hovered ? TEXT : MUTED);
            String detail = slot == null || !slot.learned ? "—" : "T" + slot.tier + "  ·  " + slot.manaCost + " MP";
            int stateWidth = slot == null || !slot.learned ? 0 : Math.min(canvas.width(slot.state), box.width / 3);
            canvas.text(fit(canvas, detail, box.width - 25 - stateWidth), box.x + 9, box.y + box.height - 16, accent);
            if (slot != null && slot.learned) rightText(canvas, fit(canvas, slot.state, box.width / 3), box.x + box.width - 8,
                box.y + box.height - 16, slot.cooldown > 0 ? GOLD : slot.affordable ? MUTED : WARNING);
            if (slot != null && slot.cooldown > 0) bar(canvas, box.x + 9, box.y + box.height - 5, box.width - 18, 2, 1 - ratio(slot.cooldown, slot.cooldownTotal), accent);
        }
        if (!compact && focused != null) centered(canvas, fit(canvas, focused.name, width - 40), cx, height - 38, readable(focused.color));
        centered(canvas, fit(canvas, instruction, width - 16), cx, height - 15, MUTED);
    }

    private static void panel(Canvas canvas, Rect box, int accent) {
        NativeUi.surface(canvas, box, PANEL, NativeUi.BORDER);
        canvas.rect(box.x + 9, box.y + 1, 20, 1, accent);
    }

    private static void bar(Canvas canvas, int x, int y, int width, int height, double amount, int color) {
        if (width <= 0 || height <= 0) return;
        NativeUi.rounded(canvas, x, y, width, height, 1, 0xFF2B3B48);
        int filled = (int) Math.round(width * Math.clamp(Double.isFinite(amount) ? amount : 0, 0, 1));
        if (filled > 0) {
            canvas.rect(x, y, filled, height, color);
            if (height > 2) canvas.rect(x, y, filled, 1, 0x70FFFFFF);
        }
        if (width > 100 && height > 2) for (int mark = 1; mark < 4; mark++) canvas.rect(x + width * mark / 4, y, 1, height, 0x50202D39);
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
