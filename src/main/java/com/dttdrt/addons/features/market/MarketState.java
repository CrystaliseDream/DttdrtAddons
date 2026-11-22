package com.dttdrt.addons.features.market;

import com.dttdrt.addons.api.CoflnetAhApi;
import com.dttdrt.addons.api.HypixelBazaarApi;
import com.dttdrt.addons.api.ItemDatabase;
import com.dttdrt.addons.api.PriceHistoryApi;
import com.dttdrt.addons.core.auth.WhitelistManager;
import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.features.market.model.AhItemListing;
import com.dttdrt.addons.features.market.model.BazaarItemSummary;
import com.dttdrt.addons.features.market.model.PriceHistoryPoint;
import com.dttdrt.addons.features.market.model.SkyblockItem;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Global singleton state holder for the Market Monitor GUI.
 * Keeps AH and BZ search queries & selected items strictly independent,
 * enforces a 10s manual refresh cooldown to avoid API rate-limits,
 * and stores interactive price history series.
 */
public class MarketState {
    public static final long REFRESH_COOLDOWN_MS = 10_000L; // 10s safe minimum interval

    private static MarketState instance;

    private String selectedCategory = "ah/bz";
    private String mode = "AH"; // "AH" or "BZ"

    // Independent AH state (empty on fresh game launch)
    private String ahSearchQuery = "";
    private SkyblockItem ahSelectedItem;
    private List<AhItemListing> lowestBins = new ArrayList<>();
    private List<PriceHistoryPoint> ahHistoryPoints = new ArrayList<>();
    private List<PriceHistoryPoint> ahComparePoints = new ArrayList<>();
    private String ahHistoryRange = "week"; // "day", "week", "month"

    // Independent BZ state (empty on fresh game launch)
    private String bzSearchQuery = "";
    private SkyblockItem bzSelectedItem;
    private BazaarItemSummary bazaarSummary;
    private List<PriceHistoryPoint> bzHistoryPoints = new ArrayList<>();
    private List<PriceHistoryPoint> bzComparePoints = new ArrayList<>();
    private String bzHistoryRange = "week"; // "hour", "day", "week"

    // Current active fuzzy suggestions (up to 5) & recent lookups (up to 6)
    private List<SkyblockItem> fuzzySuggestions = new ArrayList<>();
    private final List<SkyblockItem> recentItems = new ArrayList<>();

    private volatile boolean isLoading = false;
    private String statusMessage = "Ready";
    private long lastManualRefreshTime = 0;

    // Alert settings
    private boolean alertEnabled = false;
    private String minPriceInput = "";
    private String maxPriceInput = "";
    private boolean notifyChat = true;
    private boolean notifySound = true;
    private boolean notifyTitle = true;

    private MarketState() {
        ModConfig config = ModConfig.getInstance();
        this.selectedCategory = config.lastSelectedCategory != null ? config.lastSelectedCategory : "ah/bz";
        this.mode = config.lastMode != null ? config.lastMode : "AH";

        // Do not auto-query a preset item on fresh game launch
        this.ahSearchQuery = "";
        this.ahSelectedItem = null;
        this.bzSearchQuery = "";
        this.bzSelectedItem = null;

        this.alertEnabled = config.alertEnabled;
        this.minPriceInput = config.alertMinPriceInput != null ? config.alertMinPriceInput : "";
        this.maxPriceInput = config.alertMaxPriceInput != null ? config.alertMaxPriceInput : "";
        this.notifyChat = config.notifyChat;
        this.notifySound = config.notifySound;
        this.notifyTitle = config.notifyTitle;
    }

    public static synchronized MarketState getInstance() {
        if (instance == null) {
            instance = new MarketState();
        }
        return instance;
    }

    public void onScreenOpened() {
        refreshDataAsync(false);
    }

    public boolean canManualRefresh() {
        return System.currentTimeMillis() - lastManualRefreshTime >= REFRESH_COOLDOWN_MS;
    }

