package com.enxv.aeronauticsstructuretool.toolgun.constraint.persistence;

import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockTransform;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.TrackedConstraint;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.UUID;

final class PersistentConstraintMapper {
    private PersistentConstraintMapper() {
    }

    static PersistentConstraint capture(ServerLevel level, TrackedConstraint constraint) {
        requireTrackedConstraint(level, constraint);
        ServerSubLevel first = requireSubLevel(level, constraint.firstSubLevelId(), "first");
        ServerSubLevel second = requireSubLevel(level, constraint.secondSubLevelId(), "second");
        if (first.getUniqueId().equals(second.getUniqueId())) {
            throw new IllegalArgumentException("Cannot persist a constraint between the same sublevel");
        }

        PlotBlockTransform firstTransform = PlotBlockTransform.capture(first);
        PlotBlockTransform secondTransform = PlotBlockTransform.capture(second);
        Vector3d firstLocal = copyRequired(constraint.firstLocalPoint(), "first local point");
        Vector3d secondLocal = copyRequired(constraint.secondLocalPoint(), "second local point");
        Vector3d firstDisplay = copyRequired(constraint.firstDisplayLocalPoint(), "first display point");
        Vector3d secondDisplay = copyRequired(constraint.secondDisplayLocalPoint(), "second display point");

        return new PersistentConstraint(
                PersistentConstraintCoordinateSpace.SAVED_PLOT_LOCAL_V1,
                constraint.dimensionId(),
                constraint.constraintId(),
                constraint.firstSubLevelId(),
                constraint.secondSubLevelId(),
                constraint.connectionMode(),
                firstDisplay,
                secondDisplay,
                firstLocal,
                secondLocal,
                first.logicalPose().transformPosition(new Vector3d(firstLocal), new Vector3d()),
                second.logicalPose().transformPosition(new Vector3d(secondLocal), new Vector3d()),
                firstTransform.toSavedLocalPosition(new Vector3d(firstLocal)),
                secondTransform.toSavedLocalPosition(new Vector3d(secondLocal)),
                firstTransform.toSavedLocalPosition(new Vector3d(firstDisplay)),
                secondTransform.toSavedLocalPosition(new Vector3d(secondDisplay)),
                new Quaterniond(constraint.relativeOrientation()),
                copyOptional(constraint.firstAxisLocal()),
                copyOptional(constraint.secondAxisLocal())
        );
    }

    static ResolvedPersistentConstraint resolve(
            PersistentConstraint constraint,
            ServerSubLevel first,
            ServerSubLevel second
    ) {
        Endpoint firstEndpoint = resolveEndpoint(
                constraint.coordinateSpace(),
                first,
                constraint.firstLocalPoint(),
                constraint.firstPlotLocalPoint(),
                constraint.firstWorldPoint(),
                constraint.firstDisplayLocalPoint(),
                constraint.firstDisplayPlotLocalPoint(),
                "first"
        );
        Endpoint secondEndpoint = resolveEndpoint(
                constraint.coordinateSpace(),
                second,
                constraint.secondLocalPoint(),
                constraint.secondPlotLocalPoint(),
                constraint.secondWorldPoint(),
                constraint.secondDisplayLocalPoint(),
                constraint.secondDisplayPlotLocalPoint(),
                "second"
        );
        return new ResolvedPersistentConstraint(
                firstEndpoint.localPoint(),
                secondEndpoint.localPoint(),
                firstEndpoint.displayLocalPoint(),
                secondEndpoint.displayLocalPoint()
        );
    }

