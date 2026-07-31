package com.enxv.aeronauticsstructuretool.blueprint.placement;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.blueprint.lifecycle.SubLevelRemovalCoordinator;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;

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
            Collection<LoadedSubLevel> loadedSublevels,
            String blueprintName,
            IOException placementFailure
    ) {
        List<LoadedSubLevel> reverseOrder = new ArrayList<>(loadedSublevels);
        for (int index = reverseOrder.size() - 1; index >= 0; index--) {
            ServerSubLevel subLevel = reverseOrder.get(index).subLevel();
            try {
                if (container.getSubLevel(subLevel.getUniqueId()) == subLevel) {
                    SubLevelRemovalCoordinator.remove(
                            container.getLevel(),
                            container,
                            List.of(subLevel)
                    );
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
