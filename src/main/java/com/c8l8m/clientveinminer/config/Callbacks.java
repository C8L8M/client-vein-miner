/*
 * Client-Side Vein Miner - Copyright (c) 2026 C8L8M
 * SPDX-License-Identifier: LGPL-3.0-only
 * See LICENSE and licenses/GPL-3.0.txt in the project root.
 */
package com.c8l8m.clientveinminer.config;

import com.c8l8m.clientveinminer.vein.VeinMiner;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;

public class Callbacks
{
    public static void init()
    {
        Configs.Hotkeys.OPEN_CONFIG.getKeybind().setCallback(new OpenConfigCallback());
        Configs.Hotkeys.FORCE_STOP.getKeybind().setCallback(new ForceStopCallback());
    }

    private static class OpenConfigCallback implements IHotkeyCallback
    {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key)
        {
            GuiBase.openGui(new GuiConfigs());
            return true;
        }
    }

    /** Force-stops a running chain. */
    private static class ForceStopCallback implements IHotkeyCallback
    {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key)
        {
            VeinMiner.stop();
            return true;
        }
    }
}
