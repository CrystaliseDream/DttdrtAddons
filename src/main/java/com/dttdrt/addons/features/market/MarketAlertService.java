package com.dttdrt.addons.features.market;

import com.dttdrt.addons.features.market.model.AhItemListing;
import com.dttdrt.addons.features.market.model.BazaarItemSummary;
import com.dttdrt.addons.features.market.model.SkyblockItem;
import com.dttdrt.addons.util.NotificationUtils;
import com.dttdrt.addons.util.NumberFormatUtils;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Background price alert evaluation service.
 * Periodically compares current market prices against player-defined thresholds.
 * Strictly uses client-side notifications (chat, sound, overlay) and sends ZERO server packets.
 */
public class MarketAlertService {
    private static long lastCheckTime = 0;
    private static final long CHECK_INTERVAL_MS = 30_000L; // 30 seconds

    private static String lastAlertedAuctionUuid = null;
    private static long lastAlertTimestamp = 0;
    private static final long ALERT_COOLDOWN_MS = 60_000L; // 1 minute per alert type

    public static void tick(Minecraft client) {
        if (client.player == null) return;

        MarketState state = MarketState.getInstance();
        if (!state.isAlertEnabled()) return;

        long now = System.currentTimeMillis();
        if (now - lastCheckTime < CHECK_INTERVAL_MS) {
            return;
        }
        lastCheckTime = now;

        SkyblockItem item = state.getSelectedItem();
        if (item == null) return;

        double minThreshold = NumberFormatUtils.parsePriceInput(state.getMinPriceInput());
        double maxThreshold = NumberFormatUtils.parsePriceInput(state.getMaxPriceInput());

        if (minThreshold <= 0 && maxThreshold <= 0) return;

        if ("AH".equalsIgnoreCase(state.getMode())) {
            evaluateAhAlert(state, item, minThreshold, maxThreshold);
        } else {
            evaluateBazaarAlert(state, item, minThreshold, maxThreshold);
        }
    }

    private static void evaluateAhAlert(MarketState state, SkyblockItem item, double minThreshold, double maxThreshold) {
        List<AhItemListing> bins = state.getLowestBins();
        if (bins == null || bins.isEmpty()) return;

        // Check lowest BIN (#1)
        AhItemListing lowest = bins.get(0);
        long price = lowest.getPrice();
        String uuid = lowest.getAuctionUuid();

        long now = System.currentTimeMillis();

        if (minThreshold > 0 && price <= minThreshold) {
            if (!uuid.equals(lastAlertedAuctionUuid) || (now - lastAlertTimestamp > ALERT_COOLDOWN_MS)) {
                triggerAlert(item.getName(), price, "below", minThreshold, uuid, state);
                lastAlertedAuctionUuid = uuid;
                lastAlertTimestamp = now;
            }
        } else if (maxThreshold > 0 && price >= maxThreshold) {
            if (!uuid.equals(lastAlertedAuctionUuid) || (now - lastAlertTimestamp > ALERT_COOLDOWN_MS)) {
                triggerAlert(item.getName(), price, "above", maxThreshold, uuid, state);
                lastAlertedAuctionUuid = uuid;
                lastAlertTimestamp = now;
            }
        }
    }

    private static void evaluateBazaarAlert(MarketState state, SkyblockItem item, double minThreshold, double maxThreshold) {
        BazaarItemSummary bz = state.getBazaarSummary();
        if (bz == null) return;

        double currentPrice = bz.getInstantBuyPrice();
        long now = System.currentTimeMillis();

        if (minThreshold > 0 && currentPrice <= minThreshold) {
            if (now - lastAlertTimestamp > ALERT_COOLDOWN_MS) {
                triggerBazaarAlert(item.getName(), currentPrice, "below", minThreshold, state);
                lastAlertTimestamp = now;
            }
        } else if (maxThreshold > 0 && currentPrice >= maxThreshold) {
            if (now - lastAlertTimestamp > ALERT_COOLDOWN_MS) {
                triggerBazaarAlert(item.getName(), currentPrice, "above", maxThreshold, state);
                lastAlertTimestamp = now;
            }
        }
    }

    private static void triggerAlert(String itemName, long price, String condition, double threshold, String uuid, MarketState state) {
        if (state.isNotifyChat()) {
            NotificationUtils.sendAhPriceAlert(itemName, price, condition, threshold, uuid);
        }
        if (state.isNotifySound()) {
            NotificationUtils.playAlertSound();
        }
        if (state.isNotifyTitle()) {
            NotificationUtils.showOverlayToast(itemName, "Lowest BIN " + NumberFormatUtils.formatWithCommas(price) + " Coins (" + condition + " target)");
        }
    }

    private static void triggerBazaarAlert(String itemName, double price, String condition, double threshold, MarketState state) {
        if (state.isNotifyChat()) {
            NotificationUtils.sendBazaarPriceAlert(itemName, price, condition, threshold);
        }
        if (state.isNotifySound()) {
            NotificationUtils.playAlertSound();
        }
        if (state.isNotifyTitle()) {
            NotificationUtils.showOverlayToast(itemName, "Bazaar Buy " + NumberFormatUtils.formatExact(price) + " Coins (" + condition + " target)");
        }
    }
}

