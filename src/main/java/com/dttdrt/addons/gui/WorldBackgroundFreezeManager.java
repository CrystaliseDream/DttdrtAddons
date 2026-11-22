package com.dttdrt.addons.gui;

import com.dttdrt.addons.core.config.ModConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;

/**
 * Optional 3D World Background Freeze Manager (ModConfig.freezeWorldBackground).
 * When enabled while DttdrtMainScreen is open:
 * - Frame 1 renders the 3D level normally and copies its VRAM color buffer to a reusable GpuTexture.
 * - Subsequent frames skip LevelRenderer.renderLevel and restore the cached world texture via pure GPU copyTextureToTexture (<0.2ms),
 *   so closing/reopening never drops frames and 2D UI animates at maximum frame rate.
 */
public final class WorldBackgroundFreezeManager {

    private static GpuTexture frozenTexture;
    private static int frozenWidth = 0;
    private static int frozenHeight = 0;
    private static boolean capturedReady = false;
    private static ClientLevel lastFrozenLevel = null;

    private WorldBackgroundFreezeManager() {}

    public static void invalidateCapture() {
        capturedReady = false;
    }

    private static boolean isFreezeActive(Minecraft mc) {
        if (mc == null || mc.level == null) return false;
        if (!(mc.screen instanceof DttdrtMainScreen)) return false;
        return ModConfig.getInstance().freezeWorldBackground;
    }

    public static boolean onRenderLevelHead(GameRenderer gr) {
        Minecraft mc = Minecraft.getInstance();
        if (!isFreezeActive(mc)) {
            capturedReady = false;
            lastFrozenLevel = null;
            return false;
        }
        if (mc.level != lastFrozenLevel) {
            capturedReady = false;
            lastFrozenLevel = mc.level;
            return false;
        }

        RenderTarget main = mc.getMainRenderTarget();
        if (main == null) return false;

        GpuTexture mainColor = main.getColorTexture();
        if (mainColor == null || mainColor.isClosed()) return false;

        int w = main.width;
        int h = main.height;
        if (!capturedReady || frozenTexture == null || frozenTexture.isClosed()
                || frozenWidth != w || frozenHeight != h) {
            capturedReady = false;
            return false;
        }

        try {
            GpuDevice device = RenderSystem.getDevice();
            if (device == null) return false;
            CommandEncoder encoder = device.createCommandEncoder();
            encoder.copyTextureToTexture(frozenTexture, mainColor, 0, 0, 0, 0, 0, w, h);
            return true;
        } catch (Throwable ignored) {
            capturedReady = false;
            return false;
        }
    }

    public static void onRenderLevelReturn(GameRenderer gr) {
        Minecraft mc = Minecraft.getInstance();
        if (!isFreezeActive(mc) || capturedReady) return;

        try {
            RenderTarget main = mc.getMainRenderTarget();
            if (main == null) return;
            GpuTexture mainColor = main.getColorTexture();
            if (mainColor == null || mainColor.isClosed()) return;

            int w = main.width;
            int h = main.height;
            if (w <= 0 || h <= 0) return;

            GpuDevice device = RenderSystem.getDevice();
            if (device == null) return;

            if (frozenTexture == null || frozenTexture.isClosed()
                    || frozenWidth != w || frozenHeight != h
                    || frozenTexture.getFormat() != mainColor.getFormat()) {
                if (frozenTexture != null && !frozenTexture.isClosed()) {
                    frozenTexture.close();
                }
                frozenTexture = device.createTexture(
                        "dttdrt_frozen_world_bg",
                        GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING,
                        mainColor.getFormat(),
                        w, h, 1, 1
                );
                frozenWidth = w;
                frozenHeight = h;
            }

            CommandEncoder encoder = device.createCommandEncoder();
            encoder.copyTextureToTexture(mainColor, frozenTexture, 0, 0, 0, 0, 0, w, h);
            lastFrozenLevel = mc.level;
            capturedReady = true;
        } catch (Throwable ignored) {
            capturedReady = false;
        }
    }
}
