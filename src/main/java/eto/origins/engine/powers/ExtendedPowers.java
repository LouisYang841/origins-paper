package eto.origins.engine.powers;

import com.google.gson.JsonObject;
import eto.origins.engine.Action;
import eto.origins.engine.Actions;
import eto.origins.engine.ActivePower;
import eto.origins.engine.BlockConditions;
import eto.origins.engine.Condition;
import eto.origins.engine.Conditions;
import eto.origins.engine.OriginManager;
import eto.origins.engine.Power;
import eto.origins.engine.PowerDefinition;
import eto.origins.engine.PowerRegistry;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** M3 批次：burn、modify_jump、fire_projectile、restrict_armor、modify_exhaustion、
 *  target_action_on_hit、entity_glow、attribute_modify_transfer、modify_break_speed、action_on_callback。 */
public final class ExtendedPowers {

    private ExtendedPowers() {
    }

    public static void registerAll(PowerRegistry registry, Plugin plugin) {
        registry.register(BurnPower::new, "origins:burn");
        registry.register((def, p, m) -> jumpAsConditionedAttribute(def, p, m, plugin), "origins:modify_jump");
        registry.register((def, p, m) -> new FireProjectilePower(def, p, m), "origins:fire_projectile");
        registry.register((def, p, m) -> new RestrictArmorPower(def, p, m), "origins:restrict_armor");
        registry.register(ModifyExhaustionPower::new, "origins:modify_exhaustion");
        registry.register((def, p, m) -> new TargetActionOnHitPower(def, p, m, plugin), "origins:target_action_on_hit");
        registry.register(GlowPower::new, "origins:entity_glow");
        registry.register((def, p, m) -> new AttributeModifyTransferPower(def, p, m), "origins:attribute_modify_transfer");
        registry.register((def, p, m) -> new BreakSpeedPower(def, p, m), "origins:modify_break_speed");
        registry.register((def, p, m) -> new ActionOnCallbackPower(def, p, m), "origins:action_on_callback");
    }

    // ================= burn =================
    static final class BurnPower extends Power {
        private final long interval;
        private final int burnDuration;

        BurnPower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
            this.interval = Math.max(1, def.getLong("interval", 20));
            this.burnDuration = def.raw().has("burn_duration") ? def.raw().get("burn_duration").getAsInt() : 3;
        }

