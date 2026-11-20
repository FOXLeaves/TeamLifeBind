package com.teamlifebind.forge;

import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** A serialized, invisible title marker; old clients still receive a normal chest menu. */
public final class NativeMenuTitle {
    private static final String PREFIX = "teamlifebind:ui/";
    private static final Set<String> KINDS = Set.of("lobby", "campaign", "dev", "magic", "routes", "expedition");
    private NativeMenuTitle() { }
    public static MutableComponent of(String kind, String title) {
        if (!KINDS.contains(kind)) throw new IllegalArgumentException("Unknown native menu: " + kind);
        return Component.literal(title).withStyle(style -> style.withInsertion(PREFIX + kind));
    }
    public static String kind(Component title) {
        String insertion = title.getStyle().getInsertion();
        if (insertion == null || !insertion.startsWith(PREFIX)) return null;
        String kind = insertion.substring(PREFIX.length());
        return KINDS.contains(kind) ? kind : null;
    }
}
