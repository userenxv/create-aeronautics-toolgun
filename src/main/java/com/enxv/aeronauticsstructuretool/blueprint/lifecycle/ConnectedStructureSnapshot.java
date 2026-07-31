package com.enxv.aeronauticsstructuretool.blueprint.lifecycle;

import java.util.UUID;

public record ConnectedStructureSnapshot(
        UUID rootStructureId,
        int structureCount,
        double totalMass
) {
}
