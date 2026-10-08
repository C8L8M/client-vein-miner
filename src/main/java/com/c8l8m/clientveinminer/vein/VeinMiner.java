/*
 * Client-Side Vein Miner - Copyright (c) 2026 C8L8M
 * SPDX-License-Identifier: LGPL-3.0-only
 * See LICENSE and licenses/GPL-3.0.txt in the project root.
 */
package com.c8l8m.clientveinminer.vein;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import com.c8l8m.clientveinminer.config.Configs;
import com.c8l8m.clientveinminer.mixin.ClientPlayerInteractionManagerAccessor;
import com.c8l8m.clientveinminer.mixin.ClientWorldAccessor;
import com.c8l8m.clientveinminer.servux.ServuxAreaEdit;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

/**
 * Chain mining state machine.
 *
 * IDLE    - nothing shown.
 * PREVIEW - the chain hotkey is held and the crosshair points at a chainable block:
 *           the whole connected group is collected and drawn in the preview colour, with a HUD count.
 * ACTIVE  - the player broke one of the group blocks, so the rest of the group became targets.
 *           Reachable targets are drawn green, unreachable ones red, and the reachable ones are
 *           mined one by one. Ends when every target is gone, the auto stop fires or the force-stop
 *           key is used.
 */
public final class VeinMiner
{
    public enum State
    {
        IDLE,
        PREVIEW,
        ACTIVE
    }

    /** Give up on a single target after this many ticks so one unbreakable block cannot stall the chain. */
    private static final int MAX_TICKS_PER_TARGET = 200;
    /** Re-collect the preview group at least this often while the crosshair stays on the same block. */
    private static final int PREVIEW_REFRESH_TICKS = 4;

    /** The server accepts a stop once delta * (elapsed + 1) >= 0.7; the config cannot go below that. */
    private static final double SERVER_STOP_THRESHOLD = 0.7D;
    private static final int TICKS_PER_SECOND = 20;

    /** All 26 surrounding positions, so diagonal neighbours are part of the same vein. */
    private static final int[][] NEIGHBOUR_OFFSETS = buildNeighbourOffsets();

    private static State state = State.IDLE;

    private static final Set<BlockPos> previewTargets = new LinkedHashSet<>();
    private static BlockPos previewOrigin;
    private static int previewRefresh;

    private static final Set<BlockPos> targets = new LinkedHashSet<>();
    private static final Set<BlockPos> reachable = new LinkedHashSet<>();
    private static final Set<BlockPos> unreachable = new LinkedHashSet<>();
    private static final Set<BlockPos> skipped = new HashSet<>();

    private static BlockPos currentTarget;
    private static int currentTicks;
    private static int breakCooldown;

    /** Consecutive ticks in which nothing was mined, used by the auto stop timeout. */
    private static int idleTicks;
    /** Set while this tick actually broke something or made progress on a block. */
    private static boolean minedThisTick;

    /** Fast break: we send the start/stop packets ourselves so the stop goes out as early as the server accepts. */
    private static boolean fastBreakMining;
    private static int fastBreakStopTick;

    private VeinMiner()
    {
    }

    public static State getState()
    {
        return state;
    }

    public static boolean isActive()
    {
        return state == State.ACTIVE;
    }

    public static Set<BlockPos> getPreviewTargets()
    {
        return previewTargets;
    }

    public static Set<BlockPos> getReachable()
    {
        return reachable;
    }

    public static Set<BlockPos> getUnreachable()
    {
        return unreachable;
    }

    public static int getPreviewCount()
    {
        return previewTargets.size();
    }

    public static int getRemainingCount()
    {
        return targets.size();
    }

    // ------------------------------------------------------------------ tick

