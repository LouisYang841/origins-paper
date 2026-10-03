package eto.origins.engine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 实体动作解析器（entity_action types 子集）。 */
public final class Actions {

    private Actions() {
    }

    public static Action parse(JsonObject json, PowerRegistry registry) {
        if (json == null) {
            return null;
        }
        String type = Conditions.shortName(json.get("type").getAsString());
        return switch (type) {
            case "execute_command" -> {
                String command = json.get("command").getAsString();
                boolean serverSide = !json.has("permission_level") || json.get("permission_level").getAsInt() >= 4;
                yield (p, m) -> {
                    var server = org.bukkit.Bukkit.getServer();
                    if (serverSide) {
                        server.dispatchCommand(server.getConsoleSender(), command.replace(" ", "").isEmpty() ? command : command);
                    } else {
                        p.performCommand(command.startsWith("/") ? command.substring(1) : command);
                    }
                };
            }
            case "damage" -> {
                double amount = json.get("amount").getAsDouble();
                yield (p, m) -> DamageConditions.damage(p, amount, null);
            }
            case "set_on_fire" -> {
                int ticks = json.get("duration").getAsInt();
                yield (p, m) -> p.setFireTicks(ticks);
            }
            case "extinguish" -> {
                yield (p, m) -> p.setFireTicks(0);
            }
            case "apply_potion_effect" -> {
                PotionEffectType effect = PotionEffectType.getByKey(
                        NamespacedKey.minecraft(json.get("effect").getAsString().replace("minecraft:", "")));
                if (effect == null) {
                    warn(registry, type + ":" + json.get("effect").getAsString());
                    yield (p, m) -> {
                    };
                }
                int duration = json.has("duration") ? json.get("duration").getAsInt() : 100;
                int amplifier = json.has("amplifier") ? json.get("amplifier").getAsInt() : 0;
                boolean ambient = !json.has("ambient") || json.get("ambient").getAsBoolean();
                boolean particles = json.has("show_particles") && json.get("show_particles").getAsBoolean();
                yield (p, m) -> p.addPotionEffect(new PotionEffect(effect, duration, amplifier, ambient, particles));
            }
            case "gain_air" -> {
                int value = json.get("value").getAsInt();
                yield (p, m) -> p.setRemainingAir(Math.min(p.getMaximumAir(), p.getRemainingAir() + value));
            }
            case "play_sound" -> {
                String sound = json.get("sound").getAsString();
                float volume = json.has("volume") ? json.get("volume").getAsFloat() : 1.0f;
                float pitch = json.has("pitch") ? json.get("pitch").getAsFloat() : 1.0f;
                yield (p, m) -> {
                    try {
                        p.getWorld().playSound(p.getLocation(),
                                Sound.valueOf(sound.replace("minecraft:", "").toUpperCase(Locale.ROOT)), volume, pitch);
                    } catch (IllegalArgumentException ignored) {
                        // 未知音效，忽略
                    }
                };
            }
            case "spawn_particle" -> {
                String particle = json.get("particle").getAsString();
                yield (p, m) -> {
                    try {
                        Location loc = p.getLocation().add(0, 1, 0);
                        p.getWorld().spawnParticle(org.bukkit.Particle.valueOf(
                                particle.replace("minecraft:", "").toUpperCase(Locale.ROOT)), loc, 4, 0.2, 0.3, 0.2, 0);
                    } catch (IllegalArgumentException ignored) {
                    }
                };
            }
            case "toggle_power" -> {
                String pattern = json.get("power").getAsString();
                yield (p, m) -> m.togglePowerByPattern(p, pattern);
            }
            case "toggle" -> {
                // 按 id 切换指定 power（如 phantomize_callback 重生后恢复幻影化）
                String id = json.get("power").getAsString();
                yield (p, m) -> {
                    for (eto.origins.engine.Power power : m.powersOf(p)) {
                        if (power.definition().id().equals(id) && power instanceof ActivePower ap) {
                            ap.onTrigger();
                            return;
                        }
                    }
                };
            }
            case "give" -> {
                JsonObject stack = json.getAsJsonObject("stack");
                Material mat = Material.matchMaterial(stack.get("id").getAsString());
                int amount = stack.has("amount") ? stack.get("amount").getAsInt() : 1;
                yield (p, m) -> {
                    if (mat != null) {
                        p.getInventory().addItem(new ItemStack(mat, amount));
                    }
                };
            }
            case "if_else" -> {
                Condition cond = Conditions.parse(json.has("condition") ? json.getAsJsonObject("condition") : null, registry);
                Action ifAction = parseOrNoop(json.get("if_action"), registry, "if_else.if");
                Action elseAction = json.has("else_action")
                        ? parseOrNoop(json.get("else_action"), registry, "if_else.else") : (p, m) -> {
                };
                yield (p, m) -> {
                    if (cond == null || cond.test(p, m)) {
                        ifAction.execute(p, m);
                    } else {
                        elseAction.execute(p, m);
                    }
                };
            }
            case "block_action_at" -> {
                // 无通用方块动作 VM；仅 no-op 兜底（webbing 由 TargetActionOnHitPower 特化处理）
                warn(registry, "block_action_at @ 非特化上下文");
                yield (p, m) -> {
                };
            }
            default -> null;
        };
    }

    /** entity_action 字段可能是数组；按顺序执行。 */
    public static Action parseListOrSingle(JsonElement el, PowerRegistry registry, String context) {
        if (el != null && el.isJsonArray()) {
            List<Action> list = new ArrayList<>();
            for (JsonElement e : el.getAsJsonArray()) {
                list.add(parseOrNoop(e, registry, context));
            }
            return (p, m) -> list.forEach(a -> a.execute(p, m));
        }
        return parseOrNoop(el, registry, context);
    }

    private static void warn(PowerRegistry registry, String msg) {
        if (registry != null) {
            registry.warnUnknownAction(msg);
        }
    }

    /** 宽松解析：null/未知返回 no-op 并警告一次。 */
    public static Action parseOrNoop(JsonElement el, PowerRegistry registry, String context) {
        if (el == null || !el.isJsonObject()) {
            return (p, m) -> {
            };
        }
        Action a = parse(el.getAsJsonObject(), registry);
        if (a == null) {
            warn(registry, Conditions.shortName(el.getAsJsonObject().get("type").getAsString()) + " @ " + context);
            return (p, m) -> {
            };
        }
        return a;
    }
}