        @Override
        public void tick(long tickCount) {
            if (tickCount % interval != 0 || !active()) return;
            player.setFireTicks(Math.max(player.getFireTicks(), burnDuration * 20));
        }
    }

    // ================= modify_jump → 条件化跳跃强度属性 =================
    static Power jumpAsConditionedAttribute(PowerDefinition def, Player p, OriginManager m, Plugin plugin) {
        JsonObject raw = def.raw().deepCopy();
        JsonObject modifier = raw.has("modifier") ? raw.getAsJsonObject("modifier") : new JsonObject();
        modifier.addProperty("attribute", "minecraft:generic.jump_strength");
        raw.add("modifier", modifier);
        Condition cond = def.condition();
        return new NumericPowers.ConditionedAttributePower(
                new PowerDefinition(def.id(), "origins:conditioned_attribute", raw, cond), p, m, plugin);
    }

    // ================= fire_projectile =================
    static final class FireProjectilePower extends Power implements ActivePower {
        private final String slot;
        private final double speed;
        private final String sound;

        FireProjectilePower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
            this.slot = ActivePowers.keySlotOf(def);
            this.speed = def.raw().has("speed") ? def.raw().get("speed").getAsDouble() : 1.5;
            this.sound = def.raw().has("sound") ? def.raw().get("sound").getAsString() : null;
        }

        @Override
        public String keySlot() {
            return slot;
        }

        @Override
        public boolean onTrigger() {
            if (!active()) return false;
            var pearl = player.launchProjectile(EnderPearl.class);
            pearl.setVelocity(player.getLocation().getDirection().normalize().multiply(speed));
            if (sound != null) {
                try {
                    player.getWorld().playSound(player.getLocation(),
                            Sound.valueOf(sound.replace("minecraft:", "").toUpperCase(Locale.ROOT)), 1f, 1f);
                } catch (IllegalArgumentException ignored) {
                }
            }
            return true;
        }
    }

    // ================= restrict_armor =================
    public static final class RestrictArmorPower extends Power {
        record SlotLimit(String slot, String cmp, double compare) {
        }

        private final List<SlotLimit> limits = new ArrayList<>();

        RestrictArmorPower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
            for (var e : def.raw().entrySet()) {
                if (e.getValue().isJsonObject()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    if (o.has("comparison")) {
                        String key = e.getKey().toUpperCase(Locale.ROOT);
                        if (List.of("HEAD", "CHEST", "LEGS", "FEET").contains(key)) {
                            limits.add(new SlotLimit(key, o.get("comparison").getAsString(), o.get("compare_to").getAsDouble()));
                        }
                    }
                }
            }
        }

        /** 监听器在 PlayerArmorChangeEvent 后调用；违规则弹回装备。 */
        public void checkEquip(Player p, String slotName, ItemStack newItem) {
            if (!active() || newItem == null || newItem.getType().isAir()) return;
            for (SlotLimit limit : limits) {
                if (!limit.slot().equals(slotName)) continue;
                double armorValue = armorValueOf(newItem);
                if (Conditions.compare(armorValue, limit.cmp(), limit.compare())) {
                    p.getInventory().setItem(invSlotOf(slotName), null);
                    p.getWorld().dropItemNaturally(p.getLocation(), newItem);
                    p.sendActionBar("§c你的身体无法穿戴这件装备");
                    return;
                }
            }
        }

        private static double armorValueOf(ItemStack item) {
            var meta = item.getItemMeta();
            if (meta == null) return 0;
            var mods = meta.getAttributeModifiers(Attribute.GENERIC_ARMOR);
            if (mods == null) return 0;
            double sum = 0;
            for (AttributeModifier m : mods) sum += m.getAmount();
            return sum;
        }

        private static int invSlotOf(String slot) {
            return switch (slot) {
                case "HEAD" -> 39;
                case "CHEST" -> 38;
                case "LEGS" -> 37;
                case "FEET" -> 36;
                default -> -1;
            };
        }
    }

    // ================= modify_exhaustion =================
    public static final class ModifyExhaustionPower extends Power {
        private final double amount;
        private final String operation;

        ModifyExhaustionPower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
            JsonObject mod = def.raw().has("modifier") ? def.raw().getAsJsonObject("modifier") : new JsonObject();
            this.amount = mod.has("amount") ? mod.get("amount").getAsDouble() : 0;
            this.operation = mod.has("operation") ? mod.get("operation").getAsString() : "multiply_base_multiplicative";
        }

        /** 监听器调用：返回修改后的 exhaustion 值。 */
        public double apply(double exhaustion) {
            if (!active()) return exhaustion;
            return switch (operation) {
                case "add_value", "add_base_early" -> exhaustion + amount;
                default -> exhaustion * (1 + amount);
            };
        }
    }

    // ================= target_action_on_hit =================
    public static final class TargetActionOnHitPower extends Power {
        private final String blockAt;
        private final String placeBlock;

        TargetActionOnHitPower(PowerDefinition def, Player player, OriginManager mgr, Plugin plugin) {
            super(def, player, mgr);
            // 特化实现 master_of_webs webbing: block_action_at → if_else(replaceable) → place_block cobweb
            String at = null, place = null;
            try {
                JsonObject action = def.raw().getAsJsonObject("entity_action");
                if (Conditions.shortName(action.get("type").getAsString()).equals("block_action_at")) {
                    JsonObject ba = action.getAsJsonObject("block_action");
                    if (Conditions.shortName(ba.get("type").getAsString()).equals("if_else")
                            && ba.has("if_action")) {
                        JsonObject ifAction = ba.getAsJsonObject("if_action");
                        if (Conditions.shortName(ifAction.get("type").getAsString()).equals("place_block")
                                && ifAction.has("block")) {
                            at = "target";
                            place = ifAction.get("block").getAsString();
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            this.blockAt = at;
            this.placeBlock = place;
        }

        /** 监听器调用：玩家近战命中目标时。返回 true 表示触发（需进冷却）。 */
        public boolean onHitTarget(Entity victim) {
            if (!active() || blockAt == null || placeBlock == null) return false;
            Block b = victim.getLocation().getBlock();
            if (b.isReplaceable() && !b.getType().isSolid()) {
                Material mat = Material.matchMaterial(placeBlock.contains(":") ? placeBlock : "minecraft:" + placeBlock);
                if (mat != null) {
                    b.setType(mat);
                    return true;
                }
            }
            return false;
        }
    }

    // ================= entity_glow =================
    static final class GlowPower extends Power {
        private final long frequency;

        GlowPower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
            this.frequency = Math.max(1, def.getLong("frequency", 30));
        }

        @Override
        public void tick(long tickCount) {
            if (tickCount % frequency != 0 || !active()) return;
            for (Entity e : player.getNearbyEntities(10, 5, 10)) {
                if (e instanceof LivingEntity living && !living.isGlowing()) {
                    living.setGlowing(true);
                    mgr.scheduleGlowClear(e);
                }
            }
        }
    }

    // ================= attribute_modify_transfer =================
    static final class AttributeModifyTransferPower extends Power {
        private final Attribute source;
        private final double multiplier;

        AttributeModifyTransferPower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
            this.source = NumericPowers.parseAttribute(
                    def.raw().has("attribute") ? def.raw().get("attribute").getAsString() : "minecraft:generic.movement_speed");
            this.multiplier = def.raw().has("multiplier") ? def.raw().get("multiplier").getAsDouble() : 1.0;
        }

        @Override
        public void tick(long tickCount) {
            if (tickCount % 20 != 0 || !active() || source == null) return;
            AttributeInstance move = player.getAttribute(source);
            AttributeInstance flying = player.getAttribute(Attribute.GENERIC_FLYING_SPEED);
            if (move == null || flying == null) return;
            double base = move.getBaseValue();
            double ratio = base > 0 ? move.getValue() / base - 1 : 0;
            NamespacedKey key = new NamespacedKey("origins", "transfer_" + def.id().replace(':', '_').replace('/', '_'));
            AttributeModifier existing = flying.getModifier(key);
            if (existing != null) flying.removeModifier(key);
            if (Math.abs(ratio) > 1e-6) {
                // 飞行速度基值 0.05，等比例迁移修改量
                flying.addTransientModifier(new AttributeModifier(key,
                        ratio * 0.05 * multiplier, AttributeModifier.Operation.ADD_NUMBER));
            }
        }

        @Override
        public void onRemove() {
            AttributeInstance flying = player.getAttribute(Attribute.GENERIC_FLYING_SPEED);
            if (flying != null) {
                NamespacedKey key = new NamespacedKey("origins", "transfer_" + def.id().replace(':', '_').replace('/', '_'));
                AttributeModifier existing = flying.getModifier(key);
                if (existing != null) flying.removeModifier(key);
            }
        }
    }

    // ================= modify_break_speed（药水近似）=================
    static final class BreakSpeedPower extends Power {
        private final double amount;
        private final String operation;
        private final BlockConditions.BlockCondition blockCondition;

        BreakSpeedPower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
            JsonObject mod = def.raw().has("modifier") ? def.raw().getAsJsonObject("modifier") : new JsonObject();
            this.amount = mod.has("amount") ? mod.get("amount").getAsDouble() : 0;
            this.operation = mod.has("operation") ? mod.get("operation").getAsString() : "add_value";
            this.blockCondition = BlockConditions.parse(
                    def.raw().has("block_condition") ? def.raw().getAsJsonObject("block_condition") : null, null);
        }

        @Override
        public void tick(long tickCount) {
            if (tickCount % 10 != 0) return;
            boolean matches = active() && (blockCondition == null || matchLookBlock());
            if (!matches) return;
            boolean speedUp = "add_base_early".equals(operation) || amount > 0;
            if (speedUp) {
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.HASTE, 40, 1, true, false, false));
            } else {
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.MINING_FATIGUE, 40, 1, true, false, false));
            }
        }

        private boolean matchLookBlock() {
            Block target = player.getTargetBlockExact(5);
            return target != null && blockCondition.test(target);
        }
    }

    // ================= action_on_callback（respawned 字段）=================
    static final class ActionOnCallbackPower extends Power {
        private final Action respawnedAction;

        ActionOnCallbackPower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
            this.respawnedAction = def.raw().has("entity_action_respawned")
                    ? Actions.parseOrNoop(def.raw().get("entity_action_respawned"), null, def.id())
                    : null;
        }

        @Override
        public void onRespawn() {
            if (respawnedAction != null) {
                respawnedAction.execute(player, mgr);
            }
        }
    }
}
