package com.dttdrt.addons.features.mining;

import com.dttdrt.addons.api.HypixelBazaarApi;
import com.dttdrt.addons.core.auth.WhitelistManager;
import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.core.feature.Feature;
import com.dttdrt.addons.core.lang.ModLang;
import com.dttdrt.addons.features.mining.profit.CaptureBuffer;
import com.dttdrt.addons.features.mining.profit.MiningActivityTracker;
import com.dttdrt.addons.features.mining.profit.MiningCostLedger;
import com.dttdrt.addons.features.mining.profit.MiningItemTable;
import com.dttdrt.addons.features.mining.profit.ProfitDataStore;
import com.dttdrt.addons.texture.PackDisablerIconBridge;
import com.dttdrt.addons.util.NumberFormatUtils;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
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
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mining Profit Tracker combining skbProfiTracker's inventory/container & [Sacks] capture pipeline,
 * anti-double-count compacting/deposit/recall guards, signal-driven active timing (90s grace),
 * and mining consumable cost deduction, while strictly tracking ONLY player block-mining yield
 * (suppressing Corpse, Nucleus, Treasure Chest, mob/slayer, Essence, and Purse coin gains).
 */
public class MiningProfitFeature implements Feature {

    private static final Identifier HUD_ID =
            Identifier.fromNamespaceAndPath("dttdrtaddons", "mining_profit_hud");

    private static final int TICKS_INVENTORY_SCAN = 4;
    private static final long BZ_REFRESH_INTERVAL_MS = 30_000L;
    private static final long INVENTORY_STABLE_MS = 3_500L;
    private static final long PENDING_INCREASE_MS = 1_000L;
    private static final long REMOVAL_CREDIT_MS = 10_000L;
    private static final long SCREEN_CLOSE_GRACE_MS = 1_500L;
    private static final long SACK_SCREEN_COOLDOWN_MS = 5_000L;

    private static final long BAZAAR_FILTER_MS = 2_000L;
    private static final long AUCTION_FILTER_MS = 5_000L;
    private static final long NPC_FILTER_MS = 2_000L;
    private static final long TRADE_FILTER_MS = 2_000L;
    private static final long SUPERCRAFT_FILTER_MS = 2_000L;
    private static final long NON_MINING_LOOT_FILTER_MS = 2_500L;

    private static final long PURCHASE_GUARD_WINDOW_MS = 10_000L;
    private static final long SUPERCRAFT_INV_WINDOW_MS = 10_000L;
    private static final long SUPERCRAFT_SACK_WINDOW_MS = 35_000L;

    private static final long SACK_DEFER_MS = 600L;
    private static final long DEPOSIT_TTL_MS = 120_000L;
    private static final long MAX_SACKS_DELAY_MS = 35_000L;

    private static final long CREDIT_TTL_MS = 60_000L;
    private static final long RECALL_MS = 10_000L;
    private static final long LOOT_BLOCK_STALE_MS = 10_000L;

    private static final int MAX_VISIBLE_ROWS = 12;

    private static final Set<String> PLAYER_MINED_KEYS = Set.of(
            "GLOSSY_GEMSTONE",
            "ENCHANTED_MITHRIL",
            "ENCHANTED_TITANIUM",
            "ENCHANTED_GLACITE",
            "ENCHANTED_UMBER",
            "ENCHANTED_TUNGSTEN",
            "ENCHANTED_HARD_STONE",
            "SUSPICIOUS_SCRAP", "SLUDGE_JUICE",
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
            "ENCHANTED_SULPHUR"
    );

    private static final List<String> CONVERSION_SCREEN_NAMES = List.of(
            "crafting", "anvil", "enchant", "brewing", "forge",
            "\u5de5\u4f5c\u53f0", "\u5408\u6210", "\u94c1\u7827", "\u9644\u9b54", "\u917f\u9020", "\u953b\u9020"
    );
    private static final List<String> MENU_SCREEN_NAMES = List.of(
            "skyblock menu", "skyblock \u83dc\u5355", "skyblock\u9009\u5355",
            "fast travel", "skyblock leveling", "collections"
    );

    // Chat patterns
    private static final Pattern AUCTION_PURCHASED = Pattern.compile("^You purchased (.+) for ([\\d,]+) coins!$");
    private static final Pattern AUCTION_CLAIMED = Pattern.compile("^You claimed (.+) from .+'s auction!$");
    private static final Pattern SACK_GRAB = Pattern.compile("grab x([\\d,]+) (.+?) from sacks!");
    private static final String SUPERCRAFT_PREFIX = "You Supercrafted ";
    private static final String STASH_TO_SACKS_MSG =
            "You have successfully transferred your items from this stash to your sacks!";
    private static final String CHEST_LOCK_PICKED = "You have successfully picked the lock on this chest!";

    private static final Pattern SACKS_ADDED_LINE = Pattern.compile("(\\+[\\d,]+)\\s+(.+?)\\s+\\((.+?)\\)");
    private static final Pattern SACKS_REMOVED_LINE = Pattern.compile("(-[\\d,]+)\\s+(.+?)\\s+\\((.+?)\\)");

    private static final Pattern CORPSE_START =
            Pattern.compile("^\\s+(LAPIS|TUNGSTEN|UMBER|VANGUARD) CORPSE LOOT!.*");
    private static final Pattern NUCLEUS_START =
            Pattern.compile("^\\s+CRYSTAL NUCLEUS LOOT BUNDLE.*");
    private static final Pattern CHEST_LOOT_START =
            Pattern.compile("^\\s+CHEST LOOT.*");
    private static final Pattern CHAT_AMOUNT_PATTERN = Pattern.compile("^(.*?)\\s+x([\\d,]+)$");

    private static volatile MiningProfitFeature instance;

    private record Deposit(int amount, long atMs) {}
    private record SackPickup(String itemName, int amount) {}
    private record DeferredSackAdd(List<SackPickup> items, long dueAtMs) {}
    private record PurchaseFence(Set<String> keys, long atMs) {}
    private record RecentAdd(String key, long amount, long atMs) {}
    private record ChatCredit(long amount, long atMs) {}

