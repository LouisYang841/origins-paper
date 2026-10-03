package eto.origins.gui;

import eto.origins.engine.Origin;
import eto.origins.engine.OriginManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 起源选择 GUI（箱子界面）。监听器注册一次，holder 标识归属。 */
public final class OriginSelectGui implements Listener {

    /** GUI 标识 holder。 */
    public static final class Holder implements InventoryHolder {
        final OriginManager mgr;
        final Inventory inventory;

        Holder(OriginManager mgr, Inventory inventory) {
            this.mgr = mgr;
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final OriginManager mgr;

    public OriginSelectGui(OriginManager mgr) {
        this.mgr = mgr;
    }

    public void open(Plugin plugin, Player player) {
        List<Origin> origins = new ArrayList<>(mgr.origins());
        origins.sort(Comparator.comparingInt(Origin::order));
        int size = ((Math.max(1, origins.size()) - 1) / 9 + 1) * 9;
        Holder holder = new Holder(mgr, Bukkit.createInventory(null, size, "选择你的起源"));
        for (Origin origin : origins) {
            Material mat = Material.matchMaterial(origin.iconMaterial());
            ItemStack item = new ItemStack(mat == null ? Material.BOOK : mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.AQUA + origin.name());
            List<String> lore = new ArrayList<>();
            if (!origin.description().isEmpty()) {
                lore.add(ChatColor.GRAY + origin.description());
                lore.add("");
            }
            lore.add(ChatColor.GOLD + "影响程度: " + "★".repeat(Math.max(1, origin.impact())));
            lore.add(ChatColor.YELLOW + "点击选择");
            meta.setLore(lore);
            item.setItemMeta(meta);
            holder.inventory.addItem(item);
        }
        player.openInventory(holder.inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        HumanEntity clicker = event.getWhoClicked();
        if (!(clicker instanceof Player player) || event.getClickedInventory() != holder.inventory) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()
                || clicked.getItemMeta().getDisplayName() == null) return;
        String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        for (Origin origin : holder.mgr.origins()) {
            if (origin.name().equals(name)) {
                holder.mgr.setOrigin(player, origin.id());
                player.closeInventory();
                player.sendMessage("§a你的起源已设为 §e" + origin.name());
                return;
            }
        }
    }
}
