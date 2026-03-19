package com.teamlifebind.paper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Player;

final class PaperLocatorBarSupport {

    private final Logger logger;
    private Method craftPlayerGetHandle;
    private Field serverPlayerConnectionField;
    private Method connectionSendPacket;
    private Method livingEntityWaypointIcon;
    private Method waypointIconCloneAndAssignStyle;
    private Method addWaypointAzimuthPacket;
    private Method updateWaypointAzimuthPacket;
    private Method removeWaypointPacket;
    private boolean available;

    PaperLocatorBarSupport(Logger logger) {
        this.logger = logger;
        this.available = resolveReflectionHandles();
    }

    boolean isAvailable() {
        return available;
    }

    void sendAzimuth(Player viewer, Player target, boolean add) {
        if (!available || viewer == null || target == null || !viewer.isOnline() || !target.isOnline()) {
            return;
        }

        try {
            Object targetHandle = craftPlayerGetHandle.invoke(target);
            Object icon = livingEntityWaypointIcon.invoke(targetHandle);
            Object styledIcon = waypointIconCloneAndAssignStyle.invoke(icon, targetHandle);
            float azimuth = azimuthBetween(viewer.getLocation(), target.getLocation());
            Object packet = add
                ? addWaypointAzimuthPacket.invoke(null, target.getUniqueId(), styledIcon, azimuth)
                : updateWaypointAzimuthPacket.invoke(null, target.getUniqueId(), styledIcon, azimuth);
            sendPacket(viewer, packet);
        } catch (ReflectiveOperationException ex) {
            disableAfterFailure("Failed to send Paper locator bar waypoint packet", ex);
        }
    }

    void remove(Player viewer, UUID targetId) {
        if (!available || viewer == null || targetId == null || !viewer.isOnline()) {
            return;
        }

        try {
            Object packet = removeWaypointPacket.invoke(null, targetId);
            sendPacket(viewer, packet);
        } catch (ReflectiveOperationException ex) {
            disableAfterFailure("Failed to remove Paper locator bar waypoint packet", ex);
        }
    }

    private boolean resolveReflectionHandles() {
        try {
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.Packet");
            Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            Class<?> connectionClass = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
            Class<?> livingEntityClass = Class.forName("net.minecraft.world.entity.LivingEntity");
            Class<?> waypointIconClass = Class.forName("net.minecraft.world.waypoints.Waypoint$Icon");
            Class<?> trackedWaypointPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket");

            craftPlayerGetHandle = craftPlayerClass.getMethod("getHandle");
            serverPlayerConnectionField = serverPlayerClass.getField("connection");
            connectionSendPacket = connectionClass.getMethod("send", packetClass);
            livingEntityWaypointIcon = livingEntityClass.getMethod("waypointIcon");
            waypointIconCloneAndAssignStyle = waypointIconClass.getMethod("cloneAndAssignStyle", livingEntityClass);
            addWaypointAzimuthPacket = trackedWaypointPacketClass.getMethod("addWaypointAzimuth", UUID.class, waypointIconClass, float.class);
            updateWaypointAzimuthPacket = trackedWaypointPacketClass.getMethod("updateWaypointAzimuth", UUID.class, waypointIconClass, float.class);
            removeWaypointPacket = trackedWaypointPacketClass.getMethod("removeWaypoint", UUID.class);
            return true;
        } catch (ReflectiveOperationException ex) {
            logger.warning("Paper locator bar teammate filtering is unavailable: " + ex.getMessage());
            return false;
        }
    }

    private void sendPacket(Player viewer, Object packet) throws ReflectiveOperationException {
        Object viewerHandle = craftPlayerGetHandle.invoke(viewer);
        Object connection = serverPlayerConnectionField.get(viewerHandle);
        connectionSendPacket.invoke(connection, packet);
    }

    private float azimuthBetween(Location receiver, Location source) {
        double dx = receiver.getX() - source.getX();
        double dz = receiver.getZ() - source.getZ();
        return (float) Math.atan2(dx, -dz);
    }

    private void disableAfterFailure(String message, ReflectiveOperationException ex) {
        if (!available) {
            return;
        }
        available = false;
        logger.warning(message + ": " + ex.getMessage());
    }
}
