package com.dttdrt.addons.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_DIR = ConfigPaths.getConfigDir();
    private static final File CONFIG_FILE = new File(CONFIG_DIR, "config.json");

    private static ModConfig instance;

    public String lastSelectedCategory = "AH / BZ";
    public String lastMode = "AH";

    // Separate AH and BZ last searched items (empty on fresh startup)
    public String lastAhQuery = "";
    public String lastAhItemId = "";
    public String lastBzQuery = "";
    public String lastBzItemId = "";

    // Alert settings
    public boolean alertEnabled = false;
    public String alertMinPriceInput = "";
    public String alertMaxPriceInput = "";

    // Notification channels
    public boolean notifyChat = true;
    public boolean notifySound = true;
    public boolean notifyTitle = true;

    public int refreshIntervalSeconds = 30;

    // Crystal Hollows Map
    public boolean crystalHollowsMapEnabled = true;
    public int chMapX = 8;
    public int chMapY = 8;
    public float chMapScale = 0.20f;

    // Custom Menu Background & Display
    public boolean uiSoundsEnabled = true;
    public boolean showMarketHud = true;
    public float marketHudScale = 1.0f;  // 0.60 .. 1.50 overall Market HUD scale
    public int drawerTopLineColor = 0xFFFF66AA; // Customizable top accent line color
    public String customBackgroundPath = "";
    public int backgroundDarkness = 165; // 0 .. 242 alpha overlay
    public float bgScale = 1.0f;         // 0.25 .. 3.0
    public int bgOffsetX = 0;            // -300 .. 300
    public int bgOffsetY = 0;            // -300 .. 300
    public int uiPanelOpacity = 215;     // 40 .. 245 alpha for cards/panels
    public float uiScale = 1.0f;         // Preset scale: 0.6, 0.75, 0.85, 1.0, 1.15, 1.25

    // Gemstone ESP (MelodySky-style)
    public boolean gemstoneEspEnabled = false;
    public String gemstoneEspKey = "key.keyboard.unknown";
    public int gemstoneEspRadius = 100;           // 1 .. 500 blocks
    public int gemstoneEspScanIntervalMs = 100;   // 100 .. 5000 ms

    // Temple Waypoint (Crystal Hollows Jungle Temple helper)
    public String templeWaypointRenderStyle = "NEU"; // NEU, Skytils
    public boolean gemstoneJade = true;
    public boolean gemstoneAmber = true;
    public boolean gemstoneTopaz = true;
    public boolean gemstoneSapphire = true;
    public boolean gemstoneAmethyst = true;
    public boolean gemstoneJasper = true;
    public boolean gemstoneRuby = true;
    public boolean gemstoneOpal = true;
    public boolean gemstoneOnyx = true;
    public boolean gemstoneAquamarine = true;
    public boolean gemstoneCitrine = true;
    public boolean gemstonePeridot = true;

    // Glacite Mineshaft Corpse & LittleFoot ESP
    public boolean corpseEspEnabled = false;
    public boolean corpseLapis = true;
    public boolean corpseUmber = true;
    public boolean corpseTungsten = true;
    public boolean littleFootEspEnabled = false;

    // Mining Profit Tracker
    public boolean miningProfitEnabled = false;
    public String miningProfitPriceMode = "NPC"; // NPC, BZ_SELL_OFFER, BZ_BUY_ORDER
    public String miningProfitDisplayMode = "PERSISTENT"; // PERSISTENT, SESSION
    public int miningProfitHudX = 12;
    public int miningProfitHudY = 140;
    public float miningProfitHudScale = 1.0f;

    // Scatha Mining Profit Tracker
    public boolean scathaProfitEnabled = false;
    public String scathaProfitPriceMode = "BZ_SELL_OFFER"; // NPC, BZ_SELL_OFFER, BZ_BUY_ORDER
    public String scathaProfitDisplayMode = "PERSISTENT"; // PERSISTENT, SESSION
    public int scathaProfitHudX = 12;
    public int scathaProfitHudY = 260;
    public float scathaProfitHudScale = 1.0f;

    // Nucleus Run Profit Tracker
    public boolean nucleusProfitEnabled = false;
    public String nucleusProfitPriceMode = "BZ_SELL_OFFER"; // NPC, BZ_SELL_OFFER, BZ_BUY_ORDER
    public String nucleusProfitDisplayMode = "PERSISTENT"; // PERSISTENT, SESSION
    public int nucleusProfitHudX = 12;
    public int nucleusProfitHudY = 380;
    public float nucleusProfitHudScale = 1.0f;

    // Party !vote Command
    public boolean partyVoteEnabled = true;

    // Custom Keybinds (InputConstants translation key names)
    public String openMenuKey = "key.keyboard.m";
    public String quickVoteKey = "key.keyboard.unknown";
    public String quickVoteYesKey = "key.keyboard.unknown";
    public String quickVoteNoKey = "key.keyboard.unknown";

    // Mineshaft !vote Streak Tracker (Party >= 5)
    public static final String DEFAULT_STREAK_MESSAGE = "";
    public boolean mineshaftStreakEnabled = false;
    public String mineshaftStreakMessage = "";
    public int mineshaftStreak = 0;

    // Cached last known Bazaar Buy Order prices for mining cost/key items
    public Map<String, Double> lastCostBzPrices = new ConcurrentHashMap<>();

    // Storage (EC/BP) Preview Overlay
    public boolean storageOverlayEnabled = true;
    public int storageOverlayColumns = 3;
    public int storageOverlayHeight = 3 * 18 * 6;
    public boolean storageOverlayRetainScroll = true;
    public int storageOverlayScrollSpeed = 10;
    public boolean storageOverlayInverseScroll = false;
    public boolean storageOverlayItemsBlockScroll = true;
    public boolean storageOverlayHighlightSearch = true;
    public boolean storageOverlayShowInactiveTooltips = true;
    public boolean storageOverlayOutlineActivePage = false;

    // Nametag (other players only: show nametag, distance, color palette)
    public boolean nametagEnabled = false;
    public boolean nametagShowDistance = false;
    public int nametagColor = 0xFFFF69B4;

    // Combat
    public boolean damageTrackerEnabled = false;

    // FreeCam (100% pure client-side detached camera)
    public String freecamKey = "key.keyboard.unknown";
    public float freecamSpeed = 1.5f;
    public boolean freecamShowHud = true;

    // Chroma Shader (§z dynamic rainbow text)
    public boolean chromaEnabled = true;
    public float chromaSize = 30.0f;       // 5 .. 100
    public float chromaSpeed = 6.0f;       // 0.5 .. 20.0
    public float chromaSaturation = 0.75f; // 0.0 .. 1.0
    public String chromaDirection = "FORWARD_RIGHT"; // FORWARD_RIGHT, FORWARD_LEFT, BACKWARD_RIGHT, BACKWARD_LEFT

    // Custom Name (visual client-side name replacement)
    public boolean customNameEnabled = false;
    public String customNameText = "";
    public String customNameColorMode = "CHROMA"; // CHROMA, SOLID
    public int customNameColor = 0xFFFF55FF;
    public boolean customNameBold = false;
    public boolean customNameItalic = false;
    public boolean customNameUnderlined = false;

    public static ModConfig getInstance() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                instance = GSON.fromJson(reader, ModConfig.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (instance == null) {
            instance = new ModConfig();
            save();
        }
        if (instance.openMenuKey == null || instance.openMenuKey.isEmpty()) {
            instance.openMenuKey = "key.keyboard.m";
        }
        if (instance.quickVoteKey == null || instance.quickVoteKey.isEmpty()) {
            instance.quickVoteKey = "key.keyboard.unknown";
        }
        if (instance.quickVoteYesKey == null || instance.quickVoteYesKey.isEmpty()) {
            instance.quickVoteYesKey = "key.keyboard.unknown";
        }
        if (instance.quickVoteNoKey == null || instance.quickVoteNoKey.isEmpty()) {
            instance.quickVoteNoKey = "key.keyboard.unknown";
        }
        if (instance.nametagColor == 0) {
            instance.nametagColor = 0xFFFF69B4;
        } else {
            instance.nametagColor |= 0xFF000000;
        }
        if (instance.freecamKey == null || instance.freecamKey.isEmpty()) {
            instance.freecamKey = "key.keyboard.unknown";
        }
        if (instance.gemstoneEspKey == null || instance.gemstoneEspKey.isEmpty()) {
            instance.gemstoneEspKey = "key.keyboard.unknown";
        }
        if (!"Skytils".equalsIgnoreCase(instance.templeWaypointRenderStyle)) {
            instance.templeWaypointRenderStyle = "NEU";
        } else {
            instance.templeWaypointRenderStyle = "Skytils";
        }
        if (instance.freecamSpeed < 0.25f || instance.freecamSpeed > 6.0f || Float.isNaN(instance.freecamSpeed)) {
            instance.freecamSpeed = 1.5f;
        }
        if (instance.mineshaftStreakMessage == null) {
            instance.mineshaftStreakMessage = "";
        }
        if (instance.mineshaftStreak < 0) {
            instance.mineshaftStreak = 0;
        }
        if (!"SESSION".equalsIgnoreCase(instance.miningProfitDisplayMode)) {
            instance.miningProfitDisplayMode = "PERSISTENT";
        } else {
            instance.miningProfitDisplayMode = "SESSION";
        }
        if (!"SESSION".equalsIgnoreCase(instance.scathaProfitDisplayMode)) {
            instance.scathaProfitDisplayMode = "PERSISTENT";
        } else {
            instance.scathaProfitDisplayMode = "SESSION";
        }
        if (instance.scathaProfitPriceMode == null || instance.scathaProfitPriceMode.isBlank()) {
            instance.scathaProfitPriceMode = "BZ_SELL_OFFER";
        }
        if (!"SESSION".equalsIgnoreCase(instance.nucleusProfitDisplayMode)) {
            instance.nucleusProfitDisplayMode = "PERSISTENT";
        } else {
            instance.nucleusProfitDisplayMode = "SESSION";
        }
        if (instance.nucleusProfitPriceMode == null || instance.nucleusProfitPriceMode.isBlank()) {
            instance.nucleusProfitPriceMode = "BZ_SELL_OFFER";
        }
        if (instance.lastCostBzPrices == null) {
            instance.lastCostBzPrices = new ConcurrentHashMap<>();
        } else if (!(instance.lastCostBzPrices instanceof ConcurrentHashMap)) {
            instance.lastCostBzPrices = new ConcurrentHashMap<>(instance.lastCostBzPrices);
        }
        if (instance.chromaSize < 5.0f || instance.chromaSize > 100.0f || Float.isNaN(instance.chromaSize)) {
            instance.chromaSize = 30.0f;
        }
        if (instance.chromaSpeed < 0.5f || instance.chromaSpeed > 20.0f || Float.isNaN(instance.chromaSpeed)) {
            instance.chromaSpeed = 6.0f;
        }
        if (instance.chromaSaturation < 0.0f || instance.chromaSaturation > 1.0f || Float.isNaN(instance.chromaSaturation)) {
            instance.chromaSaturation = 0.75f;
        }
        if (instance.chromaDirection == null || instance.chromaDirection.isBlank()) {
            instance.chromaDirection = "FORWARD_RIGHT";
        }
        if (instance.customNameText == null) {
            instance.customNameText = "";
        }
        if (!"SOLID".equalsIgnoreCase(instance.customNameColorMode)) {
            instance.customNameColorMode = "CHROMA";
        } else {
            instance.customNameColorMode = "SOLID";
        }
        if (instance.customNameColor == 0) {
            instance.customNameColor = 0xFFFF55FF;
        } else {
            instance.customNameColor |= 0xFF000000;
        }
    }

    public static synchronized void save() {
        try {
            if (!CONFIG_DIR.exists()) {
                CONFIG_DIR.mkdirs();
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(getInstance(), writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
