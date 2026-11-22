package com.dttdrt.addons.features.market;

import com.dttdrt.addons.core.feature.Feature;
import net.minecraft.client.Minecraft;

public class MarketMonitorFeature implements Feature {
    private boolean enabled = true;

    @Override
    public String getId() {
        return "market_monitor";
    }

    @Override
    public String getName() {
        return "ah/bz";
    }

    @Override
    public String getCategory() {
        return "ah/bz";
    }

    @Override
    public void init() {
        // Pre-load state singleton
        MarketState.getInstance();
    }

    @Override
    public void clientTick(Minecraft client) {
        if (!enabled) return;
        MarketAlertService.tick(client);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}

