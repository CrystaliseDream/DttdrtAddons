package com.dttdrt.addons.api;

import com.dttdrt.addons.core.net.HttpTransportHelper;
import com.dttdrt.addons.core.net.HttpTransportHelper.HttpResult;
import com.dttdrt.addons.features.market.model.AhItemListing;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Communicates with the Coflnet AH Indexing API to retrieve lowest BIN auctions.
 * Filters strictly for Buy-It-Now auctions, ignoring all bid-only auctions.
 */
public class CoflnetAhApi {
    private static final String COFL_BASE = "https://sky.coflnet.com/api";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static CompletableFuture<List<AhItemListing>> getLowestBinsAsync(String itemId) {
        return getLowestBinsAsync(itemId, 7);
    }

    /**
     * Retrieve up to `limit` lowest BIN auctions for the given Skyblock item ID.
     */
    public static CompletableFuture<List<AhItemListing>> getLowestBinsAsync(String itemId, int limit) {
        if (itemId == null || itemId.trim().isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        final int maxCount = Math.max(7, limit);

        String targetId = itemId.toUpperCase().trim();
        int pagesNeeded = Math.min(10, Math.max(1, (maxCount + 9) / 10));

        List<CompletableFuture<List<AhItemListing>>> pageFutures = new ArrayList<>();
        for (int p = 0; p < pagesNeeded; p++) {
            pageFutures.add(fetchPageAsync(targetId, p));
        }

        return CompletableFuture.allOf(pageFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    Map<String, AhItemListing> deduped = new LinkedHashMap<>();
                    for (CompletableFuture<List<AhItemListing>> f : pageFutures) {
                        for (AhItemListing item : f.join()) {
                            String key = (item.getAuctionUuid() != null && !item.getAuctionUuid().isEmpty())
                                    ? item.getAuctionUuid()
                                    : (item.getSellerUuid() + ":" + item.getPrice());
                            deduped.putIfAbsent(key, item);
                        }
                    }

                    List<AhItemListing> results = new ArrayList<>(deduped.values());
                    results.sort(Comparator.comparingLong(AhItemListing::getPrice));
                    List<AhItemListing> topN = results.size() > maxCount ? new ArrayList<>(results.subList(0, maxCount)) : results;

                    for (AhItemListing listing : topN) {
                        if (!listing.getSellerUuid().isEmpty() && PlayerNameResolver.getCachedName(listing.getSellerUuid()) == null) {
                            PlayerNameResolver.resolveAsync(listing.getSellerUuid(), listing::setSellerName);
                        }
                    }
                    return topN;
                });
    }

