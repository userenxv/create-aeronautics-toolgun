package com.enxv.aeronauticsstructuretool.compat.synaxis;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.core.FailureMessages;
import com.enxv.aeronauticsstructuretool.server.BlueprintPlacementWarningNotifier;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class SynaxisControllerWireRestoreManager {
    private static final int MAX_ATTEMPTS = 40;
    private final List<PendingBatch> pending = new ArrayList<>();

    public void queue(
            ServerLevel level,
            String blueprintName,
            List<SynaxisControllerWireConnection> connections,
            UUID notificationPlayerId
    ) {
        if (connections.isEmpty()) {
            return;
        }
        pending.add(new PendingBatch(level, blueprintName, connections, notificationPlayerId));
        AeronauticsStructureToolMod.LOGGER.info(
                "Queued {} deferred Synaxis controller-wire connection(s) for blueprint '{}'",
                connections.size(),
                blueprintName
        );
    }

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        Iterator<PendingBatch> batchIterator = pending.iterator();
        while (batchIterator.hasNext()) {
            PendingBatch batch = batchIterator.next();
            if (batch.level.getServer() != event.getServer()) {
                continue;
            }

            batch.attempts++;
            int restoredThisTick = 0;
            Iterator<SynaxisControllerWireConnection> connectionIterator = batch.connections.iterator();
            while (connectionIterator.hasNext()) {
                SynaxisControllerWireConnection connection = connectionIterator.next();
                Attempt attempt = tryRestore(batch.level, connection);
                if (attempt.result() == AttemptResult.RESTORED) {
                    restoredThisTick++;
                    connectionIterator.remove();
                } else if (attempt.result() == AttemptResult.ALREADY_EXISTS) {
                    connectionIterator.remove();
                } else if (attempt.result() == AttemptResult.PERMANENT_FAILURE) {
                    batch.permanentFailures++;
                    notifyFailure(batch, attempt.reason() == null
                            ? describeConnection(connection) + " was rejected by Synaxis"
                            : attempt.reason());
                    connectionIterator.remove();
                }
            }

            if (restoredThisTick > 0) {
                try {
                    SynaxisControllerWireCompat.syncDimension(batch.level);
                } catch (IOException exception) {
                    AeronauticsStructureToolMod.LOGGER.error(
                            "Failed to sync deferred Synaxis controller wires for blueprint '{}'",
                            batch.blueprintName,
                            exception
                    );
                    notifyFailure(
                            batch,
                            "Synaxis dimension sync failed: "
                                    + FailureMessages.describe(exception, "unknown sync error")
                    );
                }
            }

            if (batch.connections.isEmpty()) {
                if (batch.permanentFailures > 0) {
                    AeronauticsStructureToolMod.LOGGER.warn(
                            "Finished deferred Synaxis controller-wire restore for '{}' with {} rejected connection(s)",
                            batch.blueprintName,
                            batch.permanentFailures
                    );
                } else {
                    AeronauticsStructureToolMod.LOGGER.debug(
                            "Finished deferred Synaxis controller-wire restore for '{}' after {} attempt(s)",
                            batch.blueprintName,
                            batch.attempts
                    );
                }
                batchIterator.remove();
                continue;
            }

            if (batch.attempts >= MAX_ATTEMPTS) {
                String reason = batch.connections.size() + " connection(s) were not ready after "
                        + batch.attempts + " attempts";
                AeronauticsStructureToolMod.LOGGER.error(
                        "Synaxis controller-wire restore failed for blueprint '{}': {}",
                        batch.blueprintName,
                        reason
                );
                notifyFailure(batch, reason);
                batchIterator.remove();
            }
        }
    }

    private static Attempt tryRestore(
            ServerLevel level,
            SynaxisControllerWireConnection connection
    ) {
        try {
            if (!SynaxisControllerWireCompat.sourceHasChannel(
                    level,
                    connection.sourcePos(),
                    connection.channel()
            )) {
                return new Attempt(AttemptResult.RETRY_LATER, null);
            }
            return switch (SynaxisControllerWireCompat.createConnection(level, connection)) {
                case RESTORED -> new Attempt(AttemptResult.RESTORED, null);
                case ALREADY_EXISTS -> new Attempt(AttemptResult.ALREADY_EXISTS, null);
                case RETRY_LATER -> new Attempt(AttemptResult.RETRY_LATER, null);
                case PERMANENT_FAILURE -> new Attempt(
                        AttemptResult.PERMANENT_FAILURE,
                        describeConnection(connection) + " was rejected by Synaxis"
                );
            };
        } catch (IOException exception) {
            AeronauticsStructureToolMod.LOGGER.error(
                    "Deferred Synaxis controller-wire restore failed for {} -> {} ({})",
                    connection.sourcePos(),
                    connection.sinkPos(),
                    connection.channel(),
                    exception
            );
            return new Attempt(
                    AttemptResult.PERMANENT_FAILURE,
                    describeConnection(connection) + " failed: "
                            + FailureMessages.describe(exception, "unknown Synaxis error")
            );
        }
    }

    private static String describeConnection(SynaxisControllerWireConnection connection) {
        return "connection " + connection.sourcePos() + " -> " + connection.sinkPos()
                + " (channel " + connection.channel() + ")";
    }

    private static void notifyFailure(PendingBatch batch, String reason) {
        BlueprintPlacementWarningNotifier.notify(
                batch.level,
                batch.notificationPlayerId,
                "Synaxis controller wires",
                reason
        );
    }

    private enum AttemptResult {
        RESTORED,
        ALREADY_EXISTS,
        RETRY_LATER,
        PERMANENT_FAILURE
    }

    private record Attempt(AttemptResult result, String reason) {
    }

    private static final class PendingBatch {
        private final ServerLevel level;
        private final String blueprintName;
        private final List<SynaxisControllerWireConnection> connections;
        private final UUID notificationPlayerId;
        private int attempts;
        private int permanentFailures;

        private PendingBatch(
                ServerLevel level,
                String blueprintName,
                List<SynaxisControllerWireConnection> connections,
                UUID notificationPlayerId
        ) {
            this.level = level;
            this.blueprintName = blueprintName;
            this.connections = new ArrayList<>(connections);
            this.notificationPlayerId = notificationPlayerId;
        }
    }
}
