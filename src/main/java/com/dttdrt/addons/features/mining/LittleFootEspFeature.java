package com.dttdrt.addons.features.mining;

import com.dttdrt.addons.core.auth.WhitelistManager;
import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.core.feature.Feature;
import com.dttdrt.addons.util.NotificationUtils;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Glacite Mineshaft LittleFoot ESP (100% local, zero packets).
 * Scans loaded entities in the Mineshaft for LittleFoot / LittleFoot nameplates
 * and renders through-wall boxes + distance labels.
 */
public class LittleFootEspFeature implements Feature {

    public static final int RGB = (232 << 16) | (121 << 8) | 249; // Vivid magenta (#E879F9)
    private static final int FILL_ARGB = (90 << 24) | RGB;
    private static final int STROKE_ARGB = (240 << 24) | RGB;

    private static final Identifier HUD_ID =
            Identifier.fromNamespaceAndPath("dttdrtaddons", "littlefoot_esp_labels");

    private record TargetEntry(AABB box, double cx, double labelY, double cz) {}

    private volatile List<TargetEntry> targets = Collections.emptyList();
    private final Set<Integer> notifiedEntityIds = new HashSet<>();
    private ClientLevel lastLevel = null;
    private boolean hasUnannouncedDiscovery = false;
    private int settleTicksRemaining = 0;

    @Override
    public String getId() {
        return "littlefoot_esp";
    }

    @Override
    public String getName() {
        return "LittleFoot ESP";
    }

    @Override
    public String getCategory() {
        return "Gemstone ESP";
    }

