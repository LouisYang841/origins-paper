package eto.origins.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 物品条件（prevent_item_use / action_on_item_use 的 item_condition 子集）。 */
public final class ItemConditions {

    private ItemConditions() {
    }

    @FunctionalInterface
    public interface ItemCondition {
        boolean test(ItemStack item);
    }

    public static ItemCondition parse(JsonObject json, PowerRegistry registry) {
        if (json == null) {
            return null;
        }
        String type = Conditions.shortName(json.get("type").getAsString());
        boolean inverted = json.has("inverted") && json.get("inverted").getAsBoolean();
        ItemCondition c = parseInner(type, json, registry);
        if (c == null) {
            return null;
        }
        return inverted ? (i) -> !c.test(i) : c;
    }

    private static ItemCondition parseInner(String type, JsonObject json, PowerRegistry registry) {
        switch (type) {
            case "all_of", "any_of" -> {
                boolean all = type.equals("all_of");
                List<ItemCondition> list = new ArrayList<>();
                JsonArray arr = json.getAsJsonArray("conditions");
                for (JsonElement e : arr) {
                    ItemCondition c = parse(e.getAsJsonObject(), registry);
                    if (c != null) list.add(c);
                }
                return item -> (all ? list.stream().allMatch(c -> c.test(item))
                        : list.stream().anyMatch(c -> c.test(item)));
            }
            case "ingredient" -> {
                JsonObject ing = json.getAsJsonObject("ingredient");
                if (ing.has("item")) {
                    Material mat = Material.matchMaterial(ing.get("item").getAsString());
                    return item -> item != null && item.getType() == mat;
                } else if (ing.has("tag")) {
                    Tag<Material> tag = tagOf(ing.get("tag").getAsString());
                    return item -> item != null && tag != null && tag.isTagged(item.getType());
                }
                return item -> false;
            }
            case "food" -> {
                return item -> item != null && item.getType().isEdible();
            }
            case "meat" -> { // 兼容旧写法
                Tag<Material> tag = tagOf("origins:meat");
                return item -> item != null && tag != null && tag.isTagged(item.getType());
            }
            default -> {
                return null;
            }
        }
    }

    /** 支持自定义插件 tag（origins:meat / origins:ignore_diet）与 vanilla tag。 */
    public static Tag<Material> tagOf(String key) {
        NamespacedKey nk = NamespacedKey.fromString(key.replace("minecraft:", "minecraft:"));
        if (nk == null) {
            return null;
        }
        if (nk.getNamespace().equals("origins")) {
            Tag<Material> t = org.bukkit.Bukkit.getTag("items", nk, Material.class);
            if (t != null) return t;
            // 内置兜底：肉食 tag
            return switch (nk.getKey()) {
                case "meat" -> meatTag();
                case "ignore_diet" -> null; // 空 tag：永远不匹配 → allow 逻辑由调用方 inverted 处理
                default -> null;
            };
        }
        return org.bukkit.Bukkit.getTag("items", nk, Material.class);
    }

    private static Tag<Material> meatTag;

    /** 插件内置肉食 tag（与原版 origins:meat 数据包等价的近似集合）。 */
    public static Tag<Material> meatTag() {
        if (meatTag == null) {
            meatTag = new Tag<>() {
                private final java.util.Set<Material> meats = java.util.Set.of(
                        Material.BEEF, Material.COOKED_BEEF, Material.PORKCHOP, Material.COOKED_PORKCHOP,
                        Material.CHICKEN, Material.COOKED_CHICKEN, Material.MUTTON, Material.COOKED_MUTTON,
                        Material.RABBIT, Material.COOKED_RABBIT, Material.COD, Material.COOKED_COD,
                        Material.SALMON, Material.COOKED_SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH,
                        Material.ROTTEN_FLESH, Material.SPIDER_EYE, Material.PHANTOM_MEMBRANE,
                        Material.RABBIT_STEW);

                @Override
                public boolean isTagged(Material material) {
                    return meats.contains(material);
                }

                @Override
                public java.util.Set<Material> getValues() {
                    return meats;
                }

                @Override
                public NamespacedKey getKey() {
                    return new NamespacedKey("origins", "meat");
                }
            };
        }
        return meatTag;
    }
}
