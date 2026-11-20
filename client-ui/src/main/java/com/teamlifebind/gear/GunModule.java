package com.teamlifebind.gear;

import java.util.Locale;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Three interchangeable sockets on the individual weapon stack. */
public enum GunModule {
    WIDE_LENS(Socket.OPTIC, 1.0, 1, 1, 1, 1, 1),
    PRISM_LENS(Socket.OPTIC, 2.25, 1, 1, 1, 1, 1),
    STAR_LENS(Socket.OPTIC, 3.5, 1, 1, 1, 1, 1),
    MOSS_WRAP(Socket.GRIP, 1, .75, 1, .95, 1, 1),
    AMBER_WRAP(Socket.GRIP, 1, 1, .80, .85, 1, 1),
    CLOUD_WRAP(Socket.GRIP, 1, 1, 1, .65, 1, 1),
    DUSK_RUNE(Socket.RUNE, 1, 1, 1, 1, .55, .70),
    COMET_RUNE(Socket.RUNE, 1, 1, 1, 1, .85, 1.45),
    ECHO_RUNE(Socket.RUNE, 1, .92, .92, 1, 1, 1);

    public enum Socket {
        OPTIC, GRIP, RUNE;
        String key() { return "tlb_socket_" + name().toLowerCase(Locale.ROOT); }
    }

    public final Socket socket;
    public final double zoom, hipSpread, aimedSpread, kick, flash, trail;
    GunModule(Socket socket, double zoom, double hipSpread, double aimedSpread, double kick, double flash, double trail) {
        this.socket = socket;
        this.zoom = zoom;
        this.hipSpread = hipSpread;
        this.aimedSpread = aimedSpread;
        this.kick = kick;
        this.flash = flash;
        this.trail = trail;
    }
    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static GunModule installed(CompoundTag tag, Socket socket) {
        String id = tag.getStringOr(socket.key(), "");
        for (GunModule module : values()) if (module.socket == socket && module.id().equals(id)) return module;
        return null;
    }

    public static GunModule installed(ItemStack gun, Socket socket) {
        CustomData custom = gun.get(DataComponents.CUSTOM_DATA);
        return custom == null ? null : installed(custom.copyTag(), socket);
    }
}
