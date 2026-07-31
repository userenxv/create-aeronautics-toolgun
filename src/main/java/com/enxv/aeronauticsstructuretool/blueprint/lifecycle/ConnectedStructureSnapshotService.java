package com.enxv.aeronauticsstructuretool.blueprint.lifecycle;

import com.enxv.aeronauticsstructuretool.blueprint.capture.ConnectedSubLevelCollector;
import com.enxv.aeronauticsstructuretool.blueprint.capture.NativeBlueprintCaptureService;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.util.List;

public final class ConnectedStructureSnapshotService {
    private ConnectedStructureSnapshotService() {
    }

    public static ConnectedStructureSnapshot capture(
            ServerLevel level,
            ServerSubLevel rootSubLevel
    ) throws IOException {
        return capture(
                level,
                rootSubLevel,
                NativeBlueprintCaptureService.DEFAULT_CONNECTED_SUBLEVEL_PROXIMITY_BLOCKS
        );
    }

    public static ConnectedStructureSnapshot capture(
            ServerLevel level,
            ServerSubLevel rootSubLevel,
            double maximumNeighborGap
    ) throws IOException {
        List<ServerSubLevel> connected = ConnectedSubLevelCollector.collect(
                level,
                rootSubLevel,
                maximumNeighborGap
        );
        double totalMass = connected.stream()
                .mapToDouble(subLevel -> subLevel.getMassTracker().getMass())
                .sum();
        return new ConnectedStructureSnapshot(
                rootSubLevel.getUniqueId(),
                connected.size(),
                totalMass
        );
    }

}
