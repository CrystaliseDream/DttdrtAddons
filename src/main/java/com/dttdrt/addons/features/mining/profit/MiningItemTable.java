package com.dttdrt.addons.features.mining.profit;

import com.dttdrt.addons.core.lang.ModLang;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SkyBlock tracked item registry loaded from bundled mining_items.json.
 * Resolves hover names, enchanted books, pets, custom NBT item IDs, compacting chains,
 * and preserves DttdrtAddons' exact gemstone NPC base prices.
 */
public final class MiningItemTable {

    public static final String COINS_KEY = "SKYBLOCK_COIN";

    public record Entry(
            String name,
            String key,
            String compactsFrom,
            int compactRatio,
            int defaultColorArgb,
            double npcPrice
    ) {}

    private static final Pattern BOOK_NAME_PATTERN =
            Pattern.compile("^(Enchanted Book \\(.+?) (I|II|III|IV|V|VI|VII|VIII|IX|X)\\)$");
    private static final List<String> ROMAN = List.of("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X");
    private static final Pattern PET_NAME_PATTERN = Pattern.compile("^\\[Lvl \\d+] (.+?) \\(([^)]+)\\)$");
    private static final Pattern LEADING_GLYPH = Pattern.compile("^[^\\p{L}\\p{N}\\[(]+");
    private static final Pattern NPC_AMOUNT_SUFFIX = Pattern.compile(".+ \u00A78x\\d+$");
    private static final Pattern GEM_STRIP_NON_ALNUM = Pattern.compile("[^a-zA-Z0-9 ]");

    private static final Map<String, Integer> GEMSTONE_BASE_NPC = Map.ofEntries(
            Map.entry("RUBY", 3),
            Map.entry("AMBER", 4),
            Map.entry("JADE", 4),
            Map.entry("AMETHYST", 4),
            Map.entry("OPAL", 4),
            Map.entry("SAPPHIRE", 4),
            Map.entry("TOPAZ", 5),
            Map.entry("ONYX", 6),
            Map.entry("AQUAMARINE", 6),
            Map.entry("CITRINE", 6),
            Map.entry("PERIDOT", 6),
            Map.entry("JASPER", 6)
    );

    private static final Map<String, Integer> GEMSTONE_COLORS = Map.ofEntries(
            Map.entry("RUBY", 0xFFFF3344),
            Map.entry("AMBER", 0xFFED8B23),
            Map.entry("JADE", 0xFF9DF920),
            Map.entry("AMETHYST", 0xFFB446FF),
            Map.entry("OPAL", 0xFFF2F2FA),
            Map.entry("SAPPHIRE", 0xFF288CF5),
            Map.entry("TOPAZ", 0xFFFCDE29),
            Map.entry("ONYX", 0xFF9CA3AF),
            Map.entry("AQUAMARINE", 0xFF28A0DC),
            Map.entry("CITRINE", 0xFFC8823C),
            Map.entry("PERIDOT", 0xFF46A53C),
            Map.entry("JASPER", 0xFFFF3CC8)
    );

    private static final Map<String, Entry> BY_NAME = new HashMap<>();
    private static final Map<String, Entry> BY_KEY = new HashMap<>();
    private static final Map<String, Set<String>> BY_BASE_NAME = new HashMap<>();
    private static final Map<String, Integer> LEARNED_COLORS = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;

    private MiningItemTable() {}

