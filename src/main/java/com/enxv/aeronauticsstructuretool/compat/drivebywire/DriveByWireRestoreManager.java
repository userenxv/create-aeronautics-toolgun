package com.enxv.aeronauticsstructuretool.compat.drivebywire;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.core.FailureMessages;
import com.enxv.aeronauticsstructuretool.server.BlueprintPlacementWarningNotifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.Set;

/** Retries DriveByWire endpoint-unavailable restores every 20 ticks. */
public final class DriveByWireRestoreManager {
    private static final int RETRY_INTERVAL_TICKS = 20;
    private static final int MAX_RETRY_ATTEMPTS = 40;

    private final DriveByWireFullSyncManager fullSyncManager;
    private final List<PendingBatch> pending = new ArrayList<>();

    public DriveByWireRestoreManager(DriveByWireFullSyncManager fullSyncManager) {
        this.fullSyncManager = fullSyncManager;
    }

    public void queue(
            ServerLevel level,
            String blueprintName,
            DriveByWireLoadResult loadResult,
            UUID notificationPlayerId
    ) {
        if (loadResult.deferredRestoreRequests().isEmpty()) {
            return;
        }
        pending.add(new PendingBatch(
                level,
                blueprintName,
                loadResult.deferredRestoreRequests(),
                notificationPlayerId
        ));
        AeronauticsStructureToolMod.LOGGER.info(
                "Queued {} deferred DriveByWire snapshot(s) for blueprint '{}'",
                loadResult.deferredRestoreRequests().size(),
                blueprintName
        );
    }

    // DriveByWire owns live links; this only clears matching placement retries.
    public int cancelForSubLevels(ServerLevel level, Set<UUID> subLevelIds) {
        if (subLevelIds.isEmpty()) {
            return 0;
        }
        int removedConnections = 0;
        Iterator<PendingBatch> batches = pending.iterator();
        while (batches.hasNext()) {
            PendingBatch batch = batches.next();
            if (batch.level != level) {
                continue;
            }
            Iterator<DriveByWireRestoreRequest> requests = batch.requests.iterator();
            while (requests.hasNext()) {
                DriveByWireRestoreRequest request = requests.next();
                removedConnections += removeConnections(request.snapshot(), subLevelIds);
                ListTag remaining = request.snapshot().getList(
                        DriveByWireSnapshotNbt.CONNECTIONS,
                        Tag.TAG_COMPOUND
                );
                if (remaining.isEmpty()) {
                    requests.remove();
                }
            }
            if (batch.requests.isEmpty()) {
                batches.remove();
            }
        }
        return removedConnections;
    }

    private static int removeConnections(CompoundTag snapshot, Set<UUID> subLevelIds) {
        ListTag connections = snapshot.getList(DriveByWireSnapshotNbt.CONNECTIONS, Tag.TAG_COMPOUND);
        int removed = 0;
        for (int index = connections.size() - 1; index >= 0; index--) {
            CompoundTag connection = connections.getCompound(index);
            if (referencesAny(connection, subLevelIds)
                    || (!connection.hasUUID(DriveByWireSnapshotNbt.SOURCE_OWNER)
                    && !connection.hasUUID(DriveByWireSnapshotNbt.SINK_OWNER)
                    && snapshot.hasUUID(DriveByWireSnapshotNbt.OWNER)
                    && subLevelIds.contains(snapshot.getUUID(DriveByWireSnapshotNbt.OWNER)))) {
                connections.remove(index);
                removed++;
            }
        }
        return removed;
    }

    private static boolean referencesAny(CompoundTag connection, Set<UUID> subLevelIds) {
        return references(connection, DriveByWireSnapshotNbt.SOURCE_OWNER, subLevelIds)
                || references(connection, DriveByWireSnapshotNbt.SINK_OWNER, subLevelIds)
                || references(connection, DriveByWireSnapshotNbt.OWNER, subLevelIds);
    }

    private static boolean references(CompoundTag tag, String key, Set<UUID> subLevelIds) {
        return tag.hasUUID(key) && subLevelIds.contains(tag.getUUID(key));
    }

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        Iterator<PendingBatch> iterator = pending.iterator();
        while (iterator.hasNext()) {
            PendingBatch batch = iterator.next();
            if (batch.level.getServer() != event.getServer()) {
                continue;
            }
            if (--batch.ticksUntilRetry > 0) {
                continue;
            }
            batch.ticksUntilRetry = RETRY_INTERVAL_TICKS;
            batch.attempts++;

            try {
                DriveByWireRestoreService.RestoreBatchResult result =
                        DriveByWireRestoreService.restore(
                                batch.level,
                                batch.blueprintName,
                                batch.requests
                        );
                batch.requests.clear();
                batch.requests.addAll(result.deferredRequests());
                if (batch.requests.isEmpty()) {
                    fullSyncManager.schedule(batch.level, batch.blueprintName);
                    AeronauticsStructureToolMod.LOGGER.info(
                            "Finished deferred DriveByWire restore for '{}' after {} retry attempt(s)",
                            batch.blueprintName,
                            batch.attempts
                    );
                    iterator.remove();
                    continue;
                }
            } catch (IOException exception) {
                fail(batch, FailureMessages.describe(exception, "unknown DriveByWire error"));
                iterator.remove();
                continue;
            }

            if (batch.attempts >= MAX_RETRY_ATTEMPTS) {
                fail(
                        batch,
                        batch.requests.size() + " DriveByWire snapshot(s) still had unavailable endpoints after "
                                + MAX_RETRY_ATTEMPTS + " retries"
                );
                iterator.remove();
            }
        }
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            pending.removeIf(batch -> batch.level == level);
        }
    }

    private static void fail(PendingBatch batch, String reason) {
        AeronauticsStructureToolMod.LOGGER.error(
                "Deferred DriveByWire restore failed for blueprint '{}': {}",
                batch.blueprintName,
                reason
        );
        BlueprintPlacementWarningNotifier.notify(
                batch.level,
                batch.notificationPlayerId,
                "DriveByWire links",
                reason
        );
    }

    private static final class PendingBatch {
        private final ServerLevel level;
        private final String blueprintName;
        private final List<DriveByWireRestoreRequest> requests;
        private final UUID notificationPlayerId;
        private int attempts;
        private int ticksUntilRetry = RETRY_INTERVAL_TICKS;

        private PendingBatch(
                ServerLevel level,
                String blueprintName,
                List<DriveByWireRestoreRequest> requests,
                UUID notificationPlayerId
        ) {
            this.level = level;
            this.blueprintName = blueprintName;
            this.requests = new ArrayList<>(requests);
            this.notificationPlayerId = notificationPlayerId;
        }
    }
}
