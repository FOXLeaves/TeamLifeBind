package com.teamlifebind.paper;

import java.util.regex.Pattern;
import net.kyori.adventure.text.format.NamedTextColor;

final class ChatColor {

    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)§[0-9A-FK-ORX]");

    static final ChatColor BLACK = new ChatColor("§0", NamedTextColor.BLACK);
    static final ChatColor DARK_BLUE = new ChatColor("§1", NamedTextColor.DARK_BLUE);
    static final ChatColor DARK_GREEN = new ChatColor("§2", NamedTextColor.DARK_GREEN);
    static final ChatColor DARK_AQUA = new ChatColor("§3", NamedTextColor.DARK_AQUA);
    static final ChatColor DARK_RED = new ChatColor("§4", NamedTextColor.DARK_RED);
    static final ChatColor DARK_PURPLE = new ChatColor("§5", NamedTextColor.DARK_PURPLE);
    static final ChatColor GOLD = new ChatColor("§6", NamedTextColor.GOLD);
    static final ChatColor GRAY = new ChatColor("§7", NamedTextColor.GRAY);
    static final ChatColor DARK_GRAY = new ChatColor("§8", NamedTextColor.DARK_GRAY);
    static final ChatColor BLUE = new ChatColor("§9", NamedTextColor.BLUE);
    static final ChatColor GREEN = new ChatColor("§a", NamedTextColor.GREEN);
    static final ChatColor AQUA = new ChatColor("§b", NamedTextColor.AQUA);
    static final ChatColor RED = new ChatColor("§c", NamedTextColor.RED);
    static final ChatColor LIGHT_PURPLE = new ChatColor("§d", NamedTextColor.LIGHT_PURPLE);
    static final ChatColor YELLOW = new ChatColor("§e", NamedTextColor.YELLOW);
    static final ChatColor WHITE = new ChatColor("§f", NamedTextColor.WHITE);
    static final ChatColor BOLD = new ChatColor("§l", null);
    static final ChatColor RESET = new ChatColor("§r", NamedTextColor.WHITE);

    private final String legacyCode;
    private final NamedTextColor namedTextColor;

    private ChatColor(String legacyCode, NamedTextColor namedTextColor) {
        this.legacyCode = legacyCode;
        this.namedTextColor = namedTextColor;
    }

    static String stripColor(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return STRIP_COLOR_PATTERN.matcher(input).replaceAll("");
    }

    NamedTextColor namedTextColor() {
        return namedTextColor == null ? NamedTextColor.WHITE : namedTextColor;
    }

    @Override
    public String toString() {
        return legacyCode;
    }
}
