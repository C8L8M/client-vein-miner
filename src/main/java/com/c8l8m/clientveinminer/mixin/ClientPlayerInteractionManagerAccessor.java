/*
 * Client-Side Vein Miner - Copyright (c) 2026 C8L8M
 * SPDX-License-Identifier: LGPL-3.0-only
 * See LICENSE and licenses/GPL-3.0.txt in the project root.
 */
package com.c8l8m.clientveinminer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.network.ClientPlayerInteractionManager;

/** Access to the vanilla per-block break cooldown, which the mod overrides with its own interval. */
@Mixin(ClientPlayerInteractionManager.class)
public interface ClientPlayerInteractionManagerAccessor
{
    @Accessor("blockBreakingCooldown")
    void clientVeinMiner$setBreakCooldown(int cooldown);
}
