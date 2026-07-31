package com.enxv.aeronauticsstructuretool.compat.drivebywire;

import com.enxv.aeronauticsstructuretool.blueprint.compat.BlockEntityCompatibilityPipeline;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.util.UUID;
import java.util.function.BiConsumer;

final class DriveByWireSnapshotNbt {
    static final String MOD_ID = "drivebywire";
    static final String NAMESPACE_PREFIX = "drivebywire:";
    static final String BACKUP_BLOCK_ID = "drivebywire:backup_block";
    static final String CONTROLLER_HUB_ID = "drivebywire:controller_hub";
    static final String TWEAKED_CONTROLLER_HUB_ID = "drivebywire:tweaked_controller_hub";

    static final String BLOCK_ENTITY_ID = "id";
    static final String BLOCK_ENTITY_SNAPSHOT = "WireNetwork";
    static final String SYNTHETIC_SNAPSHOT = "AST_DriveByWireWireNetwork";
    static final String CONNECTIONS = "Connections";
    static final String SOURCE = "Source";
    static final String SINK = "Sink";
    static final String DIRECTION = "Direction";
    static final String CHANNEL = "Channel";
    static final String SOURCE_OWNER = "SourceOwnerSubLevel";
    static final String SINK_OWNER = "SinkOwnerSubLevel";
    static final String OWNER = "OwnerSubLevel";
    static final String SNAPSHOT_VERSION = "SnapshotVersion";
    static final String UNSUPPORTED_CONNECTIONS = "UnsupportedConnections";
    static final String PLACEMENT_RESOLVED = "PlacementResolved";
    static final String AST_COORDINATE_SPACE_VERSION = "AST_CoordinateSpaceVersion";
    static final int AST_CENTER_RELATIVE_COORDINATES = 1;

    private DriveByWireSnapshotNbt() {
    }

    static void forEachMutableSnapshot(CompoundTag plotTag, BiConsumer<BlockPos, CompoundTag> consumer) {
        if (plotTag.contains(SYNTHETIC_SNAPSHOT, Tag.TAG_COMPOUND)) {
            consumer.accept(BlockPos.ZERO, plotTag.getCompound(SYNTHETIC_SNAPSHOT));
        }
        BlockEntityCompatibilityPipeline.forEachBlockEntity(plotTag, blockEntityTag -> {
            if (BACKUP_BLOCK_ID.equals(blockEntityTag.getString(BLOCK_ENTITY_ID))
                    && blockEntityTag.contains(BLOCK_ENTITY_SNAPSHOT, Tag.TAG_COMPOUND)) {
                consumer.accept(readBlockEntityPos(blockEntityTag), blockEntityTag.getCompound(BLOCK_ENTITY_SNAPSHOT));
            }
        });
    }

    static CompoundTag normalizeCapturedSnapshot(CompoundTag snapshot) {
        return isOwnerOnlyEmptyNetwork(snapshot) ? new CompoundTag() : snapshot;
    }

    static int removeOwnerOnlyEmptyNetworks(CompoundTag plotTag) {
        int removed = 0;
        if (plotTag.contains(SYNTHETIC_SNAPSHOT, Tag.TAG_COMPOUND)
                && isOwnerOnlyEmptyNetwork(plotTag.getCompound(SYNTHETIC_SNAPSHOT))) {
            plotTag.remove(SYNTHETIC_SNAPSHOT);
            removed++;
        }
        int[] removedBlockEntitySnapshots = {0};
        BlockEntityCompatibilityPipeline.forEachBlockEntity(plotTag, blockEntityTag -> {
            if (BACKUP_BLOCK_ID.equals(blockEntityTag.getString(BLOCK_ENTITY_ID))
                    && blockEntityTag.contains(BLOCK_ENTITY_SNAPSHOT, Tag.TAG_COMPOUND)
                    && isOwnerOnlyEmptyNetwork(blockEntityTag.getCompound(BLOCK_ENTITY_SNAPSHOT))) {
                blockEntityTag.remove(BLOCK_ENTITY_SNAPSHOT);
                removedBlockEntitySnapshots[0]++;
            }
        });
        return removed + removedBlockEntitySnapshots[0];
    }

