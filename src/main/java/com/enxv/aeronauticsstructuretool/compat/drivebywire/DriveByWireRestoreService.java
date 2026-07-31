package com.enxv.aeronauticsstructuretool.compat.drivebywire;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class DriveByWireRestoreService {
    private DriveByWireRestoreService() {
    }

    static RestoreBatchResult restore(
            ServerLevel level,
            String blueprintName,
            List<DriveByWireRestoreRequest> requests
    ) throws IOException {
        if (requests.isEmpty()) {
            return RestoreBatchResult.empty();
        }

        int expected = 0;
        int restored = 0;
        int existing = 0;
        int skipped = 0;
        List<DriveByWireRestoreRequest> deferred = new ArrayList<>();
        for (DriveByWireRestoreRequest request : requests) {
            BlockState state = level.getBlockState(request.backupBlockPos());
            Direction facing = state.hasProperty(HorizontalDirectionalBlock.FACING)
                    ? state.getValue(HorizontalDirectionalBlock.FACING)
                    : Direction.NORTH;
            DriveByWireApiBridge.RestoreResult result = DriveByWireApiBridge.restoreSnapshot(
                    level,
                    request.backupBlockPos(),
                    facing,
                    request.snapshot()
            );
            expected += result.expectedConnections();
            restored += result.restoredConnections();
            existing += result.existingConnections();
            skipped += result.skippedConnections();
            if (shouldRetry(result)) {
                deferred.add(request);
                continue;
            }
            validateResolvedResult(blueprintName, request, result);
            DriveByWireApiBridge.clearPendingBackup(level, request.backupBlockPos());
        }

        AeronauticsStructureToolMod.LOGGER.info(
                "DriveByWire restore pass for '{}': snapshots={} expected={} restored={} existing={} deferredSnapshots={} omitted={}",
                blueprintName,
                requests.size(),
                expected,
                restored,
                existing,
                deferred.size(),
                skipped
        );
        if (skipped > 0) {
            AeronauticsStructureToolMod.LOGGER.warn(
                    "DriveByWire restore for '{}' omitted {} connection(s) that were outside the captured structure",
                    blueprintName,
                    skipped
            );
        }
        return new RestoreBatchResult(deferred, restored, existing);
    }

    static boolean shouldRetry(DriveByWireApiBridge.RestoreResult result) {
        return !result.attempted() || result.deferredConnections() > 0;
    }

    private static void validateResolvedResult(
            String blueprintName,
            DriveByWireRestoreRequest request,
            DriveByWireApiBridge.RestoreResult result
    ) throws IOException {
        int resolved = result.restoredConnections() + result.existingConnections();
        if (resolved != result.expectedConnections()) {
            throw new IOException(
                    "DriveByWire restored " + resolved + " of " + result.expectedConnections()
                            + " expected connection(s) for '" + blueprintName + "'"
            );
        }
    }

    record RestoreBatchResult(
            List<DriveByWireRestoreRequest> deferredRequests,
            int restoredConnections,
            int existingConnections
    ) {
        RestoreBatchResult {
            deferredRequests = List.copyOf(deferredRequests);
        }

        static RestoreBatchResult empty() {
            return new RestoreBatchResult(List.of(), 0, 0);
        }
    }
}
