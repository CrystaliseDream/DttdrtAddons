package com.dttdrt.addons.features.mining;

import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.util.NotificationUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure client-side read-only tracker for Crystal Hollows zone detection,
 * server ID tracking (with in-memory per-server coordinate persistence),
 * special structure bounding-box discovery, and NPC/Boss entity tracking.
 * Sends ZERO packets to the server.
 */
public class CrystalHollowsTracker {
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("§.");
    private static final Pattern SERVER_ID_PATTERN = Pattern.compile("\\b((?:mini|mega|m)\\d+[A-Za-z0-9]*)\\b");
    private static final Pattern SENDING_SERVER_PATTERN = Pattern.compile("Sending to server ([A-Za-z0-9_]+)");
    private static final Pattern SBE_DSM_PATTERN = Pattern.compile(
            "\\$(?:SBECHWP\\b|DSMCHWP):(?<loc>[^@]+)@-(?<x>-?\\d+),(?<y>-?\\d+),(?<z>-?\\d+)");
    private static final Pattern LABELED_XYZ_PATTERN = Pattern.compile(
            "(?i)\\bx:\\s*(?<x>-?\\d{1,4})\\s*[,/]?\\s*y:\\s*(?<y>-?\\d{1,3})\\s*[,/]?\\s*z:\\s*(?<z>-?\\d{1,4})\\b");
    private static final Pattern CHAT_XYZ_PATTERN = Pattern.compile(
            "^.*?(?<user>[A-Za-z0-9_]{3,16}):.*?(?<!\\d)(?<x>[2-8]\\d{2})(?:\\s*[,/]\\s*|\\s+)(?:[yY]:\\s*)?(?<y>\\d{2,3})(?:\\s*[,/]\\s*|\\s+)(?:[zZ]:\\s*)?(?<z>[2-8]\\d{2})(?!\\d).*$");
    private static final Pattern CHAT_XZ_PATTERN = Pattern.compile(
            "^.*?(?<user>[A-Za-z0-9_]{3,16}):.*?(?<!\\d)(?<x>[2-8]\\d{2})(?:\\s*[,/]\\s*|\\s+)(?<z>[2-8]\\d{2})(?!\\d).*$");
    private static final Pattern PLAYER_SENDER_PATTERN = Pattern.compile("(?:^|>|\\])\\s*([A-Za-z0-9_]{3,16}):");

    public enum Crystal {
        AMBER,
        AMETHYST,
        JADE,
        SAPPHIRE,
        TOPAZ
    }

    public enum CrystalLocation {
        LOST_PRECURSOR_CITY("Lost Precursor City", 0xFFFFFFFF, 0xFFFFFFFF, 50),
        JUNGLE_TEMPLE("Jungle Temple", 0xFF55FF55, 0xFF55FF55, 50),
        GOBLIN_QUEENS_DEN("Goblin Queen's Den", 0xFFFFFF55, 0xFFFFFF55, 50),
        MINES_OF_DIVAN("Mines of Divan", 0xFF5555FF, 0xFF5555FF, 50),
        KING_YOLKAR("King Yolkar", 0xFFFFAA00, 0xFFFFAA00, 25),
        KHAZAD_DUM("Khazad-dum", 0xFFFF5555, 0xFFFF5555, 50),
        FAIRY_GROTTO("Fairy Grotto", 0xFFFF55FF, 0xFFFF55FF, 26),
        CORLEONE("Corleone", 0xFF55FFFF, 0xFF55FFFF, 26),
        ODAWA("Odawa", 0xFF00AA00, 0xFF00AA00, 26);

        public final String displayName;
        public final int fillColor;
        public final int borderColor;
        public final int mapRadius;

        private Double mapX;
        private Double worldY;
        private Double mapZ;
        private double minX = 1100.0;
        private double minY = 1100.0;
        private double minZ = 1100.0;
        private double maxX = -100.0;
        private double maxY = -100.0;
        private double maxZ = -100.0;

        CrystalLocation(String displayName, int fillColor, int borderColor, int mapRadius) {
            this.displayName = displayName;
            this.fillColor = fillColor;
            this.borderColor = borderColor;
            this.mapRadius = mapRadius;
        }

