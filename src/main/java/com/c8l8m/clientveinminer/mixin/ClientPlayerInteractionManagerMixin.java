/*
 * Client-Side Vein Miner - Copyright (c) 2026 C8L8M
 * SPDX-License-Identifier: LGPL-3.0-only
 * See LICENSE and licenses/GPL-3.0.txt in the project root.
 */
package com.c8l8m.clientveinminer.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.c8l8m.clientveinminer.vein.VeinMiner;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Starts a chain when the player actually breaks a chainable block, i.e. at the moment the vanilla
 * client finishes breaking it (covers both survival progress completion and instant breaking).
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin
{
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "breakBlock", at = @At("HEAD"))
    private void clientVeinMiner$onBreakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir)
    {
        final ClientWorld world = this.client.world;

        if (world == null)
        {
            return;
        }

        VeinMiner.onBlockBroken(world, pos);
    }
}
