package com.dttdrt.addons.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Resolves Minecraft player UUIDs (e.g. Coflnet auctioneerId) into usernames
 * via Mojang's profile API, with thread-safe caching.
 */
public class PlayerNameResolver {
    private static final String MOJANG_PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private static final Map<String, String> NAME_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<String>> IN_FLIGHT = new ConcurrentHashMap<>();

    public static String getCachedName(String uuid) {
        if (uuid == null || uuid.isEmpty()) return null;
        return NAME_CACHE.get(uuid.replace("-", "").toLowerCase());
    }

    public static void resolveAsync(String uuid, Consumer<String> callback) {
        if (uuid == null || uuid.isEmpty()) return;
        String cleanUuid = uuid.replace("-", "").toLowerCase();

        String cached = NAME_CACHE.get(cleanUuid);
        if (cached != null) {
            callback.accept(cached);
            return;
        }

        CompletableFuture<String> future = IN_FLIGHT.computeIfAbsent(cleanUuid, id -> {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(MOJANG_PROFILE_URL + id))
                    .header("User-Agent", "DttdrtAddons/1.1.6")
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            return HTTP_CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .thenApply(resp -> {
                        if (resp.statusCode() == 200) {
                            try {
                                JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
                                if (obj.has("name")) {
                                    String name = obj.get("name").getAsString();
                                    NAME_CACHE.put(id, name);
                                    return name;
                                }
                            } catch (Exception ignored) {}
                        }
                        return null;
                    })
                    .exceptionally(ex -> null)
                    .whenComplete((res, ex) -> IN_FLIGHT.remove(id));
        });

        future.thenAccept(name -> {
            if (name != null) {
                callback.accept(name);
            }
        });
    }
}
