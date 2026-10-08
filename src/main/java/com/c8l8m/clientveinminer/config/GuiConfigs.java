/*
 * Client-Side Vein Miner - Copyright (c) 2026 C8L8M
 * SPDX-License-Identifier: LGPL-3.0-only
 * See LICENSE and licenses/GPL-3.0.txt in the project root.
 */
package com.c8l8m.clientveinminer.config;

import java.util.Collections;
import java.util.List;

import com.c8l8m.clientveinminer.Reference;

import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.interfaces.IConfigGuiAllTab;
import fi.dy.masa.malilib.util.StringUtils;

/**
 * The config screen, split into categories.
 *
 * malilib's stock GuiModConfigs deliberately draws no title, which makes this mod's screen look
 * different from masa's own mods. Extending GuiConfigsBase (like MaLiLibConfigGui does, and copying
 * its tab layout) restores both the usual title line and the category buttons on top.
 *
 * Every new feature category gets its own enum constant here, so the tab row grows with the mod.
 */
public class GuiConfigs extends GuiConfigsBase implements IConfigGuiAllTab
{
    private static Tab tab = Tab.CHAIN_MINING;

    public GuiConfigs()
    {
        // malilib sets the title with StringUtils.translate(titleKey, args), so the single %s in the
        // title string is filled from the FIRST argument: pass the version, not the mod name.
        super(10, 50, Reference.MOD_ID, null, "client_vein_miner.gui.title.configs",
                Reference.getModVersion());
    }

    @Override
    public void initGui()
    {
        super.initGui();

        this.clearOptions();

        int x = 10;
        final int y = 26;

        for (Tab tab : Tab.values())
        {
            x += this.createButton(x, y, -1, tab) + 2;
        }
    }

    private int createButton(int x, int y, int width, Tab tab)
    {
        ButtonGeneric button = new ButtonGeneric(x, y, width, 20, tab.getDisplayName());
        button.setEnabled(GuiConfigs.tab != tab);
        this.addButton(button, new ButtonListener(tab, this));

        return button.getWidth();
    }

    @Override
    public boolean useAllTab()
    {
        // The category row is explicit now (General and Chain Mining), so there is no "All" tab.
        return false;
    }

    @Override
    protected boolean useKeybindSearch()
    {
        return false;
    }

    @Override
    public List<ConfigOptionWrapper> getAllConfigs()
    {
        return ConfigOptionWrapper.createFor(Configs.getAllConfigs());
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs()
    {
        return switch (tab)
        {
            case GENERAL      -> ConfigOptionWrapper.createFor(Configs.getGeneralConfigs());
            case CHAIN_MINING -> ConfigOptionWrapper.createFor(Configs.getChainMiningConfigs());
            case MISC         -> Collections.<ConfigOptionWrapper>emptyList();
        };
    }

    private record ButtonListener(Tab tab, GuiConfigs parent) implements IButtonActionListener
    {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton)
        {
            GuiConfigs.tab = this.tab;

            this.parent.reCreateListWidget(); // apply the new config width

            if (this.parent.getListWidget() != null)
            {
                this.parent.getListWidget().resetScrollbarPosition();
            }

            this.parent.initGui();
        }
    }

    public enum Tab
    {
        GENERAL     ("client_vein_miner.gui.title.general"),
        CHAIN_MINING("client_vein_miner.gui.title.chain_mining"),
        MISC        ("client_vein_miner.gui.title.misc");

        private final String translationKey;

        Tab(String translationKey)
        {
            this.translationKey = translationKey;
        }

        public String getDisplayName()
        {
            return StringUtils.translate(this.translationKey);
        }
    }
}
