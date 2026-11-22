package com.dttdrt.addons.features.freecam;

import com.dttdrt.addons.core.auth.WhitelistManager;
import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.core.feature.Feature;
import com.dttdrt.addons.core.lang.ModLang;
import com.dttdrt.addons.core.log.ModLogManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Pure client-side detached FreeCam.
 * Never modifies player position, velocity, ground state, or rotation,
 * and never sends, cancels, or tampers with network packets.
 */
public class FreeCamFeature implements Feature {

    private static final double BASE_BLOCKS_PER_SEC = 18.0;
    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("dttdrtaddons", "freecam_hud");

    private static FreeCamFeature instance;

    private volatile boolean active = false;
    private double camX = 0.0;
    private double camY = 0.0;
    private double camZ = 0.0;
    private float camYaw = 0.0f;
    private float camPitch = 0.0f;
    private long lastFrameNs = 0L;

    private ClientLevel entryLevel = null;
    private boolean prevSmartCull = true;
    private boolean smartCullOverridden = false;
    private boolean firstFrameLogged = false;

    public FreeCamFeature() {
        instance = this;
    }

    public static FreeCamFeature getInstance() {
        return instance;
    }

    @Override
    public String getId() {
        return "freecam";
    }

    @Override
    public String getName() {
        return "FreeCam";
    }

