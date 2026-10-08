/*
 * Client-Side Vein Miner - Copyright (c) 2026 C8L8M
 * SPDX-License-Identifier: LGPL-3.0-only
 * See LICENSE and licenses/GPL-3.0.txt in the project root.
 */
package com.c8l8m.clientveinminer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.world.ClientWorld;

/** Access to the world's pending update manager, which is package private. */
@Mixin(ClientWorld.class)
public interface ClientWorldAccessor
{
    @Invoker("getPendingUpdateManager")
    PendingUpdateManager clientVeinMiner$getPendingUpdateManager();
}
