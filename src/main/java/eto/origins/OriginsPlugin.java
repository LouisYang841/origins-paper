package eto.origins;

import eto.origins.commands.OriginCommand;
import eto.origins.engine.ContentLoader;
import eto.origins.engine.OriginManager;
import eto.origins.engine.PowerRegistry;
import eto.origins.gui.OriginSelectGui;
import eto.origins.listeners.PlayerListener;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class OriginsPlugin extends JavaPlugin {

    private PowerRegistry registry;
    private OriginManager manager;
    private ContentLoader loader;
    private OriginSelectGui gui;

    @Override
    public void onEnable() {
        registry = new PowerRegistry();
        manager = new OriginManager(this, registry);
        manager.registerBuiltins();
        loader = new ContentLoader(this, manager, registry);
        loader.reload();
        manager.startTickLoop();

        gui = new OriginSelectGui(manager);
        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(manager, this, gui::open), this);

        var cmd = getCommand("origin");
        if (cmd != null) {
            var executor = new OriginCommand(this, manager, this::reloadContent, gui::open);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getLogger().info("OriginsPaper 已启用");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.stopTickLoop();
            // 移除 attribute 修改器，避免残留
            for (Player p : getServer().getOnlinePlayers()) {
                manager.unloadFor(p);
            }
        }
    }

    public void reloadContent() {
        loader.reload();
        // 重建在线玩家的 power 实例
        for (Player p : getServer().getOnlinePlayers()) {
            if (manager.hasChosen(p)) {
                manager.setOrigin(p, manager.originOf(p).id());
            }
        }
    }
}
