package eto.origins.commands;

import eto.origins.engine.Origin;
import eto.origins.engine.OriginManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /origin 命令。 */
public final class OriginCommand implements TabExecutor {

    private final OriginManager mgr;
    private final Runnable reloadAction;
    private final java.util.function.BiConsumer<org.bukkit.plugin.Plugin, Player> guiOpener;
    private final org.bukkit.plugin.Plugin plugin;

    public OriginCommand(org.bukkit.plugin.Plugin plugin, OriginManager mgr,
                         Runnable reloadAction,
                         java.util.function.BiConsumer<org.bukkit.plugin.Plugin, Player> guiOpener) {
        this.plugin = plugin;
        this.mgr = mgr;
        this.reloadAction = reloadAction;
        this.guiOpener = guiOpener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase();
        switch (sub) {
            case "select" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("仅玩家可用");
                    return true;
                }
                guiOpener.accept(plugin, p);
            }
            case "info" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("仅玩家可用");
                    return true;
                }
                Origin origin = mgr.originOf(p);
                if (origin == null) {
                    p.sendMessage("§7你还没有选择起源，用 /origin select 打开选择界面");
                } else {
                    p.sendMessage("§a你的起源: §e" + origin.name());
                    if (!origin.description().isEmpty()) {
                        p.sendMessage("§7" + origin.description());
                    }
                }
            }
            case "list" -> {
                sender.sendMessage("§e可用起源:");
                List<Origin> origins = new ArrayList<>(mgr.origins());
                origins.sort(java.util.Comparator.comparingInt(Origin::order));
                for (Origin o : origins) {
                    sender.sendMessage(" §8- §f" + o.name() + " §7(" + o.id() + ")");
                }
            }
            case "reload" -> {
                if (!sender.hasPermission("origins.admin")) {
                    sender.sendMessage("§c无权限");
                    return true;
                }
                reloadAction.run();
                sender.sendMessage("§a内容已重载");
            }
            case "set" -> {
                if (!sender.hasPermission("origins.admin")) {
                    sender.sendMessage("§c无权限");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§7用法: /origin set <玩家> <origin_id|human>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§c玩家不在线: " + args[1]);
                    return true;
                }
                if (args[2].equalsIgnoreCase("human") || args[2].endsWith(":human")) {
                    mgr.setOrigin(target, null);
                    sender.sendMessage("§a已将 " + target.getName() + " 的起源设为 人类");
                    target.sendMessage("§a你的起源已被设为 §e人类");
                    return true;
                }
                String id = args[2].contains(":") ? args[2] : "origins:" + args[2];
                if (mgr.origin(id) == null) {
                    sender.sendMessage("§c未知起源: " + id);
                    return true;
                }
                mgr.setOrigin(target, id);
                sender.sendMessage("§a已将 " + target.getName() + " 的起源设为 " + id);
                target.sendMessage("§a你的起源已被设为 §e" + mgr.origin(id).name());
            }
            default -> sender.sendMessage("§7用法: /origin [select|info|list|reload|set]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("select", "info", "list", "reload", "set").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return mgr.originIds().stream()
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase())).toList();
        }
        return List.of();
    }
}
