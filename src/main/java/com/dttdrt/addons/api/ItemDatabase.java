package com.dttdrt.addons.api;

import com.dttdrt.addons.features.market.model.SkyblockItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * High-performance Skyblock Item Database with full Hypixel catalog sync & fuzzy matching engine.
 * Synchronizes all 5,000+ Skyblock items & Bazaar products asynchronously on startup.
 */
public class ItemDatabase {
    private static final String HYPIXEL_ITEMS_URL = "https://api.hypixel.net/v2/resources/skyblock/items";
    private static final String HYPIXEL_BAZAAR_URL = "https://api.hypixel.net/v2/skyblock/bazaar";
    private static final String COFL_ITEMS_URL = "https://sky.coflnet.com/api/items";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final Map<String, SkyblockItem> ITEMS_BY_ID = new ConcurrentHashMap<>();
    private static final Map<String, String> ABBREVIATIONS = new HashMap<>();
    private static volatile boolean catalogSynced = false;

    static {
        // Common Skyblock community acronyms & abbreviations
        ABBREVIATIONS.put("hyp", "HYPERION");
        ABBREVIATIONS.put("hype", "HYPERION");
        ABBREVIATIONS.put("hyperion", "HYPERION");
        ABBREVIATIONS.put("aote", "ASPECT_OF_THE_END");
        ABBREVIATIONS.put("aotv", "ASPECT_OF_THE_VOID");
        ABBREVIATIONS.put("aotd", "ASPECT_OF_THE_DRAGON");
        ABBREVIATIONS.put("term", "TERMINATOR");
        ABBREVIATIONS.put("terminator", "TERMINATOR");
        ABBREVIATIONS.put("juju", "JUJU_SHORTBOW");
        ABBREVIATIONS.put("sc3000", "SUPER_COMPACTOR_3000");
        ABBREVIATIONS.put("sc3k", "SUPER_COMPACTOR_3000");
        ABBREVIATIONS.put("gs", "GIANTS_SWORD");
        ABBREVIATIONS.put("sf", "SHADOW_FURY");
        ABBREVIATIONS.put("livid", "LIVID_DAGGER");
        ABBREVIATIONS.put("handle", "NECRON_HANDLE");
        ABBREVIATIONS.put("recomb", "RECOMBOBULATOR_3000");
        ABBREVIATIONS.put("cookie", "BOOSTER_COOKIE");
        ABBREVIATIONS.put("warden", "WARDEN_HELMET");
        ABBREVIATIONS.put("stonks", "STOCK_OF_STONKS");
        ABBREVIATIONS.put("claymore", "DARK_CLAYMORE");

        // Immediate seed items before remote catalog finishes loading
        register(new SkyblockItem("HYPERION", "Hyperion", "MYTHIC", "IRON_SWORD", "SWORD", false));
        register(new SkyblockItem("VALKYRIE", "Valkyrie", "MYTHIC", "IRON_SWORD", "SWORD", false));
        register(new SkyblockItem("SCYLLA", "Scylla", "MYTHIC", "IRON_SWORD", "SWORD", false));
        register(new SkyblockItem("ASTRAEA", "Astraea", "MYTHIC", "IRON_SWORD", "SWORD", false));
        register(new SkyblockItem("TERMINATOR", "Terminator", "LEGENDARY", "BOW", "BOW", false));
        register(new SkyblockItem("JUJU_SHORTBOW", "Juju Shortbow", "EPIC", "BOW", "BOW", false));
        register(new SkyblockItem("ASPECT_OF_THE_END", "Aspect of the End", "RARE", "DIAMOND_SWORD", "SWORD", false));
        register(new SkyblockItem("ASPECT_OF_THE_VOID", "Aspect of the Void", "EPIC", "DIAMOND_SPADE", "SWORD", false));
        register(new SkyblockItem("ASPECT_OF_THE_DRAGON", "Aspect of the Dragons", "LEGENDARY", "DIAMOND_SWORD", "SWORD", false));
        register(new SkyblockItem("SHADOW_FURY", "Shadow Fury", "LEGENDARY", "DIAMOND_SWORD", "SWORD", false));
        register(new SkyblockItem("LIVID_DAGGER", "Livid Dagger", "LEGENDARY", "IRON_SWORD", "SWORD", false));
        register(new SkyblockItem("GIANTS_SWORD", "Giant's Sword", "LEGENDARY", "IRON_SWORD", "SWORD", false));
        register(new SkyblockItem("NECRON_HANDLE", "Necron's Handle", "LEGENDARY", "STICK", "MATERIAL", false));
        register(new SkyblockItem("RECOMBOBULATOR_3000", "Recombobulator 3000", "LEGENDARY", "SKULL_ITEM", "MISC", true));
        register(new SkyblockItem("SUPER_COMPACTOR_3000", "Super Compactor 3000", "RARE", "DROPPER", "ACCESSORY", true));
        register(new SkyblockItem("BOOSTER_COOKIE", "Booster Cookie", "SPECIAL", "COOKIE", "CONSUMABLE", true));
        register(new SkyblockItem("STOCK_OF_STONKS", "Stock of Stonks", "COMMON", "PAPER", "MISC", true));
        register(new SkyblockItem("ENCHANTED_CARROT", "Enchanted Carrot", "UNCOMMON", "GOLDEN_CARROT", "BAZAAR", true));
        register(new SkyblockItem("ENCHANTED_DIAMOND", "Enchanted Diamond", "UNCOMMON", "DIAMOND", "BAZAAR", true));
        register(new SkyblockItem("ENCHANTED_DIAMOND_BLOCK", "Enchanted Diamond Block", "RARE", "DIAMOND_BLOCK", "BAZAAR", true));
    }

