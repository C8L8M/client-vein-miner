/*
 * Client-Side Vein Miner - Copyright (c) 2026 C8L8M
 * SPDX-License-Identifier: LGPL-3.0-only
 * See LICENSE and licenses/GPL-3.0.txt in the project root.
 */
package com.c8l8m.clientveinminer.servux;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import com.c8l8m.clientveinminer.Reference;
import com.c8l8m.clientveinminer.config.Configs;

import fi.dy.masa.malilib.MaLiLib;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Client side of Servux's "servux:litematics" channel, used to hand a whole chain group to the
 * server as one area edit task.
 *
 * <p>Servux deletes the requested boxes without ever checking the interaction range, which is the
 * only way to get distance free chain mining on a dedicated server. The price is that the blocks are
 * removed like a world edit: nothing drops, neighbouring blocks are not updated, and only already
 * loaded chunks are touched.</p>
 *
 * <p>Protocol (Servux 0.9.7+, litematic_data provider, PROTOCOL_VERSION 2). Every packet starts with
 * a VarInt type:</p>
 * <ul>
 *   <li>2 - C2S metadata request, vanilla NBT {version: 2}; registers this player for tasks</li>
 *   <li>1 - S2C metadata reply, vanilla NBT; only sent when the registration was accepted</li>
 *   <li>14 - C2S task request, 4 byte big endian length followed by gzip compressed unnamed NBT</li>
 * </ul>
 */
public final class ServuxAreaEdit
{
    private static final Identifier CHANNEL_ID = ServuxLitematicsPayload.ID.id();
    private static final int PROTOCOL_VERSION = 2;
    private static final int PACKET_C2S_METADATA_REQUEST = 2;
    private static final int PACKET_C2S_TASK_REQUEST = 14;
    /** Each box costs roughly 90 bytes, so this stays far below the 32762 byte packet limit. */
    private static final int MAX_BOXES_PER_PACKET = 200;
    private static final String BOX_NAME = "client_vein_miner";

    private static boolean registered;
    private static boolean handshakeSent;
    private static boolean metadataSeen;
    private static ClientWorld lastWorld;

    private ServuxAreaEdit()
    {
    }

    /** Registers the payload type once, from the client initializer. */
    public static void register()
    {
        // Litematica ships the same channel and would trip over a second registration, so leave the
        // channel to it; its own paste tool already covers area editing for those users.
        if (FabricLoader.getInstance().isModLoaded("litematica"))
        {
            MaLiLib.LOGGER.info("[{}] Litematica owns the servux:litematics channel, area editing stays off", Reference.MOD_ID);
            return;
        }

        try
        {
            PayloadTypeRegistry.playC2S().register(ServuxLitematicsPayload.ID, ServuxLitematicsPayload.CODEC);
            PayloadTypeRegistry.playS2C().register(ServuxLitematicsPayload.ID, ServuxLitematicsPayload.CODEC);
            ClientPlayNetworking.registerGlobalReceiver(ServuxLitematicsPayload.ID, (payload, context) -> onPayload(payload));
            registered = true;
        }
        catch (RuntimeException e)
        {
            registered = false;
            MaLiLib.LOGGER.warn("[{}] Servux area editing is unavailable: {}", Reference.MOD_ID, e.toString());
        }
    }

    /** Drops the handshake state whenever the client switches worlds. Called from the client tick. */
    public static void onClientTick(MinecraftClient client)
    {
        if (client.world != lastWorld)
        {
            lastWorld = client.world;
            handshakeSent = false;
            metadataSeen = false;
        }
    }

    /**
     * Warms the registration up while a group is previewed, so that the task request of the first
     * chain press is already accepted. Only one handshake is sent per world: a rejected handshake
     * counts towards Servux's failure limit, and enough failures blacklist the player until they
     * reconnect.
     */
    public static void prepare(ClientPlayerEntity player)
    {
        if (registered && handshakeSent == false && isUsable(player) && ClientPlayNetworking.canSend(CHANNEL_ID))
        {
            handshakeSent = sendHandshake();
        }
    }

