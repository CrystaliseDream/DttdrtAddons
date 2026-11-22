package com.dttdrt.addons.features.combat;

import com.dttdrt.addons.core.auth.WhitelistManager;
import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.core.feature.Feature;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 1:1 port of NwjnAddons DamageTracker.
 * Tracks newly spawned ArmorStand damage indicators in the world and prints them to local client chat.
 * Pure client-side read-only entity scan; sends zero packets.
 */
public class DamageTrackerFeature implements Feature {

    private static final int MAX_TRACKED_UUIDS = 2048;
    // Exact port of NwjnAddons' /[A-Za-z:-_.#]/ exclusion regex (plus '/' to skip HP ratio tags)
    private static final Pattern EXCLUDE_PATTERN = Pattern.compile("[A-Za-z:-_.#/]");

    private final Set<UUID> seenDamageIds = new HashSet<>();
    private final Deque<UUID> seenOrder = new ArrayDeque<>();
    private ClientLevel lastLevel = null;

    @Override
    public String getId() {
        return "damage_tracker";
    }

    @Override
    public String getName() {
        return "Damage Tracker";
    }

    @Override
    public String getCategory() {
        return "Combat";
    }

    @Override
    public boolean isEnabled() {
        return ModConfig.getInstance().damageTrackerEnabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        ModConfig.getInstance().damageTrackerEnabled = enabled;
        ModConfig.save();
    }

    @Override
    public void init() {
    }

    @Override
    public void clientTick(Minecraft mc) {
        if (mc == null || mc.level == null || mc.player == null) {
            clearTracked();
            lastLevel = null;
            return;
        }

        if (mc.level != lastLevel) {
            clearTracked();
            lastLevel = mc.level;
        }

        if (!WhitelistManager.isAuthorized() || !isEnabled()) {
            return;
        }

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand)) {
                continue;
            }
            UUID uuid = stand.getUUID();
            if (seenDamageIds.contains(uuid)) {
                continue;
            }

            Component customName = stand.getCustomName();
            if (customName == null) {
                continue;
            }

            String stripped = ChatFormatting.stripFormatting(customName.getString());
            if (!isDamageTag(stripped)) {
                continue;
            }

            rememberUuid(uuid);
            mc.player.sendSystemMessage(customName);
        }
    }

    private void rememberUuid(UUID uuid) {
        if (seenDamageIds.size() >= MAX_TRACKED_UUIDS) {
            UUID oldest = seenOrder.pollFirst();
            if (oldest != null) {
                seenDamageIds.remove(oldest);
            }
        }
        if (seenDamageIds.add(uuid)) {
            seenOrder.addLast(uuid);
        }
    }

    private void clearTracked() {
        seenDamageIds.clear();
        seenOrder.clear();
    }

    private static boolean isDamageTag(String plain) {
        if (plain == null || plain.isEmpty()) {
            return false;
        }
        if (EXCLUDE_PATTERN.matcher(plain).find()) {
            return false;
        }
        for (int i = 0; i < plain.length(); i++) {
            if (Character.isDigit(plain.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
