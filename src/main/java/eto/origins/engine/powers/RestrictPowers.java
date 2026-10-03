package eto.origins.engine.powers;

import com.google.gson.JsonObject;
import eto.origins.engine.BlockConditions;
import eto.origins.engine.ItemConditions;
import eto.origins.engine.OriginManager;
import eto.origins.engine.Power;
import eto.origins.engine.PowerDefinition;
import eto.origins.engine.PowerRegistry;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

/** 限制类 power：禁用物品/禁睡/禁疾跑/重生点重定向。 */
public final class RestrictPowers {

    private RestrictPowers() {
    }

    public static void registerAll(PowerRegistry registry, org.bukkit.plugin.Plugin plugin) {
        registry.register(PreventItemUsePower::new, "origins:prevent_item_use");
        registry.register(PreventSleepPower::new, "origins:prevent_sleep");
        registry.register(PreventSprintingPower::new, "origins:prevent_sprinting");
        registry.register((def, p, m) -> new ModifyPlayerSpawnPower(def, p, m, plugin), "origins:modify_player_spawn");
    }

    // ================= prevent_item_use =================
    static final class PreventItemUsePower extends Power {
        private final ItemConditions.ItemCondition itemCondition;

        PreventItemUsePower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
            this.itemCondition = ItemConditions.parse(
                    def.raw().has("item_condition") ? def.raw().getAsJsonObject("item_condition") : null, null);
        }

        @Override
        public boolean preventItemUse(ItemStack item) {
            return active() && itemCondition != null && itemCondition.test(item);
        }
    }

    // ================= prevent_sleep =================
    static final class PreventSleepPower extends Power implements eto.origins.engine.SleepMessage {
        private final BlockConditions.BlockCondition blockCondition;
        private final String message;

        PreventSleepPower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
            this.blockCondition = BlockConditions.parse(
                    def.raw().has("block_condition") ? def.raw().getAsJsonObject("block_condition") : null, null);
            this.message = def.raw().has("message") && def.raw().getAsJsonObject("message").has("translate")
                    ? def.raw().getAsJsonObject("message").get("translate").getAsString()
                    : "你无法在此入睡";
        }

        @Override
        public boolean preventSleep(Block bed) {
            return active() && (blockCondition == null || blockCondition.test(bed));
        }

        @Override
        public String message() {
            return message;
        }
    }

    // ================= prevent_sprinting =================
    static final class PreventSprintingPower extends Power {
        PreventSprintingPower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
        }

        @Override
        public boolean preventSprinting() {
            return active();
        }
    }

    // ================= modify_player_spawn =================
    static final class ModifyPlayerSpawnPower extends Power {
        private final String dimension;
        private final double distanceMultiplier;
        private final org.bukkit.plugin.Plugin plugin;

        ModifyPlayerSpawnPower(PowerDefinition def, Player player, OriginManager mgr, org.bukkit.plugin.Plugin plugin) {
            super(def, player, mgr);
            this.plugin = plugin;
            String dim = def.raw().has("dimension") ? def.raw().get("dimension").getAsString() : "";
            this.dimension = dim.startsWith("minecraft:") ? dim.substring("minecraft:".length()) : dim;
            this.distanceMultiplier = def.raw().has("dimension_distance_multiplier")
                    ? def.raw().get("dimension_distance_multiplier").getAsDouble() : 1.0;
        }

        @Override
        public Location2 modifyRespawnLocation(Location2 vanilla) {
            if (!active() || dimension.isEmpty()) {
                return vanilla;
            }
            World target = findWorld(dimension);
            if (target == null) {
                return vanilla;
            }
            int x = (int) Math.round(vanilla.x() * distanceMultiplier);
            int z = (int) Math.round(vanilla.z() * distanceMultiplier);
            Block highest = target.getHighestBlockAt(x, z);
            return new Location2(target.getName(), x + 0.5, highest.getY() + 1.0, z + 0.5, vanilla.yaw(), vanilla.pitch());
        }

        private World findWorld(String dim) {
            for (World w : Bukkit.getWorlds()) {
                String n = w.getName().toLowerCase();
                if (n.equals(dim) || n.endsWith("_" + dim) || n.replace("_", "").endsWith(dim.replace("_", ""))) {
                    return w;
                }
            }
            return null;
        }
    }
}
