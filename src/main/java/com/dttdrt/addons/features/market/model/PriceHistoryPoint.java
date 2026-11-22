package com.dttdrt.addons.features.market.model;

/**
 * Single data point in an AH or BZ price history chart.
 * For AH: primaryPrice = avg/min BIN, secondaryPrice = min BIN.
 * For BZ: primaryPrice = buy price, secondaryPrice = sell price.
 */
public class PriceHistoryPoint {
    private final String timestampLabel;
    private final double primaryPrice;
    private final double secondaryPrice;

    public PriceHistoryPoint(String timestampLabel, double primaryPrice, double secondaryPrice) {
        this.timestampLabel = timestampLabel;
        this.primaryPrice = primaryPrice;
        this.secondaryPrice = secondaryPrice;
    }

    public String getTimestampLabel() {
        return timestampLabel;
    }

    public double getPrimaryPrice() {
        return primaryPrice;
    }

    public double getSecondaryPrice() {
        return secondaryPrice;
    }
}
