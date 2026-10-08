/*
 * Client-Side Vein Miner - Copyright (c) 2026 C8L8M
 * SPDX-License-Identifier: LGPL-3.0-only
 * See LICENSE and licenses/GPL-3.0.txt in the project root.
 */
package com.c8l8m.clientveinminer;

import net.fabricmc.loader.api.FabricLoader;

public final class Reference
{
    public static final String MOD_ID = "client_vein_miner";
    public static final String MOD_NAME = "Client-Side Vein Miner";

    private Reference()
    {
    }

    /** The version from fabric.mod.json, so the GUI title always matches the built jar. */
    public static String getModVersion()
    {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("0.1");
    }
}
