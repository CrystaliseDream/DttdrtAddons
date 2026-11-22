package com.dttdrt.addons.features.mining;

import com.dttdrt.addons.core.auth.WhitelistManager;
import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.core.feature.Feature;
import com.dttdrt.addons.core.lang.ModLang;
import com.dttdrt.addons.core.log.ModLogManager;
import com.dttdrt.addons.util.NotificationUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Pure client-side Crystal Hollows Temple coordinate waypoint helper (zero packets).
 * Generates a highlight at player position + (29, -32, 48) and supports selectable
 * NEU-style (beacon beam >10m / block box <=10m + 12m clamped label) or
 * Skytils-style (shaded filled bounding box + shadowed 12m clamped label) rendering.
 */
public class TempleWaypointFeature implements Feature {

    public static final String STYLE_NEU = "NEU";
    public static final String STYLE_SKYTILS = "Skytils";

    public static final int OFFSET_X = 29;
    public static final int OFFSET_Y = -32;
    public static final int OFFSET_Z = 48;

    private static final int COLOR_R = 85;
    private static final int COLOR_G = 255;
    private static final int COLOR_B = 85;
    private static final int RGB = (COLOR_R << 16) | (COLOR_G << 8) | COLOR_B;

    private static final double LABEL_CLAMP_DIST = 12.0;
    private static final float LABEL_SCALE = 0.02666667f;
    private static final int LABEL_BG_COLOR = 0x40000000;

    private static volatile BlockPos activeTarget = null;
    private static volatile ClientLevel activeLevel = null;

    private final Matrix4f billboardMatrix = new Matrix4f();

    @Override
    public String getId() {
        return "temple_waypoint";
    }

    @Override
    public String getName() {
        return "Temple Waypoint";
    }

    @Override
    public String getCategory() {
        return "Mining";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void setEnabled(boolean enabled) {
        // Controlled via Generate / Clear actions.
    }

    @Override
    public void init() {
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> renderHighlight());
        LevelRenderEvents.COLLECT_SUBMITS.register(this::renderWaypointLabel);
    }

    @Override
    public void clientTick(Minecraft mc) {
        if (mc == null || mc.level == null || mc.player == null) {
            activeTarget = null;
            activeLevel = null;
            return;
        }
        if (activeLevel != null && mc.level != activeLevel) {
            activeTarget = null;
            activeLevel = null;
        }
    }

