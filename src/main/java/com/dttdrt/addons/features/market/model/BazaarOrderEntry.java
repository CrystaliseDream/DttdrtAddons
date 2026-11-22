package com.dttdrt.addons.features.market.model;

/**
 * Single price level row in a Bazaar order book (Buy Orders or Sell Orders).
 */
public class BazaarOrderEntry {
    private final int orders;
    private final long amount;
    private final double pricePerUnit;

    public BazaarOrderEntry(int orders, long amount, double pricePerUnit) {
        this.orders = orders;
        this.amount = amount;
        this.pricePerUnit = pricePerUnit;
    }

    public int getOrders() {
        return orders;
    }

    public long getAmount() {
        return amount;
    }

    public double getPricePerUnit() {
        return pricePerUnit;
    }

    public double getCoinEquivalent() {
        return amount * pricePerUnit;
    }
}
