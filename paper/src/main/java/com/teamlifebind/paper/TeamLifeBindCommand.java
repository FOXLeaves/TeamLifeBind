package com.teamlifebind.paper;

import com.teamlifebind.common.HealthPreset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class TeamLifeBindCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT = List.of("help", "ready", "unready", "start", "stop", "status", "setspawn", "clearspawns", "teams", "health", "norespawn", "reload");
    private final TeamLifeBindPaperPlugin plugin;

    public TeamLifeBindCommand(TeamLifeBindPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "ready" -> {
                handleReady(sender);
                return true;
            }
            case "unready" -> {
                handleUnready(sender);
                return true;
            }
            case "help" -> sendUsage(sender);
            case "status" -> sender.sendMessage(plugin.statusText());
            default -> {
            }
        }

        if (!sender.hasPermission("teamlifebind.admin")) {
            sender.sendMessage(ChatColor.RED + "[TLB] No permission.");
            return true;
        }

        switch (sub) {
            case "start" -> plugin.startMatch(sender);
            case "stop" -> plugin.stopMatch(sender);
            case "reload" -> plugin.reloadPluginConfig(sender);
            case "clearspawns" -> {
                plugin.clearSpawns();
                sender.sendMessage(ChatColor.GREEN + "[TLB] Cleared all team spawns.");
            }
            case "setspawn" -> handleSetSpawn(sender, args);
            case "teams" -> handleTeams(sender, args);
            case "health" -> handleHealth(sender, args);
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
            sender.sendMessage(ChatColor.RED + "[TLB] Must be used by a player.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "[TLB] Usage: /tlb setspawn <teamId>");
            return;
        }

        int teamId;
        try {
            teamId = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "[TLB] teamId must be a number.");
            return;
        }

        boolean ok = plugin.setTeamSpawn(teamId, player.getLocation());
        if (!ok) {
            sender.sendMessage(ChatColor.RED + "[TLB] Invalid teamId or location.");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "[TLB] Set spawn for Team #" + teamId + ".");
    }

    private void handleTeams(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "[TLB] Current team-count: " + plugin.getTeamCount());
            sender.sendMessage(ChatColor.GRAY + "Usage: /tlb teams <2-32>");
            return;
        }

        int count;
        try {
            count = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "[TLB] team count must be a number.");
            return;
        }
        if (count < 2 || count > 32) {
            sender.sendMessage(ChatColor.RED + "[TLB] team count must be 2-32.");
            return;
        }
        plugin.setTeamCount(count);
        sender.sendMessage(ChatColor.GREEN + "[TLB] team-count set to " + count + ".");
    }

    private void handleHealth(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "[TLB] Current health preset: " + plugin.getHealthPreset().name());
            sender.sendMessage(ChatColor.GRAY + "Usage: /tlb health <ONE_HEART|HALF_ROW|ONE_ROW>");
            return;
        }

        HealthPreset preset = HealthPreset.fromString(args[1]);
        plugin.setHealthPreset(preset);
        sender.sendMessage(ChatColor.GREEN + "[TLB] health-preset set to " + preset.name() + ".");
    }

    private void handleReady(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "[TLB] \u8be5\u6307\u4ee4\u4ec5\u73a9\u5bb6\u53ef\u7528\u3002");
            return;
        }
        plugin.markReady(player);
    }

    private void handleUnready(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "[TLB] \u8be5\u6307\u4ee4\u4ec5\u73a9\u5bb6\u53ef\u7528\u3002");
            return;
        }
        plugin.unready(player);
    }

    private void handleNoRespawn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.noRespawnStatusText());
            sender.sendMessage(ChatColor.GRAY + "Usage: /tlb norespawn <on|off|add|remove|clear> [dimension]");
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on" -> {
                plugin.setNoRespawnEnabled(true);
                sender.sendMessage(ChatColor.GREEN + "[TLB] norespawn enabled.");
            }
            case "off" -> {
                plugin.setNoRespawnEnabled(false);
                sender.sendMessage(ChatColor.GREEN + "[TLB] norespawn disabled.");
            }
            case "clear" -> {
                plugin.clearNoRespawnDimensions();
                sender.sendMessage(ChatColor.GREEN + "[TLB] norespawn blocked dimensions cleared.");
            }
            case "add" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "[TLB] Usage: /tlb norespawn add <namespace:path>");
                    return;
                }
                if (!plugin.addNoRespawnDimension(args[2])) {
                    sender.sendMessage(ChatColor.RED + "[TLB] Invalid dimension id. Use namespace:path.");
                    return;
                }
                sender.sendMessage(ChatColor.GREEN + "[TLB] Added norespawn dimension: " + args[2]);
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "[TLB] Usage: /tlb norespawn remove <namespace:path>");
                    return;
                }
                if (!plugin.removeNoRespawnDimension(args[2])) {
                    sender.sendMessage(ChatColor.RED + "[TLB] Dimension not found or invalid.");
                    return;
                }
                sender.sendMessage(ChatColor.GREEN + "[TLB] Removed norespawn dimension: " + args[2]);
            }
            default -> sender.sendMessage(ChatColor.RED + "[TLB] Usage: /tlb norespawn <on|off|add|remove|clear> [dimension]");
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "[TLB] \u6307\u4ee4\u8bf4\u660e\uff08\u9ed8\u8ba4\u4e2d\u6587\uff09");
        sender.sendMessage(ChatColor.GRAY + "/tlb help" + ChatColor.DARK_GRAY + " - \u67e5\u770b\u6240\u6709\u6307\u4ee4\u8bf4\u660e");
        sender.sendMessage(ChatColor.GRAY + "/tlb ready" + ChatColor.DARK_GRAY + " - \u73a9\u5bb6\u51c6\u5907\u5f00\u59cb\u6bd4\u8d5b");
        sender.sendMessage(ChatColor.GRAY + "/tlb unready" + ChatColor.DARK_GRAY + " - \u53d6\u6d88\u51c6\u5907");
        sender.sendMessage(ChatColor.GRAY + "/tlb start" + ChatColor.DARK_GRAY + " - \u7ba1\u7406\u5458\u5f3a\u5236\u5f00\u59cb\u6bd4\u8d5b");
        sender.sendMessage(ChatColor.GRAY + "/tlb stop" + ChatColor.DARK_GRAY + " - \u7ed3\u675f\u5f53\u524d\u6bd4\u8d5b");
        sender.sendMessage(ChatColor.GRAY + "/tlb status" + ChatColor.DARK_GRAY + " - \u67e5\u770b\u6bd4\u8d5b\u72b6\u6001");
        sender.sendMessage(ChatColor.GRAY + "/tlb setspawn <teamId>" + ChatColor.DARK_GRAY + " - \u8bbe\u7f6e\u6307\u5b9a\u961f\u4f0d\u56fa\u5b9a\u51fa\u751f\u70b9");
        sender.sendMessage(ChatColor.GRAY + "/tlb clearspawns" + ChatColor.DARK_GRAY + " - \u6e05\u7a7a\u6240\u6709\u56fa\u5b9a\u961f\u4f0d\u51fa\u751f\u70b9");
        sender.sendMessage(ChatColor.GRAY + "/tlb teams <2-32>" + ChatColor.DARK_GRAY + " - \u8bbe\u7f6e\u961f\u4f0d\u6570\u91cf");
        sender.sendMessage(ChatColor.GRAY + "/tlb health <ONE_HEART|HALF_ROW|ONE_ROW>" + ChatColor.DARK_GRAY + " - \u8bbe\u7f6e\u8840\u91cf\u9884\u8bbe");
        sender.sendMessage(ChatColor.GRAY + "/tlb norespawn" + ChatColor.DARK_GRAY + " - \u67e5\u770b\u6b7b\u4ea1\u4e0d\u53ef\u590d\u6d3b\u673a\u5236\u72b6\u6001");
        sender.sendMessage(ChatColor.GRAY + "/tlb norespawn on|off" + ChatColor.DARK_GRAY + " - \u5f00\u542f\u6216\u5173\u95ed\u8be5\u673a\u5236");
        sender.sendMessage(ChatColor.GRAY + "/tlb norespawn add <namespace:path>" + ChatColor.DARK_GRAY + " - \u6dfb\u52a0\u4e0d\u53ef\u590d\u6d3b\u7ef4\u5ea6");
        sender.sendMessage(ChatColor.GRAY + "/tlb norespawn remove <namespace:path>" + ChatColor.DARK_GRAY + " - \u79fb\u9664\u4e0d\u53ef\u590d\u6d3b\u7ef4\u5ea6");
        sender.sendMessage(ChatColor.GRAY + "/tlb norespawn clear" + ChatColor.DARK_GRAY + " - \u6e05\u7a7a\u4e0d\u53ef\u590d\u6d3b\u7ef4\u5ea6\u5217\u8868");
        sender.sendMessage(ChatColor.GRAY + "/tlb reload" + ChatColor.DARK_GRAY + " - \u91cd\u8f7d\u63d2\u4ef6\u914d\u7f6e");
    }
}