    public static void generateFromPlayer() {
        if (!WhitelistManager.isAuthorized()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;

        BlockPos base = mc.player.blockPosition();
        BlockPos target = new BlockPos(
                base.getX() + OFFSET_X,
                base.getY() + OFFSET_Y,
                base.getZ() + OFFSET_Z
        );
        activeTarget = target;
        activeLevel = mc.level;

        String style = getRenderStyle();
        NotificationUtils.sendClientChatMessage(
                Component.literal(ModLang.tr("dttdrtaddons.mining.temple_generated_chat")).withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(target.getX() + ", " + target.getY() + ", " + target.getZ())
                                .withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" [" + style + "]").withStyle(ChatFormatting.GRAY))
        );
        ModLogManager.info("TempleWaypoint", "Generated waypoint at "
                + target.getX() + "," + target.getY() + "," + target.getZ() + " style=" + style);
    }

    public static void clearWaypoint() {
        activeTarget = null;
        activeLevel = null;
    }

    public static BlockPos getActiveTarget() {
        return activeTarget;
    }

    public static String getRenderStyle() {
        String configured = ModConfig.getInstance().templeWaypointRenderStyle;
        return STYLE_SKYTILS.equalsIgnoreCase(configured) ? STYLE_SKYTILS : STYLE_NEU;
    }

    public static void setRenderStyle(String style) {
        ModConfig cfg = ModConfig.getInstance();
        cfg.templeWaypointRenderStyle = STYLE_SKYTILS.equalsIgnoreCase(style) ? STYLE_SKYTILS : STYLE_NEU;
        ModConfig.save();
    }

    private void renderHighlight() {
        if (!WhitelistManager.isAuthorized()) return;
        BlockPos target = activeTarget;
        ClientLevel level = activeLevel;
        if (target == null || level == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.level != level || mc.player == null) return;

        if (STYLE_SKYTILS.equals(getRenderStyle())) {
            renderSkytilsBox(target);
        } else {
            renderNeuBeaconOrBox(mc, level, target);
        }
    }

    private static void renderSkytilsBox(BlockPos target) {
        double tx = target.getX();
        double ty = target.getY();
        double tz = target.getZ();
        Vec3 min = new Vec3(tx - 0.002, ty - 0.002, tz - 0.002);
        Vec3 max = new Vec3(tx + 1.002, ty + 1.002, tz + 1.002);

        int yFaceArgb = (0x80 << 24) | RGB;
        int xFaceArgb = (0x80 << 24)
                | ((int) (COLOR_R * 0.8f) << 16)
                | ((int) (COLOR_G * 0.8f) << 8)
                | (int) (COLOR_B * 0.8f);
        int zFaceArgb = (0x80 << 24)
                | ((int) (COLOR_R * 0.9f) << 16)
                | ((int) (COLOR_G * 0.9f) << 8)
                | (int) (COLOR_B * 0.9f);

        Gizmos.rect(min, max, Direction.DOWN, GizmoStyle.fill(yFaceArgb)).setAlwaysOnTop();
        Gizmos.rect(min, max, Direction.UP, GizmoStyle.fill(yFaceArgb)).setAlwaysOnTop();
        Gizmos.rect(min, max, Direction.WEST, GizmoStyle.fill(xFaceArgb)).setAlwaysOnTop();
        Gizmos.rect(min, max, Direction.EAST, GizmoStyle.fill(xFaceArgb)).setAlwaysOnTop();
        Gizmos.rect(min, max, Direction.NORTH, GizmoStyle.fill(zFaceArgb)).setAlwaysOnTop();
        Gizmos.rect(min, max, Direction.SOUTH, GizmoStyle.fill(zFaceArgb)).setAlwaysOnTop();
    }

    private static void renderNeuBeaconOrBox(Minecraft mc, ClientLevel level, BlockPos target) {
        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null) return;
        Vec3 camPos = camera.position();

        double tx = target.getX();
        double ty = target.getY();
        double tz = target.getZ();

        double dx = (tx + 0.5) - camPos.x;
        double dy = (ty + 0.5) - camPos.y;
        double dz = (tz + 0.5) - camPos.z;
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq > 10.0 * 10.0) {
            float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            double time = level.getGameTime() + partialTick;
            double angle = time * 0.025 * -1.5;

            double cx = tx + 0.5;
            double cz = tz + 0.5;
            double topY = ty + 300.0;

            double[] cornerX = new double[4];
            double[] cornerZ = new double[4];
            for (int i = 0; i < 4; i++) {
                double theta = angle + (Math.PI / 4.0) + i * (Math.PI / 2.0);
                cornerX[i] = cx + Math.cos(theta) * 0.2;
                cornerZ[i] = cz + Math.sin(theta) * 0.2;
            }

            int innerBeamArgb = (200 << 24) | RGB;
            GizmoStyle innerStyle = GizmoStyle.fill(innerBeamArgb);
            for (int i = 0; i < 4; i++) {
                int next = (i + 1) & 3;
                Vec3 botA = new Vec3(cornerX[i], ty, cornerZ[i]);
                Vec3 topA = new Vec3(cornerX[i], topY, cornerZ[i]);
                Vec3 topB = new Vec3(cornerX[next], topY, cornerZ[next]);
                Vec3 botB = new Vec3(cornerX[next], ty, cornerZ[next]);
                Gizmos.rect(botA, topA, topB, botB, innerStyle).setAlwaysOnTop();
            }

            int outerBeamArgb = (64 << 24) | RGB;
            AABB outerColumn = new AABB(tx + 0.2, ty, tz + 0.2, tx + 0.8, topY, tz + 0.8);
            Gizmos.cuboid(outerColumn, GizmoStyle.fill(outerBeamArgb)).setAlwaysOnTop();
        } else {
            AABB box = new AABB(tx, ty, tz, tx + 1.0, ty + 1.0, tz + 1.0).inflate(0.002);
            Gizmos.cuboid(box, GizmoStyle.strokeAndFill((220 << 24) | RGB, 2.0f, (112 << 24) | RGB))
                    .setAlwaysOnTop();
        }
    }

    private void renderWaypointLabel(LevelRenderContext context) {
        if (!WhitelistManager.isAuthorized()) return;
        BlockPos target = activeTarget;
        ClientLevel level = activeLevel;
        if (target == null || level == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.level != level || mc.player == null || mc.options.hideGui) {
            return;
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null) return;

        Vec3 camPos = camera.position();
        double dx = (target.getX() + 0.5) - camPos.x;
        double dy = (target.getY() + 1.0) - camPos.y;
        double dz = (target.getZ() + 0.5) - camPos.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        double rx = dx;
        double ry = dy;
        double rz = dz;
        if (dist > LABEL_CLAMP_DIST && dist > 1.0e-6) {
            double factor = LABEL_CLAMP_DIST / dist;
            rx *= factor;
            ry *= factor;
            rz *= factor;
        }

        boolean isSkytils = STYLE_SKYTILS.equals(getRenderStyle());
        int titleColor = isSkytils ? 0xFFFFFFFF : 0xFF55FF55;
        int distColor = 0xFFFFFF55;

        Font font = mc.font;
        String title = "Temple";
        String distText = Math.round(dist) + "m";
        FormattedCharSequence titleSeq = Component.literal(title).getVisualOrderText();
        FormattedCharSequence distSeq = Component.literal(distText).getVisualOrderText();

        PoseStack poseStack = context.poseStack();
        SubmitNodeCollector collector = context.submitNodeCollector();

        poseStack.pushPose();
        poseStack.translate(rx, ry, rz);
        billboardMatrix.identity()
                .rotateY((float) Math.toRadians(-camera.yRot()))
                .rotateX((float) Math.toRadians(camera.xRot()));
        poseStack.mulPose(billboardMatrix);
        poseStack.scale(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);

        collector.submitText(
                poseStack,
                -font.width(title) * 0.5f,
                0.0f,
                titleSeq,
                isSkytils,
                Font.DisplayMode.SEE_THROUGH,
                LightCoordsUtil.FULL_BRIGHT,
                titleColor,
                LABEL_BG_COLOR,
                0
        );
        collector.submitText(
                poseStack,
                -font.width(distText) * 0.5f,
                10.0f,
                distSeq,
                isSkytils,
                Font.DisplayMode.SEE_THROUGH,
                LightCoordsUtil.FULL_BRIGHT,
                distColor,
                LABEL_BG_COLOR,
                0
        );

        poseStack.popPose();
    }
}
