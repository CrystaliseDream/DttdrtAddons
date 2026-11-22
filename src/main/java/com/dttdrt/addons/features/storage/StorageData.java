package com.dttdrt.addons.features.storage;

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
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * Persistent storage cache for unlocked Ender Chest & Backpack pages and custom labels.
 * Stored in config/dttdrtaddons/storage-data.json.
 */
public final class StorageData {

    public static final class StorageInventory {
        public final StoragePageSlot slot;
        public volatile VirtualInventory inventory;

        public StorageInventory(StoragePageSlot slot, VirtualInventory inventory) {
            this.slot = slot;
            this.inventory = inventory;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DATA_DIR = ConfigPaths.getConfigDir();
    private static final File DATA_FILE = new File(DATA_DIR, "storage-data.json");

    private static volatile StorageData instance;
    private static final Object SAVE_LOCK = new Object();

    private final TreeMap<StoragePageSlot, StorageInventory> storageInventories = new TreeMap<>();
    private final TreeMap<StoragePageSlot, String> customNames = new TreeMap<>();

    public static StorageData getInstance() {
        if (instance == null) {
            synchronized (StorageData.class) {
                if (instance == null) {
                    instance = load();
                }
            }
        }
        return instance;
    }

    public synchronized TreeMap<StoragePageSlot, StorageInventory> getStorageInventories() {
        return storageInventories;
    }

    public synchronized Map<StoragePageSlot, StorageInventory> snapshotInventories() {
        return new TreeMap<>(storageInventories);
    }

    public synchronized StorageInventory getOrCreate(StoragePageSlot slot) {
        return storageInventories.computeIfAbsent(slot, s -> new StorageInventory(s, null));
    }

    public synchronized void removeSlot(StoragePageSlot slot) {
        storageInventories.remove(slot);
    }

    public synchronized boolean containsSlot(StoragePageSlot slot) {
        return storageInventories.containsKey(slot);
    }

    public synchronized String displayName(StoragePageSlot slot) {
        String custom = customNames.get(slot);
        return (custom != null && !custom.isEmpty()) ? custom : slot.defaultName();
    }

    public synchronized void setCustomName(StoragePageSlot slot, String name) {
        if (name == null || name.isBlank()) {
            customNames.remove(slot);
        } else {
            customNames.put(slot, name.trim());
        }
        markDirtyAsync();
    }

    public void markDirtyAsync() {
        CompletableFuture.runAsync(this::saveSync);
    }

    public void saveSync() {
        synchronized (SAVE_LOCK) {
            try {
                if (!DATA_DIR.exists()) {
                    DATA_DIR.mkdirs();
                }
                JsonObject root = new JsonObject();
                JsonObject invs = new JsonObject();
                JsonObject names = new JsonObject();
                Map<StoragePageSlot, StorageInventory> snapInv;
                Map<StoragePageSlot, String> snapNames;
                synchronized (this) {
                    snapInv = new TreeMap<>(storageInventories);
                    snapNames = new TreeMap<>(customNames);
                }
                for (Map.Entry<StoragePageSlot, StorageInventory> entry : snapInv.entrySet()) {
                    String key = String.valueOf(entry.getKey().index());
                    VirtualInventory vi = entry.getValue().inventory;
                    if (vi == null) {
                        invs.add(key, null);
                    } else {
                        String b64 = vi.serializeBlocking();
                        if (b64.isEmpty()) {
                            invs.add(key, null);
                        } else {
                            invs.addProperty(key, b64);
                        }
                    }
                }
                for (Map.Entry<StoragePageSlot, String> entry : snapNames.entrySet()) {
                    names.addProperty(String.valueOf(entry.getKey().index()), entry.getValue());
                }
                root.add("storageInventories", invs);
                root.add("customNames", names);
                try (FileWriter writer = new FileWriter(DATA_FILE, StandardCharsets.UTF_8)) {
                    GSON.toJson(root, writer);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static StorageData load() {
        StorageData data = new StorageData();
        if (!DATA_FILE.exists()) {
            return data;
        }
        try (FileReader reader = new FileReader(DATA_FILE, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) return data;
            JsonObject root = parsed.getAsJsonObject();
            if (root.has("storageInventories") && root.get("storageInventories").isJsonObject()) {
                JsonObject invs = root.getAsJsonObject("storageInventories");
                for (Map.Entry<String, JsonElement> entry : invs.entrySet()) {
                    try {
                        int idx = Integer.parseInt(entry.getKey());
                        if (idx < 0 || idx >= StoragePageSlot.TOTAL_SLOTS) continue;
                        StoragePageSlot slot = new StoragePageSlot(idx);
                        VirtualInventory vi = entry.getValue().isJsonPrimitive()
                                ? VirtualInventory.deserialize(entry.getValue().getAsString())
                                : null;
                        data.storageInventories.put(slot, new StorageInventory(slot, vi));
                    } catch (Exception ignored) {
                    }
                }
            }
            if (root.has("customNames") && root.get("customNames").isJsonObject()) {
                JsonObject names = root.getAsJsonObject("customNames");
                for (Map.Entry<String, JsonElement> entry : names.entrySet()) {
                    try {
                        int idx = Integer.parseInt(entry.getKey());
                        if (idx < 0 || idx >= StoragePageSlot.TOTAL_SLOTS) continue;
                        if (entry.getValue().isJsonPrimitive()) {
                            data.customNames.put(new StoragePageSlot(idx), entry.getValue().getAsString());
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return data;
    }
}
