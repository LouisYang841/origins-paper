package eto.origins.engine.powers;

import com.google.gson.JsonObject;
import eto.origins.engine.Action;
import eto.origins.engine.Actions;
import eto.origins.engine.ActivePower;
import eto.origins.engine.OriginManager;
import eto.origins.engine.Power;
import eto.origins.engine.PowerDefinition;
import eto.origins.engine.PowerRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** 主动/开关类 power：toggle、cooldown、launch、climbing。 */
public final class ActivePowers {

    private ActivePowers() {
    }

    public static void registerAll(PowerRegistry registry, org.bukkit.plugin.Plugin plugin) {
        registry.register((def, p, m) -> new TogglePower(def, p, m), "origins:toggle");
        registry.register((def, p, m) -> new CooldownPower(def, p, m), "origins:cooldown");
        registry.register((def, p, m) -> new LaunchPower(def, p, m), "origins:launch");
        registry.register((def, p, m) -> new ClimbingPower(def, p, m), "origins:climbing");
    }

    static String keySlotOf(PowerDefinition def) {
        if (def.raw().has("key") && def.raw().getAsJsonObject("key").has("key")) {
            String k = def.raw().getAsJsonObject("key").get("key").getAsString();
            if (k.contains("secondary")) return "secondary_active";
        }
        return "primary_active";
    }

    // ================= toggle =================
    public static final class TogglePower extends Power implements ActivePower {
        private final String slot;

        TogglePower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
            this.slot = keySlotOf(def);
        }

        @Override
        public String keySlot() {
            return slot;
        }

        @Override
        public boolean onTrigger() {
            boolean nowOn = mgr.toggleStateWithParents(player, def.id());
            player.sendMessage(nowOn ? "§a[爬墙模式] 已开启" : "§7[爬墙模式] 已关闭");
            return true;
        }
    }

    // ================= cooldown =================
    public static final class CooldownPower extends Power implements ActivePower {
        private final String slot;
        private final Action entityAction;

        CooldownPower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
            this.slot = keySlotOf(def);
            this.entityAction = def.raw().has("entity_action")
                    ? Actions.parseOrNoop(def.raw().get("entity_action"), null, def.id())
                    : null;
        }

        @Override
        public String keySlot() {
            return slot;
        }

        @Override
        public boolean onTrigger() {
            if (!active()) return false;
            if (entityAction != null) {
                entityAction.execute(player, mgr);
            }
            return true;
        }
    }

    // ================= launch =================
    public static final class LaunchPower extends Power implements ActivePower {
        private final String slot;
        private final double speed;
        private final String sound;

        LaunchPower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
            this.slot = keySlotOf(def);
            this.speed = def.raw().has("speed") ? def.raw().get("speed").getAsDouble() : 1.0;
            this.sound = def.raw().has("sound") ? def.raw().get("sound").getAsString() : null;
        }

        @Override
        public String keySlot() {
            return slot;
        }

        @Override
        public boolean onTrigger() {
            if (!active()) return false;
            Vector dir = player.getLocation().getDirection().normalize();
            Vector v = new Vector(dir.getX() * speed * 0.4, Math.max(speed * 0.5, 0.9), dir.getZ() * speed * 0.4);
            player.setVelocity(v);
            if (sound != null) {
                try {
                    player.getWorld().playSound(player.getLocation(),
                            Sound.valueOf(sound.replace("minecraft:", "").toUpperCase()), 1.0f, 1.0f);
                } catch (IllegalArgumentException ignored) {
                }
            }
            return true;
        }
    }

    // ================= climbing =================
    public static final class ClimbingPower extends Power {
        ClimbingPower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
        }

        @Override
        public void tick(long tickCount) {
            if (tickCount % 2 != 0 || !active()) return;
            if (!wallTouch()) return;
            var v = player.getVelocity();
            player.setVelocity(new Vector(v.getX(), 0.16, v.getZ()));
            player.setFallDistance(0);
        }

        /** 检查身体上下两层、面向及左右斜向是否贴着实心方块。 */
        private boolean wallTouch() {
            Location loc = player.getLocation();
            var dir = loc.getDirection().setY(0);
            dir = dir.lengthSquared() < 1e-4 ? new Vector(1, 0, 0) : dir.normalize().multiply(0.4);
            var left = new Vector(-dir.getZ(), 0, dir.getX());
            for (double y : new double[]{0.5, 1.5}) {
                var base = loc.clone().add(0, y, 0);
                if (solid(base.clone().add(dir))) return true;
                if (solid(base.clone().add(dir).add(left.clone().multiply(0.35)))) return true;
                if (solid(base.clone().add(dir).subtract(left.clone().multiply(0.35)))) return true;
            }
            return false;
        }

        private boolean solid(Location l) {
            return l.getBlock().getType().isSolid();
        }

        /** 诊断: 各条件分量实时值 */
        public String debugState() {
            boolean toggle = mgr.isPowerActive(player, "*:*_toggle");
            String raw = mgr.dumpToggleStates(player);
            boolean collided;
            try {
                collided = (boolean) player.getClass().getMethod("isCollidingHorizontally").invoke(player);
            } catch (Exception e) {
                var dir0 = player.getLocation().getDirection().setY(0);
                if (dir0.lengthSquared() < 1e-4) dir0 = new Vector(1, 0, 0);
                collided = player.getLocation().add(dir0.normalize().multiply(0.6)).getBlock().getType().isSolid();
            }
            return "toggle:" + toggle + " 贴墙:" + wallTouch() + " 碰撞:" + collided + " 潜行:" + player.isSneaking() + " 状态集:[" + raw + "]";
        }
    }
}