    private static Endpoint resolveEndpoint(
            PersistentConstraintCoordinateSpace coordinateSpace,
            ServerSubLevel subLevel,
            Vector3d storedLocal,
            Vector3d storedPlotLocal,
            Vector3d storedWorld,
            Vector3d storedDisplayLocal,
            Vector3d storedDisplayPlotLocal,
            String endpointName
    ) {
        PlotBlockTransform transform = PlotBlockTransform.capture(subLevel);
        if (coordinateSpace == PersistentConstraintCoordinateSpace.SAVED_PLOT_LOCAL_V1) {
            Vector3d local = transform.toGlobalPosition(copyRequired(
                    storedPlotLocal,
                    endpointName + " saved plot-local point"
            ));
            Vector3d display;
            if (storedDisplayPlotLocal != null) {
                display = transform.toGlobalPosition(new Vector3d(storedDisplayPlotLocal));
            } else if (storedDisplayLocal != null) {
                Vector3d offset = new Vector3d(storedDisplayLocal)
                        .sub(copyRequired(storedLocal, endpointName + " local point"));
                display = new Vector3d(local).add(offset);
                com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod.LOGGER.warn(
                        "Persistent constraint {} endpoint is missing its display plot-local field; preserving the stored display offset",
                        endpointName
                );
            } else {
                display = new Vector3d(local);
            }
            return new Endpoint(local, display);
        }

        Vector3d local = LegacyPersistentConstraintDecoder.decodeLocal(
                subLevel,
                storedLocal,
                storedPlotLocal,
                storedWorld
        );
        Vector3d display = LegacyPersistentConstraintDecoder.decodeDisplay(
                storedDisplayLocal,
                storedLocal,
                local
        );
        return new Endpoint(local, display);
    }

    private static void requireTrackedConstraint(ServerLevel level, TrackedConstraint constraint) {
        if (constraint == null) {
            throw new IllegalArgumentException("Tracked constraint is missing");
        }
        if (constraint.handle() == null || !constraint.handle().isValid()) {
            throw new IllegalArgumentException("Tracked constraint has no valid physics handle");
        }
        String dimensionId = level.dimension().location().toString();
        if (!dimensionId.equals(constraint.dimensionId())) {
            throw new IllegalArgumentException(
                    "Constraint dimension " + constraint.dimensionId() + " does not match " + dimensionId
            );
        }
        if (constraint.constraintId() == null || constraint.connectionMode() == null) {
            throw new IllegalArgumentException("Tracked constraint identity or mode is missing");
        }
        copyRequired(constraint.relativeOrientation(), "relative orientation");
    }

    private static ServerSubLevel requireSubLevel(ServerLevel level, UUID subLevelId, String endpointName) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || !(container.getSubLevel(subLevelId) instanceof ServerSubLevel subLevel)) {
            throw new IllegalStateException(
                    "Cannot persist constraint because its " + endpointName + " sublevel is unavailable: " + subLevelId
            );
        }
        return subLevel;
    }

    private static Vector3d copyRequired(Vector3d value, String fieldName) {
        if (value == null || !Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z)) {
            throw new IllegalArgumentException("Constraint " + fieldName + " is missing or non-finite");
        }
        return new Vector3d(value);
    }

    private static Quaterniond copyRequired(Quaterniond value, String fieldName) {
        if (value == null
                || !Double.isFinite(value.x)
                || !Double.isFinite(value.y)
                || !Double.isFinite(value.z)
                || !Double.isFinite(value.w)
                || value.lengthSquared() <= 1.0E-12D) {
            throw new IllegalArgumentException("Constraint " + fieldName + " is missing, non-finite, or zero");
        }
        return new Quaterniond(value);
    }

    private static Vector3d copyOptional(Vector3d value) {
        return value == null ? null : copyRequired(value, "optional vector");
    }

    private record Endpoint(Vector3d localPoint, Vector3d displayLocalPoint) {
    }

    private static final class LegacyPersistentConstraintDecoder {
        private LegacyPersistentConstraintDecoder() {
        }

        static Vector3d decodeLocal(
                ServerSubLevel subLevel,
                Vector3d storedLocal,
                Vector3d storedPlotLocal,
                Vector3d storedWorld
        ) {
            if (storedPlotLocal != null) {
                return PlotBlockTransform.capture(subLevel).toGlobalPosition(new Vector3d(storedPlotLocal));
            }
            if (storedWorld != null) {
                return subLevel.logicalPose().transformPositionInverse(new Vector3d(storedWorld), new Vector3d());
            }
            return copyRequired(storedLocal, "legacy local point");
        }

        static Vector3d decodeDisplay(
                Vector3d storedDisplayLocal,
                Vector3d storedLocal,
                Vector3d resolvedLocal
        ) {
            if (storedDisplayLocal == null) {
                return new Vector3d(resolvedLocal);
            }
            Vector3d offset = new Vector3d(storedDisplayLocal)
                    .sub(copyRequired(storedLocal, "legacy local point"));
            return new Vector3d(resolvedLocal).add(offset);
        }
    }
}
