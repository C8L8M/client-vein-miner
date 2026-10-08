/*
 * Client-Side Vein Miner - Copyright (c) 2026 C8L8M
 * SPDX-License-Identifier: LGPL-3.0-only
 * See LICENSE and licenses/GPL-3.0.txt in the project root.
 */
package com.c8l8m.clientveinminer.hud;

import com.c8l8m.clientveinminer.Reference;
import com.c8l8m.clientveinminer.config.Configs;
import com.c8l8m.clientveinminer.vein.VeinMiner;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Block counter, drawn right below the crosshair, with the auto stop countdown on the line under it. */
public final class ChainHud
{
    private static final Identifier ID = Identifier.of(Reference.MOD_ID, "chain_count");
    /** Distance from the screen centre to the top of the text. */
    private static final int CROSSHAIR_OFFSET = 14;
    /** Gap between the counter line and the countdown line below it. */
    private static final int COUNTDOWN_LINE_GAP = 2;
    private static final int COUNTDOWN_COLOR = 0xFFFFD24D;
    private static final int COUNTDOWN_URGENT_COLOR = 0xFFFF5555;
    /** The countdown is drawn in red from this many seconds down. */
    private static final int COUNTDOWN_URGENT_SECONDS = 3;

    private ChainHud()
    {
    }

    public static void register()
    {
        HudElementRegistry.addLast(ID, ChainHud::render);
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter)
    {
        if (Configs.Generic.SHOW_COUNT.getBooleanValue() == false)
        {
            return;
        }

        final MinecraftClient client = MinecraftClient.getInstance();

        if (client == null || client.textRenderer == null)
        {
            return;
        }

        final Text text;
        // -1 means "no countdown running", see VeinMiner#getAutoStopRemainingSeconds.
        int countdownSeconds = -1;

        if (VeinMiner.isActive())
        {
            final int remaining = VeinMiner.getRemainingCount();

            if (remaining <= 0)
            {
                return;
            }

            text = Text.translatable("client_vein_miner.hud.chain", remaining, VeinMiner.getReachable().size());
            countdownSeconds = VeinMiner.getAutoStopRemainingSeconds();

            // While anything is still reachable the chain is making progress, so the auto stop
            // countdown is only worth showing once there is nothing left to mine.
            if (VeinMiner.getReachable().isEmpty() == false)
            {
                countdownSeconds = -1;
            }
        }
        else if (VeinMiner.getState() == VeinMiner.State.PREVIEW)
        {
            final int count = VeinMiner.getPreviewCount();

            if (count <= 0)
            {
                return;
            }

            text = Text.translatable("client_vein_miner.hud.preview", count);
        }
        else
        {
            return;
        }

        final int screenWidth = context.getScaledWindowWidth();
        final int x = (screenWidth - client.textRenderer.getWidth(text)) / 2;
        final int y = context.getScaledWindowHeight() / 2 + CROSSHAIR_OFFSET;

        context.drawText(client.textRenderer, text, x, y, 0xFFFFFFFF, true);

        if (countdownSeconds >= 0)
        {
            final Text countdown = Text.translatable("client_vein_miner.hud.auto_stop", countdownSeconds);
            final int countdownX = (screenWidth - client.textRenderer.getWidth(countdown)) / 2;
            final int countdownY = y + client.textRenderer.fontHeight + COUNTDOWN_LINE_GAP;
            final int color = countdownSeconds <= COUNTDOWN_URGENT_SECONDS ? COUNTDOWN_URGENT_COLOR : COUNTDOWN_COLOR;

            context.drawText(client.textRenderer, countdown, countdownX, countdownY, color, true);
        }
    }
}
