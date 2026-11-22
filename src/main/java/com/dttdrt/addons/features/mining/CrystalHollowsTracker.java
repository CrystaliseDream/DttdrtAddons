package com.dttdrt.addons.features.mining;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    private static ClientLevel lastLevel = null;
    private static volatile boolean inCrystalHollows = false;
    private static volatile boolean inMineshaftOrDwarven = false;
    private static volatile boolean inMineshaft = false;
    private static volatile String currentZoneName = "";
    private static volatile String currentServerId = "";
    private static volatile CustomWaypoint activeWishingWaypoint = null;
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

    public static void onChatMessage(String rawText) {
        if (rawText == null || rawText.isEmpty()) return;
        String clean = stripColors(rawText);
        Matcher m = SENDING_SERVER_PATTERN.matcher(clean);
        if (m.find()) {
            handleServerChange(m.group(1));
        }
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
    }

    private static void resetActiveLocationsOnly() {
        for (CrystalLocation loc : CrystalLocation.values()) {
            loc.reset();
        }
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
            if (title.contains("skyblock")) {
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
