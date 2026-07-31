package com.enxv.aeronauticsstructuretool.compat.drivebywire;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.util.UUID;

public final class DriveByWireMissingDependencyRegressionCheck {
    private DriveByWireMissingDependencyRegressionCheck() {
    }

    public static void main(String[] args) {
        verifyMissingDependencySnapshotsAreDiscarded();
        verifyCapturedOwnerOnlyNetworksAreNormalized();
    }

    private static void verifyMissingDependencySnapshotsAreDiscarded() {
        CompoundTag plot = new CompoundTag();
        plot.put(DriveByWireSnapshotNbt.SYNTHETIC_SNAPSHOT, snapshot(2));

        CompoundTag backup = new CompoundTag();
        backup.putString(DriveByWireSnapshotNbt.BLOCK_ENTITY_ID, DriveByWireSnapshotNbt.BACKUP_BLOCK_ID);
        backup.put(DriveByWireSnapshotNbt.BLOCK_ENTITY_SNAPSHOT, snapshot(1));
        ListTag blockEntities = new ListTag();
        blockEntities.add(backup);
        CompoundTag chunk = new CompoundTag();
        chunk.put("block_entities", blockEntities);
        CompoundTag chunks = new CompoundTag();
        chunks.put("0", chunk);
        plot.put("chunks", chunks);

        DriveByWireSnapshotNbt.DiscardResult discarded =
                DriveByWireSnapshotNbt.discardSnapshots(plot);
        require(discarded.snapshots() == 2, "all DriveByWire snapshots must be counted");
        require(discarded.connections() == 3, "all skipped DriveByWire connections must be counted");
        require(!plot.contains(DriveByWireSnapshotNbt.SYNTHETIC_SNAPSHOT),
                "synthetic DriveByWire snapshot survived missing-mod fallback");
        require(!backup.contains(DriveByWireSnapshotNbt.BLOCK_ENTITY_SNAPSHOT),
                "backup-block DriveByWire snapshot survived missing-mod fallback");
    }

    private static void verifyCapturedOwnerOnlyNetworksAreNormalized() {
        CompoundTag ownerOnly = new CompoundTag();
        ownerOnly.putUUID(
                DriveByWireSnapshotNbt.OWNER,
                UUID.fromString("cb178669-71ab-4a88-bf65-d8fdb6396fc0")
        );
        require(
                DriveByWireSnapshotNbt.normalizeCapturedSnapshot(ownerOnly.copy()).isEmpty(),
                "fresh owner-only DriveByWire network was not normalized to an empty snapshot"
        );

        CompoundTag plot = new CompoundTag();
        plot.put(DriveByWireSnapshotNbt.SYNTHETIC_SNAPSHOT, ownerOnly.copy());
        CompoundTag backup = new CompoundTag();
        backup.putString(DriveByWireSnapshotNbt.BLOCK_ENTITY_ID, DriveByWireSnapshotNbt.BACKUP_BLOCK_ID);
        backup.put(DriveByWireSnapshotNbt.BLOCK_ENTITY_SNAPSHOT, ownerOnly.copy());
        ListTag blockEntities = new ListTag();
        blockEntities.add(backup);
        CompoundTag chunk = new CompoundTag();
        chunk.put("block_entities", blockEntities);
        CompoundTag chunks = new CompoundTag();
        chunks.put("0", chunk);
        plot.put("chunks", chunks);

        require(
                DriveByWireSnapshotNbt.removeOwnerOnlyEmptyNetworks(plot) == 2,
                "owner-only synthetic and backup-block networks were not both removed"
        );
        require(!plot.contains(DriveByWireSnapshotNbt.SYNTHETIC_SNAPSHOT),
                "owner-only synthetic network survived capture normalization");
        require(!backup.contains(DriveByWireSnapshotNbt.BLOCK_ENTITY_SNAPSHOT),
                "owner-only backup-block network survived capture normalization");

        CompoundTag malformed = ownerOnly.copy();
        malformed.putInt(DriveByWireSnapshotNbt.SNAPSHOT_VERSION, 3);
        require(
                !DriveByWireSnapshotNbt.normalizeCapturedSnapshot(malformed).isEmpty(),
                "malformed non-empty DriveByWire snapshot was silently discarded"
        );
        try {
            DriveByWireSnapshotNbt.validate(malformed, "regression snapshot", false);
            throw new IllegalStateException("malformed DriveByWire snapshot bypassed strict validation");
        } catch (IOException expected) {
            require(expected.getMessage().contains("has no DriveByWire connection list"),
                    "malformed DriveByWire failure was not specific");
        }
    }

    private static CompoundTag snapshot(int connectionCount) {
        CompoundTag snapshot = new CompoundTag();
        ListTag connections = new ListTag();
        for (int i = 0; i < connectionCount; i++) {
            connections.add(new CompoundTag());
        }
        snapshot.put(DriveByWireSnapshotNbt.CONNECTIONS, connections);
        return snapshot;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
