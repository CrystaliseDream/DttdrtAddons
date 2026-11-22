package com.dttdrt.addons.features.market.model;

import java.util.Collections;
import java.util.List;

/**
 * Summary and detailed order book of a product in the Hypixel Bazaar.
 */
public class BazaarItemSummary {
    private final String productId;
    private final double instantBuyPrice;
    private final double instantSellPrice;
    private final long buyVolume;
    private final long sellVolume;
    private final int buyOrders;
    private final int sellOrders;
    private final List<BazaarOrderEntry> buyOrderBook;  // Top Buy Orders (from Hypixel sell_summary)
    private final List<BazaarOrderEntry> sellOrderBook; // Top Sell Orders (from Hypixel buy_summary)

    public BazaarItemSummary(String productId, double instantBuyPrice, double instantSellPrice,
                             long buyVolume, long sellVolume, int buyOrders, int sellOrders,
                             List<BazaarOrderEntry> buyOrderBook, List<BazaarOrderEntry> sellOrderBook) {
        this.productId = productId;
        this.instantBuyPrice = instantBuyPrice;
        this.instantSellPrice = instantSellPrice;
        this.buyVolume = buyVolume;
        this.sellVolume = sellVolume;
        this.buyOrders = buyOrders;
        this.sellOrders = sellOrders;
        this.buyOrderBook = buyOrderBook != null ? buyOrderBook : Collections.emptyList();
        this.sellOrderBook = sellOrderBook != null ? sellOrderBook : Collections.emptyList();
    }

    public String getProductId() { return productId; }
    public double getInstantBuyPrice() { return instantBuyPrice; }
    public double getInstantSellPrice() { return instantSellPrice; }
    public long getBuyVolume() { return buyVolume; }
    public long getSellVolume() { return sellVolume; }
    public int getBuyOrders() { return buyOrders; }
    public int getSellOrders() { return sellOrders; }
    public List<BazaarOrderEntry> getBuyOrderBook() { return buyOrderBook; }
    public List<BazaarOrderEntry> getSellOrderBook() { return sellOrderBook; }

    public double getMargin() {
        return instantBuyPrice - instantSellPrice;
    }

    public double getMarginPercentage() {
        if (instantSellPrice <= 0) return 0.0;
        return (getMargin() / instantSellPrice) * 100.0;
    }
}
