/*
 * Client-Side Vein Miner - Copyright (c) 2026 C8L8M
 * SPDX-License-Identifier: LGPL-3.0-only
 * See LICENSE and licenses/GPL-3.0.txt in the project root.
 */
package com.c8l8m.clientveinminer.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.c8l8m.clientveinminer.config.GuiConfigs;

/**
 * Mod Menu integration. Mod Menu reads this entrypoint, so the class is only ever loaded when
 * Mod Menu is actually installed; the dependency is compile-only.
 */
public class ModMenuImpl implements ModMenuApi
{
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory()
    {
        return parent ->
        {
            GuiConfigs gui = new GuiConfigs();
            gui.setParent(parent);
            return gui;
        };
    }
}
