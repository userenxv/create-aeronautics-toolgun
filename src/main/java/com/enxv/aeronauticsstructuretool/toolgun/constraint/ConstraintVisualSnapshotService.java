package com.enxv.aeronauticsstructuretool.toolgun.constraint;

import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

public final class ConstraintVisualSnapshotService {
    private ConstraintVisualSnapshotService() {
    }

    public static List<ConstraintVisualSnapshot> snapshots(ServerLevel level) {
        ConstraintRuntimeRepository.cleanupInvalid();
        String dimensionId = level.dimension().location().toString();
        List<ConstraintVisualSnapshot> snapshots = new ArrayList<>();
        for (TrackedConstraint constraint : ConstraintRuntimeRepository.all()) {
            if (!dimensionId.equals(constraint.dimensionId())) {
                continue;
            }
            snapshots.add(new ConstraintVisualSnapshot(
                    constraint.firstSubLevelId(),
                    constraint.secondSubLevelId(),
                    constraint.connectionMode(),
                    new Vector3d(constraint.firstDisplayLocalPoint()),
                    new Vector3d(constraint.secondDisplayLocalPoint()),
                    new Vector3d(constraint.firstLocalPoint()),
                    new Vector3d(constraint.secondLocalPoint()),
                    constraint.firstAxisLocal() == null ? null : new Vector3d(constraint.firstAxisLocal())
            ));
        }
        return List.copyOf(snapshots);
    }
}