    @Override
    public String getCategory() {
        return "Custom";
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public void setEnabled(boolean enabled) {
        Minecraft mc = Minecraft.getInstance();
        if (enabled) {
            enable(mc, "setEnabled");
        } else {
            disable(mc, "setEnabled");
        }
    }

    @Override
    public void init() {
        HudElementRegistry.addLast(HUD_ID, (graphics, deltaTracker) -> renderHud(graphics));
    }

    @Override
    public void clientTick(Minecraft mc) {
        if (!active) {
            return;
        }
        if (mc == null || mc.level == null || mc.player == null) {
            disable(mc, "tick_null_world_or_player");
            return;
        }
        if (mc.level != entryLevel) {
            disable(mc, "world_changed");
            return;
        }
        if (!WhitelistManager.isAuthorized()) {
            disable(mc, "inactive");
        }
    }

    public static boolean isActive() {
        FreeCamFeature fc = instance;
        if (fc == null || !fc.active || !WhitelistManager.isAuthorized()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null && mc.player != null;
    }

    public static void toggleIfPossible(Minecraft mc) {
        toggleIfPossible(mc, "keybind");
    }

    public static void toggleIfPossible(Minecraft mc, String trigger) {
        if (instance == null) {
            ModLogManager.warn("FreeCam", "Toggle ignored (" + trigger + "): feature instance not initialized.");
            return;
        }
        if (!WhitelistManager.isAuthorized()) {
            return;
        }
        if (mc == null || mc.level == null || mc.player == null) {
            ModLogManager.warn("FreeCam", "Toggle ignored (" + trigger + "): player not in a world.");
            return;
        }
        if (instance.active) {
            instance.disable(mc, trigger);
        } else {
            instance.enable(mc, trigger);
        }
    }

    public static void disableIfActive() {
        disableIfActive("external");
    }

    public static void disableIfActive(String reason) {
        if (instance != null && instance.active) {
            instance.disable(Minecraft.getInstance(), reason);
        }
    }

    public synchronized void enable(Minecraft mc) {
        enable(mc, "enable");
    }

    public synchronized void enable(Minecraft mc, String trigger) {
        if (mc == null || mc.level == null || mc.player == null || !WhitelistManager.isAuthorized()) {
            return;
        }
        Camera camera = mc.gameRenderer != null ? mc.gameRenderer.getMainCamera() : null;
        if (camera != null && camera.isInitialized()) {
            Vec3 pos = camera.position();
            this.camX = pos.x;
            this.camY = pos.y;
            this.camZ = pos.z;
            this.camYaw = camera.yRot();
            this.camPitch = Mth.clamp(camera.xRot(), -90.0f, 90.0f);
        } else {
            Vec3 eye = mc.player.getEyePosition();
            this.camX = eye.x;
            this.camY = eye.y;
            this.camZ = eye.z;
            this.camYaw = mc.player.getYRot();
            this.camPitch = Mth.clamp(mc.player.getXRot(), -90.0f, 90.0f);
        }

        this.entryLevel = mc.level;
        if (!this.smartCullOverridden) {
            this.prevSmartCull = mc.smartCull;
            this.smartCullOverridden = true;
        }
        mc.smartCull = false;
        this.lastFrameNs = System.nanoTime();
        this.firstFrameLogged = false;
        this.active = true;
        ModLogManager.info("FreeCam", String.format(Locale.ROOT,
                "Enabled (%s) at (%.2f, %.2f, %.2f) yaw=%.1f pitch=%.1f speed=%.2fx prevSmartCull=%s",
                trigger, this.camX, this.camY, this.camZ, this.camYaw, this.camPitch,
                ModConfig.getInstance().freecamSpeed, this.prevSmartCull));
    }

    public synchronized void disable(Minecraft mc) {
        disable(mc, "disable");
    }

    public synchronized void disable(Minecraft mc, String reason) {
        boolean wasActive = this.active;
        this.active = false;
        this.entryLevel = null;
        this.lastFrameNs = 0L;
        if (this.smartCullOverridden) {
            if (mc != null) {
                mc.smartCull = this.prevSmartCull;
            }
            this.smartCullOverridden = false;
        }
        if (wasActive) {
            ModLogManager.info("FreeCam", "Disabled (reason=" + reason + ")");
        }
    }

    public void updateCameraPositionFrame() {
        if (!active) {
            return;
        }
        if (!firstFrameLogged) {
            firstFrameLogged = true;
            ModLogManager.info("FreeCam", "Camera.alignWithEntity mixin applied on render frame.");
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) {
            disable(mc, "frame_null_world");
            return;
        }

        long now = System.nanoTime();
        double dt = lastFrameNs == 0L ? 0.0 : (now - lastFrameNs) / 1_000_000_000.0;
        lastFrameNs = now;
        if (dt <= 0.0 || dt > 0.05) {
            dt = Math.min(0.05, Math.max(0.0, dt));
        }

        if (mc.screen != null || mc.isPaused()) {
            return;
        }

        double forward = 0.0;
        if (mc.options.keyUp.isDown()) forward += 1.0;
        if (mc.options.keyDown.isDown()) forward -= 1.0;

        double strafe = 0.0;
        if (mc.options.keyRight.isDown()) strafe += 1.0;
        if (mc.options.keyLeft.isDown()) strafe -= 1.0;

        double vertical = 0.0;
        if (mc.options.keyJump.isDown()) vertical += 1.0;
        if (mc.options.keyShift.isDown()) vertical -= 1.0;

        double horizLen = Math.hypot(forward, strafe);
        if (horizLen > 1.0) {
            forward /= horizLen;
            strafe /= horizLen;
        }

        if (forward == 0.0 && strafe == 0.0 && vertical == 0.0) {
            return;
        }

        ModConfig cfg = ModConfig.getInstance();
        double speedMul = Mth.clamp(cfg.freecamSpeed, 0.25f, 6.0f);
        if (mc.options.keySprint.isDown()) {
            speedMul *= 2.0;
        }
        double step = BASE_BLOCKS_PER_SEC * speedMul * dt;

        double yawRad = Math.toRadians(camYaw);
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        double rx = -Math.cos(yawRad);
        double rz = -Math.sin(yawRad);

        camX += (fx * forward + rx * strafe) * step;
        camY += vertical * step;
        camZ += (fz * forward + rz * strafe) * step;
    }

    public void turn(double yDelta, double xDelta) {
        camPitch = Mth.clamp(camPitch + (float) xDelta * 0.15f, -90.0f, 90.0f);
        camYaw = Mth.wrapDegrees(camYaw + (float) yDelta * 0.15f);
    }

    public void adjustSpeed(double scrollDelta) {
        if (scrollDelta == 0.0) {
            return;
        }
        ModConfig cfg = ModConfig.getInstance();
        float next = cfg.freecamSpeed + (float) (Math.signum(scrollDelta) * 0.25);
        cfg.freecamSpeed = Math.round(Mth.clamp(next, 0.25f, 6.0f) * 100.0f) / 100.0f;
    }

    public double getCamX() {
        return camX;
    }

    public double getCamY() {
        return camY;
    }

    public double getCamZ() {
        return camZ;
    }

    public float getCamYaw() {
        return camYaw;
    }

    public float getCamPitch() {
        return camPitch;
    }

    private void renderHud(GuiGraphicsExtractor g) {
        if (!isActive()) {
            return;
        }
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.freecamShowHud) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options.hideGui || mc.screen != null) {
            return;
        }

        int screenW = mc.getWindow().getGuiScaledWidth();
        String label = ModLang.tr("dttdrtaddons.hud.freecam", cfg.freecamSpeed);
        int textW = mc.font.width(label);
        int pillW = textW + 18;
        int pillH = 16;
        int x1 = (screenW - pillW) / 2;
        int y1 = 8;
        int accent = (cfg.drawerTopLineColor & 0x00FFFFFF) | 0xFF000000;

        g.fill(x1, y1, x1 + pillW, y1 + pillH, 0xB0181620);
        g.fill(x1, y1, x1 + pillW, y1 + 1, accent);
        g.centeredText(mc.font, label, screenW / 2, y1 + 4, 0xFFF3F4F6);
    }
}
