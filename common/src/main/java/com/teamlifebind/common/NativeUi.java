package com.teamlifebind.common;

import java.util.List;

/** Shared native GUI geometry. No gameplay state, texture pack or client singleton is needed here. */
public final class NativeUi {
    public static final int BACKGROUND = 0xF0111720;
    public static final int SURFACE = 0xEE19222D;
    public static final int BORDER = 0xFF344452;
    private NativeUi() { }

    public record MenuLayout(NativeHud.Rect panel, NativeHud.Rect grid, NativeHud.Rect details,
                             int cellWidth, int cellHeight, int columns, int header, boolean compact, List<Integer> slots) {
        public MenuLayout { slots = List.copyOf(slots); }
        public NativeHud.Rect tile(int slot) {
            int index = slots.indexOf(slot);
            if (index < 0) throw new IllegalArgumentException("Slot is not displayed: " + slot);
            return new NativeHud.Rect(grid.x() + index % columns * cellWidth, grid.y() + index / columns * cellHeight, cellWidth - 4, cellHeight - 4);
        }
        public int hit(double x, double y) {
            if (!grid.contains(x, y)) return -1;
            int index = (int) ((y - grid.y()) / cellHeight) * columns + (int) ((x - grid.x()) / cellWidth);
            if (index < 0 || index >= slots.size()) return -1;
            int slot = slots.get(index);
            return tile(slot).contains(x, y) ? slot : -1;
        }
        public NativeHud.Rect close() { return new NativeHud.Rect(panel.x() + panel.width() - 27, panel.y() + 7, 18, 18); }
        public NativeHud.Rect inspect() { return new NativeHud.Rect(panel.x() + panel.width() - 49, panel.y() + 7, 18, 18); }
    }

    public static MenuLayout menuLayout(int width, int height, int rows) {
        if (rows < 1 || rows > 6) throw new IllegalArgumentException("Native menu needs 1..6 rows");
        return menuLayout(width, height, java.util.stream.IntStream.range(0, rows * 9).boxed().toList());
    }
    public static MenuLayout menuLayout(int width, int height, List<Integer> slots) {
        return menuLayout(width, height, slots, true);
    }
    /** Control panels keep values on their cards and open longer explanations on demand. */
    public static MenuLayout menuLayout(int width, int height, List<Integer> slots, boolean showDetails) {
        if (slots.size() > 54 || slots.stream().anyMatch(slot -> slot < 0 || slot >= 54) || slots.stream().distinct().count() != slots.size())
            throw new IllegalArgumentException("Native menu needs unique server slots 0..53");
        boolean compact = height < 260 || width < 400;
        int margin = compact ? 6 : 16, header = compact ? 28 : 44, footer = compact ? 18 : 28;
        boolean inspect = showDetails && width >= 540 && height >= 260;
        int detailsWidth = inspect ? 176 : 0;
        int availableHeight = height - margin * 2 - header - footer;
        int availableWidth = width - margin * 2 - 24 - (inspect ? detailsWidth + 12 : 0);
        int size = Math.max(1, slots.size());
        int preferredColumns = size <= 12 ? 3 : 4;
        int maxRows = Math.max(1, availableHeight / 20);
        int columns = Math.min(size, Math.min(9, Math.max(preferredColumns, (size + maxRows - 1) / maxRows)));
        int rows = (size + columns - 1) / columns;
        int cellWidth = Math.max(20, Math.min(showDetails ? 112 : 140, availableWidth / columns));
        int cellHeight = Math.max(20, Math.min(showDetails ? size <= 16 ? 48 : 34 : 32, availableHeight / rows));
        int contentHeight = Math.max(rows * cellHeight, inspect ? 132 : 0);
        int panelWidth = columns * cellWidth + 24 + (inspect ? detailsWidth + 12 : 0);
        int panelHeight = header + contentHeight + footer;
        NativeHud.Rect panel = new NativeHud.Rect((width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight);
        NativeHud.Rect grid = new NativeHud.Rect(panel.x() + 12, panel.y() + header, columns * cellWidth, rows * cellHeight);
        NativeHud.Rect details = new NativeHud.Rect(grid.x() + grid.width() + 8, grid.y(), detailsWidth, contentHeight - 3);
        return new MenuLayout(panel, grid, details, cellWidth, cellHeight, columns, header, compact, slots);
    }

    public static void surface(NativeHud.Canvas canvas, NativeHud.Rect box, int color, int border) {
        rounded(canvas, box.x() + 1, box.y() + 3, box.width(), box.height(), 4, 0x38000000);
        rounded(canvas, box.x(), box.y(), box.width(), box.height(), 4, border);
        rounded(canvas, box.x() + 1, box.y() + 1, box.width() - 2, box.height() - 2, 3, color);
        canvas.rect(box.x() + 5, box.y() + 1, box.width() - 10, 1, 0x163E687B);
    }

    public static void rounded(NativeHud.Canvas canvas, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        canvas.rect(x + r, y, width - 2 * r, height, color);
        canvas.rect(x, y + r, r, height - 2 * r, color);
        canvas.rect(x + width - r, y + r, r, height - 2 * r, color);
        for (int row = 0; row < r; row++) {
            int inset = Math.max(1, r - (int) Math.sqrt(r * r - (r - row - .5) * (r - row - .5)));
            canvas.rect(x + inset, y + row, r - inset, 1, color);
            canvas.rect(x + width - r, y + row, r - inset, 1, color);
            canvas.rect(x + inset, y + height - row - 1, r - inset, 1, color);
            canvas.rect(x + width - r, y + height - row - 1, r - inset, 1, color);
        }
    }

    public static void line(NativeHud.Canvas canvas, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) { canvas.rect(x1, y1, 1, 1, color); return; }
        for (int step = 0; step <= steps; step++) canvas.rect(x1 + (x2 - x1) * step / steps, y1 + (y2 - y1) * step / steps, 1, 1, color);
    }