    private static final class LootBlockState {
        final boolean isCorpse;
        boolean active = false;
        long lastLineMs = 0L;

        LootBlockState(boolean isCorpse) {
            this.isCorpse = isCorpse;
        }
    }

    private final MiningActivityTracker activityTracker = new MiningActivityTracker(ProfitDataStore.TRACKER_MINING);
    private final MiningCostLedger costLedger = new MiningCostLedger();
    private final CaptureBuffer buffer = new CaptureBuffer(PENDING_INCREASE_MS, REMOVAL_CREDIT_MS);

    private final Map<String, double[]> bzPrices = new ConcurrentHashMap<>();

    private final Map<String, Deposit> pendingDeposits = new HashMap<>();
    private final List<DeferredSackAdd> deferredSackAdds = new ArrayList<>();
    private final Deque<PurchaseFence> purchaseFences = new ArrayDeque<>();
    private final Map<String, Long> supercraftedAt = new HashMap<>();
    private final Map<String, ChatCredit> chatCredits = new HashMap<>();
    private final Deque<RecentAdd> recentNormalAdds = new ArrayDeque<>();

    private final LootBlockState corpseBlock = new LootBlockState(true);
    private final LootBlockState nucleusBlock = new LootBlockState(false);
    private final LootBlockState chestBlock = new LootBlockState(false);

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

    private long lastBazaarMsgMs = 0L;
    private long lastAuctionMsgMs = 0L;
    private long lastNpcMsgMs = 0L;
    private long lastTradeMsgMs = 0L;
    private long lastSupercraftMsgMs = 0L;
    private long lastStashToSacksMs = 0L;
    private long lastNonMiningLootMs = 0L;

    private int scanTickCounter = 0;
    private long lastBzFetchMs = 0L;

    public MiningProfitFeature() {
        instance = this;
        MiningItemTable.ensureLoaded();
    }

    public static MiningProfitFeature getInstance() {
        return instance;
    }

    @Override
    public String getId() {
        return "mining_profit";
    }

    @Override
    public String getName() {
        return "Mining Profit Tracker";
    }

    @Override
    public String getCategory() {
        return "Gemstone ESP";
    }

