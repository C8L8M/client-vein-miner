/*
 * Client-Side Vein Miner - Copyright (c) 2026 C8L8M
 * SPDX-License-Identifier: LGPL-3.0-only
 * See LICENSE and licenses/GPL-3.0.txt in the project root.
 */
package com.c8l8m.clientveinminer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.c8l8m.clientveinminer.vein.VeinMiner;

import net.minecraft.client.MinecraftClient;

/**
 * Suppresses the vanilla mouse driven block breaking while a chain is running, so that the chain
 * state machine has exclusive use of the interaction manager.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin
{
    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void clientVeinMiner$suppressVanillaBreaking(boolean breaking, CallbackInfo ci)
    {
        if (VeinMiner.isActive())
        {
            ci.cancel();
        }
    }
}
