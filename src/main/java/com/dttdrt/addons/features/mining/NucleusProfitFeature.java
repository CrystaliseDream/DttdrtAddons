package com.dttdrt.addons.features.mining;

import com.dttdrt.addons.api.CoflnetAhApi;
import com.dttdrt.addons.api.HypixelBazaarApi;
import com.dttdrt.addons.api.ItemDatabase;
import com.dttdrt.addons.core.auth.WhitelistManager;
import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.core.feature.Feature;
import com.dttdrt.addons.core.lang.ModLang;
import com.dttdrt.addons.features.market.model.SkyblockItem;
import com.dttdrt.addons.features.mining.profit.MiningItemTable;
import com.dttdrt.addons.features.mining.profit.ProfitDataStore;
import com.dttdrt.addons.texture.PackDisablerIconBridge;
import com.dttdrt.addons.util.NumberFormatUtils;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure client-side Crystal Nucleus Run Profit Tracker.
 * Loot & fixed per-run cost deduction (1x Jungle Key + 6 Robot Parts) modeled after SkyHanni's CrystalNucleusTracker,
 * combined with custom crystal-found timing start, CH/lobby auto-pause, 60s coordinate-AFK auto-pause,
 * AH Lowest BIN / Bazaar / NPC pricing, and SkyHanni-style Persistent/Session dual-bucket persistence.
 */
public class NucleusProfitFeature implements Feature {

    private static NucleusProfitFeature instance;

    private static final Identifier HUD_ID =
            Identifier.fromNamespaceAndPath("dttdrtaddons", "nucleus_profit_hud");

    private static final long AFK_TIMEOUT_MS = 60_000L;
    private static final long CRYSTAL_HEADER_WINDOW_MS = 3_000L;
    private static final long UNCHECKED_BOOK_TIMEOUT_MS = 5_000L;
    private static final long BAL_SHARD_DEDUPE_MS = 2_500L;
    private static final long BZ_REFRESH_INTERVAL_MS = 30_000L;
    private static final long AH_REFRESH_INTERVAL_MS = 60_000L;
    private static final int MAX_VISIBLE_ROWS = 12;

    // Fixed per-run SkyHanni default costs (1x Jungle Key + 6 Robot Parts)
    private static final List<String> NUCLEUS_COST_KEYS = List.of(
            "JUNGLE_KEY",
            "CONTROL_SWITCH",
            "ELECTRON_TRANSMITTER",
            "FTX_3070",
            "ROBOTRON_REFLECTOR",
            "SUPERLITE_MOTOR",
            "SYNTHETIC_HEART"
    );

    // Non-Bazaar items always priced via AH Lowest BIN across all modes
    private static final Set<String> AH_LBIN_KEYS = Set.of(
            "DIVAN_ALLOY",
            "PET_ITEM_QUICK_CLAW",
            "BAL;3",
            "BAL;4",
            "PICKONIMBUS",
            "PREHISTORIC_EGG",
            "RECALL_POTION"
    );

    private static final Set<String> CRYSTAL_NAMES = Set.of(
            "TOPAZ CRYSTAL",
            "JADE CRYSTAL",
            "AMETHYST CRYSTAL",
            "AMBER CRYSTAL",
            "SAPPHIRE CRYSTAL"
    );