    public static synchronized void ensureLoaded() {
        if (initialized) return;
        initialized = true;

        try (InputStream is = MiningItemTable.class.getResourceAsStream("/assets/dttdrtaddons/mining_items.json")) {
            if (is == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("items");
            if (arr == null) return;

            for (JsonElement elem : arr) {
                if (!elem.isJsonObject()) continue;
                JsonObject obj = elem.getAsJsonObject();
                String name = obj.get("name").getAsString();
                String key = obj.get("key").getAsString();
                String compactsFrom = obj.has("compactsFrom") && !obj.get("compactsFrom").isJsonNull()
                        ? obj.get("compactsFrom").getAsString() : null;
                int compactRatio = obj.has("compactRatio") && !obj.get("compactRatio").isJsonNull()
                        ? obj.get("compactRatio").getAsInt() : 0;
                String colorCode = obj.has("color") && !obj.get("color").isJsonNull()
                        ? obj.get("color").getAsString() : null;
                double rawNpc = obj.has("npc") && !obj.get("npc").isJsonNull()
                        ? obj.get("npc").getAsDouble() : 0.0;

                double npc = overrideGemstoneNpc(key, rawNpc);
                int colorArgb = resolveDefaultColor(key, colorCode);
                if (compactsFrom == null) {
                    String[] gemComp = inferGemstoneCompaction(key);
                    if (gemComp != null) {
                        compactsFrom = gemComp[0];
                        compactRatio = 80;
                    }
                }

                Entry entry = new Entry(name, key, compactsFrom, compactRatio, colorArgb, npc);
                BY_NAME.put(name, entry);
                BY_KEY.put(key, entry);

                String alias = bookAlias(name);
                if (alias != null) {
                    BY_NAME.putIfAbsent(alias, entry);
                }

                String base = baseName(name);
                BY_BASE_NAME.computeIfAbsent(base, k -> new HashSet<>()).add(key);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Virtual entry for coins dropped into the purse
        Entry coinEntry = new Entry("Coins", COINS_KEY, null, 0, 0xFFFFD166, 1.0);
        BY_KEY.putIfAbsent(COINS_KEY, coinEntry);
    }

    private static double overrideGemstoneNpc(String key, double fallback) {
        if (key == null || !key.endsWith("_GEM")) return fallback;
        String[] parts = key.split("_");
        if (parts.length != 3 || !"GEM".equals(parts[2])) return fallback;
        Integer baseNpc = GEMSTONE_BASE_NPC.get(parts[1]);
        if (baseNpc == null) return fallback;
        return switch (parts[0]) {
            case "ROUGH" -> baseNpc;
            case "FLAWED" -> baseNpc * 80.0;
            case "FINE" -> baseNpc * 6400.0;
            case "FLAWLESS" -> baseNpc * 512_000.0;
            default -> fallback;
        };
    }

    private static String[] inferGemstoneCompaction(String key) {
        if (key == null || !key.endsWith("_GEM")) return null;
        String[] parts = key.split("_");
        if (parts.length != 3 || !"GEM".equals(parts[2])) return null;
        String gem = parts[1];
        String label = gem.substring(0, 1).toUpperCase(Locale.ROOT) + gem.substring(1).toLowerCase(Locale.ROOT);
        return switch (parts[0]) {
            case "FLAWED" -> new String[]{"Rough " + label + " Gemstone"};
            case "FINE" -> new String[]{"Flawed " + label + " Gemstone"};
            case "FLAWLESS" -> new String[]{"Fine " + label + " Gemstone"};
            default -> null;
        };
    }

    private static int resolveDefaultColor(String key, String colorCode) {
        if (key != null && key.endsWith("_GEM")) {
            String[] parts = key.split("_");
            if (parts.length == 3) {
                Integer c = GEMSTONE_COLORS.get(parts[1]);
                if (c != null) return c;
            }
        }
        return mcColorToArgb(colorCode, 0xFFE5E7EB);
    }

    private static int mcColorToArgb(String code, int fallback) {
        if (code == null || code.length() < 2) return fallback;
        char c = Character.toLowerCase(code.charAt(code.length() - 1));
        return switch (c) {
            case '0' -> 0xFF9CA3AF;
            case '1' -> 0xFF3B82F6;
            case '2' -> 0xFF22C55E;
            case '3' -> 0xFF06B6D4;
            case '4' -> 0xFFEF4444;
            case '5' -> 0xFFA855F7;
            case '6' -> 0xFFF59E0B;
            case '7' -> 0xFFD1D5DB;
            case '8' -> 0xFF9CA3AF;
            case '9' -> 0xFF60A5FA;
            case 'a' -> 0xFF4ADE80;
            case 'b' -> 0xFF38BDF8;
            case 'c' -> 0xFFF87171;
            case 'd' -> 0xFFE879F9;
            case 'e' -> 0xFFFACC15;
            case 'f' -> 0xFFF3F4F6;
            default -> fallback;
        };
    }

    private static String bookAlias(String name) {
        Matcher m = BOOK_NAME_PATTERN.matcher(name);
        if (!m.matches()) return null;
        int idx = ROMAN.indexOf(m.group(2)) + 1;
        if (idx <= 0) return null;
        return m.group(1) + " " + idx + ")";
    }

    private static String baseName(String name) {
        Matcher m = PET_NAME_PATTERN.matcher(name);
        return m.matches() ? m.group(1) : name;
    }

    public static Entry get(String displayName) {
        ensureLoaded();
        return displayName == null ? null : BY_NAME.get(displayName);
    }

    public static Entry getByKey(String key) {
        ensureLoaded();
        return key == null ? null : BY_KEY.get(key);
    }

    public static Collection<String> getAllKeys() {
        ensureLoaded();
        return BY_KEY.keySet();
    }

    public static boolean isGemstoneKey(String key) {
        return key != null && key.endsWith("_GEM");
    }

    public static boolean isRoughGemstoneKey(String key) {
        return key != null && key.startsWith("ROUGH_") && key.endsWith("_GEM");
    }

    public static boolean isFlawedGemstoneKey(String key) {
        return key != null && key.startsWith("FLAWED_") && key.endsWith("_GEM");
    }

    public static String toFlawedGemstoneKey(String roughKey) {
        if (!isRoughGemstoneKey(roughKey)) return roughKey;
        return "FLAWED_" + roughKey.substring("ROUGH_".length());
    }

    public static String toFineGemstoneKey(String key) {
        if (isRoughGemstoneKey(key)) {
            return "FINE_" + key.substring("ROUGH_".length());
        }
        if (isFlawedGemstoneKey(key)) {
            return "FINE_" + key.substring("FLAWED_".length());
        }
        return key;
    }

    public static Map<String, Double> mergeToFineGemstones(Map<String, Long> rawCounts) {
        Map<String, Double> merged = new java.util.LinkedHashMap<>();
        if (rawCounts == null || rawCounts.isEmpty()) {
            return merged;
        }
        for (Map.Entry<String, Long> entry : rawCounts.entrySet()) {
            long amt = entry.getValue() != null ? entry.getValue() : 0L;
            if (amt <= 0L) continue;
            String key = entry.getKey();
            if (isRoughGemstoneKey(key)) {
                merged.merge(toFineGemstoneKey(key), amt / 6400.0, Double::sum);
            } else if (isFlawedGemstoneKey(key)) {
                merged.merge(toFineGemstoneKey(key), amt / 80.0, Double::sum);
            } else {
                merged.merge(key, (double) amt, Double::sum);
            }
        }
        return merged;
    }

    public static String getLocalizedName(String key, String fallbackName) {
        if (key == null || key.isEmpty()) return fallbackName != null ? fallbackName : "";
        String langKey = "dttdrtaddons.item." + key.toLowerCase(Locale.ROOT);
        if (ModLang.hasCustomTranslation(langKey)) {
            return ModLang.trWithDefault(langKey, fallbackName);
        }
        return fallbackName != null ? fallbackName : key;
    }

    public static CaptureBuffer.Compaction resolveCompaction(String key) {
        Entry entry = getByKey(key);
        if (entry == null || entry.compactRatio() <= 0 || entry.compactsFrom() == null) return null;
        Entry parent = get(entry.compactsFrom());
        if (parent == null) return null;
        return new CaptureBuffer.Compaction(parent.key(), entry.compactRatio());
    }

    public static Set<String> getChatKeys(String rawName) {
        ensureLoaded();
        if (rawName == null || rawName.isBlank()) return Set.of();
        String cleaned = stripFormatting(rawName).trim();
        Entry direct = BY_NAME.get(cleaned);
        if (direct != null) return Set.of(direct.key());

        String stripped = LEADING_GLYPH.matcher(cleaned).replaceFirst("").trim();
        Entry strippedEntry = BY_NAME.get(stripped);
        if (strippedEntry != null) return Set.of(strippedEntry.key());

        Set<String> byBase = BY_BASE_NAME.get(stripped);
        return byBase != null ? byBase : Set.of();
    }

    public static int getColor(String key) {
        Integer learned = LEARNED_COLORS.get(key);
        if (learned != null) return learned;
        Entry entry = getByKey(key);
        return entry != null ? entry.defaultColorArgb() : 0xFFE5E7EB;
    }

    public static Entry resolveStack(ItemStack stack) {
        ensureLoaded();
        if (stack == null || stack.isEmpty()) return null;

        String hoverRaw = stack.getHoverName().getString();
        String cleanName = cleanItemName(hoverRaw);
        if (cleanName.isBlank()) return null;

        if ("Enchanted Book".equals(cleanName)) {
            String bookName = getEnchantedBookName(stack);
            if (bookName != null && !bookName.isBlank()) {
                cleanName += " (" + bookName + ")";
            }
        } else if (cleanName.startsWith("[Lvl ")) {
            String tier = getPetTier(stack);
            if (tier != null) {
                cleanName += " (" + tier.toUpperCase(Locale.ROOT) + ")";
            }
        }

        Entry byName = BY_NAME.get(cleanName);
        if (byName != null) {
            rememberRarityColor(byName.key(), stack);
            return byName;
        }

        String customId = getCustomDataId(stack);
        if (customId != null && !customId.isBlank()) {
            Entry byId = BY_KEY.get(customId);
            if (byId != null) {
                rememberRarityColor(byId.key(), stack);
                return byId;
            }
        }
        return null;
    }

    public static String cleanItemName(String rawName) {
        if (rawName == null || rawName.isBlank()) return "";
        String cleaned = rawName;
        if (NPC_AMOUNT_SUFFIX.matcher(cleaned).matches()) {
            int lastSpace = cleaned.lastIndexOf(' ');
            if (lastSpace > 0) {
                cleaned = cleaned.substring(0, lastSpace);
            }
        }
        cleaned = stripFormatting(cleaned).trim();
        if (cleaned.endsWith(" Gemstone")) {
            cleaned = GEM_STRIP_NON_ALNUM.matcher(cleaned).replaceAll("").trim();
        }
        return cleaned;
    }

    private static void rememberRarityColor(String key, ItemStack stack) {
        if (LEARNED_COLORS.containsKey(key) || (key != null && key.endsWith("_GEM"))) return;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            List<Component> lines = lore.lines();
            for (int i = lines.size() - 1; i >= 0; i--) {
                String s = lines.get(i).getString().trim();
                String upper = stripFormatting(s).toUpperCase(Locale.ROOT);
                if (upper.startsWith("COMMON")) { LEARNED_COLORS.put(key, 0xFFF3F4F6); return; }
                if (upper.startsWith("UNCOMMON")) { LEARNED_COLORS.put(key, 0xFF4ADE80); return; }
                if (upper.startsWith("RARE")) { LEARNED_COLORS.put(key, 0xFF60A5FA); return; }
                if (upper.startsWith("EPIC")) { LEARNED_COLORS.put(key, 0xFFC084FC); return; }
                if (upper.startsWith("LEGENDARY")) { LEARNED_COLORS.put(key, 0xFFF59E0B); return; }
                if (upper.startsWith("MYTHIC")) { LEARNED_COLORS.put(key, 0xFFF472B6); return; }
                if (upper.startsWith("DIVINE")) { LEARNED_COLORS.put(key, 0xFF38BDF8); return; }
                if (upper.startsWith("SPECIAL")) { LEARNED_COLORS.put(key, 0xFFF87171); return; }
            }
        }
    }

    private static String getEnchantedBookName(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return null;
        for (Component line : lore.lines()) {
            String clean = stripFormatting(line.getString()).trim();
            if (!clean.isEmpty() && !clean.contains("Combinable in Anvil")) {
                return clean;
            }
        }
        return null;
    }

    private static String getCustomDataId(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return null;
        CompoundTag tag = cd.copyTag();
        Optional<String> id = tag.getString("id");
        return id.orElse(null);
    }

    private static String getPetTier(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return null;
        CompoundTag tag = cd.copyTag();
        if (!"PET".equals(tag.getString("id").orElse(""))) return null;
        String petInfo = tag.getString("petInfo").orElse("");
        if (petInfo.isEmpty()) return null;
        try {
            JsonObject obj = JsonParser.parseString(petInfo).getAsJsonObject();
            if (obj.has("tier") && obj.get("tier").isJsonPrimitive()) {
                return obj.get("tier").getAsString();
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static String resolveScathaPetKey(ItemStack stack) {
        return resolveTieredPetKey(stack, "SCATHA");
    }

    public static String resolveTieredPetKey(ItemStack stack, String targetType) {
        if (stack == null || stack.isEmpty() || targetType == null) return null;
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return null;
        CompoundTag tag = cd.copyTag();
        if (!"PET".equals(tag.getString("id").orElse(""))) return null;
        String petInfo = tag.getString("petInfo").orElse("");
        if (petInfo.isEmpty()) return null;
        try {
            JsonObject obj = JsonParser.parseString(petInfo).getAsJsonObject();
            String type = obj.has("type") && obj.get("type").isJsonPrimitive()
                    ? obj.get("type").getAsString().toUpperCase(Locale.ROOT)
                    : "";
            if (!targetType.equalsIgnoreCase(type)) return null;
            String tier = obj.has("tier") && obj.get("tier").isJsonPrimitive()
                    ? obj.get("tier").getAsString().toUpperCase(Locale.ROOT)
                    : "";
            return switch (tier) {
                case "RARE" -> targetType.toUpperCase(Locale.ROOT) + ";2";
                case "EPIC" -> targetType.toUpperCase(Locale.ROOT) + ";3";
                case "LEGENDARY" -> targetType.toUpperCase(Locale.ROOT) + ";4";
                default -> null;
            };
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Resolves an incoming Enchanted Book stack (used by Crystal Nucleus Loot Bundle fallback)
     * via Hypixel CUSTOM_DATA enchantments or tooltip lore.
     */
    public static String resolveNucleusBookKeyFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd != null) {
            CompoundTag tag = cd.copyTag();
            Optional<CompoundTag> enchOpt = tag.getCompound("enchantments");
            if (enchOpt.isPresent()) {
                CompoundTag ench = enchOpt.get();
                if (ench.contains("fortune")) {
                    int lvl = ench.getInt("fortune").orElse(4);
                    return "ENCHANTMENT_FORTUNE_" + Math.max(1, lvl);
                }
                if (ench.contains("lapidary")) {
                    int lvl = ench.getInt("lapidary").orElse(1);
                    return "ENCHANTMENT_LAPIDARY_" + Math.max(1, lvl);
                }
            }
        }
        String cleanHover = cleanItemName(stack.getHoverName().getString());
        if ("Enchanted Book".equals(cleanHover)) {
            String bookName = getEnchantedBookName(stack);
            if (bookName != null) {
                if (bookName.equalsIgnoreCase("Fortune IV") || bookName.equalsIgnoreCase("Fortune 4")) {
                    return "ENCHANTMENT_FORTUNE_4";
                }
                if (bookName.equalsIgnoreCase("Lapidary I") || bookName.equalsIgnoreCase("Lapidary 1")) {
                    return "ENCHANTMENT_LAPIDARY_1";
                }
                Entry e = get("Enchanted Book (" + bookName + ")");
                if (e != null) return e.key();
            }
        }
        return null;
    }

    public static String stripFormatting(String text) {
        if (text == null) return "";
        String stripped = ChatFormatting.stripFormatting(text);
        return stripped != null ? stripped : text;
    }
}
