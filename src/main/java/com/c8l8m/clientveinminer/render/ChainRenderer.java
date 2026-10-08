/*
 * Client-Side Vein Miner - Copyright (c) 2026 C8L8M
 * SPDX-License-Identifier: LGPL-3.0-only
 * See LICENSE and licenses/GPL-3.0.txt in the project root.
 */
package com.c8l8m.clientveinminer.render;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.c8l8m.clientveinminer.config.Configs;
import com.c8l8m.clientveinminer.vein.VeinMiner;

import fi.dy.masa.malilib.util.data.Color4f;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

/**
 * Draws the block highlights, using vanilla's gizmo/shape renderer.
 *
 * Preview group: the OUTLINE OF THE WHOLE GROUP, not of the individual blocks. Only the faces that
 * are not shared with another block of the group are collected, and of the edges of those faces only
 * the ones where two faces actually meet at an angle survive, so the vein reads as one single shape
 * without any line running through it. Drawn opaque and see-through.
 *
 * Chain targets are drawn in one of two ways, depending on whether the chain hotkey is held:
 * held = the 1.0.10 way, only the outer shell of the group, drawn through blocks; released = the
 * 1.0.7 way, one translucent box per target, depth tested so blocks in front hide it. In both modes
 * the fill alpha comes from the highlight opacity option (the colours are plain RGB, no alpha).
 */
public final class ChainRenderer
{
    private ChainRenderer()
    {
    }

    public static void register()
    {
        WorldRenderEvents.BEFORE_ENTITIES.register(ChainRenderer::onRender);
    }

    private static void onRender(WorldRenderContext context)
    {
        final Set<BlockPos> preview = VeinMiner.getPreviewTargets();
        final Set<BlockPos> reachable = VeinMiner.getReachable();
        final Set<BlockPos> unreachable = VeinMiner.getUnreachable();

        if (preview.isEmpty() && reachable.isEmpty() && unreachable.isEmpty())
        {
            return;
        }

        final WorldRenderer worldRenderer = context.worldRenderer();

        if (worldRenderer == null)
        {
            return;
        }

        final boolean outline = Configs.Generic.RENDER_OUTLINE.getBooleanValue();
        final float width = (float) Math.max(1.0D, Configs.Generic.LINE_WIDTH.getDoubleValue());
        final int opacity = Math.max(0, Math.min(100, Configs.Generic.HIGHLIGHT_OPACITY.getIntegerValue()));
        final int highlightAlpha = (opacity * 255) / 100;

        // The rendering mode follows the hotkey: held = 1.0.10 (outer shell of the group, through
        // blocks), released = 1.0.7 (one box per target, depth tested).
        final boolean held = VeinMiner.isActivateHeld();

        try (GizmoDrawing.CollectorScope scope = worldRenderer.startDrawingGizmos())
        {
            if (preview.isEmpty() == false)
            {
                // The preview colour is a plain RGB value and the outline never uses an alpha channel.
                drawGroupOutline(preview, opaque(Configs.Generic.PREVIEW_COLOR.getColor()), width);
            }

            // The two target sets are one shape as far as the shell is concerned: the border between a
            // reachable and an unreachable block is an interior face of the group, not a surface.
            final Set<BlockPos> group = held ? union(reachable, unreachable) : Set.of();

            drawTargets(reachable, group, held, Configs.Generic.REACHABLE_COLOR.getColor(), outline, width, highlightAlpha);
            drawTargets(unreachable, group, held, Configs.Generic.UNREACHABLE_COLOR.getColor(), outline, width, highlightAlpha);
        }
    }

    private static Set<BlockPos> union(Set<BlockPos> first, Set<BlockPos> second)
    {
        final Set<BlockPos> all = new HashSet<>(first);
        all.addAll(second);
        return all;
    }

