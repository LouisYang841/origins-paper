package eto.origins.engine;

/** 起源定义。 */
public record Origin(
        String id,               // 如 "origins:avian"
        String name,             // 显示名（zh）
        String description,      // 显示描述（zh）
        String iconMaterial,     // GUI 图标材质，如 "minecraft:feather"
        int order,
        int impact               // 0~3
) {
}
