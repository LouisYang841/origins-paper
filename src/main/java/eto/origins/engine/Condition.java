package eto.origins.engine;

import org.bukkit.entity.Player;

/** 实体条件评估器。 */
@FunctionalInterface
public interface Condition {

    boolean test(Player player, OriginManager mgr);

    default Condition inverted() {
        return (p, m) -> !this.test(p, m);
    }
}
