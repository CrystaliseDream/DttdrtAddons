package com.dttdrt.addons.features.nametag;

import com.dttdrt.addons.core.auth.WhitelistManager;
import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.core.feature.Feature;
import com.dttdrt.addons.mixin.accessor.AvatarRendererInvoker;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Custom SEE_THROUGH nametag renderer for other players with optional distance
 * indicator and configurable palette color.
 */
public class NametagFeature implements Feature {

    private static final int COLOR_CYAN = 0xFF00FFFF;
    private static final float BASE_SCALE = 0.03f;

    private static final ThreadLocal<Boolean> BYPASS_SHOULD_SHOW_NAME = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private record CachedName(String rawText, FormattedCharSequence plainSeq) {}
    private static final Map<UUID, CachedName> NAME_CACHE = new HashMap<>();

    private static boolean shouldRenderLookupDone = false;
    private static Method shouldRenderMethod = null;
    private static int shouldRenderArgCount = 0;

    private final Matrix4f billboardMatrix = new Matrix4f();

    @Override
    public String getId() {
        return "nametag";
    }

    @Override
    public String getName() {
        return "Nametag";
    }

    @Override
    public String getCategory() {
        return "Custom";
    }

    @Override
    public boolean isEnabled() {
        return ModConfig.getInstance().nametagEnabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        ModConfig.getInstance().nametagEnabled = enabled;
        ModConfig.save();
    }

    @Override
    public void init() {
        LevelRenderEvents.COLLECT_SUBMITS.register(this::renderCustomNametags);
    }

    @Override
    public void clientTick(Minecraft mc) {
        if (!isEnabledStatic() || mc == null || mc.level == null) {
            if (mc == null || mc.level == null) {
                NAME_CACHE.clear();
            }
            return;
        }

        if (!NAME_CACHE.isEmpty() && (mc.level.getGameTime() % 40L) == 0L) {
            Iterator<UUID> it = NAME_CACHE.keySet().iterator();
            while (it.hasNext()) {
                if (mc.level.getPlayerByUUID(it.next()) == null) {
                    it.remove();
                }
            }
        }
    }

    public static boolean isEnabledStatic() {
        return WhitelistManager.isAuthorized() && ModConfig.getInstance().nametagEnabled;
    }

    public static boolean isBypassingShouldShowName() {
        return Boolean.TRUE.equals(BYPASS_SHOULD_SHOW_NAME.get());
    }

    public static boolean isNpc(Player player, Minecraft mc) {
        return player != mc.player
                && player.getUUID().version() == 2
                && player.getHealth() == 20.0f;
    }

