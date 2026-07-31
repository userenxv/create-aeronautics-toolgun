package com.enxv.aeronauticsstructuretool.compat.drivebywire;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class DriveByWireWorldSourceRefreshManager {
    private static final int MAX_IDLE_TICKS = 200;

    private final Map<SourceKey, WatchedSource> watchedSources = new LinkedHashMap<>();
    private boolean apiUnavailable;

    public void watchSources(ServerLevel level, List<DriveByWireWorldSource> sources) {
        if (sources.isEmpty() || apiUnavailable) {
            return;
        }
        for (DriveByWireWorldSource source : sources) {
            SourceKey key = new SourceKey(
                    level.dimension(),
                    source.ownerSubLevelId(),
                    source.localSourcePos().immutable()
            );
            watchedSources.compute(
                    key,
                    (ignored, watched) -> watched == null ? WatchedSource.initial() : watched.resetIdle()
            );
        }
    }

    public int forgetSubLevels(ServerLevel level, java.util.Set<UUID> subLevelIds) {
        if (subLevelIds.isEmpty()) {
            return 0;
        }
        int previousSize = watchedSources.size();
        watchedSources.keySet().removeIf(key -> key.dimension().equals(level.dimension())
                && subLevelIds.contains(key.ownerSubLevelId()));
        return previousSize - watchedSources.size();
    }

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        if (watchedSources.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<SourceKey, WatchedSource>> iterator = watchedSources.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SourceKey, WatchedSource> entry = iterator.next();
            SourceKey key = entry.getKey();
            WatchedSource watched = entry.getValue();
            ServerLevel level = event.getServer().getLevel(key.dimension());
            if (level == null) {
                iterator.remove();
                continue;
            }
            if (!(SubLevelContainer.getContainer(level) instanceof ServerSubLevelContainer container)) {
                iterator.remove();
                continue;
            }
            if (!(container.getSubLevel(key.ownerSubLevelId()) instanceof ServerSubLevel subLevel)) {
                WatchedSource updated = watched.incrementIdle();
                if (updated.idleTicks() > MAX_IDLE_TICKS) {
                    iterator.remove();
                } else {
                    entry.setValue(updated);
                }
                continue;
            }

            BlockPos worldSourcePos = subLevel.getPlot().getCenterBlock().offset(key.localSourcePos());
            try {
                int signal = DriveByWireApiBridge.computeWorldSignal(level, worldSourcePos);
                if (!Objects.equals(watched.lastWorldSourcePos(), worldSourcePos)
                        || !Objects.equals(watched.lastSignal(), signal)) {
                    DriveByWireApiBridge.pushWorldSignal(level, worldSourcePos, signal);
                    AeronauticsStructureToolMod.LOGGER.debug(
                            "DriveByWire world source refreshed in {}: owner={} local={} world={} signal={}",
                            key.dimension().location(),
                            key.ownerSubLevelId(),
                            key.localSourcePos(),
                            worldSourcePos,
                            signal
                    );
                    entry.setValue(new WatchedSource(worldSourcePos.immutable(), signal, 0));
                } else {
                    entry.setValue(watched.resetIdle().withWorldPos(worldSourcePos));
                }
            } catch (IOException exception) {
                disable(exception);
                return;
            }
        }
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            watchedSources.keySet().removeIf(key -> key.dimension().equals(level.dimension()));
        }
    }

    private void disable(IOException exception) {
        apiUnavailable = true;
        watchedSources.clear();
        AeronauticsStructureToolMod.LOGGER.warn(
                "DriveByWire world-source refresh was disabled: {}",
                exception.getMessage()
        );
    }

    private record SourceKey(
            ResourceKey<Level> dimension,
            UUID ownerSubLevelId,
            BlockPos localSourcePos
    ) {
    }

    private record WatchedSource(BlockPos lastWorldSourcePos, Integer lastSignal, int idleTicks) {
        static WatchedSource initial() {
            return new WatchedSource(null, null, 0);
        }

        WatchedSource resetIdle() {
            return new WatchedSource(lastWorldSourcePos, lastSignal, 0);
        }

        WatchedSource incrementIdle() {
            return new WatchedSource(lastWorldSourcePos, lastSignal, idleTicks + 1);
        }

        WatchedSource withWorldPos(BlockPos worldPos) {
            return new WatchedSource(worldPos.immutable(), lastSignal, idleTicks);
        }
    }
}
