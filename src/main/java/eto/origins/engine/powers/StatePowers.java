package eto.origins.engine.powers;

import com.google.gson.JsonObject;
import eto.origins.engine.DamageConditions;
import eto.origins.engine.OriginManager;
import eto.origins.engine.Power;
import eto.origins.engine.PowerDefinition;
import eto.origins.engine.PowerRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** 状态类 power：免疫、夜视、隐身、水中呼吸、摔落修改。 */
public final class StatePowers {

    private StatePowers() {
    }

    public static void registerAll(PowerRegistry registry) {
        registry.register(InvulnerabilityPower::new, "origins:invulnerability");
        registry.register((def, p, m) -> InvulnerabilityPower.fire(), "origins:fire_immunity");
        registry.register(EffectImmunityPower::new, "origins:effect_immunity");
        registry.register(WaterBreathingPower::new, "origins:water_breathing");
        registry.register(NightVisionPower::new, "origins:night_vision");
        registry.register(ToggleNightVisionPower::new, "origins:toggle_night_vision");
        registry.register(InvisibilityPower::new, "origins:invisibility");
        registry.register(ModifyFallingPower::new, "origins:modify_falling", "apoli:modify_falling");
    }

    // ================= invulnerability / fire_immunity =================
    static class InvulnerabilityPower extends Power {
        private final DamageConditions.DamageCondition damageCondition;

        InvulnerabilityPower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
            this.damageCondition = DamageConditions.parse(
                    def.raw().has("damage_condition") ? def.raw().getAsJsonObject("damage_condition") : null, null);
        }

        static Power fire() {
            return new InvulnerabilityPower(null, null, null) {
                @Override
                public boolean invulnerableTo(EntityDamageEvent event) {
                    return switch (event.getCause()) {
                        case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> true;
                        default -> false;
                    };
                }
            };
        }

        @Override
        public boolean invulnerableTo(EntityDamageEvent event) {
            return active() && damageCondition != null && damageCondition.test(event);
        }
    }

    // ================= effect_immunity =================
    static final class EffectImmunityPower extends Power {
        private final Set<PotionEffectType> effects = new HashSet<>();

        EffectImmunityPower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
            if (def.raw().has("effects")) {
                for (var e : def.raw().getAsJsonArray("effects")) {
                    PotionEffectType t = PotionEffectType.getByKey(
                            NamespacedKey.minecraft(e.getAsString().replace("minecraft:", "")));
                    if (t != null) effects.add(t);
                }
            }
        }

        @Override
        public boolean immuneToEffect(PotionEffectType type) {
            return active() && effects.contains(type);
        }
    }

    // ================= water_breathing =================
    static final class WaterBreathingPower extends Power {
        WaterBreathingPower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
        }

        @Override
        public void tick(long tickCount) {
            if (tickCount % 10 != 0 || !active()) return;
            if (player.getEyeLocation().getBlock().isLiquid()) {
                player.setRemainingAir(player.getMaximumAir());
            }
        }
    }

    // ================= night_vision =================
    static class NightVisionPower extends Power {
        NightVisionPower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
        }

        @Override
        public void tick(long tickCount) {
            if (tickCount % 40 != 5) return;
            if (active()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 200, 0, true, false, false));
            }
        }
    }

    // ================= toggle_night_vision =================
    static final class ToggleNightVisionPower extends NightVisionPower {
        ToggleNightVisionPower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
        }

        @Override
        public void tick(long tickCount) {
            if (tickCount % 40 != 5) return;
            if (active() && mgr.isPowerActive(player, "*:*_toggle")) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 200, 0, true, false, false));
            }
        }
    }

    // ================= invisibility =================
    static final class InvisibilityPower extends Power {
        InvisibilityPower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
        }

        @Override
        public void tick(long tickCount) {
            if (tickCount % 40 != 10) return;
            if (active()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 200, 0, true, false, false));
            }
        }

        @Override
        public boolean suppressMobTarget() {
            return active();
        }
    }

    // ================= modify_falling =================
    static final class ModifyFallingPower extends Power {
        private final boolean takeFallDamage;

        ModifyFallingPower(PowerDefinition def, Player player, OriginManager mgr) {
            super(def, player, mgr);
            this.takeFallDamage = !def.raw().has("take_fall_damage") || def.raw().get("take_fall_damage").getAsBoolean();
            // 注：velocity 乘数（缓降手感）需要客户端配合，服务端仅保证不受伤
        }

        @Override
        public boolean invulnerableTo(EntityDamageEvent event) {
            return active() && !takeFallDamage && event.getCause() == EntityDamageEvent.DamageCause.FALL;
        }
    }
}
