package com.enxv.aeronauticsstructuretool.toolgun.constraint;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.ConnectionMode;
import com.enxv.aeronauticsstructuretool.ToolgunConstraintTracker;
import com.enxv.aeronauticsstructuretool.toolgun.transform.SubLevelPoseOperations;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.enxv.aeronauticsstructuretool.toolgun.ToolgunTransformValidation.isFinite;

public final class ConstraintPoseTransaction {
    private static final double AXIS_EPSILON_SQUARED = 1.0E-6D;

    private ConstraintPoseTransaction() {
    }

    public static ServerSubLevel apply(
            ServerLevel level,
            UUID subLevelId,
            PoseMutation mutation
    ) throws IOException {
        return apply(level, subLevelId, mutation, subLevel -> {
        });
    }

    public static ServerSubLevel apply(
            ServerLevel level,
            UUID subLevelId,
            PoseMutation mutation,
            PostRebuildAction postRebuildAction
    ) throws IOException {
        if (mutation == null || postRebuildAction == null) {
            throw new IOException("missing constraint-preserving pose operation");
        }

        ServerSubLevel target = requireSubLevel(level, subLevelId);
        List<ResolvedConstraint> constraints = resolveConstraints(level, subLevelId);
        PoseSnapshot originalPose = PoseSnapshot.capture(target);
        boolean replacementStarted = false;
        try {
            mutation.apply(target);
            requireFinitePose(target);

            List<RebuildSpec> currentPoseSpecs = computeRebuildSpecs(constraints);
            replacementStarted = true;
            replaceConstraints(level, subLevelId, currentPoseSpecs);
            postRebuildAction.apply(target);
            return target;
        } catch (Exception exception) {
            IOException failure = asIOException("constraint-preserving pose operation failed", exception);
            try {
                if (replacementStarted) {
                    ToolgunConstraintTracker.removeConstraintsForSubLevel(level, subLevelId);
                }
                originalPose.restore(level, target);
                if (replacementStarted) {
                    restoreConstraints(level, computeRebuildSpecs(constraints));
                }
            } catch (Exception rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
                AeronauticsStructureToolMod.LOGGER.error(
                        "Failed to roll back pose and constraints for sublevel {} in {}",
                        subLevelId,
                        level.dimension().location(),
                        rollbackFailure
                );
            }
            throw failure;
        }
    }

    public static ServerSubLevel requireSubLevel(ServerLevel level, UUID subLevelId) throws IOException {
        if (level == null || subLevelId == null) {
            throw new IOException("missing sublevel lookup context");
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            throw new IOException("sublevel container unavailable in " + level.dimension().location());
        }
        SubLevel subLevel = container.getSubLevel(subLevelId);
        if (!(subLevel instanceof ServerSubLevel serverSubLevel)) {
            throw new IOException("missing sublevel " + subLevelId + " in " + level.dimension().location());
        }
        return serverSubLevel;
    }

    private static List<ResolvedConstraint> resolveConstraints(
            ServerLevel level,
            UUID movedSubLevelId
    ) throws IOException {
        String dimensionId = level.dimension().location().toString();
        List<ResolvedConstraint> resolved = new ArrayList<>();
        for (TrackedConstraint tracked : ToolgunConstraintTracker.getConstraintsForSubLevel(movedSubLevelId)) {
            ConstraintSnapshot snapshot = ConstraintSnapshot.copyOf(tracked);
            if (!dimensionId.equals(snapshot.dimensionId())) {
                throw new IOException(
                        "constraint " + snapshot.constraintId() + " belongs to " + snapshot.dimensionId()
                                + " instead of " + dimensionId
                );
            }
            ServerSubLevel first = requireSubLevel(level, snapshot.firstSubLevelId());
            ServerSubLevel second = requireSubLevel(level, snapshot.secondSubLevelId());
            if (first.getUniqueId().equals(second.getUniqueId())) {
                throw new IOException("constraint " + snapshot.constraintId() + " references one sublevel twice");
            }
            resolved.add(new ResolvedConstraint(snapshot, first, second));
        }
        return List.copyOf(resolved);
    }