    /**
     * Asynchronously fetches the full Hypixel Skyblock items & bazaar product list.
     */
    public static CompletableFuture<Void> syncFullCatalogAsync() {
        if (catalogSynced) {
            return CompletableFuture.completedFuture(null);
        }

        HttpRequest itemsReq = HttpRequest.newBuilder()
                .uri(URI.create(HYPIXEL_ITEMS_URL))
                .header("User-Agent", "DttdrtAddons/1.1.4")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpRequest bzReq = HttpRequest.newBuilder()
                .uri(URI.create(HYPIXEL_BAZAAR_URL))
                .header("User-Agent", "DttdrtAddons/1.1.4")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        CompletableFuture<Set<String>> bzIdsFuture = HTTP_CLIENT.sendAsync(bzReq, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    Set<String> ids = new HashSet<>();
                    if (resp.statusCode() == 200) {
                        try {
                            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                            if (root.has("products") && root.get("products").isJsonObject()) {
                                ids.addAll(root.getAsJsonObject("products").keySet());
                            }
                        } catch (Exception ignored) {}
                    }
                    return ids;
                })
                .exceptionally(e -> Collections.emptySet());

        CompletableFuture<String> itemsFuture = HTTP_CLIENT.sendAsync(itemsReq, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> resp.statusCode() == 200 ? resp.body() : null)
                .exceptionally(e -> null);

        HttpRequest coflReq = HttpRequest.newBuilder()
                .uri(URI.create(COFL_ITEMS_URL))
                .header("User-Agent", "DttdrtAddons/1.1.4")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        CompletableFuture<String> coflItemsFuture = HTTP_CLIENT.sendAsync(coflReq, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> resp.statusCode() == 200 ? resp.body() : null)
                .exceptionally(e -> null);

