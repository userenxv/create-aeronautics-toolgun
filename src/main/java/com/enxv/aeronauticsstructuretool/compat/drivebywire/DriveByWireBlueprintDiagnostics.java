package com.enxv.aeronauticsstructuretool.compat.drivebywire;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class DriveByWireBlueprintDiagnostics {
    private DriveByWireBlueprintDiagnostics() {
    }

    static void logSummary(String phase, String blueprintName, DriveByWireBlueprintSummary summary) {
        if (!summary.hasBlocks()) {
            return;
        }
        AeronauticsStructureToolMod.LOGGER.info(
                "DriveByWire {} scan for '{}': blocks={}, backupBlocks={}, controllerHubs={}, tweakedControllerHubs={}, backupSnapshots={}, syntheticSnapshots={}",
                phase,
                blueprintName,
                summary.blockCount(),
                summary.backupBlockCount(),
                summary.controllerHubCount(),
                summary.tweakedControllerHubCount(),
                summary.backupSnapshotCount(),
                summary.syntheticSnapshotCount()
        );
        if (!summary.hasSnapshots()) {
            AeronauticsStructureToolMod.LOGGER.info(
                    "DriveByWire {} scan for '{}' found no saved connections; DriveByWire blocks will print without cable links",
                    phase,
                    blueprintName
            );
        }
    }

    static void logSnapshot(String phase, String blueprintName, BlockPos backupBlockPos, CompoundTag snapshot) {
        if (!AeronauticsStructureToolMod.LOGGER.isDebugEnabled()) {
            return;
        }
        ListTag connections = snapshot.getList(DriveByWireSnapshotNbt.CONNECTIONS, Tag.TAG_COMPOUND);
        AeronauticsStructureToolMod.LOGGER.debug(
                "DriveByWire {} snapshot for '{}' at {}: version={}, owner={}, resolved={}, connections={}, unsupported={}",
                phase,
                blueprintName,
                backupBlockPos,
                snapshot.getInt(DriveByWireSnapshotNbt.SNAPSHOT_VERSION),
                snapshot.hasUUID(DriveByWireSnapshotNbt.OWNER)
                        ? snapshot.getUUID(DriveByWireSnapshotNbt.OWNER)
                        : "<none>",
                snapshot.getBoolean(DriveByWireSnapshotNbt.PLACEMENT_RESOLVED),
                connections.size(),
                snapshot.getInt(DriveByWireSnapshotNbt.UNSUPPORTED_CONNECTIONS)
        );
        for (int i = 0; i < connections.size(); i++) {
            CompoundTag connection = connections.getCompound(i);
            Direction direction = connection.contains(DriveByWireSnapshotNbt.DIRECTION, Tag.TAG_BYTE)
                    ? Direction.from3DDataValue(connection.getByte(DriveByWireSnapshotNbt.DIRECTION))
                    : null;
            AeronauticsStructureToolMod.LOGGER.debug(
                    "DriveByWire {} connection[{}] for '{}': source={} sourceOwner={} channel={} sink={} sinkFace={} sinkOwner={}",
                    phase,
                    i,
                    blueprintName,
                    readPos(connection, DriveByWireSnapshotNbt.SOURCE),
                    readOwner(connection, DriveByWireSnapshotNbt.SOURCE_OWNER),
                    connection.getString(DriveByWireSnapshotNbt.CHANNEL),
                    readPos(connection, DriveByWireSnapshotNbt.SINK),
                    direction,
                    readOwner(connection, DriveByWireSnapshotNbt.SINK_OWNER)
            );
        }
    }

    private static Object readPos(CompoundTag connection, String key) {
        return connection.contains(key, Tag.TAG_LONG) ? BlockPos.of(connection.getLong(key)) : "<invalid>";
    }

    private static Object readOwner(CompoundTag connection, String key) {
        return connection.hasUUID(key) ? connection.getUUID(key) : "<none>";
    }
}