    /**
     * Outline of the whole group: the faces that border a block outside the group, reduced to the
     * edges that form the silhouette of the group.
     *
     * Every such face contributes its four edges. An edge is only drawn when at least two of the
     * faces meeting along it are NOT parallel - where two coplanar faces meet (the seam between two
     * neighbouring group blocks, or the shared edge of two faces of the same block that lie in the
     * same plane) the surface just continues, so no line is drawn there. A single block therefore
     * still gets its full cube outline, only the internal seams disappear.
     */
    private static void drawGroupOutline(Set<BlockPos> group, int argb, float width)
    {
        final Map<String, Edge> edges = new HashMap<>();

        for (BlockPos pos : group)
        {
            for (Direction dir : Direction.values())
            {
                if (group.contains(pos.offset(dir)))
                {
                    continue;
                }

                final int[][] corners = faceCorners(dir);

                for (int i = 0; i < 4; i++)
                {
                    final double[] a = vertex(pos, corners[i]);
                    final double[] b = vertex(pos, corners[(i + 1) % 4]);

                    edges.computeIfAbsent(edgeKey(a, b), key -> new Edge(a, b, EnumSet.noneOf(Direction.class)))
                         .faces().add(dir);
                }
            }
        }

        for (Edge edge : edges.values())
        {
            if (edge.faces().size() < 2)
            {
                continue;
            }

            GizmoDrawing.line(toVec(edge.a()), toVec(edge.b()), argb, width).ignoreOcclusion();
        }
    }

    /** An edge of the collected surface, together with the directions of the faces that contain it. */
    private record Edge(double[] a, double[] b, EnumSet<Direction> faces)
    {
    }

    private static double[] vertex(BlockPos pos, int[] corner)
    {
        return new double[] {pos.getX() + corner[0], pos.getY() + corner[1], pos.getZ() + corner[2]};
    }

    private static Vec3d toVec(double[] vertex)
    {
        return new Vec3d(vertex[0], vertex[1], vertex[2]);
    }

    private static String edgeKey(double[] a, double[] b)
    {
        final String first = pointKey(a);
        final String second = pointKey(b);

        return first.compareTo(second) <= 0 ? first + "/" + second : second + "/" + first;
    }

    private static String pointKey(double[] point)
    {
        return (int) point[0] + "," + (int) point[1] + "," + (int) point[2];
    }

    /** The four corners of the unit cube face on the given side, in order around the face. */
    private static int[][] faceCorners(Direction dir)
    {
        return switch (dir)
        {
            case DOWN  -> new int[][] {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}};
            case UP    -> new int[][] {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
            case NORTH -> new int[][] {{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}};
            case SOUTH -> new int[][] {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}};
            case WEST  -> new int[][] {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}};
            case EAST  -> new int[][] {{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}};
        };
    }

    /**
     * Draws one of the two target sets. With {@code xray} (hoitkey held) only the faces whose
     * neighbour is not part of the whole group are drawn, through blocks; otherwise every target gets
     * a complete depth tested box, the way 1.0.7 drew them.
     */
    private static void drawTargets(Set<BlockPos> positions, Set<BlockPos> group, boolean xray, Color4f color,
                                    boolean outline, float width, int alpha)
    {
        if (positions.isEmpty())
        {
            return;
        }

        final int fill = withAlpha(color, alpha);
        // The border stays opaque even when the fill is translucent, so that it remains readable.
        final int stroke = opaque(color);
        final DrawStyle style = outline
                ? DrawStyle.filledAndStroked(stroke, width, fill)
                : DrawStyle.filled(fill);

        if (xray)
        {
            // Skipping the faces whose neighbour is a target as well keeps two translucent layers off
            // the same pixels: that double drawing darkened every seam of the group.
            for (BlockPos pos : positions)
            {
                final Vec3d min = new Vec3d(pos.getX(), pos.getY(), pos.getZ());
                final Vec3d max = min.add(1.0D, 1.0D, 1.0D);

                for (Direction dir : Direction.values())
                {
                    if (group.contains(pos.offset(dir)))
                    {
                        continue;
                    }

                    GizmoDrawing.face(min, max, dir, style).ignoreOcclusion();
                }
            }

            return;
        }

        for (BlockPos pos : positions)
        {
            // Deliberately not ignoring occlusion here, see the class comment.
            GizmoDrawing.box(pos, style);
        }
    }

    private static int opaque(Color4f color)
    {
        return ColorHelper.fromFloats(1.0F, color.r, color.g, color.b);
    }

    private static int withAlpha(Color4f color, int alpha)
    {
        return ColorHelper.fromFloats(Math.max(0, Math.min(255, alpha)) / 255.0F, color.r, color.g, color.b);
    }
}
