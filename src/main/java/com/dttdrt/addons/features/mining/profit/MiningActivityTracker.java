package com.dttdrt.addons.features.mining.profit;

import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.features.mining.CrystalHollowsTracker;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Signal-driven mining session timer and activity gate adapted from skbProfiTracker's ActivityBucket.
 * Strong signals (block break / start / continue with no GUI open) open a 90s mining window.
 * Weak signals (loot pickups) extend an already-open timer window without opening pickup attribution.
 */
public final class MiningActivityTracker {

    private static final long MINING_GRACE_MS = 90_000L;
    private static final long TAIL_MS = 15_000L;
    private static final long SECOND_MS = 1_000L;
    private static final int AUTO_RESUME_SIGNALS = 10;
    private static final long AUTO_RESUME_WINDOW_MS = 10_000L;
    private static final long INVENTORY_MINE_WINDOW_MS = 12_000L;
    private static final long SACKS_MINE_WINDOW_MS = 40_000L;
    private static final long FISHING_SETTLE_MS = 2_000L;
    private static final long FISHING_STICKY_MS = 8_000L;

    private final Deque<Long> recentStrongSignals = new ArrayDeque<>();
    private final String trackerId;

    private boolean manuallyPaused = false;
    private boolean bucketOpen = false;
    private boolean hasStrongSignal = false;
    private long lastSignalMs = 0L;
    private long lastBlockMineMs = 0L;
    private long closedAtMs = 0L;
    private long lastCountMs = 0L;

    private long bobberSinceMs = 0L;
    private long lastFishingMs = 0L;

    public MiningActivityTracker() {
        this(ProfitDataStore.TRACKER_MINING);
    }

    public MiningActivityTracker(String trackerId) {
        this.trackerId = trackerId != null ? trackerId : ProfitDataStore.TRACKER_MINING;
    }

    public synchronized void onStrongSignal(Minecraft mc) {
        if (mc == null || mc.screen != null) {
            return;
        }

        long now = System.currentTimeMillis();
        recentStrongSignals.addLast(now);
        while (!recentStrongSignals.isEmpty() && now - recentStrongSignals.peekFirst() > AUTO_RESUME_WINDOW_MS) {
            recentStrongSignals.removeFirst();
        }

        if (manuallyPaused) {
            if (recentStrongSignals.size() < AUTO_RESUME_SIGNALS) {
                return;
            }
            manuallyPaused = false;
        }

        lastSignalMs = now;
        lastBlockMineMs = now;
        hasStrongSignal = true;
    }

    public synchronized void onWeakSignal() {
        if (manuallyPaused || !hasStrongSignal) return;
        lastSignalMs = System.currentTimeMillis();
    }

    public synchronized void onWorldChanged() {
        bucketOpen = false;
        hasStrongSignal = false;
        lastSignalMs = 0L;
        lastBlockMineMs = 0L;
        closedAtMs = 0L;
        recentStrongSignals.clear();
        bobberSinceMs = 0L;
        lastFishingMs = 0L;
    }

    public synchronized void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        boolean miningZone = CrystalHollowsTracker.isInGemstoneMiningArea() && !isFishing(mc, now);
        if (miningZone) {
            bucketOpen = true;
            closedAtMs = now;
        } else if (bucketOpen && now - closedAtMs > TAIL_MS) {
            bucketOpen = false;
            hasStrongSignal = false;
        }

        if (now - lastCountMs >= SECOND_MS) {
            lastCountMs = now;
            if (isCounting()) {
                ProfitDataStore.getInstance().incrementActiveSecond(trackerId);
            }
        }
    }

    private boolean isFishing(Minecraft mc, long now) {
        if (mc == null || mc.player == null || mc.player.fishing == null) {
            bobberSinceMs = 0L;
        } else {
            if (bobberSinceMs == 0L) bobberSinceMs = now;
            if (now - bobberSinceMs >= FISHING_SETTLE_MS) {
                lastFishingMs = now;
            }
        }
        return now - lastFishingMs <= FISHING_STICKY_MS;
    }

    public synchronized boolean isActiveForInventoryPickup() {
        if (manuallyPaused || !bucketOpen || !hasStrongSignal) return false;
        return System.currentTimeMillis() - lastBlockMineMs <= INVENTORY_MINE_WINDOW_MS;
    }

    public synchronized boolean isActiveForSacksPickup() {
        if (manuallyPaused || !bucketOpen || !hasStrongSignal) return false;
        return System.currentTimeMillis() - lastBlockMineMs <= SACKS_MINE_WINDOW_MS;
    }

    public synchronized boolean isActiveForPickup() {
        return isActiveForInventoryPickup();
    }

    public synchronized boolean isCounting() {
        if (manuallyPaused || !bucketOpen || !hasStrongSignal) return false;
        return System.currentTimeMillis() - lastSignalMs <= MINING_GRACE_MS;
    }

    public synchronized long getActiveSeconds() {
        String mode = ProfitDataStore.TRACKER_SCATHA.equals(trackerId)
                ? ModConfig.getInstance().scathaProfitDisplayMode
                : ModConfig.getInstance().miningProfitDisplayMode;
        return ProfitDataStore.getInstance().getActiveSeconds(trackerId, mode);
    }

    public synchronized long getActiveSeconds(String displayMode) {
        return ProfitDataStore.getInstance().getActiveSeconds(trackerId, displayMode);
    }

    public synchronized boolean isManuallyPaused() {
        return manuallyPaused;
    }

    public synchronized void setManuallyPaused(boolean paused) {
        this.manuallyPaused = paused;
        if (paused) {
            this.hasStrongSignal = false;
            this.lastBlockMineMs = 0L;
            this.recentStrongSignals.clear();
        }
    }

    public synchronized void togglePaused() {
        setManuallyPaused(!this.manuallyPaused);
    }

    public synchronized void resetSession() {
        lastSignalMs = 0L;
        lastBlockMineMs = 0L;
        hasStrongSignal = false;
        manuallyPaused = false;
        recentStrongSignals.clear();
    }
}
