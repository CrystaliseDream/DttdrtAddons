package com.dttdrt.addons.features.storage;

import com.dttdrt.addons.features.mining.profit.MiningItemTable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.ChestMenu;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses an open Minecraft ContainerScreen into either Storage Overview (/storage)
 * or a specific Ender Chest / Backpack page.
 * Direct 1:1 port of Firmament's StorageBackingHandle.
 */
public sealed interface StorageBackingHandle
        permits StorageBackingHandle.Overview, StorageBackingHandle.Page {

    Pattern ENDER_CHEST_NAME = Pattern.compile("^Ender Chest (?:\u2726 )?\\(([1-9])/[1-9]\\)$");
    Pattern BACKPACK_NAME = Pattern.compile("^.+Backpack (?:\u2726 )?\\(Slot #([0-9]+)\\)$");

    ChestMenu handler();

    record Overview(ChestMenu handler) implements StorageBackingHandle {}

    record Page(ChestMenu handler, StoragePageSlot storagePageSlot) implements StorageBackingHandle {}

    static StorageBackingHandle fromScreen(Screen screen) {
        if (!(screen instanceof ContainerScreen cs)) {
            return null;
        }
        String rawTitle = cs.getTitle() != null ? cs.getTitle().getString() : "";
        String title = MiningItemTable.stripFormatting(rawTitle).trim();
        if ("Storage".equals(title)) {
            return new Overview(cs.getMenu());
        }
        Matcher ec = ENDER_CHEST_NAME.matcher(title);
        if (ec.matches()) {
            try {
                int page = Integer.parseInt(ec.group(1));
                return new Page(cs.getMenu(), StoragePageSlot.ofEnderChestPage(page));
            } catch (Exception ignored) {}
        }
        Matcher bp = BACKPACK_NAME.matcher(title);
        if (bp.matches()) {
            try {
                int page = Integer.parseInt(bp.group(1));
                if (page >= 1 && page <= 18) {
                    return new Page(cs.getMenu(), StoragePageSlot.ofBackPackPage(page));
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}
