package eto.origins.engine;

import com.google.gson.JsonObject;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityDamageEvent;

/** 伤害条件（damage_condition 子集）。 */
public final class DamageConditions {

    private DamageConditions() {
    }

    @FunctionalInterface
    public interface DamageCondition {
        boolean test(EntityDamageEvent event);
    }

    public static DamageCondition parse(JsonObject json, PowerRegistry registry) {
        if (json == null) {
            return null;
        }
        String type = Conditions.shortName(json.get("type").getAsString());
        boolean inverted = json.has("inverted") && json.get("inverted").getAsBoolean();
        DamageCondition c = parseInner(type, json, registry);
        if (c == null) {
            return null;
        }
        return inverted ? (e) -> !c.test(e) : c;
    }

    private static DamageCondition parseInner(String type, JsonObject json, PowerRegistry registry) {
        switch (type) {
            case "in_tag" -> {
                String tag = json.get("tag").getAsString();
                java.util.Set<EntityDamageEvent.DamageCause> causes = tagCauses(tag);
                return event -> causes.contains(event.getCause());
            }
            case "fire" -> {
                return event -> switch (event.getCause()) {
                    case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> true;
                    default -> false;
                };
            }
            case "name" -> {
                String name = json.get("name").getAsString();
                return event -> event.getDamageSource() != null
                        && event.getDamageSource().getDamageType().getKey().getKey().equals(name.replace("minecraft:", ""));
            }
            default -> {
                return null;
            }
        }
    }

    /** 数据包伤害 tag → Bukkit DamageCause 的近似映射。 */
    private static java.util.Set<EntityDamageEvent.DamageCause> tagCauses(String tag) {
        return switch (tag) {
            case "minecraft:is_fall", "origins:kinetic", "*:kinetic" -> java.util.Set.of(
                    EntityDamageEvent.DamageCause.FALL, EntityDamageEvent.DamageCause.FLY_INTO_WALL);
            case "minecraft:is_fire" -> java.util.Set.of(EntityDamageEvent.DamageCause.FIRE,
                    EntityDamageEvent.DamageCause.FIRE_TICK, EntityDamageEvent.DamageCause.LAVA,
                    EntityDamageEvent.DamageCause.HOT_FLOOR);
            case "minecraft:is_explosion" -> java.util.Set.of(EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
                    EntityDamageEvent.DamageCause.BLOCK_EXPLOSION);
            case "minecraft:is_projectile" -> java.util.Set.of(EntityDamageEvent.DamageCause.PROJECTILE);
            case "minecraft:is_drowning" -> java.util.Set.of(EntityDamageEvent.DamageCause.DROWNING);
            case "minecraft:is_lightning" -> java.util.Set.of(EntityDamageEvent.DamageCause.LIGHTNING);
            case "origins:no_water_for_gills" -> java.util.Set.of(EntityDamageEvent.DamageCause.DROWNING);
            default -> java.util.Set.of();
        };
    }

    /** helper：对伤害事件受害者的简化调用（用于 entity_action damage）。 */
    public static void damage(Entity target, double amount, EntityDamageEvent.DamageCause cause) {
        if (target instanceof Damageable d && amount > 0) {
            d.damage(amount);
        }
    }
}