    private static CompletableFuture<List<AhItemListing>> fetchPageAsync(String targetId, int page) {
        String url = COFL_BASE + "/auctions/tag/" + targetId + "/active/bin?page=" + page;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "DttdrtAddons/1.1.3")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    List<AhItemListing> results = new ArrayList<>();
                    if (response.statusCode() == 200) {
                        try {
                            JsonElement parsed = JsonParser.parseString(response.body());
                            if (parsed.isJsonArray()) {
                                JsonArray array = parsed.getAsJsonArray();
                                for (JsonElement elem : array) {
                                    if (!elem.isJsonObject()) continue;
                                    JsonObject obj = elem.getAsJsonObject();

                                    boolean isBin = !obj.has("bin") || obj.get("bin").getAsBoolean();
                                    if (!isBin) continue;

                                    String uuid = obj.has("uuid") ? obj.get("uuid").getAsString() :
                                            (obj.has("id") ? obj.get("id").getAsString() : "");
                                    long price = obj.has("startingBid") ? obj.get("startingBid").getAsLong() :
                                            (obj.has("price") ? obj.get("price").getAsLong() : 0L);

                                    if (price <= 0) continue;

                                    String itemName = obj.has("itemName") ? obj.get("itemName").getAsString() : targetId;
                                    String sellerUuid = obj.has("auctioneerId") && !obj.get("auctioneerId").isJsonNull()
                                            ? obj.get("auctioneerId").getAsString()
                                            : (obj.has("seller") && !obj.get("seller").isJsonNull() ? obj.get("seller").getAsString() : "");

                                    String cachedName = PlayerNameResolver.getCachedName(sellerUuid);
                                    String sellerName = cachedName != null ? cachedName
                                            : (obj.has("sellerName") && !obj.get("sellerName").isJsonNull() ? obj.get("sellerName").getAsString() : "Loading...");

                                    String tier = obj.has("tier") && !obj.get("tier").isJsonNull() ? obj.get("tier").getAsString() : "COMMON";
                                    int count = obj.has("count") ? obj.get("count").getAsInt() : 1;

                                    long startTimestamp = obj.has("start") ? parseTimestamp(obj.get("start").getAsString()) : System.currentTimeMillis();
                                    long endTimestamp = obj.has("end") ? parseTimestamp(obj.get("end").getAsString()) : (startTimestamp + 86400000L);

                                    results.add(new AhItemListing(uuid, targetId, itemName, price, sellerUuid, sellerName,
                                            endTimestamp, startTimestamp, tier, count));
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    return results;
                })
                .exceptionally(e -> new ArrayList<>());
    }

    private static long parseTimestamp(String ts) {
        if (ts == null || ts.isEmpty()) return System.currentTimeMillis();
        try {
            if (ts.endsWith("Z")) {
                return Instant.parse(ts).toEpochMilli();
            }
            return LocalDateTime.parse(ts).toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    /**
     * Fetches Lowest BIN prices per rarity tier for tiered pets (e.g. Scatha),
     * querying Coflnet active BIN pages with Moulberry lowestbin fallback for missing tiers.
     */
    public static CompletableFuture<Map<String, Double>> getScathaPetLowestBinsAsync() {
        return getTieredPetLowestBinsAsync("PET_SCATHA", Map.of(
                "RARE", "SCATHA;2",
                "EPIC", "SCATHA;3",
                "LEGENDARY", "SCATHA;4"
        ));
    }

    /**
     * Fetches Lowest BIN prices per rarity tier for any Coflnet pet tag (e.g. PET_SCATHA, PET_BAL),
     * with Moulberry lowestbin.json fallback for missing tiers.
     */
    public static CompletableFuture<Map<String, Double>> getTieredPetLowestBinsAsync(
            String coflPetTag, Map<String, String> tierToKey) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Double> result = new ConcurrentHashMap<>();
            fetchTieredPetBinsFromCoflnet(coflPetTag, tierToKey, result);
            List<String> missing = new ArrayList<>();
            for (String key : tierToKey.values()) {
                if (!result.containsKey(key)) missing.add(key);
            }
            if (!missing.isEmpty()) {
                fillFromMoulberryLowestBins(missing, result);
            }
            return result;
        });
    }

