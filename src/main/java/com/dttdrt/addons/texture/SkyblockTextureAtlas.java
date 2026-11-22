package com.dttdrt.addons.texture;

import com.dttdrt.addons.core.config.ConfigPaths;
import com.dttdrt.addons.gui.GuiBatchHelper;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Dedicated 2048x2048 Pixel-Art Sprite Atlas (FilterMode.NEAREST) for custom Skyblock textures.
 * Automatically packs PNGs from both:
 *   1) mod resources: assets/dttdrtaddons/textures/skyblock/*.png
 *   2) external config folder: config/dttdrtaddons/skyblock_textures/*.png
 * Supports vertical animated sprite strips (height = frames * frameWidth) with zero GPU re-uploads,
 * and batches all Skyblock icons into a single Draw Call via GuiBatchHelper.
 */
public final class SkyblockTextureAtlas {

    private static final Identifier SKYBLOCK_ATLAS_ID =
            Identifier.fromNamespaceAndPath("dttdrtaddons", "skyblock_sprite_atlas");
    private static final int ATLAS_SIZE = 2048;
    private static final int PAD = 1;

    private record AtlasRegion(int px, int py, int w, int frameH, int frameCount) {}

    private static final Map<String, AtlasRegion> REGIONS = new HashMap<>();
    private static boolean initialized = false;
    private static NativeImage atlasImage;
    private static DynamicTexture atlasTexture;
    private static TextureSetup atlasTextureSetup;
    private static int cursorX = PAD;
    private static int cursorY = PAD;
    private static int rowH = 0;

    private SkyblockTextureAtlas() {}

    public static synchronized void ensureInitialized() {
        if (initialized) return;
        initialized = true;
        try {
            atlasImage = new NativeImage(NativeImage.Format.RGBA, ATLAS_SIZE, ATLAS_SIZE, true);
            GpuSampler nearestSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            atlasTexture = new DynamicTexture(() -> "dttdrt_skyblock_atlas", atlasImage) {
                {
                    this.sampler = nearestSampler;
                }
            };
            Minecraft.getInstance().getTextureManager().register(SKYBLOCK_ATLAS_ID, atlasTexture);
            atlasTextureSetup = TextureSetup.singleTexture(atlasTexture.getTextureView(), atlasTexture.getSampler());

            loadBuiltinResources();
            loadExternalConfigFolder();

            if (!REGIONS.isEmpty()) {
                atlasTexture.upload();
            }
        } catch (Throwable ignored) {}
    }

    public static synchronized void reload() {
        initialized = false;
        REGIONS.clear();
        cursorX = PAD;
        cursorY = PAD;
        rowH = 0;
        if (atlasTexture != null) {
            try {
                atlasTexture.close();
            } catch (Throwable ignored) {}
            atlasTexture = null;
            atlasImage = null;
            atlasTextureSetup = null;
        }
        ensureInitialized();
    }

    private static void loadBuiltinResources() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getResourceManager() == null) return;
            Map<Identifier, Resource> resources = mc.getResourceManager()
                    .listResources("textures/skyblock", id -> "dttdrtaddons".equals(id.getNamespace())
                            && id.getPath().toLowerCase(Locale.ROOT).endsWith(".png"));
            for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
                String key = extractItemKey(entry.getKey().getPath());
                if (key == null || REGIONS.containsKey(key)) continue;
                try (InputStream is = entry.getValue().open()) {
                    BufferedImage img = ImageIO.read(is);
                    if (img != null) {
                        packSprite(key, img);
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private static void loadExternalConfigFolder() {
        try {
            File dir = ConfigPaths.getConfigFile("skyblock_textures");
            if (!dir.exists() || !dir.isDirectory()) return;
            scanDirectory(dir);
        } catch (Throwable ignored) {}
    }

    private static void scanDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scanDirectory(f);
            } else if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
                String key = extractItemKey(f.getName());
                if (key == null) continue;
                try {
                    BufferedImage img = ImageIO.read(f);
                    if (img != null) {
                        packSprite(key, img);
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    private static String extractItemKey(String pathOrName) {
        if (pathOrName == null) return null;
        int slash = Math.max(pathOrName.lastIndexOf('/'), pathOrName.lastIndexOf('\\'));
        String base = slash >= 0 ? pathOrName.substring(slash + 1) : pathOrName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        base = base.trim().toUpperCase(Locale.ROOT);
        return base.isEmpty() ? null : base;
    }

    private static void packSprite(String key, BufferedImage img) {
        if (atlasImage == null) return;
        int w = img.getWidth();
        int h = img.getHeight();
        if (w <= 0 || h <= 0 || w > 512 || h > ATLAS_SIZE - PAD * 2) return;

        if (cursorX + w + PAD > ATLAS_SIZE) {
            cursorX = PAD;
            cursorY += rowH + PAD;
            rowH = 0;
        }
        if (cursorY + h + PAD > ATLAS_SIZE) return;

        int dstX = cursorX;
        int dstY = cursorY;
        int[] pixels = img.getRGB(0, 0, w, h, null, 0, w);
        for (int py = 0; py < h; py++) {
            int row = py * w;
            for (int px = 0; px < w; px++) {
                atlasImage.setPixel(dstX + px, dstY + py, pixels[row + px]);
            }
        }

        int frames = (h > w && h % w == 0) ? (h / w) : 1;
        int frameH = frames > 1 ? w : h;
        REGIONS.put(key, new AtlasRegion(dstX, dstY, w, frameH, frames));

        cursorX += w + PAD;
        rowH = Math.max(rowH, h);
    }

    public static boolean hasSprite(String skyblockId) {
        if (skyblockId == null || skyblockId.isEmpty()) return false;
        ensureInitialized();
        return REGIONS.containsKey(skyblockId.toUpperCase(Locale.ROOT));
    }

    public static boolean drawSprite(GuiGraphicsExtractor g, String skyblockId, int x, int y, int size, int tintArgb) {
        if (skyblockId == null || skyblockId.isEmpty() || ((tintArgb >>> 24) & 0xFF) == 0) return false;
        ensureInitialized();
        AtlasRegion r = REGIONS.get(skyblockId.toUpperCase(Locale.ROOT));
        if (r == null) return false;

        int frameIdx = r.frameCount > 1 ? (int) ((System.currentTimeMillis() / 100L) % r.frameCount) : 0;
        int srcY = r.py + frameIdx * r.frameH;

        float inv = 1.0f / ATLAS_SIZE;
        if (atlasTextureSetup != null) {
            GuiBatchHelper.QuadCollector qc = new GuiBatchHelper.QuadCollector(1);
            qc.addRect(x, y, x + size, y + size,
                    r.px * inv, srcY * inv,
                    (r.px + r.w) * inv, (srcY + r.frameH) * inv,
                    tintArgb);
            if (GuiBatchHelper.submitBatch(g, RenderPipelines.GUI_TEXTURED, atlasTextureSetup, qc)) {
                return true;
            }
        }

        g.blit(RenderPipelines.GUI_TEXTURED, SKYBLOCK_ATLAS_ID,
                x, y, (float) r.px, (float) srcY, size, size, r.w, r.frameH, ATLAS_SIZE, ATLAS_SIZE, tintArgb);
        return true;
    }
}