        public void reset() {
            this.mapX = null;
            this.worldY = null;
            this.mapZ = null;
            this.minX = 1100.0;
            this.minY = 1100.0;
            this.minZ = 1100.0;
            this.maxX = -100.0;
            this.maxY = -100.0;
            this.maxZ = -100.0;
        }

        public void updateFromPlayer(double worldX, double worldY, double worldZ) {
            double mx = clamp(worldX - 200.0, 0.0, 624.0);
            double my = clamp(worldY, 0.0, 256.0);
            double mz = clamp(worldZ - 200.0, 0.0, 624.0);

            this.minX = Math.min(this.minX, mx);
            this.minY = Math.min(this.minY, my);
            this.minZ = Math.min(this.minZ, mz);
            this.maxX = Math.max(this.maxX, mx);
            this.maxY = Math.max(this.maxY, my);
            this.maxZ = Math.max(this.maxZ, mz);

            this.mapX = (this.minX + this.maxX) / 2.0;
            this.worldY = (this.minY + this.maxY) / 2.0;
            this.mapZ = (this.minZ + this.maxZ) / 2.0;
        }

        public void setExactWorld(double worldX, double worldY, double worldZ) {
            double mx = clamp(worldX - 200.0, 0.0, 624.0);
            double my = clamp(worldY, 0.0, 256.0);
            double mz = clamp(worldZ - 200.0, 0.0, 624.0);

            this.mapX = mx;
            this.minX = mx;
            this.maxX = mx;

            this.worldY = my;
            this.minY = my;
            this.maxY = my;

            this.mapZ = mz;
            this.minZ = mz;
            this.maxZ = mz;
        }

        public boolean exists() {
            return mapX != null && worldY != null && mapZ != null;
        }

        public double getMapX() {
            return mapX != null ? mapX : 0.0;
        }

        public double getMapZ() {
            return mapZ != null ? mapZ : 0.0;
        }

        public double getWorldX() {
            return (mapX != null ? mapX : 0.0) + 200.0;
        }

        public double getWorldY() {
            return worldY != null ? worldY : 100.0;
        }

        public double getWorldZ() {
            return (mapZ != null ? mapZ : 0.0) + 200.0;
        }
    }

    public static class CustomWaypoint {
        public final String label;
        public final double x;
        public final double y;
        public final double z;
        public final int color;

        public CustomWaypoint(String label, double x, double y, double z, int color) {
            this.label = label;
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color;
        }
    }

    public static class SavedCoord {
        public final double worldX;
        public final double worldY;
        public final double worldZ;

        public SavedCoord(double worldX, double worldY, double worldZ) {
            this.worldX = worldX;
            this.worldY = worldY;
            this.worldZ = worldZ;
        }
    }

    private static final Map<String, EnumMap<CrystalLocation, SavedCoord>> SERVER_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, List<CustomWaypoint>> SERVER_CUSTOM_CACHE = new ConcurrentHashMap<>();
    private static final List<CustomWaypoint> CUSTOM_WAYPOINTS = new CopyOnWriteArrayList<>();

    private static ClientLevel lastLevel = null;
    private static volatile boolean inCrystalHollows = false;
    private static volatile boolean inMineshaftOrDwarven = false;
    private static volatile boolean inMineshaft = false;
    private static volatile String currentZoneName = "";
    private static volatile String currentServerId = "";
    private static volatile CustomWaypoint activeWishingWaypoint = null;
    private static volatile String lastChatCoordSig = "";
    private static volatile long lastChatCoordMs = 0L;
    private static int tickCounter = 0;

    public static boolean isInGemstoneMiningArea() {
        if (inCrystalHollows || inMineshaftOrDwarven) return true;
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.isLocalServer();
    }

    public static boolean isInMineshaft() {
        if (inMineshaft) return true;
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.isLocalServer();
    }

    public static String getCurrentServerId() {
        return currentServerId;
    }

    public static List<CustomWaypoint> getCustomWaypoints() {
        return CUSTOM_WAYPOINTS;
    }

