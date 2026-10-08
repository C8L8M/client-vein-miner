/*
 * Client-Side Vein Miner - Copyright (c) 2026 C8L8M
 * SPDX-License-Identifier: LGPL-3.0-only
 * See LICENSE and licenses/GPL-3.0.txt in the project root.
 */
package com.c8l8m.clientveinminer.servux;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Raw carrier for Servux's "servux:litematics" channel.
 *
 * <p>Every packet of that channel starts with a VarInt packet type, and what follows depends on the
 * type: vanilla NBT for the metadata exchange, gzip compressed NBT for the task requests. Both
 * shapes are built and read by {@link ServuxAreaEdit}, so this payload is a plain byte blob.</p>
 */
public record ServuxLitematicsPayload(byte[] data) implements CustomPayload
{
    public static final CustomPayload.Id<ServuxLitematicsPayload> ID =
            new CustomPayload.Id<>(Identifier.of("servux", "litematics"));

    public static final PacketCodec<RegistryByteBuf, ServuxLitematicsPayload> CODEC =
            CustomPayload.codecOf(ServuxLitematicsPayload::write, ServuxLitematicsPayload::new);

    public ServuxLitematicsPayload(RegistryByteBuf buf)
    {
        this(readAll(buf));
    }

    private static byte[] readAll(RegistryByteBuf buf)
    {
        final byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }

    private void write(RegistryByteBuf buf)
    {
        buf.writeBytes(this.data);
    }

    @Override
    public CustomPayload.Id<ServuxLitematicsPayload> getId()
    {
        return ID;
    }
}
