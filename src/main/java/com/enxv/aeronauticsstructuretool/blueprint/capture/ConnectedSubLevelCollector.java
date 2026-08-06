package com.enxv.aeronauticsstructuretool.blueprint.capture;

import com.enxv.aeronauticsstructuretool.ToolgunConstraintTracker;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.TrackedConstraint;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockTransform;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

public final class ConnectedSubLevelCollector {
    private ConnectedSubLevelCollector() {
    }

    /**
     * @return the list of connected sub-levels
     */
    public static List<ServerSubLevel> collect(
            ServerLevel level,
            ServerSubLevel rootSubLevel,
            double maximumNeighborGap
    ) throws IOException {
        if (!Double.isFinite(maximumNeighborGap)) {
            throw new IOException("connected sublevel proximity must be a finite value");
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            throw new IOException("Sable sublevel container is unavailable");
        }

        Map<UUID, ServerSubLevel> allSublevels = new LinkedHashMap<>();
        for (ServerSubLevel subLevel : container.getAllSubLevels()) {
            allSublevels.put(subLevel.getUniqueId(), subLevel);
        }

        LinkedHashSet<ServerSubLevel> discovered = new LinkedHashSet<>();
        discovered.add(rootSubLevel);
        for (SubLevel connected : SubLevelHelper.getConnectedChain(rootSubLevel)) {
            if (connected instanceof ServerSubLevel serverSubLevel) {
                discovered.add(serverSubLevel);
            }
        }

        Queue<ServerSubLevel> queue = new ArrayDeque<>(discovered);
        Set<UUID> queued = new LinkedHashSet<>();
        for (ServerSubLevel subLevel : discovered) {
            queued.add(subLevel.getUniqueId());
        }
        while (!queue.isEmpty()) {
            ServerSubLevel current = queue.remove();
            enqueueNew(findToolgunConnections(current, allSublevels), discovered, queue, queued);
            enqueueNew(findDeclaredConnections(level, current), discovered, queue, queued);
            if (maximumNeighborGap >= 0.0D) enqueueNew(findNeighbors(current, allSublevels, maximumNeighborGap), discovered, queue, queued);
        }
        return new ArrayList<>(discovered);
    }

    private static void enqueueNew(
            List<ServerSubLevel> candidates,
            Set<ServerSubLevel> discovered,
            Queue<ServerSubLevel> queue,
            Set<UUID> queued
    ) {
        for (ServerSubLevel candidate : candidates) {
            if (queued.add(candidate.getUniqueId())) {
                discovered.add(candidate);
                queue.add(candidate);
            }
        }
    }

    private static List<ServerSubLevel> findToolgunConnections(
            ServerSubLevel current,
            Map<UUID, ServerSubLevel> allSublevels
    ) {
        LinkedHashSet<ServerSubLevel> results = new LinkedHashSet<>();
        for (TrackedConstraint constraint
                : ToolgunConstraintTracker.getConstraintsForSubLevel(current.getUniqueId())) {
            UUID otherId = current.getUniqueId().equals(constraint.firstSubLevelId())
                    ? constraint.secondSubLevelId()
                    : constraint.firstSubLevelId();
            ServerSubLevel other = allSublevels.get(otherId);
            if (other != null && !other.equals(current)) {
                results.add(other);
            }
        }
        return new ArrayList<>(results);
    }

    private static List<ServerSubLevel> findDeclaredConnections(
            ServerLevel level,
            ServerSubLevel current
    ) {
        LinkedHashSet<ServerSubLevel> results = new LinkedHashSet<>();
        PlotBlockTransform transform = PlotBlockTransform.capture(current);
        for (BlockEntity blockEntity : transform.findBlockEntities(level)) {
            if (!(blockEntity instanceof BlockEntitySubLevelActor actor)) {
                continue;
            }
            Iterable<SubLevel> dependencies = actor.sable$getConnectionDependencies();
            if (dependencies == null) {
                com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod.LOGGER.warn(
                        "Sublevel actor {} at {} returned no dependency iterable during blueprint capture",
                        blockEntity.getType(),
                        blockEntity.getBlockPos()
                );
                continue;
            }
            for (SubLevel dependency : dependencies) {
                if (dependency instanceof ServerSubLevel serverSubLevel && !serverSubLevel.equals(current)) {
                    results.add(serverSubLevel);
                }
            }
        }
        return new ArrayList<>(results);
    }

    private static List<ServerSubLevel> findNeighbors(
            ServerSubLevel current,
            Map<UUID, ServerSubLevel> allSublevels,
            double maximumGap
    ) {
        LinkedHashSet<ServerSubLevel> results = new LinkedHashSet<>();
        BoundingBox currentBounds = toMinecraftBounds(current);
        for (ServerSubLevel candidate : allSublevels.values()) {
            if (!candidate.equals(current)
                    && withinDistance(currentBounds, toMinecraftBounds(candidate), maximumGap)) {
                results.add(candidate);
            }
        }
        return new ArrayList<>(results);
    }

    private static BoundingBox toMinecraftBounds(ServerSubLevel subLevel) {
        var bounds = new BoundingBox3i(new BoundingBox3d(subLevel.boundingBox()));
        return new BoundingBox(
                bounds.minX(),
                bounds.minY(),
                bounds.minZ(),
                bounds.maxX(),
                bounds.maxY(),
                bounds.maxZ()
        );
    }

    private static boolean withinDistance(BoundingBox first, BoundingBox second, double maximumGap) {
        return axisGap(first.minX(), first.maxX(), second.minX(), second.maxX()) <= maximumGap
                && axisGap(first.minY(), first.maxY(), second.minY(), second.maxY()) <= maximumGap
                && axisGap(first.minZ(), first.maxZ(), second.minZ(), second.maxZ()) <= maximumGap;
    }

    private static double axisGap(int firstMin, int firstMax, int secondMin, int secondMax) {
        if (firstMax < secondMin) {
            return secondMin - firstMax;
        }
        if (secondMax < firstMin) {
            return firstMin - secondMax;
        }
        return 0.0D;
    }
}
