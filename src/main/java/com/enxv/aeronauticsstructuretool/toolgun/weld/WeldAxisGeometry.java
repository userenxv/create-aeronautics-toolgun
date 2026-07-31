package com.enxv.aeronauticsstructuretool.toolgun.weld;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.Direction;
import org.joml.Vector3d;

public final class WeldAxisGeometry {
    private WeldAxisGeometry() {
    }

    public static Vector3d worldAxisFromPointsOrFaces(
            Vector3d firstPoint,
            Vector3d secondPoint,
            Direction firstFace,
            Direction secondFace
    ) {
        return primarySelectionAxis(firstPoint, secondPoint, firstFace, secondFace);
    }

    public static Vector3d worldAxisFromFace(Direction face) {
        Vector3d axis = directionVector(face);
        return axis.lengthSquared() > 1.0E-6D
                ? axis.normalize()
                : new Vector3d(0.0D, 1.0D, 0.0D);
    }

    public static Vector3d worldAxisFromFace(SubLevel subLevel, Direction face) {
        return transformDirection(subLevel, directionVector(face));
    }

    public static Vector3d distanceAdjustAxis(
            Vector3d firstPoint,
            Vector3d secondPoint,
            Direction firstFace,
            Direction secondFace
    ) {
        Vector3d between = new Vector3d(firstPoint).sub(secondPoint);
        if (between.lengthSquared() > 1.0E-6D) {
            return between.normalize();
        }
        Vector3d primary = primarySelectionAxis(firstPoint, secondPoint, firstFace, secondFace);
        return primary.lengthSquared() > 1.0E-6D
                ? primary.normalize()
                : new Vector3d(0.0D, 1.0D, 0.0D);
    }

    public static Vector3d primarySelectionAxis(
            Vector3d firstPoint,
            Vector3d secondPoint,
            Direction firstFace,
            Direction secondFace
    ) {
        Vector3d firstNormal = directionVector(firstFace);
        Vector3d secondNormal = directionVector(secondFace).negate();
        Vector3d combined = new Vector3d(firstNormal).add(secondNormal);
        if (combined.lengthSquared() > 1.0E-6D) {
            return combined.normalize();
        }
        if (firstNormal.lengthSquared() > 1.0E-6D) {
            return firstNormal.normalize();
        }
        if (secondNormal.lengthSquared() > 1.0E-6D) {
            return secondNormal.normalize();
        }
        return new Vector3d(0.0D, 1.0D, 0.0D);
    }

    public static Vector3d secondarySelectionAxis(
            Vector3d firstPoint,
            Vector3d secondPoint,
            Direction firstFace,
            Direction secondFace
    ) {
        Vector3d primary = primarySelectionAxis(firstPoint, secondPoint, firstFace, secondFace);
        Vector3d reference = stableReferenceAxis(primary);
        Vector3d secondary = reference.cross(primary, new Vector3d());
        if (secondary.lengthSquared() <= 1.0E-6D) {
            return new Vector3d(1.0D, 0.0D, 0.0D);
        }
        return secondary.normalize();
    }

    public static Vector3d tertiarySelectionAxis(
            Vector3d firstPoint,
            Vector3d secondPoint,
            Direction firstFace,
            Direction secondFace
    ) {
        Vector3d primary = primarySelectionAxis(firstPoint, secondPoint, firstFace, secondFace);
        Vector3d secondary = secondarySelectionAxis(firstPoint, secondPoint, firstFace, secondFace);
        Vector3d tertiary = new Vector3d(primary).cross(secondary);
        if (tertiary.lengthSquared() <= 1.0E-6D) {
            return new Vector3d(0.0D, 0.0D, 1.0D);
        }
        return tertiary.normalize();
    }

    public static Vector3d primarySelectionAxis(
            SubLevel firstSubLevel,
            Direction firstFace,
            SubLevel secondSubLevel,
            Direction secondFace
    ) {
        Vector3d firstNormal = worldAxisFromFace(firstSubLevel, firstFace);
        Vector3d secondNormal = worldAxisFromFace(secondSubLevel, secondFace).negate();
        Vector3d combined = new Vector3d(firstNormal).add(secondNormal);
        if (combined.lengthSquared() > 1.0E-6D) {
            return combined.normalize();
        }
        if (firstNormal.lengthSquared() > 1.0E-6D) {
            return firstNormal.normalize();
        }
        if (secondNormal.lengthSquared() > 1.0E-6D) {
            return secondNormal.normalize();
        }
        return new Vector3d(0.0D, 1.0D, 0.0D);
    }

    public static Vector3d secondarySelectionAxis(SubLevel subLevel, Direction face) {
        Vector3d tangent = transformDirection(subLevel, localFaceTangent(face));
        if (tangent.lengthSquared() <= 1.0E-6D) {
            Vector3d primary = worldAxisFromFace(subLevel, face);
            Vector3d fallback = stableReferenceAxis(primary).cross(primary, new Vector3d());
            if (fallback.lengthSquared() <= 1.0E-6D) {
                return new Vector3d(1.0D, 0.0D, 0.0D);
            }
            return fallback.normalize();
        }
        return tangent.normalize();
    }

    public static Vector3d tertiarySelectionAxis(SubLevel subLevel, Direction face) {
        Vector3d tangent = transformDirection(subLevel, localFaceBitangent(face));
        if (tangent.lengthSquared() <= 1.0E-6D) {
            Vector3d primary = worldAxisFromFace(subLevel, face);
            Vector3d secondary = secondarySelectionAxis(subLevel, face);
            Vector3d fallback = new Vector3d(primary).cross(secondary);
            if (fallback.lengthSquared() <= 1.0E-6D) {
                return new Vector3d(0.0D, 0.0D, 1.0D);
            }
            return fallback.normalize();
        }
        return tangent.normalize();
    }

    private static Vector3d transformDirection(SubLevel subLevel, Vector3d localAxis) {
        if (subLevel == null || localAxis == null || localAxis.lengthSquared() <= 1.0E-6D) {
            return new Vector3d();
        }
        Vector3d worldAxis = new Vector3d(localAxis);
        subLevel.logicalPose().orientation().transform(worldAxis);
        if (worldAxis.lengthSquared() <= 1.0E-6D) {
            return new Vector3d();
        }
        return worldAxis.normalize();
    }

    private static Vector3d directionVector(Direction face) {
        if (face == null) {
            return new Vector3d();
        }
        return new Vector3d(face.getStepX(), face.getStepY(), face.getStepZ());
    }

    private static Vector3d localFaceTangent(Direction face) {
        if (face == null) {
            return new Vector3d(1.0D, 0.0D, 0.0D);
        }
        return switch (face.getAxis()) {
            case Y, Z -> new Vector3d(1.0D, 0.0D, 0.0D);
            case X -> new Vector3d(0.0D, 0.0D, 1.0D);
        };
    }

    private static Vector3d localFaceBitangent(Direction face) {
        if (face == null) {
            return new Vector3d(0.0D, 0.0D, 1.0D);
        }
        return switch (face.getAxis()) {
            case Y -> new Vector3d(0.0D, 0.0D, 1.0D);
            case Z, X -> new Vector3d(0.0D, 1.0D, 0.0D);
        };
    }

    private static Vector3d stableReferenceAxis(Vector3d primary) {
        if (Math.abs(primary.y) < 0.92D) {
            return new Vector3d(0.0D, 1.0D, 0.0D);
        }
        if (Math.abs(primary.x) < 0.92D) {
            return new Vector3d(1.0D, 0.0D, 0.0D);
        }
        return new Vector3d(0.0D, 0.0D, 1.0D);
    }
}
