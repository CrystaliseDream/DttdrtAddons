package com.dttdrt.addons.features.mining;

import com.dttdrt.addons.core.auth.WhitelistManager;
import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.features.mining.CrystalHollowsTracker.CrystalLocation;
import com.dttdrt.addons.features.mining.CrystalHollowsTracker.CustomWaypoint;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Renders:
 * 1) Xaero-style 3D-projected in-world waypoints (through walls) for all discovered
 *    Crystal Hollows structures, Crystal Nucleus, and Wishing Compass targets.
 * 2) Crystal Hollows 2D Minimap HUD with colored structure blocks and rotated player arrow,
 *    using user-configured position (chMapX, chMapY) and scale (chMapScale).
 * Strictly visible ONLY when the map is enabled AND the player is inside Crystal Hollows.
 */
public class CrystalHollowsMapHud {
    private static final Identifier MAP_TEXTURE =
            Identifier.fromNamespaceAndPath("dttdrtaddons", "textures/gui/crystalhollowsmap.png");
    private static final Identifier HUD_ID =
            Identifier.fromNamespaceAndPath("dttdrtaddons", "crystal_hollows_map");

    public static void register() {
        HudElementRegistry.addLast(HUD_ID, (graphics, deltaTracker) -> render(graphics));
    }

    private static void render(GuiGraphicsExtractor graphics) {
        if (!WhitelistManager.isAuthorized()) return;
        Minecraft mc = Minecraft.getInstance();
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.crystalHollowsMapEnabled) return;
        if (!CrystalHollowsTracker.isInCrystalHollows()) return;
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        // Avoid double-rendering when the dedicated HUD Position Editor is open
        if (mc.screen instanceof CrystalHollowsHudEditScreen) return;

        // 1. Xaero-style 3D-projected Waypoints (when no GUI screen is blocking)
        if (mc.screen == null) {
            renderProjectedWaypoints(graphics, mc);
        }

