package eto.origins.listeners;

import eto.origins.engine.OriginManager;
import eto.origins.engine.Power;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;

/** 玩家生命周期 + 事件分发。 */
public final class PlayerListener implements Listener {

    private final OriginManager mgr;
    private final Plugin plugin;
    private final java.util.function.BiConsumer<Plugin, Player> guiOpener;

    public PlayerListener(OriginManager mgr, Plugin plugin, java.util.function.BiConsumer<Plugin, Player> guiOpener) {
        this.mgr = mgr;
        this.plugin = plugin;
        this.guiOpener = guiOpener;
    }

    // ================= 生命周期 =================

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        mgr.loadFor(p);
        if (!mgr.hasChosen(p)) {
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> guiOpener.accept(plugin, p), 40L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        mgr.unloadFor(event.getPlayer());
    }

    // ================= 主动技能（潜行 + 副手切换 代替原版按键）=================

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        if (p.isSneaking()) {
            event.setCancelled(true);
            mgr.triggerActive(p, "primary_active");
        } else {
            mgr.triggerActive(p, "secondary_active");
        }
    }

    // ================= 物品使用限制 / 使用动作 =================

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() == null) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        var item = event.getItem();

        for (Power power : mgr.powersOf(event.getPlayer())) {
            if (power.onItemUse(item)) {
                event.setCancelled(true);
                return;
            }
        }
        for (Power power : mgr.powersOf(event.getPlayer())) {
            if (power.preventItemUse(item)) {
                event.setCancelled(true);
                event.getPlayer().sendActionBar("§c你的身体无法使用这个物品");
                return;
            }
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        var item = event.getItem();
        for (Power power : mgr.powersOf(event.getPlayer())) {
            if (power.onItemUse(item)) {
                event.setCancelled(true);
                return;
            }
        }
        for (Power power : mgr.powersOf(event.getPlayer())) {
            if (power.preventItemUse(item)) {
                event.setCancelled(true);
                event.getPlayer().sendActionBar("§c你的身体无法吃下这个食物");
                return;
            }
        }
    }

    // ================= 睡眠 / 疾跑 =================

    @EventHandler
    public void onBedEnter(PlayerBedEnterEvent event) {
        for (Power power : mgr.powersOf(event.getPlayer())) {
            if (power.preventSleep(event.getBed())) {
                event.setCancelled(true);
                String msg = power instanceof eto.origins.engine.SleepMessage r
                        ? r.message() : "你无法在此入睡";
                event.getPlayer().sendMessage("§c" + msg);
                return;
            }
        }
    }

    @EventHandler
    public void onToggleSprint(PlayerToggleSprintEvent event) {
        if (event.isSprinting()) {
            for (Power power : mgr.powersOf(event.getPlayer())) {
                if (power.preventSprinting()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    // ================= 伤害 =================

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        // 受害者是玩家
        if (event.getEntity() instanceof Player victim) {
            for (Power power : mgr.powersOf(victim)) {
                if (power.invulnerableTo(event)) {
                    event.setCancelled(true);
                    return;
                }
            }
            double dmg = event.getDamage();
            for (Power power : mgr.powersOf(victim)) {
                dmg = power.modifyDamageTaken(dmg, event);
            }
            if (dmg != event.getDamage()) {
                event.setDamage(Math.max(0, dmg));
            }
        }
        // 攻击者是玩家
        if (event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Player attacker) {
            double dmg = event.getDamage();
            for (Power power : mgr.powersOf(attacker)) {
                dmg = power.modifyDamageDealt(dmg, event);
            }
            if (dmg != event.getDamage()) {
                event.setDamage(Math.max(0, dmg));
            }
            // target_action_on_hit（如蛛裔命中结网）
            if (!event.isCancelled() && event.getEntity() instanceof org.bukkit.entity.LivingEntity victim
                    && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
                for (Power power : mgr.powersOf(attacker)) {
                    if (power instanceof eto.origins.engine.powers.ExtendedPowers.TargetActionOnHitPower tap) {
                        if (!mgr.onCooldown(attacker, tap.definition().id())
                                && tap.onHitTarget(victim)) {
                            mgr.startCooldown(attacker, tap.definition().id(), tap.definition().getLong("cooldown", 0));
                        }
                    }
                }
            }
        }
    }

    // ================= 装备限制 / 饥饿消耗 =================

    @EventHandler
    public void onArmorChange(com.destroystokyo.paper.event.player.PlayerArmorChangeEvent event) {
        Player p = event.getPlayer();
        for (Power power : mgr.powersOf(p)) {
            if (power instanceof eto.origins.engine.powers.ExtendedPowers.RestrictArmorPower rap) {
                rap.checkEquip(p, event.getSlotType().name(), event.getNewItem());
            }
        }
    }

    @EventHandler
    public void onExhaustion(org.bukkit.event.entity.EntityExhaustionEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        double v = event.getExhaustion();
        for (Power power : mgr.powersOf(p)) {
            if (power instanceof eto.origins.engine.powers.ExtendedPowers.ModifyExhaustionPower mep) {
                v = mep.apply(v);
            }
        }
        if (v != event.getExhaustion()) {
            event.setExhaustion((float) v);
        }
    }

    // ================= 效果免疫 / 隐身仇恨抑制 =================

    @EventHandler
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (event.getNewEffect() == null) return;
        var type = event.getNewEffect().getType();
        for (Power power : mgr.powersOf(p)) {
            if (power.immuneToEffect(type)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player p)) return;
        for (Power power : mgr.powersOf(p)) {
            if (power.suppressMobTarget()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ================= 重生点 =================

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        for (Power power : mgr.powersOf(event.getPlayer())) {
            power.onRespawn();
        }
        if (event.isBedSpawn() || event.isAnchorSpawn()) return;
        for (Power power : mgr.powersOf(event.getPlayer())) {
            var loc = power.modifyRespawnLocation(new Power.Location2(
                    event.getRespawnLocation().getWorld().getName(),
                    event.getRespawnLocation().getX(),
                    event.getRespawnLocation().getY(),
                    event.getRespawnLocation().getZ(),
                    event.getRespawnLocation().getYaw(),
                    event.getRespawnLocation().getPitch()));
            if (loc != null) {
                var world = plugin.getServer().getWorld(loc.worldName());
                if (world != null) {
                    event.setRespawnLocation(new org.bukkit.Location(world, loc.x(), loc.y(), loc.z(), loc.yaw(), loc.pitch()));
                    return;
                }
            }
        }
    }
}