    /**
     * Hands the group to the server as a single delete task.
     *
     * @return true when the request was sent, meaning the caller must not mine the blocks itself
     */
    public static boolean tryDelete(Collection<BlockPos> positions)
    {
        final ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (player == null || positions.isEmpty() || isUsable(player) == false
                || ClientPlayNetworking.canSend(CHANNEL_ID) == false)
        {
            return false;
        }

        if (metadataSeen == false)
        {
            // The reply of the handshake has not arrived yet, so this chain mines normally and the
            // next one can use the server side route.
            prepare(player);
            return false;
        }

        return sendTask(positions);
    }

    private static boolean isUsable(ClientPlayerEntity player)
    {
        return registered
                && Configs.getCreativeReachMode().usesAreaEdit()
                && player.isCreative();
    }

    private static void onPayload(ServuxLitematicsPayload payload)
    {
        // Any reply on this channel means Servux answered the handshake, which is the only thing this
        // mod needs to know: the packet body itself (box NBT replies, task status) is not used.
        metadataSeen = true;
    }

    private static boolean sendHandshake()
    {
        final NbtCompound nbt = new NbtCompound();
        nbt.putInt("version", PROTOCOL_VERSION);

        final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());

        try
        {
            buf.writeVarInt(PACKET_C2S_METADATA_REQUEST);
            // Vanilla framing on purpose: Servux reads this back with PacketByteBuf#readNbt.
            buf.writeNbt(nbt);
            ClientPlayNetworking.send(new ServuxLitematicsPayload(toByteArray(buf)));
            return true;
        }
        catch (RuntimeException e)
        {
            MaLiLib.LOGGER.warn("[{}] Failed to send the Servux handshake: {}", Reference.MOD_ID, e.toString());
            return false;
        }
        finally
        {
            buf.release();
        }
    }

    private static boolean sendTask(Collection<BlockPos> positions)
    {
        final List<BlockPos> batch = new ArrayList<>(MAX_BOXES_PER_PACKET);
        boolean sent = false;

        for (BlockPos pos : positions)
        {
            batch.add(pos);

            if (batch.size() >= MAX_BOXES_PER_PACKET)
            {
                sent |= sendTaskBatch(batch);
                batch.clear();
            }
        }

        if (batch.isEmpty() == false)
        {
            sent |= sendTaskBatch(batch);
        }

        return sent;
    }

    private static boolean sendTaskBatch(List<BlockPos> batch)
    {
        final NbtList boxes = new NbtList();

        for (BlockPos pos : batch)
        {
            // BlockPos.CODEC is an int stream, so let it produce the tag instead of guessing the shape.
            final NbtElement corner = BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, pos).result().orElse(null);

            if (corner == null)
            {
                continue;
            }

            final NbtCompound box = new NbtCompound();
            box.put("pos1", corner);
            box.put("pos2", corner);
            // Servux drops a box without a name.
            box.putString("name", BOX_NAME);
            boxes.add(box);
        }

        if (boxes.isEmpty())
        {
            return false;
        }

        final NbtCompound task = new NbtCompound();
        task.putString("Task", "Delete");
        task.put("Boxes", boxes);
        // Must be a byte tag; Servux ignores any other type.
        task.putBoolean("RemoveEntities", false);
        task.putInt("Interval", 1);

        final byte[] payload = compress(task);

        if (payload == null)
        {
            return false;
        }

        final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());

        try
        {
            buf.writeVarInt(PACKET_C2S_TASK_REQUEST);
            buf.writeInt(payload.length);
            buf.writeBytes(payload);
            ClientPlayNetworking.send(new ServuxLitematicsPayload(toByteArray(buf)));
            MaLiLib.LOGGER.info("[{}] Sent a Servux delete task for {} block(s)", Reference.MOD_ID, boxes.size());
            return true;
        }
        catch (RuntimeException e)
        {
            MaLiLib.LOGGER.warn("[{}] Failed to send a Servux task: {}", Reference.MOD_ID, e.toString());
            return false;
        }
        finally
        {
            buf.release();
        }
    }

    private static byte[] compress(NbtCompound nbt)
    {
        try
        {
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes)))
            {
                // The unnamed NBT stream (type byte, empty root name, body) that Servux decompresses.
                NbtIo.writeCompound(nbt, out);
            }

            return bytes.toByteArray();
        }
        catch (IOException e)
        {
            MaLiLib.LOGGER.warn("[{}] Failed to compress a Servux task: {}", Reference.MOD_ID, e.toString());
            return null;
        }
    }

    private static byte[] toByteArray(PacketByteBuf buf)
    {
        final byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }
}
