package com.dttdrt.addons.mixin;

import com.dttdrt.addons.features.storage.gui.CoordRememberingSlot;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Slot.class)
public class SlotMixin implements CoordRememberingSlot {

    @Shadow
    @Mutable
    public int x;

    @Shadow
    @Mutable
    public int y;

    @Unique
    private int dttdrt$originalX;

    @Unique
    private int dttdrt$originalY;

    @Override
    public void dttdrt$rememberCoords() {
        this.dttdrt$originalX = this.x;
        this.dttdrt$originalY = this.y;
    }

    @Override
    public void dttdrt$restoreCoords() {
        this.x = this.dttdrt$originalX;
        this.y = this.dttdrt$originalY;
    }

    @Override
    public void dttdrt$setCoords(int newX, int newY) {
        this.x = newX;
        this.y = newY;
    }
}
