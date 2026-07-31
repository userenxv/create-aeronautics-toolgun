package com.enxv.aeronauticsstructuretool.client.render;

import com.enxv.aeronauticsstructuretool.ConnectionMode;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class ClientConstraintVisualTracker {
    private static final List<ConstraintVisual> CONSTRAINT_VISUALS = new ArrayList<>();

    private ClientConstraintVisualTracker() {
    }

    public static void registerConstraint(
            UUID firstSubLevelId,
            UUID secondSubLevelId,
            ConnectionMode connectionMode,
            Vector3d firstDisplayLocalPoint,
            Vector3d secondDisplayLocalPoint,
            Vector3d firstConstraintLocalPoint,
            Vector3d secondConstraintLocalPoint,
            Vector3d firstAxisLocal
    ) {
        if (firstSubLevelId == null || secondSubLevelId == null || connectionMode == null || firstConstraintLocalPoint == null || secondConstraintLocalPoint == null) {
            return;
        }
        removeByPair(firstSubLevelId, secondSubLevelId);
        CONSTRAINT_VISUALS.add(new ConstraintVisual(
                firstSubLevelId,
                secondSubLevelId,
                connectionMode,
                firstDisplayLocalPoint != null ? new Vector3d(firstDisplayLocalPoint) : new Vector3d(firstConstraintLocalPoint),
                secondDisplayLocalPoint != null ? new Vector3d(secondDisplayLocalPoint) : new Vector3d(secondConstraintLocalPoint),
                new Vector3d(firstConstraintLocalPoint),
                new Vector3d(secondConstraintLocalPoint),
                firstAxisLocal != null && firstAxisLocal.lengthSquared() > 1.0E-6D ? new Vector3d(firstAxisLocal).normalize() : null
        ));
    }

    public static List<ConstraintVisual> constraintVisuals() {
        return List.copyOf(CONSTRAINT_VISUALS);
    }

    public static void replaceAll(List<ConstraintVisual> visuals) {
        CONSTRAINT_VISUALS.clear();
        if (visuals != null) {
            CONSTRAINT_VISUALS.addAll(visuals);
        }
    }

    public static void clear() {
        CONSTRAINT_VISUALS.clear();
    }

    public static void removeForSubLevel(UUID subLevelId) {
        if (subLevelId == null) {
            return;
        }
        Iterator<ConstraintVisual> iterator = CONSTRAINT_VISUALS.iterator();
        while (iterator.hasNext()) {
            ConstraintVisual visual = iterator.next();
            if (visual.firstSubLevelId().equals(subLevelId) || visual.secondSubLevelId().equals(subLevelId)) {
                iterator.remove();
            }
        }
    }

    private static void removeByPair(UUID firstSubLevelId, UUID secondSubLevelId) {
        Iterator<ConstraintVisual> iterator = CONSTRAINT_VISUALS.iterator();
        while (iterator.hasNext()) {
            ConstraintVisual visual = iterator.next();
            boolean sameOrder = visual.firstSubLevelId().equals(firstSubLevelId) && visual.secondSubLevelId().equals(secondSubLevelId);
            boolean reverseOrder = visual.firstSubLevelId().equals(secondSubLevelId) && visual.secondSubLevelId().equals(firstSubLevelId);
            if (sameOrder || reverseOrder) {
                iterator.remove();
            }
        }
    }

    public record ConstraintVisual(
            UUID firstSubLevelId,
            UUID secondSubLevelId,
            ConnectionMode connectionMode,
            Vector3d firstDisplayLocalPoint,
            Vector3d secondDisplayLocalPoint,
            Vector3d firstConstraintLocalPoint,
            Vector3d secondConstraintLocalPoint,
            Vector3d firstAxisLocal
    ) {
    }
}