    private static List<RebuildSpec> computeRebuildSpecs(
            List<ResolvedConstraint> constraints
    ) throws IOException {
        List<RebuildSpec> specs = new ArrayList<>(constraints.size());
        for (ResolvedConstraint resolved : constraints) {
            ConstraintSnapshot snapshot = resolved.snapshot();
            ServerSubLevel first = resolved.first();
            ServerSubLevel second = resolved.second();

            Quaterniond relativeOrientation = new Quaterniond(first.logicalPose().orientation())
                    .conjugate()
                    .mul(second.logicalPose().orientation())
                    .normalize();
            Vector3d firstWorldPoint = first.logicalPose().transformPosition(
                    new Vector3d(snapshot.firstLocalPoint()),
                    new Vector3d()
            );
            Vector3d secondWorldPoint = second.logicalPose().transformPosition(
                    new Vector3d(snapshot.secondLocalPoint()),
                    new Vector3d()
            );
            Vector3d sharedWorldPoint = firstWorldPoint.add(secondWorldPoint, new Vector3d()).mul(0.5D);
            Vector3d firstAxisLocal = copyOptional(snapshot.firstAxisLocal());
            Vector3d secondAxisLocal = copyOptional(snapshot.secondAxisLocal());
            if (snapshot.connectionMode() == ConnectionMode.BEARING) {
                Vector3d worldAxis = resolveBearingWorldAxis(first, second, snapshot);
                firstAxisLocal = transformWorldAxisToLocal(first, worldAxis, snapshot.constraintId());
                secondAxisLocal = transformWorldAxisToLocal(second, worldAxis, snapshot.constraintId());
            }

            RebuildSpec spec = new RebuildSpec(
                    snapshot.constraintId(),
                    first,
                    second,
                    first.logicalPose().transformPositionInverse(sharedWorldPoint, new Vector3d()),
                    second.logicalPose().transformPositionInverse(sharedWorldPoint, new Vector3d()),
                    copyOptional(snapshot.firstDisplayLocalPoint()),
                    copyOptional(snapshot.secondDisplayLocalPoint()),
                    relativeOrientation,
                    firstAxisLocal,
                    secondAxisLocal,
                    snapshot.connectionMode()
            );
            spec.validate();
            specs.add(spec);
        }
        return List.copyOf(specs);
    }

    private static Vector3d resolveBearingWorldAxis(
            ServerSubLevel first,
            ServerSubLevel second,
            ConstraintSnapshot snapshot
    ) throws IOException {
        Vector3d worldAxis = new Vector3d();
        int contributors = 0;
        contributors += addWorldAxis(worldAxis, first, snapshot.firstAxisLocal());
        contributors += addWorldAxis(worldAxis, second, snapshot.secondAxisLocal());
        if (contributors == 0 || !isFinite(worldAxis) || worldAxis.lengthSquared() <= AXIS_EPSILON_SQUARED) {
            throw new IOException("bearing constraint " + snapshot.constraintId() + " has no valid axis");
        }
        return worldAxis.normalize();
    }

    private static int addWorldAxis(Vector3d total, ServerSubLevel subLevel, Vector3d localAxis) {
        if (!isFinite(localAxis) || localAxis.lengthSquared() <= AXIS_EPSILON_SQUARED) {
            return 0;
        }
        Vector3d transformed = new Vector3d(localAxis);
        subLevel.logicalPose().orientation().transform(transformed);
        if (!isFinite(transformed) || transformed.lengthSquared() <= AXIS_EPSILON_SQUARED) {
            return 0;
        }
        total.add(transformed.normalize());
        return 1;
    }

    private static Vector3d transformWorldAxisToLocal(
            ServerSubLevel subLevel,
            Vector3d worldAxis,
            UUID constraintId
    ) throws IOException {
        Vector3d localAxis = new Vector3d(worldAxis);
        subLevel.logicalPose().orientation().transformInverse(localAxis);
        if (!isFinite(localAxis) || localAxis.lengthSquared() <= AXIS_EPSILON_SQUARED) {
            throw new IOException("bearing constraint " + constraintId + " produced an invalid local axis");
        }
        return localAxis.normalize();
    }

    private static void replaceConstraints(
            ServerLevel level,
            UUID movedSubLevelId,
            List<RebuildSpec> specs
    ) throws IOException {
        ToolgunConstraintTracker.removeConstraintsForSubLevel(level, movedSubLevelId);
        restoreConstraints(level, specs);
    }

    private static void restoreConstraints(ServerLevel level, List<RebuildSpec> specs) throws IOException {
        for (RebuildSpec spec : specs) {
            ToolgunConstraintTracker.register(level, ConstraintRuntimeFactory.restoreTrackedConstraint(
                    level,
                    spec.first(),
                    spec.second(),
                    spec.firstLocalPoint(),
                    spec.secondLocalPoint(),
                    spec.firstDisplayLocalPoint(),
                    spec.secondDisplayLocalPoint(),
                    spec.relativeOrientation(),
                    spec.firstAxisLocal(),
                    spec.secondAxisLocal(),
                    spec.connectionMode(),
                    spec.constraintId()
            ));
        }
    }

    private static void requireFinitePose(ServerSubLevel subLevel) throws IOException {
        if (!isFinite(subLevel.logicalPose().position())
                || !isFinite(subLevel.logicalPose().orientation())) {
            throw new IOException("pose mutation produced non-finite coordinates for " + subLevel.getUniqueId());
        }
    }

