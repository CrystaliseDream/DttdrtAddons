package com.dttdrt.addons.api;

import com.dttdrt.addons.features.market.model.PriceHistoryPoint;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Fetches historical price series for AH and Bazaar items from Coflnet.
 */
public class PriceHistoryApi {
    private static final String COFL_BASE = "https://sky.coflnet.com/api";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Fetches AH price history. Range: "day", "week", or "month".
     */
    public static CompletableFuture<List<PriceHistoryPoint>> getAhHistoryAsync(String itemId, String range) {
        if (itemId == null || itemId.isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        String r = (range == null || range.isEmpty()) ? "week" : range.toLowerCase();
        String url = COFL_BASE + "/item/price/" + itemId.toUpperCase() + "/history/" + r;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "DttdrtAddons/1.1.3")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return HTTP_CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    List<PriceHistoryPoint> points = new ArrayList<>();
                    if (resp.statusCode() == 200) {
                        try {
                            JsonElement parsed = JsonParser.parseString(resp.body());
                            if (parsed.isJsonArray()) {
                                JsonArray arr = parsed.getAsJsonArray();
                                for (JsonElement el : arr) {
                                    if (!el.isJsonObject()) continue;
                                    JsonObject obj = el.getAsJsonObject();
                                    double min = obj.has("min") && !obj.get("min").isJsonNull() ? obj.get("min").getAsDouble() : 0;
                                    double avg = obj.has("avg") && !obj.get("avg").isJsonNull() ? obj.get("avg").getAsDouble() : min;
                                    String time = obj.has("time") ? formatTimeLabel(obj.get("time").getAsString()) : "";
                                    if (avg > 0 || min > 0) {
                                        points.add(new PriceHistoryPoint(time, avg > 0 ? avg : min, min > 0 ? min : avg));
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                    return points;
                })
                .exceptionally(ex -> new ArrayList<>());
    }

    /**
     * Fetches Bazaar price history. Range: "hour", "day", or "week".
     */
    public static CompletableFuture<List<PriceHistoryPoint>> getBazaarHistoryAsync(String productId, String range) {
        if (productId == null || productId.isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        String r = (range == null || range.isEmpty()) ? "week" : range.toLowerCase();
        String url = COFL_BASE + "/bazaar/" + productId.toUpperCase() + "/history/" + r;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "DttdrtAddons/1.1.3")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return HTTP_CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    List<PriceHistoryPoint> points = new ArrayList<>();
                    if (resp.statusCode() == 200) {
                        try {
                            JsonElement parsed = JsonParser.parseString(resp.body());
                            if (parsed.isJsonArray()) {
                                JsonArray arr = parsed.getAsJsonArray();
                                // Coflnet BZ history comes newest-first; reverse to oldest->newest for chart
                                for (int i = arr.size() - 1; i >= 0; i--) {
                                    JsonElement el = arr.get(i);
                                    if (!el.isJsonObject()) continue;
                                    JsonObject obj = el.getAsJsonObject();
                                    double buy = obj.has("buy") && !obj.get("buy").isJsonNull() ? obj.get("buy").getAsDouble() : 0;
                                    double sell = obj.has("sell") && !obj.get("sell").isJsonNull() ? obj.get("sell").getAsDouble() : 0;
                                    String time = obj.has("timestamp") ? formatTimeLabel(obj.get("timestamp").getAsString()) : "";
                                    if (buy > 0 || sell > 0) {
                                        points.add(new PriceHistoryPoint(time, buy, sell));
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                    return points;
                })
                .exceptionally(ex -> new ArrayList<>());
    }

    private static String formatTimeLabel(String iso) {
        if (iso == null || iso.length() < 16) return iso != null ? iso : "";
        // "2026-11-14T18:00:00" -> "11-14 18:00"
        return iso.substring(5, 10) + " " + iso.substring(11, 16);
    }
}
