package com.dttdrt.addons.mixin;

import com.dttdrt.addons.core.auth.WhitelistManager;
import com.dttdrt.addons.features.mining.MiningProfitFeature;
import com.dttdrt.addons.features.mining.ScathaProfitFeature;
import com.dttdrt.addons.features.storage.StorageOverlayManager;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pure read-only listener on client block-mining actions to drive active session timing.
 * Sends ZERO packets.
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void dttdrt$onDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (WhitelistManager.isAuthorized()) {
            MiningProfitFeature.onStrongActivitySignal();
            ScathaProfitFeature.onStrongActivitySignal();
        }
    }

    @Inject(method = "startDestroyBlock", at = @At("HEAD"))
    private void dttdrt$onStartDestroyBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (WhitelistManager.isAuthorized()) {
            MiningProfitFeature.onStrongActivitySignal();
            ScathaProfitFeature.onStrongActivitySignal();
        }
    }

    @Inject(method = "continueDestroyBlock", at = @At("HEAD"))
    private void dttdrt$onContinueDestroyBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (WhitelistManager.isAuthorized()) {
            MiningProfitFeature.onStrongActivitySignal();
            ScathaProfitFeature.onStrongActivitySignal();
        }
    }

    @Inject(method = "handleContainerInput", at = @At("HEAD"))
    private void dttdrt$onHandleContainerInput(int containerId, int slotNum, int buttonNum, ContainerInput containerInput, Player player, CallbackInfo ci) {
        if (!WhitelistManager.isAuthorized() || player == null) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu != null && slotNum >= 0 && slotNum < menu.slots.size()) {
            Slot slot = menu.getSlot(slotNum);
            ItemStack snapshot = slot.getItem().copy();
            StorageOverlayManager.onSlotClick(slot, snapshot);
        }
    }
}
