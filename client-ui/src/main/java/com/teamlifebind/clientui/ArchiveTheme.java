package com.teamlifebind.clientui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** One original visual vocabulary for Minecraft's UI. Input and game state stay with their owners. */
public final class ArchiveTheme {
    public static final int INK = 0xE00B1320, PAPER = 0xFFF1E6CA, GOLD = 0xFFD8C392;
    public static final int SILVER = 0xFFB4C3D8, MUTED = 0xFFA9B5C4, VIOLET = 0xFFC1A8F2;
    private ArchiveTheme() {}
    public static boolean enabled() { return !Boolean.getBoolean("teamlifebind.ui.native"); }
    public static boolean widgetsEnabled() {
        var mc=Minecraft.getInstance();
        return enabled()&&(mc==null||mc.gui==null||!ClientRenderCompat.externalOptions(mc.gui.screen()));
    }
    public static boolean owned(Screen screen) {
        return screen instanceof com.teamlifebind.aui.screen.AuiLinkedScreen;
    }
    public static String family(Screen screen) {
        if(screen instanceof net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen)return "gamemode";
        String n = screen.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (n.contains("title")) return "title";
        if (n.contains("pause")) return "pause";
        if (n.contains("death")) return "death";
        if (n.contains("book") || n.contains("sign") || n.contains("lectern")) return "reading";
        if (n.contains("chat") || n.contains("social") || n.contains("report")) return "social";
        if (n.contains("loading") || n.contains("progress") || n.contains("connect") || n.contains("working")) return "loading";
        if (n.contains("enchant") || n.contains("beacon")) return "arcane";
        if (screen instanceof net.minecraft.client.gui.screens.inventory.MenuAccess<?>) return "workshop";
        if (n.contains("world") || n.contains("server") || n.contains("join") || n.contains("multiplayer")) return "journey";
        return "settings";
    }
    public static String tr(String key, Object... arguments) { return Component.translatable("ui.teamlifebind.archive." + key, arguments).getString(); }
    public static int accent(Screen screen) { return screen != null && family(screen).equals("arcane") ? VIOLET : GOLD; }

    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent, boolean selected) {
        if (w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + h, selected ? 0xDE293343 : INK);
        int line = (selected ? 0xDB000000 : 0x50000000) | accent & 0xFFFFFF;
        g.fill(x, y, x + w, y + 1, line); g.fill(x, y + h - 1, x + w, y + h, line);
        int cut = Math.min(5, Math.min(w, h) / 3);
        g.fill(x, y, x + 1, y + cut, line); g.fill(x + w - 1, y + h - cut, x + w, y + h, line);
        if (selected && w > 26) { g.fill(x + 1, y + 2, x + 3, y + h - 2, accent); }
    }
    public static void diamond(GuiGraphicsExtractor g, int cx, int cy, int r, int color, boolean solid) {
        for (int y = -r; y <= r; y++) {
            int span = r - Math.abs(y);
            if (solid) g.fill(cx - span, cy + y, cx + span + 1, cy + y + 1, color);
            else { g.fill(cx - span, cy + y, cx - span + 1, cy + y + 1, color); g.fill(cx + span, cy + y, cx + span + 1, cy + y + 1, color); }
        }
    }
    public static void button(GuiGraphicsExtractor g, AbstractWidget b) {
        int color = b.active ? GOLD : MUTED;
        panel(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), color, b.isHoveredOrFocused());
        if (b.active && b.isHoveredOrFocused() && b.getWidth() > 44)
            diamond(g, b.getRight() - 7, b.getY() + b.getHeight() / 2, 2, GOLD, true);
    }

    /** Every GUI-atlas sprite is replaced. Item/world atlases never enter this path. */
    public static boolean sprite(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h, int tint) {
        if (!widgetsEnabled() || !id.getNamespace().equals("minecraft")) return false;
        String p = id.getPath();
        int color = p.contains("disabled") || p.contains("empty") ? 0xFF657184
            : p.contains("poison") ? 0xFFA7CF83 : p.contains("wither") ? 0xFFB5A7CD
            : p.contains("freez") ? 0xFFA6DCD8 : p.contains("absorb") ? GOLD
            : p.contains("heart") ? 0xFFE7A18C : p.contains("food") ? GOLD
            : p.contains("air") ? 0xFFA6DCD8 : p.contains("enchant") ? VIOLET : GOLD;
        int alpha = tint >>> 24;
        if (alpha == 0 || w <= 0 || h <= 0) return true;
        color = color & 0xFFFFFF | alpha << 24;
        boolean lit = p.contains("highlight") || p.contains("selected") || p.contains("selection") || p.contains("focused");
        if (p.contains("background") || p.contains("frame") || p.contains("button") || p.contains("slot")
                || p.contains("hotbar") || p.contains("text_field") || p.contains("tooltip") || p.contains("toast")) {
            panel(g, x, y, w, h, color, lit);
            if (p.equals("hud/hotbar")) for (int i = 1; i < 9; i++) g.fill(x + i * 20 + 1, y + 5, x + i * 20 + 2, y + h - 5, 0x506A7382);
        } else if (p.contains("slider_handle") || p.contains("scroller")) {
            g.fill(x, y + 2, x + w, y + h - 2, color); g.fill(x + w / 2, y + 4, x + w / 2 + 1, y + h - 4, 0xFF18212D);
        } else if (p.contains("slider") || p.contains("bar") || p.contains("progress")) {
            g.fill(x, y + h / 2 - 1, x + w, y + h / 2 + 1, color);
        } else if (p.contains("crosshair")) {
            int cx = x + w / 2, cy = y + h / 2;
            diamond(g, cx, cy, 3, 0xFFC9D6E4, false);
            g.fill(cx - 7, cy, cx - 5, cy + 1, PAPER); g.fill(cx + 6, cy, cx + 8, cy + 1, PAPER);
        } else if (p.contains("arrow") || p.contains("page_forward") || p.contains("page_backward")) {
            int cx = x + w / 2, cy = y + h / 2, r = Math.max(2, Math.min(w, h) / 3);
            boolean back = p.contains("left") || p.contains("backward");
            for (int i = -r; i <= r; i++) { int px = cx + (back ? -1 : 1) * (r - Math.abs(i)); g.fill(px, cy + i, px + 2, cy + i + 1, color); }
        } else if (p.contains("check")) {
            panel(g, x, y, w, h, color, lit);
            if (p.contains("selected")) diamond(g, x + w / 2, y + h / 2, Math.max(2, Math.min(w, h) / 4), color, true);
        } else if (p.contains("heart") || p.contains("food") || p.contains("armor") || p.contains("air")) {
            boolean full = !p.contains("empty") && !p.contains("container");
            int r = Math.max(2, Math.min(w, h) / 2 - 1);
            if (p.contains("half")) {
                diamond(g, x + w / 2, y + h / 2, r, color, false);
                for (int row = -r; row <= r; row++) g.fill(x + w / 2 - r + Math.abs(row), y + h / 2 + row, x + w / 2 + 1, y + h / 2 + row + 1, color);
            } else diamond(g, x + w / 2, y + h / 2, r, color, full);
        } else if (w >= 40 || h >= 40) {
            panel(g, x, y, w, h, color, lit);
        } else {
            // Small status and tool glyphs share a crest; the notch pattern distinguishes identities.
            int cx = x + w / 2, cy = y + h / 2, r = Math.max(2, Math.min(w, h) / 2 - 1);
            diamond(g, cx, cy, r, color, false);
            int hash = p.hashCode();
            for (int i = 0; i < 4; i++) if ((hash & 1 << i) != 0) g.fill(cx - 2 + i, cy - 1, cx - 1 + i, cy + 2, color);
        }
        return true;
    }
    public static boolean texture(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h, int tint) {
        if (!widgetsEnabled() || !id.getNamespace().equals("minecraft") || !id.getPath().startsWith("textures/gui/")) return false;
        String p = id.getPath();
        if (p.contains("title/") || p.contains("menu_background") || p.contains("separator")) return true;
        if (p.contains("book") || p.contains("sign")) {
            g.fill(x, y, x + w, y + h, 0xF2DDCCA6);
            g.fill(x + 4, y + 4, x + w - 4, y + 5, 0xFF8C7758);
            g.fill(x + 4, y + h - 5, x + w - 4, y + h - 4, 0xFF8C7758);
        } else panel(g, x, y, w, h, GOLD, false);
        return true;
    }
    public static int readable(int color) {
        if (!widgetsEnabled()) return color;
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen == null || family(screen).equals("reading")) return color;
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        return Math.max(r, Math.max(g, b)) < 100 ? (color & 0xFF000000) | 0xD3DCE6 : color;
    }
    public static void slot(GuiGraphicsExtractor g, int x, int y, boolean hover, boolean filled) {
        panel(g, x - 1, y - 1, 18, 18, filled ? SILVER : GOLD, hover);
        if (!filled) g.fill(x + 7, y + 7, x + 9, y + 9, 0x405F6D82);
    }
    /** Bespoke asymmetric main/pause menus, retaining each loader's installed actions. */
    public static void layoutActions(Screen screen) {
        if (!enabled() || !(family(screen).equals("title") || family(screen).equals("pause"))) return;
        var all = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).filter(b -> b.visible).toList();
        var buttons = all.stream().filter(b -> !utility(b) && !b.getMessage().getString().contains("Mojang")).toList();
        if (buttons.isEmpty()) return;
        int x=Math.max(16,screen.width/10),columns=buttons.size()>6?2:1,rows=(buttons.size()+columns-1)/columns;
        int width=Math.min(228,(screen.width-2*x-8*(columns-1))/columns);
        int h=Math.clamp((screen.height-90)/Math.max(1,rows)-4,12,27);
        int y=Math.max(42,(screen.height-rows*(h+4))/2);
        for(int i=0;i<buttons.size();i++)buttons.get(i).setRectangle(width,h,x+(i%columns)*(width+8),y+(i/columns)*(h+4));
        y+=rows*(h+4);
        var tools=all.stream().filter(ArchiveTheme::utility).toList();
        int toolWidth=(width-Math.max(0,tools.size()-1)*5)/Math.max(1,tools.size());
        for(int i=0;i<tools.size();i++)tools.get(i).setRectangle(toolWidth,24,x+i*(toolWidth+5),y+4);
        for(var b:all)if(b.getMessage().getString().contains("Mojang"))b.setRectangle(Math.min(width,screen.width-32),14,x,screen.height-29);
    }
    private static boolean utility(AbstractWidget b) {
        return b instanceof net.minecraft.client.gui.components.ImageButton || b instanceof net.minecraft.client.gui.components.SpriteIconButton || b.getMessage().getString().isBlank();
    }
    public static void title(Screen screen, GuiGraphicsExtractor g, int mx, int my, float dt) {
        // Loader extensions can arrange their title widgets after Screen.init returns.
        layoutActions(screen);
        for (var child : screen.children()) if (child instanceof net.minecraft.client.gui.components.Renderable renderable) {
            if (child instanceof AbstractWidget widget) widget.setAlpha(1);
            renderable.extractRenderState(g, mx, my, dt);
            if (child instanceof AbstractWidget b && utility(b) && b.getWidth()>50 && !b.getMessage().getString().isBlank()) {
                g.centeredText(Minecraft.getInstance().font,b.getMessage(),b.getX()+b.getWidth()/2,b.getBottom()-10,SILVER);
            }
        }
    }
    public static int hudColor(int color) {
        if (!enabled()) return color;
        return switch(color) {
            case 0xFFF0F2EF -> PAPER; case 0xFFAAB7C0 -> SILVER; case 0xFF7C8C9B -> 0xFF8C9BAF;
            case 0xFF86DDCF -> GOLD; case 0xFFE3C18A -> 0xFFE4CEA0; case 0xE8131C26 -> 0xDE0B1320;
            default -> color;
        };
    }
}
