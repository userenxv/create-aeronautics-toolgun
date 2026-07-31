package com.enxv.aeronauticsstructuretool.toolgun.constraint.persistence;

import java.util.Map;
import java.util.UUID;

public record ConstraintRestoreResult(
        int savedCount,
        int alreadyPresentCount,
        int restoredCount,
        Map<UUID, String> unresolved
) {
    public ConstraintRestoreResult {
        unresolved = Map.copyOf(unresolved);
    }

    public boolean complete() {
        return unresolved.isEmpty() && alreadyPresentCount + restoredCount == savedCount;
    }
}
