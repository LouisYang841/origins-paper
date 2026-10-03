package eto.origins.engine;

import com.google.gson.JsonObject;
import eto.origins.engine.paper.PaperScheduler;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 玩家起源状态中心：按玩家隔离，无跨玩家共享可变状态（Folia 纪律）。 */
public final class OriginManager {

    public record PlayerOriginData(Origin origin, List<Power> powers) {
    }

    private final Plugin plugin;
    private final SchedulerAdapter scheduler;
    private final Map<String, Origin> origins = new HashMap<>();
    private final Map<String, PowerDefinition> powerDefs = new HashMap<>();
    private final PowerRegistry registry;
    private final NamespacedKey pdcKey;

    private final Map<UUID, PlayerOriginData> active = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> toggleStates = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private SchedulerAdapter.Cancelable tickTask;

    public OriginManager(Plugin plugin, PowerRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.scheduler = new PaperScheduler(plugin);
        this.pdcKey = new NamespacedKey(plugin, "origin_id");
    }

    // ================= 内容 =================

    public void loadContent(Map<String, Origin> origins, Map<String, PowerDefinition> powerDefs) {
        this.origins.clear();
        this.origins.putAll(origins);
        this.powerDefs.clear();
        this.powerDefs.putAll(powerDefs);
    }

    public Origin origin(String id) {
        return origins.get(id);
    }

    /** 注册全部内置原语。 */
    public void registerBuiltins() {
        eto.origins.engine.powers.NumericPowers.registerAll(registry, plugin);
        eto.origins.engine.powers.StatePowers.registerAll(registry);
        eto.origins.engine.powers.RestrictPowers.registerAll(registry, plugin);
        eto.origins.engine.powers.ActivePowers.registerAll(registry, plugin);
        eto.origins.engine.powers.TimedPowers.registerAll(registry, plugin);
        eto.origins.engine.powers.ExtendedPowers.registerAll(registry, plugin);
    }

    /** GlowPower 用：延迟熄灭实体发光。 */
    public void scheduleGlowClear(org.bukkit.entity.Entity entity) {
        scheduler.runAtLocation(entity.getLocation(), 40, () -> {
            if (entity.isValid()) {
                entity.setGlowing(false);
            }
        });
    }