    /**
     * Batch-fetches Lowest BIN prices for AH items (Crystal Nucleus drops like DIVAN_ALLOY,
     * PET_ITEM_QUICK_CLAW, PICKONIMBUS, PREHISTORIC_EGG, RECALL_POTION, plus BAL;3 / BAL;4).
     * Queries Moulberry lowestbin.json once, falling back to Coflnet active BIN for missing items.
     */
    public static CompletableFuture<Map<String, Double>> getNucleusAhLowestBinsAsync(List<String> flatAhKeys) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Double> result = new ConcurrentHashMap<>();
            if (flatAhKeys != null && !flatAhKeys.isEmpty()) {
                fillFromMoulberryLowestBins(flatAhKeys, result);
                for (String key : flatAhKeys) {
                    if (!result.containsKey(key) && !key.contains(";")) {
                        double coflMin = fetchFirstPageMinBinFromCoflnet(key);
                        if (coflMin > 0.0) {
                            result.put(key, coflMin);
                        }
                    }
                }
            }
            Map<String, String> balTiers = Map.of("EPIC", "BAL;3", "LEGENDARY", "BAL;4");
            if (!result.containsKey("BAL;3") || !result.containsKey("BAL;4")) {
                fetchTieredPetBinsFromCoflnet("PET_BAL", balTiers, result);
            }
            return result;
        });
    }

    private static void fetchTieredPetBinsFromCoflnet(
            String coflPetTag, Map<String, String> tierToKey, Map<String, Double> out) {
        for (int page = 0; page < 10; page++) {
            try {
                String url = COFL_BASE + "/auctions/tag/" + coflPetTag + "/active/bin?page=" + page;
                HttpResult resp = HttpTransportHelper.send(
                        url,
                        "GET",
                        null,
                        Map.of("User-Agent", "DttdrtAddons/1.1.3", "Accept", "application/json"),
                        10
                );
                if (resp.statusCode() != 200 || resp.body().isEmpty()) {
                    break;
                }
                JsonElement parsed = JsonParser.parseString(resp.body());
                if (!parsed.isJsonArray() || parsed.getAsJsonArray().isEmpty()) {
                    break;
                }
                for (JsonElement elem : parsed.getAsJsonArray()) {
                    if (!elem.isJsonObject()) continue;
                    JsonObject obj = elem.getAsJsonObject();
                    if (obj.has("bin") && !obj.get("bin").getAsBoolean()) continue;
                    long price = obj.has("startingBid") ? obj.get("startingBid").getAsLong()
                            : (obj.has("price") ? obj.get("price").getAsLong() : 0L);
                    if (price <= 0L) continue;
                    String tier = obj.has("tier") && !obj.get("tier").isJsonNull()
                            ? obj.get("tier").getAsString().toUpperCase(Locale.ROOT)
                            : "";
                    String key = tierToKey.get(tier);
                    if (key != null) {
                        out.merge(key, (double) price, Math::min);
                    }
                }
                boolean allFound = true;
                for (String k : tierToKey.values()) {
                    if (!out.containsKey(k)) {
                        allFound = false;
                        break;
                    }
                }
                if (allFound) {
                    break;
                }
            } catch (Throwable ignored) {
                break;
            }
        }
    }

    private static double fetchFirstPageMinBinFromCoflnet(String tag) {
        try {
            String url = COFL_BASE + "/auctions/tag/" + tag + "/active/bin?page=0";
            HttpResult resp = HttpTransportHelper.send(
                    url,
                    "GET",
                    null,
                    Map.of("User-Agent", "DttdrtAddons/1.1.3", "Accept", "application/json"),
                    10
            );
            if (resp.statusCode() != 200 || resp.body().isEmpty()) {
                return 0.0;
            }
            JsonElement parsed = JsonParser.parseString(resp.body());
            if (!parsed.isJsonArray()) return 0.0;
            double best = 0.0;
            for (JsonElement elem : parsed.getAsJsonArray()) {
                if (!elem.isJsonObject()) continue;
                JsonObject obj = elem.getAsJsonObject();
                if (obj.has("bin") && !obj.get("bin").getAsBoolean()) continue;
                long price = obj.has("startingBid") ? obj.get("startingBid").getAsLong()
                        : (obj.has("price") ? obj.get("price").getAsLong() : 0L);
                if (price > 0L && (best <= 0.0 || price < best)) {
                    best = price;
                }
            }
            return best;
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    private static void fillFromMoulberryLowestBins(List<String> keys, Map<String, Double> out) {
        try {
            HttpResult mb = HttpTransportHelper.send(
                    "https://moulberry.codes/lowestbin.json",
                    "GET",
                    null,
                    Map.of("User-Agent", "DttdrtAddons/1.1.3", "Accept", "application/json"),
                    10
            );
            if (mb.statusCode() == 200 && !mb.body().isEmpty()) {
                JsonObject root = JsonParser.parseString(mb.body()).getAsJsonObject();
                for (String key : keys) {
                    if (!out.containsKey(key) && root.has(key) && root.get(key).isJsonPrimitive()) {
                        double val = root.get(key).getAsDouble();
                        if (val > 0.0) {
                            out.put(key, val);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
