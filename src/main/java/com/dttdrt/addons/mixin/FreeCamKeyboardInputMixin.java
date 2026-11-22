package com.dttdrt.addons.mixin;

import com.dttdrt.addons.features.freecam.FreeCamFeature;
import com.dttdrt.addons.mixin.accessor.ClientInputAccessor;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clears local player movement/jump/sneak/sprint inputs while FreeCam controls the detached camera.
 */
@Mixin(KeyboardInput.class)
public class FreeCamKeyboardInputMixin {

    @Inject(method = "tick()V", at = @At("RETURN"))
    private void dttdrt$onTickReturn(CallbackInfo ci) {
        if (!FreeCamFeature.isActive()) {
            return;
        }
        KeyboardInput self = (KeyboardInput) (Object) this;
        self.keyPresses = Input.EMPTY;
        ((ClientInputAccessor) self).dttdrt_setMoveVector(Vec2.ZERO);
    }
}
