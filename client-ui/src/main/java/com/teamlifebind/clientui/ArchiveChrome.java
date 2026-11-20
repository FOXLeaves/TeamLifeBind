package com.teamlifebind.clientui;

import com.teamlifebind.aui.init.Document;
import com.teamlifebind.aui.client.gui.ApricityGuiLayers;
import com.teamlifebind.aui.render.ScreenDocumentCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** Transparent HTML masthead for every non-TLB screen. Never receives inventory or keyboard input. */
public final class ArchiveChrome {
    private static Screen owner;
    private static Document document;
    private static String family;
    private static long generation=-1;
    private ArchiveChrome() {}
    public static void draw(Screen screen, GuiGraphicsExtractor g) {
        if (!ArchiveTheme.enabled() || ArchiveTheme.owned(screen) || ClientRenderCompat.externalOptions(screen)) return;
        if (owner != screen) {
            close(); owner = screen; family = ArchiveTheme.family(screen);
            document = AuiClient.open("teamlifebind/archive.html");
            generation=-1;
        }
        if(generation!=document.getRefreshGeneration()){
            generation=document.getRefreshGeneration();
            try (var ignored = Document.withContext(document)) {
                document.body.setAttribute("data-family", family);
                text("archive-section", ArchiveTheme.tr("family." + family));
                text("archive-title", family.equals("title") ? "TEAM LIFE BIND" : screen.getTitle().getString());
                text("archive-subtitle", ArchiveTheme.tr("intro." + family));
                text("archive-hint", ArchiveTheme.tr("hint." + (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> ? "container" : "screen")));
            }
        }
        try (var ignored = Document.withContext(document)) {
            if(AuiPageReadiness.placeholder(g,document))return;
            document.applyViewport(false); ScreenDocumentCache.attach(document); ApricityGuiLayers.submitUi(g);
        }
    }
    private static void text(String id, String value) { document.getElementById(id).setTextContent(value); }
    public static void tick() {
        if (owner != null && (Minecraft.getInstance().gui.screen() != owner || !ArchiveTheme.enabled())) close();
    }
    public static void close() { if (document != null) document.remove(); document = null; owner = null; }
    static boolean active() { return document != null; }
}
