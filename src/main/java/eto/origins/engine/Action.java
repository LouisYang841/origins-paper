package eto.origins.engine;

import org.bukkit.entity.Player;

/** 实体动作执行器。 */
@FunctionalInterface
public interface Action {

    void execute(Player player, OriginManager mgr);
}
