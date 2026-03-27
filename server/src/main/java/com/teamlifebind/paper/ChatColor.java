package com.teamlifebind.paper;

import java.util.regex.Pattern;

final class ChatColor {

    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)\u00A7[0-9A-FK-ORX]");

    static final ChatColor BLACK = new ChatColor("\u00A70", org.bukkit.ChatColor.BLACK);
    static final ChatColor DARK_BLUE = new ChatColor("\u00A71", org.bukkit.ChatColor.DARK_BLUE);
    static final ChatColor DARK_GREEN = new ChatColor("\u00A72", org.bukkit.ChatColor.DARK_GREEN);
    static final ChatColor DARK_AQUA = new ChatColor("\u00A73", org.bukkit.ChatColor.DARK_AQUA);
    static final ChatColor DARK_RED = new ChatColor("\u00A74", org.bukkit.ChatColor.DARK_RED);
    static final ChatColor DARK_PURPLE = new ChatColor("\u00A75", org.bukkit.ChatColor.DARK_PURPLE);
    static final ChatColor GOLD = new ChatColor("\u00A76", org.bukkit.ChatColor.GOLD);
    static final ChatColor GRAY = new ChatColor("\u00A77", org.bukkit.ChatColor.GRAY);
    static final ChatColor DARK_GRAY = new ChatColor("\u00A78", org.bukkit.ChatColor.DARK_GRAY);
    static final ChatColor BLUE = new ChatColor("\u00A79", org.bukkit.ChatColor.BLUE);
    static final ChatColor GREEN = new ChatColor("\u00A7a", org.bukkit.ChatColor.GREEN);
    static final ChatColor AQUA = new ChatColor("\u00A7b", org.bukkit.ChatColor.AQUA);
    static final ChatColor RED = new ChatColor("\u00A7c", org.bukkit.ChatColor.RED);
    static final ChatColor LIGHT_PURPLE = new ChatColor("\u00A7d", org.bukkit.ChatColor.LIGHT_PURPLE);
    static final ChatColor YELLOW = new ChatColor("\u00A7e", org.bukkit.ChatColor.YELLOW);
    static final ChatColor WHITE = new ChatColor("\u00A7f", org.bukkit.ChatColor.WHITE);
    static final ChatColor BOLD = new ChatColor("\u00A7l", null);

    private final String legacyCode;
    private final org.bukkit.ChatColor bukkitColor;

    private ChatColor(String legacyCode, org.bukkit.ChatColor bukkitColor) {
        this.legacyCode = legacyCode;
        this.bukkitColor = bukkitColor;
    }

    static String stripColor(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return STRIP_COLOR_PATTERN.matcher(input).replaceAll("");
    }

    org.bukkit.ChatColor bukkitColor() {
        return bukkitColor == null ? org.bukkit.ChatColor.WHITE : bukkitColor;
    }

    @Override
    public String toString() {
        return legacyCode;
    }
}
