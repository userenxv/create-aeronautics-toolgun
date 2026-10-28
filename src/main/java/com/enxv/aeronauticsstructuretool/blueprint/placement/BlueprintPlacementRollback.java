package com.enxv.aeronauticsstructuretool.blueprint.placement;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.blueprint.lifecycle.SubLevelRemovalCoordinator;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class BlueprintPlacementRollback {
    private BlueprintPlacementRollback() {
    }

    public static void rollback(
            ServerSubLevelContainer container,
            Collection<ServerSubLevel> allocatedSublevels,
            String blueprintName,
            IOException placementFailure
    ) {
        List<ServerSubLevel> reverseOrder = new ArrayList<>(allocatedSublevels);
        for (int index = reverseOrder.size() - 1; index >= 0; index--) {
            ServerSubLevel subLevel = reverseOrder.get(index);
            try {
                if (container.getSubLevel(subLevel.getUniqueId()) == subLevel) {
                    SubLevelRemovalCoordinator.remove(
                            container.getLevel(),
                            container,
                            List.of(subLevel)
                    );
                    if (container.getSubLevel(subLevel.getUniqueId()) == subLevel) {
                        throw new IllegalStateException("placement rollback left a live sublevel: " + subLevel.getUniqueId());
                    }
                }
            } catch (RuntimeException rollbackFailure) {
                placementFailure.addSuppressed(rollbackFailure);
                AeronauticsStructureToolMod.LOGGER.error(
                        "Failed to roll back sublevel {} after blueprint '{}' placement failed",
                        subLevel.getUniqueId(),
                        blueprintName,
                        rollbackFailure
                );
            }
        }
    }
}