    public static void addCustomWaypoint(String label, double worldX, double worldY, double worldZ, int color) {
        String cleanLabel = (label == null || label.isBlank()) ? "Waypoint" : label.trim();
        double cx = clamp(worldX, 200.0, 824.0);
        double cy = clamp(worldY, 30.0, 256.0);
        double cz = clamp(worldZ, 200.0, 824.0);
        CUSTOM_WAYPOINTS.removeIf(w -> w.label.equalsIgnoreCase(cleanLabel)
                || (Math.abs(w.x - cx) < 2.0 && Math.abs(w.z - cz) < 2.0));
        CUSTOM_WAYPOINTS.add(new CustomWaypoint(cleanLabel, cx, cy, cz, color));
        saveCurrentToCache();
    }

    public static void removeCustomWaypoint(CustomWaypoint target) {
        if (target == null) return;
        CUSTOM_WAYPOINTS.remove(target);
        saveCurrentToCache();
    }

    public static void onChatMessage(String rawText) {
        if (rawText == null || rawText.isEmpty()) return;
        String clean = stripColors(rawText).trim();
        Matcher m = SENDING_SERVER_PATTERN.matcher(clean);
        if (m.find()) {
            handleServerChange(m.group(1));
            return;
        }
        tryParseLobbyCoordinates(clean);
    }

    private static void tryParseLobbyCoordinates(String clean) {
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.crystalHollowsMapEnabled || !cfg.chChatCoordsEnabled || !isInCrystalHollows()) {
            return;
        }
        if (clean.startsWith("[DA]") || clean.startsWith("[NPC]") || clean.startsWith("[Bazaar]")) {
            return;
        }

        int x;
        int y;
        int z;
        String explicitLocHint = null;
        String sender = extractSenderName(clean);

        Matcher sbe = SBE_DSM_PATTERN.matcher(clean);
        if (sbe.find()) {
            explicitLocHint = sbe.group("loc");
            Integer px = parseIntSafe(sbe.group("x"));
            Integer py = parseIntSafe(sbe.group("y"));
            Integer pz = parseIntSafe(sbe.group("z"));
            if (px == null || py == null || pz == null) return;
            x = px;
            y = py;
            z = pz;
        } else {
            Matcher labeled = LABELED_XYZ_PATTERN.matcher(clean);
            if (labeled.find()) {
                Integer px = parseIntSafe(labeled.group("x"));
                Integer py = parseIntSafe(labeled.group("y"));
                Integer pz = parseIntSafe(labeled.group("z"));
                if (px == null || py == null || pz == null) return;
                x = px;
                y = py;
                z = pz;
            } else {
                Matcher xyz = CHAT_XYZ_PATTERN.matcher(clean);
                if (xyz.matches()) {
                    if (sender == null) sender = xyz.group("user");
                    Integer px = parseIntSafe(xyz.group("x"));
                    Integer py = parseIntSafe(xyz.group("y"));
                    Integer pz = parseIntSafe(xyz.group("z"));
                    if (px == null || py == null || pz == null) return;
                    x = px;
                    y = py;
                    z = pz;
                } else {
                    Matcher xz = CHAT_XZ_PATTERN.matcher(clean);
                    if (!xz.matches()) return;
                    if (sender == null) sender = xz.group("user");
                    Integer px = parseIntSafe(xz.group("x"));
                    Integer pz = parseIntSafe(xz.group("z"));
                    if (px == null || pz == null) return;
                    x = px;
                    String lower = clean.toLowerCase(Locale.ROOT);
                    y = (lower.contains("bal") || lower.contains("khazad") || lower.contains("magma")) ? 54 : 100;
                    z = pz;
                }
            }
        }

        if (!isValidHollowsCoord(x, y, z)) {
            return;
        }

        long now = System.currentTimeMillis();
        String sig = x + "," + y + "," + z;
        if (sig.equals(lastChatCoordSig) && now - lastChatCoordMs < 2000L) {
            return;
        }
        lastChatCoordSig = sig;
        lastChatCoordMs = now;

