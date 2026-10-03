package eto.origins.engine.powers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import eto.origins.engine.Action;
import eto.origins.engine.Actions;
import eto.origins.engine.ItemConditions;
import eto.origins.engine.OriginManager;
import eto.origins.engine.Power;
import eto.origins.engine.PowerDefinition;
import eto.origins.engine.PowerRegistry;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** 周期类 power：damage_over_time、exhaust、particle、action_on_item_use、stacking_status_effect。 */
public final class TimedPowers {

    private TimedPowers() {
    }

    public static void registerAll(PowerRegistry registry, org.bukkit.plugin.Plugin plugin) {
        registry.register(DamageOverTimePower::new, "origins:damage_over_time");
        registry.register(ExhaustPower::new, "origins:exhaust");
        registry.register(ParticlePower::new, "origins:particle");
        registry.register((def, p, m) -> new ActionOnItemUsePower(def, p, m), "origins:action_on_item_use");
        registry.register(StackingStatusEffectPower::new, "origins:stacking_status_effect");
    }

    // ================= damage_over_time =================
    static final class DamageOverTimePower extends Power {
        private final long interval;
        private final long onsetDelay;
        private final double damage;
        private long ticksSinceStart;

        DamageOverTimePower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
            this.interval = Math.max(1, def.getLong("interval", 20));
            this.onsetDelay = def.getLong("onset_delay", 0);
            this.damage = def.raw().has("damage") ? def.raw().get("damage").getAsDouble() : 1.0;
            // 注：自定义 damage_type（如 origins:hurt_by_water）服务端以通用伤害近似
        }

        @Override
        public void tick(long tickCount) {
            ticksSinceStart++;
            if (!active()) {
                ticksSinceStart = 0;
                return;
            }
            if (ticksSinceStart > onsetDelay && ticksSinceStart % interval == 0) {
                player.damage(damage);
            }
        }
    }

    // ================= exhaust =================
    static final class ExhaustPower extends Power {
        private final long interval;
        private final double exhaustion;

        ExhaustPower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
            this.interval = Math.max(1, def.getLong("interval", 20));
            this.exhaustion = def.raw().has("exhaustion") ? def.raw().get("exhaustion").getAsDouble() : 0.1;
        }

        @Override
        public void tick(long tickCount) {
            if (tickCount % interval != 0 || !active()) return;
            player.setExhaustion((float) (player.getExhaustion() + exhaustion));
        }
    }

    // ================= particle =================
    static final class ParticlePower extends Power {
        private final long frequency;
        private Particle particle;

        ParticlePower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
            this.frequency = Math.max(1, def.getLong("frequency", 4));
            try {
                this.particle = Particle.valueOf(def.raw().get("particle").getAsString()
                        .replace("minecraft:", "").toUpperCase());
            } catch (Exception e) {
                this.particle = null;
            }
        }

        @Override
        public void tick(long tickCount) {
            if (tickCount % frequency != 0 || !active() || particle == null) return;
            Location loc = player.getLocation().add(0, 1, 0);
            player.getWorld().spawnParticle(particle, loc, 3, 0.2, 0.4, 0.2, 0);
        }
    }

    // ================= action_on_item_use =================
    static final class ActionOnItemUsePower extends Power {
        private final ItemConditions.ItemCondition itemCondition;
        private final Action entityAction;

        ActionOnItemUsePower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
            this.itemCondition = ItemConditions.parse(
                    def.raw().has("item_condition") ? def.raw().getAsJsonObject("item_condition") : null, null);
            this.entityAction = def.raw().has("entity_action")
                    ? Actions.parseOrNoop(def.raw().get("entity_action"), null, def.id())
                    : null;
        }

        @Override
        public boolean onItemUse(ItemStack item) {
            if (!active() || itemCondition == null || !itemCondition.test(item)) return false;
            if (entityAction != null) {
                entityAction.execute(player, mgr);
            }
            return true;
        }
    }

    // ================= stacking_status_effect =================
    static final class StackingStatusEffectPower extends Power {
        private final int minStacks;
        private final int maxStacks;
        private final int durationPerStack;
        private final java.util.List<PotionEffectType> effects = new java.util.ArrayList<>();
        private final boolean showParticles;
        private int stacks;

        StackingStatusEffectPower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
            this.minStacks = def.raw().has("min_stacks") ? def.raw().get("min_stacks").getAsInt() : 0;
            this.maxStacks = def.raw().has("max_stacks") ? def.raw().get("max_stacks").getAsInt() : 10;
            this.durationPerStack = def.raw().has("duration_per_stack") ? def.raw().get("duration_per_stack").getAsInt() : 10;
            this.showParticles = def.raw().has("show_particles") && def.raw().get("show_particles").getAsBoolean();
            if (def.raw().has("effects")) {
                for (JsonElement e : def.raw().getAsJsonArray("effects")) {
                    String id = e.isJsonObject() ? e.getAsJsonObject().get("id").getAsString() : e.getAsString();
                    PotionEffectType t = PotionEffectType.getByKey(NamespacedKey.minecraft(id.replace("minecraft:", "")));
                    if (t != null) effects.add(t);
                }
            }
            this.stacks = Math.min(maxStacks, minStacks);
        }

        @Override
        public void tick(long tickCount) {
            if (tickCount % 20 != 0) return;
            if (active()) {
                stacks = Math.min(maxStacks, stacks + 1);
            } else {
                stacks = Math.max(minStacks, stacks - 1);
            }
            if (stacks > 0) {
                int duration = Math.min(stacks * durationPerStack, 400);
                for (PotionEffectType t : effects) {
                    player.addPotionEffect(new PotionEffect(t, duration, 0, true, showParticles, true));
                }
            }
        }
    }
}
