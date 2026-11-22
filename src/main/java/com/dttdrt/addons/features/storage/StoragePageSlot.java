package com.dttdrt.addons.features.storage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * Represents one SkyBlock storage page slot (0..8 = Ender Chest #1..9, 9..26 = Backpack #1..18).
 * Direct 1:1 port of Firmament's StoragePageSlot.
 */
public record StoragePageSlot(int index) implements Comparable<StoragePageSlot> {

    public static final int ENDER_CHEST_COUNT = 9;
    public static final int TOTAL_SLOTS = 27;

    public StoragePageSlot {
        if (index < 0 || index >= TOTAL_SLOTS) {
            throw new IllegalArgumentException("StoragePageSlot index out of range: " + index);
        }
    }

    public boolean isEnderChest() {
        return index < ENDER_CHEST_COUNT;
    }

    public boolean isBackPack() {
        return !isEnderChest();
    }

    public int slotIndexInOverviewPage() {
        return isEnderChest() ? index + 9 : index + 18;
    }

    public String defaultName() {
        return isEnderChest()
                ? "Ender Chest #" + (index + 1)
                : "Backpack #" + (index - ENDER_CHEST_COUNT + 1);
    }

    public void navigateTo() {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener conn = mc != null ? mc.getConnection() : null;
        if (conn == null) return;
        if (isBackPack()) {
            conn.sendCommand("backpack " + (index - ENDER_CHEST_COUNT + 1));
        } else {
            conn.sendCommand("enderchest " + (index + 1));
        }
    }

    public static StoragePageSlot fromOverviewSlotIndex(int slot) {
        if (slot >= 9 && slot < 18) {
            return new StoragePageSlot(slot - 9);
        }
        if (slot >= 27 && slot < 45) {
            return new StoragePageSlot(slot - 27 + ENDER_CHEST_COUNT);
        }
        return null;
    }

    public static StoragePageSlot ofEnderChestPage(int pageOneBased) {
        if (pageOneBased < 1 || pageOneBased > 9) {
            throw new IllegalArgumentException("Ender chest page out of range: " + pageOneBased);
        }
        return new StoragePageSlot(pageOneBased - 1);
    }

    public static StoragePageSlot ofBackPackPage(int pageOneBased) {
        if (pageOneBased < 1 || pageOneBased > 18) {
            throw new IllegalArgumentException("Backpack page out of range: " + pageOneBased);
        }
        return new StoragePageSlot(pageOneBased - 1 + ENDER_CHEST_COUNT);
    }

    @Override
    public int compareTo(StoragePageSlot other) {
        return Integer.compare(this.index, other.index);
    }
}
