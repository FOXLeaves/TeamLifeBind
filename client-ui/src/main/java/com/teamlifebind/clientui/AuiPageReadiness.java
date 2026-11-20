package com.teamlifebind.clientui;

import com.teamlifebind.aui.init.Document;
import com.teamlifebind.aui.resource.async.style.StyleAsyncHandler;
import com.teamlifebind.aui.resource.async.style.StyleHandle;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Paint barrier, not a loading timer: CSS and all of its font publications must settle first. */
public final class AuiPageReadiness {
    private static int blockedFrames, paintedFrames;
    private AuiPageReadiness() {}
    static boolean settled(StyleHandle handle) {
        return handle != null && handle.pendingCount() == 0 && handle.pendingCss() == 0
            && handle.pendingFonts() == 0
            && handle.state() == com.teamlifebind.aui.task.AbstractAsyncHandler.AsyncState.READY;
    }
    public static boolean ready(Document document) {
        return document != null && settled(StyleAsyncHandler.peekHandle(document));
    }
    /** Used by the PIP renderer as well, so HUDs/overlays cannot flash an unstyled font. */
    public static boolean canPaint(Document document) {
        boolean ready = ready(document);
        if (Boolean.getBoolean("teamlifebind.archive.selftest")) {
            if (ready) paintedFrames++; else blockedFrames++;
        }
        return ready;
    }
    static boolean placeholder(GuiGraphicsExtractor g, Document document) {
        if (ready(document)) return false;
        if(Boolean.getBoolean("teamlifebind.archive.selftest"))blockedFrames++;
        ArchiveLoading.draw(g, -1, 0, "TEAM LIFE BIND", "PREPARING INTERFACE", false);
        return true;
    }
    static int blockedFrames() { return blockedFrames; }
    static int paintedFrames() { return paintedFrames; }
}