    @Override
    public void init() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.OVERLAY_MESSAGE, HUD_ID, (graphics, deltaTracker) -> {
            Minecraft mc = Minecraft.getInstance();
            if (!WhitelistManager.isAuthorized() || !isEnabled()) return;
            if (mc.player == null || mc.level == null || mc.options.hideGui) return;
            if (mc.screen instanceof MiningProfitHudEditScreen) return;
            ModConfig cfg = ModConfig.getInstance();
            if (hasAnyData() || CrystalHollowsTracker.isInGemstoneMiningArea()) {
                renderHudAt(graphics, mc, cfg.miningProfitHudX, cfg.miningProfitHudY, cfg.miningProfitHudScale, false);
            }
        });
    }

    public static void onStrongActivitySignal() {
        MiningProfitFeature inst = instance;
        if (inst == null || !inst.isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        inst.activityTracker.onStrongSignal(mc);
    }

    public static void onActionBarMessage(String rawText) {
        // ActionBar essence gains are intentionally ignored; tracker counts only direct player mining yield.
    }

    public static void onChatMessage(String rawText) {
        MiningProfitFeature inst = instance;
        if (inst == null || !WhitelistManager.isAuthorized() || !inst.isEnabled() || rawText == null) return;
        inst.handlePlainChat(MiningItemTable.stripFormatting(rawText));
    }

    public static void onChatComponent(Component message) {
        MiningProfitFeature inst = instance;
        if (inst == null || !WhitelistManager.isAuthorized() || !inst.isEnabled() || message == null) return;
        String clean = MiningItemTable.stripFormatting(message.getString());
        inst.handlePlainChat(clean);
        if (clean.startsWith("[Sacks] +") || clean.startsWith("[Sacks] -")) {
            inst.handleSacksChatComponent(message, clean);
        }
    }

    @Override
    public void clientTick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (!isEnabled() || mc.player == null || mc.level == null) {
            resetTransientState();
            lastLevel = mc != null ? mc.level : null;
            return;
        }

        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            resetTransientState();
            activityTracker.onWorldChanged();
        }

        activityTracker.tick(mc);
        ProfitDataStore.getInstance().tickAutoSave(now);
        trackScreenTransitions(mc, now);
        flushDeferredSackAdds(now);

        if (now - lastBzFetchMs >= BZ_REFRESH_INTERVAL_MS
                || (bzPrices.isEmpty() && now - lastBzFetchMs >= 5_000L)) {
            lastBzFetchMs = now;
            refreshBazaarPricesAsync();
        }

        scanTickCounter++;
        if (scanTickCounter < TICKS_INVENTORY_SCAN) return;
        scanTickCounter = 0;

        detectInventoryChanges(mc, now);
    }

    private void resetTransientState() {
        previousInventory = null;
        previousContainer = null;
        inventoryLoaded = false;
        lastFingerprint = null;
        fingerprintStableSince = 0L;
        lastScreenOpen = false;
        sackScreenOpen = false;
        lastScreenTitle = null;
        lastNonMiningLootMs = 0L;
        corpseBlock.active = false;
        nucleusBlock.active = false;
        chestBlock.active = false;
        buffer.clearHoldsAndSkipped();
        synchronized (deferredSackAdds) {
            deferredSackAdds.clear();
        }
        synchronized (pendingDeposits) {
            pendingDeposits.clear();
        }
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
                if (isBazaarChestName(lastScreenTitle)) lastBazaarGuiClosedMs = now;
                if (isAuctionChestName(lastScreenTitle)) lastAuctionGuiClosedMs = now;
            }
        }
        if (screenOpen && title != null) {
            lastScreenTitle = title;
        } else if (!screenOpen) {
            lastScreenTitle = null;
        }
    }

    private void detectInventoryChanges(Minecraft mc, long now) {
        if (!inventoryLoaded) {
            String fp = computeFingerprint(mc);
            if (!fp.equals(lastFingerprint)) {
                lastFingerprint = fp;
                fingerprintStableSince = now;
                return;
            }
            if (fingerprintStableSince == 0L) {
                fingerprintStableSince = now;
                return;
            }
            if (now - fingerprintStableSince < INVENTORY_STABLE_MS) {
                return;
            }
            inventoryLoaded = true;
            previousInventory = collectInventory(mc);
            previousContainer = collectContainer(mc);
            lastScreenOpen = mc.screen != null;
            sackScreenOpen = isInSacksGui(mc);
            return;
        }

        Map<String, Integer> currentInv = collectInventory(mc);
        Map<String, Integer> currentCont = collectContainer(mc);
        Map<String, Integer> prevInv = previousInventory;
        if (prevInv == null) {
            previousInventory = currentInv;
            previousContainer = currentCont;
            return;
        }

        Screen currentScreen = mc.screen;
        boolean screenOpen = currentScreen != null;
        boolean inSackNow = isInSacksGui(mc);

        if (screenOpen != lastScreenOpen) {
            boolean closingSack = !screenOpen && sackScreenOpen;
            lastScreenOpen = screenOpen;
            sackScreenOpen = screenOpen && inSackNow;

            if (closingSack) {
                for (Map.Entry<String, Integer> entry : prevInv.entrySet()) {
                    int cur = currentInv.getOrDefault(entry.getKey(), 0);
                    if (cur < entry.getValue()) {
                        noteManualDeposit(entry.getKey(), entry.getValue() - cur, now);
                    }
                }
            }

            previousInventory = currentInv;
            previousContainer = currentCont;
            return;
        }

        if (isConversionScreen(mc) || isMenuScreen(mc)) {
            previousInventory = currentInv;
            previousContainer = currentCont;
            return;
        }

        Map<String, Integer> prevCont = previousContainer != null ? previousContainer : Map.of();

        try {
            boolean activeMining = activityTracker.isActiveForPickup();
            boolean containerOpen = getCurrentChestName(mc) != null;

            for (Map.Entry<String, Integer> entry : prevInv.entrySet()) {
                String key = entry.getKey();
                int prevTotal = entry.getValue();
                int curTotal = currentInv.getOrDefault(key, 0);
                if (curTotal >= prevTotal) continue;

                int removed = prevTotal - curTotal;
                if (!screenOpen) {
                    buffer.addRemoval(key, removed, now);
                    costLedger.onItemRemoved(key, removed, activeMining);
                } else if (inSackNow) {
                    noteManualDeposit(key, removed, now);
                }
            }

            for (Map.Entry<String, Integer> entry : prevCont.entrySet()) {
                String key = entry.getKey();
                int lost = entry.getValue() - currentCont.getOrDefault(key, 0);
                if (lost > 0) {
                    buffer.explain(key, lost);
                }
            }

            for (Map.Entry<String, Integer> entry : currentInv.entrySet()) {
                String key = entry.getKey();
                int curTotal = entry.getValue();
                int prevTotal = prevInv.getOrDefault(key, 0);
                if (curTotal <= prevTotal) continue;

                int increase = curTotal - prevTotal;
                int contDec = prevCont.getOrDefault(key, 0) - currentCont.getOrDefault(key, 0);
                int containerExplained = Math.min(increase, Math.max(contDec, 0));
                int pickupAmount = increase - containerExplained;
                if (pickupAmount <= 0) continue;

                if (screenOpen && inSackNow) {
                    buffer.noteSkipped(key, pickupAmount);
                    continue;
                }
                if (!screenOpen && (now - lastScreenClosedMs) < SCREEN_CLOSE_GRACE_MS) {
                    continue;
                }
                if ((now - lastSackScreenClosedMs) < SACK_SCREEN_COOLDOWN_MS) {
                    continue;
                }
                if (screenOpen) {
                    // Mining drops cannot arrive legitimately while a non-sack menu is open.
                    continue;
                }

                pickupAmount = buffer.takeCredit(key, pickupAmount, now, MiningProfitFeature::resolveTrackedCompaction);
                if (pickupAmount <= 0) continue;

                onItemPickedUp(mc, key, pickupAmount, now);
            }

            for (CaptureBuffer.KeyAmount due : buffer.dueHolds(now)) {
                int credited = buffer.takeCredit(due.key(), due.amount(), now, MiningProfitFeature::resolveTrackedCompaction);
                if (credited > 0) {
                    onItemPickedUp(mc, due.key(), credited, now);
                }
            }
        } finally {
            previousInventory = currentInv;
            previousContainer = currentCont;
        }
    }

    private static CaptureBuffer.Compaction resolveTrackedCompaction(String key) {
        CaptureBuffer.Compaction comp = MiningItemTable.resolveCompaction(key);
        if (comp == null || !isPlayerMinedKey(comp.parentKey())) {
            return null;
        }
        return comp;
    }

    private static boolean isPlayerMinedKey(String key) {
        if (key == null) return false;
        return key.endsWith("_GEM") || PLAYER_MINED_KEYS.contains(key);
    }

    private void onItemPickedUp(Minecraft mc, String key, int amount, long now) {
        if (amount <= 0 || shouldSkipPickup(mc, now)) return;
        if (!isPlayerMinedKey(key)) return;

        MiningItemTable.Entry entry = MiningItemTable.getByKey(key);
        if (entry != null && isSupercraftedRecently(entry.name(), SUPERCRAFT_INV_WINDOW_MS, now)) {
            return;
        }
        if (consumePurchaseFence(key, now)) {
            return;
        }
        if (!activityTracker.isActiveForInventoryPickup()) {
            return;
        }

        activityTracker.onWeakSignal();
        addNormalItem(key, amount, now);
    }

    private boolean shouldSkipPickup(Minecraft mc, long now) {
        String chestName = getCurrentChestName(mc);
        if (chestName != null) {
            if (isBazaarChestName(chestName) || isAuctionChestName(chestName) || isInSacksGui(mc)) {
                return true;
            }
            if ("Pets".equals(chestName) || chestName.contains("Hunting Box")) {
                return true;
            }
        }
        if (now - lastBazaarGuiClosedMs < BAZAAR_FILTER_MS) return true;
        if (now - lastBazaarMsgMs < BAZAAR_FILTER_MS) return true;
        if (now - lastAuctionGuiClosedMs < AUCTION_FILTER_MS) return true;
        if (now - lastAuctionMsgMs < AUCTION_FILTER_MS) return true;
        if (now - lastNpcMsgMs < NPC_FILTER_MS) return true;
        if (now - lastTradeMsgMs < TRADE_FILTER_MS) return true;
        if (now - lastSupercraftMsgMs < SUPERCRAFT_FILTER_MS) return true;
        if (now - lastNonMiningLootMs < NON_MINING_LOOT_FILTER_MS) return true;
        return false;
    }

    private void handlePlainChat(String message) {
        if (message == null || message.isEmpty()) return;
        long now = System.currentTimeMillis();

        if (message.startsWith(SUPERCRAFT_PREFIX)) {
            lastSupercraftMsgMs = now;
            recordSupercraft(message.substring(SUPERCRAFT_PREFIX.length()), now);
        } else if (message.startsWith("[Bazaar]") || message.contains("Bazaar!")) {
            lastBazaarMsgMs = now;
        } else if (message.startsWith("You bought") || message.startsWith("You sold")) {
            lastNpcMsgMs = now;
        } else if (message.startsWith("You traded with") || message.contains("Trade completed")) {
            lastTradeMsgMs = now;
        } else if (message.contains("Auction")) {
            lastAuctionMsgMs = now;
        }

        if (STASH_TO_SACKS_MSG.equals(message.trim())) {
            lastStashToSacksMs = now;
        }
        if (message.contains(CHEST_LOCK_PICKED)) {
            lastNonMiningLootMs = now;
        }

        Matcher mBuy = AUCTION_PURCHASED.matcher(message);
        if (mBuy.find()) {
            addPurchaseFence(mBuy.group(1), now);
        }
        Matcher mClaim = AUCTION_CLAIMED.matcher(message);
        if (mClaim.find()) {
            addPurchaseFence(mClaim.group(1), now);
        }

        Matcher mGrab = SACK_GRAB.matcher(message);
        if (mGrab.find()) {
            try {
                int amt = Integer.parseInt(mGrab.group(1).replace(",", ""));
                MiningItemTable.Entry entry = MiningItemTable.get(mGrab.group(2).trim());
                if (entry != null && amt > 0) {
                    buffer.addRemoval(entry.key(), amt, now);
                }
            } catch (NumberFormatException ignored) {}
        }

        processLootBlockLine(corpseBlock, message, now);
        processLootBlockLine(nucleusBlock, message, now);
        processLootBlockLine(chestBlock, message, now);
    }

    private void handleSacksChatComponent(Component message, String plain) {
        if (!activityTracker.isActiveForSacksPickup()) return;
        long now = System.currentTimeMillis();

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
                    if (left > 0 && isPlayerMinedKey(entry.key())) {
                        buffer.addRemoval(entry.key(), left, now);
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
                } catch (NumberFormatException ignored) {}
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
        if (!activityTracker.isActiveForSacksPickup()) return;

        for (DeferredSackAdd batch : due) {
            for (SackPickup pickup : batch.items()) {
                MiningItemTable.Entry entry = MiningItemTable.get(MiningItemTable.cleanItemName(pickup.itemName()));
                if (entry == null || !isPlayerMinedKey(entry.key())) continue;
                if (isSupercraftedRecently(pickup.itemName(), SUPERCRAFT_SACK_WINDOW_MS, now)) continue;

                int fresh = pickup.amount() - takeManualDeposit(entry.key(), pickup.amount(), now);
                if (fresh <= 0) continue;

                fresh = buffer.takeCredit(entry.key(), fresh, now, MiningProfitFeature::resolveTrackedCompaction);
                if (fresh <= 0) continue;

                activityTracker.onWeakSignal();
                addNormalItem(entry.key(), fresh, now);
            }
        }
    }

    private void processLootBlockLine(LootBlockState state, String text, long now) {
        if (state.active && now - state.lastLineMs > LOOT_BLOCK_STALE_MS) {
            closeLootBlock(state);
        }

        if (!state.active) {
            boolean matched = state.isCorpse
                    ? CORPSE_START.matcher(text).matches()
                    : (state == nucleusBlock ? NUCLEUS_START.matcher(text).matches() : CHEST_LOOT_START.matcher(text).matches());
            if (matched) {
                state.active = true;
                state.lastLineMs = now;
                lastNonMiningLootMs = now;
            }
            return;
        }

        state.lastLineMs = now;
        lastNonMiningLootMs = now;
        if (isLootBlockEnd(text)) {
            closeLootBlock(state);
            return;
        }
        if (!isRewardLine(text)) return;

        String[] parsed = parseRewardLine(text);
        if (parsed == null) return;
        String rawName = parsed[0];
        long amount = Long.parseLong(parsed[1]);
        if (rawName.contains(" Powder") || amount <= 0) return;

        String lookupName = (state.isCorpse && "Enchanted Book".equals(rawName))
                ? "Enchanted Book (Ice Cold I)"
                : rawName;
        MiningItemTable.Entry entry = MiningItemTable.get(lookupName);
        if (entry == null || !isPlayerMinedKey(entry.key())) return;

        suppressNonMiningLoot(entry.key(), amount, now);
    }

    private void closeLootBlock(LootBlockState state) {
        state.active = false;
    }

    private static boolean isLootBlockEnd(String text) {
        String trimmed = text.trim();
        if (trimmed.length() < 60) return false;
        for (int i = 0; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) != '\u25AC') return false;
        }
        return true;
    }

    private static boolean isRewardLine(String text) {
        int indent = 0;
        while (indent < text.length() && Character.isWhitespace(text.charAt(indent))) {
            indent++;
        }
        return indent >= 4;
    }

    private static String[] parseRewardLine(String text) {
        String body = text.trim();
        int idx = 0;
        while (idx < body.length() && !Character.isLetterOrDigit(body.charAt(idx)) && body.charAt(idx) != '(') {
            idx++;
        }
        body = body.substring(idx).trim();
        if (body.isEmpty()) return null;

        Matcher m = CHAT_AMOUNT_PATTERN.matcher(body);
        if (m.matches()) {
            try {
                long amt = Long.parseLong(m.group(2).replace(",", ""));
                String name = m.group(1).trim();
                return (!name.isEmpty() && amt > 0) ? new String[]{name, String.valueOf(amt)} : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return new String[]{body, "1"};
    }

    private synchronized void addNormalItem(String key, long amount, long now) {
        if (amount <= 0 || key == null) return;
        if (MiningCostLedger.isCostOnly(key) || !isPlayerMinedKey(key)) return;

        long remaining = amount - consumeChatCredit(key, amount, now);
        if (remaining <= 0) return;

        ProfitDataStore.getInstance().addItem(ProfitDataStore.TRACKER_MINING, key, remaining);
        recentNormalAdds.addLast(new RecentAdd(key, remaining, now));
        while (!recentNormalAdds.isEmpty() && now - recentNormalAdds.peekFirst().atMs() > RECALL_MS) {
            recentNormalAdds.removeFirst();
        }
    }

    private synchronized void suppressNonMiningLoot(String key, long amount, long now) {
        if (amount <= 0 || key == null) return;

        long left = amount;
        List<RecentAdd> kept = new ArrayList<>();
        while (!recentNormalAdds.isEmpty()) {
            kept.add(recentNormalAdds.removeFirst());
        }
        for (int i = kept.size() - 1; i >= 0 && left > 0; i--) {
            RecentAdd add = kept.get(i);
            if (!add.key().equals(key) || now - add.atMs() > RECALL_MS) continue;
            long taken = Math.min(left, add.amount());
            subtractNormalItem(key, taken);
            left -= taken;
            if (add.amount() > taken) {
                kept.set(i, new RecentAdd(add.key(), add.amount() - taken, add.atMs()));
            } else {
                kept.remove(i);
            }
        }
        for (RecentAdd remaining : kept) {
            recentNormalAdds.addLast(remaining);
        }

        if (left > 0) {
            ChatCredit existing = chatCredits.get(key);
            long curAmt = (existing != null && now - existing.atMs() <= CREDIT_TTL_MS) ? existing.amount() : 0L;
            chatCredits.put(key, new ChatCredit(curAmt + left, now));
        }
    }

    private void subtractNormalItem(String key, long amount) {
        ProfitDataStore.getInstance().subtractItem(ProfitDataStore.TRACKER_MINING, key, amount);
    }

    private long consumeChatCredit(String key, long amount, long now) {
        ChatCredit credit = chatCredits.get(key);
        if (credit == null) return 0L;
        if (now - credit.atMs() > CREDIT_TTL_MS) {
            chatCredits.remove(key);
            return 0L;
        }
        long taken = Math.min(amount, credit.amount());
        long left = credit.amount() - taken;
        if (left <= 0) {
            chatCredits.remove(key);
        } else {
            chatCredits.put(key, new ChatCredit(left, credit.atMs()));
        }
        return taken;
    }

    private void noteManualDeposit(String key, int amount, long now) {
        if (key == null || amount <= 0) return;
        synchronized (pendingDeposits) {
            Deposit prev = pendingDeposits.get(key);
            int carried = (prev != null && now - prev.atMs() <= DEPOSIT_TTL_MS) ? prev.amount() : 0;
            pendingDeposits.put(key, new Deposit(carried + amount, now));
        }
    }

    private int takeManualDeposit(String key, int requested, long now) {
        synchronized (pendingDeposits) {
            Deposit dep = pendingDeposits.get(key);
            if (dep == null) return 0;
            if (now - dep.atMs() > DEPOSIT_TTL_MS) {
                pendingDeposits.remove(key);
                return 0;
            }
            int used = Math.min(requested, dep.amount());
            int left = dep.amount() - used;
            if (left <= 0) {
                pendingDeposits.remove(key);
            } else {
                pendingDeposits.put(key, new Deposit(left, now));
            }
            return used;
        }
    }

    private void recordSupercraft(String rawRemainder, long now) {
        String normalized = normalizeSupercraftName(rawRemainder);
        if (normalized.isEmpty()) return;
        synchronized (supercraftedAt) {
            supercraftedAt.put(normalized, now);
            supercraftedAt.entrySet().removeIf(e -> now - e.getValue() > SUPERCRAFT_SACK_WINDOW_MS);
        }
    }

    private boolean isSupercraftedRecently(String itemName, long windowMs, long now) {
        String normalized = normalizeSupercraftName(itemName);
        if (normalized.isEmpty()) return false;
        synchronized (supercraftedAt) {
            Long at = supercraftedAt.get(normalized);
            return at != null && (now - at <= windowMs);
        }
    }

    private static String normalizeSupercraftName(String raw) {
        if (raw == null) return "";
        int xIdx = raw.indexOf(" x");
        String cut = xIdx >= 0 ? raw.substring(0, xIdx) : raw;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cut.length(); i++) {
            char c = cut.charAt(i);
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '\'') {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString().trim();
    }

    private void addPurchaseFence(String rawName, long now) {
        Set<String> keys = MiningItemTable.getChatKeys(rawName);
        if (keys.isEmpty()) return;
        synchronized (purchaseFences) {
            purchaseFences.addLast(new PurchaseFence(keys, now));
            while (!purchaseFences.isEmpty() && now - purchaseFences.peekFirst().atMs() > PURCHASE_GUARD_WINDOW_MS) {
                purchaseFences.removeFirst();
            }
        }
    }

    private boolean consumePurchaseFence(String key, long now) {
        synchronized (purchaseFences) {
            while (!purchaseFences.isEmpty() && now - purchaseFences.peekFirst().atMs() > PURCHASE_GUARD_WINDOW_MS) {
                purchaseFences.removeFirst();
            }
            Iterator<PurchaseFence> it = purchaseFences.iterator();
            while (it.hasNext()) {
                PurchaseFence fence = it.next();
                if (fence.keys().contains(key)) {
                    it.remove();
                    return true;
                }
            }
            return false;
        }
    }

    private static Map<String, Integer> collectInventory(Minecraft mc) {
        Map<String, Integer> result = new HashMap<>();
        if (mc.player == null) return result;
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < 36 && i < inv.getContainerSize(); i++) {
            addStack(result, inv.getItem(i));
        }
        addStack(result, mc.player.containerMenu.getCarried());
        return result;
    }

    private static Map<String, Integer> collectContainer(Minecraft mc) {
        Map<String, Integer> result = new HashMap<>();
        if (mc.player == null) return result;
        Inventory playerInv = mc.player.getInventory();
        for (Slot slot : mc.player.containerMenu.slots) {
            if (slot.container == playerInv) continue;
            addStack(result, slot.getItem());
        }
        if (mc.screen instanceof InventoryScreen) {
            addStack(result, mc.player.getItemBySlot(EquipmentSlot.HEAD));
            addStack(result, mc.player.getItemBySlot(EquipmentSlot.CHEST));
            addStack(result, mc.player.getItemBySlot(EquipmentSlot.LEGS));
            addStack(result, mc.player.getItemBySlot(EquipmentSlot.FEET));
            addStack(result, mc.player.getItemBySlot(EquipmentSlot.OFFHAND));
        }
        return result;
    }

    private static void addStack(Map<String, Integer> target, ItemStack stack) {
        MiningItemTable.Entry entry = MiningItemTable.resolveStack(stack);
        if (entry != null && stack.getCount() > 0) {
            target.merge(entry.key(), stack.getCount(), Integer::sum);
        }
    }

    private static String computeFingerprint(Minecraft mc) {
        if (mc.player == null) return "";
        Inventory inv = mc.player.getInventory();
        StringBuilder sb = new StringBuilder(256);
        for (int i = 0; i < 36 && i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
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
        keys.addAll(MiningCostLedger.getAllCostKeys());
        HypixelBazaarApi.getBatchPricesAsync(new ArrayList<>(keys)).thenAccept(map -> {
            if (map == null || map.isEmpty()) return;
            ModConfig cfg = ModConfig.getInstance();
            Set<String> costKeys = MiningCostLedger.getAllCostKeys();
            boolean persistCostUpdate = false;
            for (Map.Entry<String, double[]> e : map.entrySet()) {
                String k = e.getKey();
                double[] fresh = e.getValue();
                if (fresh == null || fresh.length < 2) continue;
                double[] prev = bzPrices.get(k);
                double sellOffer = fresh[0] > 0.0 ? fresh[0] : (prev != null ? prev[0] : 0.0);
                double buyOrder = fresh[1] > 0.0 ? fresh[1] : (prev != null ? prev[1] : 0.0);
                bzPrices.put(k, new double[]{sellOffer, buyOrder});

                if (costKeys.contains(k) && fresh[1] > 0.0) {
                    Double cached = cfg.lastCostBzPrices.get(k);
                    if (cached == null || cached <= 0.0
                            || Math.abs(fresh[1] - cached) / cached >= 0.01) {
                        persistCostUpdate = true;
                    }
                    cfg.lastCostBzPrices.put(k, fresh[1]);
                }
            }
            if (persistCostUpdate) {
                ModConfig.save();
            }
        });
    }

    public double getUnitPrice(String key, String mode) {
        if (MiningItemTable.COINS_KEY.equals(key)) {
            return 1.0;
        }
        MiningItemTable.Entry entry = MiningItemTable.getByKey(key);
        double npcPrice = entry != null ? entry.npcPrice() : 0.0;
        if ("NPC".equalsIgnoreCase(mode)) {
            if ("GLOSSY_GEMSTONE".equals(key)) {
                double[] prices = bzPrices.get(key);
                if (prices != null) {
                    double instaBuy = prices[0];
                    double instaSell = prices[1];
                    if (instaBuy > 0.0 && instaSell > 0.0) {
                        return (instaBuy + instaSell) * 0.5;
                    }
                    if (instaBuy > 0.0) {
                        return instaBuy;
                    }
                    if (instaSell > 0.0) {
                        return instaSell;
                    }
                }
            }
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

    /**
     * Costs (Skeleton/Umber/Tungsten/Jungle keys, Goblin Eggs, Robot Parts) uniformly use
     * Bazaar Buy Order price regardless of loot valuation mode, falling back to the last
     * fetched Bazaar Buy Order price if live data is unavailable.
     */
    public double getCostUnitPrice(String key, String mode) {
        if (MiningItemTable.COINS_KEY.equals(key)) {
            return 1.0;
        }
        double[] prices = bzPrices.get(key);
        if (prices != null && prices[1] > 0.0) {
            return prices[1];
        }
        Double lastKnown = ModConfig.getInstance().lastCostBzPrices.get(key);
        if (lastKnown != null && lastKnown > 0.0) {
            return lastKnown;
        }
        if (prices != null && prices[0] > 0.0) {
            return prices[0];
        }
        MiningItemTable.Entry entry = MiningItemTable.getByKey(key);
        return entry != null ? entry.npcPrice() : 0.0;
    }

    private Map<String, Double> getMergedDisplayQuantities() {
        String displayMode = ModConfig.getInstance().miningProfitDisplayMode;
        Map<String, Long> raw = ProfitDataStore.getInstance().getItemsSnapshot(ProfitDataStore.TRACKER_MINING, displayMode);
        return MiningItemTable.mergeToFineGemstones(raw);
    }

    public double getTotalCoins(String mode) {
        double total = 0.0;
        for (Map.Entry<String, Double> entry : getMergedDisplayQuantities().entrySet()) {
            double amt = entry.getValue();
            if (amt > 0.0) {
                total += amt * getUnitPrice(entry.getKey(), mode);
            }
        }
        return total;
    }

    public double getTotalCosts(String mode) {
        return costLedger.getTotalCost(ModConfig.getInstance().miningProfitDisplayMode, k -> getCostUnitPrice(k, mode));
    }

    public double getNetCoins(String mode) {
        return getTotalCoins(mode) - getTotalCosts(mode);
    }

    public double getCoinsPerHour(String mode) {
        long secs = activityTracker.getActiveSeconds(ModConfig.getInstance().miningProfitDisplayMode);
        if (secs < 1L) return 0.0;
        double hours = secs / 3600.0;
        return getNetCoins(mode) / hours;
    }

    public boolean hasAnyData() {
        String displayMode = ModConfig.getInstance().miningProfitDisplayMode;
        return ProfitDataStore.getInstance().hasAnyData(ProfitDataStore.TRACKER_MINING, displayMode);
    }

    public synchronized void resetSession() {
        String displayMode = ModConfig.getInstance().miningProfitDisplayMode;
        ProfitDataStore.getInstance().reset(ProfitDataStore.TRACKER_MINING, displayMode);
        recentNormalAdds.clear();
        chatCredits.clear();
        lastNonMiningLootMs = 0L;
        corpseBlock.active = false;
        nucleusBlock.active = false;
        chestBlock.active = false;
        costLedger.reset();
        activityTracker.resetSession();
    }

    public static String getDisplayModeName(String displayMode) {
        return ProfitDataStore.MODE_SESSION.equalsIgnoreCase(displayMode)
                ? ModLang.tr("dttdrtaddons.hud.profit.display_session")
                : ModLang.tr("dttdrtaddons.hud.profit.display_persistent");
    }

    public static String cycleDisplayMode() {
        ModConfig cfg = ModConfig.getInstance();
        cfg.miningProfitDisplayMode = ProfitDataStore.MODE_SESSION.equalsIgnoreCase(cfg.miningProfitDisplayMode)
                ? ProfitDataStore.MODE_PERSISTENT
                : ProfitDataStore.MODE_SESSION;
        ModConfig.save();
        return cfg.miningProfitDisplayMode;
    }

    public boolean isPaused() {
        return activityTracker.isManuallyPaused();
    }

    public void togglePaused() {
        activityTracker.togglePaused();
    }

    public static String getModeDisplayName(String mode) {
        if ("BZ_SELL_OFFER".equalsIgnoreCase(mode)) return ModLang.tr("dttdrtaddons.hud.mining_profit.mode_bz_sell");
        if ("BZ_BUY_ORDER".equalsIgnoreCase(mode)) return ModLang.tr("dttdrtaddons.hud.mining_profit.mode_bz_buy");
        return ModLang.tr("dttdrtaddons.hud.mining_profit.mode_npc");
    }

    public static String cyclePriceMode() {
        ModConfig cfg = ModConfig.getInstance();
        String cur = cfg.miningProfitPriceMode != null ? cfg.miningProfitPriceMode : "NPC";
        String next = switch (cur.toUpperCase(Locale.ROOT)) {
            case "NPC" -> "BZ_SELL_OFFER";
            case "BZ_SELL_OFFER" -> "BZ_BUY_ORDER";
            default -> "NPC";
        };
        cfg.miningProfitPriceMode = next;
        ModConfig.save();
        if (instance != null && !"NPC".equals(next)) {
            instance.refreshBazaarPricesAsync();
        }
        return next;
    }

    private record DisplayRow(String key, String name, double amount, double coins, int color) {}

    private List<DisplayRow> buildDisplayRows(String mode, boolean previewMode) {
        Map<String, Double> items = getMergedDisplayQuantities();
        List<DisplayRow> rows = new ArrayList<>();
        for (Map.Entry<String, Double> entry : items.entrySet()) {
            double amt = entry.getValue();
            if (amt <= 0.0) continue;
            String key = entry.getKey();
            MiningItemTable.Entry meta = MiningItemTable.getByKey(key);
            String rawName = meta != null ? meta.name() : key;
            String name = MiningItemTable.getLocalizedName(key, rawName);
            double coins = amt * getUnitPrice(key, mode);
            int color = MiningItemTable.getColor(key);
            rows.add(new DisplayRow(key, name, amt, coins, color));
        }

        if (rows.isEmpty() && previewMode) {
            rows.add(new DisplayRow("FINE_JASPER_GEM",
                    MiningItemTable.getLocalizedName("FINE_JASPER_GEM", "Fine Jasper Gemstone"),
                    28.0, 28.0 * getUnitPrice("FINE_JASPER_GEM", mode),
                    MiningItemTable.getColor("FINE_JASPER_GEM")));
            rows.add(new DisplayRow("FINE_TOPAZ_GEM",
                    MiningItemTable.getLocalizedName("FINE_TOPAZ_GEM", "Fine Topaz Gemstone"),
                    12.5, 12.5 * getUnitPrice("FINE_TOPAZ_GEM", mode),
                    MiningItemTable.getColor("FINE_TOPAZ_GEM")));
            rows.add(new DisplayRow("ENCHANTED_MITHRIL",
                    MiningItemTable.getLocalizedName("ENCHANTED_MITHRIL", "Enchanted Mithril"),
                    85.0, 85.0 * getUnitPrice("ENCHANTED_MITHRIL", mode),
                    MiningItemTable.getColor("ENCHANTED_MITHRIL")));
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

    public int[] getHudBounds(Minecraft mc, float scale) {
        return getHudBounds(mc, scale, false);
    }

    public int[] getHudBounds(Minecraft mc, float scale, boolean previewMode) {
        ModConfig cfg = ModConfig.getInstance();
        String mode = cfg.miningProfitPriceMode != null ? cfg.miningProfitPriceMode : "NPC";
        List<DisplayRow> rows = buildDisplayRows(mode, previewMode);
        boolean showCosts = getTotalCosts(mode) > 0.0;
        int visibleRows = rows.size() > MAX_VISIBLE_ROWS ? (MAX_VISIBLE_ROWS + 1) : Math.max(1, rows.size());
        int footerLines = showCosts ? 4 : 2;
        int rowH = 12;
        int baseW = 216;
        int baseH = 29 + visibleRows * rowH + footerLines * 11;
        return new int[]{Math.round(baseW * scale), Math.round(baseH * scale)};
    }

    public void renderHudAt(GuiGraphicsExtractor graphics, Minecraft mc, int x, int y, float scale, boolean previewMode) {
        ModConfig cfg = ModConfig.getInstance();
        String mode = cfg.miningProfitPriceMode != null ? cfg.miningProfitPriceMode : "NPC";

        List<DisplayRow> allRows = buildDisplayRows(mode, previewMode);
        double costs = getTotalCosts(mode);
        boolean showCosts = costs > 0.0;

        int overflowCount = Math.max(0, allRows.size() - MAX_VISIBLE_ROWS);
        List<DisplayRow> visibleRows = overflowCount > 0
                ? allRows.subList(0, MAX_VISIBLE_ROWS)
                : allRows;

        int rowH = 12;
        int rowLines = visibleRows.isEmpty() ? 1 : visibleRows.size() + (overflowCount > 0 ? 1 : 0);
        int footerLines = showCosts ? 4 : 2;
        int baseW = 216;
        int baseH = 29 + rowLines * rowH + footerLines * 11;

        graphics.pose().pushMatrix();
        graphics.pose().translate((float) x, (float) y);
        graphics.pose().scale(scale, scale);

        graphics.fill(0, 0, baseW, baseH, 0xB8101218);
        graphics.outline(0, 0, baseW, baseH, 0xFF2D3646);

        graphics.text(mc.font, ModLang.tr("dttdrtaddons.hud.mining_profit.title"), 6, 5, MiningItemTable.LEGACY_GOLD);
        String badge = "[" + getModeDisplayName(mode) + "]";
        int badgeW = mc.font.width(badge);
        graphics.text(mc.font, badge, baseW - badgeW - 6, 5, MiningItemTable.LEGACY_AQUA);
        graphics.fill(5, 16, baseW - 5, 17, 0x663A4659);

        int curY = 20;
        if (visibleRows.isEmpty()) {
            graphics.text(mc.font, ModLang.tr("dttdrtaddons.hud.mining_profit.empty"), 6, curY, MiningItemTable.LEGACY_GRAY);
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

        double grossCoins = (previewMode && !hasAnyData())
                ? allRows.stream().mapToDouble(DisplayRow::coins).sum()
                : getTotalCoins(mode);
        double netCoins = grossCoins - costs;
        double perHour = (previewMode && !hasAnyData())
                ? grossCoins * 4.0
                : getCoinsPerHour(mode);

        if (showCosts) {
            String grossStr = NumberFormatUtils.formatCompact(grossCoins);
            graphics.text(mc.font, ModLang.tr("dttdrtaddons.hud.mining_profit.gross", grossStr), 6, curY, MiningItemTable.LEGACY_GRAY);
            curY += 11;

            String costStr = "-" + NumberFormatUtils.formatCompact(costs);
            graphics.text(mc.font, ModLang.tr("dttdrtaddons.hud.mining_profit.costs", costStr), 6, curY, MiningItemTable.LEGACY_RED);
            curY += 11;
        }

        String totalLabel = ModLang.tr(
                showCosts ? "dttdrtaddons.hud.mining_profit.net" : "dttdrtaddons.hud.mining_profit.total",
                NumberFormatUtils.formatCompact(showCosts ? netCoins : grossCoins));
        String rateText = NumberFormatUtils.formatCompact(perHour) + ModLang.tr("dttdrtaddons.hud.mining_profit.per_hr");
        graphics.text(mc.font, totalLabel, 6, curY, (showCosts && netCoins < 0) ? MiningItemTable.LEGACY_RED : MiningItemTable.LEGACY_GOLD);
        graphics.text(mc.font, rateText, baseW - mc.font.width(rateText) - 6, curY, MiningItemTable.LEGACY_GREEN);
        curY += 11;

        long secs = activityTracker.getActiveSeconds(cfg.miningProfitDisplayMode);
        String timeStr = String.format("%02d:%02d", secs / 60, secs % 60);
        String statusSuffix = activityTracker.isManuallyPaused()
                ? ModLang.tr("dttdrtaddons.hud.mining_profit.paused")
                : (!activityTracker.isCounting() && secs > 0 ? ModLang.tr("dttdrtaddons.hud.mining_profit.idle") : "");
        String leftMeta = ModLang.tr("dttdrtaddons.hud.mining_profit.time", timeStr, statusSuffix);
        int metaColor = activityTracker.isManuallyPaused() ? MiningItemTable.LEGACY_RED : MiningItemTable.LEGACY_GRAY;
        graphics.text(mc.font, leftMeta, 6, curY, metaColor);
        String dispTag = "[" + getDisplayModeName(cfg.miningProfitDisplayMode) + "]";
        graphics.text(mc.font, dispTag, baseW - mc.font.width(dispTag) - 6, curY, MiningItemTable.LEGACY_GRAY);

        graphics.pose().popMatrix();
    }

    @Override
    public boolean isEnabled() {
        return ModConfig.getInstance().miningProfitEnabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        ModConfig.getInstance().miningProfitEnabled = enabled;
        ModConfig.save();
    }
}
