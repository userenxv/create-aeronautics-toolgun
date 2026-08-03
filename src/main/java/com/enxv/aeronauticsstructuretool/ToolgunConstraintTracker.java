package com.enxv.aeronauticsstructuretool;

import com.enxv.aeronauticsstructuretool.blueprint.model.CapturePlan;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import com.enxv.aeronauticsstructuretool.server.ConstraintVisualPublisher;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.ConstraintBlueprintService;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.ConstraintVisualSnapshot;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.TrackedConstraint;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.persistence.ConstraintPersistenceService;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.persistence.ConstraintRestoreResult;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.ConstraintRuntimeRepository;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.ConstraintVisualSnapshotService;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.enxv.aeronauticsstructuretool.toolgun.constraint.persistence.ConstraintNbtKeys.CONSTRAINTS_TAG;

public final class ToolgunConstraintTracker {
    private ToolgunConstraintTracker() {
    }

    public static void register(ServerLevel level, TrackedConstraint constraint) {
        ConstraintPersistenceService.register(level, constraint);
        ConstraintVisualPublisher.sync(level);
    }

    public static int removeConstraintsForSubLevel(ServerLevel level, UUID subLevelId) {
        Set<UUID> removed = ConstraintPersistenceService.removeForSubLevel(level, subLevelId);
        if (!removed.isEmpty()) {
            ConstraintVisualPublisher.sync(level);
        }
        return removed.size();
    }

    public static ListTag writeConstraintsForSave(
            CapturePlan plan,
            Collection<SubLevel> includedSublevels
    ) {
        return ConstraintBlueprintService.write(plan, includedSublevels);
    }

    public static List<String> restoreConstraintsFromSave(
            ServerLevel level,
            ListTag constraintsTag,
            Map<UUID, LoadedSubLevel> loadedSublevels
    ) {
        List<String> warnings = ConstraintBlueprintService.restore(
                level,
                constraintsTag,
                loadedSublevels,
                constraint -> ConstraintPersistenceService.register(level, constraint)
        );
        ConstraintVisualPublisher.sync(level);
        return warnings;
    }

    public static void restorePersistentConstraints(ServerLevel level) {
        ConstraintRestoreResult result = ConstraintPersistenceService.restoreMissing(level);
        if (result.restoredCount() > 0) {
            ConstraintVisualPublisher.sync(level);
        }
    }

    public static ListTag emptyConstraintList() {
        return new ListTag();
    }

    public static String constraintsTagName() {
        return CONSTRAINTS_TAG;
    }

    public static List<TrackedConstraint> getConstraintsForSubLevel(UUID subLevelId) {
        return ConstraintRuntimeRepository.forSubLevel(subLevelId);
    }

    public static void cleanupInvalid() {
        ConstraintRuntimeRepository.cleanupInvalid();
    }

    public static List<ConstraintVisualSnapshot> getConstraintVisualSnapshots(ServerLevel level) {
        return ConstraintVisualSnapshotService.snapshots(level);
    }
}
