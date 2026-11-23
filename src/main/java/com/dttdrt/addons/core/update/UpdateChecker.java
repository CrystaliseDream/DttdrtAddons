package com.dttdrt.addons.core.update;

import com.dttdrt.addons.DttdrtAddons;
import com.dttdrt.addons.core.config.ConfigPaths;
import com.dttdrt.addons.core.config.ModConfig;
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
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Checks CrystaliseDream/DttdrtAddons on GitHub once at client startup,
 * silently stages and verifies the matching Minecraft version jar when auto-update
 * is enabled, notifies the player in chat, and launches a detached helper process on
 * exit so the old mod jar is replaced after Windows file locks release.
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
    private static final AtomicBoolean UPDATER_LAUNCHED = new AtomicBoolean(false);

    private static volatile boolean updateAvailable = false;
    private static volatile boolean stagedUpdateReady = false;
    private static volatile String currentVersion = "1.1.5";
    private static volatile String latestVersion = "";
    private static volatile String latestPageUrl = REPO_URL;
    private static volatile Path stagedOldJar = null;
    private static volatile Path stagedNewJar = null;
    private static volatile Path stagedTargetJar = null;
    private static int inWorldTicks = 0;

    private record ReleaseAsset(String name, String downloadUrl, long size, String sha256) {}
    private record ReleaseInfo(String version, String url, ReleaseAsset matchingAsset) {}

    private UpdateChecker() {
    }

    public static void checkAsync() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        currentVersion = resolveCurrentModVersion();
        final String mcVersion = resolveCurrentMcVersion();

        CompletableFuture.runAsync(() -> {
            try {
                cleanupUpdateTempFiles();
                ReleaseInfo remote = fetchLatestRemoteVersion(mcVersion);
                if (remote != null && remote.version() != null && !remote.version().isEmpty()) {
                    if (compareVersions(remote.version(), currentVersion) > 0) {
                        latestVersion = remote.version();
                        latestPageUrl = (remote.url() != null && !remote.url().isBlank()) ? remote.url() : REPO_URL;
                        updateAvailable = true;
                        ModLogManager.info("Update", "New version detected: " + latestVersion
                                + " (current=" + currentVersion + ", mc=" + mcVersion + ")");

                        if (ModConfig.getInstance().autoUpdateEnabled && remote.matchingAsset() != null) {
                            tryStageUpdate(remote);
                        }
                    } else {
                        ModLogManager.info("Update", "Up to date (current=" + currentVersion
                                + ", remote=" + remote.version() + ", mc=" + mcVersion + ")");
                    }
                }
            } catch (Throwable t) {
                ModLogManager.info("Update", "Update check skipped: " + t.getMessage());
            }
        });
    }

    public static boolean isStagedUpdateReady() {
        return stagedUpdateReady;
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

            String headlineKey = stagedUpdateReady ? "dttdrtaddons.update.ready_on_exit" : "dttdrtaddons.update.available";
            MutableComponent msg = Component.literal(ModLang.tr(headlineKey, latestVersion, currentVersion))
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

    /**
     * Spawns the detached SelfUpdaterMain process if a verified update jar is staged.
     * Safe to invoke during client shutdown; executes at most once.
     */
    public static void launchStagedUpdateIfReady() {
        if (!stagedUpdateReady || !ModConfig.getInstance().autoUpdateEnabled) {
            return;
        }
        Path oldJar = stagedOldJar;
        Path newJar = stagedNewJar;
        Path targetJar = stagedTargetJar;
        if (oldJar == null || newJar == null || targetJar == null || !Files.isRegularFile(newJar)) {
            return;
        }
        if (!UPDATER_LAUNCHED.compareAndSet(false, true)) {
            return;
        }
        try {
            String javaExec = resolveJavaExecutable();
            Path logFile = ConfigPaths.getConfigPath().resolve("update").resolve("last_update.log");
            long pid = ProcessHandle.current().pid();
            ProcessBuilder pb = new ProcessBuilder(
                    javaExec,
                    "-cp", newJar.toAbsolutePath().toString(),
                    SelfUpdaterMain.class.getName(),
                    Long.toString(pid),
                    oldJar.toAbsolutePath().toString(),
                    newJar.toAbsolutePath().toString(),
                    targetJar.toAbsolutePath().toString(),
                    logFile.toAbsolutePath().toString()
            );
            pb.redirectInput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            pb.start();
            ModLogManager.info("Update", "Spawned background updater for v" + latestVersion);
        } catch (Throwable t) {
            ModLogManager.info("Update", "Failed to spawn background updater: " + t.getMessage());
        }
    }

    private static void tryStageUpdate(ReleaseInfo remote) {
        ReleaseAsset asset = remote.matchingAsset();
        if (asset == null || asset.downloadUrl() == null || asset.downloadUrl().isBlank()) {
            return;
        }
        Path oldJar = resolveCurrentModJar();
        if (oldJar == null) {
            ModLogManager.info("Update", "Skipping auto-update: current mod jar path is not a replaceable jar in mods/");
            return;
        }
        Path targetInMods = oldJar.resolveSibling(asset.name());
        if (Files.isRegularFile(targetInMods)) {
            ModLogManager.info("Update", "Skipping auto-update: " + asset.name() + " already present in mods/");
            return;
        }

        Path updateDir = ConfigPaths.getConfigPath().resolve("update");
        Path stagedJar = updateDir.resolve(asset.name());
        Path partFile = updateDir.resolve(asset.name() + ".part");

        try {
            Files.createDirectories(updateDir);
            if (Files.isRegularFile(stagedJar) && verifyDownloadedJar(stagedJar, remote.version(), asset)) {
                markStaged(oldJar, stagedJar, targetInMods);
                ModLogManager.info("Update", "Reused verified staged jar: " + stagedJar.getFileName());
                return;
            }

            Files.deleteIfExists(partFile);
            HttpTransportHelper.downloadFile(
                    asset.downloadUrl(),
                    partFile,
                    Map.of(
                            "Accept", "application/octet-stream",
                            "User-Agent", "DttdrtAddons/" + currentVersion
                    ),
                    60
            );

            if (!verifyDownloadedJar(partFile, remote.version(), asset)) {
                Files.deleteIfExists(partFile);
                ModLogManager.info("Update", "Auto-update verification failed for " + asset.name());
                return;
            }

            Files.move(partFile, stagedJar, StandardCopyOption.REPLACE_EXISTING);
            markStaged(oldJar, stagedJar, targetInMods);
            ModLogManager.info("Update", "Staged verified update: " + stagedJar.getFileName() + " (will apply on exit)");
        } catch (Throwable t) {
            try {
                Files.deleteIfExists(partFile);
            } catch (Throwable ignored) {
            }
            ModLogManager.info("Update", "Auto-update staging failed: " + t.getMessage());
        }
    }

    private static void markStaged(Path oldJar, Path stagedJar, Path targetJar) {
        stagedOldJar = oldJar;
        stagedNewJar = stagedJar;
        stagedTargetJar = targetJar;
        stagedUpdateReady = true;
    }

    private static boolean verifyDownloadedJar(Path file, String expectedVersion, ReleaseAsset asset) {
        try {
            if (!Files.isRegularFile(file)) {
                return false;
            }
            long actualSize = Files.size(file);
            if (actualSize < 1024L) {
                return false;
            }
            if (asset.size() > 0L && actualSize != asset.size()) {
                ModLogManager.info("Update", "Size mismatch: expected=" + asset.size() + ", actual=" + actualSize);
                return false;
            }
            if (asset.sha256() != null && !asset.sha256().isBlank()) {
                String actualHash = computeSha256(file);
                if (!asset.sha256().equalsIgnoreCase(actualHash)) {
                    ModLogManager.info("Update", "SHA-256 mismatch for " + asset.name());
                    return false;
                }
            }
            try (ZipFile zip = new ZipFile(file.toFile(), StandardCharsets.UTF_8)) {
                ZipEntry modJsonEntry = zip.getEntry("fabric.mod.json");
                if (modJsonEntry == null) {
                    return false;
                }
                try (InputStream is = zip.getInputStream(modJsonEntry);
                     InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    String id = getString(json, "id");
                    String ver = getString(json, "version");
                    if (!DttdrtAddons.MOD_ID.equals(id)) {
                        return false;
                    }
                    if (ver != null && expectedVersion != null && compareVersions(ver, expectedVersion) != 0) {
                        return false;
                    }
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String computeSha256(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[16384];
        try (InputStream is = Files.newInputStream(file)) {
            int n;
            while ((n = is.read(buf)) != -1) {
                md.update(buf, 0, n);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format(Locale.ROOT, "%02x", b & 0xFF));
        }
        return sb.toString();
    }

    private static Path resolveCurrentModJar() {
        try {
            Optional<ModContainer> containerOpt = FabricLoader.getInstance().getModContainer(DttdrtAddons.MOD_ID);
            if (containerOpt.isEmpty()) {
                return null;
            }
            ModOrigin origin = containerOpt.get().getOrigin();
            if (origin == null || origin.getKind() != ModOrigin.Kind.PATH) {
                return null;
            }
            Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods").toAbsolutePath().normalize();
            for (Path p : origin.getPaths()) {
                if (p == null) {
                    continue;
                }
                Path norm = p.toAbsolutePath().normalize();
                if (Files.isRegularFile(norm)
                        && norm.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")
                        && norm.getParent() != null
                        && norm.getParent().startsWith(modsDir)) {
                    return norm;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String resolveJavaExecutable() {
        try {
            Optional<String> cmd = ProcessHandle.current().info().command();
            if (cmd.isPresent()) {
                String pathStr = cmd.get();
                if (pathStr.toLowerCase(Locale.ROOT).endsWith("\\java.exe")) {
                    Path javaw = Path.of(pathStr.substring(0, pathStr.length() - 8) + "javaw.exe");
                    if (Files.isRegularFile(javaw)) {
                        return javaw.toString();
                    }
                }
                return pathStr;
            }
        } catch (Throwable ignored) {
        }
        String javaHome = System.getProperty("java.home", "");
        boolean win = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path candidate = Path.of(javaHome, "bin", win ? "javaw.exe" : "java");
        if (Files.isRegularFile(candidate)) {
            return candidate.toString();
        }
        return win ? "javaw" : "java";
    }

    private static void cleanupUpdateTempFiles() {
        try {
            Path updateDir = ConfigPaths.getConfigPath().resolve("update");
            if (!Files.isDirectory(updateDir)) {
                return;
            }
            try (Stream<Path> list = Files.list(updateDir)) {
                list.forEach(p -> {
                    try {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (Files.isRegularFile(p) && (name.endsWith(".part") || name.endsWith(".tmp"))) {
                            Files.deleteIfExists(p);
                        }
                    } catch (Throwable ignored) {
                    }
                });
            }
            Path backupDir = updateDir.resolve("backup");
            if (Files.isDirectory(backupDir)) {
                long cutoff = System.currentTimeMillis() - 7L * 24L * 3600L * 1000L;
                try (Stream<Path> list = Files.list(backupDir)) {
                    list.forEach(p -> {
                        try {
                            if (Files.isRegularFile(p) && Files.getLastModifiedTime(p).toMillis() < cutoff) {
                                Files.deleteIfExists(p);
                            }
                        } catch (Throwable ignored) {
                        }
                    });
                }
            }
        } catch (Throwable ignored) {
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

    private static String resolveCurrentMcVersion() {
        try {
            return FabricLoader.getInstance()
                    .getModContainer("minecraft")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("26.1.2");
        } catch (Throwable ignored) {
            return "26.1.2";
        }
    }

    private static ReleaseInfo fetchLatestRemoteVersion(String mcVersion) {
        JsonElement latestEl = fetchJson(API_LATEST_RELEASE);
        if (latestEl != null && latestEl.isJsonObject()) {
            ReleaseInfo info = parseReleaseObject(latestEl.getAsJsonObject(), mcVersion);
            if (info != null) {
                return info;
            }
        }

        JsonElement releasesEl = fetchJson(API_RELEASES);
        if (releasesEl != null && releasesEl.isJsonArray() && !releasesEl.getAsJsonArray().isEmpty()) {
            JsonElement first = releasesEl.getAsJsonArray().get(0);
            if (first.isJsonObject()) {
                ReleaseInfo info = parseReleaseObject(first.getAsJsonObject(), mcVersion);
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
                    return new ReleaseInfo(ver, RELEASES_PAGE_URL, null);
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
                return new ReleaseInfo(bestVer, REPO_URL, null);
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
                        return new ReleaseInfo(ver, link, null);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static ReleaseInfo parseReleaseObject(JsonObject obj, String mcVersion) {
        String htmlUrl = getString(obj, "html_url");
        if (htmlUrl == null || htmlUrl.isBlank()) {
            htmlUrl = RELEASES_PAGE_URL;
        }
        String ver = extractVersion(getString(obj, "tag_name"));
        if (ver == null) {
            ver = extractVersion(getString(obj, "name"));
        }

        ReleaseAsset matchingAsset = null;
        String suffix = "-mc" + mcVersion.toLowerCase(Locale.ROOT) + ".jar";
        if (obj.has("assets") && obj.get("assets").isJsonArray()) {
            JsonArray assets = obj.getAsJsonArray("assets");
            for (JsonElement a : assets) {
                if (!a.isJsonObject()) {
                    continue;
                }
                JsonObject assetObj = a.getAsJsonObject();
                String assetName = getString(assetObj, "name");
                if (assetName == null) {
                    continue;
                }
                if (ver == null) {
                    ver = extractVersion(assetName);
                }
                String lower = assetName.toLowerCase(Locale.ROOT);
                if (lower.startsWith("dttdrtaddons-") && lower.endsWith(suffix)) {
                    String dlUrl = getString(assetObj, "browser_download_url");
                    long size = getLong(assetObj, "size", -1L);
                    String sha256 = extractSha256(getString(assetObj, "digest"));
                    if (dlUrl != null && !dlUrl.isBlank()) {
                        matchingAsset = new ReleaseAsset(assetName, dlUrl, size, sha256);
                    }
                }
            }
        }
        return ver != null ? new ReleaseInfo(ver, htmlUrl, matchingAsset) : null;
    }

    private static String extractSha256(String digest) {
        if (digest == null) {
            return null;
        }
        String trimmed = digest.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("sha256:")) {
            return trimmed.substring(7).trim();
        }
        return null;
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

    private static long getLong(JsonObject obj, String member, long fallback) {
        if (obj == null || !obj.has(member) || obj.get(member).isJsonNull()) {
            return fallback;
        }
        try {
            return obj.get(member).getAsLong();
        } catch (Throwable ignored) {
            return fallback;
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
