package com.dttdrt.addons.core.feature;

import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.features.market.MarketState;
import com.dttdrt.addons.features.mining.TempleWaypointFeature;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FeatureManager {
    private static final List<Feature> FEATURES = new ArrayList<>();

    public static void register(Feature feature) {
        FEATURES.add(feature);
        feature.init();
    }

    public static List<Feature> getFeatures() {
        return Collections.unmodifiableList(FEATURES);
    }

    public static void tickAll(Minecraft client) {
        for (Feature f : FEATURES) {
            if (f.isEnabled()) {
                try {
                    f.clientTick(client);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void disableAllFeatures() {
        for (Feature f : FEATURES) {
            try {
                f.setEnabled(false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        TempleWaypointFeature.clearWaypoint();
        MarketState.getInstance().setAlertEnabled(false);
        ModConfig cfg = ModConfig.getInstance();
        cfg.alertEnabled = false;
        cfg.mineshaftStreakEnabled = false;
        cfg.storageOverlayEnabled = false;
        cfg.nametagEnabled = false;
        cfg.miningProfitEnabled = false;
        cfg.scathaProfitEnabled = false;
        cfg.nucleusProfitEnabled = false;
        cfg.gemstoneEspEnabled = false;
        cfg.corpseEspEnabled = false;
        cfg.littleFootEspEnabled = false;
        cfg.crystalHollowsMapEnabled = false;
        cfg.partyVoteEnabled = false;
        cfg.damageTrackerEnabled = false;
        cfg.customNameEnabled = false;
        ModConfig.save();
    }
}

