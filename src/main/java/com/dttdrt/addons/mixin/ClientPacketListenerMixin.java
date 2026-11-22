package com.dttdrt.addons.mixin;

import com.dttdrt.addons.DttdrtAddons;
import com.dttdrt.addons.core.auth.WhitelistManager;
import com.dttdrt.addons.features.freecam.FreeCamFeature;
import com.dttdrt.addons.features.mining.CrystalHollowsTracker;
import com.dttdrt.addons.features.mining.CrystalWishingCompassSolver;
import com.dttdrt.addons.features.mining.MiningProfitFeature;
import com.dttdrt.addons.features.mining.NucleusProfitFeature;
import com.dttdrt.addons.features.mining.ScathaProfitFeature;
import com.dttdrt.addons.features.party.PartyVoteFeature;
import com.dttdrt.addons.features.storage.StorageOverlayManager;
import com.dttdrt.addons.gui.DttdrtMainScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pure read-only listener on incoming packets for Wishing Compass triangulation and Server ID detection.
 * Sends ZERO packets.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void dttdrt$onSendCommand(String command, CallbackInfo ci) {
        if (command == null) return;
        String trimmed = command.trim();
        if ("da".equalsIgnoreCase(trimmed)) {
            Minecraft mc = Minecraft.getInstance();
            mc.schedule(() -> mc.setScreen(new DttdrtMainScreen()));
            ci.cancel();
            return;
        }
        if ("daretry".equalsIgnoreCase(trimmed) || "dttdrtretry".equalsIgnoreCase(trimmed)) {
            DttdrtAddons.executeWhitelistRetryCommand(Minecraft.getInstance());
            ci.cancel();
            return;
        }
        if ("daapply".equalsIgnoreCase(trimmed)
                || (trimmed.length() > 8 && trimmed.substring(0, 8).equalsIgnoreCase("daapply "))) {
            String note = trimmed.length() > 8 ? trimmed.substring(8).trim() : "";
            DttdrtAddons.executeWhitelistApplyCommand(Minecraft.getInstance(), note);
            ci.cancel();
        }
    }

    @Inject(method = "handleParticleEvent", at = @At("HEAD"))
    private void dttdrt$onParticleEvent(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        if (!WhitelistManager.isAuthorized() || !CrystalHollowsTracker.isInCrystalHollows()) return;
        if (packet.getParticle().getType() == ParticleTypes.HAPPY_VILLAGER) {
            CrystalWishingCompassSolver.getInstance()
                    .onHappyVillagerParticle(packet.getX(), packet.getY(), packet.getZ());
        }
    }

    @Inject(method = "handleSystemChat", at = @At("RETURN"))
    private void dttdrt$onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (!WhitelistManager.isAuthorized() || packet.content() == null) return;
        String msg = packet.content().getString();
        if (packet.overlay()) {
            MiningProfitFeature.onActionBarMessage(msg);
            return;
        }
        CrystalHollowsTracker.onChatMessage(msg);
        PartyVoteFeature.onChatMessage(msg);
        MiningProfitFeature.onChatComponent(packet.content());
        ScathaProfitFeature.onChatComponent(packet.content());
        NucleusProfitFeature.onChatComponent(packet.content());
    }

    @Inject(method = "handleSoundEvent", at = @At("RETURN"))
    private void dttdrt$onSoundEvent(ClientboundSoundPacket soundPacket, CallbackInfo ci) {
        if (!WhitelistManager.isAuthorized() || soundPacket == null || soundPacket.getSound() == null) return;
        SoundEvent sound = soundPacket.getSound().value();
        if (sound != null) {
            ScathaProfitFeature.onSoundPlayed(sound.location(), soundPacket.getPitch());
        }
    }

    @Inject(method = "handleContainerSetSlot", at = @At("RETURN"))
    private void dttdrt$onContainerSetSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        if (!WhitelistManager.isAuthorized()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null && packet.getContainerId() != 0
                && packet.getContainerId() == mc.player.containerMenu.containerId) {
            StorageOverlayManager.onChestContentUpdate();
        }
    }

    @Inject(method = "handleContainerContent", at = @At("RETURN"))
    private void dttdrt$onContainerContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        if (!WhitelistManager.isAuthorized()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null && packet.containerId() != 0
                && packet.containerId() == mc.player.containerMenu.containerId) {
            StorageOverlayManager.onChestContentUpdate();
        }
    }

    @Inject(method = "handleLogin", at = @At("RETURN"))
    private void dttdrt$onLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        FreeCamFeature.disableIfActive("handleLogin");
    }

    @Inject(method = "handleRespawn", at = @At("RETURN"))
    private void dttdrt$onRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        FreeCamFeature.disableIfActive("handleRespawn");
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void dttdrt$onMovePlayer(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        FreeCamFeature.disableIfActive("teleport_packet");
    }
}
