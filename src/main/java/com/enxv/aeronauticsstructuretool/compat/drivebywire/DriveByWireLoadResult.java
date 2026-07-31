package com.enxv.aeronauticsstructuretool.compat.drivebywire;

import java.util.List;

public record DriveByWireLoadResult(
        List<DriveByWireWorldSource> worldSources,
        List<DriveByWireRestoreRequest> deferredRestoreRequests,
        boolean requiresFullSync
) {
    public DriveByWireLoadResult {
        worldSources = List.copyOf(worldSources);
        deferredRestoreRequests = List.copyOf(deferredRestoreRequests);
    }
}
