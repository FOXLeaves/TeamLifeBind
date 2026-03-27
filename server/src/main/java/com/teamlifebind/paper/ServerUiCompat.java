package com.teamlifebind.paper;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

final class ServerUiCompat {

    private final Logger logger;
    private boolean titleResolved;
    private boolean headerFooterResolved;
    private boolean actionBarResolved;
    private boolean actionBarUnavailableLogged;
    private Method sendTitleMethod;
    private Method setPlayerListHeaderFooterMethod;
    private Method playerSpigotMethod;
    private Method spigotSendMessageMethod;
    private Constructor<?> textComponentConstructor;
    private Class<?> baseComponentClass;
    private Object actionBarType;

    ServerUiCompat(Logger logger) {
        this.logger = logger;
    }

    void sendTitle(Player player, String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!resolveTitleMethod()) {
            fallbackTitle(player, title, subtitle);
            return;
        }
        try {
            sendTitleMethod.invoke(player, title, subtitle, fadeInTicks, stayTicks, fadeOutTicks);
        } catch (ReflectiveOperationException ex) {
            fallbackTitle(player, title, subtitle);
        }
    }

    void setPlayerListHeaderFooter(Player player, String header, String footer) {
        if (player == null || !player.isOnline() || !resolveHeaderFooterMethod()) {
            return;
        }
        try {
            setPlayerListHeaderFooterMethod.invoke(player, header, footer);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    void sendActionBar(Player player, String message) {
        if (player == null || !player.isOnline() || message == null || message.isBlank()) {
            return;
        }
        if (!resolveActionBarSupport()) {
            if (!actionBarUnavailableLogged) {
                actionBarUnavailableLogged = true;
                logger.fine("Action bar APIs are unavailable on this server implementation.");
            }
            return;
        }
        try {
            Object spigot = playerSpigotMethod.invoke(player);
            Object component = textComponentConstructor.newInstance(message);
            Object array = Array.newInstance(baseComponentClass, 1);
            Array.set(array, 0, component);
            spigotSendMessageMethod.invoke(spigot, actionBarType, array);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private boolean resolveTitleMethod() {
        if (titleResolved) {
            return sendTitleMethod != null;
        }
        titleResolved = true;
        try {
            sendTitleMethod = Player.class.getMethod(
                "sendTitle",
                String.class,
                String.class,
                int.class,
                int.class,
                int.class
            );
        } catch (ReflectiveOperationException ignored) {
            sendTitleMethod = null;
        }
        return sendTitleMethod != null;
    }

    private boolean resolveHeaderFooterMethod() {
        if (headerFooterResolved) {
            return setPlayerListHeaderFooterMethod != null;
        }
        headerFooterResolved = true;
        try {
            setPlayerListHeaderFooterMethod = Player.class.getMethod(
                "setPlayerListHeaderFooter",
                String.class,
                String.class
            );
        } catch (ReflectiveOperationException ignored) {
            setPlayerListHeaderFooterMethod = null;
        }
        return setPlayerListHeaderFooterMethod != null;
    }

    private boolean resolveActionBarSupport() {
        if (actionBarResolved) {
            return spigotSendMessageMethod != null;
        }
        actionBarResolved = true;
        try {
            Class<?> chatMessageTypeClass = Class.forName("net.md_5.bungee.api.ChatMessageType");
            baseComponentClass = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
            Class<?> baseComponentArrayClass = Array.newInstance(baseComponentClass, 0).getClass();
            Class<?> spigotClass = Class.forName("org.bukkit.entity.Player$Spigot");
            Class<?> textComponentClass = Class.forName("net.md_5.bungee.api.chat.TextComponent");

            playerSpigotMethod = Player.class.getMethod("spigot");
            spigotSendMessageMethod = spigotClass.getMethod("sendMessage", chatMessageTypeClass, baseComponentArrayClass);
            textComponentConstructor = textComponentClass.getConstructor(String.class);
            actionBarType = chatMessageTypeClass.getField("ACTION_BAR").get(null);
        } catch (ReflectiveOperationException ex) {
            spigotSendMessageMethod = null;
        }
        return spigotSendMessageMethod != null;
    }

    private void fallbackTitle(Player player, String title, String subtitle) {
        if (title != null && !title.isBlank()) {
            player.sendMessage(title);
        }
        if (subtitle != null && !subtitle.isBlank()) {
            player.sendMessage(subtitle);
        }
    }
}
