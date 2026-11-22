package com.dttdrt.addons.core.lang;

import com.dttdrt.addons.core.config.ConfigPaths;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side localization helper supporting Minecraft's built-in resource pack languages
 * (en_us / zh_cn) plus optional external overrides in config/dttdrtaddons/lang/<lang>.json.
 */
public final class ModLang {

    private static final String DEFAULT_LANG = "en_us";
    private static final Map<String, String> EN_FALLBACKS = loadBundledEnglishFallbacks();
    private static volatile String loadedExternalLang = "";
    private static volatile Map<String, String> externalOverrides = Collections.emptyMap();
    private static volatile long lastExternalCheckMs = 0L;

    private ModLang() {
    }

    private static Map<String, String> loadBundledEnglishFallbacks() {
        Map<String, String> map = new ConcurrentHashMap<>();
        try (InputStream is = ModLang.class.getResourceAsStream("/assets/dttdrtaddons/lang/en_us.json")) {
            if (is != null) {
                JsonObject obj = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                    if (e.getValue() != null && e.getValue().isJsonPrimitive()) {
                        map.put(e.getKey(), e.getValue().getAsString());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return map;
    }

    public static String getCurrentLanguageCode() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getLanguageManager() != null) {
                String selected = mc.getLanguageManager().getSelected();
                if (selected != null && !selected.isBlank()) {
                    return selected.toLowerCase(Locale.ROOT);
                }
            }
        } catch (Throwable ignored) {
        }
        return DEFAULT_LANG;
    }

    public static void reloadExternalOverrides() {
        lastExternalCheckMs = 0L;
        ensureExternalOverridesLoaded(getCurrentLanguageCode());
    }

    private static void ensureExternalOverridesLoaded(String langCode) {
        long now = System.currentTimeMillis();
        if (langCode.equals(loadedExternalLang) && now - lastExternalCheckMs < 5_000L) {
            return;
        }
        synchronized (ModLang.class) {
            now = System.currentTimeMillis();
            if (langCode.equals(loadedExternalLang) && now - lastExternalCheckMs < 5_000L) {
                return;
            }
            loadedExternalLang = langCode;
            lastExternalCheckMs = now;
            Path path = ConfigPaths.getConfigPath()
                    .resolve("lang")
                    .resolve(langCode + ".json");
            if (!Files.isRegularFile(path)) {
                externalOverrides = Collections.emptyMap();
                return;
            }
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                Map<String, String> parsed = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                    if (e.getValue() != null && e.getValue().isJsonPrimitive()) {
                        String val = e.getValue().getAsString();
                        if (!val.isEmpty()) {
                            parsed.put(e.getKey(), val);
                        }
                    }
                }
                externalOverrides = parsed;
            } catch (Throwable ignored) {
                externalOverrides = Collections.emptyMap();
            }
        }
    }

    public static boolean hasCustomTranslation(String key) {
        if (key == null || key.isEmpty()) return false;
        ensureExternalOverridesLoaded(getCurrentLanguageCode());
        if (externalOverrides.containsKey(key)) {
            return true;
        }
        try {
            Language lang = Language.getInstance();
            return lang != null && lang.has(key);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String tr(String key, Object... args) {
        return trWithDefault(key, EN_FALLBACKS.getOrDefault(key, key), args);
    }

    public static String trWithDefault(String key, String fallback, Object... args) {
        if (key == null || key.isEmpty()) {
            return fallback != null ? fallback : "";
        }
        String langCode = getCurrentLanguageCode();
        ensureExternalOverridesLoaded(langCode);

        String template = externalOverrides.get(key);
        if (template == null || template.isEmpty()) {
            try {
                Language lang = Language.getInstance();
                if (lang != null && lang.has(key)) {
                    template = lang.getOrDefault(key);
                }
            } catch (Throwable ignored) {
            }
        }
        if (template == null || template.isEmpty()) {
            template = EN_FALLBACKS.getOrDefault(key, fallback != null ? fallback : key);
        }

        if (args != null && args.length > 0) {
            try {
                return String.format(Locale.ROOT, template, args);
            } catch (Exception ignored) {
            }
        }
        return template;
    }
}
