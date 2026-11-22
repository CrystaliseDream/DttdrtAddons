package com.dttdrt.addons.features.mining.profit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleFunction;

/**
 * Mining spending/cost ledger adapted from skbProfiTracker's CostLedger.
 * Keeps cost-only exclusions for keys/Nucleus parts/consumables and bills only
 * player mining consumables (Fiesta Flask, Chilled Pristine Potato, Filet O' Fortune, Powder Pie).
 */
public final class MiningCostLedger {

    private static final long CONSUMABLE_DEDUPE_MS = 5_000L;

    private static final Set<String> CORPSE_COST_KEYS = Set.of(
            "UMBER_KEY",
            "TUNGSTEN_KEY",
            "SKELETON_KEY"
    );

    private static final Set<String> NUCLEUS_COST_KEYS = Set.of(
            "JUNGLE_KEY",
            "GOBLIN_EGG",
            "PRECURSOR_APPARATUS",
            "ROBOTRON_REFLECTOR",
            "SUPERLITE_MOTOR",
            "CONTROL_SWITCH",
            "ELECTRON_TRANSMITTER",
            "FTX_3070",
            "SYNTHETIC_HEART"
    );

    private static final Map<String, Double> MINING_CONSUMABLE_COSTS = Map.of(
            "FIESTA_FLASK", 2_500_000.0,
            "CHILLED_PRISTINE_POTATO", 242_500.0,
            "FILET_O_FORTUNE", 97_000.0,
            "MINING_PUMPKIN", 48_500.0
    );

    private static final Set<String> ALL_COST_KEYS;
    static {
        Set<String> all = new HashSet<>();
        all.addAll(CORPSE_COST_KEYS);
        all.addAll(NUCLEUS_COST_KEYS);
        all.addAll(MINING_CONSUMABLE_COSTS.keySet());
        all.add("REHEATED_GUMMY_POLAR_BEAR");
        ALL_COST_KEYS = Set.copyOf(all);
    }

    private final Map<String, Long> lastCostAtMs = new HashMap<>();

    public static boolean isCostOnly(String key) {
        return key != null && ALL_COST_KEYS.contains(key);
    }

    public static Set<String> getAllCostKeys() {
        return ALL_COST_KEYS;
    }

    public synchronized void onItemRemoved(String key, int amount, boolean activeMining) {
        if (!activeMining || amount <= 0 || key == null) {
            return;
        }
        // Only bill direct player mining consumables; ignore corpse keys and nucleus materials.
        if (!MINING_CONSUMABLE_COSTS.containsKey(key)) {
            return;
        }

        long now = System.currentTimeMillis();
        Long prev = lastCostAtMs.get(key);
        if (prev != null && now - prev < CONSUMABLE_DEDUPE_MS) {
            return;
        }
        lastCostAtMs.put(key, now);
        ProfitDataStore.getInstance().addCost(ProfitDataStore.TRACKER_MINING, key, amount);
    }

    public double getTotalCost(String displayMode, ToDoubleFunction<String> keyUnitPriceLookup) {
        double total = 0.0;
        Map<String, Long> costs = ProfitDataStore.getInstance().getCostsSnapshot(ProfitDataStore.TRACKER_MINING, displayMode);
        for (Map.Entry<String, Long> entry : costs.entrySet()) {
            long count = entry.getValue();
            if (count <= 0) continue;
            Double shopCost = MINING_CONSUMABLE_COSTS.get(entry.getKey());
            double unit = shopCost != null
                    ? shopCost
                    : keyUnitPriceLookup.applyAsDouble(entry.getKey());
            total += unit * count;
        }
        return total;
    }

    public synchronized void reset() {
        lastCostAtMs.clear();
    }
}
