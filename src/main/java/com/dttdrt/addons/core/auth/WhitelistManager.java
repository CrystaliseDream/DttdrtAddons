package com.dttdrt.addons.core.auth;

import com.dttdrt.addons.core.net.HttpTransportHelper;
import com.dttdrt.addons.core.net.HttpTransportHelper.HttpResult;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Verifies the local Minecraft account UUID against the private VPS whitelist API
 * using challenge-response Ed25519 signature verification (direct + Windows system proxy fallback).
 */
public class WhitelistManager {

    private static final String API_BASE_URL = "https://wl.354215469.xyz";
    private static final String VERIFY_URL = API_BASE_URL + "/api/v1/verify";
    private static final String REQUEST_URL = API_BASE_URL + "/api/v1/request";
    private static final String PUBLIC_KEY_B64 = "MCowBQYDK2VwAyEAar4DzGgE0jP6hq7XOgYzUsn8LR2VbbvST8zeqdMYjtk=";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final List<Consumer<String>> pendingCallbacks = new CopyOnWriteArrayList<>();

    private static volatile boolean checking = false;
    private static volatile boolean requesting = false;
    private static volatile boolean authorized = false;
    private static volatile String statusMessage = "Checking whitelist...";

    public static boolean isAuthorized() {
        return authorized;
    }

    public static boolean isChecking() {
        return checking;
    }

    public static String getStatusMessage() {
        return statusMessage;
    }

