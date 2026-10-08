/*
 * Client-Side Vein Miner - Copyright (c) 2026 C8L8M
 * SPDX-License-Identifier: LGPL-3.0-only
 * See LICENSE and licenses/GPL-3.0.txt in the project root.
 */
package com.c8l8m.clientveinminer.config;

import java.util.List;

import com.c8l8m.clientveinminer.Reference;

import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;

public class InputHandler implements IKeybindProvider
{
    private static final InputHandler INSTANCE = new InputHandler();

    public static InputHandler getInstance()
    {
        return INSTANCE;
    }

    private InputHandler()
    {
        super();
    }

    @Override
    public void addKeysToMap(IKeybindManager manager)
    {
        manager.addKeybindToMap(Configs.Hotkeys.ACTIVATE.getKeybind());
        manager.addKeybindToMap(Configs.Hotkeys.FORCE_STOP.getKeybind());
        manager.addKeybindToMap(Configs.Hotkeys.OPEN_CONFIG.getKeybind());
    }

    @Override
    public void addHotkeys(IKeybindManager manager)
    {
        List<? extends IHotkey> hotkeys = Configs.Hotkeys.HOTKEYS;
        manager.addHotkeysForCategory(Reference.MOD_NAME, "client_vein_miner.hotkeys.category.generic", hotkeys);
    }
}
