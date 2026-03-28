package com.teamlifebind.paper;

import com.teamlifebind.common.HealthPreset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class TeamLifeBindCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT = List.of(
        "help",
        "menu",
        "language",
        "lang",
        "ready",
        "unready",
        "start",
        "stop",
        "status",
        "setspawn",
        "clearspawns",
        "teams",
        "health",
        "healthsync",
        "norespawn",
        "reload"
    );

    private final TeamLifeBindPaperPlugin plugin;

    public TeamLifeBindCommand(TeamLifeBindPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                plugin.openLobbyMenu(player);
                return true;
            }
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "ready" -> {
                handleReady(sender);
                return true;
            }
            case "menu" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + plugin.text("command.player_only"));
                    return true;
                }
                plugin.openLobbyMenu(player);
                return true;
            }
            case "unready" -> {
                handleUnready(sender);
                return true;
            }
            case "help" -> {
                sendUsage(sender);
                return true;
            }
            case "status" -> {
                sender.sendMessage(plugin.statusText());
                return true;
            }
            case "language", "lang" -> {
                handleLanguage(sender, args);
                return true;
            }
            default -> {
            }
        }

        if (!sender.hasPermission("teamlifebind.admin")) {
            sender.sendMessage(ChatColor.RED + plugin.text("command.no_permission"));
            return true;
        }

        switch (sub) {
            case "start" -> plugin.startMatch(sender);
            case "stop" -> plugin.stopMatch(sender);
            case "reload" -> plugin.reloadPluginConfig(sender);
            case "clearspawns" -> {
                plugin.clearSpawns();
                sender.sendMessage(ChatColor.GREEN + plugin.text("command.spawns.cleared"));
            }
            case "setspawn" -> handleSetSpawn(sender, args);
            case "teams" -> handleTeams(sender, args);
            case "health" -> handleHealth(sender, args);
            case "healthsync" -> handleHealthSync(sender, args);
            case "norespawn" -> handleNoRespawn(sender, args);
            default -> sendUsage(sender);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String token = args[0].toLowerCase(Locale.ROOT);
            return ROOT.stream().filter(s -> s.startsWith(token)).toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("health")) {
            return Stream.of(HealthPreset.values()).map(Enum::name).toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("healthsync")) {
            return List.of("on", "off");
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("language") || args[0].equalsIgnoreCase("lang"))) {
            return plugin.availableLanguageCodes();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("setspawn")) {
            List<String> out = new ArrayList<>();
            int maxPreview = Math.min(plugin.getTeamCount() + 2, 12);
            for (int i = 1; i <= maxPreview; i++) {
                out.add(String.valueOf(i));
            }
            return out;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("norespawn")) {
            return List.of("on", "off", "add", "remove", "clear");
        }

        return List.of();
    }

    private void handleSetSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + plugin.text("command.player_only"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + plugin.text("command.usage.setspawn"));
            return;
        }

        int teamId;
        try {
            teamId = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + plugin.text("command.setspawn.invalid_number"));
            return;
        }

        boolean ok = plugin.setTeamSpawn(teamId, player.getLocation());
        if (!ok) {
            sender.sendMessage(ChatColor.RED + plugin.text("command.setspawn.invalid_target"));
            return;
        }
        sender.sendMessage(ChatColor.GREEN + plugin.text("command.setspawn.success", plugin.teamLabel(teamId)));
    }

    private void handleTeams(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + plugin.text("command.team_count.current", plugin.getTeamCount()));
            sender.sendMessage(ChatColor.GRAY + plugin.text("command.usage.teams"));
            return;
        }
        if (plugin.hasActiveMatchSession()) {
            sender.sendMessage(ChatColor.RED + plugin.text("command.team_count.running"));
            return;
        }

        int count;
        try {
            count = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + plugin.text("command.team_count.invalid_number"));
            return;
        }
        if (count < 2 || count > 32) {
            sender.sendMessage(ChatColor.RED + plugin.text("command.team_count.invalid_range"));
            return;
        }
        plugin.setTeamCount(count);
        sender.sendMessage(ChatColor.GREEN + plugin.text("command.team_count.updated", count));
    }

    private void handleHealth(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + plugin.text("command.health.current", plugin.healthPresetLabel(plugin.getHealthPreset())));
            sender.sendMessage(ChatColor.GRAY + plugin.text("command.usage.health"));
            return;
        }
        if (plugin.hasActiveMatchSession()) {
            sender.sendMessage(ChatColor.RED + plugin.text("command.health.running"));
            return;
        }

        HealthPreset preset = parseHealthPreset(args[1]);
        if (preset == null) {
            sender.sendMessage(ChatColor.RED + plugin.text("command.health.invalid"));
            return;
        }

        plugin.setHealthPreset(preset);
        sender.sendMessage(ChatColor.GREEN + plugin.text("command.health.updated", plugin.healthPresetLabel(preset)));
    }

    private void handleHealthSync(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.healthSyncStatusText());
            sender.sendMessage(ChatColor.GRAY + plugin.text("command.usage.healthsync"));
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on" -> {
                plugin.setHealthSyncEnabled(true);
                sender.sendMessage(ChatColor.GREEN + plugin.text("command.healthsync.enabled"));
            }
            case "off" -> {
                plugin.setHealthSyncEnabled(false);
                sender.sendMessage(ChatColor.GREEN + plugin.text("command.healthsync.disabled"));
            }
            default -> sender.sendMessage(ChatColor.RED + plugin.text("command.usage.healthsync"));
        }
    }

    private void handleLanguage(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.languageStatusText());
            sender.sendMessage(ChatColor.GRAY + plugin.text("command.usage.language"));
            return;
        }
        if (!sender.hasPermission("teamlifebind.admin")) {
            sender.sendMessage(ChatColor.RED + plugin.text("command.no_permission"));
            return;
        }
        if (!plugin.setLanguageCode(args[1])) {
            sender.sendMessage(ChatColor.RED + plugin.text("command.language.invalid", plugin.availableLanguageSummary()));
            return;
        }
        sender.sendMessage(ChatColor.GREEN + plugin.text("command.language.updated", plugin.configuredLanguageCode()));
    }

    private void handleReady(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + plugin.text("command.player_only"));
            return;
        }
        plugin.markReady(player);
    }

    private void handleUnready(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + plugin.text("command.player_only"));
            return;
        }
        plugin.unready(player);
    }

    private void handleNoRespawn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.noRespawnStatusText());
            sender.sendMessage(ChatColor.GRAY + plugin.text("command.usage.norespawn"));
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on" -> {
                plugin.setNoRespawnEnabled(true);
                sender.sendMessage(ChatColor.GREEN + plugin.text("command.norespawn.enabled"));
            }
            case "off" -> {
                plugin.setNoRespawnEnabled(false);
                sender.sendMessage(ChatColor.GREEN + plugin.text("command.norespawn.disabled"));
            }
            case "clear" -> {
                plugin.clearNoRespawnDimensions();
                sender.sendMessage(ChatColor.GREEN + plugin.text("command.norespawn.cleared"));
            }
            case "add" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + plugin.text("command.usage.norespawn.add"));
                    return;
                }
                if (!plugin.addNoRespawnDimension(args[2])) {
                    sender.sendMessage(ChatColor.RED + plugin.text("command.norespawn.invalid_dimension"));
                    return;
                }
                sender.sendMessage(ChatColor.GREEN + plugin.text("command.norespawn.added", args[2]));
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + plugin.text("command.usage.norespawn.remove"));
                    return;
                }
                if (!plugin.removeNoRespawnDimension(args[2])) {
                    sender.sendMessage(ChatColor.RED + plugin.text("command.norespawn.not_found"));
                    return;
                }
                sender.sendMessage(ChatColor.GREEN + plugin.text("command.norespawn.removed", args[2]));
            }
            default -> sender.sendMessage(ChatColor.RED + plugin.text("command.usage.norespawn"));
        }
    }

    private void sendUsage(CommandSender sender) {
        for (String key : List.of(
            "command.help.title",
            "command.help.help",
            "command.help.menu",
            "command.help.language",
            "command.help.language_set",
            "command.help.ready",
            "command.help.unready",
            "command.help.start",
            "command.help.stop",
            "command.help.status",
            "command.help.setspawn",
            "command.help.clearspawns",
            "command.help.teams",
            "command.help.health",
            "command.help.healthsync",
            "command.help.healthsync_toggle",
            "command.help.norespawn",
            "command.help.norespawn_toggle",
            "command.help.norespawn_add",
            "command.help.norespawn_remove",
            "command.help.norespawn_clear",
            "command.help.reload"
        )) {
            sender.sendMessage((key.equals("command.help.title") ? ChatColor.GOLD : ChatColor.GRAY) + plugin.text(key));
        }
    }

    private HealthPreset parseHealthPreset(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (HealthPreset preset : HealthPreset.values()) {
            if (preset.name().equals(normalized)) {
                return preset;
            }
        }
        return null;
    }
}
