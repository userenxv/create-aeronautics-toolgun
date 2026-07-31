package com.enxv.aeronauticsstructuretool.client.tool;

import com.enxv.aeronauticsstructuretool.WeldSelectionMode;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

public final class WeldSelectionGeometry {
    private WeldSelectionGeometry() {
    }

    public static Vec3 resolve(
            Level level,
            BlockHitResult hit,
            LocalPlayer player,
            WeldSelectionMode selectionMode,
            Vec3 projectedHit
    ) {
        if (player == null || selectionMode == WeldSelectionMode.FREE) {
            return projectedHit;
        }

        BlockState state = level.getBlockState(hit.getBlockPos());
        CollisionContext context = CollisionContext.of(player);
        VoxelShape shape = state.getCollisionShape(level, hit.getBlockPos(), context);
        if (shape.isEmpty()) {
            shape = state.getShape(level, hit.getBlockPos(), context);
        }
        if (shape.isEmpty()) {
            return projectedHit;
        }

        Vec3 snapped = null;
        double bestDistance = Double.MAX_VALUE;
        for (AABB box : shape.toAabbs()) {
            for (Vec3 candidate : createFaceSnapPoints(
                    hit.getBlockPos(),
                    box,
                    hit.getDirection(),
                    level,
                    selectionMode
            )) {
                double distance = candidate.distanceToSqr(projectedHit);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    snapped = candidate;
                }
            }
        }
        return snapped != null ? snapped : projectedHit;
    }

    private static List<Vec3> createFaceSnapPoints(
            BlockPos pos,
            AABB box,
            Direction face,
            Level level,
            WeldSelectionMode selectionMode
    ) {
        List<Vec3> points = new ArrayList<>(selectionMode == WeldSelectionMode.FACE_POINTS_18 ? 18 : 9);
        double xMin = pos.getX() + box.minX;
        double xMid = pos.getX() + (box.minX + box.maxX) * 0.5D;
        double xMax = pos.getX() + box.maxX;
        double yMin = pos.getY() + box.minY;
        double yMid = pos.getY() + (box.minY + box.maxY) * 0.5D;
        double yMax = pos.getY() + box.maxY;
        double zMin = pos.getZ() + box.minZ;
        double zMid = pos.getZ() + (box.minZ + box.maxZ) * 0.5D;
        double zMax = pos.getZ() + box.maxZ;
        switch (face) {
            case DOWN -> addPlanePoints(points, level, xMin, xMid, xMax, yMin, zMin, zMid, zMax, AxisPlane.XZ, selectionMode);
            case UP -> addPlanePoints(points, level, xMin, xMid, xMax, yMax, zMin, zMid, zMax, AxisPlane.XZ, selectionMode);
            case NORTH -> addPlanePoints(points, level, xMin, xMid, xMax, zMin, yMin, yMid, yMax, AxisPlane.XY, selectionMode);
            case SOUTH -> addPlanePoints(points, level, xMin, xMid, xMax, zMax, yMin, yMid, yMax, AxisPlane.XY, selectionMode);
            case WEST -> addPlanePoints(points, level, zMin, zMid, zMax, xMin, yMin, yMid, yMax, AxisPlane.ZY, selectionMode);
            case EAST -> addPlanePoints(points, level, zMin, zMid, zMax, xMax, yMin, yMid, yMax, AxisPlane.ZY, selectionMode);
        }
        return points;
    }

    private static void addPlanePoints(
            List<Vec3> points,
            Level level,
            double aMin,
            double aMid,
            double aMax,
            double fixed,
            double bMin,
            double bMid,
            double bMax,
            AxisPlane plane,
            WeldSelectionMode selectionMode
    ) {
        addProjectedPoint(points, level, plane, aMin, fixed, bMin);
        addProjectedPoint(points, level, plane, aMid, fixed, bMin);
        addProjectedPoint(points, level, plane, aMax, fixed, bMin);
        addProjectedPoint(points, level, plane, aMin, fixed, bMid);
        addProjectedPoint(points, level, plane, aMid, fixed, bMid);
        addProjectedPoint(points, level, plane, aMax, fixed, bMid);
        addProjectedPoint(points, level, plane, aMin, fixed, bMax);
        addProjectedPoint(points, level, plane, aMid, fixed, bMax);
        addProjectedPoint(points, level, plane, aMax, fixed, bMax);
        if (selectionMode != WeldSelectionMode.FACE_POINTS_18) {
            return;
        }

        double aQuarter = (aMin + aMid) * 0.5D;
        double aThreeQuarter = (aMid + aMax) * 0.5D;
        double bQuarter = (bMin + bMid) * 0.5D;
        double bThreeQuarter = (bMid + bMax) * 0.5D;
        addProjectedPoint(points, level, plane, aQuarter, fixed, bMin);
        addProjectedPoint(points, level, plane, aThreeQuarter, fixed, bMin);
        addProjectedPoint(points, level, plane, aMax, fixed, bQuarter);
        addProjectedPoint(points, level, plane, aMax, fixed, bThreeQuarter);
        addProjectedPoint(points, level, plane, aThreeQuarter, fixed, bMax);
        addProjectedPoint(points, level, plane, aQuarter, fixed, bMax);
        addProjectedPoint(points, level, plane, aMin, fixed, bThreeQuarter);
        addProjectedPoint(points, level, plane, aMin, fixed, bQuarter);
        addProjectedPoint(points, level, plane, aMid, fixed, bMid);
    }

    private static void addProjectedPoint(
            List<Vec3> points,
            Level level,
            AxisPlane plane,
            double first,
            double fixed,
            double second
    ) {
        Vec3 point = switch (plane) {
            case XZ -> new Vec3(first, fixed, second);
            case XY -> new Vec3(first, second, fixed);
            case ZY -> new Vec3(fixed, second, first);
        };
        Vec3 projected = Sable.HELPER.projectOutOfSubLevel(level, point);
        for (Vec3 existing : points) {
            if (existing.distanceToSqr(projected) <= 1.0E-8D) {
                return;
            }
        }
        points.add(projected);
    }

    private enum AxisPlane {
        XZ,
        XY,
        ZY
    }
}
