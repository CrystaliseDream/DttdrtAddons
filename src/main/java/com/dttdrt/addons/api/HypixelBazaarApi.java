package com.dttdrt.addons.api;

import com.dttdrt.addons.features.market.model.BazaarItemSummary;
import com.dttdrt.addons.features.market.model.BazaarOrderEntry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Fetches real-time Bazaar market state and order book directly from Hypixel's official API.
 * Hypixel Bazaar API requires no authentication and is updated every 10-60 seconds.
 */
public class HypixelBazaarApi {
    private static final String BAZAAR_URL = "https://api.hypixel.net/v2/skyblock/bazaar";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static volatile JsonObject cachedBazaarData = null;
    private static volatile long lastCacheTime = 0;
    private static final long CACHE_TTL_MS = 10_000L; // 10s cache matching safe refresh cooldown

    /**
     * Asynchronously query market state and order books for a specific product in Bazaar.
     */
    public static CompletableFuture<BazaarItemSummary> getBazaarSummaryAsync(String productId) {
        if (productId == null || productId.trim().isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        String targetId = productId.toUpperCase().trim();

        if (cachedBazaarData != null && (System.currentTimeMillis() - lastCacheTime < CACHE_TTL_MS)) {
            return CompletableFuture.completedFuture(extractSummary(cachedBazaarData, targetId));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BAZAAR_URL))
                .header("User-Agent", "DttdrtAddons/1.1.6")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                            if (root.has("products") && root.get("products").isJsonObject()) {
                                cachedBazaarData = root.getAsJsonObject("products");
                                lastCacheTime = System.currentTimeMillis();
                                ItemDatabase.ensureBazaarIdsRegistered(cachedBazaarData.keySet());
                                return extractSummary(cachedBazaarData, targetId);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    return null;
                });
    }

    private static BazaarItemSummary extractSummary(JsonObject products, String targetId) {
        if (products == null || !products.has(targetId)) {
            return null;
        }

        JsonObject product = products.getAsJsonObject(targetId);
        JsonObject quickStatus = product.has("quick_status") ? product.getAsJsonObject("quick_status") : null;

        if (quickStatus == null) {
            return null;
        }

        double buyPrice = quickStatus.has("buyPrice") ? quickStatus.get("buyPrice").getAsDouble() : 0.0;
        double sellPrice = quickStatus.has("sellPrice") ? quickStatus.get("sellPrice").getAsDouble() : 0.0;
        long buyVolume = quickStatus.has("buyVolume") ? quickStatus.get("buyVolume").getAsLong() : 0L;
        long sellVolume = quickStatus.has("sellVolume") ? quickStatus.get("sellVolume").getAsLong() : 0L;
        int buyOrders = quickStatus.has("buyOrders") ? quickStatus.get("buyOrders").getAsInt() : 0;
        int sellOrders = quickStatus.has("sellOrders") ? quickStatus.get("sellOrders").getAsInt() : 0;

        // In Hypixel API: sell_summary = Buy Orders (top instant-sell targets), buy_summary = Sell Orders (top instant-buy offers)
        List<BazaarOrderEntry> buyOrderBook = parseOrderSummary(product.getAsJsonArray("sell_summary"));
        List<BazaarOrderEntry> sellOrderBook = parseOrderSummary(product.getAsJsonArray("buy_summary"));

        return new BazaarItemSummary(targetId, buyPrice, sellPrice, buyVolume, sellVolume,
                buyOrders, sellOrders, buyOrderBook, sellOrderBook);
    }

    private static List<BazaarOrderEntry> parseOrderSummary(JsonArray arr) {
        List<BazaarOrderEntry> entries = new ArrayList<>();
        if (arr == null) return entries;
        for (JsonElement elem : arr) {
            if (!elem.isJsonObject()) continue;
            JsonObject obj = elem.getAsJsonObject();
            int orders = obj.has("orders") ? obj.get("orders").getAsInt() : 1;
            long amount = obj.has("amount") ? obj.get("amount").getAsLong() : 0L;
            double unitPrice = obj.has("pricePerUnit") ? obj.get("pricePerUnit").getAsDouble() : 0.0;
            if (amount > 0 && unitPrice > 0) {
                entries.add(new BazaarOrderEntry(orders, amount, unitPrice));
            }
        }
        return entries;
    }

    /**
     * Batch-fetches [sellOfferPrice (quick_status.buyPrice), buyOrderPrice (quick_status.sellPrice)]
     * for the given Bazaar product IDs, reusing the 10s cache.
     */
    public static CompletableFuture<Map<String, double[]>> getBatchPricesAsync(Collection<String> productIds) {
        if (cachedBazaarData != null && (System.currentTimeMillis() - lastCacheTime < CACHE_TTL_MS)) {
            return CompletableFuture.completedFuture(extractBatchPrices(cachedBazaarData, productIds));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BAZAAR_URL))
                .header("User-Agent", "DttdrtAddons/1.1.6")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                            if (root.has("products") && root.get("products").isJsonObject()) {
                                cachedBazaarData = root.getAsJsonObject("products");
                                lastCacheTime = System.currentTimeMillis();
                                return extractBatchPrices(cachedBazaarData, productIds);
                            }
                        } catch (Exception ignored) {}
                    }
                    return new HashMap<String, double[]>();
                })
                .exceptionally(t -> new HashMap<>());
    }

    private static Map<String, double[]> extractBatchPrices(JsonObject products, Collection<String> productIds) {
        Map<String, double[]> map = new HashMap<>();
        if (products == null || productIds == null) return map;
        for (String id : productIds) {
            if (!products.has(id)) continue;
            JsonObject product = products.getAsJsonObject(id);
            JsonObject qs = product.has("quick_status") ? product.getAsJsonObject("quick_status") : null;
            if (qs == null) continue;
            double sellOffer = qs.has("buyPrice") ? qs.get("buyPrice").getAsDouble() : 0.0;
            double buyOrder = qs.has("sellPrice") ? qs.get("sellPrice").getAsDouble() : 0.0;
            map.put(id, new double[]{sellOffer, buyOrder});
        }
        return map;
    }
}