    public int getRemainingCooldownSeconds() {
        long elapsed = System.currentTimeMillis() - lastManualRefreshTime;
        if (elapsed >= REFRESH_COOLDOWN_MS) return 0;
        return (int) Math.ceil((REFRESH_COOLDOWN_MS - elapsed) / 1000.0);
    }

    public void selectItem(SkyblockItem item) {
        if (item == null || !WhitelistManager.isAuthorized()) return;
        ModConfig config = ModConfig.getInstance();

        if ("AH".equalsIgnoreCase(mode)) {
            boolean changed = this.ahSelectedItem == null || !this.ahSelectedItem.getId().equals(item.getId());
            this.ahSelectedItem = item;
            this.ahSearchQuery = item.getName();
            this.ahDisplayLimit = 7;
            if (changed) {
                this.ahHistoryPoints = new ArrayList<>();
                this.ahComparePoints = new ArrayList<>();
            }
            config.lastAhQuery = this.ahSearchQuery;
            config.lastAhItemId = item.getId();
        } else {
            boolean changed = this.bzSelectedItem == null || !this.bzSelectedItem.getId().equals(item.getId());
            this.bzSelectedItem = item;
            this.bzSearchQuery = item.getName();
            this.bzOrderLimit = 6;
            if (changed) {
                this.bzHistoryPoints = new ArrayList<>();
                this.bzComparePoints = new ArrayList<>();
            }
            config.lastBzQuery = this.bzSearchQuery;
            config.lastBzItemId = item.getId();
        }

        this.recentItems.removeIf(existing -> existing.getId().equals(item.getId()));
        this.recentItems.add(0, item);
        while (this.recentItems.size() > 6) {
            this.recentItems.remove(this.recentItems.size() - 1);
        }

        this.fuzzySuggestions.clear();
        ModConfig.save();
        refreshDataAsync(false);
    }

    public void updateSearchQuery(String query) {
        if (!WhitelistManager.isAuthorized()) {
            this.fuzzySuggestions.clear();
            return;
        }
        if ("AH".equalsIgnoreCase(mode)) {
            this.ahSearchQuery = query != null ? query : "";
        } else {
            this.bzSearchQuery = query != null ? query : "";
        }

        if (query == null || query.trim().isEmpty()) {
            this.fuzzySuggestions.clear();
        } else {
            this.fuzzySuggestions = ItemDatabase.fuzzySearch(query, "BZ".equalsIgnoreCase(mode));
        }
    }

    public void setMode(String newMode) {
        if (newMode.equalsIgnoreCase(this.mode)) return;
        this.mode = newMode.toUpperCase();
        ModConfig.getInstance().lastMode = this.mode;
        ModConfig.save();
        this.fuzzySuggestions.clear();
        refreshDataAsync(false);
    }

    public void setHistoryRange(String range) {
        if ("AH".equalsIgnoreCase(mode)) {
            if (range.equalsIgnoreCase(ahHistoryRange)) return;
            ahHistoryRange = range.toLowerCase();
            fetchHistoryOnlyAsync();
        } else {
            if (range.equalsIgnoreCase(bzHistoryRange)) return;
            bzHistoryRange = range.toLowerCase();
            fetchHistoryOnlyAsync();
        }
    }

    public String getHistoryRange() {
        return "AH".equalsIgnoreCase(mode) ? ahHistoryRange : bzHistoryRange;
    }

    public List<PriceHistoryPoint> getHistoryPoints() {
        return "AH".equalsIgnoreCase(mode) ? ahHistoryPoints : bzHistoryPoints;
    }

    /**
     * Fixed-interval comparison series: AH uses 1-hour intervals ("day"/"week"),
     * BZ uses 2-hour intervals ("week").
     */
    public List<PriceHistoryPoint> getComparisonHistoryPoints() {
        return "AH".equalsIgnoreCase(mode) ? ahComparePoints : bzComparePoints;
    }

    private int ahDisplayLimit = 7;
    private int bzOrderLimit = 6;

    public int getAhDisplayLimit() { return ahDisplayLimit; }
    public int getBzOrderLimit() { return bzOrderLimit; }

