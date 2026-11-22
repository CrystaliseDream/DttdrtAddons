package com.dttdrt.addons.features.mining;

import com.dttdrt.addons.core.auth.WhitelistManager;
import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.core.feature.Feature;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

/**
 * Modular Crystal Hollows Map & Wishing Compass feature.
 */
public class CrystalHollowsFeature implements Feature {
    @Override
    public String getId() {
        return "crystal_hollows_map";
    }

    @Override
    public String getName() {
        return "crystal hollows map";
    }

    @Override
    public String getCategory() {
        return "crystal hollows map";
    }

    @Override
    public void init() {
        CrystalHollowsMapHud.register();

        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (WhitelistManager.isAuthorized() && level.isClientSide() && CrystalHollowsTracker.isInCrystalHollows()) {
                ItemStack stack = player.getItemInHand(hand);
                if (!stack.isEmpty()) {
                    String name = CrystalHollowsTracker.stripColors(stack.getHoverName().getString());
                    if (name.contains("Wishing Compass")) {
                        CrystalWishingCompassSolver.getInstance()
                                .onWishingCompassUsed(Minecraft.getInstance(), player.blockPosition());
                    }
                }
            }
            return InteractionResult.PASS;
        });
    }

    @Override
    public void clientTick(Minecraft client) {
        // CrystalHollowsTracker.tick is always driven in DttdrtAddons END_CLIENT_TICK so Mineshaft/Gemstone ESPs work even if Map HUD is disabled
    }

    @Override
    public boolean isEnabled() {
        return ModConfig.getInstance().crystalHollowsMapEnabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        ModConfig.getInstance().crystalHollowsMapEnabled = enabled;
        ModConfig.save();
    }
}
