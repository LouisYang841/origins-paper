package eto.origins.engine;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

/**
 * Power 实例基类：每个玩家 × 每个已启用 power 一个实例。
 * 钩子由监听器/OriginManager 分发调用；条件由 active() 统一把关。
 */
public abstract class Power {

    protected final PowerDefinition def;
    protected final Player player;
    protected final OriginManager mgr;

    protected Power(PowerDefinition def, Player player, OriginManager mgr) {
        this.def = def;
        this.player = player;
        this.mgr = mgr;
    }

    public PowerDefinition definition() {
        return def;
    }

    /** power 的顶层条件是否满足（未写 condition 则恒真）。 */
    public boolean active() {
        return def.condition() == null || def.condition().test(player, mgr);
    }

    // ---- 生命周期 ----
    public void onApply() {
    }

    public void onRemove() {
    }

    // ---- 每tick钩子（保持廉价；有 interval 需求的自行用 tickCount 取模）----
    public void tick(long tickCount) {
    }

    // ---- 事件钩子 ----
    public double modifyDamageDealt(double damage, EntityDamageEvent event) {
        return damage;
    }

    public double modifyDamageTaken(double damage, EntityDamageEvent event) {
        return damage;
    }

    public boolean invulnerableTo(EntityDamageEvent event) {
        return false;
    }

    public boolean immuneToEffect(PotionEffectType type) {
        return false;
    }

    public boolean preventItemUse(ItemStack item) {
        return false;
    }

    public boolean preventSleep(Block bed) {
        return false;
    }

    public boolean preventSprinting() {
        return false;
    }

    public float modifyBreakSpeed(Block block, float speed) {
        return speed;
    }

    public void onLand(float fallDistance) {
    }

    public void onBlockBreak(Block block) {
    }

    /** 玩家死亡重生后（非床/锚点重生也会触发）。 */
    public void onRespawn() {
    }

    public boolean onItemUse(ItemStack item) {
        return false; // true = 已消耗该次使用（拦截并执行动作）
    }

    /** 隐身等状态下阻止怪物锁定目标。 */
    public boolean suppressMobTarget() {
        return false;
    }

    /** 重生点调整；返回 null 表示不改。 */
    public Location2 modifyRespawnLocation(Location2 vanilla) {
        return vanilla;
    }

    /** 简化坐标记录（避免直接耦合 Bukkit Location 的构造需求）。 */
    public record Location2(String worldName, double x, double y, double z, float yaw, float pitch) {
    }
}
