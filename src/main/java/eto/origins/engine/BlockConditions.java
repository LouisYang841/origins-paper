package eto.origins.engine;

import com.google.gson.JsonObject;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;

/** 方块条件（block_condition 子集）。 */
public final class BlockConditions {

    private BlockConditions() {
    }

    @FunctionalInterface
    public interface BlockCondition {
        boolean test(Block block);
    }

    public static BlockCondition parse(JsonObject json, PowerRegistry registry) {
        if (json == null) {
            return null;
        }
        String type = Conditions.shortName(json.get("type").getAsString());
        boolean inverted = json.has("inverted") && json.get("inverted").getAsBoolean();
        BlockCondition c = parseInner(type, json, registry);
        if (c == null) {
            return null;
        }
        return inverted ? (b) -> !c.test(b) : c;
    }

    private static BlockCondition parseInner(String type, JsonObject json, PowerRegistry registry) {
        switch (type) {
            case "height" -> {
                String cmp = json.get("comparison").getAsString();
                double compareTo = json.get("compare_to").getAsDouble();
                return block -> Conditions.compare(block.getY(), cmp, compareTo);
            }
            case "in_tag" -> {
                Tag<Material> tag = blockTagOf(json.get("tag").getAsString());
                return block -> tag != null && tag.isTagged(block.getType());
            }
            case "block" -> {
                Material mat = Material.matchMaterial(json.get("block").getAsString());
                return block -> block.getType() == mat;
            }
            default -> {
                return null;
            }
        }
    }

    public static Tag<Material> blockTagOf(String key) {
        NamespacedKey nk = NamespacedKey.fromString(key.contains(":") ? key : "minecraft:" + key);
        if (nk == null) {
            return null;
        }
        if (nk.getNamespace().equals("origins")) {
            return switch (nk.getKey()) {
                case "natural_stone" -> naturalStoneTag();
                default -> null;
            };
        }
        return org.bukkit.Bukkit.getTag("blocks", nk, Material.class);
    }

    private static Tag<Material> naturalStoneTag;

    /** origins:natural_stone（石类，Shulk/Feline 睡眠与 Enderian 强臂用）。 */
    public static Tag<Material> naturalStoneTag() {
        if (naturalStoneTag == null) {
            naturalStoneTag = new Tag<>() {
                private final java.util.Set<Material> stones = java.util.Set.of(
                        Material.STONE, Material.DIORITE, Material.ANDESITE, Material.GRANITE,
                        Material.NETHERRACK, Material.BLACKSTONE, Material.BASALT,
                        Material.SANDSTONE, Material.RED_SANDSTONE,
                        Material.DEEPSLATE, Material.TUFF);

                @Override
                public boolean isTagged(Material material) {
                    return stones.contains(material);
                }

                @Override
                public java.util.Set<Material> getValues() {
                    return stones;
                }

                @Override
                public NamespacedKey getKey() {
                    return new NamespacedKey("origins", "natural_stone");
                }
            };
        }
        return naturalStoneTag;
    }
}