    public static void tick(MinecraftClient client)
    {
        ServuxAreaEdit.onClientTick(client);

        final ClientWorld world = client.world;
        final ClientPlayerEntity player = client.player;
        final ClientPlayerInteractionManager interactionManager = client.interactionManager;

        if (world == null || player == null || interactionManager == null)
        {
            stop();
            return;
        }

        // Single player "pauses" by skipping the world tick, but the client tick loop - and with it
        // this state machine - keeps running, and the integrated server still processes the packets
        // we send. Without this the chain went on mining behind the pause menu. While paused nothing
        // advances at all: no mining, no preview and no auto stop countdown (the idle counter is left
        // untouched, so it resumes exactly where it stopped).
        if (client.isPaused())
        {
            return;
        }

        if (state == State.ACTIVE)
        {
            // Switching the mod or the feature off while a chain is running ends it there.
            if (Configs.isChainMiningEnabled() == false)
            {
                stop();
                return;
            }

            if (Configs.Generic.STOP_ON_KEY_RELEASE.getBooleanValue() && isActivateHeld() == false)
            {
                stop();
                return;
            }

            minedThisTick = false;
            final int targetsBefore = targets.size();

            driveMining(world, player, interactionManager);

            // driveMining stops the chain itself once the last target is gone.
            if (state != State.ACTIVE)
            {
                return;
            }

            if (updateIdleTimeout(targetsBefore))
            {
                stop();
            }

            return;
        }

        // A chain can only be started from a screen-free in-world state; don't preview either.
        if (Configs.isChainMiningEnabled() && client.currentScreen == null && isActivateHeld())
        {
            final BlockPos origin = getCrosshairBlock(client);

            if (origin != null && isChainable(world, origin, world.getBlockState(origin)))
            {
                if (state != State.PREVIEW || previewOrigin == null || previewOrigin.equals(origin) == false)
                {
                    collectGroup(world, origin, previewTargets);
                    previewOrigin = origin;
                    previewRefresh = PREVIEW_REFRESH_TICKS;
                }
                else if (--previewRefresh <= 0)
                {
                    collectGroup(world, origin, previewTargets);
                    previewRefresh = PREVIEW_REFRESH_TICKS;
                }

                // Ask the server for the task permission while the group is previewed, so that the
                // first chain press can already use the area edit route.
                ServuxAreaEdit.prepare(player);

                state = State.PREVIEW;
                return;
            }
        }

        clearPreview();
    }

    /** Called (from the mixin) right before a block is actually broken client side. */
    public static void onBlockBroken(ClientWorld world, BlockPos pos)
    {
        if (state == State.ACTIVE || Configs.isChainMiningEnabled() == false || isActivateHeld() == false)
        {
            return;
        }

        if (isChainable(world, pos, world.getBlockState(pos)) == false)
        {
            return;
        }

        startChain(world, pos);
    }

    public static void stop()
    {
        final MinecraftClient client = MinecraftClient.getInstance();

        if (currentTarget != null && client != null && client.interactionManager != null)
        {
            client.interactionManager.cancelBlockBreaking();
        }

        state = State.IDLE;
        previewOrigin = null;
        previewRefresh = 0;
        previewTargets.clear();
        targets.clear();
        reachable.clear();
        unreachable.clear();
        skipped.clear();
        currentTarget = null;
        currentTicks = 0;
        breakCooldown = 0;
        idleTicks = 0;
        minedThisTick = false;
        fastBreakMining = false;
        fastBreakStopTick = 0;
    }

    // ------------------------------------------------------------- internals

    /**
     * Tracks how long nothing has been mined. While any target is reachable blocks keep breaking, so
     * a timeout here means the chain is stuck waiting - typically because every remaining target is
     * out of reach - and it ends by itself instead of hanging around forever.
     *
     * Holding the chain hotkey suspends this completely: the counter is reset every tick while the key
     * is down, so the countdown only starts running once the key is released.
     *
     * @return true when the timeout expired and the chain should stop
     */
    private static boolean updateIdleTimeout(int targetsBefore)
    {
        if (isActivateHeld())
        {
            idleTicks = 0;
            return false;
        }

        final int timeout = autoStopTimeoutTicks();

        if (minedThisTick || targets.size() < targetsBefore)
        {
            idleTicks = 0;
            return false;
        }

        return timeout > 0 && ++idleTicks > timeout;
    }

    /** Ticks without progress after which the auto stop fires; 0 when the auto stop is switched off. */
    private static int autoStopTimeoutTicks()
    {
        return Math.max(0, Configs.Generic.AUTO_STOP_SECONDS.getIntegerValue()) * TICKS_PER_SECOND;
    }

