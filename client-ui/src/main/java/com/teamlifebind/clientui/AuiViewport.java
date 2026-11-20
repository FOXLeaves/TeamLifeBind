package com.teamlifebind.clientui;

import com.mojang.renderpearl.api.commands.RenderPass;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Resolves the optional NeoForge viewport extension once, without linking it on Fabric/Forge. */
public final class AuiViewport {
    private static final MethodHandle SET = resolve();
    private AuiViewport() {}
    private static MethodHandle resolve() {
        try {
            return MethodHandles.publicLookup().findVirtual(RenderPass.class, "setViewport",
                    MethodType.methodType(void.class, int.class, int.class, int.class, int.class));
        } catch (NoSuchMethodException | IllegalAccessException ignored) { return null; }
    }
    public static void set(RenderPass pass, int x, int y, int width, int height) {
        if (SET == null) throw new UnsupportedOperationException("Custom viewports are unavailable on this backend");
        try { SET.invokeExact(pass, x, y, width, height); }
        catch (Throwable failure) { throw new IllegalStateException("AUI viewport update failed", failure); }
    }
}
