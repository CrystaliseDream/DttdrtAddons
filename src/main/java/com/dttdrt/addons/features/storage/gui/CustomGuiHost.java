package com.dttdrt.addons.features.storage.gui;

/**
 * Implemented via Mixin on AbstractContainerScreen to attach/query a CustomGui overlay.
 */
public interface CustomGuiHost {
    CustomGui dttdrt$getCustomGui();
    void dttdrt$setCustomGui(CustomGui gui);
    void dttdrt$clearCustomGuiAndReinit();
}
