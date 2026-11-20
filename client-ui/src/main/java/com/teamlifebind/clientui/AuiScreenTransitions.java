package com.teamlifebind.clientui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Retains only the last UI texture. Native navigation/close/save executes immediately. */
public final class AuiScreenTransitions {
    private static java.util.function.LongSupplier clock = System::nanoTime;
    private static final AuiInkTransition transition = new AuiInkTransition(() -> clock.getAsLong());
    private static int direction = 1;
    private AuiScreenTransitions() {}
    public static void back(Runnable navigate) {
        int previous = direction;
        direction = -1;
        try { navigate.run(); } finally { direction = previous; }
    }
    public static void changingTo(Screen next) {
        var current = Minecraft.getInstance().gui.screen();
        if (current == next || ArchivePolicy.blocked(next)) return;
        boolean hadPage = ArchivePresenter.hasActivePage() || AuiMenuPage.hasActivePage()
            || current != null && ArchiveTheme.owned(current);
        if (hadPage && ArchiveTheme.enabled() && AuiMenuSettings.motion()) {
            boolean hasIncomingPage = ArchivePresenter.supports(next) || next != null && ArchiveTheme.owned(next);
            if (hasIncomingPage)
                transition.startWhenReady(AuiInkTransition.Mode.CONTENT, AuiInkTransition.Region.FULL, false, direction, AuiInkTransition.CONTENT_MILLIS);
            else
                transition.start(AuiInkTransition.Mode.CONTENT, AuiInkTransition.Region.FULL, false, -1, AuiInkTransition.CLOSE_MILLIS);
            AuiInkEffect.retainBeforeNavigation(transition.frame());
        } else transition.finish();
    }
    public static boolean active() {
        if (!AuiMenuSettings.motion()) { transition.finish(); return false; }
        return transition.busy();
    }
    public static AuiInkTransition.Frame frame() {
        if (!active()) return AuiInkTransition.Frame.IDLE;
        var current = Minecraft.getInstance().gui.screen();
        boolean ready = current instanceof com.teamlifebind.aui.screen.AuiLinkedScreen linked
            ? AuiPageReadiness.ready(linked.getLinkedDocument())
            : ArchivePresenter.handles(current) ? AuiPageReadiness.ready(ArchivePresenter.document())
            : !ArchivePresenter.supports(current);
        return transition.frame(ready);
    }
    static void finish() { transition.finish(); }
    static void clockForTest(java.util.function.LongSupplier replacement) {
        if (!Boolean.getBoolean("teamlifebind.archive.selftest")) throw new IllegalStateException("Fixture clock is disabled");
        transition.finish();
        clock = replacement == null ? System::nanoTime : replacement;
    }
}