    @Override
    public void init() {
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> renderBoxes());
        HudElementRegistry.attachElementAfter(VanillaHudElements.OVERLAY_MESSAGE, HUD_ID, (graphics, deltaTracker) -> renderLabels(graphics));
    }

    @Override
    public void clientTick(Minecraft mc) {
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            notifiedEntityIds.clear();
            hasUnannouncedDiscovery = false;
            settleTicksRemaining = 0;
        }

        if (!isEnabled() || !CrystalHollowsTracker.isInMineshaft() || mc.level == null || mc.player == null) {
            targets = Collections.emptyList();
            if (!isEnabled()) {
                notifiedEntityIds.clear();
                hasUnannouncedDiscovery = false;
                settleTicksRemaining = 0;
            }
            return;
        }

        List<LivingEntity> mobs = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && !(entity instanceof ArmorStand) && entity != mc.player) {
                mobs.add(living);
            }
        }

        Set<Integer> seenIds = new HashSet<>();
        List<TargetEntry> found = new ArrayList<>();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player || !matchesLittleFoot(entity)) continue;

            if (entity instanceof ArmorStand || entity instanceof Display.TextDisplay) {
                LivingEntity matchedMob = findClosestMobUnderStand(entity, mobs);
                if (matchedMob != null) {
                    if (seenIds.add(matchedMob.getId())) {
                        AABB box = matchedMob.getBoundingBox();
                        found.add(new TargetEntry(box, matchedMob.getX(), box.maxY + 0.35, matchedMob.getZ()));
                    }
                } else if (seenIds.add(entity.getId())) {
                    double x = entity.getX();
                    double y = entity.getY() - 1.9;
                    double z = entity.getZ();
                    AABB box = new AABB(x - 0.35, y, z - 0.35, x + 0.35, y + 1.9, z + 0.35);
                    found.add(new TargetEntry(box, x, box.maxY + 0.35, z));
                }
            } else if (entity instanceof LivingEntity living) {
                if (seenIds.add(living.getId())) {
                    AABB box = living.getBoundingBox();
                    found.add(new TargetEntry(box, living.getX(), box.maxY + 0.35, living.getZ()));
                }
            }
        }

        targets = found;

        // Clean up entities that despawned / moved out so future spawns re-announce cleanly
        notifiedEntityIds.retainAll(seenIds);

        boolean discoveredNew = false;
        for (Integer id : seenIds) {
            if (!notifiedEntityIds.contains(id)) {
                discoveredNew = true;
                break;
            }
        }

        if (discoveredNew) {
            if (!hasUnannouncedDiscovery) {
                hasUnannouncedDiscovery = true;
                settleTicksRemaining = 2; // Wait 2 ticks so ArmorStand + Mob pair & nearby group finish loading together
            } else if (settleTicksRemaining > 0) {
                settleTicksRemaining--;
            }

            if (settleTicksRemaining <= 0) {
                notifiedEntityIds.addAll(seenIds);
                hasUnannouncedDiscovery = false;
                int count = found.size();
                String noun = count == 1 ? "LittleFoot" : "LittleFoots";
                NotificationUtils.sendClientChatMessage(
                        Component.literal("§dLittleFoot ESP §fdetected §d" + count + " §f" + noun + " nearby!")
                );
            }
        } else {
            hasUnannouncedDiscovery = false;
            settleTicksRemaining = 0;
        }
    }

    private static boolean matchesLittleFoot(Entity entity) {
        String name = ChatFormatting.stripFormatting(entity.getName().getString());
        if (name != null && name.toLowerCase(Locale.ROOT).contains("littlefoot")) {
            return true;
        }
        if (entity.getCustomName() != null) {
            String custom = ChatFormatting.stripFormatting(entity.getCustomName().getString());
            if (custom != null && custom.toLowerCase(Locale.ROOT).contains("littlefoot")) {
                return true;
            }
        }
        String display = ChatFormatting.stripFormatting(entity.getDisplayName().getString());
        if (display != null && display.toLowerCase(Locale.ROOT).contains("littlefoot")) {
            return true;
        }
        if (entity instanceof Display.TextDisplay td && td.textRenderState() != null) {
            String text = ChatFormatting.stripFormatting(td.textRenderState().text().getString());
            return text != null && text.toLowerCase(Locale.ROOT).contains("littlefoot");
        }
        return false;
    }

    private static LivingEntity findClosestMobUnderStand(Entity stand, List<LivingEntity> mobs) {
        LivingEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (LivingEntity mob : mobs) {
            double dx = mob.getX() - stand.getX();
            double dz = mob.getZ() - stand.getZ();
            double horizSq = dx * dx + dz * dz;
            double dy = stand.getY() - mob.getY();
            if (horizSq <= 2.25 && dy >= -1.0 && dy <= 3.5) {
                double distSq = horizSq + dy * dy;
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    best = mob;
                }
            }
        }
        return best;
    }

    private void renderBoxes() {
        if (!WhitelistManager.isAuthorized() || !isEnabled() || !CrystalHollowsTracker.isInMineshaft()) return;
        List<TargetEntry> list = targets;
        if (list.isEmpty()) return;

        for (TargetEntry entry : list) {
            Gizmos.cuboid(entry.box, GizmoStyle.strokeAndFill(STROKE_ARGB, 2.5f, FILL_ARGB))
                    .setAlwaysOnTop();
        }
    }

    private void renderLabels(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (!WhitelistManager.isAuthorized() || !isEnabled() || !CrystalHollowsTracker.isInMineshaft()) return;
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null) return;

        List<TargetEntry> list = targets;
        if (list.isEmpty()) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null) return;

        Vec3 camPos = camera.position();
        Matrix4f viewProj = camera.getViewRotationProjectionMatrix(new Matrix4f());
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        for (TargetEntry entry : list) {
            float dx = (float) (entry.cx - camPos.x);
            float dy = (float) (entry.labelY - camPos.y);
            float dz = (float) (entry.cz - camPos.z);

            Vector4f clip = new Vector4f(dx, dy, dz, 1.0f).mul(viewProj);
            if (clip.w <= 0.05f) continue;

            float ndcX = clip.x / clip.w;
            float ndcY = clip.y / clip.w;
            if (ndcX < -1.15f || ndcX > 1.15f || ndcY < -1.15f || ndcY > 1.15f) continue;

            int sx = Math.round((ndcX * 0.5f + 0.5f) * screenW);
            int sy = Math.round((0.5f - ndcY * 0.5f) * screenH);

            int dist = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
            String label = "LittleFoot (" + dist + "m)";

            graphics.pose().pushMatrix();
            graphics.pose().translate((float) sx, (float) sy);
            graphics.pose().scale(0.85f, 0.85f);
            graphics.centeredText(mc.font, label, 0, -4, 0xFF000000 | RGB);
            graphics.pose().popMatrix();
        }
    }

    @Override
    public boolean isEnabled() {
        return ModConfig.getInstance().littleFootEspEnabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        ModConfig.getInstance().littleFootEspEnabled = enabled;
        ModConfig.save();
    }
}
