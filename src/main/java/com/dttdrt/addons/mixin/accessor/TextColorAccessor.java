package com.dttdrt.addons.mixin.accessor;

import net.minecraft.network.chat.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TextColor.class)
public interface TextColorAccessor {

    @Invoker("<init>")
    static TextColor dttdrt$create(int value, String name) {
        throw new AssertionError();
    }

    @Accessor("name")
    String dttdrt$getName();
}
