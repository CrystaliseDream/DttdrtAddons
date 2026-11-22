package com.dttdrt.addons.features.market.model;

/**
 * Represents a single Lowest BIN (Buy It Now) auction entry from the Auction House.
 */
public class AhItemListing {
    private final String auctionUuid;
    private final String itemId;
    private final String itemName;
    private final long price;
    private final String sellerUuid;
    private volatile String sellerName;
    private final long endTimestamp;
    private final long startTimestamp;
    private final String tier;
    private final int count;

    public AhItemListing(String auctionUuid, String itemId, String itemName, long price,
                         String sellerUuid, String sellerName, long endTimestamp, long startTimestamp,
                         String tier, int count) {
        this.auctionUuid = auctionUuid;
        this.itemId = itemId;
        this.itemName = itemName;
        this.price = price;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName != null && !sellerName.isEmpty() ? sellerName : "Unknown";
        this.endTimestamp = endTimestamp;
        this.startTimestamp = startTimestamp;
        this.tier = tier != null ? tier : "COMMON";
        this.count = count > 0 ? count : 1;
    }

    public String getAuctionUuid() {
        return auctionUuid;
    }

    public String getItemId() {
        return itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public long getPrice() {
        return price;
    }

    public String getSellerUuid() {
        return sellerUuid;
    }

    public String getSellerName() {
        return sellerName;
    }

    public void setSellerName(String sellerName) {
        if (sellerName != null && !sellerName.isEmpty()) {
            this.sellerName = sellerName;
        }
    }

    public long getEndTimestamp() {
        return endTimestamp;
    }

    public long getStartTimestamp() {
        return startTimestamp;
    }

    public String getTier() {
        return tier;
    }

    public int getCount() {
        return count;
    }

    /**
     * Helper to get relative time string, e.g. "2m ago" or "10m remaining"
     */
    public String getRelativeAgeString() {
        long now = System.currentTimeMillis();
        long diff = now - startTimestamp;
        if (diff < 0) diff = 0;
        long seconds = diff / 1000;
        if (seconds < 60) return seconds + "s ago";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        return (hours / 24) + "d ago";
    }
}

