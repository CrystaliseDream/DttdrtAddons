package com.dttdrt.addons.core.feature;

import net.minecraft.client.Minecraft;

/**
 * Base contract for pluggable modular features in DttdrtAddons.
 * Allows effortless addition of new in-game features without cluttering existing code.
 */
public interface Feature {
    String getId();
    String getName();
    String getCategory();

    void init();
    void clientTick(Minecraft client);

    boolean isEnabled();
    void setEnabled(boolean enabled);
}

