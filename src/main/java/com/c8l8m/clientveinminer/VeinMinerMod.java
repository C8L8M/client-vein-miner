/*
 * Client-Side Vein Miner - Copyright (c) 2026 C8L8M
 * SPDX-License-Identifier: LGPL-3.0-only
 * See LICENSE and licenses/GPL-3.0.txt in the project root.
 */
package com.c8l8m.clientveinminer;

import com.c8l8m.clientveinminer.config.Callbacks;
import com.c8l8m.clientveinminer.config.Configs;
import com.c8l8m.clientveinminer.config.GuiConfigs;
import com.c8l8m.clientveinminer.config.InputHandler;
import com.c8l8m.clientveinminer.hud.ChainHud;
import com.c8l8m.clientveinminer.render.ChainRenderer;
import com.c8l8m.clientveinminer.servux.ServuxAreaEdit;
import com.c8l8m.clientveinminer.vein.VeinMiner;

import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.data.ModInfo;
import fi.dy.masa.malilib.util.i18n.i18nManager;
import fi.dy.masa.malilib.util.i18n.i18nMode;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class VeinMinerMod implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        ConfigManager.getInstance().registerConfigHandler(Reference.MOD_ID, new Configs());
        InputEventHandler.getKeybindManager().registerKeybindProvider(InputHandler.getInstance());
        Callbacks.init();

        // Translations live in assets/client_vein_miner/lang (en_us + zh_cn). Registering the manager is what
        // malilib's rewritten i18n system expects from downstream mods; with FOLLOW_VANILLA the mod
        // simply follows the vanilla language setting, and malilib's own language override option
        // can serve the same files instead.
        final i18nManager lang = i18nManager.create(Reference.MOD_ID);

        if (lang != null)
        {
            Registry.TRANSLATION_OVERRIDE_MANAGER.registerTranslationManager(
                    Reference.MOD_ID, lang, i18nMode.FOLLOW_VANILLA);
        }

        // Register the config screen up front. malilib would otherwise only discover it while
        // constructing the screen for the first time, which registered a stale screen instance and
        // a name derived from the mod id, and left this mod out of the config screen switcher
        // (the mod list box) on the first open.
        Registry.CONFIG_SCREEN.registerConfigScreenFactory(
                new ModInfo(Reference.MOD_ID, Reference.MOD_NAME, GuiConfigs::new));

        ClientTickEvents.END_CLIENT_TICK.register(VeinMiner::tick);

        ChainRenderer.register();
        ChainHud.register();
        ServuxAreaEdit.register();
    }
}
