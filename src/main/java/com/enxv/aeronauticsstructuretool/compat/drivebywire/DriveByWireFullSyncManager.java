package com.enxv.aeronauticsstructuretool.compat.drivebywire;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class DriveByWireFullSyncManager {
    private static final int DEFAULT_LIFETIME_TICKS = 40;
    private static final int SYNC_INTERVAL_TICKS = 5;

    private final List<PendingSync> pendingSyncs = new ArrayList<>();
    private boolean apiUnavailable;

    public void schedule(ServerLevel level, String blueprintName) {
        if (apiUnavailable) {
            return;
        }
        pendingSyncs.add(new PendingSync(
                level.dimension(),
                blueprintName,
                DEFAULT_LIFETIME_TICKS,
                0
        ));
        AeronauticsStructureToolMod.LOGGER.debug(
                "DriveByWire full sync scheduled for '{}' in {}",
                blueprintName,
                level.dimension().location()
        );
    }

    public void scheduleOnce(ServerLevel level, String reason) {
        if (apiUnavailable) {
            return;
        }
        pendingSyncs.add(new PendingSync(level.dimension(), reason, 1, 0));
    }

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        if (pendingSyncs.isEmpty()) {
            return;
        }

        List<PendingSync> next = new ArrayList<>();
        for (PendingSync pending : pendingSyncs) {
            ServerLevel level = event.getServer().getLevel(pending.dimension());
            if (level == null) {
                continue;
            }
            int ticksRemaining = pending.ticksRemaining() - 1;
            int ticksUntilNextSync = pending.ticksUntilNextSync() - 1;
            if (ticksUntilNextSync <= 0) {
                try {
                    int syncedPlayers = syncAllPlayers(level);
                    AeronauticsStructureToolMod.LOGGER.debug(
                            "DriveByWire full sync sent for '{}' in {} to {} player(s)",
                            pending.blueprintName(),
                            pending.dimension().location(),
                            syncedPlayers
                    );
                } catch (IOException exception) {
                    disable(exception);
                    return;
                }
                ticksUntilNextSync = SYNC_INTERVAL_TICKS;
            }
            if (ticksRemaining > 0) {
                next.add(new PendingSync(
                        pending.dimension(),
                        pending.blueprintName(),
                        ticksRemaining,
                        ticksUntilNextSync
                ));
            }
        }
        pendingSyncs.clear();
        pendingSyncs.addAll(next);
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            pendingSyncs.removeIf(pending -> pending.dimension().equals(level.dimension()));
        }
    }

    private static int syncAllPlayers(ServerLevel level) throws IOException {
        int syncedPlayers = 0;
        for (ServerPlayer player : level.players()) {
            DriveByWireApiBridge.sendFullSync(player);
            syncedPlayers++;
        }
        return syncedPlayers;
    }

    private void disable(IOException exception) {
        apiUnavailable = true;
        pendingSyncs.clear();
        AeronauticsStructureToolMod.LOGGER.warn(
                "DriveByWire full-sync retries were disabled: {}",
                exception.getMessage()
        );
    }

    private record PendingSync(
            ResourceKey<Level> dimension,
            String blueprintName,
            int ticksRemaining,
            int ticksUntilNextSync
    ) {
    }
}