    public static String getLocalUuid() {
        try {
            UUID id = Minecraft.getInstance().getUser().getProfileId();
            if (id != null) {
                return id.toString().replace("-", "").toLowerCase(Locale.ROOT);
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    public static String getLocalUsername() {
        try {
            String name = Minecraft.getInstance().getUser().getName();
            if (name != null) {
                return name.trim();
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    public static void checkAsync() {
        checkAsync(null);
    }

    public static void checkAsync(Consumer<String> onComplete) {
        if (onComplete != null) {
            pendingCallbacks.add(onComplete);
        }
        if (checking) {
            return;
        }
        checking = true;
        statusMessage = "Checking whitelist...";

        CompletableFuture.runAsync(() -> {
            try {
                String localUuid = getLocalUuid();
                if (localUuid.isEmpty()) {
                    authorized = false;
                    statusMessage = "Could not read local account UUID.";
                    return;
                }

                String localName = getLocalUsername();
                String nonce = generateNonce();
                JsonObject reqJson = new JsonObject();
                reqJson.addProperty("uuid", localUuid);
                reqJson.addProperty("username", localName);
                reqJson.addProperty("nonce", nonce);

                HttpResult resp = HttpTransportHelper.send(
                        VERIFY_URL,
                        "POST",
                        reqJson.toString(),
                        Map.of(
                                "Content-Type", "application/json",
                                "User-Agent", "DttdrtAddons/1.1.5",
                                "Cache-Control", "no-cache"
                        ),
                        12
                );

                if (resp.statusCode() != 200 || resp.body().isEmpty()) {
                    authorized = false;
                    statusMessage = "Whitelist server returned HTTP " + resp.statusCode() + ".";
                    return;
                }

                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                String respUuid = root.get("uuid").getAsString().replace("-", "").toLowerCase(Locale.ROOT);
                String status = root.get("status").getAsString();
                boolean respAuthorized = root.get("authorized").getAsBoolean();
                String respNonce = root.get("nonce").getAsString();
                long issuedAt = root.get("issuedAt").getAsLong();
                long expiresAt = root.get("expiresAt").getAsLong();
                String sigB64 = root.get("sig").getAsString();

                if (!localUuid.equals(respUuid) || !nonce.equals(respNonce) || expiresAt <= issuedAt) {
                    authorized = false;
                    statusMessage = "Whitelist response challenge mismatch.";
                    return;
                }

                String payload = respUuid + "|" + status + "|" + respNonce + "|" + issuedAt + "|" + expiresAt;
                if (!verifyEd25519(payload, sigB64)) {
                    authorized = false;
                    statusMessage = "Whitelist signature verification failed.";
                    return;
                }

                if (respAuthorized && "APPROVED".equals(status)) {
                    authorized = true;
                    statusMessage = "Authorized";
                } else if ("PENDING".equals(status)) {
                    authorized = false;
                    statusMessage = "Waiting for whitelist approval. Please wait for the admin to confirm.";
                } else {
                    authorized = false;
                    statusMessage = "Not in whitelist (" + localUuid + "). Run /daapply in game to request access.";
                }
            } catch (Throwable t) {
                authorized = false;
                statusMessage = "Unable to reach whitelist server: " + t.getClass().getSimpleName();
            } finally {
                checking = false;
                String result = statusMessage;
                if (!pendingCallbacks.isEmpty()) {
                    List<Consumer<String>> callbacks = new ArrayList<>(pendingCallbacks);
                    pendingCallbacks.clear();
                    for (Consumer<String> cb : callbacks) {
                        try {
                            cb.accept(result);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        });
    }

    public static void requestAsync(String note, Consumer<String> onComplete) {
        if (requesting) {
            if (onComplete != null) {
                onComplete.accept("Whitelist application is already being sent...");
            }
            return;
        }
        requesting = true;
        CompletableFuture.runAsync(() -> {
            String reply;
            boolean shouldRefreshVerify = false;
            try {
                String localUuid = getLocalUuid();
                if (localUuid.isEmpty()) {
                    reply = "Could not read local account UUID.";
                    finishRequest(reply, onComplete);
                    return;
                }

                JsonObject reqJson = new JsonObject();
                reqJson.addProperty("uuid", localUuid);
                reqJson.addProperty("username", getLocalUsername());
                if (note != null && !note.isBlank()) {
                    reqJson.addProperty("note", note.trim());
                }

                HttpResult resp = HttpTransportHelper.send(
                        REQUEST_URL,
                        "POST",
                        reqJson.toString(),
                        Map.of(
                                "Content-Type", "application/json",
                                "User-Agent", "DttdrtAddons/1.1.5"
                        ),
                        12
                );

                if (resp.statusCode() == 429) {
                    reply = "Too many applications submitted recently. Please try again later.";
                    finishRequest(reply, onComplete);
                    return;
                }
                if (resp.statusCode() != 200 || resp.body().isEmpty()) {
                    String detail = "";
                    try {
                        JsonObject errObj = JsonParser.parseString(resp.body()).getAsJsonObject();
                        if (errObj.has("error")) {
                            detail = " (" + errObj.get("error").getAsString() + ")";
                        }
                    } catch (Throwable ignored) {
                    }
                    reply = "Whitelist application failed: HTTP " + resp.statusCode() + detail;
                    finishRequest(reply, onComplete);
                    return;
                }

                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                String status = root.has("status") ? root.get("status").getAsString() : "PENDING";
                if ("APPROVED".equals(status)) {
                    shouldRefreshVerify = true;
                    reply = "You are already whitelisted! Refreshing authorization...";
                } else {
                    statusMessage = "Waiting for whitelist approval. Please wait for the admin to confirm.";
                    reply = "Whitelist application sent! Waiting for admin approval (run /daretry once approved).";
                }
            } catch (Throwable t) {
                reply = "Failed to contact whitelist server: " + t.getClass().getSimpleName();
            } finally {
                requesting = false;
            }

            if (shouldRefreshVerify) {
                checkAsync(onComplete);
            } else {
                finishRequest(reply, onComplete);
            }
        });
    }

    private static void finishRequest(String message, Consumer<String> onComplete) {
        if (onComplete != null) {
            try {
                onComplete.accept(message);
            } catch (Throwable ignored) {
            }
        }
    }

    private static String generateNonce() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean verifyEd25519(String payload, String sigB64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(PUBLIC_KEY_B64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            PublicKey pub = kf.generatePublic(spec);

            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(pub);
            verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(sigB64));
        } catch (Throwable t) {
            return false;
        }
    }
}
