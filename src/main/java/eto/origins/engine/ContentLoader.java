package eto.origins.engine;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/** 内容加载器：首次运行把 resources 里的 JSON 拷到数据目录，然后解析。 */
public final class ContentLoader {

    private final Plugin plugin;
    private final OriginManager manager;
    private final PowerRegistry registry;
    private final Gson gson = new Gson();

    public ContentLoader(Plugin plugin, OriginManager manager, PowerRegistry registry) {
        this.plugin = plugin;
        this.manager = manager;
        this.registry = registry;
    }

    public void reload() {
        try {
            copyDefaults();
        } catch (IOException e) {
            plugin.getLogger().severe("默认内容拷贝失败: " + e.getMessage());
        }

        Path base = plugin.getDataFolder().toPath().resolve("origins");
        Map<String, String[]> names = loadNames(base.resolve("names.json"));

        Map<String, Origin> origins = new HashMap<>();
        Map<String, PowerDefinition> powers = new HashMap<>();

        listJson(base.resolve("origins")).forEach(p -> {
            String id = "origins:" + baseName(p);
            try (var reader = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                JsonObject json = gson.fromJson(reader, JsonObject.class);
                String key = baseName(p);
                String[] nd = names.getOrDefault(key, new String[]{key, ""});
                Origin origin = new Origin(id, nd[0], nd[1],
                        json.has("icon") && json.getAsJsonObject("icon").has("id")
                                ? json.getAsJsonObject("icon").get("id").getAsString() : "minecraft:book",
                        json.has("order") ? json.get("order").getAsInt() : 99,
                        json.has("impact") ? json.get("impact").getAsInt() : 0);
                origins.put(id, origin);
                List<String> powerIds = new ArrayList<>();
                if (json.has("powers")) {
                    for (var e : json.getAsJsonArray("powers")) {
                        powerIds.add(e.getAsString());
                    }
                }
                manager.setOriginPowers(id, powerIds);
            } catch (Exception e) {
                plugin.getLogger().warning("解析起源失败 " + p + ": " + e.getMessage());
            }
        });

        listJson(base.resolve("powers")).forEach(p -> {
            String id = "origins:" + baseName(p);
            try (var reader = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                JsonObject json = gson.fromJson(reader, JsonObject.class);
                if (!json.has("type")) return;
                String type = json.get("type").getAsString();
                Condition cond = json.has("condition")
                        ? Conditions.parse(json.getAsJsonObject("condition"), registry) : null;
                powers.put(id, new PowerDefinition(id, type, json, cond));
            } catch (Exception e) {
                plugin.getLogger().warning("解析 power 失败 " + p + ": " + e.getMessage());
            }
        });

        manager.loadContent(origins, powers);
        plugin.getLogger().info("内容加载完成: " + origins.size() + " 个起源, " + powers.size() + " 个 power 定义");
    }

    private Map<String, String[]> loadNames(Path file) {
        Map<String, String[]> out = new HashMap<>();
        if (Files.exists(file)) {
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject json = gson.fromJson(reader, JsonObject.class);
                for (var e : json.entrySet()) {
                    if (e.getValue().isJsonObject()) {
                        JsonObject o = e.getValue().getAsJsonObject();
                        out.put(e.getKey(), new String[]{
                                o.has("name") ? o.get("name").getAsString() : e.getKey(),
                                o.has("description") ? o.get("description").getAsString() : ""});
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("names.json 解析失败: " + e.getMessage());
            }
        }
        return out;
    }

    private List<Path> listJson(Path dir) {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(f -> f.getFileName().toString().endsWith(".json")).forEach(out::add);
        } catch (IOException e) {
            plugin.getLogger().warning("读取目录失败 " + dir + ": " + e.getMessage());
        }
        out.sort(null);
        return out;
    }

    /** 从插件 jar 把 origins/ 资源拷到数据目录（仅缺失时）。 */
    private void copyDefaults() throws IOException {
        Path base = plugin.getDataFolder().toPath().resolve("origins");
        Files.createDirectories(base.resolve("origins"));
        Files.createDirectories(base.resolve("powers"));
        java.io.File jarFile;
        try {
            jarFile = new java.io.File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            jarFile = null;
        }
        if (jarFile == null || !jarFile.exists()) return;
        try (JarFile jar = new JarFile(jarFile)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("origins/") || !name.endsWith(".json") || name.equals("origins/names.json")) {
                    continue;
                }
                Path target = base.resolve(name.substring("origins/".length()));
                if (Files.exists(target)) continue;
                Files.createDirectories(target.getParent());
                try (InputStream is = jar.getInputStream(entry)) {
                    Files.copy(is, target);
                }
            }
        }
        // names.json 每次随包更新（我们维护的文案）
        try (InputStream is = plugin.getResource("origins/names.json")) {
            if (is != null) {
                Files.copy(is, base.resolve("names.json"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static String baseName(Path p) {
        String n = p.getFileName().toString();
        return n.substring(0, n.length() - ".json".length());
    }
}