    /**
     * Seconds left until the auto stop ends the chain, rounded up, for the HUD.
     *
     * @return -1 while no countdown is running (the auto stop is switched off, or the chain hotkey is
     *         still held and the countdown has not started yet)
     */
    public static int getAutoStopRemainingSeconds()
    {
        final int timeout = autoStopTimeoutTicks();

        if (timeout <= 0 || isActivateHeld())
        {
            return -1;
        }

        final int left = timeout - idleTicks;
        return left <= 0 ? 0 : (left + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
    }

    /**
     * True when the reach check may be lifted: the creative reach mode allows it and this client hosts
     * the world.
     *
     * The reach limit is enforced by the server side of the interaction manager, and in single player
     * that server runs in this very JVM, so the PlayerEntityReachMixin can lift the check completely -
     * the attribute route (raising the interaction range) could never go past its hard clamp of 64
     * blocks. On a dedicated server only the Servux area edit path (mode "all") can ignore distance.
     */
    public static boolean ignoresDistance(PlayerEntity player)
    {
        if (Configs.getCreativeReachMode().ignoresReach() == false || player.isCreative() == false)
        {
            return false;
        }

        final MinecraftClient client = MinecraftClient.getInstance();

        return client != null && client.isIntegratedServerRunning();
    }

    /** True while the chain hotkey is held; also selects the see-through target rendering. */
    public static boolean isActivateHeld()
    {
        return Configs.Hotkeys.ACTIVATE.getKeybind().isKeybindHeld();
    }

    private static void startChain(ClientWorld world, BlockPos origin)
    {
        collectGroup(world, origin, targets);
        reachable.clear();
        unreachable.clear();
        skipped.clear();
        previewTargets.clear();
        previewOrigin = null;
        previewRefresh = 0;
        currentTarget = null;
        currentTicks = 0;
        breakCooldown = 0;
        idleTicks = 0;
        minedThisTick = false;
        fastBreakMining = false;
        fastBreakStopTick = 0;

        if (targets.isEmpty())
        {
            state = State.IDLE;
            return;
        }

        // On a server running Servux the whole group can be removed with a single area edit task,
        // which is the only distance free route there: the reach check never runs for that path.
        if (ServuxAreaEdit.tryDelete(targets))
        {
            stop();
            return;
        }

        state = State.ACTIVE;
    }

    private static void clearPreview()
    {
        if (state == State.PREVIEW)
        {
            state = State.IDLE;
        }

        if (previewTargets.isEmpty() == false)
        {
            previewTargets.clear();
        }

        previewOrigin = null;
        previewRefresh = 0;
    }

    private static void driveMining(ClientWorld world, ClientPlayerEntity player, ClientPlayerInteractionManager interactionManager)
    {
        targets.removeIf(pos -> world.getBlockState(pos).isAir());

        if (targets.isEmpty())
        {
            stop();
            return;
        }

        // Vanilla hard-codes a 5 tick cooldown after every broken block, which is why a configured
        // mining interval of 0 still had a gap. Clear it so the configured interval is the only
        // delay between blocks.
        if (interactionManager instanceof ClientPlayerInteractionManagerAccessor accessor)
        {
            accessor.clientVeinMiner$setBreakCooldown(0);
        }

        reachable.clear();
        unreachable.clear();

        for (BlockPos pos : targets)
        {
            if (isReachable(world, player, pos))
            {
                reachable.add(pos);
            }
            else
            {
                unreachable.add(pos);
            }
        }

        if (currentTarget != null && (targets.contains(currentTarget) == false || reachable.contains(currentTarget) == false))
        {
            if (fastBreakMining == false)
            {
                interactionManager.cancelBlockBreaking();
            }

            // The target was broken, so wait out the configured interval before the next block.
            if (targets.contains(currentTarget) == false)
            {
                breakCooldown = currentInterval();
            }

            currentTarget = null;
            currentTicks = 0;
            fastBreakMining = false;
        }

        if (currentTarget != null && ++currentTicks > MAX_TICKS_PER_TARGET)
        {
            if (fastBreakMining == false)
            {
                interactionManager.cancelBlockBreaking();
            }

            skipped.add(currentTarget);
            currentTarget = null;
            currentTicks = 0;
            fastBreakMining = false;
        }

        // Creative mode breaking is instant server side, so with the mining interval at 0 every
        // reachable target can be finished in the same tick. There is deliberately no separate
        // switch for this: it follows from the interval alone, the creative path has no rate limit,
        // and it is plain vanilla behaviour, so it works on dedicated servers too. Survival cannot
        // do this - there the server recounts the break progress itself and ignores extra starts.
        if (player.isCreative() && currentInterval() == 0)
        {
            boolean attacked = false;

            for (BlockPos pos : reachable)
            {
                if (skipped.contains(pos) == false)
                {
                    interactionManager.attackBlock(pos, sideFor(player, pos));
                    attacked = true;
                }
            }

            if (attacked)
            {
                minedThisTick = true;
                player.swingHand(Hand.MAIN_HAND);
            }

            currentTarget = null;
            currentTicks = 0;
            breakCooldown = 0;
            return;
        }

        // A threshold of 100 means "wait for the full break", which is exactly vanilla timing, so the
        // plain vanilla progress path is used and no packets are sent by hand.
        final boolean fastBreak = Configs.Generic.MINING_PROGRESS_THRESHOLD.getIntegerValue() < 100 && player.isCreative() == false;

        // Continue a break that is already in progress.
        if (currentTarget != null)
        {
            driveCurrentTarget(world, player, interactionManager, fastBreak);
            return;
        }

        if (breakCooldown > 0)
        {
            breakCooldown--;
            return;
        }

        // Sweep every target that a single hit finishes. For those the server breaks the block on the
        // start packet alone and leaves no mining state behind, so all of them can go in the same
        // tick. This is plain vanilla behaviour - a strong enough tool plus haste pushes the break
        // delta to 1.0 and the block pops instantly - and it is what makes a mining interval of 0
        // actually feel instant. Vanilla's own client side attackBlock makes the same decision.
        final int swept = sweepInstantTargets(world, player);

        if (swept > 0)
        {
            minedThisTick = true;
            player.swingHand(Hand.MAIN_HAND);
            breakCooldown = currentInterval();

            if (breakCooldown > 0)
            {
                return;
            }
        }

        final BlockPos next = pickNearest(player);

        if (next == null)
        {
            return;
        }

        minedThisTick = true;

        if (fastBreak)
        {
            startFastProgress(world, player, next);
        }
        else
        {
            interactionManager.attackBlock(next, sideFor(player, next));
            currentTarget = next;
            currentTicks = 0;
            fastBreakMining = false;
        }
    }

    /** Keeps the current progress based break going, either vanilla driven or with our own packets. */
    private static void driveCurrentTarget(ClientWorld world, ClientPlayerEntity player,
                                           ClientPlayerInteractionManager interactionManager, boolean fastBreak)
    {
        minedThisTick = true;

        if (fastBreak == false || fastBreakMining == false)
        {
            interactionManager.updateBlockBreakingProgress(currentTarget, sideFor(player, currentTarget));
            // The vanilla path that normally swings the arm is suppressed while a chain runs.
            player.swingHand(Hand.MAIN_HAND);
            return;
        }

        if (currentTicks >= fastBreakStopTick)
        {
            world.setBlockBreakingInfo(player.getId(), currentTarget, -1);
            sendBreakPacket(world, player, currentTarget, PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK);
            skipped.add(currentTarget);
            breakCooldown = currentInterval();
            currentTarget = null;
            currentTicks = 0;
            fastBreakMining = false;
            return;
        }

        // Show the vanilla breaking overlay, otherwise the block would just pop out of existence.
        final float ratio = fastBreakStopTick <= 0
                ? 1.0F
                : (float) currentTicks / (float) (fastBreakStopTick + 1);
        world.setBlockBreakingInfo(player.getId(), currentTarget, (int) (Math.min(1.0F, ratio) * 9.0F));
        player.swingHand(Hand.MAIN_HAND);
    }

    /**
     * Sends the start packet for a block, and the stop packet once the configured break progress
     * threshold is reached instead of waiting for the client's own progress to reach 100%. The server
     * never trusts a client declared break - it recounts the progress itself - so this is also the
     * fastest a client can legitimately go: a stop below 0.7 of the server's own progress is ignored.
     */
    private static void startFastProgress(ClientWorld world, ClientPlayerEntity player, BlockPos pos)
    {
        final float delta = world.getBlockState(pos).calcBlockBreakingDelta(player, world, pos);
        final int stopTick = ticksUntilServerAccepts(delta);

        sendBreakPacket(world, player, pos, PlayerActionC2SPacket.Action.START_DESTROY_BLOCK);

        if (stopTick < 0)
        {
            // One hit block: the server already broke it on the start packet, nothing more to send.
            skipped.add(pos);
            breakCooldown = currentInterval();
            return;
        }

        currentTarget = pos;
        currentTicks = 0;
        fastBreakMining = true;
        fastBreakStopTick = stopTick;
    }

    /**
     * Breaks every reachable target that a single hit finishes, all in this tick.
     *
     * @return the number of blocks that were started
     */
    private static int sweepInstantTargets(ClientWorld world, ClientPlayerEntity player)
    {
        final int max = Math.max(1, Configs.Generic.MAX_BLOCKS.getIntegerValue());
        int swept = 0;

        while (swept < max)
        {
            final BlockPos next = pickNearest(player);

            if (next == null)
            {
                break;
            }

            final float delta = world.getBlockState(next).calcBlockBreakingDelta(player, world, next);

            if (delta < 1.0F)
            {
                break;
            }

            sendBreakPacket(world, player, next, PlayerActionC2SPacket.Action.START_DESTROY_BLOCK);
            skipped.add(next);
            swept++;
        }

        return swept;
    }

    /** The break progress fraction after which the stop packet is sent, from the config. */
    private static double stopThreshold()
    {
        final int percent = Math.max(70, Math.min(100, Configs.Generic.MINING_PROGRESS_THRESHOLD.getIntegerValue()));
        return Math.max(SERVER_STOP_THRESHOLD, percent / 100.0D);
    }

    /**
     * Ticks we should wait before sending the stop, or -1 when no stop is needed because the server
     * breaks the block on the start packet (delta >= 1, i.e. instant mine blocks).
     */
    private static int ticksUntilServerAccepts(float delta)
    {
        if (delta <= 0.0F)
        {
            return -1;
        }

        if (delta >= 1.0F)
        {
            return -1;
        }

        return Math.max(1, (int) Math.ceil(stopThreshold() / delta) - 1);
    }

    private static void sendBreakPacket(ClientWorld world, ClientPlayerEntity player, BlockPos pos, PlayerActionC2SPacket.Action action)
    {
        final int sequence = ((ClientWorldAccessor) world).clientVeinMiner$getPendingUpdateManager()
                .incrementSequence().getSequence();

        player.networkHandler.sendPacket(new PlayerActionC2SPacket(action, pos, sideFor(player, pos), sequence));
    }

    private static int currentInterval()
    {
        return Math.max(0, Configs.Generic.MINING_INTERVAL.getIntegerValue());
    }

    private static BlockPos getCrosshairBlock(MinecraftClient client)
    {
        final HitResult hit = client.crosshairTarget;

        if (hit != null && hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit)
        {
            return blockHit.getBlockPos();
        }

        return null;
    }

    private static void collectGroup(ClientWorld world, BlockPos origin, Set<BlockPos> out)
    {
        out.clear();

        final BlockState originState = world.getBlockState(origin);

        if (isChainable(world, origin, originState) == false)
        {
            return;
        }

        final Block originBlock = originState.getBlock();
        final int max = Math.max(1, Configs.Generic.MAX_BLOCKS.getIntegerValue());
        final ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        out.add(origin);
        queue.add(origin);

        while (queue.isEmpty() == false && out.size() < max)
        {
            final BlockPos pos = queue.poll();

            for (int[] offset : NEIGHBOUR_OFFSETS)
            {
                if (out.size() >= max)
                {
                    break;
                }

                final BlockPos next = pos.add(offset[0], offset[1], offset[2]);

                if (out.contains(next))
                {
                    continue;
                }

                final BlockState nextState = world.getBlockState(next);

                if (matches(originBlock, originState, nextState) && isChainable(world, next, nextState))
                {
                    out.add(next);
                    queue.add(next);
                }
            }
        }
    }

    private static boolean matches(Block originBlock, BlockState originState, BlockState state)
    {
        if (Configs.Generic.MATCH_EXACT_STATE.getBooleanValue())
        {
            return state.equals(originState);
        }

        return state.getBlock() == originBlock;
    }

    private static boolean isChainable(ClientWorld world, BlockPos pos, BlockState state)
    {
        if (state.isAir() || state.getHardness(world, pos) < 0.0F)
        {
            return false;
        }

        if (Configs.Generic.IGNORE_TILE_ENTITIES.getBooleanValue() && state.hasBlockEntity())
        {
            return false;
        }

        final String id = Registries.BLOCK.getId(state.getBlock()).toString();
        return Configs.Generic.BLOCK_BLACKLIST.getStrings().contains(id) == false;
    }

    /**
     * Distance only. When the single player "ignore distance" option applies the range is unlimited;
     * otherwise the configured value is used, or the vanilla block interaction range when it is 0.
     */
    private static boolean inRange(ClientPlayerEntity player, BlockPos pos)
    {
        if (ignoresDistance(player))
        {
            return true;
        }

        final double reach = Configs.Generic.REACH_DISTANCE.getDoubleValue();

        if (reach > 0.0D)
        {
            final Vec3d eye = player.getEyePos();
            return eye.squaredDistanceTo(Vec3d.ofCenter(pos)) <= (reach * reach);
        }

        // Vanilla semantics: the player's block interaction range against the block's shape.
        return player.canInteractWithBlockAt(pos, 0.0D);
    }

    private static boolean isReachable(ClientWorld world, ClientPlayerEntity player, BlockPos pos)
    {
        if (inRange(player, pos) == false)
        {
            return false;
        }

        if (Configs.Generic.REQUIRE_LINE_OF_SIGHT.getBooleanValue() == false)
        {
            return true;
        }

        final Vec3d eye = player.getEyePos();
        final BlockState state = world.getBlockState(pos);
        VoxelShape shape = state.getRaycastShape(world, pos);

        if (shape.isEmpty())
        {
            shape = state.getOutlineShape(world, pos);
        }

        // Thin blocks such as vines and ladders do not contain the block centre, so aim at the centre
        // of the block's own shape. Aiming at the block centre makes the ray slip past them and hit
        // the wall behind, which used to make those blocks permanently "unreachable".
        final Box shapeBox = shape.isEmpty() ? new Box(pos) : shape.getBoundingBox().offset(pos);

        if (shapeBox.contains(eye.x, eye.y, eye.z))
        {
            return true;
        }

        final RaycastContext context = new RaycastContext(eye, shapeBox.getCenter(),
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player);
        final BlockHitResult result = world.raycast(context);

        return result.getType() == HitResult.Type.BLOCK && result.getBlockPos().equals(pos);
    }

    private static BlockPos pickNearest(ClientPlayerEntity player)
    {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        final Vec3d eye = player.getEyePos();

        for (BlockPos pos : reachable)
        {
            if (skipped.contains(pos))
            {
                continue;
            }

            final double distance = eye.squaredDistanceTo(Vec3d.ofCenter(pos));

            if (distance < bestDistance)
            {
                bestDistance = distance;
                best = pos;
            }
        }

        return best;
    }

    /** The block face that points towards the player, so breaking particles appear on the visible side. */
    private static Direction sideFor(ClientPlayerEntity player, BlockPos pos)
    {
        final Vec3d eye = player.getEyePos();
        final Vec3d center = Vec3d.ofCenter(pos);
        final double dx = eye.x - center.x;
        final double dy = eye.y - center.y;
        final double dz = eye.z - center.z;
        final double ax = Math.abs(dx);
        final double ay = Math.abs(dy);
        final double az = Math.abs(dz);

        if (ax >= ay && ax >= az)
        {
            return dx >= 0.0D ? Direction.EAST : Direction.WEST;
        }

        if (ay >= az)
        {
            return dy >= 0.0D ? Direction.UP : Direction.DOWN;
        }

        return dz >= 0.0D ? Direction.SOUTH : Direction.NORTH;
    }

    private static int[][] buildNeighbourOffsets()
    {
        final int[][] offsets = new int[26][3];
        int index = 0;

        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dy = -1; dy <= 1; dy++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    if (dx == 0 && dy == 0 && dz == 0)
                    {
                        continue;
                    }

                    offsets[index][0] = dx;
                    offsets[index][1] = dy;
                    offsets[index][2] = dz;
                    index++;
                }
            }
        }

        return offsets;
    }
}
