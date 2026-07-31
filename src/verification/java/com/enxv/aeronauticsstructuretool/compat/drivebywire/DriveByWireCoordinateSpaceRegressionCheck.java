package com.enxv.aeronauticsstructuretool.compat.drivebywire;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.io.IOException;
import java.util.UUID;

public final class DriveByWireCoordinateSpaceRegressionCheck {
    private static final UUID OWNER = UUID.fromString("c1669241-ee78-4591-bc5c-e47fe2718cd5");
    private static final BlockPos LEGACY_PLOT_CENTER = new BlockPos(1032, 128, 1032);

    private DriveByWireCoordinateSpaceRegressionCheck() {
    }

    public static void main(String[] args) throws Exception {
        verifyLegacyLineControlCoordinatesAreMigratedOnce();
        verifyCurrentCoordinatesRemainUntouched();
        verifyUnknownCoordinateSpaceIsRejected();
        verifyTallWorldContentAnchorCorrection();
    }

    private static void verifyLegacyLineControlCoordinatesAreMigratedOnce() throws IOException {
        CompoundTag snapshot = snapshot(false);
        int migrated = DriveByWireSnapshotNbt.migrateLegacyOwnerCoordinates(
                snapshot,
                (ownerId, savedLocalPosition) -> {
                    require(OWNER.equals(ownerId), "legacy endpoint owner changed");
                    return savedLocalPosition.subtract(LEGACY_PLOT_CENTER);
                }
        );

        require(migrated == 2, "both legacy endpoints must be migrated");
        CompoundTag connection = snapshot.getList(DriveByWireSnapshotNbt.CONNECTIONS, 10).getCompound(0);
        require(
                BlockPos.of(connection.getLong(DriveByWireSnapshotNbt.SOURCE)).equals(new BlockPos(0, 1, 0)),
                "legacy line-control source was not converted to Plot-center-relative coordinates"
        );
        require(
                BlockPos.of(connection.getLong(DriveByWireSnapshotNbt.SINK)).equals(new BlockPos(3, 1, -3)),
                "legacy line-control sink was not converted to Plot-center-relative coordinates"
        );
        require(
                snapshot.getInt(DriveByWireSnapshotNbt.AST_COORDINATE_SPACE_VERSION)
                        == DriveByWireSnapshotNbt.AST_CENTER_RELATIVE_COORDINATES,
                "migrated snapshot was not marked with its coordinate space"
        );

        int repeated = DriveByWireSnapshotNbt.migrateLegacyOwnerCoordinates(
                snapshot,
                (ownerId, savedLocalPosition) -> {
                    throw new IllegalStateException("coordinate migration ran twice");
                }
        );
        require(repeated == 0, "marked coordinates must not be migrated twice");
    }

    private static void verifyCurrentCoordinatesRemainUntouched() throws IOException {
        CompoundTag snapshot = snapshot(true);
        CompoundTag before = snapshot.copy();
        int migrated = DriveByWireSnapshotNbt.migrateLegacyOwnerCoordinates(
                snapshot,
                (ownerId, savedLocalPosition) -> {
                    throw new IllegalStateException("current coordinates were treated as legacy");
                }
        );
        require(migrated == 0, "current coordinate space must not be migrated");
        require(snapshot.equals(before), "current DriveByWire snapshot changed during migration");
    }

    private static void verifyUnknownCoordinateSpaceIsRejected() {
        CompoundTag snapshot = snapshot(false);
        snapshot.putInt(DriveByWireSnapshotNbt.AST_COORDINATE_SPACE_VERSION, 2);
        try {
            DriveByWireSnapshotNbt.migrateLegacyOwnerCoordinates(
                    snapshot,
                    (ownerId, savedLocalPosition) -> savedLocalPosition
            );
            throw new IllegalStateException("unknown coordinate space was accepted");
        } catch (IOException expected) {
            require(expected.getMessage().contains("unsupported AST coordinate-space version"),
                    "unknown coordinate-space failure was not specific");
        }
    }

    private static void verifyTallWorldContentAnchorCorrection() {
        require(
                DriveByWirePlacementCoordinateMapper.contentToPlotCenterYShift(128, 0, 448) == -320,
                "tall-world owner endpoints must be shifted back to the preserved content Y"
        );
        require(
                DriveByWirePlacementCoordinateMapper.contentToPlotCenterYShift(448, 0, 448) == 0,
                "a blueprint captured in the same tall world must not be shifted"
        );
        require(
                DriveByWirePlacementCoordinateMapper.contentToPlotCenterYShift(128, 16, 144) == 0,
                "vertical layout shifts must be included in the endpoint correction"
        );
    }

    private static CompoundTag snapshot(boolean currentCoordinates) {
        CompoundTag connection = new CompoundTag();
        connection.putLong(DriveByWireSnapshotNbt.SOURCE, new BlockPos(1032, 129, 1032).asLong());
        connection.putUUID(DriveByWireSnapshotNbt.SOURCE_OWNER, OWNER);
        connection.putLong(DriveByWireSnapshotNbt.SINK, new BlockPos(1035, 129, 1029).asLong());
        connection.putUUID(DriveByWireSnapshotNbt.SINK_OWNER, OWNER);
        connection.putByte(DriveByWireSnapshotNbt.DIRECTION, (byte) 3);
        connection.putString(DriveByWireSnapshotNbt.CHANNEL, "keyLeft");

        ListTag connections = new ListTag();
        connections.add(connection);
        CompoundTag snapshot = new CompoundTag();
        snapshot.putInt(DriveByWireSnapshotNbt.SNAPSHOT_VERSION, 3);
        snapshot.put(DriveByWireSnapshotNbt.CONNECTIONS, connections);
        if (currentCoordinates) {
            DriveByWireSnapshotNbt.markCenterRelativeCoordinates(snapshot);
        }
        return snapshot;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
