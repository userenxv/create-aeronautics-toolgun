package com.enxv.aeronauticsstructuretool.toolgun.constraint;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ConstraintRuntimeRepository {
    private static final Map<UUID, TrackedConstraint> CONSTRAINTS = new LinkedHashMap<>();

    private ConstraintRuntimeRepository() {
    }

    public static void register(ServerLevel level, TrackedConstraint constraint) {
        try (RegistrationTransaction transaction = beginRegistration(level, constraint)) {
            transaction.commit();
        }
    }

    public static RegistrationTransaction beginRegistration(
            ServerLevel level,
            TrackedConstraint constraint
    ) {
        if (constraint == null || constraint.handle() == null || !constraint.handle().isValid()) {
            throw new IllegalArgumentException(
                    "Cannot register a constraint without a valid physics handle in " + level.dimension().location()
            );
        }
        cleanupInvalid();
        TrackedConstraint previous = CONSTRAINTS.put(constraint.constraintId(), constraint);
        return new RegistrationTransaction(constraint, previous);
    }

    public static boolean remove(UUID constraintId, String reason) {
        TrackedConstraint removed = CONSTRAINTS.remove(constraintId);
        if (removed == null) {
            return false;
        }
        removeHandle(removed, reason);
        return true;
    }

    public static void discard(TrackedConstraint constraint, String reason) {
        if (constraint != null) {
            removeHandle(constraint, reason);
        }
    }

    public static boolean contains(UUID constraintId) {
        return CONSTRAINTS.containsKey(constraintId);
    }

    public static List<TrackedConstraint> all() {
        return List.copyOf(CONSTRAINTS.values());
    }

    public static List<TrackedConstraint> forSubLevel(UUID subLevelId) {
        cleanupInvalid();
        List<TrackedConstraint> results = new ArrayList<>();
        for (TrackedConstraint constraint : CONSTRAINTS.values()) {
            if (constraint.firstSubLevelId().equals(subLevelId)
                    || constraint.secondSubLevelId().equals(subLevelId)) {
                results.add(constraint);
            }
        }
        return List.copyOf(results);
    }

    public static Set<UUID> removeForSubLevel(UUID subLevelId) {
        cleanupInvalid();
        Set<UUID> removed = new LinkedHashSet<>();
        Iterator<TrackedConstraint> iterator = CONSTRAINTS.values().iterator();
        while (iterator.hasNext()) {
            TrackedConstraint constraint = iterator.next();
            if (!constraint.firstSubLevelId().equals(subLevelId)
                    && !constraint.secondSubLevelId().equals(subLevelId)) {
                continue;
            }
            removeHandle(constraint, "sublevel removal " + subLevelId);
            iterator.remove();
            removed.add(constraint.constraintId());
        }
        return removed;
    }

    public static boolean removeForSubLevels(String dimensionId, Collection<UUID> subLevelIds) {
        if (subLevelIds.isEmpty()) {
            return false;
        }
        boolean removedAny = false;
        Iterator<TrackedConstraint> iterator = CONSTRAINTS.values().iterator();
        while (iterator.hasNext()) {
            TrackedConstraint constraint = iterator.next();
            if (!dimensionId.equals(constraint.dimensionId())
                    || (!subLevelIds.contains(constraint.firstSubLevelId())
                    && !subLevelIds.contains(constraint.secondSubLevelId()))) {
                continue;
            }
            removeHandle(constraint, "sublevel reload in " + dimensionId);
            iterator.remove();
            removedAny = true;
        }
        return removedAny;
    }

    public static void clearDimension(String dimensionId) {
        Iterator<TrackedConstraint> iterator = CONSTRAINTS.values().iterator();
        while (iterator.hasNext()) {
            TrackedConstraint constraint = iterator.next();
            if (!dimensionId.equals(constraint.dimensionId())) {
                continue;
            }
            removeHandle(constraint, "dimension unload " + dimensionId);
            iterator.remove();
        }
    }

    public static long countForDimension(String dimensionId) {
        return CONSTRAINTS.values().stream()
                .filter(constraint -> dimensionId.equals(constraint.dimensionId()))
                .count();
    }

    public static void cleanupInvalid() {
        CONSTRAINTS.values().removeIf(constraint ->
                constraint.handle() == null || !constraint.handle().isValid()
        );
    }

    private static void removeHandle(TrackedConstraint constraint, String reason) {
        if (constraint.handle() == null) {
            return;
        }
        try {
            constraint.handle().remove();
        } catch (Exception exception) {
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Failed to remove runtime constraint {} during {}",
                    constraint.constraintId(),
                    reason,
                    exception
            );
        }
    }

    public static final class RegistrationTransaction implements AutoCloseable {
        private final TrackedConstraint replacement;
        private final TrackedConstraint previous;
        private boolean completed;

        private RegistrationTransaction(TrackedConstraint replacement, TrackedConstraint previous) {
            this.replacement = replacement;
            this.previous = previous;
        }

        public void commit() {
            if (this.completed) {
                return;
            }
            this.completed = true;
            if (this.previous != null
                    && this.previous != this.replacement
                    && this.previous.handle() != null
                    && this.previous.handle() != this.replacement.handle()) {
                removeHandle(this.previous, "replaced constraint");
            }
        }

        @Override
        public void close() {
            if (this.completed) {
                return;
            }
            this.completed = true;
            if (CONSTRAINTS.get(this.replacement.constraintId()) == this.replacement) {
                if (this.previous != null
                        && this.previous.handle() != null
                        && this.previous.handle().isValid()) {
                    CONSTRAINTS.put(this.replacement.constraintId(), this.previous);
                } else {
                    CONSTRAINTS.remove(this.replacement.constraintId());
                }
            }
            if (this.previous != this.replacement
                    && (this.previous == null || this.previous.handle() != this.replacement.handle())) {
                removeHandle(this.replacement, "rolled back constraint registration");
            }
        }
    }
}
