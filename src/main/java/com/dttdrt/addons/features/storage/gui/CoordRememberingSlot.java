package com.dttdrt.addons.features.storage.gui;

/**
 * Implemented via Mixin on net.minecraft.world.inventory.Slot so custom GUIs can relocate slots
 * and restore original vanilla coordinates cleanly if the custom overlay is turned off.
 */
public interface CoordRememberingSlot {
    void dttdrt$rememberCoords();
    void dttdrt$restoreCoords();
    void dttdrt$setCoords(int x, int y);
}
