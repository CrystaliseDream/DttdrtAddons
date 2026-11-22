package com.dttdrt.addons.features.mining.profit;

import com.dttdrt.addons.core.config.ConfigPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dual-bucket profit persistence store (adapted from SkyHanni's Persistent/Total vs Session tracker).
 * Updates both Persistent (disk-saved across restarts) and Session (in-memory) buckets simultaneously,
 * so switching display modes or changing lobbies never loses tracked items, costs, or active time.
 */
public final class ProfitDataStore {

    public static final String MODE_PERSISTENT = "PERSISTENT";
    public static final String MODE_SESSION = "SESSION";
    public static final String TRACKER_MINING = "mining";
    public static final String TRACKER_SCATHA = "scatha";
    public static final String TRACKER_NUCLEUS = "nucleus";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DATA_DIR = ConfigPaths.getConfigDir();
    private static final File DATA_FILE = new File(DATA_DIR, "profit-data.json");
    private static final Object SAVE_LOCK = new Object();

    private static volatile ProfitDataStore instance;

    public static final class TrackerBucket {
        public final Map<String, Long> items = new ConcurrentHashMap<>();
        public final Map<String, Long> costs = new ConcurrentHashMap<>();
        public volatile long activeSeconds = 0L;
        public volatile long runsCompleted = 0L;

        public synchronized void clear() {
            items.clear();
            costs.clear();
            activeSeconds = 0L;
            runsCompleted = 0L;
        }
    }

    private static final class TrackerPair {
        final TrackerBucket persistent = new TrackerBucket();
        final TrackerBucket session = new TrackerBucket();

        TrackerBucket getBucket(String displayMode) {
            return MODE_SESSION.equalsIgnoreCase(displayMode) ? session : persistent;
        }
    }

    private final Map<String, TrackerPair> trackers = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;
    private volatile long lastSaveMs = 0L;

    private ProfitDataStore() {
        trackers.put(TRACKER_MINING, new TrackerPair());
        trackers.put(TRACKER_SCATHA, new TrackerPair());
        trackers.put(TRACKER_NUCLEUS, new TrackerPair());
    }

    public static ProfitDataStore getInstance() {
        if (instance == null) {
            synchronized (ProfitDataStore.class) {
                if (instance == null) {
                    ProfitDataStore store = new ProfitDataStore();
                    store.load();
                    instance = store;
                }
            }
        }
        return instance;
    }

    public static String normalizeMode(String mode) {
        return MODE_SESSION.equalsIgnoreCase(mode) ? MODE_SESSION : MODE_PERSISTENT;
    }

    private TrackerPair getPair(String trackerId) {
        return trackers.computeIfAbsent(trackerId, k -> new TrackerPair());
    }

    public void addItem(String trackerId, String key, long amount) {
        if (trackerId == null || key == null || amount <= 0) return;
        TrackerPair pair = getPair(trackerId);
        pair.persistent.items.merge(key, amount, Long::sum);
        pair.session.items.merge(key, amount, Long::sum);
        dirty = true;
    }

    public void subtractItem(String trackerId, String key, long amount) {
        if (trackerId == null || key == null || amount <= 0) return;
        TrackerPair pair = getPair(trackerId);
        subtractBucketItem(pair.persistent, key, amount);
        subtractBucketItem(pair.session, key, amount);
        dirty = true;
    }

    private static void subtractBucketItem(TrackerBucket bucket, String key, long amount) {
        bucket.items.computeIfPresent(key, (k, cur) -> {
            long next = cur - amount;
            return next > 0L ? next : null;
        });
    }

    public void addCost(String trackerId, String key, long amount) {
        if (trackerId == null || key == null || amount <= 0) return;
        TrackerPair pair = getPair(trackerId);
        pair.persistent.costs.merge(key, amount, Long::sum);
        pair.session.costs.merge(key, amount, Long::sum);
        dirty = true;
    }

    public void incrementActiveSecond(String trackerId) {
        if (trackerId == null) return;
        TrackerPair pair = getPair(trackerId);
        synchronized (pair.persistent) {
            pair.persistent.activeSeconds += 1L;
        }
        synchronized (pair.session) {
            pair.session.activeSeconds += 1L;
        }
        dirty = true;
    }

    public Map<String, Long> getItemsSnapshot(String trackerId, String displayMode) {
        return new HashMap<>(getPair(trackerId).getBucket(displayMode).items);
    }

    public Map<String, Long> getCostsSnapshot(String trackerId, String displayMode) {
        return new HashMap<>(getPair(trackerId).getBucket(displayMode).costs);
    }

    public long getActiveSeconds(String trackerId, String displayMode) {
        return getPair(trackerId).getBucket(displayMode).activeSeconds;
    }

    public void incrementRunsCompleted(String trackerId) {
        if (trackerId == null) return;
        TrackerPair pair = getPair(trackerId);
        synchronized (pair.persistent) {
            pair.persistent.runsCompleted += 1L;
        }
        synchronized (pair.session) {
            pair.session.runsCompleted += 1L;
        }
        dirty = true;
    }

    public long getRunsCompleted(String trackerId, String displayMode) {
        return getPair(trackerId).getBucket(displayMode).runsCompleted;
    }

    public boolean hasAnyData(String trackerId, String displayMode) {
        TrackerBucket bucket = getPair(trackerId).getBucket(displayMode);
        return !bucket.items.isEmpty() || !bucket.costs.isEmpty() || bucket.activeSeconds > 0L || bucket.runsCompleted > 0L;
    }

    public void reset(String trackerId, String displayMode) {
        TrackerPair pair = getPair(trackerId);
        TrackerBucket bucket = pair.getBucket(displayMode);
        bucket.clear();
        if (!MODE_SESSION.equalsIgnoreCase(displayMode)) {
            dirty = true;
            saveAsync();
        }
    }

    public void tickAutoSave(long nowMs) {
        if (dirty && nowMs - lastSaveMs >= 10_000L) {
            lastSaveMs = nowMs;
            saveAsync();
        }
    }

    public void saveAsync() {
        CompletableFuture.runAsync(this::saveSync);
    }

    public void saveSync() {
        synchronized (SAVE_LOCK) {
            dirty = false;
            lastSaveMs = System.currentTimeMillis();
            try {
                if (!DATA_DIR.exists()) {
                    DATA_DIR.mkdirs();
                }
                JsonObject root = new JsonObject();
                for (Map.Entry<String, TrackerPair> entry : trackers.entrySet()) {
                    TrackerBucket p = entry.getValue().persistent;
                    JsonObject tObj = new JsonObject();
                    tObj.addProperty("activeSeconds", p.activeSeconds);
                    if (p.runsCompleted > 0L) {
                        tObj.addProperty("runsCompleted", p.runsCompleted);
                    }

                    JsonObject itemsObj = new JsonObject();
                    for (Map.Entry<String, Long> ie : new HashMap<>(p.items).entrySet()) {
                        if (ie.getValue() != null && ie.getValue() > 0) {
                            itemsObj.addProperty(ie.getKey(), ie.getValue());
                        }
                    }
                    tObj.add("items", itemsObj);

                    JsonObject costsObj = new JsonObject();
                    for (Map.Entry<String, Long> ce : new HashMap<>(p.costs).entrySet()) {
                        if (ce.getValue() != null && ce.getValue() > 0) {
                            costsObj.addProperty(ce.getKey(), ce.getValue());
                        }
                    }
                    tObj.add("costs", costsObj);
                    root.add(entry.getKey(), tObj);
                }

                try (FileWriter writer = new FileWriter(DATA_FILE, StandardCharsets.UTF_8)) {
                    GSON.toJson(root, writer);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void load() {
        if (!DATA_FILE.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(DATA_FILE, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                return;
            }
            JsonObject root = parsed.getAsJsonObject();
            for (String trackerId : root.keySet()) {
                if (!root.get(trackerId).isJsonObject()) continue;
                JsonObject tObj = root.getAsJsonObject(trackerId);
                TrackerBucket p = getPair(trackerId).persistent;
                if (tObj.has("activeSeconds")) {
                    p.activeSeconds = Math.max(0L, tObj.get("activeSeconds").getAsLong());
                }
                if (tObj.has("runsCompleted")) {
                    p.runsCompleted = Math.max(0L, tObj.get("runsCompleted").getAsLong());
                }
                if (tObj.has("items") && tObj.get("items").isJsonObject()) {
                    JsonObject itemsObj = tObj.getAsJsonObject("items");
                    for (String k : itemsObj.keySet()) {
                        long v = itemsObj.get(k).getAsLong();
                        if (v > 0L) p.items.put(k, v);
                    }
                }
                if (tObj.has("costs") && tObj.get("costs").isJsonObject()) {
                    JsonObject costsObj = tObj.getAsJsonObject("costs");
                    for (String k : costsObj.keySet()) {
                        long v = costsObj.get(k).getAsLong();
                        if (v > 0L) p.costs.put(k, v);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
