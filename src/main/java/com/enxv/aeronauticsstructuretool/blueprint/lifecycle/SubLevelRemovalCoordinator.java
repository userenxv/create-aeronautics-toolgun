package com.enxv.aeronauticsstructuretool.blueprint.lifecycle;

import com.enxv.aeronauticsstructuretool.ToolgunConstraintTracker;
import com.enxv.aeronauticsstructuretool.compat.simulated.SimulatedRopeRemovalBridge;
import com.enxv.aeronauticsstructuretool.server.ServerServices;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class SubLevelRemovalCoordinator {
    private SubLevelRemovalCoordinator() {
    }

    public static void remove(
            ServerLevel level,
            ServerSubLevelContainer container,
            Collection<SubLevel> targets
    ) {
        Set<SubLevel> existing = new LinkedHashSet<>();
        for (SubLevel target : targets) {
            if (target != null && container.getSubLevel(target.getUniqueId()) == target) {
                existing.add(target);
            }
        }
        if (existing.isEmpty()) {
            return;
        }

        Set<UUID> subLevelIds = new LinkedHashSet<>();
        for (SubLevel target : existing) {
            subLevelIds.add(target.getUniqueId());
        }
        SimulatedRopeRemovalBridge.removeForSubLevels(level, subLevelIds);
        ServerServices.DRIVEBYWIRE_SUBLEVEL_LIFECYCLE.beforeRemoval(level, subLevelIds);

        for (SubLevel target : existing) {
            ToolgunConstraintTracker.removeConstraintsForSubLevel(level, target.getUniqueId());
            container.removeSubLevel(target, SubLevelRemovalReason.REMOVED);
        }
        ServerServices.DRIVEBYWIRE_SUBLEVEL_LIFECYCLE.afterRemoval(level);
    }
}
