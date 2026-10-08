/*
 * Client-Side Vein Miner - Copyright (c) 2026 C8L8M
 * SPDX-License-Identifier: LGPL-3.0-only
 * See LICENSE and licenses/GPL-3.0.txt in the project root.
 */
package com.c8l8m.clientveinminer.config;

import com.google.common.collect.ImmutableList;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.StringUtils;

/**
 * How far a chain may reach while the player is in creative mode.
 *
 * The block interaction range is checked by the server side of the interaction manager, so raising a
 * number is not enough: the attribute route is clamped to 64 blocks. OFF simply keeps the vanilla
 * range. SINGLE_PLAYER lifts the check itself, which works because the integrated server runs in this
 * very JVM (see PlayerEntityReachMixin). ALL additionally lets a server running Servux delete the
 * whole group as one area edit - that path never runs a range check at all, which is the only way to
 * ignore the distance on a dedicated server.
 */
public enum CreativeReachMode implements IConfigOptionListEntry
{
    OFF           ("off",           "client_vein_miner.config.value.creative_reach_mode.off"),
    SINGLE_PLAYER ("single_player", "client_vein_miner.config.value.creative_reach_mode.single_player"),
    ALL           ("all",           "client_vein_miner.config.value.creative_reach_mode.all");

    public static final ImmutableList<CreativeReachMode> VALUES = ImmutableList.copyOf(values());

    private final String configString;
    private final String translationKey;

    CreativeReachMode(String configString, String translationKey)
    {
        this.configString = configString;
        this.translationKey = translationKey;
    }

    /** True when the reach check may be lifted in a world this client hosts. */
    public boolean ignoresReach()
    {
        return this == SINGLE_PLAYER || this == ALL;
    }

    /** True when a Servux server may be asked to run the group as a single area edit. */
    public boolean usesAreaEdit()
    {
        return this == ALL;
    }

    @Override
    public String getStringValue()
    {
        return this.configString;
    }

    @Override
    public String getDisplayName()
    {
        return StringUtils.translate(this.translationKey);
    }

    @Override
    public IConfigOptionListEntry cycle(boolean forward)
    {
        int id = this.ordinal();

        if (forward)
        {
            if (++id >= values().length)
            {
                id = 0;
            }
        }
        else
        {
            if (--id < 0)
            {
                id = values().length - 1;
            }
        }

        return values()[id % values().length];
    }

    @Override
    public CreativeReachMode fromString(String name)
    {
        return fromStringStatic(name);
    }

    public static CreativeReachMode fromStringStatic(String name)
    {
        for (CreativeReachMode value : VALUES)
        {
            if (value.configString.equalsIgnoreCase(name))
            {
                return value;
            }
        }

        return CreativeReachMode.OFF;
    }
}
