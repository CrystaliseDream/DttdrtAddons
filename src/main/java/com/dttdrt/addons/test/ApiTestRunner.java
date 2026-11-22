package com.dttdrt.addons.test;

import com.dttdrt.addons.api.CoflnetAhApi;
import com.dttdrt.addons.api.HypixelBazaarApi;
import com.dttdrt.addons.api.ItemDatabase;
import com.dttdrt.addons.api.PriceHistoryApi;
import com.dttdrt.addons.features.market.model.AhItemListing;
import com.dttdrt.addons.features.market.model.BazaarItemSummary;
import com.dttdrt.addons.features.market.model.PriceHistoryPoint;
import com.dttdrt.addons.features.market.model.SkyblockItem;
import com.dttdrt.addons.util.NumberFormatUtils;

import java.util.List;

public class ApiTestRunner {
    public static void main(String[] args) throws Exception {
        System.out.println("=== 1. Fuzzy Search Test ===");
        ItemDatabase.syncFullCatalogAsync().get();
        System.out.println("Synced catalog size: " + ItemDatabase.getCatalogSize());

        String[] testQueries = {"hyp", "aote", "manticore", "enchanted", "necron"};
        for (String q : testQueries) {
            List<SkyblockItem> matches = ItemDatabase.fuzzySearch(q, false);
            System.out.println("Query '" + q + "' returned " + matches.size() + " matches:");
            for (SkyblockItem m : matches) {
                System.out.println("  -> [" + m.getTier() + "] " + m.getName() + " | Material: " + m.getVanillaMaterial()
                        + (m.getSkinValue() != null ? " | HasSkin: true" : ""));
            }
        }

        System.out.println("\n=== 2. Live Coflnet AH Lowest 7 BIN + Player Name Test ===");
        List<AhItemListing> bins = CoflnetAhApi.getLowestBinsAsync("HYPERION").get();
        Thread.sleep(1500); // Wait for async Mojang UUID resolution
        System.out.println("Found " + bins.size() + " lowest BIN auctions for Hyperion:");
        for (int i = 0; i < bins.size(); i++) {
            AhItemListing l = bins.get(i);
            System.out.println("  #" + (i + 1) + " " + NumberFormatUtils.formatCompact(l.getPrice())
                    + " (" + NumberFormatUtils.formatWithCommas(l.getPrice()) + ") | Seller: " + l.getSellerName()
                    + " | " + l.getRelativeAgeString());
        }

        System.out.println("\n=== 3. Live Hypixel Official Bazaar + Order Book Test ===");
        BazaarItemSummary bz = HypixelBazaarApi.getBazaarSummaryAsync("ENCHANTED_CARROT").get();
        if (bz != null) {
            System.out.println("Bazaar data for ENCHANTED_CARROT:");
            System.out.println("  Instant Buy: " + bz.getInstantBuyPrice() + " | Instant Sell: " + bz.getInstantSellPrice());
            System.out.println("  Buy Orders rows: " + bz.getBuyOrderBook().size() + " | Sell Orders rows: " + bz.getSellOrderBook().size());
        }

        System.out.println("\n=== 4. Live Price History Test ===");
        List<PriceHistoryPoint> ahHist = PriceHistoryApi.getAhHistoryAsync("HYPERION", "week").get();
        List<PriceHistoryPoint> bzHist = PriceHistoryApi.getBazaarHistoryAsync("ENCHANTED_CARROT", "week").get();
        System.out.println("AH History points: " + ahHist.size() + " | BZ History points: " + bzHist.size());

        System.out.println("\n=== ALL TESTS COMPLETED SUCCESSFULLY ===");
    }
}
