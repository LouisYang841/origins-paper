package eto.origins.engine.powers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import eto.origins.engine.Condition;
import eto.origins.engine.DamageConditions;
import eto.origins.engine.OriginManager;
import eto.origins.engine.Power;
import eto.origins.engine.PowerDefinition;
import eto.origins.engine.PowerRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 数值/属性类 power：attribute、conditioned_attribute、modify_damage_dealt/taken。 */
public final class NumericPowers {

    private NumericPowers() {
    }

    public static void registerAll(PowerRegistry registry, Plugin plugin) {
        registry.register((def, p, m) -> new AttributePower(def, p, m, plugin), "origins:attribute");
        registry.register((def, p, m) -> new ConditionedAttributePower(def, p, m, plugin), "origins:conditioned_attribute");
        registry.register((def, p, m) -> new DamageModifierPower(def, p, m, true), "origins:modify_damage_dealt");
        registry.register((def, p, m) -> new DamageModifierPower(def, p, m, false), "origins:modify_damage_taken");
    }

    // ================= attribute =================
    static class AttributePower extends Power {
        private final Plugin plugin;
        private final List<ModifierSpec> specs;
        private boolean applied;

        AttributePower(PowerDefinition def, Player player, OriginManager mgr, Plugin plugin) {
            super(def, player, mgr);
            this.plugin = plugin;
            this.specs = parseModifiers(def.raw().get("modifier"), def.id(), plugin);
        }

        @Override
        public void onApply() {
            apply();
        }

        @Override
        public void onRemove() {
            remove();
        }

        void apply() {
            if (applied) return;
            applied = true;
            for (ModifierSpec spec : specs) {
                AttributeInstance inst = player.getAttribute(spec.attribute);
                if (inst != null && inst.getModifier(spec.key) == null) {
                    inst.addTransientModifier(new AttributeModifier(spec.key, spec.amount, spec.operation));
                }
            }
        }

        void remove() {
            if (!applied) return;
            applied = false;
            for (ModifierSpec spec : specs) {
                AttributeInstance inst = player.getAttribute(spec.attribute);
                if (inst != null && inst.getModifier(spec.key) != null) {
                    inst.removeModifier(spec.key);
                }
            }
        }
    }

    // ================= conditioned_attribute =================
    static final class ConditionedAttributePower extends AttributePower {
        private final long tickRate;

        ConditionedAttributePower(PowerDefinition def, Player player, OriginManager mgr, Plugin plugin) {
            super(def, player, mgr, plugin);
            this.tickRate = def.getLong("tick_rate", 20);
        }

        @Override
        public void tick(long tickCount) {
            if (tickCount % tickRate != 0) return;
            if (active()) {
                apply();
            } else {
                remove();
            }
        }
    }

    // ================= modify_damage_dealt / taken =================
    static final class DamageModifierPower extends Power {
        private final boolean dealt;
        private final double amount;
        private final DamageConditions.DamageCondition damageCondition;

        DamageModifierPower(PowerDefinition def, Player player, OriginManager mgr, boolean dealt) {
            super(def, player, mgr);
            this.dealt = dealt;
            JsonObject mod = def.raw().has("modifier") ? def.raw().getAsJsonObject("modifier") : new JsonObject();
            this.amount = mod.has("amount") ? mod.get("amount").getAsDouble() : 0;
            this.damageCondition = DamageConditions.parse(
                    def.raw().has("damage_condition") ? def.raw().getAsJsonObject("damage_condition") : null, null);
        }

        @Override
        public double modifyDamageDealt(double damage, EntityDamageEvent event) {
            if (!dealt || !active() || !conditionPass(event)) return damage;
            return applyOp(damage);
        }

        @Override
        public double modifyDamageTaken(double damage, EntityDamageEvent event) {
            if (dealt || !active() || !conditionPass(event)) return damage;
            return applyOp(damage);
        }

        private boolean conditionPass(EntityDamageEvent event) {
            return damageCondition == null || damageCondition.test(event);
        }

        private double applyOp(double damage) {
            String op = def.raw().getAsJsonObject("modifier").get("operation").getAsString();
            return switch (op) {
                case "add_value", "add_base_early" -> damage + amount;
                case "multiply_base_multiplicative", "multiply_total_multiplicative",
                     "add_multiplied_total", "add_multiplied_base" -> damage * (1 + amount);
                default -> damage;
            };
        }
    }

    // ================= 共享解析 =================
    record ModifierSpec(Attribute attribute, NamespacedKey key, double amount, AttributeModifier.Operation operation) {
    }

    static List<ModifierSpec> parseModifiers(JsonElement el, String powerId, Plugin plugin) {
        List<ModifierSpec> out = new ArrayList<>();
        List<JsonObject> objs = new ArrayList<>();
        if (el != null && el.isJsonObject()) {
            objs.add(el.getAsJsonObject());
        } else if (el != null && el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) objs.add(e.getAsJsonObject());
        }
        NamespacedKey key = new NamespacedKey(plugin, "power_" + powerId.replace(':', '_').replace('/', '_'));
        for (JsonObject o : objs) {
            Attribute attr = parseAttribute(o.get("attribute").getAsString());
            if (attr == null) continue;
            double amount = o.get("amount").getAsDouble();
            String op = o.has("operation") ? o.get("operation").getAsString() : "add_value";
            out.add(new ModifierSpec(attr, key, amount, mapOperation(op)));
        }
        return out;
    }

    static Attribute parseAttribute(String name) {
        String s = name.replace("minecraft:", "").replace('.', '_').toUpperCase(Locale.ROOT);
        try {
            return Attribute.valueOf(s);
        } catch (IllegalArgumentException e) {
            System.out.println("[OriginsPaper] 未知属性（跳过该修改器）: " + name);
            return null;
        }
    }

    static AttributeModifier.Operation mapOperation(String op) {
        return switch (op) {
            case "add_value", "add_base_early" -> AttributeModifier.Operation.ADD_NUMBER;
            case "add_multiplied_base" -> AttributeModifier.Operation.ADD_SCALAR;
            case "add_multiplied_total", "multiply_total_multiplicative", "multiply_base_multiplicative" ->
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1;
            default -> AttributeModifier.Operation.ADD_NUMBER;
        };
    }
}
