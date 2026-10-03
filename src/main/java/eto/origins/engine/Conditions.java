package eto.origins.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 实体条件解析器（origins:xxx entity condition types 子集）。
 * 支持在任意条件对象上使用 "inverted": true 取反。
 */
public final class Conditions {

    private Conditions() {
    }

    public static Condition parse(JsonObject json, PowerRegistry registry) {
        if (json == null || json.isJsonNull()) {
            return null;
        }
        String type = json.get("type").getAsString();
        boolean inverted = json.has("inverted") && json.get("inverted").getAsBoolean();
        Condition c = parseInner(type, json, registry);
        if (c == null) {
            return null; // 未知类型由调用方警告
        }
        return inverted ? c.inverted() : c;
    }

    private static Condition parseInner(String type, JsonObject json, PowerRegistry registry) {
        switch (shortName(type)) {
            case "all_of" -> {
                List<Condition> list = parseList(json, registry);
                return (p, m) -> list.stream().allMatch(c -> c.test(p, m));
            }
            case "any_of" -> {
                List<Condition> list = parseList(json, registry);
                return (p, m) -> list.stream().anyMatch(c -> c.test(p, m));
            }
            case "sneaking" -> {
                return (p, m) -> p.isSneaking();
            }
            case "sprinting" -> {
                return (p, m) -> p.isSprinting();
            }
            case "on_fire" -> {
                return (p, m) -> p.getFireTicks() > 0;
            }
            case "fall_flying" -> {
                return (p, m) -> p.isGliding();
            }
            case "collided_horizontally" -> {
                return (p, m) -> horizontalCollision(p);
            }
            case "submerged_in" -> {
                String fluid = json.get("fluid").getAsString();
                boolean water = fluid.endsWith("water");
                boolean lava = fluid.endsWith("lava");
                return (p, m) -> {
                    var eyes = p.getEyeLocation().getBlock();
                    if (water) return eyes.isLiquid() && eyes.getType() != Material.LAVA;
                    if (lava) return eyes.getType() == Material.LAVA;
                    return false;
                };
            }
            case "in_rain" -> {
                return (p, m) -> p.getWorld().hasStorm() && p.getLocation().getBlock().getRelative(BlockFace.UP).getType().isAir();
            }
            case "exposed_to_sun", "exposed_to_sky" -> {
                return (p, m) -> p.getLocation().getBlock().getLightFromSky() >= 15 && p.getWorld().isDayTime();
            }
            case "health", "food", "food_level" -> {
                String cmp = json.get("comparison").getAsString();
                double compareTo = json.get("compare_to").getAsDouble();
                boolean isFood = shortName(type).equals("food") || shortName(type).equals("food_level");
                return (p, m) -> {
                    double v = isFood ? p.getFoodLevel() : p.getHealth();
                    return compare(v, cmp, compareTo);
                };
            }
            case "invisible" -> {
                return (p, m) -> p.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
            }
            case "on_block" -> {
                return (p, m) -> p.isOnGround() && p.getLocation().getBlock().getRelative(BlockFace.DOWN).getType().isSolid();
            }
            case "block_collision" -> {
                double ox = json.has("offset_x") ? json.get("offset_x").getAsDouble() : 0;
                double oy = json.has("offset_y") ? json.get("offset_y").getAsDouble() : 0;
                double oz = json.has("offset_z") ? json.get("offset_z").getAsDouble() : 0;
                return (p, m) -> p.getLocation().add(ox, oy, oz).getBlock().getType().isSolid();
            }
            case "power_active" -> {
                String pattern = json.get("power").getAsString(); // 形如 "*:*_toggle"
                return (p, m) -> m.isPowerActive(p, pattern);
            }
            case "resource" -> {
                return (p, m) -> false; // TODO M3: resource 条件
            }
            case "status_effect" -> {
                PotionEffectType effect = PotionEffectType.getByKey(NamespacedKey.minecraft(json.get("effect").getAsString().replace("minecraft:", "")));
                return (p, m) -> effect != null && p.hasPotionEffect(effect);
            }
            case "using_effective_tool" -> {
                return (p, m) -> false; // TODO M3
            }
            case "enchantment" -> {
                return (p, m) -> false; // TODO M3
            }
            case "fluid_height" -> {
                String fluid = json.get("fluid").getAsString();
                String cmp = json.get("comparison").getAsString();
                double compareTo = json.get("compare_to").getAsDouble();
                return (p, m) -> {
                    boolean inWater = fluid.endsWith("water");
                    boolean submerged = p.getEyeLocation().getBlock().isLiquid() == inWater
                            && p.getEyeLocation().getBlock().isLiquid();
                    return compare(submerged ? 1 : 0, cmp, compareTo);
                };
            }
            default -> {
                return null;
            }
        }
    }

    private static List<Condition> parseList(JsonObject json, PowerRegistry registry) {
        List<Condition> list = new ArrayList<>();
        JsonArray arr = json.getAsJsonArray("conditions");
        for (JsonElement e : arr) {
            Condition c = parse(e.getAsJsonObject(), registry);
            if (c != null) {
                list.add(c);
            } else if (registry != null) {
                registry.warnUnknownCondition(e.getAsJsonObject().get("type").getAsString());
            }
        }
        return list;
    }

    private static boolean horizontalCollision(Player p) {
        // 1.21 Paper: LivingEntity 有 isCollidingHorizontally？兜底用方块检测
        try {
            var method = p.getClass().getMethod("isCollidingHorizontally");
            return (boolean) method.invoke(p);
        } catch (Exception e) {
            var dir = p.getLocation().getDirection().setY(0).normalize().multiply(0.6);
            return p.getLocation().add(dir).getBlock().getType().isSolid();
        }
    }

    public static boolean compare(double value, String op, double compareTo) {
        return switch (op) {
            case "==" -> value == compareTo;
            case "!=" -> value != compareTo;
            case "<" -> value < compareTo;
            case "<=" -> value <= compareTo;
            case ">" -> value > compareTo;
            case ">=" -> value >= compareTo;
            default -> false;
        };
    }

    /** "origins:all_of" → "all_of" */
    public static String shortName(String type) {
        int i = type.indexOf(':');
        return (i >= 0 ? type.substring(i + 1) : type).toLowerCase(Locale.ROOT);
    }
}