        // 2. 2D Crystal Hollows Minimap HUD
        renderMapAt(graphics, mc, cfg.chMapX, cfg.chMapY, cfg.chMapScale);
    }

    public static void renderMapAt(GuiGraphicsExtractor graphics, Minecraft mc, int mapX, int mapY, float scale) {
        float clampedScale = Math.max(0.08f, Math.min(0.60f, scale));
        int hudSize = Math.round(624 * clampedScale);

        int maxX = Math.max(4, mc.getWindow().getGuiScaledWidth() - hudSize - 4);
        int maxY = Math.max(4, mc.getWindow().getGuiScaledHeight() - hudSize - 4);
        int x = Math.max(4, Math.min(maxX, mapX));
        int y = Math.max(4, Math.min(maxY, mapY));

        graphics.fill(x - 2, y - 2, x + hudSize + 2, y + hudSize + 2, 0x990D1015);
        graphics.outline(x - 2, y - 2, hudSize + 4, hudSize + 4, 0xFF2B313B);

        graphics.pose().pushMatrix();
        graphics.pose().translate((float) x, (float) y);
        graphics.pose().scale(clampedScale, clampedScale);

        // Base Crystal Hollows map texture (624x624)
        graphics.blit(RenderPipelines.GUI_TEXTURED, MAP_TEXTURE, 0, 0, 0f, 0f, 624, 624, 624, 624);

        // Discovered special structure colored blocks
        for (CrystalLocation loc : CrystalLocation.values()) {
            if (!loc.exists()) continue;
            int minX = (int) Math.max(0, Math.round(loc.getMapX() - loc.mapRadius));
            int minZ = (int) Math.max(0, Math.round(loc.getMapZ() - loc.mapRadius));
            int bMaxX = (int) Math.min(624, Math.round(loc.getMapX() + loc.mapRadius));
            int bMaxZ = (int) Math.min(624, Math.round(loc.getMapZ() + loc.mapRadius));

            graphics.fill(minX, minZ, bMaxX, bMaxZ, loc.fillColor);
            graphics.outline(minX, minZ, Math.max(1, bMaxX - minX), Math.max(1, bMaxZ - minZ), loc.borderColor);
        }

        // Ambiguous Wishing Compass marker on 2D map if present
        CustomWaypoint wishing = CrystalHollowsTracker.getActiveWishingWaypoint();
        if (wishing != null) {
            int wx = (int) Math.max(10, Math.min(614, Math.round(wishing.x - 200.0)));
            int wz = (int) Math.max(10, Math.min(614, Math.round(wishing.z - 200.0)));
            graphics.fill(wx - 12, wz - 12, wx + 12, wz + 12, 0x8855FFFF);
            graphics.outline(wx - 12, wz - 12, 24, 24, 0xFF55FFFF);
        }

        // Player position & rotated arrow indicator
        float px = 312f;
        float pz = 312f;
        float yawRad = (float) Math.toRadians(180f);
        if (mc.player != null) {
            px = (float) Math.max(0.0, Math.min(624.0, mc.player.getX() - 202.0));
            pz = (float) Math.max(0.0, Math.min(624.0, mc.player.getZ() - 202.0));
            yawRad = (float) Math.toRadians((mc.player.getYHeadRot() + 180f) % 360f);
        }

        graphics.pose().pushMatrix();
        graphics.pose().translate(px, pz);
        graphics.pose().rotate(yawRad);

        graphics.fill(-11, -11, 11, 11, 0xFF000000);
        graphics.fill(-8, -8, 8, 8, 0xFFFFFFFF);
        graphics.fill(-4, -14, 4, -6, 0xFFFF3333);

        graphics.pose().popMatrix();
        graphics.pose().popMatrix();
    }

    private static void renderProjectedWaypoints(GuiGraphicsExtractor graphics, Minecraft mc) {
        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null) return;

        Vec3 camPos = camera.position();
        Matrix4f viewProj = camera.getViewRotationProjectionMatrix(new Matrix4f());
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        // Always show Crystal Nucleus waypoint
        renderSingleWaypoint(graphics, mc, camPos, viewProj, screenW, screenH,
                "Crystal Nucleus", 513.5, 107.0, 513.5, 0xFFFF55FF);

        // Discovered structure waypoints
        for (CrystalLocation loc : CrystalLocation.values()) {
            if (!loc.exists()) continue;
            renderSingleWaypoint(graphics, mc, camPos, viewProj, screenW, screenH,
                    loc.displayName, loc.getWorldX(), loc.getWorldY() + 2.0, loc.getWorldZ(), loc.borderColor);
        }

        // Ambiguous Wishing Compass waypoint if any
        CustomWaypoint wishing = CrystalHollowsTracker.getActiveWishingWaypoint();
        if (wishing != null) {
            renderSingleWaypoint(graphics, mc, camPos, viewProj, screenW, screenH,
                    wishing.label, wishing.x, wishing.y + 2.0, wishing.z, wishing.color);
        }
    }

    private static void renderSingleWaypoint(
            GuiGraphicsExtractor graphics,
            Minecraft mc,
            Vec3 camPos,
            Matrix4f viewProj,
            int screenW,
            int screenH,
            String name,
            double wx,
            double wy,
            double wz,
            int color
    ) {
        float dx = (float) (wx - camPos.x);
        float dy = (float) (wy - camPos.y);
        float dz = (float) (wz - camPos.z);

        Vector4f clip = new Vector4f(dx, dy, dz, 1.0f).mul(viewProj);
        if (clip.w <= 0.05f) {
            return;
        }

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        if (ndcX < -1.15f || ndcX > 1.15f || ndcY < -1.15f || ndcY > 1.15f) {
            return;
        }

        int sx = Math.round((ndcX * 0.5f + 0.5f) * screenW);
        int sy = Math.round((0.5f - ndcY * 0.5f) * screenH);

        int dist = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
        String label = name + " (" + dist + "m)";

        float textScale = 0.75f;
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) sx, (float) sy);
        graphics.pose().scale(textScale, textScale);
        graphics.centeredText(mc.font, label, 0, -4, 0xFFFFFFFF);
        graphics.pose().popMatrix();
    }
}