    public static boolean shouldSuppressVanillaNametag(Player player) {
        if (player == null || !isEnabledStatic()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        return mc != null && player != mc.player && !isNpc(player, mc);
    }

    private void renderCustomNametags(LevelRenderContext context) {
        if (!isEnabledStatic()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) {
            return;
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null) {
            return;
        }

        Vec3 camPos = camera.position();
        Frustum frustum = camera.getCullFrustum();
        float cameraYaw = camera.yRot();
        float cameraPitch = camera.xRot();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        long currentTime = System.currentTimeMillis();
        double cachedSinValue = Math.sin(currentTime * 0.001) * 0.08;

        ModConfig cfg = ModConfig.getInstance();
        PoseStack poseStack = context.poseStack();
        SubmitNodeCollector collector = context.submitNodeCollector();
        Font font = mc.font;

        for (AbstractClientPlayer player : mc.level.players()) {
            if (!shouldRenderCustomNametag(player, mc, frustum, camPos, partialTick)) {
                continue;
            }
            renderPlayerNametag(
                    poseStack,
                    collector,
                    font,
                    player,
                    cfg,
                    camPos,
                    cameraYaw,
                    cameraPitch,
                    partialTick,
                    cachedSinValue
            );
        }
    }

    private boolean shouldRenderCustomNametag(
            Player player,
            Minecraft mc,
            Frustum frustum,
            Vec3 camPos,
            float partialTick
    ) {
        if (player == null || player == mc.player || !player.isAlive() || isNpc(player, mc)) {
            return false;
        }

        double dx = Mth.lerp(partialTick, player.xo, player.getX()) - camPos.x;
        double dy = Mth.lerp(partialTick, player.yo, player.getY()) - camPos.y;
        double dz = Mth.lerp(partialTick, player.zo, player.getZ()) - camPos.z;
        double distSq = dx * dx + dy * dy + dz * dz;

        if (!wouldVanillaShowName(player, mc, distSq)) {
            return false;
        }
        return wouldEntityRender(player, mc, frustum, camPos, partialTick);
    }

    private boolean wouldVanillaShowName(Player player, Minecraft mc, double distSq) {
        if (player instanceof Avatar avatar) {
            EntityRenderer<? super Player, ?> renderer = mc.getEntityRenderDispatcher().getRenderer(player);
            if (renderer instanceof AvatarRendererInvoker invoker) {
                BYPASS_SHOULD_SHOW_NAME.set(Boolean.TRUE);
                try {
                    return invoker.dttdrt_invokeShouldShowName(avatar, distSq);
                } finally {
                    BYPASS_SHOULD_SHOW_NAME.set(Boolean.FALSE);
                }
            }
        }
        return !player.isInvisibleTo(mc.player);
    }

    private boolean wouldEntityRender(Player player, Minecraft mc, Frustum frustum, Vec3 camPos, float partialTick) {
        if (frustum == null) {
            return true;
        }
        if (!shouldRenderLookupDone) {
            shouldRenderLookupDone = true;
            for (Method m : EntityRenderDispatcher.class.getMethods()) {
                if ("shouldRender".equals(m.getName())) {
                    int count = m.getParameterCount();
                    if (count == 5 || count == 6) {
                        shouldRenderMethod = m;
                        shouldRenderArgCount = count;
                        break;
                    }
                }
            }
        }
        if (shouldRenderMethod != null) {
            try {
                if (shouldRenderArgCount == 6) {
                    return (boolean) shouldRenderMethod.invoke(
                            mc.getEntityRenderDispatcher(),
                            player,
                            frustum,
                            camPos.x,
                            camPos.y,
                            camPos.z,
                            partialTick
                    );
                } else {
                    return (boolean) shouldRenderMethod.invoke(
                            mc.getEntityRenderDispatcher(),
                            player,
                            frustum,
                            camPos.x,
                            camPos.y,
                            camPos.z
                    );
                }
            } catch (Throwable ignored) {
            }
        }
        return frustum.isVisible(player.getBoundingBox());
    }

    private void renderPlayerNametag(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            Font font,
            Player player,
            ModConfig cfg,
            Vec3 camPos,
            float cameraYaw,
            float cameraPitch,
            float partialTick,
            double cachedSinValue
    ) {
        double px = Mth.lerp(partialTick, player.xo, player.getX());
        double py = Mth.lerp(partialTick, player.yo, player.getY());
        double pz = Mth.lerp(partialTick, player.zo, player.getZ());

        double heightOffset = player.getBbHeight() + 0.5f;
        double dx = px - camPos.x;
        double dy = (py + heightOffset) - camPos.y;
        double dz = pz - camPos.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        float animatedScale = (float) (BASE_SCALE * (1.0 + cachedSinValue * 0.3));

        CachedName cached = getCachedName(player);
        FormattedCharSequence nameSeq = cached.plainSeq;
        int nameColor = cfg.nametagColor | 0xFF000000;

        int nameWidth = font.width(nameSeq);
        String distText = cfg.nametagShowDistance ? (" [" + Math.round(distance) + "]") : null;

        float nameX;
        float distX = 0.0f;
        if (distText != null) {
            int distWidth = font.width(distText);
            float totalWidth = nameWidth + 2.0f + distWidth;
            nameX = -totalWidth * 0.5f;
            distX = nameX + nameWidth + 2.0f;
        } else {
            nameX = -nameWidth * 0.5f;
        }

        poseStack.pushPose();
        poseStack.translate(dx, dy, dz);
        billboardMatrix.identity()
                .rotateY((float) Math.toRadians(-cameraYaw))
                .rotateX((float) Math.toRadians(cameraPitch));
        poseStack.mulPose(billboardMatrix);
        poseStack.scale(-animatedScale, -animatedScale, animatedScale);

        collector.submitText(
                poseStack,
                nameX,
                0.0f,
                nameSeq,
                false,
                Font.DisplayMode.SEE_THROUGH,
                LightCoordsUtil.FULL_BRIGHT,
                nameColor,
                0,
                0
        );

        if (distText != null) {
            collector.submitText(
                    poseStack,
                    distX,
                    0.0f,
                    Component.literal(distText).getVisualOrderText(),
                    false,
                    Font.DisplayMode.SEE_THROUGH,
                    LightCoordsUtil.FULL_BRIGHT,
                    COLOR_CYAN,
                    0,
                    0
            );
        }

        poseStack.popPose();
    }

    private static CachedName getCachedName(Player player) {
        UUID uuid = player.getUUID();
        Component displayName = player.getDisplayName();
        String raw = displayName != null ? displayName.getString() : player.getName().getString();
        CachedName existing = NAME_CACHE.get(uuid);
        if (existing != null && existing.rawText.equals(raw)) {
            return existing;
        }
        String stripped = ChatFormatting.stripFormatting(raw);
        if (stripped == null) {
            stripped = raw;
        }
        FormattedCharSequence plainSeq = Component.literal(stripped).getVisualOrderText();
        CachedName created = new CachedName(raw, plainSeq);
        NAME_CACHE.put(uuid, created);
        return created;
    }
}