    private static Vector3d copyOptional(Vector3d vector) {
        return vector == null ? null : new Vector3d(vector);
    }

    private static IOException asIOException(String message, Exception exception) {
        if (exception instanceof IOException ioException) {
            return ioException;
        }
        return new IOException(message, exception);
    }

    @FunctionalInterface
    public interface PoseMutation {
        void apply(ServerSubLevel subLevel) throws IOException;
    }

    @FunctionalInterface
    public interface PostRebuildAction {
        void apply(ServerSubLevel subLevel) throws IOException;
    }

    private record PoseSnapshot(Vector3d position, Quaterniond orientation) {
        private static PoseSnapshot capture(ServerSubLevel subLevel) {
            return new PoseSnapshot(
                    new Vector3d(subLevel.logicalPose().position()),
                    new Quaterniond(subLevel.logicalPose().orientation())
            );
        }

        private void restore(ServerLevel level, ServerSubLevel subLevel) throws IOException {
            subLevel.logicalPose().position().set(this.position);
            subLevel.logicalPose().orientation().set(this.orientation);
            SubLevelPoseOperations.synchronize(level, subLevel);
        }
    }

    private record ConstraintSnapshot(
            String dimensionId,
            UUID constraintId,
            UUID firstSubLevelId,
            UUID secondSubLevelId,
            ConnectionMode connectionMode,
            Vector3d firstDisplayLocalPoint,
            Vector3d secondDisplayLocalPoint,
            Vector3d firstLocalPoint,
            Vector3d secondLocalPoint,
            Quaterniond relativeOrientation,
            Vector3d firstAxisLocal,
            Vector3d secondAxisLocal
    ) {
        private static ConstraintSnapshot copyOf(TrackedConstraint constraint) throws IOException {
            if (constraint == null
                    || constraint.dimensionId() == null
                    || constraint.constraintId() == null
                    || constraint.firstSubLevelId() == null
                    || constraint.secondSubLevelId() == null
                    || constraint.connectionMode() == null) {
                throw new IOException("live constraint metadata is incomplete");
            }
            ConstraintSnapshot snapshot = new ConstraintSnapshot(
                    constraint.dimensionId(),
                    constraint.constraintId(),
                    constraint.firstSubLevelId(),
                    constraint.secondSubLevelId(),
                    constraint.connectionMode(),
                    copyOptional(constraint.firstDisplayLocalPoint()),
                    copyOptional(constraint.secondDisplayLocalPoint()),
                    copyOptional(constraint.firstLocalPoint()),
                    copyOptional(constraint.secondLocalPoint()),
                    constraint.relativeOrientation() == null
                            ? null
                            : new Quaterniond(constraint.relativeOrientation()),
                    copyOptional(constraint.firstAxisLocal()),
                    copyOptional(constraint.secondAxisLocal())
            );
            if (!isFinite(snapshot.firstLocalPoint())
                    || !isFinite(snapshot.secondLocalPoint())
                    || !isFinite(snapshot.relativeOrientation())
                    || (snapshot.firstDisplayLocalPoint() != null && !isFinite(snapshot.firstDisplayLocalPoint()))
                    || (snapshot.secondDisplayLocalPoint() != null && !isFinite(snapshot.secondDisplayLocalPoint()))) {
                throw new IOException("constraint " + snapshot.constraintId() + " contains non-finite transform data");
            }
            return snapshot;
        }
    }

    private record ResolvedConstraint(
            ConstraintSnapshot snapshot,
            ServerSubLevel first,
            ServerSubLevel second
    ) {
    }

    private record RebuildSpec(
            UUID constraintId,
            ServerSubLevel first,
            ServerSubLevel second,
            Vector3d firstLocalPoint,
            Vector3d secondLocalPoint,
            Vector3d firstDisplayLocalPoint,
            Vector3d secondDisplayLocalPoint,
            Quaterniond relativeOrientation,
            Vector3d firstAxisLocal,
            Vector3d secondAxisLocal,
            ConnectionMode connectionMode
    ) {
        private void validate() throws IOException {
            if (!isFinite(this.firstLocalPoint)
                    || !isFinite(this.secondLocalPoint)
                    || !isFinite(this.relativeOrientation)
                    || (this.firstDisplayLocalPoint != null && !isFinite(this.firstDisplayLocalPoint))
                    || (this.secondDisplayLocalPoint != null && !isFinite(this.secondDisplayLocalPoint))) {
                throw new IOException("constraint " + this.constraintId + " rebuild produced non-finite data");
            }
            if (this.connectionMode == ConnectionMode.BEARING
                    && (!isFinite(this.firstAxisLocal) || !isFinite(this.secondAxisLocal))) {
                throw new IOException("bearing constraint " + this.constraintId + " rebuild produced invalid axes");
            }
        }
    }
}
