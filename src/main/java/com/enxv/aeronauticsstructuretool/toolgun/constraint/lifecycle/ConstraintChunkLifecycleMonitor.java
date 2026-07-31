package com.enxv.aeronauticsstructuretool.toolgun.constraint.lifecycle;

import com.enxv.aeronauticsstructuretool.toolgun.constraint.persistence.PersistentConstraint;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

final class ConstraintChunkLifecycleMonitor {
    private final Map<ServerLevel, Map<UUID, ObservedChunkState>> statesByLevel = new WeakHashMap<>();

    ChangeSet collectChanges(ServerLevel level, Collection<PersistentConstraint> persistedConstraints) {
        Set<UUID> referencedSubLevels = new LinkedHashSet<>();
        for (PersistentConstraint constraint : persistedConstraints) {
            referencedSubLevels.add(constraint.firstSubLevelId());
            referencedSubLevels.add(constraint.secondSubLevelId());
        }

        Map<UUID, ObservedChunkState> states = statesByLevel.computeIfAbsent(
                level,
                ignored -> new LinkedHashMap<>()
        );
        Set<UUID> changed = new HashSet<>();
        Set<UUID> availabilityImproved = new HashSet<>();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        for (UUID subLevelId : referencedSubLevels) {
            ServerSubLevel subLevel = container != null
                    && container.getSubLevel(subLevelId) instanceof ServerSubLevel serverSubLevel
                    ? serverSubLevel
                    : null;
            if (subLevel == null) {
                if (states.remove(subLevelId) != null) {
                    changed.add(subLevelId);
                }
                continue;
            }
            ChunkLoadSignature current = signatureOf(subLevel);
            ObservedChunkState previous = states.get(subLevelId);
            if (previous == null) {
                states.put(subLevelId, new ObservedChunkState(current, current.count()));
                continue;
            }
            if (!previous.signature().equals(current)) {
                changed.add(subLevelId);
            }
            int maxLoadedCount = Math.max(previous.maxLoadedCount(), current.count());
            if (current.count() > previous.maxLoadedCount()) {
                availabilityImproved.add(subLevelId);
            }
            states.put(subLevelId, new ObservedChunkState(current, maxLoadedCount));
        }
        states.keySet().retainAll(referencedSubLevels);
        return new ChangeSet(changed, availabilityImproved);
    }

    void forget(ServerLevel level, UUID subLevelId) {
        Map<UUID, ObservedChunkState> states = statesByLevel.get(level);
        if (states != null) {
            states.remove(subLevelId);
        }
    }

    void clear(ServerLevel level) {
        statesByLevel.remove(level);
    }

    private static ChunkLoadSignature signatureOf(ServerSubLevel subLevel) {
        int count = 0;
        long xor = 0L;
        long sum = 0L;
        for (PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            ChunkPos pos = holder.getChunk().getPos();
            long packed = ChunkPos.asLong(pos.x, pos.z);
            count++;
            xor ^= packed;
            sum += packed;
        }
        return new ChunkLoadSignature(count, xor, sum);
    }

    private record ChunkLoadSignature(int count, long xor, long sum) {
    }

    private record ObservedChunkState(ChunkLoadSignature signature, int maxLoadedCount) {
    }

    record ChangeSet(Set<UUID> changedSubLevels, Set<UUID> availabilityImprovedSubLevels) {
        ChangeSet {
            changedSubLevels = Set.copyOf(changedSubLevels);
            availabilityImprovedSubLevels = Set.copyOf(availabilityImprovedSubLevels);
        }
    }
}
