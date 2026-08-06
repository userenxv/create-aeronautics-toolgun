package com.enxv.aeronauticsstructuretool.toolgun;

import com.enxv.aeronauticsstructuretool.blueprint.lifecycle.SubLevelRemovalCoordinator;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class StructureDeleteService {
    private StructureDeleteService() {
    }

    public static void delete(
            ServerLevel level,
            BlockPos clickedPos,
            boolean rangeDeleteEnabled,
            int deleteRange
    ) {
        SubLevel containing = Sable.HELPER.getContaining(level, clickedPos);
        if (!(containing instanceof ServerSubLevel root)) {
            throw new IllegalStateException("not a physical structure");
        }
        ServerSubLevelContainer container = requireContainer(level);
        SubLevelRemovalCoordinator.remove(
                level,
                container,
                resolveTargets(container, root, rangeDeleteEnabled, deleteRange)
        );
    }

    private static List<ServerSubLevel> resolveTargets(
            ServerSubLevelContainer container,
            ServerSubLevel root,
            boolean rangeDeleteEnabled,
            int deleteRange
    ) {
        if (!rangeDeleteEnabled || deleteRange <= 0) {
            return List.of(root);
        }

        Set<ServerSubLevel> targets = new LinkedHashSet<>();
        targets.add(root);
        BoundingBox rootBounds = toMinecraftBounds(root);
        for (ServerSubLevel candidate : container.getAllSubLevels()) {
            if (candidate == null || candidate.equals(root)) {
                continue;
            }
            if (withinDistance(rootBounds, toMinecraftBounds(candidate), deleteRange)) {
                targets.add(candidate);
            }
        }
        return new ArrayList<>(targets);
    }

    private static ServerSubLevelContainer requireContainer(ServerLevel level) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            throw new IllegalStateException("sublevel container unavailable");
        }
        return container;
    }

    private static BoundingBox toMinecraftBounds(ServerSubLevel subLevel) {
        BoundingBox3i bounds = new BoundingBox3i(new BoundingBox3d(subLevel.boundingBox()));
        return new BoundingBox(
                bounds.minX(),
                bounds.minY(),
                bounds.minZ(),
                bounds.maxX(),
                bounds.maxY(),
                bounds.maxZ()
        );
    }

    private static boolean withinDistance(BoundingBox first, BoundingBox second, int maxGap) {
        return axisGap(first.minX(), first.maxX(), second.minX(), second.maxX()) <= maxGap
                && axisGap(first.minY(), first.maxY(), second.minY(), second.maxY()) <= maxGap
                && axisGap(first.minZ(), first.maxZ(), second.minZ(), second.maxZ()) <= maxGap;
    }

    private static int axisGap(int firstMin, int firstMax, int secondMin, int secondMax) {
        if (firstMax < secondMin) {
            return secondMin - firstMax;
        }
        if (secondMax < firstMin) {
            return firstMin - secondMax;
        }
        return 0;
    }
}
