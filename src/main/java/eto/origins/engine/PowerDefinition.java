package eto.origins.engine;

import com.google.gson.JsonObject;

/** power 原语定义（解析自 powers/*.json）。 */
public record PowerDefinition(String id, String type, JsonObject raw, Condition condition) {

    /** 取 raw 里的长整型字段。 */
    public long getLong(String key, long fallback) {
        return raw.has(key) ? raw.get(key).getAsLong() : fallback;
    }
}