    public static void diamond(NativeHud.Canvas canvas, int x, int y, int radius, int color) {
        line(canvas, x, y - radius, x + radius, y, color); line(canvas, x + radius, y, x, y + radius, color);
        line(canvas, x, y + radius, x - radius, y, color); line(canvas, x - radius, y, x, y - radius, color);
        canvas.rect(x - 1, y - 1, 3, 3, color);
    }

    public static void ring(NativeHud.Canvas canvas, int x, int y, int radius, double progress, int color) {
        int steps = Math.max(24, radius * 5);
        int active = (int) Math.round(steps * Math.clamp(Double.isFinite(progress) ? progress : 0D, 0D, 1D));
        for (int step = 0; step < steps; step++) {
            double angle = -Math.PI / 2 + step * Math.PI * 2 / steps;
            int px = x + (int) Math.round(Math.cos(angle) * radius), py = y + (int) Math.round(Math.sin(angle) * radius);
            canvas.rect(px, py, 1, 1, step < active ? color : 0x80506877);
        }
    }

    public static void menuFrame(NativeHud.Canvas canvas, MenuLayout layout, String title, String category, String hint, int accent) {
        NativeHud.Rect box = layout.panel();
        surface(canvas, box, BACKGROUND, BORDER);
        int left = box.x() + 13;
        diamond(canvas, left + 5, box.y() + (layout.compact() ? 13 : 18), 5, accent);
        float titleScale = layout.compact() ? 1F : 1.15F;
        canvas.text(NativeHud.fit(canvas, title, (int) ((box.width() - 88) / titleScale)), left + 18, box.y() + 9, NativeHud.TEXT, titleScale);
        if (!layout.compact()) canvas.text(category, left + 18, box.y() + 26, accent);
        var close = layout.close();
        rounded(canvas, close.x(), close.y(), close.width(), close.height(), 3, 0xFF25313D);
        line(canvas, close.x() + 6, close.y() + 6, close.x() + 11, close.y() + 11, NativeHud.MUTED);
        line(canvas, close.x() + 11, close.y() + 6, close.x() + 6, close.y() + 11, NativeHud.MUTED);
        var inspect = layout.inspect();
        rounded(canvas, inspect.x(), inspect.y(), inspect.width(), inspect.height(), 3, 0xFF25313D);
        canvas.text("i", inspect.x() + 7, inspect.y() + 5, accent);
        canvas.rect(left, box.y() + box.height() - (layout.compact() ? 17 : 25), box.width() - 26, 1, 0xFF293744);
        canvas.text(NativeHud.fit(canvas, hint, box.width() - 26), left, box.y() + box.height() - (layout.compact() ? 12 : 17), NativeHud.MUTED);
        if (layout.details().width() > 0) rounded(canvas, layout.details().x(), layout.details().y(), layout.details().width(), layout.details().height(), 4, 0xFF151E28);
    }

    public static void menuTile(NativeHud.Canvas canvas, NativeHud.Rect box, boolean occupied, boolean focused, int accent) {
        rounded(canvas, box.x(), box.y(), box.width(), box.height(), 3, focused ? 0xFF2A414C : occupied ? 0xFF202B37 : 0x381C2732);
        if (focused) canvas.rect(box.x() + 4, box.y() + box.height() - 2, box.width() - 8, 2, accent);
        else if (occupied) canvas.rect(box.x() + 4, box.y() + box.height() - 1, box.width() - 8, 1, 0xFF374653);
    }
}