    public void startTickLoop() {
        stopTickLoop();
        tickTask = scheduler.runGlobalTimer(1, 1, () -> {
            long tick = Bukkit.getCurrentTick();
            for (var entry : active.entrySet()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p == null || !p.isOnline()) continue;
                for (Power power : entry.getValue().powers()) {
                    try {
                        power.tick(tick);
                    } catch (Exception e) {
                        plugin.getLogger().warning("power tick 异常 " + power.definition().id() + ": " + e.getMessage());
                    }
                }
            }
        });
    }

    public void stopTickLoop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    // ================= 玩家起源生命周期 =================

    public PlayerOriginData data(Player p) {
        return active.get(p.getUniqueId());
    }

    public Origin originOf(Player p) {
        var d = active.get(p.getUniqueId());
        return d == null ? null : d.origin();
    }

    /** 设置玩家起源（null = 人类/无）。会重建 power 实例并持久化。 */
    public void setOrigin(Player p, String originId) {
        removePowers(p);
        toggleStates.remove(p.getUniqueId());
        cooldowns.remove(p.getUniqueId());
        if (originId == null) {
            p.getPersistentDataContainer().set(pdcKey, PersistentDataType.STRING, "origins:human");
            active.remove(p.getUniqueId());
            return;
        }
        Origin origin = origins.get(originId);
        if (origin == null) {
            plugin.getLogger().warning("未知的起源 id: " + originId);
            return;
        }
        List<Power> powers = new ArrayList<>();
        for (String pid : originPowerIds(originId)) {
            PowerDefinition def = powerDefs.get(pid);
            if (def == null) continue;
            instantiate(def, p, powers);
        }
        active.put(p.getUniqueId(), new PlayerOriginData(origin, powers));
        p.getPersistentDataContainer().set(pdcKey, PersistentDataType.STRING, originId);
        for (Power power : powers) {
            try {
                power.onApply();
            } catch (Exception e) {
                plugin.getLogger().warning("power onApply 异常 " + power.definition().id() + ": " + e.getMessage());
            }
        }
    }

    /** 玩家上线：从 PDC 恢复。 */
    public void loadFor(Player p) {
        String id = p.getPersistentDataContainer().get(pdcKey, PersistentDataType.STRING);
        if (id != null && origins.containsKey(id)) {
            setOrigin(p, id);
        }
    }

    /** 玩家下线：清理内存状态。 */
    public void unloadFor(Player p) {
        removePowers(p);
        active.remove(p.getUniqueId());
        toggleStates.remove(p.getUniqueId());
        cooldowns.remove(p.getUniqueId());
    }

    private void removePowers(Player p) {
        var d = active.get(p.getUniqueId());
        if (d != null) {
            for (Power power : d.powers()) {
                try {
                    power.onRemove();
                } catch (Exception e) {
                    plugin.getLogger().warning("power onRemove 异常: " + e.getMessage());
                }
            }
        }
    }

    /** 实例化一个 power 定义（含 multiple 展开）。 */
    private void instantiate(PowerDefinition def, Player p, List<Power> out) {
        String type = def.type().toLowerCase(Locale.ROOT);
        if (Conditions.shortName(type).equals("multiple")) {
            for (var entry : def.raw().entrySet()) {
                if (entry.getKey().equals("type")) continue;
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject sub = entry.getValue().getAsJsonObject();
                if (!sub.has("type")) continue;
                String subId = def.id() + "/" + entry.getKey();
                PowerDefinition subDef = new PowerDefinition(subId,
                        sub.get("type").getAsString(), sub,
                        Conditions.parse(sub.has("condition") ? sub.getAsJsonObject("condition") : null, registry));
                instantiate(subDef, p, out);
            }
            return;
        }
        PowerRegistry.PowerFactory factory = registry.factory(type);
        if (factory == null) {
            registry.warnUnsupported(type);
            return;
        }
        try {
            out.add(factory.create(def, p, this));
        } catch (Exception e) {
            plugin.getLogger().warning("power 实例化失败 " + def.id() + ": " + e.getMessage());
        }
    }

    /** 起源包含的 power id 列表。 */
    public List<String> originPowerIds(String originId) {
        Origin o = origins.get(originId);
        if (o == null) return Collections.emptyList();
        // 从原始 JSON 取 powers 列表（Origin record 不携带，直接读 loader 缓存）
        return originPowers.getOrDefault(originId, Collections.emptyList());
    }

    private final Map<String, List<String>> originPowers = new HashMap<>();

    public void setOriginPowers(String originId, List<String> powerIds) {
        originPowers.put(originId, powerIds);
    }

    // ================= 状态查询（power_active / toggle / cooldown）=================

    public boolean isPowerActive(Player p, String pattern) {
        Set<String> states = toggleStates.get(p.getUniqueId());
        if (states == null) return false;
        for (String id : states) {
            if (matchesPattern(id, pattern)) return true;
        }
        return false;
    }

    /** "*:*_toggle" 后缀通配；"*:phantomize" 任意命名空间精确 key；"origins:phantomize" 精确 id。 */
    public static boolean matchesPattern(String id, String pattern) {
        if (id.equals(pattern)) return true;
        if (pattern.contains("*:*")) {
            return id.endsWith(pattern.substring(pattern.indexOf("*:*") + 3));
        }
        if (pattern.startsWith("*:")) {
            return id.endsWith(":" + pattern.substring(2));
        }
        return false;
    }

    /** 切换 toggle 状态；返回切换后的开/关。 */
    public boolean toggleState(Player p, String powerId) {
        Set<String> states = toggleStates.computeIfAbsent(p.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
        if (states.contains(powerId)) {
            states.remove(powerId);
            return false;
        } else {
            states.add(powerId);
            return true;
        }
    }

    /** 切换 toggle，并同步切换其 multiple 父 power 的状态（power_active 按 parent 匹配）。 */
    public boolean toggleStateWithParents(Player p, String powerId) {
        boolean nowOn = toggleState(p, powerId);
        int slash = powerId.lastIndexOf('/');
        if (slash > 0) {
            String parent = powerId.substring(0, slash);
            Set<String> states = toggleStates.get(p.getUniqueId());
            boolean siblingOn = states.stream().anyMatch(id -> id.startsWith(parent + "/"));
            if (siblingOn) states.add(parent);
            else states.remove(parent);
        }
        return nowOn;
    }

    /** 默认开启的 toggle（active_by_default）。 */
    public void setToggle(Player p, String powerId, boolean on) {
        Set<String> states = toggleStates.computeIfAbsent(p.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
        if (on) states.add(powerId);
        else states.remove(powerId);
    }

    /** entity_action: toggle_power —— 按模式（如 "*:*_toggle"）找到 toggle 并切换。 */
    public void togglePowerByPattern(Player p, String pattern) {
        for (Power power : powersOf(p)) {
            if (matchesPattern(power.definition().id(), pattern) && power instanceof ActivePower ap) {
                ap.onTrigger();
                return;
            }
        }
    }

    public boolean onCooldown(Player p, String powerId) {
        Map<String, Long> cd = cooldowns.get(p.getUniqueId());
        if (cd == null) return false;
        Long until = cd.get(powerId);
        return until != null && Bukkit.getCurrentTick() < until;
    }

    public long cooldownRemaining(Player p, String powerId) {
        Map<String, Long> cd = cooldowns.get(p.getUniqueId());
        if (cd == null) return 0;
        Long until = cd.get(powerId);
        return until == null ? 0 : Math.max(0, until - Bukkit.getCurrentTick());
    }

    public void startCooldown(Player p, String powerId, long ticks) {
        cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>())
                .put(powerId, Bukkit.getCurrentTick() + ticks);
    }

    // ================= 触发主动技能 =================

    /** keySlot: "primary_active" / "secondary_active"。 */
    public void triggerActive(Player p, String keySlot) {
        var d = active.get(p.getUniqueId());
        if (d == null) return;
        for (Power power : d.powers()) {
            if (!(power instanceof ActivePower ap) || !ap.keySlot().equals(keySlot)) continue;
            PowerDefinition def = power.definition();
            if (onCooldown(p, def.id())) {
                p.sendActionBar("§7冷却中: " + cooldownRemaining(p, def.id()) / 20 + "s");
                continue;
            }
            if (ap.onTrigger()) {
                long cd = def.getLong("cooldown", 0);
                if (cd > 0) startCooldown(p, def.id(), cd);
            }
        }
    }

    // ================= 事件分发辅助 =================

    /** 遍历玩家所有 power（含 active 检查交给调用方）。 */
    public List<Power> powersOf(Player p) {
        var d = active.get(p.getUniqueId());
        return d == null ? Collections.emptyList() : d.powers();
    }

    public boolean hasChosen(Player p) {
        return active.containsKey(p.getUniqueId());
    }

    public Set<String> originIds() {
        return Collections.unmodifiableSet(new HashSet<>(origins.keySet()));
    }

    public java.util.Collection<Origin> origins() {
        return Collections.unmodifiableCollection(origins.values());
    }
}
