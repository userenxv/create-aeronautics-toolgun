package com.enxv.aeronauticsstructuretool.blueprint.placement;

import com.enxv.aeronauticsstructuretool.PlacementSnapMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.AxisAngle4d;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

public final class PlacementTargetMath {
    private static final double BLOCK_FACE_INSET = 1.0E-3D;

    private PlacementTargetMath() {
    }

    public static BlockPos resolveClickedBlock(Vec3 worldHit, Direction worldFace) {
        requireFinite(worldHit);
        if (worldFace == null) {
            throw new IllegalArgumentException("placement face is missing");
        }
        return BlockPos.containing(
                worldHit.x - worldFace.getStepX() * BLOCK_FACE_INSET,
                worldHit.y - worldFace.getStepY() * BLOCK_FACE_INSET,
                worldHit.z - worldFace.getStepZ() * BLOCK_FACE_INSET
        );
    }

    public static Direction projectFace(Quaterniondc orientation, Direction localFace) {
        if (orientation == null || localFace == null) {
            throw new IllegalArgumentException("placement face projection is incomplete");
        }
        Vector3d worldNormal = new Vector3d(
                localFace.getStepX(),
                localFace.getStepY(),
                localFace.getStepZ()
        );
        orientation.transform(worldNormal);
        if (!isFinite(worldNormal) || worldNormal.lengthSquared() <= 1.0E-12D) {
            throw new IllegalArgumentException("placement face projection is invalid");
        }
        worldNormal.normalize();

        Direction nearest = localFace;
        double bestDot = Double.NEGATIVE_INFINITY;
        for (Direction candidate : Direction.values()) {
            double dot = worldNormal.x * candidate.getStepX()
                    + worldNormal.y * candidate.getStepY()
                    + worldNormal.z * candidate.getStepZ();
            if (dot > bestDot) {
                bestDot = dot;
                nearest = candidate;
            }
        }
        return nearest;
    }

    public static Vector3d computePlacementTarget(
            BlockPos clickedPos,
            Direction face,
            double hitX,
            double hitY,
            double hitZ,
            PlacementSnapMode snapMode,
            int offsetX,
            int offsetY,
            int offsetZ
    ) {
        Vector3d target = switch (snapMode) {
            case FACE_CENTER -> new Vector3d(
                    clickedPos.getX() + 0.5D + face.getStepX() * 1.5D,
                    clickedPos.getY() + 0.5D + face.getStepY() * 1.5D,
                    clickedPos.getZ() + 0.5D + face.getStepZ() * 1.5D
            );
            case HIT -> new Vector3d(
                    hitX + face.getStepX() * 0.9D,
                    hitY + face.getStepY() * 0.9D,
                    hitZ + face.getStepZ() * 0.9D
            );
            case LEGACY -> new Vector3d(
                    clickedPos.getX() + 0.5D + face.getStepX(),
                    clickedPos.getY() + (face == Direction.UP ? 3.0D : 2.5D) + face.getStepY(),
                    clickedPos.getZ() + 0.5D + face.getStepZ()
            );
        };
        target.add(offsetX, offsetY, offsetZ);
        return target;
    }

    public static Quaterniond computeExtraRotation(Direction face, int rotationDegrees) {
        if (rotationDegrees == 0) {
            return new Quaterniond();
        }
        Vector3d axis = new Vector3d(face.getStepX(), face.getStepY(), face.getStepZ());
        if (axis.lengthSquared() < 1.0E-6D) {
            axis.set(0.0D, 1.0D, 0.0D);
        }
        axis.normalize();
        return new Quaterniond(new AxisAngle4d(
                Math.toRadians(rotationDegrees),
                axis.x,
                axis.y,
                axis.z
        ));
    }

    public static void requireFinite(Vec3 point) {
        if (point == null
                || !Double.isFinite(point.x)
                || !Double.isFinite(point.y)
                || !Double.isFinite(point.z)) {
            throw new IllegalArgumentException("placement hit contains non-finite coordinates");
        }
    }

    private static boolean isFinite(Vector3d vector) {
        return Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }
}
