package com.dttdrt.addons.features.storage;

import com.dttdrt.addons.core.auth.WhitelistManager;
import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.features.storage.gui.CustomGui;
import com.dttdrt.addons.features.storage.gui.CustomGuiHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Storage (Ender Chest & Backpack) Preview Overlay manager.
 * Direct 1:1 port of Firmament's StorageOverlay state machine and caching logic.
 */
public final class StorageOverlayManager {

    public static StorageOverviewScreen lastStorageOverview = null;
    private static boolean skipNextStorageOverlayBackflip = false;
    private static StorageBackingHandle currentHandler = null;

    private StorageOverlayManager() {}

    public static boolean isActive() {
        return WhitelistManager.isAuthorized() && ModConfig.getInstance().storageOverlayEnabled;
    }

    public static double adjustScrollSpeed(double amount) {
        ModConfig cfg = ModConfig.getInstance();
        int speed = Math.max(1, Math.min(50, cfg.storageOverlayScrollSpeed));
        return amount * speed * (cfg.storageOverlayInverseScroll ? 1 : -1);
    }

    public static void onChestContentUpdate() {
        if (!isActive()) return;
        rememberContent(currentHandler);
    }

    public static void onSlotClick(Slot slot, ItemStack stackSnapshot) {
        if (!isActive()) return;
        if (lastStorageOverview != null
                && slot != null
                && !(slot.container instanceof Inventory)
                && slot.getContainerSlot() < 9
                && (stackSnapshot == null || !stackSnapshot.is(Blocks.BLACK_STAINED_GLASS_PANE.asItem()))) {
            skipNextStorageOverlayBackflip = true;
        }
    }

    public static Screen onScreenChange(Screen oldScreen, Screen newScreen) {
        if (oldScreen == null && newScreen == null) {
            return null;
        }
        if (!isActive()) {
            currentHandler = null;
            lastStorageOverview = null;
            skipNextStorageOverlayBackflip = false;
            return null;
        }

        StorageOverlayScreen retainedOverlay = null;
        if (oldScreen instanceof StorageOverlayScreen sos) {
            retainedOverlay = sos;
        } else if (oldScreen instanceof CustomGuiHost host
                && host.dttdrt$getCustomGui() instanceof StorageOverlayCustom soc) {
            retainedOverlay = soc.getOverview();
        }

        StorageOverviewScreen storageOverviewScreen = (oldScreen instanceof StorageOverviewScreen ov)
                ? ov
                : lastStorageOverview;
        ContainerScreen containerScreen = (newScreen instanceof ContainerScreen cs) ? cs : null;

        rememberContent(currentHandler);
        StorageBackingHandle oldHandler = currentHandler;
        currentHandler = StorageBackingHandle.fromScreen(containerScreen);

        if (oldScreen instanceof StorageOverviewScreen && oldHandler != null) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc != null ? mc.player : null;
            if (player != null && player.connection != null) {
                player.connection.send(new ServerboundContainerClosePacket(oldHandler.handler().containerId));
                if (player.containerMenu == oldHandler.handler()) {
                    player.containerMenu = player.inventoryMenu;
                }
            }
        }

        if (newScreen == null && storageOverviewScreen != null && !storageOverviewScreen.isClosing()) {
            // Re-open standalone overview if backing screen was replaced cleanly
            return storageOverviewScreen;
        }

        if (storageOverviewScreen != null
                && !storageOverviewScreen.isClosing()
                && (currentHandler instanceof StorageBackingHandle.Overview || currentHandler == null)) {
            if (skipNextStorageOverlayBackflip) {
                skipNextStorageOverlayBackflip = false;
            } else {
                lastStorageOverview = null;
                return storageOverviewScreen;
            }
            return null;
        }

        if (containerScreen == null || currentHandler == null) {
            return null;
        }
        if (retainedOverlay != null && retainedOverlay.isExiting()) {
            return null;
        }

        if (containerScreen instanceof CustomGuiHost host) {
            StorageOverlayScreen overlay = retainedOverlay != null ? retainedOverlay : new StorageOverlayScreen();
            host.dttdrt$setCustomGui(new StorageOverlayCustom(currentHandler, containerScreen, overlay));
        }
        return null;
    }

    public static void rememberContent(StorageBackingHandle handler) {
        if (handler == null) return;
        StorageData data = StorageData.getInstance();
        if (handler instanceof StorageBackingHandle.Overview overview) {
            rememberStorageOverview(overview, data);
        } else if (handler instanceof StorageBackingHandle.Page page) {
            rememberPage(page, data);
        }
    }

    private static void rememberStorageOverview(StorageBackingHandle.Overview handler, StorageData data) {
        var items = handler.handler().getItems();
        for (int index = 0; index < items.size(); index++) {
            ItemStack stack = items.get(index);
            if (stack == null || stack.isEmpty()) continue;
            StoragePageSlot slot = StoragePageSlot.fromOverviewSlotIndex(index);
            if (slot == null) continue;
            boolean isEmpty = StorageOverviewScreen.EMPTY_STORAGE_SLOT_ITEMS.contains(stack.getItem());
            if (data.containsSlot(slot)) {
                if (isEmpty) {
                    data.removeSlot(slot);
                }
                continue;
            }
            if (!isEmpty) {
                data.getOrCreate(slot);
            }
        }
        data.markDirtyAsync();
    }

    private static void rememberPage(StorageBackingHandle.Page handler, StorageData data) {
        int totalPageSlots = handler.handler().getRowCount() * 9;
        var items = handler.handler().getItems();
        int limit = Math.min(items.size(), totalPageSlots);
        if (limit <= 9) return;
        List<ItemStack> pageItems = new ArrayList<>(limit - 9);
        for (int i = 9; i < limit; i++) {
            ItemStack s = items.get(i);
            pageItems.add(s == null ? ItemStack.EMPTY : s.copy());
        }
        if (pageItems.isEmpty() || pageItems.size() % 9 != 0) return;
        VirtualInventory vi = new VirtualInventory(pageItems);
        StorageData.StorageInventory entry = data.getOrCreate(handler.storagePageSlot());
        entry.inventory = vi;
        data.markDirtyAsync();
    }

    public static CustomGui getActiveCustomGui(Object screen) {
        if (screen instanceof CustomGuiHost host) {
            return host.dttdrt$getCustomGui();
        }
        return null;
    }
}
