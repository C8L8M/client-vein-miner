/*
 * Client-Side Vein Miner - Copyright (c) 2026 C8L8M
 * SPDX-License-Identifier: LGPL-3.0-only
 * See LICENSE and licenses/GPL-3.0.txt in the project root.
 */
package com.c8l8m.clientveinminer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.c8l8m.clientveinminer.vein.VeinMiner;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Lifts the block interaction range check completely when the single player "ignore distance" option
 * is on in creative mode.
 *
 * Raising the block interaction range attribute cannot do this: the attribute is clamped, so the
 * check would still fail on far blocks. The check itself lives in PlayerEntity#canInteractWithBlockAt,
 * which both the client interaction manager and the integrated server call. ServerPlayerEntity does
 * not override it, so one injection here covers the integrated server too - which is why the option
 * is single player only: on a dedicated server the server's own copy of PlayerEntity is out of reach.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityReachMixin
{
    @Inject(method = "canInteractWithBlockAt", at = @At("HEAD"), cancellable = true)
    private void clientVeinMiner$ignoreDistance(BlockPos pos, double additionalRange, CallbackInfoReturnable<Boolean> cir)
    {
        if (VeinMiner.ignoresDistance((PlayerEntity) (Object) this))
        {
            cir.setReturnValue(true);
        }
    }
}
