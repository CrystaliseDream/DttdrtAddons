package com.dttdrt.addons.core.update;

import com.dttdrt.addons.DttdrtAddons;
import com.dttdrt.addons.core.lang.ModLang;
import com.dttdrt.addons.core.log.ModLogManager;
import com.dttdrt.addons.core.net.HttpTransportHelper;
import com.dttdrt.addons.core.net.HttpTransportHelper.HttpResult;
import com.dttdrt.addons.util.NotificationUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks CrystaliseDream/DttdrtAddons on GitHub once at client startup and displays
 * a clickable chat notification when a newer version is published.
 */
public final class UpdateChecker {

    public static final String REPO_URL = "https://github.com/CrystaliseDream/DttdrtAddons";
    private static final String RELEASES_PAGE_URL = REPO_URL + "/releases";
    private static final String RELEASES_ATOM_URL = REPO_URL + "/releases.atom";
    private static final String API_LATEST_RELEASE = "https://api.github.com/repos/CrystaliseDream/DttdrtAddons/releases/latest";
    private static final String API_RELEASES = "https://api.github.com/repos/CrystaliseDream/DttdrtAddons/releases?per_page=1";
    private static final String API_TAGS = "https://api.github.com/repos/CrystaliseDream/DttdrtAddons/tags?per_page=1";
    private static final String API_CONTENTS = "https://api.github.com/repos/CrystaliseDream/DttdrtAddons/contents";

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)+)");
    private static final Pattern ATOM_ENTRY_LINK_PATTERN = Pattern.compile("<entry>[\\s\\S]*?<link[^>]*href=\"([^\"]+)\"");

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean CHAT_NOTIFIED = new AtomicBoolean(false);

    private static volatile boolean updateAvailable = false;
    private static volatile String currentVersion = "1.1.5";
    private static volatile String latestVersion = "";
    private static volatile String latestPageUrl = REPO_URL;
    private static int inWorldTicks = 0;

    private UpdateChecker() {
    }

    public static void checkAsync() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        currentVersion = resolveCurrentModVersion();
        CompletableFuture.runAsync(() -> {
            try {
                ReleaseInfo remote = fetchLatestRemoteVersion();
                if (remote != null && remote.version != null && !remote.version.isEmpty()) {
                    if (compareVersions(remote.version, currentVersion) > 0) {
                        latestVersion = remote.version;
                        latestPageUrl = (remote.url != null && !remote.url.isBlank()) ? remote.url : REPO_URL;
                        updateAvailable = true;
                        ModLogManager.info("Update", "New version detected: " + latestVersion + " (current=" + currentVersion + ")");
                    } else {
                        ModLogManager.info("Update", "Up to date (current=" + currentVersion + ", remote=" + remote.version + ")");
                    }
                }
            } catch (Throwable t) {
                ModLogManager.info("Update", "Update check skipped: " + t.getMessage());
            }
        });
    }

    public static void tickNotifyIfReady(Minecraft mc) {
        if (CHAT_NOTIFIED.get()) {
            return;
        }
        if (mc == null || mc.player == null || mc.level == null) {
            inWorldTicks = 0;
            return;
        }
        inWorldTicks++;
        if (updateAvailable && inWorldTicks >= 40 && CHAT_NOTIFIED.compareAndSet(false, true)) {
            String url = (latestPageUrl != null && !latestPageUrl.isBlank()) ? latestPageUrl : REPO_URL;
            URI parsedUri;
            try {
                parsedUri = URI.create(url);
            } catch (Exception ignored) {
                url = REPO_URL;
                parsedUri = URI.create(REPO_URL);
            }
            final URI targetUri = parsedUri;
            final String displayUrl = url;

            MutableComponent msg = Component.literal(
                            ModLang.tr("dttdrtaddons.update.available", latestVersion, currentVersion))
                    .append(Component.literal(" "))
                    .append(Component.literal(ModLang.tr("dttdrtaddons.update.open_github"))
                            .withStyle(style -> style
                                    .withColor(ChatFormatting.AQUA)
                                    .withUnderlined(true)
                                    .withClickEvent(new ClickEvent.OpenUrl(targetUri))
                                    .withHoverEvent(new HoverEvent.ShowText(
                                            Component.literal(ModLang.tr("dttdrtaddons.update.hover", displayUrl))))));
            NotificationUtils.sendClientChatMessage(msg);
        }
    }

    private static String resolveCurrentModVersion() {
        try {
            return FabricLoader.getInstance()
                    .getModContainer(DttdrtAddons.MOD_ID)
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("1.1.5");
        } catch (Throwable ignored) {
            return "1.1.5";
        }
    }

    private record ReleaseInfo(String version, String url) {}

    private static ReleaseInfo fetchLatestRemoteVersion() {
        JsonElement latestEl = fetchJson(API_LATEST_RELEASE);
        if (latestEl != null && latestEl.isJsonObject()) {
            ReleaseInfo info = parseReleaseObject(latestEl.getAsJsonObject());
            if (info != null) {
                return info;
            }
        }

        JsonElement releasesEl = fetchJson(API_RELEASES);
        if (releasesEl != null && releasesEl.isJsonArray() && !releasesEl.getAsJsonArray().isEmpty()) {
            JsonElement first = releasesEl.getAsJsonArray().get(0);
            if (first.isJsonObject()) {
                ReleaseInfo info = parseReleaseObject(first.getAsJsonObject());
                if (info != null) {
                    return info;
                }
            }
        }

        JsonElement tagsEl = fetchJson(API_TAGS);
        if (tagsEl != null && tagsEl.isJsonArray() && !tagsEl.getAsJsonArray().isEmpty()) {
            JsonElement first = tagsEl.getAsJsonArray().get(0);
            if (first.isJsonObject()) {
                String tagName = getString(first.getAsJsonObject(), "name");
                String ver = extractVersion(tagName);
                if (ver != null) {
                    return new ReleaseInfo(ver, RELEASES_PAGE_URL);
                }
            }
        }

        JsonElement contentsEl = fetchJson(API_CONTENTS);
        if (contentsEl != null && contentsEl.isJsonArray()) {
            String bestVer = null;
            for (JsonElement item : contentsEl.getAsJsonArray()) {
                if (!item.isJsonObject()) {
                    continue;
                }
                String fileName = getString(item.getAsJsonObject(), "name");
                if (fileName == null || !fileName.endsWith(".jar")) {
                    continue;
                }
                String candidate = extractVersion(fileName);
                if (candidate != null && (bestVer == null || compareVersions(candidate, bestVer) > 0)) {
                    bestVer = candidate;
                }
            }
            if (bestVer != null) {
                return new ReleaseInfo(bestVer, REPO_URL);
            }
        }

        ReleaseInfo atomInfo = fetchFromReleasesAtom();
        if (atomInfo != null) {
            return atomInfo;
        }

        return null;
    }

    private static ReleaseInfo fetchFromReleasesAtom() {
        try {
            HttpResult resp = HttpTransportHelper.send(
                    RELEASES_ATOM_URL,
                    "GET",
                    null,
                    Map.of(
                            "Accept", "application/atom+xml,application/xml,text/xml",
                            "User-Agent", "DttdrtAddons/" + currentVersion
                    ),
                    10
            );
            if (resp.statusCode() >= 200 && resp.statusCode() < 300 && !resp.body().isBlank()) {
                Matcher m = ATOM_ENTRY_LINK_PATTERN.matcher(resp.body());
                if (m.find()) {
                    String link = m.group(1);
                    if (link.startsWith("/")) {
                        link = "https://github.com" + link;
                    }
                    String ver = extractVersion(link);
                    if (ver != null) {
                        return new ReleaseInfo(ver, link);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static ReleaseInfo parseReleaseObject(JsonObject obj) {
        String htmlUrl = getString(obj, "html_url");
        if (htmlUrl == null || htmlUrl.isBlank()) {
            htmlUrl = RELEASES_PAGE_URL;
        }
        String ver = extractVersion(getString(obj, "tag_name"));
        if (ver == null) {
            ver = extractVersion(getString(obj, "name"));
        }
        if (ver == null && obj.has("assets") && obj.get("assets").isJsonArray()) {
            JsonArray assets = obj.getAsJsonArray("assets");
            for (JsonElement a : assets) {
                if (a.isJsonObject()) {
                    ver = extractVersion(getString(a.getAsJsonObject(), "name"));
                    if (ver != null) {
                        break;
                    }
                }
            }
        }
        return ver != null ? new ReleaseInfo(ver, htmlUrl) : null;
    }

    private static JsonElement fetchJson(String url) {
        try {
            HttpResult resp = HttpTransportHelper.send(
                    url,
                    "GET",
                    null,
                    Map.of(
                            "Accept", "application/vnd.github+json",
                            "User-Agent", "DttdrtAddons/" + currentVersion
                    ),
                    10
            );
            if (resp.statusCode() >= 200 && resp.statusCode() < 300 && !resp.body().isBlank()) {
                return JsonParser.parseString(resp.body());
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String getString(JsonObject obj, String member) {
        if (obj == null || !obj.has(member) || obj.get(member).isJsonNull()) {
            return null;
        }
        try {
            return obj.get(member).getAsString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String extractVersion(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.replaceAll("(?i)-mc\\d+(?:\\.\\d+)*", "");
        Matcher m = VERSION_PATTERN.matcher(cleaned);
        return m.find() ? m.group(1) : null;
    }

    private static int compareVersions(String remoteRaw, String localRaw) {
        int[] r = parseParts(extractVersion(remoteRaw));
        int[] l = parseParts(extractVersion(localRaw));
        int len = Math.max(r.length, l.length);
        for (int i = 0; i < len; i++) {
            int rv = i < r.length ? r[i] : 0;
            int lv = i < l.length ? l[i] : 0;
            if (rv != lv) {
                return Integer.compare(rv, lv);
            }
        }
        return 0;
    }

    private static int[] parseParts(String version) {
        if (version == null || version.isBlank()) {
            return new int[0];
        }
        String[] segs = version.split("\\.");
        int[] out = new int[segs.length];
        for (int i = 0; i < segs.length; i++) {
            try {
                out[i] = Integer.parseInt(segs[i]);
            } catch (NumberFormatException ignored) {
                out[i] = 0;
            }
        }
        return out;
    }
}