    public void loadMoreAhAsync() {
        if (!WhitelistManager.isAuthorized()) return;
        SkyblockItem target = getSelectedItem();
        if (target == null || isLoading) return;
        this.ahDisplayLimit += 10;
        this.isLoading = true;
        this.statusMessage = "Loading more...";
        CoflnetAhApi.getLowestBinsAsync(target.getId(), this.ahDisplayLimit)
                .thenAccept(listings -> scheduleClientTask(() -> {
                    this.lowestBins = listings;
                    this.isLoading = false;
                    this.statusMessage = "Updated " + java.time.LocalTime.now().toString().substring(0, 8);
                }))
                .exceptionally(t -> {
                    scheduleClientTask(() -> {
                        this.isLoading = false;
                        this.statusMessage = "AH fetch failed";
                    });
                    return null;
                });
    }

    public void loadMoreBzAsync() {
        if (!WhitelistManager.isAuthorized()) return;
        SkyblockItem target = getSelectedItem();
        if (target == null || isLoading) return;
        this.bzOrderLimit += 10;
        this.isLoading = true;
        this.statusMessage = "Loading more...";
        HypixelBazaarApi.getBazaarSummaryAsync(target.getId())
                .thenAccept(summary -> scheduleClientTask(() -> {
                    if (summary != null) {
                        this.bazaarSummary = summary;
                    }
                    this.isLoading = false;
                    this.statusMessage = "Updated " + java.time.LocalTime.now().toString().substring(0, 8);
                }))
                .exceptionally(t -> {
                    scheduleClientTask(() -> {
                        this.isLoading = false;
                        this.statusMessage = "BZ fetch failed";
                    });
                    return null;
                });
    }

    public void refreshDataAsync(boolean isManual) {
        if (!WhitelistManager.isAuthorized()) return;
        if (isManual) {
            if (!canManualRefresh()) {
                return;
            }
            this.lastManualRefreshTime = System.currentTimeMillis();
            if ("AH".equalsIgnoreCase(mode)) {
                this.ahDisplayLimit = 7;
            } else {
                this.bzOrderLimit = 6;
            }
        }

        SkyblockItem target = getSelectedItem();
        if (target == null) return;

        this.isLoading = true;
        this.statusMessage = "Fetching...";

        if ("AH".equalsIgnoreCase(mode)) {
            CoflnetAhApi.getLowestBinsAsync(target.getId(), this.ahDisplayLimit)
                    .thenAccept(listings -> scheduleClientTask(() -> {
                        this.lowestBins = listings;
                        this.isLoading = false;
                        this.statusMessage = "Updated " + java.time.LocalTime.now().toString().substring(0, 8);
                    }))
                    .exceptionally(t -> {
                        scheduleClientTask(() -> {
                            this.isLoading = false;
                            this.statusMessage = "AH fetch failed";
                        });
                        return null;
                    });
        } else {
            HypixelBazaarApi.getBazaarSummaryAsync(target.getId())
                    .thenAccept(summary -> scheduleClientTask(() -> {
                        this.bazaarSummary = summary;
                        this.isLoading = false;
                        this.statusMessage = "Updated " + java.time.LocalTime.now().toString().substring(0, 8);
                    }))
                    .exceptionally(t -> {
                        scheduleClientTask(() -> {
                            this.isLoading = false;
                            this.statusMessage = "BZ fetch failed";
                        });
                        return null;
                    });
        }

        fetchHistoryOnlyAsync();
    }

