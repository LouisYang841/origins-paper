package eto.origins.engine;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/** power/条件/动作 原语注册表。 */
public final class PowerRegistry {

    @FunctionalInterface
    public interface PowerFactory {
        Power create(PowerDefinition def, Player player, OriginManager mgr);
    }

    private final Map<String, PowerFactory> powerTypes = new ConcurrentHashMap<>();
    private final Set<String> unsupportedWarned = ConcurrentHashMap.newKeySet();
    private final Set<String> unknownConditionWarned = ConcurrentHashMap.newKeySet();
    private final Set<String> unknownActionWarned = ConcurrentHashMap.newKeySet();

    /** 注册一个 power 原语；type 可传 "origins:attribute" 或别名列表。 */
    public void register(PowerFactory factory, String... typeAliases) {
        for (String t : typeAliases) {
            powerTypes.put(t.toLowerCase(Locale.ROOT), factory);
        }
    }

    public PowerFactory factory(String type) {
        return powerTypes.get(type.toLowerCase(Locale.ROOT));
    }

    public boolean knows(String type) {
        return powerTypes.containsKey(type.toLowerCase(Locale.ROOT));
    }

    public void warnUnsupported(String type) {
        if (unsupportedWarned.add(type)) {
            System.out.println("[OriginsPaper] 暂不支持的 power 类型（已跳过）: " + type);
        }
    }

    public void warnUnknownCondition(String type) {
        if (unknownConditionWarned.add(type)) {
            System.out.println("[OriginsPaper] 未知条件类型（按 false 处理）: " + type);
        }
    }

    public void warnUnknownAction(String name) {
        if (unknownActionWarned.add(name)) {
            System.out.println("[OriginsPaper] 未知动作类型（按 no-op 处理）: " + name);
        }
    }
}
