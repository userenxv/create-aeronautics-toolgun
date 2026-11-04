package com.enxv.aeronauticsstructuretool.compat.coasters;

import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.silvergold.simulatedcoasters.balloon.BalloonBlockEntity;
import dev.silvergold.simulatedcoasters.balloon.BalloonLoadReconciler;
import dev.silvergold.simulatedcoasters.rivet.RivetBlockEntity;
import dev.silvergold.simulatedcoasters.rivet.RivetHostIndex;
import dev.silvergold.simulatedcoasters.track.anchor.CoasterAnchorpointBlockEntity;
import dev.silvergold.simulatedcoasters.track.cart.CoasterCartResolve;
import dev.silvergold.simulatedcoasters.track.cart.CoasterCartTrainLinkConstraint;
import dev.silvergold.simulatedcoasters.track.cart.CoasterCartTrainLinkSync;
import dev.silvergold.simulatedcoasters.track.graph.CoasterTrackPropagator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Links copies using the mod's own APIs, without refreshing or relocating unrelated originals. */
final class CoastersRuntimeBridge {
    private CoastersRuntimeBridge() {}

    static Map<UUID, Set<UUID>> connections(Collection<ServerSubLevel> sublevels) {
        Map<UUID, Set<UUID>> graph = new HashMap<>();
        for (ServerSubLevel sub : sublevels) {
            for (var holder : sub.getPlot().getLoadedChunks()) {
                for (BlockEntity be : holder.getChunk().getBlockEntities().values()) {
                    UUID host = null;
                    if (be instanceof RivetBlockEntity rivet) {
                        SubLevel containing = Sable.HELPER.getContaining(sub.getLevel(), rivet.hostPos());
                        host = containing == null ? null : containing.getUniqueId();
                    } else if (be instanceof BalloonBlockEntity balloon) {
                        host = balloon.hostSubLevelId();
                    }
                    connect(graph, sub.getUniqueId(), host);
                }
            }
            for (var link : CoastersCartLinkNbt.read(sub.getUserDataTag())) {
                connect(graph, sub.getUniqueId(), CoastersBlockEntityNbt.uuid((net.minecraft.nbt.CompoundTag) link, "partner"));
            }
        }
        return graph;
    }

    private static void connect(Map<UUID, Set<UUID>> graph, UUID a, UUID b) {
        if (b == null || a.equals(b)) return;
        graph.computeIfAbsent(a, ignored -> new HashSet<>()).add(b);
        graph.computeIfAbsent(b, ignored -> new HashSet<>()).add(a);
    }

    static void complete(ServerLevel level, Collection<LoadedSubLevel> loaded) {
        List<BlockPos> anchors = new ArrayList<>();
        List<Runnable> locks = new ArrayList<>();
        for (LoadedSubLevel entry : loaded) {
            ServerSubLevel sub = entry.subLevel();
            for (var holder : sub.getPlot().getLoadedChunks()) {
                for (BlockEntity be : List.copyOf(holder.getChunk().getBlockEntities().values())) {
                    if (be instanceof RivetBlockEntity rivet) {
                        SubLevel host = Sable.HELPER.getContaining(level, rivet.hostPos());
                        if (host == null || host == sub || level.getBlockState(rivet.hostPos()).isAir()) {
                            throw CoastersBlockEntityNbt.invalid("placed rivet has no valid host");
                        }
                        RivetHostIndex.get(level).registerLoadedRivet(rivet.anchorKey(), sub.getUniqueId());
                        locks.add(() -> rivet.applyHostLock(sub));
                    } else if (be instanceof BalloonBlockEntity balloon) {
                        if (balloon.resolveHostSubLevel(level) == null) throw CoastersBlockEntityNbt.invalid("placed balloon has no valid host");
                        locks.add(() -> BalloonLoadReconciler.reconcileBalloon(level, sub, false));
                    } else if (be instanceof CoasterAnchorpointBlockEntity) {
                        anchors.add(be.getBlockPos());
                    }
                }
            }
        }
        // Allocation observers can cache empty plots. Refresh each new body and the derived
        // dimension lookup once, inside the mod's own cache classes, before restoring links.
        Set<UUID> newIds = new HashSet<>();
        for (LoadedSubLevel entry : loaded) newIds.add(entry.subLevel().getUniqueId());
        try (var refresh = CoastersCartCacheRefresh.begin(level.dimension(), newIds)) {
            locks.forEach(Runnable::run);
            // The graph is derived from remapped anchors, not copied from global SavedData.
            if (!anchors.isEmpty()) CoasterTrackPropagator.touch(level, anchors);
            for (LoadedSubLevel entry : loaded) {
                if (CoasterCartResolve.isCartSubLevel(entry.subLevel())) {
                    CoasterCartTrainLinkConstraint.tryRestoreLink(entry.subLevel(), level);
                    CoasterCartTrainLinkSync.broadcastCart(entry.subLevel());
                }
            }
        }
    }
}
