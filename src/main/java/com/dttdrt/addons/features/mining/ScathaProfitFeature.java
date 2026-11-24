package com.dttdrt.addons.features.mining;

import com.dttdrt.addons.api.CoflnetAhApi;
import com.dttdrt.addons.api.HypixelBazaarApi;
import com.dttdrt.addons.core.auth.WhitelistManager;
import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.core.feature.Feature;
import com.dttdrt.addons.core.lang.ModLang;
import com.dttdrt.addons.features.mining.profit.CaptureBuffer;
import com.dttdrt.addons.features.mining.profit.MiningItemTable;
import com.dttdrt.addons.features.mining.profit.ProfitDataStore;
import com.dttdrt.addons.texture.PackDisablerIconBridge;
import com.dttdrt.addons.util.NumberFormatUtils;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure client-side Scatha Mining Profit tracker modeled after Scatha-Pro's Crystal Hollows
 * worm/Scatha lifecycle and 30s spawn cooldown, combined with actual inventory & Sack tracking,
 * Fine-converted gemstones, AH Lowest BIN Scatha Pet pricing, Bazaar Scatha Shard pricing,
 * and SkyHanni-style Persistent/Session dual-bucket persistence.
 */
public class ScathaProfitFeature implements Feature {

    private static ScathaProfitFeature instance;

    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("dttdrtaddons", "scatha_profit_hud");

    // Adapted directly from Scatha-Pro Constants.wormSpawnCooldown (30_000 ms) & SoundParser pitch
    private static final long WORM_SPAWN_COOLDOWN_MS = 30_000L;
    private static final float WORM_PRE_SPAWN_PITCH = 2.0952382f;
    private static final long PRE_SPAWN_SOUND_DEDUPE_MS = 10_000L;

    private static final long SESSION_IDLE_TIMEOUT_MS = 90_000L;
    private static final int TICKS_INVENTORY_SCAN = 4;
    private static final int TICKS_ENTITY_SCAN = 5;
    private static final long INVENTORY_STABLE_MS = 3_500L;
    private static final long PENDING_INCREASE_MS = 1_500L;
    private static final long REMOVAL_CREDIT_MS = 12_000L;
    private static final long SCREEN_CLOSE_GRACE_MS = 1_500L;
    private static final long SACK_SCREEN_COOLDOWN_MS = 5_000L;
    private static final long BAZAAR_COOLDOWN_MS = 8_000L;
    private static final long AUCTION_COOLDOWN_MS = 8_000L;
    private static final long DEPOSIT_TTL_MS = 35_000L;
    private static final long SACK_DEFER_MS = 600L;
    private static final long MAX_SACKS_DELAY_MS = 35_000L;
    private static final long BZ_REFRESH_INTERVAL_MS = 30_000L;
    private static final long AH_REFRESH_INTERVAL_MS = 60_000L;
    private static final long PET_CHAT_DEDUPE_MS = 2_500L;

    private static final int MAX_VISIBLE_ROWS = 12;

    // Enchanted vanilla ores + Enchanted Hard Stone (via Sacks) + Worm/Scatha drops
    private static final Set<String> SACK_TRACKED_KEYS = Set.of(
            "ENCHANTED_HARD_STONE",
            "ENCHANTED_COAL",
            "ENCHANTED_IRON",
            "ENCHANTED_GOLD",
            "ENCHANTED_LAPIS_LAZULI",
            "ENCHANTED_EMERALD",
            "ENCHANTED_DIAMOND",
            "ENCHANTED_REDSTONE",
            "ENCHANTED_QUARTZ",
            "ENCHANTED_OBSIDIAN",
            "ENCHANTED_COBBLESTONE",
            "WORM_MEMBRANE",
            "DWARVEN_OS_BLOCK_BRAN"
    );

    private static final Set<String> INV_DIRECT_DROP_KEYS = Set.of(
            "WORM_MEMBRANE",
            "DWARVEN_OS_BLOCK_BRAN",
            "SCATHA;2",
            "SCATHA;3",
            "SCATHA;4"
    );

    private static final Pattern SACKS_ADDED_LINE = Pattern.compile("(\\+[\\d,]+)\\s+(.+?)\\s+\\((.+?)\\)");
    private static final Pattern SACKS_REMOVED_LINE = Pattern.compile("(-[\\d,]+)\\s+(.+?)\\s+\\((.+?)\\)");
    private static final Pattern CHARM_SHARD_PATTERN = Pattern.compile(
            "^CHARM! You charmed the (Scatha|Stoneworm) and received (\\d+) (?:Scatha|Stoneworm) Shards?!$",
            Pattern.CASE_INSENSITIVE);
    private static final String STASH_TO_SACKS_MSG = "You moved all items from your stash to your sacks!";
    private static final String WORM_APPROACHING_MSG = "You hear the sound of something approaching...";

    private static final List<String> CONVERSION_SCREEN_NAMES = List.of(
            "crafting", "anvil", "enchant", "brewing", "forge",
            "\u5de5\u4f5c\u53f0", "\u5408\u6210", "\u94c1\u7827", "\u9644\u9b54", "\u917f\u9020", "\u953b\u9020"
    );
    private static final List<String> MENU_SCREEN_NAMES = List.of(
            "skyblock menu", "skyblock \u83dc\u5355", "skyblock\u9009\u5355",
            "fast travel", "skyblock leveling", "collections"
    );

    private record Deposit(int amount, long atMs) {}
    private record SackPickup(String itemName, int amount) {}
    private record DeferredSackAdd(List<SackPickup> items, long dueAtMs) {}
    private record DisplayRow(String key, String name, double amount, double coins, int color) {}