    static void validate(
            CompoundTag snapshot,
            String owner,
            boolean requirePlacementResolved
    ) throws IOException {
        if (snapshot.isEmpty()) {
            return;
        }
        Tag rawConnections = snapshot.get(CONNECTIONS);
        if (rawConnections == null) {
            if (snapshot.getInt(UNSUPPORTED_CONNECTIONS) > 0) {
                return;
            }
            throw new IOException(owner + " has no DriveByWire connection list");
        }
        if (!(rawConnections instanceof ListTag connections)
                || (!connections.isEmpty() && connections.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IOException(owner + " has an invalid DriveByWire connection list");
        }

        for (int i = 0; i < connections.size(); i++) {
            if (!(connections.get(i) instanceof CompoundTag connection)) {
                throw new IOException(owner + " connection " + i + " is not a compound tag");
            }
            requireType(connection, SOURCE, Tag.TAG_LONG, owner, i);
            requireType(connection, SINK, Tag.TAG_LONG, owner, i);
            requireType(connection, DIRECTION, Tag.TAG_BYTE, owner, i);
            requireType(connection, CHANNEL, Tag.TAG_STRING, owner, i);
            if (connection.getByte(DIRECTION) < 0 || connection.getByte(DIRECTION) > 5) {
                throw new IOException(owner + " connection " + i + " has an invalid sink direction");
            }
            if (connection.getString(CHANNEL).isBlank()) {
                throw new IOException(owner + " connection " + i + " has an empty channel");
            }
            validateOptionalUuid(connection, SOURCE_OWNER, owner, i);
            validateOptionalUuid(connection, SINK_OWNER, owner, i);
        }

        int version = snapshot.getInt(SNAPSHOT_VERSION);
        if (requirePlacementResolved && version >= 3 && !snapshot.getBoolean(PLACEMENT_RESOLVED)) {
            throw new IOException(owner + " was not resolved for placement");
        }
    }

    static int connectionCount(CompoundTag snapshot) {
        return snapshot.getList(CONNECTIONS, Tag.TAG_COMPOUND).size();
    }

    private static boolean isOwnerOnlyEmptyNetwork(CompoundTag snapshot) {
        return snapshot.size() == 1
                && snapshot.hasUUID(OWNER)
                && !snapshot.contains(CONNECTIONS)
                && snapshot.getInt(UNSUPPORTED_CONNECTIONS) == 0;
    }

    static void markCenterRelativeCoordinates(CompoundTag snapshot) {
        if (snapshot.getInt(SNAPSHOT_VERSION) >= 3) {
            snapshot.putInt(AST_COORDINATE_SPACE_VERSION, AST_CENTER_RELATIVE_COORDINATES);
        }
    }

    static int migrateLegacyOwnerCoordinates(
            CompoundTag snapshot,
            OwnerCoordinateTransform transform
    ) throws IOException {
        if (snapshot.getInt(SNAPSHOT_VERSION) < 3) {
            return 0;
        }
        if (snapshot.contains(AST_COORDINATE_SPACE_VERSION)
                && !snapshot.contains(AST_COORDINATE_SPACE_VERSION, Tag.TAG_INT)) {
            throw new IOException("DriveByWire snapshot has an invalid AST coordinate-space version");
        }
        int coordinateSpaceVersion = snapshot.getInt(AST_COORDINATE_SPACE_VERSION);
        if (coordinateSpaceVersion == AST_CENTER_RELATIVE_COORDINATES) {
            return 0;
        }
        if (coordinateSpaceVersion != 0) {
            throw new IOException(
                    "DriveByWire snapshot uses unsupported AST coordinate-space version "
                            + coordinateSpaceVersion
            );
        }

        int migratedEndpoints = 0;
        ListTag connections = snapshot.getList(CONNECTIONS, Tag.TAG_COMPOUND);
        for (int i = 0; i < connections.size(); i++) {
            CompoundTag connection = connections.getCompound(i);
            migratedEndpoints += migrateEndpoint(connection, SOURCE, SOURCE_OWNER, transform);
            migratedEndpoints += migrateEndpoint(connection, SINK, SINK_OWNER, transform);
        }
        snapshot.putInt(AST_COORDINATE_SPACE_VERSION, AST_CENTER_RELATIVE_COORDINATES);
        return migratedEndpoints;
    }

    static DiscardResult discardSnapshots(CompoundTag plotTag) {
        int snapshots = 0;
        int connections = 0;
        if (plotTag.contains(SYNTHETIC_SNAPSHOT, Tag.TAG_COMPOUND)) {
            CompoundTag snapshot = plotTag.getCompound(SYNTHETIC_SNAPSHOT);
            snapshots++;
            connections += connectionCount(snapshot);
            plotTag.remove(SYNTHETIC_SNAPSHOT);
        }
        int[] blockEntitySnapshots = {0};
        int[] blockEntityConnections = {0};
        BlockEntityCompatibilityPipeline.forEachBlockEntity(plotTag, blockEntityTag -> {
            if (!BACKUP_BLOCK_ID.equals(blockEntityTag.getString(BLOCK_ENTITY_ID))
                    || !blockEntityTag.contains(BLOCK_ENTITY_SNAPSHOT, Tag.TAG_COMPOUND)) {
                return;
            }
            CompoundTag snapshot = blockEntityTag.getCompound(BLOCK_ENTITY_SNAPSHOT);
            blockEntitySnapshots[0]++;
            blockEntityConnections[0] += connectionCount(snapshot);
            blockEntityTag.remove(BLOCK_ENTITY_SNAPSHOT);
        });
        return new DiscardResult(
                snapshots + blockEntitySnapshots[0],
                connections + blockEntityConnections[0]
        );
    }

    static BlockPos readBlockEntityPos(CompoundTag tag) {
        return new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    private static void requireType(
            CompoundTag connection,
            String key,
            int expectedType,
            String owner,
            int connectionIndex
    ) throws IOException {
        if (!connection.contains(key, expectedType)) {
            throw new IOException(owner + " connection " + connectionIndex + " has invalid '" + key + "'");
        }
    }

    private static void validateOptionalUuid(
            CompoundTag connection,
            String key,
            String owner,
            int connectionIndex
    ) throws IOException {
        if (connection.contains(key) && !connection.hasUUID(key)) {
            throw new IOException(owner + " connection " + connectionIndex + " has invalid UUID '" + key + "'");
        }
    }

    private static int migrateEndpoint(
            CompoundTag connection,
            String positionKey,
            String ownerKey,
            OwnerCoordinateTransform transform
    ) throws IOException {
        if (!connection.contains(positionKey, Tag.TAG_LONG) || !connection.hasUUID(ownerKey)) {
            return 0;
        }
        UUID ownerId = connection.getUUID(ownerKey);
        BlockPos savedLocalPosition = BlockPos.of(connection.getLong(positionKey));
        BlockPos centerRelativePosition = transform.apply(ownerId, savedLocalPosition);
        connection.putLong(positionKey, centerRelativePosition.asLong());
        return 1;
    }

    @FunctionalInterface
    interface OwnerCoordinateTransform {
        BlockPos apply(UUID ownerId, BlockPos savedLocalPosition) throws IOException;
    }

    record DiscardResult(int snapshots, int connections) {
    }
}