    private void fetchHistoryOnlyAsync() {
        SkyblockItem target = getSelectedItem();
        if (target == null) return;

        final String itemId = target.getId();
        if ("AH".equalsIgnoreCase(mode)) {
            final String range = ahHistoryRange;
            final boolean isOneHourSeries = "day".equalsIgnoreCase(range) || "week".equalsIgnoreCase(range);
            PriceHistoryApi.getAhHistoryAsync(itemId, range)
                    .thenAccept(pts -> scheduleClientTask(() -> {
                        if (ahSelectedItem == null || !itemId.equals(ahSelectedItem.getId())) return;
                        this.ahHistoryPoints = pts;
                        if (isOneHourSeries) {
                            this.ahComparePoints = pts;
                        }
                    }));
            if (!isOneHourSeries) {
                PriceHistoryApi.getAhHistoryAsync(itemId, "day")
                        .thenAccept(pts -> scheduleClientTask(() -> {
                            if (ahSelectedItem != null && itemId.equals(ahSelectedItem.getId())) {
                                this.ahComparePoints = pts;
                            }
                        }));
            }
        } else {
            final String range = bzHistoryRange;
            final boolean isTwoHourSeries = "week".equalsIgnoreCase(range);
            PriceHistoryApi.getBazaarHistoryAsync(itemId, range)
                    .thenAccept(pts -> scheduleClientTask(() -> {
                        if (bzSelectedItem == null || !itemId.equals(bzSelectedItem.getId())) return;
                        this.bzHistoryPoints = pts;
                        if (isTwoHourSeries) {
                            this.bzComparePoints = pts;
                        }
                    }));
            if (!isTwoHourSeries) {
                PriceHistoryApi.getBazaarHistoryAsync(itemId, "week")
                        .thenAccept(pts -> scheduleClientTask(() -> {
                            if (bzSelectedItem != null && itemId.equals(bzSelectedItem.getId())) {
                                this.bzComparePoints = pts;
                            }
                        }));
            }
        }
    }

    private void scheduleClientTask(Runnable task) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.schedule(task);
        } else {
            task.run();
        }
    }

    // Active mode getters
    public String getMode() { return mode; }
    public String getSearchQuery() { return "AH".equalsIgnoreCase(mode) ? ahSearchQuery : bzSearchQuery; }
    public SkyblockItem getSelectedItem() { return "AH".equalsIgnoreCase(mode) ? ahSelectedItem : bzSelectedItem; }
    public List<SkyblockItem> getFuzzySuggestions() { return fuzzySuggestions; }
    public List<SkyblockItem> getRecentItems() { return recentItems; }
    public List<AhItemListing> getLowestBins() { return lowestBins; }
    public BazaarItemSummary getBazaarSummary() { return bazaarSummary; }
    public boolean isLoading() { return isLoading; }
    public String getStatusMessage() { return statusMessage; }

    public String getSelectedCategory() { return selectedCategory; }
    public void setSelectedCategory(String category) {
        this.selectedCategory = category;
        ModConfig.getInstance().lastSelectedCategory = category;
        ModConfig.save();
    }

    public boolean isAlertEnabled() { return alertEnabled; }
    public void setAlertEnabled(boolean alertEnabled) {
        this.alertEnabled = alertEnabled;
        ModConfig.getInstance().alertEnabled = alertEnabled;
        ModConfig.save();
    }

    public String getMinPriceInput() { return minPriceInput; }
    public void setMinPriceInput(String minPriceInput) {
        this.minPriceInput = minPriceInput;
        ModConfig.getInstance().alertMinPriceInput = minPriceInput;
        ModConfig.save();
    }

    public String getMaxPriceInput() { return maxPriceInput; }
    public void setMaxPriceInput(String maxPriceInput) {
        this.maxPriceInput = maxPriceInput;
        ModConfig.getInstance().alertMaxPriceInput = maxPriceInput;
        ModConfig.save();
    }

    public boolean isNotifyChat() { return notifyChat; }
    public void setNotifyChat(boolean notifyChat) {
        this.notifyChat = notifyChat;
        ModConfig.getInstance().notifyChat = notifyChat;
        ModConfig.save();
    }

    public boolean isNotifySound() { return notifySound; }
    public void setNotifySound(boolean notifySound) {
        this.notifySound = notifySound;
        ModConfig.getInstance().notifySound = notifySound;
        ModConfig.save();
    }

    public boolean isNotifyTitle() { return notifyTitle; }
    public void setNotifyTitle(boolean notifyTitle) {
        this.notifyTitle = notifyTitle;
        ModConfig.getInstance().notifyTitle = notifyTitle;
        ModConfig.save();
    }
}