    private final CaptureBuffer buffer = new CaptureBuffer(PENDING_INCREASE_MS, REMOVAL_CREDIT_MS);
    private final Map<String, double[]> bzPrices = new ConcurrentHashMap<>();
    private final Map<String, Double> ahLowestBins = new ConcurrentHashMap<>();
    private final Map<String, Deposit> pendingDeposits = new HashMap<>();
    private final List<DeferredSackAdd> deferredSackAdds = new ArrayList<>();
    private final Map<String, Long> recentChatPets = new HashMap<>();
    private final Set<UUID> seenWormEntities = new HashSet<>();

    private ClientLevel lastLevel = null;
    private Map<String, Integer> previousInventory = null;
    private Map<String, Integer> previousContainer = null;
    private boolean inventoryLoaded = false;
    private String lastFingerprint = null;
    private long fingerprintStableSince = 0L;

    private boolean lastScreenOpen = false;
    private boolean sackScreenOpen = false;
    private String lastScreenTitle = null;
    private long lastScreenClosedMs = 0L;
    private long lastSackScreenClosedMs = 0L;
    private long lastBazaarGuiClosedMs = 0L;
    private long lastAuctionGuiClosedMs = 0L;
    private long lastStashToSacksMs = 0L;

    private volatile boolean sessionActive = false;
    private volatile long lastActivityMs = 0L;
    private long lastSecondTickMs = 0L;

    private volatile long wormCooldownStartMs = -1L;
    private volatile long lastPreSpawnSoundMs = -1L;

    private int scanTickCounter = 0;
    private int entityScanTickCounter = 0;
    private long lastBzFetchMs = 0L;
    private long lastAhFetchMs = 0L;

    public ScathaProfitFeature() {
        instance = this;
        MiningItemTable.ensureLoaded();
    }

    public static ScathaProfitFeature getInstance() {
        return instance;
    }

    @Override
    public String getId() {
        return "scatha_profit";
    }

    @Override
    public String getName() {
        return "Scatha Mining Profit";
    }

    @Override
    public String getCategory() {
        return "Mining";
    }

