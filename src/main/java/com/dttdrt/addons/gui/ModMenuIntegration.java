package com.dttdrt.addons.gui;

import com.dttdrt.addons.DttdrtAddons;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.Minecraft;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            if (!DttdrtAddons.canOpenMenu(Minecraft.getInstance())) {
                return parent;
            }
            return new DttdrtMainScreen();
        };
    }
}