        CrystalLocation inferred = explicitLocHint != null
                ? inferLocationFromText(explicitLocHint)
                : inferLocationFromText(clean);
        sendClickableCoordPrompt(x, y, z, inferred, sender);
    }

    private static boolean isValidHollowsCoord(int x, int y, int z) {
        return x >= 200 && x <= 824 && y >= 30 && y <= 190 && z >= 200 && z <= 824;
    }

    private static Integer parseIntSafe(String raw) {
        if (raw == null) return null;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String extractSenderName(String clean) {
        Matcher m = PLAYER_SENDER_PATTERN.matcher(clean);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    public static String getTerrainZoneName(double x, double y, double z) {
        if (x >= 462 && x <= 564 && y >= 63 && y <= 181 && z >= 461 && z <= 565) {
            return "Crystal Nucleus";
        }
        if (y <= 64) {
            return "Magma Fields";
        }
        if (x < 512 && z < 512) {
            return "Jungle";
        }
        if (x >= 512 && z < 512) {
            return "Mithril Deposits";
        }
        if (x < 512 && z >= 512) {
            return "Goblin Holdout";
        }
        return "Precursor Remnants";
    }

    public static CrystalLocation inferLocationFromText(String text) {
        if (text == null || text.isEmpty()) return null;
        String normalized = text.toLowerCase(Locale.ROOT)
                .replace("û", "u")
                .replace("'", "");
        if (normalized.contains("precursor") || containsWord(normalized, "city")) {
            return CrystalLocation.LOST_PRECURSOR_CITY;
        }
        if (normalized.contains("jungle temple") || containsWord(normalized, "temple")) {
            return CrystalLocation.JUNGLE_TEMPLE;
        }
        if (normalized.contains("odawa")) {
            return CrystalLocation.ODAWA;
        }
        if (normalized.contains("goblin queen") || normalized.contains("queens den")
                || containsWord(normalized, "queen") || containsWord(normalized, "den")) {
            return CrystalLocation.GOBLIN_QUEENS_DEN;
        }
        if (normalized.contains("yolkar") || normalized.contains("goblin king") || containsWord(normalized, "king")) {
            return CrystalLocation.KING_YOLKAR;
        }
        if (normalized.contains("divan") || containsWord(normalized, "mod")) {
            return CrystalLocation.MINES_OF_DIVAN;
        }
        if (normalized.contains("corleone") || containsWord(normalized, "corl")) {
            return CrystalLocation.CORLEONE;
        }
        if (normalized.contains("khazad") || containsWord(normalized, "bal")) {
            return CrystalLocation.KHAZAD_DUM;
        }
        if (normalized.contains("fairy") || normalized.contains("grotto") || containsWord(normalized, "jasper")) {
            return CrystalLocation.FAIRY_GROTTO;
        }
        return null;
    }

    private static boolean containsWord(String lower, String word) {
        int idx = lower.indexOf(word);
        while (idx >= 0) {
            boolean leftOk = idx == 0 || !Character.isLetterOrDigit(lower.charAt(idx - 1));
            int end = idx + word.length();
            boolean rightOk = end >= lower.length() || !Character.isLetterOrDigit(lower.charAt(end));
            if (leftOk && rightOk) return true;
            idx = lower.indexOf(word, idx + 1);
        }
        return false;
    }

    private static Set<CrystalLocation> getTerrainCandidates(double x, double y, double z) {
        Set<CrystalLocation> set = new LinkedHashSet<>();
        if (y <= 64) {
            set.add(CrystalLocation.KHAZAD_DUM);
            set.add(CrystalLocation.FAIRY_GROTTO);
            return set;
        }
        if (x < 512 && z < 512) {
            set.add(CrystalLocation.JUNGLE_TEMPLE);
            set.add(CrystalLocation.ODAWA);
        } else if (x >= 512 && z < 512) {
            set.add(CrystalLocation.MINES_OF_DIVAN);
            set.add(CrystalLocation.CORLEONE);
        } else if (x < 512 && z >= 512) {
            set.add(CrystalLocation.GOBLIN_QUEENS_DEN);
            set.add(CrystalLocation.KING_YOLKAR);
        } else {
            set.add(CrystalLocation.LOST_PRECURSOR_CITY);
        }
        // Large multi-chunk structures can overlap ~50 blocks across quadrant or Magma borders
        if (y <= 72) {
            set.add(CrystalLocation.KHAZAD_DUM);
        }
        if (x < 562 && z < 562) set.add(CrystalLocation.JUNGLE_TEMPLE);
        if (x >= 462 && z < 562) set.add(CrystalLocation.MINES_OF_DIVAN);
        if (x < 562 && z >= 462) set.add(CrystalLocation.GOBLIN_QUEENS_DEN);
        if (x >= 462 && z >= 462) set.add(CrystalLocation.LOST_PRECURSOR_CITY);
        set.add(CrystalLocation.FAIRY_GROTTO);
        return set;
    }

    private static void sendClickableCoordPrompt(int x, int y, int z, CrystalLocation inferred, String sender) {
        String zone = getTerrainZoneName(x, y, z);
        MutableComponent root = Component.literal("§6[DA] §eFound coords §f(" + x + ", " + y + ", " + z + ") ")
                .append(Component.literal("[" + zone + "] ").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("click to mark:\n").withStyle(ChatFormatting.GRAY));

        Set<CrystalLocation> ordered = new LinkedHashSet<>();
        if (inferred != null) {
            ordered.add(inferred);
        }
        for (CrystalLocation cand : getTerrainCandidates(x, y, z)) {
            if (!cand.exists() || cand == inferred) {
                ordered.add(cand);
            }
        }
        if (ordered.isEmpty()) {
            for (CrystalLocation loc : CrystalLocation.values()) {
                if (!loc.exists()) ordered.add(loc);
            }
        }

        for (CrystalLocation loc : ordered) {
            boolean primary = (loc == inferred);
            String label = primary ? "[* " + loc.displayName + "]" : "[" + loc.displayName + "]";
            String cmd = "/dahw set " + loc.name() + " " + x + " " + y + " " + z;
            Style btnStyle = Style.EMPTY
                    .withColor(TextColor.fromRgb(loc.fillColor & 0x00FFFFFF))
                    .withBold(primary)
                    .withClickEvent(new ClickEvent.RunCommand(cmd))
                    .withHoverEvent(new HoverEvent.ShowText(
                            Component.literal("Click to mark " + loc.displayName + " at (" + x + ", " + y + ", " + z + ") on Crystal Hollows Map")));
            root.append(Component.literal(label).setStyle(btnStyle)).append(Component.literal(" "));
        }

        String customName = (inferred != null)
                ? inferred.displayName
                : ((sender != null && !sender.isBlank()) ? sender : zone);
        String safeCustomName = customName.replace(' ', '_');
        String customCmd = "/dahw custom " + x + " " + y + " " + z + " " + safeCustomName;
        Style customStyle = Style.EMPTY
                .withColor(ChatFormatting.YELLOW)
                .withClickEvent(new ClickEvent.RunCommand(customCmd))
                .withHoverEvent(new HoverEvent.ShowText(
                        Component.literal("Click to add custom waypoint \"" + customName + "\" at (" + x + ", " + y + ", " + z + ")")));
        root.append(Component.literal("[Custom]").setStyle(customStyle));

        NotificationUtils.sendClientChatMessage(root);
    }

    public static boolean handleClientWaypointCommand(String argsRaw) {
        String trimmed = argsRaw == null ? "" : argsRaw.trim();
        if (trimmed.isEmpty()) {
            NotificationUtils.sendClientChatMessage(Component.literal(
                    "§6[DA] §eUsage: /dahw set <LOCATION> <x> <y> <z> | /dahw custom <x> <y> <z> [name] | /dahw clear"));
            return true;
        }
        String[] parts = trimmed.split("\\s+");
        String sub = parts[0].toLowerCase(Locale.ROOT);
        if ("clear".equals(sub)) {
            resetAll();
            if (!currentServerId.isEmpty()) {
                SERVER_CACHE.remove(currentServerId);
                SERVER_CUSTOM_CACHE.remove(currentServerId);
            }
            NotificationUtils.sendClientChatMessage(Component.literal("§6[DA] §aCleared all Crystal Hollows Map waypoints."));
            return true;
        }
        if ("set".equals(sub) && parts.length >= 5) {
            CrystalLocation loc = resolveLocationArg(parts[1]);
            Integer x = parseIntSafe(parts[2]);
            Integer y = parseIntSafe(parts[3]);
            Integer z = parseIntSafe(parts[4]);
            if (loc == null || x == null || y == null || z == null) {
                NotificationUtils.sendClientChatMessage(Component.literal("§6[DA] §cInvalid location or coordinates."));
                return true;
            }
            setManualLocation(loc, x, y, z);
            String zone = getTerrainZoneName(x, y, z);
            NotificationUtils.sendClientChatMessage(Component.literal(
                    "§6[DA] §aMarked §f" + loc.displayName + " §aat §e(" + x + ", " + y + ", " + z + ") §7[" + zone + "]"));
            return true;
        }
        if ("custom".equals(sub) && parts.length >= 4) {
            Integer x = parseIntSafe(parts[1]);
            Integer y = parseIntSafe(parts[2]);
            Integer z = parseIntSafe(parts[3]);
            if (x == null || y == null || z == null) {
                NotificationUtils.sendClientChatMessage(Component.literal("§6[DA] §cInvalid coordinates."));
                return true;
            }
            String label = parts.length >= 5
                    ? String.join(" ", java.util.Arrays.copyOfRange(parts, 4, parts.length)).replace('_', ' ')
                    : getTerrainZoneName(x, y, z);
            addCustomWaypoint(label, x, y, z, 0xFFFFFF55);
            NotificationUtils.sendClientChatMessage(Component.literal(
                    "§6[DA] §aAdded custom waypoint §e" + label + " §aat §e(" + x + ", " + y + ", " + z + ")"));
            return true;
        }
        return true;
    }

    private static CrystalLocation resolveLocationArg(String raw) {
        if (raw == null) return null;
        for (CrystalLocation loc : CrystalLocation.values()) {
            if (loc.name().equalsIgnoreCase(raw) || loc.displayName.replace(" ", "_").equalsIgnoreCase(raw)) {
                return loc;
            }
        }
        return inferLocationFromText(raw);
    }

    public static void setManualLocation(CrystalLocation loc, double worldX, double worldY, double worldZ) {
        if (loc == null) return;
        loc.setExactWorld(worldX, worldY, worldZ);
        saveCurrentToCache();
    }

    public static void clearLocation(CrystalLocation loc) {
        if (loc == null) return;
        loc.reset();
        if (!currentServerId.isEmpty()) {
            EnumMap<CrystalLocation, SavedCoord> map = SERVER_CACHE.get(currentServerId);
            if (map != null) {
                map.remove(loc);
            }
        }
    }

    private static void handleServerChange(String newServerId) {
        String next = newServerId != null ? newServerId.trim() : "";
        if (next.equals(currentServerId)) {
            return;
        }

        // Save previous server's discovered coordinates in memory before switching
        saveCurrentToCache();

        currentServerId = next;
        resetActiveLocationsOnly();

        // Restore if player returned to a previously visited server in this session
        if (!currentServerId.isEmpty()) {
            EnumMap<CrystalLocation, SavedCoord> cached = SERVER_CACHE.get(currentServerId);
            if (cached != null) {
                for (Map.Entry<CrystalLocation, SavedCoord> e : cached.entrySet()) {
                    SavedCoord c = e.getValue();
                    e.getKey().setExactWorld(c.worldX, c.worldY, c.worldZ);
                }
            }
            List<CustomWaypoint> cachedCustom = SERVER_CUSTOM_CACHE.get(currentServerId);
            if (cachedCustom != null && !cachedCustom.isEmpty()) {
                CUSTOM_WAYPOINTS.addAll(cachedCustom);
            }
        }
    }

    private static void saveCurrentToCache() {
        if (currentServerId == null || currentServerId.isEmpty()) return;
        EnumMap<CrystalLocation, SavedCoord> map = SERVER_CACHE.computeIfAbsent(
                currentServerId, k -> new EnumMap<>(CrystalLocation.class));
        for (CrystalLocation loc : CrystalLocation.values()) {
            if (loc.exists()) {
                map.put(loc, new SavedCoord(loc.getWorldX(), loc.getWorldY(), loc.getWorldZ()));
            }
        }
        SERVER_CUSTOM_CACHE.put(currentServerId, new ArrayList<>(CUSTOM_WAYPOINTS));
    }

    private static void resetActiveLocationsOnly() {
        for (CrystalLocation loc : CrystalLocation.values()) {
            loc.reset();
        }
        CUSTOM_WAYPOINTS.clear();
        activeWishingWaypoint = null;
        CrystalWishingCompassSolver.getInstance().reset();
    }

    public static CustomWaypoint getActiveWishingWaypoint() {
        return activeWishingWaypoint;
    }

    public static void setActiveWishingWaypoint(CustomWaypoint waypoint) {
        activeWishingWaypoint = waypoint;
    }

    public static boolean isInCrystalHollows() {
        return inCrystalHollows;
    }

    public static String getCurrentZoneName() {
        return currentZoneName;
    }

    public static void resetAll() {
        resetActiveLocationsOnly();
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            inCrystalHollows = false;
            inMineshaftOrDwarven = false;
            inMineshaft = false;
            currentZoneName = "";
            if (lastLevel != null) {
                lastLevel = null;
                handleServerChange("");
            }
            return;
        }

        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            inCrystalHollows = false;
            inMineshaftOrDwarven = false;
            inMineshaft = false;
            currentZoneName = "";
            resetActiveLocationsOnly();
        }

        // Evaluate scoreboard & entities every 5 ticks (4x/sec) to keep overhead near zero
        if (++tickCounter % 5 != 0) {
            return;
        }

        updateLocationFromScoreboardAndTab(mc);
        if (!inCrystalHollows) {
            return;
        }

        // Update current zone bounds if player is inside a special structure
        CrystalLocation matched = matchZoneLocation(currentZoneName);
        if (matched != null) {
            matched.updateFromPlayer(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            saveCurrentToCache();
        }

        // Scan loaded entities for King Yolkar & Boss Corleone
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ArmorStand stand) {
                Component customName = stand.getCustomName();
                if (customName != null) {
                    String clean = stripColors(customName.getString());
                    if (clean.contains("King Yolkar")) {
                        CrystalLocation.KING_YOLKAR.setExactWorld(stand.getX(), stand.getY(), stand.getZ());
                        saveCurrentToCache();
                    }
                }
            } else if (entity instanceof Player other && other != mc.player) {
                String cleanName = stripColors(other.getName().getString());
                if ("Team Treasurite".equals(cleanName) && other.getMaxHealth() >= 950_000f) {
                    CrystalLocation.CORLEONE.setExactWorld(other.getX(), other.getY(), other.getZ());
                    saveCurrentToCache();
                }
            }
        }
    }

    private static void updateLocationFromScoreboardAndTab(Minecraft mc) {
        boolean detectedHollows = false;
        boolean detectedMineshaft = false;
        String detectedZone = "";
        String detectedServer = "";

        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar != null) {
            String title = stripColors(sidebar.getDisplayName().getString()).toLowerCase(Locale.ROOT);
            if (title.replace(" ", "").contains("skyblock")) {
                Collection<PlayerScoreEntry> entries = scoreboard.listPlayerScores(sidebar);
                for (PlayerScoreEntry entry : entries) {
                    String owner = entry.owner();
                    PlayerTeam team = scoreboard.getPlayersTeam(owner);
                    String rawLine;
                    if (entry.display() != null) {
                        rawLine = entry.display().getString();
                    } else if (team != null) {
                        rawLine = team.getPlayerPrefix().getString() + owner + team.getPlayerSuffix().getString();
                    } else {
                        rawLine = owner;
                    }
                    String clean = stripColors(rawLine).trim();
                    Matcher sm = SERVER_ID_PATTERN.matcher(clean);
                    if (sm.find()) {
                        detectedServer = sm.group(1);
                    }
                    if (clean.contains("⏣") || clean.contains("ф")) {
                        String zone = clean.replace("⏣", "").replace("ф", "").trim();
                        detectedZone = zone;
                        if (isCrystalHollowsZone(zone)) {
                            detectedHollows = true;
                        } else if (isMineshaftOrDwarvenZone(zone)) {
                            detectedMineshaft = true;
                        }
                    } else if (isCrystalHollowsZone(clean)) {
                        detectedHollows = true;
                        detectedZone = clean;
                    } else if (isMineshaftOrDwarvenZone(clean)) {
                        detectedMineshaft = true;
                        detectedZone = clean;
                    }
                }
            }
        }

        if (!detectedServer.isEmpty()) {
            handleServerChange(detectedServer);
        }

        // Fallback/confirmation via TabList "Area: Crystal Hollows" / "Mineshaft" / "Dwarven Mines"
        if (!detectedHollows && !detectedMineshaft && mc.getConnection() != null) {
            for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
                Component tabName = info.getTabListDisplayName();
                if (tabName == null) continue;
                String clean = stripColors(tabName.getString()).trim();
                if (clean.startsWith("Area:")) {
                    if (clean.contains("Crystal Hollows")) {
                        detectedHollows = true;
                        break;
                    } else if (clean.contains("Mineshaft") || clean.contains("Dwarven Mines")) {
                        detectedMineshaft = true;
                        break;
                    }
                }
            }
        }

        inCrystalHollows = detectedHollows;
        inMineshaftOrDwarven = detectedMineshaft;
        inMineshaft = isMineshaftZone(detectedZone) || hasMineshaftTabArea(mc);
        currentZoneName = detectedZone;
    }

    private static boolean isMineshaftZone(String zone) {
        return zone != null && zone.toLowerCase(Locale.ROOT).contains("mineshaft");
    }

    private static boolean hasMineshaftTabArea(Minecraft mc) {
        if (mc.getConnection() == null) return false;
        for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
            Component tabName = info.getTabListDisplayName();
            if (tabName == null) continue;
            String clean = stripColors(tabName.getString()).trim().toLowerCase(Locale.ROOT);
            if (clean.startsWith("area:") && clean.contains("mineshaft")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMineshaftOrDwarvenZone(String zone) {
        if (zone == null || zone.isEmpty()) return false;
        String lower = zone.toLowerCase(Locale.ROOT);
        return lower.contains("mineshaft")
                || lower.contains("glacite")
                || lower.contains("dwarven")
                || lower.contains("fossil research")
                || lower.contains("base camp");
    }

    private static boolean isCrystalHollowsZone(String zone) {
        if (zone == null || zone.isEmpty()) return false;
        String lower = zone.toLowerCase(Locale.ROOT);
        return lower.contains("crystal hollows")
                || lower.contains("crystal nucleus")
                || lower.contains("mithril deposits")
                || lower.contains("mines of divan")
                || lower.contains("precursor remnants")
                || lower.contains("lost precursor city")
                || lower.contains("goblin holdout")
                || lower.contains("goblin queen")
                || lower.contains("jungle")
                || lower.contains("magma fields")
                || lower.contains("khazad")
                || lower.contains("fairy grotto")
                || lower.contains("dragon's lair");
    }

    private static CrystalLocation matchZoneLocation(String zone) {
        if (zone == null || zone.isEmpty()) return null;
        String lower = zone.toLowerCase(Locale.ROOT);
        if (lower.contains("lost precursor city")) return CrystalLocation.LOST_PRECURSOR_CITY;
        if (lower.contains("jungle temple")) return CrystalLocation.JUNGLE_TEMPLE;
        if (lower.contains("goblin queen")) return CrystalLocation.GOBLIN_QUEENS_DEN;
        if (lower.contains("mines of divan")) return CrystalLocation.MINES_OF_DIVAN;
        if (lower.contains("khazad")) return CrystalLocation.KHAZAD_DUM;
        if (lower.contains("fairy grotto")) return CrystalLocation.FAIRY_GROTTO;
        return null;
    }

    public static boolean isJungleKeyInInventory(Minecraft mc) {
        if (mc.player == null) return false;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stripColors(stack.getHoverName().getString()).contains("Jungle Key")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isKingsScentPresent(Minecraft mc) {
        ClientPacketListener conn = mc.getConnection();
        if (conn == null) return false;
        for (PlayerInfo info : conn.getListedOnlinePlayers()) {
            Component tabName = info.getTabListDisplayName();
            if (tabName != null && stripColors(tabName.getString()).contains("King's Scent")) {
                return true;
            }
        }
        return false;
    }

    public static EnumSet<Crystal> getFoundCrystals(Minecraft mc) {
        EnumSet<Crystal> found = EnumSet.noneOf(Crystal.class);
        ClientPacketListener conn = mc.getConnection();
        if (conn == null) return found;
        for (PlayerInfo info : conn.getListedOnlinePlayers()) {
            Component tabName = info.getTabListDisplayName();
            if (tabName == null) continue;
            String clean = stripColors(tabName.getString());
            if (clean.contains("✔") || clean.contains("Found") || clean.contains("Placed")) {
                String upper = clean.toUpperCase(Locale.ROOT);
                for (Crystal c : Crystal.values()) {
                    if (upper.contains(c.name())) {
                        found.add(c);
                    }
                }
            }
        }
        return found;
    }

    public static String stripColors(String input) {
        if (input == null) return "";
        return COLOR_CODE_PATTERN.matcher(input).replaceAll("");
    }

    private static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }
}