    @Override
    public boolean isEnabled() {
        return ModConfig.getInstance().scathaProfitEnabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        ModConfig.getInstance().scathaProfitEnabled = enabled;
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
                renderHudAt(graphics, mc, cfg.scathaProfitHudX, cfg.scathaProfitHudY, cfg.scathaProfitHudScale, false);
            }
        });
    }

    public static void onSoundPlayed(Identifier soundId, float pitch) {
        ScathaProfitFeature inst = instance;
        if (inst == null || !WhitelistManager.isAuthorized() || !inst.isEnabled()) return;
        if (!CrystalHollowsTracker.isInCrystalHollows() || soundId == null) return;
        if ("minecraft".equals(soundId.getNamespace())
                && "entity.spider.step".equals(soundId.getPath())
                && Math.abs(pitch - WORM_PRE_SPAWN_PITCH) < 0.01f) {
            long now = System.currentTimeMillis();
            if (inst.lastPreSpawnSoundMs < 0L || now - inst.lastPreSpawnSoundMs > PRE_SPAWN_SOUND_DEDUPE_MS) {
                inst.lastPreSpawnSoundMs = now;
                inst.startWormCooldown(true, now);
                inst.markActivity(now);
            }
        }
    }

    public static void onStrongActivitySignal() {
        ScathaProfitFeature inst = instance;
        if (inst == null || !WhitelistManager.isAuthorized() || !inst.isEnabled()) return;
        if (!CrystalHollowsTracker.isInCrystalHollows()) return;
        if (inst.sessionActive || inst.wormCooldownStartMs > 0L) {
            inst.markActivity(System.currentTimeMillis());
        }
    }

    public static void onChatComponent(Component message) {
        ScathaProfitFeature inst = instance;
        if (inst == null || !WhitelistManager.isAuthorized() || !inst.isEnabled() || message == null) return;
        String clean = MiningItemTable.stripFormatting(message.getString());
        inst.handleChat(message, clean);
    }

    private void startWormCooldown(boolean forceRestart, long now) {
        if (!forceRestart && wormCooldownStartMs > 0L && (now - wormCooldownStartMs) < WORM_SPAWN_COOLDOWN_MS) {
            return;
        }
        wormCooldownStartMs = now;
    }

    private void markActivity(long now) {
        sessionActive = true;
        lastActivityMs = now;
    }

    @Override
    public void clientTick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (!isEnabled() || mc.player == null || mc.level == null) {
            pauseAndResetTransient(mc != null ? mc.level : null);
            return;
        }

        if (mc.level != lastLevel) {
            pauseAndResetTransient(mc.level);
        }

        boolean inCh = CrystalHollowsTracker.isInCrystalHollows();
        if (!inCh) {
            sessionActive = false;
            wormCooldownStartMs = -1L;
            seenWormEntities.clear();
            ProfitDataStore.getInstance().tickAutoSave(now);
            return;
        }

        // Update 30s worm cooldown expiry
        if (wormCooldownStartMs > 0L && (now - wormCooldownStartMs) >= WORM_SPAWN_COOLDOWN_MS) {
            wormCooldownStartMs = -1L;
        }

        // Count active seconds only while grinding Scatha in CH and not idle > 90s
        if (now - lastSecondTickMs >= 1_000L) {
            lastSecondTickMs = now;
            if (sessionActive) {
                if (now - lastActivityMs <= SESSION_IDLE_TIMEOUT_MS) {
                    ProfitDataStore.getInstance().incrementActiveSecond(ProfitDataStore.TRACKER_SCATHA);
                } else {
                    sessionActive = false;
                }
            }
        }
        ProfitDataStore.getInstance().tickAutoSave(now);

        trackScreenTransitions(mc, now);
        flushDeferredSackAdds(now);

        if (now - lastBzFetchMs >= BZ_REFRESH_INTERVAL_MS
                || (bzPrices.isEmpty() && now - lastBzFetchMs >= 5_000L)) {
            lastBzFetchMs = now;
            refreshBazaarPricesAsync();
        }
        if (now - lastAhFetchMs >= AH_REFRESH_INTERVAL_MS
                || (ahLowestBins.isEmpty() && now - lastAhFetchMs >= 5_000L)) {
            lastAhFetchMs = now;
            refreshScathaAhPricesAsync();
        }

        entityScanTickCounter++;
        if (entityScanTickCounter >= TICKS_ENTITY_SCAN) {
            entityScanTickCounter = 0;
            scanNearbyWormEntities(mc, now);
        }

        scanTickCounter++;
        if (scanTickCounter >= TICKS_INVENTORY_SCAN) {
            scanTickCounter = 0;
            detectInventoryChanges(mc, now);
        }
    }

    private void pauseAndResetTransient(ClientLevel newLevel) {
        lastLevel = newLevel;
        sessionActive = false;
        lastActivityMs = 0L;
        wormCooldownStartMs = -1L;
        lastPreSpawnSoundMs = -1L;
        seenWormEntities.clear();
        previousInventory = null;
        previousContainer = null;
        inventoryLoaded = false;
        lastFingerprint = null;
        fingerprintStableSince = 0L;
        lastScreenOpen = false;
        sackScreenOpen = false;
        lastScreenTitle = null;
        buffer.clearHoldsAndSkipped();
        synchronized (deferredSackAdds) {
            deferredSackAdds.clear();
        }
        synchronized (pendingDeposits) {
            pendingDeposits.clear();
        }
    }

    private void scanNearbyWormEntities(Minecraft mc, long now) {
        if (mc.level == null || mc.player == null) return;
        AABB box = AABB.ofSize(mc.player.position(), 60.0, 10.0, 60.0);
        List<ArmorStand> stands = mc.level.getEntitiesOfClass(ArmorStand.class, box);
        for (ArmorStand stand : stands) {
            if (!stand.isAlive() || seenWormEntities.contains(stand.getUUID())) continue;
            Component nameComp = stand.getCustomName();
            if (nameComp == null) continue;
            String clean = MiningItemTable.stripFormatting(nameComp.getString());
            if (clean.contains("\u2764") && (clean.contains(" Stoneworm ") || clean.contains(" Scatha "))) {
                seenWormEntities.add(stand.getUUID());
                markActivity(now);
                startWormCooldown(false, now);
            }
        }
    }

    private void handleChat(Component message, String clean) {
        if (clean == null || clean.isBlank() || !CrystalHollowsTracker.isInCrystalHollows()) {
            return;
        }
        long now = System.currentTimeMillis();
        String trimmed = clean.trim();

        if (trimmed.equalsIgnoreCase(STASH_TO_SACKS_MSG)) {
            lastStashToSacksMs = now;
            return;
        }

        if (trimmed.equalsIgnoreCase(WORM_APPROACHING_MSG)) {
            markActivity(now);
            startWormCooldown(false, now);
            return;
        }

        Matcher shardMatcher = CHARM_SHARD_PATTERN.matcher(trimmed);
        if (shardMatcher.matches()) {
            try {
                String mob = shardMatcher.group(1);
                int amt = Integer.parseInt(shardMatcher.group(2));
                if (amt > 0) {
                    String shardKey = "stoneworm".equalsIgnoreCase(mob) ? "SHARD_STONEWORM" : "SHARD_SCATHA";
                    markActivity(now);
                    ProfitDataStore.getInstance().addItem(ProfitDataStore.TRACKER_SCATHA, shardKey, amt);
                }
            } catch (NumberFormatException ignored) {
            }
            return;
        }

        if (trimmed.startsWith("PET DROP! Scatha")) {
            String petKey = detectPetDropTierKey(message, trimmed);
            if (petKey != null) {
                markActivity(now);
                synchronized (recentChatPets) {
                    recentChatPets.put(petKey, now);
                }
                ProfitDataStore.getInstance().addItem(ProfitDataStore.TRACKER_SCATHA, petKey, 1L);
            }
            return;
        }

        if (trimmed.startsWith("[Sacks] +") || trimmed.startsWith("[Sacks] -")) {
            handleSacksChatComponent(message, trimmed, now);
        }
    }

    private static String detectPetDropTierKey(Component message, String plain) {
        TextColor foundColor = findScathaWordColor(message);
        if (foundColor != null) {
            int rgb = foundColor.getValue();
            TextColor blue = TextColor.fromLegacyFormat(ChatFormatting.BLUE);
            TextColor purple = TextColor.fromLegacyFormat(ChatFormatting.DARK_PURPLE);
            TextColor gold = TextColor.fromLegacyFormat(ChatFormatting.GOLD);
            if (blue != null && rgb == blue.getValue()) return "SCATHA;2";
            if (purple != null && rgb == purple.getValue()) return "SCATHA;3";
            if (gold != null && rgb == gold.getValue()) return "SCATHA;4";
        }
        String raw = message.getString();
        if (raw.contains("\u00a76Scatha")) return "SCATHA;4";
        if (raw.contains("\u00a75Scatha")) return "SCATHA;3";
        if (raw.contains("\u00a79Scatha")) return "SCATHA;2";
        if (plain.contains("LEGENDARY")) return "SCATHA;4";
        if (plain.contains("EPIC")) return "SCATHA;3";
        return "SCATHA;2";
    }

    private static TextColor findScathaWordColor(Component comp) {
        if (comp == null) return null;
        String text = MiningItemTable.stripFormatting(comp.getString());
        if (text.contains("Scatha") && comp.getStyle().getColor() != null) {
            return comp.getStyle().getColor();
        }
        for (Component sib : comp.getSiblings()) {
            TextColor c = findScathaWordColor(sib);
            if (c != null) return c;
        }
        return null;
    }

    private void handleSacksChatComponent(Component message, String plain, long now) {
        List<SackPickup> withdrawals = parseSackHoverLines(message, "Removed items:", SACKS_REMOVED_LINE);
        if (!withdrawals.isEmpty()) {
            for (SackPickup w : withdrawals) {
                MiningItemTable.Entry entry = MiningItemTable.get(MiningItemTable.cleanItemName(w.itemName()));
                if (entry == null) continue;
                int explained = buffer.takeSkipped(entry.key(), w.amount());
                int remaining = w.amount() - explained;
                if (remaining > 0) {
                    int cancelled = buffer.cancelHold(entry.key(), remaining);
                    int left = remaining - cancelled;
                    if (left > 0 && isTrackedSackKey(entry.key())) {
                        buffer.addRemoval(entry.key(), left, now);
                        // Also deduct net Sack withdrawals made outside an open Sack GUI when not compacted
                        if (MiningItemTable.resolveCompaction(entry.key()) != null
                                || entry.key().endsWith("_GEM")
                                || SACK_TRACKED_KEYS.contains(entry.key())) {
                            // Keep subtraction conservative: only addRemoval so personal compactor upgrades deduct cleanly
                        }
                    }
                }
            }
        }

        if (plain.startsWith("[Sacks] +")) {
            List<SackPickup> added = parseSackHoverLines(message, "Added items:", SACKS_ADDED_LINE);
            if (!added.isEmpty()) {
                synchronized (deferredSackAdds) {
                    deferredSackAdds.add(new DeferredSackAdd(added, now + SACK_DEFER_MS));
                }
            }
        }
    }

    private static List<SackPickup> parseSackHoverLines(Component message, String sectionMarker, Pattern linePattern) {
        List<SackPickup> items = new ArrayList<>();
        List<Component> parts = new ArrayList<>();
        parts.add(message);
        parts.addAll(message.getSiblings());

        for (Component part : parts) {
            String partStr = MiningItemTable.stripFormatting(part.getString());
            if (!partStr.contains(" item")) continue;
            HoverEvent hover = part.getStyle().getHoverEvent();
            if (!(hover instanceof HoverEvent.ShowText showText)) continue;
            String hoverText = MiningItemTable.stripFormatting(showText.value().getString());
            if (!hoverText.contains(sectionMarker)) continue;

            Matcher m = linePattern.matcher(hoverText);
            while (m.find()) {
                String numStr = m.group(1).replace("+", "").replace("-", "").replace(",", "");
                int amount = 0;
                try {
                    amount = Integer.parseInt(numStr);
                } catch (NumberFormatException ignored) {
                }
                String itemName = MiningItemTable.stripFormatting(m.group(2)).trim();
                if (amount > 0 && !itemName.isEmpty()) {
                    items.add(new SackPickup(itemName, amount));
                }
            }
        }
        return items;
    }

    private void flushDeferredSackAdds(long now) {
        List<DeferredSackAdd> due = null;
        synchronized (deferredSackAdds) {
            if (deferredSackAdds.isEmpty()) return;
            Iterator<DeferredSackAdd> it = deferredSackAdds.iterator();
            while (it.hasNext()) {
                DeferredSackAdd item = it.next();
                if (item.dueAtMs() <= now) {
                    if (due == null) due = new ArrayList<>();
                    due.add(item);
                    it.remove();
                }
            }
        }
        if (due == null) return;
        if (now - lastStashToSacksMs < MAX_SACKS_DELAY_MS) return;
        if (!CrystalHollowsTracker.isInCrystalHollows()) return;

        for (DeferredSackAdd batch : due) {
            for (SackPickup pickup : batch.items()) {
                MiningItemTable.Entry entry = MiningItemTable.get(MiningItemTable.cleanItemName(pickup.itemName()));
                if (entry == null || !isTrackedSackKey(entry.key())) continue;

                int fresh = pickup.amount() - takeManualDeposit(entry.key(), pickup.amount(), now);
                if (fresh <= 0) continue;

                fresh = buffer.takeCredit(entry.key(), fresh, now, ScathaProfitFeature::resolveTrackedCompaction);
                if (fresh <= 0) continue;

                markActivity(now);
                ProfitDataStore.getInstance().addItem(ProfitDataStore.TRACKER_SCATHA, entry.key(), fresh);
            }
        }
    }

    private static boolean isTrackedSackKey(String key) {
        if (key == null) return false;
        return key.endsWith("_GEM") || SACK_TRACKED_KEYS.contains(key);
    }

    private static boolean isTrackedInventoryKey(String key) {
        if (key == null) return false;
        return key.endsWith("_GEM") || SACK_TRACKED_KEYS.contains(key) || INV_DIRECT_DROP_KEYS.contains(key);
    }

    private static CaptureBuffer.Compaction resolveTrackedCompaction(String key) {
        CaptureBuffer.Compaction comp = MiningItemTable.resolveCompaction(key);
        if (comp == null || !isTrackedSackKey(comp.parentKey())) {
            return null;
        }
        return comp;
    }

    private void detectInventoryChanges(Minecraft mc, long now) {
        if (mc.player == null) return;

        if (!inventoryLoaded) {
            String fp = inventoryFingerprint(mc.player.getInventory());
            if (!fp.equals(lastFingerprint)) {
                lastFingerprint = fp;
                fingerprintStableSince = now;
                return;
            }
            if (now - fingerprintStableSince < INVENTORY_STABLE_MS) {
                return;
            }
            previousInventory = collectInventory(mc);
            previousContainer = collectContainer(mc);
            inventoryLoaded = true;
            return;
        }

        Map<String, Integer> currentInv = collectInventory(mc);
        Map<String, Integer> currentCont = collectContainer(mc);
        try {
            if (previousInventory == null) {
                previousInventory = currentInv;
                previousContainer = currentCont;
                return;
            }
            if (isConversionScreen(mc) || isMenuScreen(mc)) {
                previousContainer = currentCont;
                return;
            }

            boolean screenOpen = mc.screen != null;
            boolean inSackNow = screenOpen && isInSacksGui(mc);
            if ((now - lastBazaarGuiClosedMs) < BAZAAR_COOLDOWN_MS || (now - lastAuctionGuiClosedMs) < AUCTION_COOLDOWN_MS) {
                return;
            }

            Set<String> allKeys = new HashSet<>(previousInventory.keySet());
            allKeys.addAll(currentInv.keySet());

            for (String key : allKeys) {
                int prev = previousInventory.getOrDefault(key, 0);
                int curr = currentInv.getOrDefault(key, 0);
                int diff = curr - prev;

                if (diff < 0) {
                    int removed = -diff;
                    if (!screenOpen) {
                        buffer.addRemoval(key, removed, now);
                    } else if (inSackNow) {
                        addManualDeposit(key, removed, now);
                    }
                } else if (diff > 0) {
                    if (screenOpen && inSackNow) {
                        buffer.noteSkipped(key, diff);
                        continue;
                    }
                    if (!screenOpen && (now - lastScreenClosedMs) < SCREEN_CLOSE_GRACE_MS) {
                        continue;
                    }
                    if ((now - lastSackScreenClosedMs) < SACK_SCREEN_COOLDOWN_MS) {
                        continue;
                    }
                    if (screenOpen) {
                        continue;
                    }

                    int contInc = currentCont.getOrDefault(key, 0) - previousContainer.getOrDefault(key, 0);
                    int contDec = contInc < 0 ? -contInc : 0;
                    int pickup = diff - Math.min(diff, contDec);
                    if (pickup <= 0) continue;

                    pickup = buffer.takeCredit(key, pickup, now, ScathaProfitFeature::resolveTrackedCompaction);
                    if (pickup <= 0) continue;

                    onInventoryItemGained(key, pickup, now);
                }
            }

            for (CaptureBuffer.KeyAmount due : buffer.dueHolds(now)) {
                int credited = buffer.takeCredit(due.key(), due.amount(), now, ScathaProfitFeature::resolveTrackedCompaction);
                if (credited > 0) {
                    onInventoryItemGained(due.key(), credited, now);
                }
            }
        } finally {
            previousInventory = currentInv;
            previousContainer = currentCont;
        }
    }

    private void onInventoryItemGained(String key, int amount, long now) {
        if (amount <= 0 || !isTrackedInventoryKey(key)) return;
        if (key.startsWith("SCATHA;")) {
            synchronized (recentChatPets) {
                Long chatAt = recentChatPets.get(key);
                if (chatAt != null && now - chatAt <= PET_CHAT_DEDUPE_MS) {
                    recentChatPets.remove(key);
                    return;
                }
            }
        }
        markActivity(now);
        ProfitDataStore.getInstance().addItem(ProfitDataStore.TRACKER_SCATHA, key, amount);
    }

    private void trackScreenTransitions(Minecraft mc, long now) {
        Screen currentScreen = mc.screen;
        boolean screenOpen = currentScreen != null;
        String title = (currentScreen instanceof AbstractContainerScreen<?> acs)
                ? MiningItemTable.stripFormatting(acs.getTitle().getString())
                : null;

        if (lastScreenOpen && !screenOpen) {
            lastScreenClosedMs = now;
            if (sackScreenOpen) {
                lastSackScreenClosedMs = now;
            }
            if (lastScreenTitle != null) {
                if (isBazaarChestName(lastScreenTitle)) {
                    lastBazaarGuiClosedMs = now;
                } else if (isAuctionChestName(lastScreenTitle)) {
                    lastAuctionGuiClosedMs = now;
                }
            }
            sackScreenOpen = false;
            lastScreenTitle = null;
        } else if (screenOpen) {
            if (isInSacksGui(mc)) {
                sackScreenOpen = true;
            }
            if (title != null && !title.isEmpty()) {
                lastScreenTitle = title;
            }
        }
        lastScreenOpen = screenOpen;
    }

    private void addManualDeposit(String key, int amount, long now) {
        if (amount <= 0) return;
        synchronized (pendingDeposits) {
            Deposit existing = pendingDeposits.get(key);
            int curAmt = (existing != null && now - existing.atMs() <= DEPOSIT_TTL_MS) ? existing.amount() : 0;
            pendingDeposits.put(key, new Deposit(curAmt + amount, now));
        }
    }

    private int takeManualDeposit(String key, int upTo, long now) {
        synchronized (pendingDeposits) {
            Deposit d = pendingDeposits.get(key);
            if (d == null) return 0;
            if (now - d.atMs() > DEPOSIT_TTL_MS) {
                pendingDeposits.remove(key);
                return 0;
            }
            int taken = Math.min(d.amount(), upTo);
            int rem = d.amount() - taken;
            if (rem <= 0) {
                pendingDeposits.remove(key);
            } else {
                pendingDeposits.put(key, new Deposit(rem, d.atMs()));
            }
            return taken;
        }
    }

    private static Map<String, Integer> collectInventory(Minecraft mc) {
        Map<String, Integer> counts = new HashMap<>();
        if (mc.player == null) return counts;
        Inventory inv = mc.player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            addStackCount(counts, inv.getItem(slot));
        }
        if (mc.player.containerMenu != null) {
            addStackCount(counts, mc.player.containerMenu.getCarried());
        }
        return counts;
    }

    private static Map<String, Integer> collectContainer(Minecraft mc) {
        Map<String, Integer> counts = new HashMap<>();
        if (mc.player == null || mc.player.containerMenu == null || mc.screen == null) return counts;
        if (mc.screen instanceof InventoryScreen) return counts;
        Inventory playerInv = mc.player.getInventory();
        for (Slot slot : mc.player.containerMenu.slots) {
            if (slot.container != playerInv && slot.hasItem()) {
                addStackCount(counts, slot.getItem());
            }
        }
        return counts;
    }

    private static void addStackCount(Map<String, Integer> counts, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        String scathaKey = MiningItemTable.resolveScathaPetKey(stack);
        if (scathaKey != null) {
            counts.merge(scathaKey, stack.getCount(), Integer::sum);
            return;
        }
        MiningItemTable.Entry entry = MiningItemTable.resolveStack(stack);
        if (entry != null && !MiningItemTable.COINS_KEY.equals(entry.key())) {
            counts.merge(entry.key(), stack.getCount(), Integer::sum);
        }
    }

    private static String inventoryFingerprint(Inventory inv) {
        StringBuilder sb = new StringBuilder(256);
        for (int slot = 0; slot < 36; slot++) {
            ItemStack st = inv.getItem(slot);
            if (!st.isEmpty()) {
                sb.append(MiningItemTable.stripFormatting(st.getHoverName().getString()))
                        .append('|').append(st.getCount());
            }
            sb.append(';');
        }
        return sb.toString();
    }

    private static String getCurrentChestName(Minecraft mc) {
        if (mc.screen instanceof AbstractContainerScreen<?> acs && !(mc.screen instanceof InventoryScreen)) {
            return MiningItemTable.stripFormatting(acs.getTitle().getString());
        }
        return null;
    }

    private static boolean isInSacksGui(Minecraft mc) {
        String title = getCurrentChestName(mc);
        return title != null && title.toLowerCase(Locale.ROOT).contains("sack");
    }

    private static boolean isBazaarChestName(String name) {
        return name.contains("Bazaar") || name.contains("Order options")
                || name.contains("Instant Buy") || name.contains("\u279C");
    }

    private static boolean isAuctionChestName(String name) {
        return name.contains("Auction") || name.contains("Create Auction")
                || name.contains("Confirm Purchase") || name.contains("Your Bids");
    }

    private static boolean isConversionScreen(Minecraft mc) {
        Screen s = mc.screen;
        if (s == null) return false;
        if (s instanceof CraftingScreen || s instanceof AnvilScreen || s instanceof EnchantmentScreen
                || s instanceof BrewingStandScreen || s instanceof FurnaceScreen) {
            return true;
        }
        String title = getCurrentChestName(mc);
        if (title == null) return false;
        String lower = title.toLowerCase(Locale.ROOT);
        for (String hint : CONVERSION_SCREEN_NAMES) {
            if (lower.contains(hint)) return true;
        }
        return false;
    }

    private static boolean isMenuScreen(Minecraft mc) {
        String title = getCurrentChestName(mc);
        if (title == null) return false;
        String lower = title.toLowerCase(Locale.ROOT);
        for (String hint : MENU_SCREEN_NAMES) {
            if (lower.contains(hint)) return true;
        }
        return false;
    }

    private void refreshBazaarPricesAsync() {
        Set<String> keys = new HashSet<>(MiningItemTable.getAllKeys());
        keys.add("WORM_MEMBRANE");
        keys.add("DWARVEN_OS_BLOCK_BRAN");
        keys.add("SHARD_SCATHA");
        keys.add("SHARD_STONEWORM");
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

    private void refreshScathaAhPricesAsync() {
        CoflnetAhApi.getScathaPetLowestBinsAsync().thenAccept(map -> {
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
        if (key.startsWith("SCATHA;")) {
            Double lbin = ahLowestBins.get(key);
            return lbin != null ? lbin : 0.0;
        }
        if ("SHARD_SCATHA".equals(key) || "SHARD_STONEWORM".equals(key)) {
            double[] prices = bzPrices.get(key);
            if (prices != null) {
                if ("BZ_SELL_OFFER".equalsIgnoreCase(mode)) {
                    return prices[0] > 0.0 ? prices[0] : prices[1];
                }
                // Both BZ_BUY_ORDER and NPC mode use Insta Sell (prices[1])
                return prices[1] > 0.0 ? prices[1] : prices[0];
            }
            return 0.0;
        }

        MiningItemTable.Entry entry = MiningItemTable.getByKey(key);
        double npcPrice = entry != null ? entry.npcPrice() : 0.0;
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

    private Map<String, Double> getMergedDisplayQuantities() {
        String displayMode = ModConfig.getInstance().scathaProfitDisplayMode;
        Map<String, Long> raw = ProfitDataStore.getInstance().getItemsSnapshot(ProfitDataStore.TRACKER_SCATHA, displayMode);
        return MiningItemTable.mergeToFineGemstones(raw);
    }

    public double getTotalCoins(String mode) {
        double total = 0.0;
        for (Map.Entry<String, Double> e : getMergedDisplayQuantities().entrySet()) {
            if (e.getValue() > 0.0) {
                total += e.getValue() * getUnitPrice(e.getKey(), mode);
            }
        }
        return total;
    }

    public double getCoinsPerHour(String mode) {
        long secs = ProfitDataStore.getInstance().getActiveSeconds(
                ProfitDataStore.TRACKER_SCATHA, ModConfig.getInstance().scathaProfitDisplayMode);
        if (secs < 1L) return 0.0;
        return getTotalCoins(mode) / (secs / 3600.0);
    }

    public boolean hasAnyData() {
        return ProfitDataStore.getInstance().hasAnyData(
                ProfitDataStore.TRACKER_SCATHA, ModConfig.getInstance().scathaProfitDisplayMode);
    }

    public void resetSession() {
        ProfitDataStore.getInstance().reset(
                ProfitDataStore.TRACKER_SCATHA, ModConfig.getInstance().scathaProfitDisplayMode);
        sessionActive = false;
        lastActivityMs = 0L;
    }

    public static String cyclePriceMode() {
        ModConfig cfg = ModConfig.getInstance();
        String cur = cfg.scathaProfitPriceMode != null ? cfg.scathaProfitPriceMode : "BZ_SELL_OFFER";
        String next = switch (cur.toUpperCase(Locale.ROOT)) {
            case "NPC" -> "BZ_SELL_OFFER";
            case "BZ_SELL_OFFER" -> "BZ_BUY_ORDER";
            default -> "NPC";
        };
        cfg.scathaProfitPriceMode = next;
        ModConfig.save();
        if (instance != null) {
            instance.refreshBazaarPricesAsync();
            instance.refreshScathaAhPricesAsync();
        }
        return next;
    }

    public static String cycleDisplayMode() {
        ModConfig cfg = ModConfig.getInstance();
        cfg.scathaProfitDisplayMode = ProfitDataStore.MODE_SESSION.equalsIgnoreCase(cfg.scathaProfitDisplayMode)
                ? ProfitDataStore.MODE_PERSISTENT
                : ProfitDataStore.MODE_SESSION;
        ModConfig.save();
        return cfg.scathaProfitDisplayMode;
    }

    private List<DisplayRow> buildDisplayRows(String mode, boolean previewMode) {
        Map<String, Double> items = getMergedDisplayQuantities();
        List<DisplayRow> rows = new ArrayList<>();
        for (Map.Entry<String, Double> e : items.entrySet()) {
            double amt = e.getValue();
            if (amt <= 0.0) continue;
            String key = e.getKey();
            MiningItemTable.Entry meta = MiningItemTable.getByKey(key);
            String rawName = meta != null ? meta.name() : key;
            String name = MiningItemTable.getLocalizedName(key, rawName);
            double coins = amt * getUnitPrice(key, mode);
            int color = MiningItemTable.getColor(key);
            rows.add(new DisplayRow(key, name, amt, coins, color));
        }

        if (rows.isEmpty() && previewMode) {
            rows.add(new DisplayRow("SCATHA;2",
                    MiningItemTable.getLocalizedName("SCATHA;2", "Rare Scatha Pet"),
                    1.0, getUnitPrice("SCATHA;2", mode), MiningItemTable.getColor("SCATHA;2")));
            rows.add(new DisplayRow("SHARD_SCATHA",
                    MiningItemTable.getLocalizedName("SHARD_SCATHA", "Scatha Shard"),
                    4.0, 4.0 * getUnitPrice("SHARD_SCATHA", mode), MiningItemTable.getColor("SHARD_SCATHA")));
            rows.add(new DisplayRow("FINE_AMETHYST_GEM",
                    MiningItemTable.getLocalizedName("FINE_AMETHYST_GEM", "Fine Amethyst Gemstone"),
                    1.2, 1.2 * getUnitPrice("FINE_AMETHYST_GEM", mode), MiningItemTable.getColor("FINE_AMETHYST_GEM")));
        }

        rows.sort(Comparator.comparingDouble(DisplayRow::coins).reversed());
        return rows;
    }

    private static String formatQuantity(double amount) {
        long rounded = Math.round(amount);
        if (Math.abs(amount - rounded) < 1.0e-4) {
            return String.format(Locale.ROOT, "%,d", rounded);
        }
        String formatted = String.format(Locale.ROOT, "%,.2f", amount);
        while (formatted.contains(".") && (formatted.endsWith("0") || formatted.endsWith("."))) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        return formatted;
    }

    public int[] getHudBounds(Minecraft mc, float scale, boolean previewMode) {
        ModConfig cfg = ModConfig.getInstance();
        String mode = cfg.scathaProfitPriceMode != null ? cfg.scathaProfitPriceMode : "BZ_SELL_OFFER";
        List<DisplayRow> rows = buildDisplayRows(mode, previewMode);
        int visibleRows = rows.size() > MAX_VISIBLE_ROWS ? (MAX_VISIBLE_ROWS + 1) : Math.max(1, rows.size());
        int footerLines = 3; // Total+/hr, Time+DisplayMode, Cooldown countdown line
        int rowH = 12;
        int baseW = 216;
        int baseH = 29 + visibleRows * rowH + footerLines * 11;
        return new int[]{Math.round(baseW * scale), Math.round(baseH * scale)};
    }

    public void renderHudAt(GuiGraphicsExtractor graphics, Minecraft mc, int x, int y, float scale, boolean previewMode) {
        ModConfig cfg = ModConfig.getInstance();
        String mode = cfg.scathaProfitPriceMode != null ? cfg.scathaProfitPriceMode : "BZ_SELL_OFFER";

        List<DisplayRow> allRows = buildDisplayRows(mode, previewMode);
        int overflowCount = Math.max(0, allRows.size() - MAX_VISIBLE_ROWS);
        List<DisplayRow> visibleRows = overflowCount > 0 ? allRows.subList(0, MAX_VISIBLE_ROWS) : allRows;

        int rowH = 12;
        int rowLines = visibleRows.isEmpty() ? 1 : visibleRows.size() + (overflowCount > 0 ? 1 : 0);
        int footerLines = 3;
        int baseW = 216;
        int baseH = 29 + rowLines * rowH + footerLines * 11;

        graphics.pose().pushMatrix();
        graphics.pose().translate((float) x, (float) y);
        graphics.pose().scale(scale, scale);

        graphics.fill(0, 0, baseW, baseH, 0xB8101218);
        graphics.outline(0, 0, baseW, baseH, 0xFF2D3646);

        graphics.text(mc.font, ModLang.tr("dttdrtaddons.hud.scatha_profit.title"), 6, 5, MiningItemTable.LEGACY_GOLD);
        String badge = "[" + MiningProfitFeature.getModeDisplayName(mode) + "]";
        int badgeW = mc.font.width(badge);
        graphics.text(mc.font, badge, baseW - badgeW - 6, 5, MiningItemTable.LEGACY_AQUA);
        graphics.fill(5, 16, baseW - 5, 17, 0x663A4659);

        int curY = 20;
        if (visibleRows.isEmpty()) {
            graphics.text(mc.font, ModLang.tr("dttdrtaddons.hud.scatha_profit.empty"), 6, curY, MiningItemTable.LEGACY_GRAY);
            curY += rowH;
        } else {
            for (DisplayRow row : visibleRows) {
                PackDisablerIconBridge.drawItemIconOrColor(graphics, row.key(), 5, curY - 1, 10, row.color());
                String right = NumberFormatUtils.formatCompact(row.coins());
                int rightW = mc.font.width(right);
                int maxLeftW = baseW - 28 - rightW;
                String left = formatQuantity(row.amount()) + "x " + row.name();
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

        double totalCoins = (previewMode && !hasAnyData())
                ? allRows.stream().mapToDouble(DisplayRow::coins).sum()
                : getTotalCoins(mode);
        double perHour = (previewMode && !hasAnyData())
                ? totalCoins * 2.0
                : getCoinsPerHour(mode);

        String totalLabel = ModLang.tr("dttdrtaddons.hud.mining_profit.total", NumberFormatUtils.formatCompact(totalCoins));
        String rateText = NumberFormatUtils.formatCompact(perHour) + ModLang.tr("dttdrtaddons.hud.mining_profit.per_hr");
        graphics.text(mc.font, totalLabel, 6, curY, MiningItemTable.LEGACY_GOLD);
        graphics.text(mc.font, rateText, baseW - mc.font.width(rateText) - 6, curY, MiningItemTable.LEGACY_GREEN);
        curY += 11;

        long secs = ProfitDataStore.getInstance().getActiveSeconds(
                ProfitDataStore.TRACKER_SCATHA, cfg.scathaProfitDisplayMode);
        String timeStr = String.format("%02d:%02d", secs / 60, secs % 60);
        String statusSuffix = (!sessionActive && secs > 0) ? ModLang.tr("dttdrtaddons.hud.mining_profit.paused") : "";
        String leftMeta = ModLang.tr("dttdrtaddons.hud.mining_profit.time", timeStr, statusSuffix);
        graphics.text(mc.font, leftMeta, 6, curY, (!sessionActive && secs > 0) ? MiningItemTable.LEGACY_RED : MiningItemTable.LEGACY_GRAY);
        String dispTag = "[" + MiningProfitFeature.getDisplayModeName(cfg.scathaProfitDisplayMode) + "]";
        graphics.text(mc.font, dispTag, baseW - mc.font.width(dispTag) - 6, curY, MiningItemTable.LEGACY_GRAY);
        curY += 11;

        String cdText = previewMode
                ? "24.5"
                : formatCooldownText(System.currentTimeMillis());
        int cdColor = "/".equals(cdText) ? MiningItemTable.LEGACY_GRAY : MiningItemTable.LEGACY_AQUA;
        graphics.text(mc.font, cdText, 6, curY, cdColor);

        graphics.pose().popMatrix();
    }

    private String formatCooldownText(long now) {
        if (wormCooldownStartMs <= 0L) {
            return "/";
        }
        long elapsed = now - wormCooldownStartMs;
        if (elapsed >= WORM_SPAWN_COOLDOWN_MS) {
            return "/";
        }
        double remainSec = Math.max(0.0, (WORM_SPAWN_COOLDOWN_MS - elapsed) / 1000.0);
        return String.format(Locale.ROOT, "%.1f", remainSec);
    }
}
