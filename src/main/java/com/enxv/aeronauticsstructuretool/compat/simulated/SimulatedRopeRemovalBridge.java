package com.enxv.aeronauticsstructuretool.compat.simulated;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Removes Simulated rope physics before its Sable attachment sublevels. */
public final class SimulatedRopeRemovalBridge {
    private static final String ROPE_MANAGER_CLASS =
            "dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager";
    private static final String ROPE_STRAND_CLASS =
            "dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand";

    private SimulatedRopeRemovalBridge() {
    }

    public static int removeForSubLevels(ServerLevel level, Collection<UUID> subLevelIds) {
        Set<UUID> targets = new HashSet<>(subLevelIds);
        if (targets.isEmpty()) {
            return 0;
        }

        Class<?> managerClass = SimulatedReflectionBridge.findOptionalClass(
                ROPE_MANAGER_CLASS,
                "Simulated rope manager"
        );
        if (managerClass == null) {
            return 0;
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            throw new IllegalStateException("Sable sublevel container unavailable while removing Simulated ropes");
        }

        try {
            Object manager = managerClass
                    .getMethod("getOrCreate", net.minecraft.world.level.Level.class)
                    .invoke(null, level);
            Object rawStrands = managerClass.getMethod("getAllStrands").invoke(manager);
            if (!(rawStrands instanceof Collection<?> strands)) {
                throw new IllegalStateException("Simulated rope manager returned a non-collection strand list");
            }

            Object physicsSystem = container.physicsSystem();
            int removed = 0;
            for (Object strand : new ArrayList<>(strands)) {
                if (!referencesAnySubLevel(strand, targets)) {
                    continue;
                }

                SimulatedReflectionBridge.invokeRequired(physicsSystem, "removeObject", strand);
                // Also clear constraints when the manager entry was stale.
                SimulatedReflectionBridge.invokeRequired(strand, "removeConstraints");
                UUID ropeId = requireUuid(
                        SimulatedReflectionBridge.invokeRequired(strand, "getUUID"),
                        "Simulated rope UUID"
                );
                managerClass.getMethod("removeStrand", UUID.class).invoke(manager, ropeId);
                removed++;
            }

            if (removed > 0) {
                AeronauticsStructureToolMod.LOGGER.info(
                        "Removed {} Simulated rope strand(s) before deleting {} sublevel(s)",
                        removed,
                        targets.size()
                );
            }
            return removed;
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException(
                    "failed to remove Simulated ropes before sublevel removal",
                    exception
            );
        }
    }

    private static boolean referencesAnySubLevel(Object strand, Set<UUID> targets) {
        Object attachments = SimulatedReflectionBridge.invokeRequired(strand, "getAttachments");
        if (!(attachments instanceof Iterable<?> iterable)) {
            throw new IllegalStateException("Simulated rope returned a non-iterable attachment collection");
        }
        for (Object attachment : iterable) {
            UUID subLevelId = requireUuidOrNull(
                    SimulatedReflectionBridge.invokeRequired(attachment, "subLevelID")
            );
            if (subLevelId != null && targets.contains(subLevelId)) {
                return true;
            }
        }
        return false;
    }

    private static UUID requireUuid(Object value, String label) {
        UUID uuid = requireUuidOrNull(value);
        if (uuid == null) {
            throw new IllegalStateException(label + " is missing or incompatible");
        }
        return uuid;
    }

    private static UUID requireUuidOrNull(Object value) {
        return value instanceof UUID uuid ? uuid : null;
    }
}
