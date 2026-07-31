package com.enxv.aeronauticsstructuretool.toolgun.constraint.persistence;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.ConstraintRuntimeFactory;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.TrackedConstraint;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.ConstraintRuntimeRepository;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ConstraintPersistenceService {
    private ConstraintPersistenceService() {
    }

    public static void register(ServerLevel level, TrackedConstraint constraint) {
        PersistentConstraint persistent;
        try {
            persistent = PersistentConstraintMapper.capture(level, constraint);
        } catch (RuntimeException exception) {
            ConstraintRuntimeRepository.discard(constraint, "failed persistent capture");
            throw exception;
        }

        ConstraintRuntimeRepository.RegistrationTransaction transaction;
        try {
            transaction = ConstraintRuntimeRepository.beginRegistration(level, constraint);
        } catch (RuntimeException exception) {
            ConstraintRuntimeRepository.discard(constraint, "failed runtime registration");
            throw exception;
        }
        try (transaction) {
            ConstraintSavedData.get(level).put(persistent);
            transaction.commit();
        }
    }

    public static Set<UUID> removeForSubLevel(ServerLevel level, UUID subLevelId) {
        Set<UUID> removed = new HashSet<>(ConstraintRuntimeRepository.removeForSubLevel(subLevelId));
        removed.addAll(ConstraintSavedData.get(level).removeForSubLevel(level, subLevelId));
        return Set.copyOf(removed);
    }

    public static ConstraintRestoreResult restoreMissing(ServerLevel level) {
        ConstraintRuntimeRepository.cleanupInvalid();
        List<PersistentConstraint> saved = constraintsFor(level);
        int alreadyPresent = 0;
        int restoredCount = 0;
        Map<UUID, String> unresolved = new LinkedHashMap<>();
        SubLevelContainer container = SubLevelContainer.getContainer(level);

        for (PersistentConstraint persistent : saved) {
            if (ConstraintRuntimeRepository.contains(persistent.constraintId())) {
                alreadyPresent++;
                continue;
            }
            ServerSubLevel first = resolve(container, persistent.firstSubLevelId());
            ServerSubLevel second = resolve(container, persistent.secondSubLevelId());
            if (first == null || second == null) {
                unresolved.put(
                        persistent.constraintId(),
                        "sublevel unavailable: first=" + persistent.firstSubLevelId()
                                + " second=" + persistent.secondSubLevelId()
                );
                continue;
            }
            if (first.getUniqueId().equals(second.getUniqueId())) {
                unresolved.put(
                        persistent.constraintId(),
                        "both endpoints resolve to sublevel " + first.getUniqueId()
                );
                continue;
            }

            TrackedConstraint restored = null;
            try {
                ResolvedPersistentConstraint points = PersistentConstraintMapper.resolve(persistent, first, second);
                restored = ConstraintRuntimeFactory.restoreTrackedConstraint(
                        level,
                        first,
                        second,
                        points.firstLocalPoint(),
                        points.secondLocalPoint(),
                        points.firstDisplayLocalPoint(),
                        points.secondDisplayLocalPoint(),
                        persistent.relativeOrientation(),
                        persistent.firstAxisLocal(),
                        persistent.secondAxisLocal(),
                        persistent.connectionMode(),
                        persistent.constraintId()
                );
                ConstraintRuntimeRepository.register(level, restored);
                restoredCount++;
            } catch (Exception exception) {
                ConstraintRuntimeRepository.discard(restored, "failed persistent restore");
                unresolved.put(persistent.constraintId(), describeFailure(exception));
                AeronauticsStructureToolMod.LOGGER.warn(
                        "Failed to restore persistent toolgun constraint {} in {}",
                        persistent.constraintId(),
                        level.dimension().location(),
                        exception
                );
            }
        }
        return new ConstraintRestoreResult(saved.size(), alreadyPresent, restoredCount, unresolved);
    }

    public static boolean hasMissingRuntimeConstraints(ServerLevel level) {
        ConstraintRuntimeRepository.cleanupInvalid();
        for (PersistentConstraint persistent : constraintsFor(level)) {
            if (!ConstraintRuntimeRepository.contains(persistent.constraintId())) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasConstraintForSubLevel(ServerLevel level, UUID subLevelId) {
        return ConstraintSavedData.get(level).hasConstraintForSubLevel(level, subLevelId);
    }

    public static List<PersistentConstraint> constraintsFor(ServerLevel level) {
        return ConstraintSavedData.get(level).constraintsFor(level);
    }

    private static ServerSubLevel resolve(SubLevelContainer container, UUID subLevelId) {
        return container != null && container.getSubLevel(subLevelId) instanceof ServerSubLevel subLevel
                ? subLevel
                : null;
    }

    private static String describeFailure(Exception exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