        return CompletableFuture.allOf(bzIdsFuture, itemsFuture, coflItemsFuture).thenRun(() -> {
            Set<String> bzIds = bzIdsFuture.join();
            String itemsJson = itemsFuture.join();
            String coflJson = coflItemsFuture.join();

            if (itemsJson != null) {
                try {
                    JsonObject root = JsonParser.parseString(itemsJson).getAsJsonObject();
                    if (root.has("items") && root.get("items").isJsonArray()) {
                        JsonArray arr = root.getAsJsonArray("items");
                        for (JsonElement elem : arr) {
                            if (!elem.isJsonObject()) continue;
                            JsonObject obj = elem.getAsJsonObject();
                            String id = obj.has("id") ? obj.get("id").getAsString() : null;
                            String name = obj.has("name") ? obj.get("name").getAsString() : null;
                            if (id == null || name == null || id.isEmpty()) continue;

                            String tier = obj.has("tier") ? obj.get("tier").getAsString() : "COMMON";
                            String material = obj.has("material") ? obj.get("material").getAsString() : "PAPER";
                            String category = obj.has("category") ? obj.get("category").getAsString() : "MISC";
                            String skinVal = null;
                            if (obj.has("skin") && obj.get("skin").isJsonObject()) {
                                JsonObject skinObj = obj.getAsJsonObject("skin");
                                if (skinObj.has("value")) {
                                    skinVal = skinObj.get("value").getAsString();
                                }
                            }
                            boolean isBz = bzIds.contains(id);

                            register(new SkyblockItem(id, name, tier, material, category, isBz, skinVal));
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // Ensure any Bazaar-only products (e.g. ENCHANTMENT_*, ESSENCE_*, SHARD_*) are also searchable
            ensureBazaarIdsRegistered(bzIds);

            // Merge Coflnet-indexed items (runes, pets, skins, etc. missing from Hypixel static items API)
            if (coflJson != null) {
                try {
                    JsonElement parsed = JsonParser.parseString(coflJson);
                    if (parsed.isJsonArray()) {
                        for (JsonElement elem : parsed.getAsJsonArray()) {
                            if (!elem.isJsonObject()) continue;
                            JsonObject obj = elem.getAsJsonObject();
                            String tag = obj.has("tag") && !obj.get("tag").isJsonNull() ? obj.get("tag").getAsString() : null;
                            String name = obj.has("name") && !obj.get("name").isJsonNull() ? obj.get("name").getAsString() : null;
                            if (tag == null || tag.isEmpty() || name == null || name.isEmpty() || "null".equalsIgnoreCase(name)) {
                                continue;
                            }
                            if (tag.contains(":")) continue;
                            if (!ITEMS_BY_ID.containsKey(tag.toUpperCase(Locale.ROOT))) {
                                String flags = obj.has("flags") && !obj.get("flags").isJsonNull() ? obj.get("flags").getAsString() : "";
                                boolean isBz = bzIds.contains(tag) || flags.toUpperCase(Locale.ROOT).contains("BAZAAR");
                                register(new SkyblockItem(tag, name, "COMMON", "PAPER", "MISC", isBz, null));
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (!bzIds.isEmpty() && itemsJson != null) {
                catalogSynced = true;
            }
        });
    }

    /**
     * Registers any Bazaar product IDs discovered from a live Bazaar API response
     * in case the initial startup catalog sync missed them or timed out.
     */
    public static void ensureBazaarIdsRegistered(Collection<String> bzIds) {
        if (bzIds == null || bzIds.isEmpty()) return;
        for (String bzId : bzIds) {
            if (bzId == null || bzId.isEmpty()) continue;
            SkyblockItem existing = ITEMS_BY_ID.get(bzId.toUpperCase());
            if (existing != null) {
                if (!existing.isBazaarEligible()) {
                    register(new SkyblockItem(existing.getId(), existing.getName(), existing.getTier(),
                            existing.getVanillaMaterial(), existing.getCategory(), true, existing.getSkinValue()));
                }
            } else {
                String readableName = formatBazaarIdToName(bzId);
                String mat = bzId.startsWith("ENCHANTMENT_") ? "ENCHANTED_BOOK" : "PAPER";
                String cat = bzId.startsWith("ENCHANTMENT_") ? "ENCHANTMENT" : "BAZAAR";
                register(new SkyblockItem(bzId, readableName, "COMMON", mat, cat, true));
            }
        }
    }

    private static String formatBazaarIdToName(String bzId) {
        if (bzId.startsWith("SHARD_")) {
            return titleCase(bzId.substring("SHARD_".length()).replace("_", " ")) + " Shard";
        }
        if (bzId.startsWith("ESSENCE_")) {
            return titleCase(bzId.substring("ESSENCE_".length()).replace("_", " ")) + " Essence";
        }
        String clean = bzId.replace("ENCHANTMENT_ULTIMATE_", "")
                .replace("ENCHANTMENT_", "")
                .replace("_", " ");
        return titleCase(clean);
    }

    private static String titleCase(String input) {
        String[] words = input.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    public static void register(SkyblockItem item) {
        if (item != null && item.getId() != null) {
            ITEMS_BY_ID.put(item.getId().toUpperCase(), item);
        }
    }

    public static SkyblockItem getItemById(String id) {
        if (id == null) return null;
        return ITEMS_BY_ID.get(id.toUpperCase());
    }

    public static int getCatalogSize() {
        return ITEMS_BY_ID.size();
    }

    /**
     * Fuzzy search returning up to 5 best matching items.
     * Supports acronyms, multi-word tokens, partial matches, and subsequence fallback.
     */
    public static List<SkyblockItem> fuzzySearch(String query, boolean forBazaar) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) {
            return Collections.emptyList();
        }

        String[] tokens = normalizedQuery.split("\\s+");
        List<ScoredItem> scored = new ArrayList<>();

        // Check community abbreviation hit
        String abbrevId = ABBREVIATIONS.get(normalizedQuery);

        for (SkyblockItem item : ITEMS_BY_ID.values()) {
            if (forBazaar && !item.isBazaarEligible()) {
                continue;
            }

            int score = 0;
            if (abbrevId != null && item.getId().equals(abbrevId)) {
                score = 2000;
            } else {
                score = calculateScore(item, normalizedQuery, tokens);
            }

            if (score > 0) {
                scored.add(new ScoredItem(item, score));
            }
        }

        scored.sort((a, b) -> {
            int cmp = Integer.compare(b.score, a.score);
            if (cmp != 0) return cmp;
            return Integer.compare(a.item.getName().length(), b.item.getName().length());
        });

        return scored.stream()
                .limit(5)
                .map(si -> si.item)
                .collect(Collectors.toList());
    }

    private static int calculateScore(SkyblockItem item, String query, String[] tokens) {
        String nameNorm = normalize(item.getName());
        String idNorm = normalize(item.getId().replace("_", " "));

        if (nameNorm.equals(query) || idNorm.equals(query)) {
            return 1000;
        }
        if (nameNorm.startsWith(query)) {
            return 850 - Math.min(200, nameNorm.length() - query.length());
        }
        if (idNorm.startsWith(query)) {
            return 800 - Math.min(200, idNorm.length() - query.length());
        }

        // Multi-word token matching (all tokens must appear in name or id)
        if (tokens.length > 1) {
            boolean allMatch = true;
            for (String token : tokens) {
                if (!nameNorm.contains(token) && !idNorm.contains(token)) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) {
                return 600 - Math.min(150, nameNorm.length());
            }
        }

        // Word-start matching
        for (String word : nameNorm.split("\\s+")) {
            if (word.startsWith(query)) {
                return 550 - Math.min(100, nameNorm.length());
            }
        }

        // Substring match
        int idx = nameNorm.indexOf(query);
        if (idx >= 0) {
            return 400 - Math.min(150, idx + nameNorm.length() / 2);
        }
        int idIdx = idNorm.indexOf(query);
        if (idIdx >= 0) {
            return 300 - Math.min(100, idIdx);
        }

        // Subsequence match (e.g. "hypron" -> "hyperion")
        if (query.length() >= 3 && isSubsequence(query, nameNorm)) {
            return 150 - Math.min(100, nameNorm.length());
        }

        return 0;
    }

    private static boolean isSubsequence(String query, String target) {
        int qi = 0;
        for (int ti = 0; ti < target.length() && qi < query.length(); ti++) {
            if (query.charAt(qi) == target.charAt(ti)) {
                qi++;
            }
        }
        return qi == query.length();
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT)
                .replace("'", "")
                .replace("’", "")
                .replace("-", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private record ScoredItem(SkyblockItem item, int score) {}
}