    private static final Pattern CRYSTAL_FOUND_PATTERN =
            Pattern.compile("CRYSTAL FOUND\\s*\\(\\d+/5\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BUNDLE_END_PATTERN = Pattern.compile("^\\s*\u25AC{20,}\\s*$");
    private static final Pattern LEADING_AMOUNT_PATTERN =
            Pattern.compile("^\\+?([\\d,]+)(?:x)?\\s+(.+?)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_AMOUNT_PATTERN =
            Pattern.compile("^(.+?)\\s+[xX]([\\d,]+)\\s*$");
    private static final Pattern BAL_OBTAINED_PATTERN =
            Pattern.compile("^(?:\\[[^\\]]+\\]\\s*)?([A-Za-z0-9_]{1,16})\\s+has obtained\\s+\\[Lvl \\d+]\\s+Bal!$",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern BAL_SHARD_CAUGHT_PATTERN =
            Pattern.compile("^You caught(?: an?)?(?: x(\\d+))? Bal Shards?!$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BAL_SHARD_CHARM_PATTERN =
            Pattern.compile("^CHARM! You charmed the Bal and received (\\d+) Bal Shards?!$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BAL_SHARD_LOOTSHARE_PATTERN =
            Pattern.compile("^LOOT SHARE!? You received (?:an?|(\\d+)x?) Bal Shards? (?:for assisting .*|from .*)!$",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern BAL_SHARD_HUNTING_BOX_PATTERN =
            Pattern.compile("^You sent (?:an?|(\\d+)) Bal Shards? to your Hunting Box\\.$", Pattern.CASE_INSENSITIVE);

    private record DisplayRow(String key, String name, double amount, double coins, int color) {}

    private final Map<String, double[]> bzPrices = new ConcurrentHashMap<>();
    private final Map<String, Double> ahLowestBins = new ConcurrentHashMap<>();
    private final Map<String, String> dynamicDisplayNames = new ConcurrentHashMap<>();
    private final Map<String, Double> dynamicNpcPrices = new ConcurrentHashMap<>();

    // Transient Crystal Nucleus bundle loop state
    private boolean inLootLoop = false;
    private int unCheckedBooks = 0;
    private long unCheckedBookDeadlineMs = 0L;
    private final Map<String, Integer> pendingLoot = new HashMap<>();
    private Map<Integer, ItemStack> prevBookSlots = new HashMap<>();

    // Crystal start & AFK coordinate state
    private ClientLevel lastLevel = null;
    private String lastServerId = "";
    private volatile boolean timerActive = false;
    private volatile boolean pausedByAfk = false;
    private long crystalFoundHeaderAtMs = 0L;

    private boolean lastPosValid = false;
    private double lastX = 0.0;
    private double lastY = 0.0;
    private double lastZ = 0.0;
    private long lastMoveMs = 0L;

    private long lastSecondTickMs = 0L;
    private long lastBzFetchMs = 0L;
    private long lastAhFetchMs = 0L;
    private long lastPrimaryBalShardMs = 0L;

    public NucleusProfitFeature() {
        instance = this;
        MiningItemTable.ensureLoaded();
    }

    public static NucleusProfitFeature getInstance() {
        return instance;
    }

    @Override
    public String getId() {
        return "nucleus_profit";
    }

    @Override
    public String getName() {
        return "Nucleus Run Profit";
    }

    @Override
    public String getCategory() {
        return "Mining";
    }

    @Override
    public boolean isEnabled() {
        return ModConfig.getInstance().nucleusProfitEnabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        ModConfig.getInstance().nucleusProfitEnabled = enabled;
        ModConfig.save();
    }

    @Override
    public void init() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.OVERLAY_MESSAGE, HUD_ID, (graphics, deltaTracker) -> {
            Minecraft mc = Minecraft.getInstance();
            if (!WhitelistManager.isAuthorized() || !isEnabled()) return;
            if (mc.player == null || mc.level == null || mc.options.hideGui) return;
            if (mc.screen instanceof MiningProfitHudEditScreen) return;
            ModConfig cfg = ModConfig.getInstance();
            if (hasAnyData() || CrystalHollowsTracker.isInCrystalHollows()) {
                renderHudAt(graphics, mc, cfg.nucleusProfitHudX, cfg.nucleusProfitHudY, cfg.nucleusProfitHudScale, false);
            }
        });
    }

    public static void onChatComponent(Component message) {
        NucleusProfitFeature inst = instance;
        if (inst == null || !WhitelistManager.isAuthorized() || !inst.isEnabled() || message == null) return;
        String clean = MiningItemTable.stripFormatting(message.getString());
        inst.handleChat(message, clean);
    }

    @Override
    public void clientTick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (!isEnabled() || mc.player == null || mc.level == null) {
            pauseAndResetTransient(mc != null ? mc.level : null, "");
            return;
        }

        String curServer = CrystalHollowsTracker.getCurrentServerId();
        if (mc.level != lastLevel || !Objects.equals(curServer, lastServerId)) {
            pauseAndResetTransient(mc.level, curServer);
        }

        boolean inCh = CrystalHollowsTracker.isInCrystalHollows();
        if (!inCh) {
            if (unCheckedBooks > 0 && !pendingLoot.isEmpty()) {
                commitPendingLoot();
            }
            timerActive = false;
            pausedByAfk = false;
            lastPosValid = false;
            crystalFoundHeaderAtMs = 0L;
            inLootLoop = false;
            pendingLoot.clear();
            unCheckedBooks = 0;
            ProfitDataStore.getInstance().tickAutoSave(now);
            return;
        }

        // Coordinate-only movement tracking for 60s AFK auto-pause
        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        if (!lastPosValid) {
            lastPosValid = true;
            lastX = px;
            lastY = py;
            lastZ = pz;
            lastMoveMs = now;
        } else {
            double dx = px - lastX;
            double dy = py - lastY;
            double dz = pz - lastZ;
            if (dx * dx + dy * dy + dz * dz > 1.0e-4) {
                lastX = px;
                lastY = py;
                lastZ = pz;
                lastMoveMs = now;
                if (!timerActive && pausedByAfk) {
                    pausedByAfk = false;
                    timerActive = true;
                }
            } else if (timerActive && (now - lastMoveMs) >= AFK_TIMEOUT_MS) {
                timerActive = false;
                pausedByAfk = true;
            }
        }

        if (now - lastSecondTickMs >= 1_000L) {
            lastSecondTickMs = now;
            if (timerActive) {
                ProfitDataStore.getInstance().incrementActiveSecond(ProfitDataStore.TRACKER_NUCLEUS);
            }
        }
        ProfitDataStore.getInstance().tickAutoSave(now);

        if (unCheckedBooks > 0) {
            checkPendingInventoryBooks(mc, now);
        }

        if (now - lastBzFetchMs >= BZ_REFRESH_INTERVAL_MS
                || (bzPrices.isEmpty() && now - lastBzFetchMs >= 5_000L)) {
            lastBzFetchMs = now;
            refreshBazaarPricesAsync();
        }
        if (now - lastAhFetchMs >= AH_REFRESH_INTERVAL_MS
                || (ahLowestBins.isEmpty() && now - lastAhFetchMs >= 5_000L)) {
            lastAhFetchMs = now;
            refreshAhLowestBinsAsync();
        }
    }

    private void pauseAndResetTransient(ClientLevel level, String serverId) {
        if (unCheckedBooks > 0 && !pendingLoot.isEmpty()) {
            commitPendingLoot();
        }
        lastLevel = level;
        lastServerId = serverId != null ? serverId : "";
        timerActive = false;
        pausedByAfk = false;
        crystalFoundHeaderAtMs = 0L;
        lastPosValid = false;
        inLootLoop = false;
        unCheckedBooks = 0;
        pendingLoot.clear();
        prevBookSlots.clear();
    }

    private void handleChat(Component message, String clean) {
        if (clean == null || clean.isBlank() || !CrystalHollowsTracker.isInCrystalHollows()) {
            return;
        }
        long now = System.currentTimeMillis();
        String trimmed = clean.trim();

        // 1. Detect Crystal Found banner + Crystal Name to start/resume timing
        if (CRYSTAL_FOUND_PATTERN.matcher(trimmed).find()) {
            crystalFoundHeaderAtMs = now;
            if (containsAnyCrystalName(trimmed.toUpperCase(Locale.ROOT))) {
                activateTimer(now);
                return;
            }
            return;
        }
        if (crystalFoundHeaderAtMs > 0L && (now - crystalFoundHeaderAtMs) <= CRYSTAL_HEADER_WINDOW_MS) {
            if (CRYSTAL_NAMES.contains(trimmed.toUpperCase(Locale.ROOT))) {
                crystalFoundHeaderAtMs = 0L;
                activateTimer(now);
                return;
            }
        }

        // 2. CRYSTAL NUCLEUS LOOT BUNDLE chat block (modeled after SkyHanni CrystalNucleusApi)
        if (trimmed.startsWith("CRYSTAL NUCLEUS LOOT BUNDLE")) {
            if (unCheckedBooks > 0 && !pendingLoot.isEmpty()) {
                commitPendingLoot();
            }
            inLootLoop = true;
            unCheckedBooks = 0;
            pendingLoot.clear();
            snapshotCurrentBookSlots();
            return;
        }

        if (inLootLoop) {
            if (BUNDLE_END_PATTERN.matcher(clean).matches()) {
                inLootLoop = false;
                if (unCheckedBooks <= 0) {
                    commitPendingLoot();
                } else {
                    unCheckedBookDeadlineMs = now + UNCHECKED_BOOK_TIMEOUT_MS;
                }
                return;
            }
            parseBundleLootLine(clean);
            return;
        }

        // 3. Bal Pet drop (chat tier detection, priced via AH Lowest BIN)
        Matcher balMatcher = BAL_OBTAINED_PATTERN.matcher(trimmed);
        if (balMatcher.matches()) {
            String player = balMatcher.group(1);
            Minecraft mc = Minecraft.getInstance();
            String localName = mc != null && mc.getUser() != null ? mc.getUser().getName() : "";
            if (!localName.isEmpty() && localName.equalsIgnoreCase(player)) {
                String balKey = detectBalRarityKey(message);
                if (balKey != null) {
                    ProfitDataStore.getInstance().addItem(ProfitDataStore.TRACKER_NUCLEUS, balKey, 1L);
                }
            }
            return;
        }

        // 4. Bal Shard gain
        int shardAmt = matchBalShardAmount(trimmed, now);
        if (shardAmt > 0) {
            ProfitDataStore.getInstance().addItem(ProfitDataStore.TRACKER_NUCLEUS, "SHARD_BAL", shardAmt);
        }
    }

    private void activateTimer(long now) {
        timerActive = true;
        pausedByAfk = false;
        lastMoveMs = now;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            lastPosValid = true;
            lastX = mc.player.getX();
            lastY = mc.player.getY();
            lastZ = mc.player.getZ();
        }
    }

    private static boolean containsAnyCrystalName(String upper) {
        for (String name : CRYSTAL_NAMES) {
            if (upper.contains(name)) return true;
        }
        return false;
    }

    private void parseBundleLootLine(String rawClean) {
        if (!rawClean.startsWith("    ")) {
            return;
        }
        String trimmed = rawClean.substring(4).trim();
        if (trimmed.isEmpty() || "REWARDS".equalsIgnoreCase(trimmed) || trimmed.contains(" Powder")) {
            return;
        }

        int amount = 1;
        String itemName = trimmed;

        Matcher leading = LEADING_AMOUNT_PATTERN.matcher(trimmed);
        Matcher trailing = TRAILING_AMOUNT_PATTERN.matcher(trimmed);
        if (trailing.matches()) {
            itemName = trailing.group(1).trim();
            amount = parseAmountInt(trailing.group(2), 1);
        } else if (leading.matches()) {
            int candidate = parseAmountInt(leading.group(1), -1);
            if (candidate > 0) {
                amount = candidate;
                itemName = leading.group(2).trim();
            }
        }

        if (itemName.isEmpty() || amount <= 0 || itemName.contains(" Powder")) {
            return;
        }

        String normalized = MiningItemTable.cleanItemName(itemName);
        if ("Enchanted Book".equalsIgnoreCase(normalized)) {
            unCheckedBooks += amount;
            return;
        }
        if (normalized.equalsIgnoreCase("Fortune IV")
                || normalized.equalsIgnoreCase("Enchanted Book (Fortune IV)")) {
            pendingLoot.merge("ENCHANTMENT_FORTUNE_4", amount, Integer::sum);
            return;
        }
        if (normalized.equalsIgnoreCase("Lapidary I")
                || normalized.equalsIgnoreCase("Enchanted Book (Lapidary I)")) {
            pendingLoot.merge("ENCHANTMENT_LAPIDARY_1", amount, Integer::sum);
            return;
        }

        String key = resolveNucleusItemKey(normalized);
        if (key != null && !key.isEmpty()) {
            pendingLoot.merge(key, amount, Integer::sum);
        }
    }

    private static int parseAmountInt(String str, int fallback) {
        try {
            return Integer.parseInt(str.replace(",", ""));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String resolveNucleusItemKey(String cleanName) {
        MiningItemTable.Entry tableEntry = MiningItemTable.get(cleanName);
        if (tableEntry != null) {
            return tableEntry.key();
        }
        String noLeadingGlyph = cleanName.replaceFirst("^[^\\p{L}\\p{N}\\[(]+", "").trim();
        if (!noLeadingGlyph.equals(cleanName)) {
            tableEntry = MiningItemTable.get(noLeadingGlyph);
            if (tableEntry != null) {
                return tableEntry.key();
            }
        }

        // Dynamic catalog fallback via ItemDatabase for any uncommon bundle item
        List<SkyblockItem> matches = ItemDatabase.fuzzySearch(noLeadingGlyph, false);
        for (SkyblockItem item : matches) {
            String strippedName = MiningItemTable.stripFormatting(item.getName()).trim();
            if (strippedName.equalsIgnoreCase(noLeadingGlyph)) {
                dynamicDisplayNames.putIfAbsent(item.getId(), strippedName);
                return item.getId();
            }
        }
        return null;
    }

    private void snapshotCurrentBookSlots() {
        prevBookSlots.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && "Enchanted Book".equals(MiningItemTable.cleanItemName(st.getHoverName().getString()))) {
                prevBookSlots.put(i, st.copy());
            }
        }
    }

    private void checkPendingInventoryBooks(Minecraft mc, long now) {
        if (mc.player != null) {
            Inventory inv = mc.player.getInventory();
            for (int i = 0; i < 36 && unCheckedBooks > 0; i++) {
                ItemStack cur = inv.getItem(i);
                if (cur.isEmpty()) continue;
                String cleanHover = MiningItemTable.cleanItemName(cur.getHoverName().getString());
                if (!"Enchanted Book".equals(cleanHover)) continue;
                ItemStack prev = prevBookSlots.get(i);
                if (prev == null || cur.getCount() > prev.getCount()) {
                    String bookKey = MiningItemTable.resolveNucleusBookKeyFromStack(cur);
                    if (bookKey != null) {
                        pendingLoot.merge(bookKey, 1, Integer::sum);
                    }
                    prevBookSlots.put(i, cur.copy());
                    unCheckedBooks--;
                }
            }
        }
        if (unCheckedBooks <= 0 || now >= unCheckedBookDeadlineMs) {
            commitPendingLoot();
        }
    }

    private void commitPendingLoot() {
        unCheckedBooks = 0;
        prevBookSlots.clear();
        ProfitDataStore store = ProfitDataStore.getInstance();
        store.incrementRunsCompleted(ProfitDataStore.TRACKER_NUCLEUS);
        for (Map.Entry<String, Integer> e : pendingLoot.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                store.addItem(ProfitDataStore.TRACKER_NUCLEUS, e.getKey(), e.getValue());
            }
        }
        pendingLoot.clear();
    }

    private static String detectBalRarityKey(Component message) {
        TextColor gold = TextColor.fromLegacyFormat(ChatFormatting.GOLD);
        TextColor purple = TextColor.fromLegacyFormat(ChatFormatting.DARK_PURPLE);
        TextColor balColor = findBalWordColor(message);
        if (balColor != null) {
            int rgb = balColor.getValue();
            if (gold != null && rgb == gold.getValue()) return "BAL;4";
            if (purple != null && rgb == purple.getValue()) return "BAL;3";
        }
        String raw = message.getString();
        if (raw.contains("\u00a76Bal")) return "BAL;4";
        if (raw.contains("\u00a75Bal")) return "BAL;3";
        return null;
    }

    private static TextColor findBalWordColor(Component comp) {
        if (comp == null) return null;
        String text = MiningItemTable.stripFormatting(comp.getString()).trim();
        if ("Bal".equals(text) || text.endsWith("] Bal")) {
            if (comp.getStyle().getColor() != null) {
                return comp.getStyle().getColor();
            }
        }
        for (Component sib : comp.getSiblings()) {
            TextColor c = findBalWordColor(sib);
            if (c != null) return c;
        }
        return null;
    }

    private int matchBalShardAmount(String trimmed, long now) {
        Matcher caught = BAL_SHARD_CAUGHT_PATTERN.matcher(trimmed);
        if (caught.matches()) {
            lastPrimaryBalShardMs = now;
            return parseOptionalInt(caught.group(1), 1);
        }
        Matcher charm = BAL_SHARD_CHARM_PATTERN.matcher(trimmed);
        if (charm.matches()) {
            lastPrimaryBalShardMs = now;
            return parseOptionalInt(charm.group(1), 1);
        }
        Matcher lootshare = BAL_SHARD_LOOTSHARE_PATTERN.matcher(trimmed);
        if (lootshare.matches()) {
            lastPrimaryBalShardMs = now;
            return parseOptionalInt(lootshare.group(1), 1);
        }
        Matcher huntingBox = BAL_SHARD_HUNTING_BOX_PATTERN.matcher(trimmed);
        if (huntingBox.matches()) {
            if (now - lastPrimaryBalShardMs <= BAL_SHARD_DEDUPE_MS) {
                return 0;
            }
            return parseOptionalInt(huntingBox.group(1), 1);
        }
        return 0;
    }

    private static int parseOptionalInt(String group, int fallback) {
        if (group == null || group.isEmpty()) return fallback;
        try {
            return Math.max(1, Integer.parseInt(group));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void refreshBazaarPricesAsync() {
        Set<String> keys = new HashSet<>(MiningItemTable.getAllKeys());
        keys.addAll(NUCLEUS_COST_KEYS);
        keys.add("SHARD_BAL");
        keys.add("ENCHANTMENT_FORTUNE_4");
        keys.add("ENCHANTMENT_LAPIDARY_1");
        keys.addAll(dynamicDisplayNames.keySet());
        HypixelBazaarApi.getBatchPricesAsync(new ArrayList<>(keys)).thenAccept(map -> {
            if (map == null || map.isEmpty()) return;
            for (Map.Entry<String, double[]> e : map.entrySet()) {
                double[] fresh = e.getValue();
                if (fresh == null || fresh.length < 2) continue;
                double[] prev = bzPrices.get(e.getKey());
                double sellOffer = fresh[0] > 0.0 ? fresh[0] : (prev != null ? prev[0] : 0.0);
                double buyOrder = fresh[1] > 0.0 ? fresh[1] : (prev != null ? prev[1] : 0.0);
                bzPrices.put(e.getKey(), new double[]{sellOffer, buyOrder});
            }
        });
    }

    private void refreshAhLowestBinsAsync() {
        CoflnetAhApi.getNucleusAhLowestBinsAsync(new ArrayList<>(AH_LBIN_KEYS)).thenAccept(map -> {
            if (map == null || map.isEmpty()) return;
            for (Map.Entry<String, Double> e : map.entrySet()) {
                if (e.getValue() != null && e.getValue() > 0.0) {
                    ahLowestBins.put(e.getKey(), e.getValue());
                }
            }
        });
    }

    public double getUnitPrice(String key, String mode) {
        if (key == null) return 0.0;
        if (AH_LBIN_KEYS.contains(key)) {
            Double lbin = ahLowestBins.get(key);
            return lbin != null ? lbin : 0.0;
        }
        if ("SHARD_BAL".equals(key)) {
            double[] prices = bzPrices.get(key);
            if (prices != null) {
                if ("BZ_SELL_OFFER".equalsIgnoreCase(mode)) {
                    return prices[0] > 0.0 ? prices[0] : prices[1];
                }
                // BZ_BUY_ORDER and NPC both use Insta Sell (prices[1])
                return prices[1] > 0.0 ? prices[1] : prices[0];
            }
            return 0.0;
        }

        MiningItemTable.Entry entry = MiningItemTable.getByKey(key);
        double npcPrice = entry != null ? entry.npcPrice() : dynamicNpcPrices.getOrDefault(key, 0.0);
        if ("NPC".equalsIgnoreCase(mode)) {
            return npcPrice;
        }
        double[] prices = bzPrices.get(key);
        if (prices != null) {
            double val = "BZ_BUY_ORDER".equalsIgnoreCase(mode) ? prices[1] : prices[0];
            if (val > 0.0) {
                return val;
            }
        }
        return npcPrice;
    }

    public double getTotalGrossCoins(String mode) {
        String displayMode = ModConfig.getInstance().nucleusProfitDisplayMode;
        Map<String, Long> items = ProfitDataStore.getInstance().getItemsSnapshot(
                ProfitDataStore.TRACKER_NUCLEUS, displayMode);
        double total = 0.0;
        for (Map.Entry<String, Long> e : items.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0L) {
                total += e.getValue() * getUnitPrice(e.getKey(), mode);
            }
        }
        return total;
    }

    public double getTotalCosts(String mode, long runsCompleted) {
        if (runsCompleted <= 0L) return 0.0;
        double perRun = 0.0;
        for (String costKey : NUCLEUS_COST_KEYS) {
            perRun += getUnitPrice(costKey, mode);
        }
        return perRun * runsCompleted;
    }

    public double getNetProfit(String mode) {
        String displayMode = ModConfig.getInstance().nucleusProfitDisplayMode;
        long runs = ProfitDataStore.getInstance().getRunsCompleted(ProfitDataStore.TRACKER_NUCLEUS, displayMode);
        return getTotalGrossCoins(mode) - getTotalCosts(mode, runs);
    }

    public double getNetCoinsPerHour(String mode) {
        String displayMode = ModConfig.getInstance().nucleusProfitDisplayMode;
        long secs = ProfitDataStore.getInstance().getActiveSeconds(ProfitDataStore.TRACKER_NUCLEUS, displayMode);
        if (secs < 1L) return 0.0;
        return getNetProfit(mode) / (secs / 3600.0);
    }

    public boolean hasAnyData() {
        return ProfitDataStore.getInstance().hasAnyData(
                ProfitDataStore.TRACKER_NUCLEUS, ModConfig.getInstance().nucleusProfitDisplayMode);
    }

    public void resetSession() {
        ProfitDataStore.getInstance().reset(
                ProfitDataStore.TRACKER_NUCLEUS, ModConfig.getInstance().nucleusProfitDisplayMode);
        timerActive = false;
        pausedByAfk = false;
        crystalFoundHeaderAtMs = 0L;
        inLootLoop = false;
        pendingLoot.clear();
        unCheckedBooks = 0;
    }

    public static String cyclePriceMode() {
        ModConfig cfg = ModConfig.getInstance();
        String cur = cfg.nucleusProfitPriceMode != null ? cfg.nucleusProfitPriceMode : "BZ_SELL_OFFER";
        String next = switch (cur.toUpperCase(Locale.ROOT)) {
            case "NPC" -> "BZ_SELL_OFFER";
            case "BZ_SELL_OFFER" -> "BZ_BUY_ORDER";
            default -> "NPC";
        };
        cfg.nucleusProfitPriceMode = next;
        ModConfig.save();
        if (instance != null) {
            instance.refreshBazaarPricesAsync();
            instance.refreshAhLowestBinsAsync();
        }
        return next;
    }

    public static String cycleDisplayMode() {
        ModConfig cfg = ModConfig.getInstance();
        cfg.nucleusProfitDisplayMode = ProfitDataStore.MODE_SESSION.equalsIgnoreCase(cfg.nucleusProfitDisplayMode)
                ? ProfitDataStore.MODE_PERSISTENT
                : ProfitDataStore.MODE_SESSION;
        ModConfig.save();
        return cfg.nucleusProfitDisplayMode;
    }

    private List<DisplayRow> buildDisplayRows(String mode, boolean previewMode) {
        String displayMode = ModConfig.getInstance().nucleusProfitDisplayMode;
        Map<String, Long> items = ProfitDataStore.getInstance().getItemsSnapshot(
                ProfitDataStore.TRACKER_NUCLEUS, displayMode);
        List<DisplayRow> rows = new ArrayList<>();
        for (Map.Entry<String, Long> e : items.entrySet()) {
            long amt = e.getValue() != null ? e.getValue() : 0L;
            if (amt <= 0L) continue;
            String key = e.getKey();
            MiningItemTable.Entry meta = MiningItemTable.getByKey(key);
            String rawName = meta != null ? meta.name() : dynamicDisplayNames.getOrDefault(key, key);
            String name = MiningItemTable.getLocalizedName(key, rawName);
            double coins = amt * getUnitPrice(key, mode);
            int color = MiningItemTable.getColor(key);
            rows.add(new DisplayRow(key, name, amt, coins, color));
        }

        if (rows.isEmpty() && previewMode) {
            rows.add(new DisplayRow("JADERALD",
                    MiningItemTable.getLocalizedName("JADERALD", "Jaderald"),
                    1.0, getUnitPrice("JADERALD", mode), MiningItemTable.getColor("JADERALD")));
            rows.add(new DisplayRow("BAL;4",
                    MiningItemTable.getLocalizedName("BAL;4", "Legendary Bal Pet"),
                    1.0, getUnitPrice("BAL;4", mode), MiningItemTable.getColor("BAL;4")));
            rows.add(new DisplayRow("SHARD_BAL",
                    MiningItemTable.getLocalizedName("SHARD_BAL", "Bal Shard"),
                    2.0, 2.0 * getUnitPrice("SHARD_BAL", mode), MiningItemTable.getColor("SHARD_BAL")));
        }

        rows.sort(Comparator.comparingDouble(DisplayRow::coins).reversed());
        return rows;
    }

    public int[] getHudBounds(Minecraft mc, float scale, boolean previewMode) {
        ModConfig cfg = ModConfig.getInstance();
        String mode = cfg.nucleusProfitPriceMode != null ? cfg.nucleusProfitPriceMode : "BZ_SELL_OFFER";
        List<DisplayRow> rows = buildDisplayRows(mode, previewMode);
        int visibleRows = rows.size() > MAX_VISIBLE_ROWS ? (MAX_VISIBLE_ROWS + 1) : Math.max(1, rows.size());
        int footerLines = 4; // Costs, Runs + Runs/hr, Net + Coins/hr, Time + DisplayMode
        int rowH = 12;
        int baseW = 224;
        int baseH = 29 + visibleRows * rowH + footerLines * 11;
        return new int[]{Math.round(baseW * scale), Math.round(baseH * scale)};
    }

    public void renderHudAt(GuiGraphicsExtractor graphics, Minecraft mc, int x, int y, float scale, boolean previewMode) {
        ModConfig cfg = ModConfig.getInstance();
        String mode = cfg.nucleusProfitPriceMode != null ? cfg.nucleusProfitPriceMode : "BZ_SELL_OFFER";

        List<DisplayRow> allRows = buildDisplayRows(mode, previewMode);
        int overflowCount = Math.max(0, allRows.size() - MAX_VISIBLE_ROWS);
        List<DisplayRow> visibleRows = overflowCount > 0 ? allRows.subList(0, MAX_VISIBLE_ROWS) : allRows;

        int rowH = 12;
        int rowLines = visibleRows.isEmpty() ? 1 : visibleRows.size() + (overflowCount > 0 ? 1 : 0);
        int footerLines = 4;
        int baseW = 224;
        int baseH = 29 + rowLines * rowH + footerLines * 11;

        graphics.pose().pushMatrix();
        graphics.pose().translate((float) x, (float) y);
        graphics.pose().scale(scale, scale);

        graphics.fill(0, 0, baseW, baseH, 0xB8101218);
        graphics.outline(0, 0, baseW, baseH, 0xFF2D3646);

        graphics.text(mc.font, ModLang.tr("dttdrtaddons.hud.nucleus_profit.title"), 6, 5, MiningItemTable.LEGACY_GOLD);
        String badge = "[" + MiningProfitFeature.getModeDisplayName(mode) + "]";
        int badgeW = mc.font.width(badge);
        graphics.text(mc.font, badge, baseW - badgeW - 6, 5, MiningItemTable.LEGACY_AQUA);
        graphics.fill(5, 16, baseW - 5, 17, 0x663A4659);

        int curY = 20;
        if (visibleRows.isEmpty()) {
            graphics.text(mc.font, ModLang.tr("dttdrtaddons.hud.nucleus_profit.empty"), 6, curY, MiningItemTable.LEGACY_GRAY);
            curY += rowH;
        } else {
            for (DisplayRow row : visibleRows) {
                PackDisablerIconBridge.drawItemIconOrColor(graphics, row.key(), 5, curY - 1, 10, row.color());
                String right = NumberFormatUtils.formatCompact(row.coins());
                int rightW = mc.font.width(right);
                int maxLeftW = baseW - 28 - rightW;
                String left = String.format(Locale.ROOT, "%,dx %s", Math.round(row.amount()), row.name());
                while (mc.font.width(left) > maxLeftW && left.length() > 6) {
                    left = left.substring(0, left.length() - 4) + "...";
                }
                graphics.text(mc.font, left, 18, curY, row.color());
                graphics.text(mc.font, right, baseW - rightW - 6, curY, MiningItemTable.LEGACY_YELLOW);
                curY += rowH;
            }
            if (overflowCount > 0) {
                graphics.text(mc.font, ModLang.tr("dttdrtaddons.hud.mining_profit.more_items", overflowCount),
                        18, curY, MiningItemTable.LEGACY_GRAY);
                curY += rowH;
            }
        }

        graphics.fill(5, curY + 1, baseW - 5, curY + 2, 0x663A4659);
        curY += 5;

        String displayMode = cfg.nucleusProfitDisplayMode;
        long runs = (previewMode && !hasAnyData()) ? 3L
                : ProfitDataStore.getInstance().getRunsCompleted(ProfitDataStore.TRACKER_NUCLEUS, displayMode);
        long secs = (previewMode && !hasAnyData()) ? 1800L
                : ProfitDataStore.getInstance().getActiveSeconds(ProfitDataStore.TRACKER_NUCLEUS, displayMode);

        double grossCoins = (previewMode && !hasAnyData())
                ? allRows.stream().mapToDouble(DisplayRow::coins).sum()
                : getTotalGrossCoins(mode);
        double costs = getTotalCosts(mode, runs);
        double netCoins = grossCoins - costs;
        double coinsPerHour = secs >= 1L ? netCoins / (secs / 3600.0) : 0.0;
        double runsPerHour = (secs >= 1L && runs > 0L) ? runs / (secs / 3600.0) : 0.0;

        String costText = ModLang.tr("dttdrtaddons.hud.mining_profit.costs",
                "-" + NumberFormatUtils.formatCompact(costs));
        String grossText = ModLang.tr("dttdrtaddons.hud.mining_profit.gross",
                NumberFormatUtils.formatCompact(grossCoins));
        graphics.text(mc.font, costText, 6, curY, MiningItemTable.LEGACY_RED);
        graphics.text(mc.font, grossText, baseW - mc.font.width(grossText) - 6, curY, MiningItemTable.LEGACY_GRAY);
        curY += 11;

        String runsText = ModLang.tr("dttdrtaddons.hud.nucleus_profit.runs", runs);
        String runsHrText = String.format(Locale.ROOT, "%.1f", runsPerHour)
                + ModLang.tr("dttdrtaddons.hud.nucleus_profit.per_hr");
        graphics.text(mc.font, runsText, 6, curY, MiningItemTable.LEGACY_WHITE);
        graphics.text(mc.font, runsHrText, baseW - mc.font.width(runsHrText) - 6, curY, MiningItemTable.LEGACY_BLUE);
        curY += 11;

        String netSign = netCoins < 0 ? "-" : "";
        String netLabel = ModLang.tr("dttdrtaddons.hud.mining_profit.net",
                netSign + NumberFormatUtils.formatCompact(Math.abs(netCoins)));
        String rateSign = coinsPerHour < 0 ? "-" : "";
        String rateText = rateSign + NumberFormatUtils.formatCompact(Math.abs(coinsPerHour))
                + ModLang.tr("dttdrtaddons.hud.mining_profit.per_hr");
        graphics.text(mc.font, netLabel, 6, curY, netCoins >= 0 ? MiningItemTable.LEGACY_GOLD : MiningItemTable.LEGACY_RED);
        graphics.text(mc.font, rateText, baseW - mc.font.width(rateText) - 6, curY,
                coinsPerHour >= 0 ? MiningItemTable.LEGACY_GREEN : MiningItemTable.LEGACY_RED);
        curY += 11;

        String timeStr = String.format("%02d:%02d", secs / 60, secs % 60);
        String statusSuffix = (!timerActive && secs > 0) ? ModLang.tr("dttdrtaddons.hud.mining_profit.paused") : "";
        String leftMeta = ModLang.tr("dttdrtaddons.hud.mining_profit.time", timeStr, statusSuffix);
        graphics.text(mc.font, leftMeta, 6, curY, (!timerActive && secs > 0) ? MiningItemTable.LEGACY_RED : MiningItemTable.LEGACY_GRAY);
        String dispTag = "[" + MiningProfitFeature.getDisplayModeName(displayMode) + "]";
        graphics.text(mc.font, dispTag, baseW - mc.font.width(dispTag) - 6, curY, MiningItemTable.LEGACY_GRAY);

        graphics.pose().popMatrix();
    }
}
