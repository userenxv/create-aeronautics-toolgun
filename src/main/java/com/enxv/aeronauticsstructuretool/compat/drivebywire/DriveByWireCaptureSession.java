package com.enxv.aeronauticsstructuretool.compat.drivebywire;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;

public final class DriveByWireCaptureSession {
    private final String blueprintName;
    private final DriveByWireBlueprintSummary summary = new DriveByWireBlueprintSummary();

    public DriveByWireCaptureSession(String blueprintName) {
        this.blueprintName = blueprintName;
    }

    public CompoundTag captureSnapshot(
            ServerLevel level,
            ServerSubLevel subLevel,
            SubLevelSchematicSerializationContext context
    ) throws IOException {
        // Sable plot centers are already absolute positions in the plot storage space.
        BlockPos anchor = subLevel.getPlot().getCenterBlock();
        CompoundTag snapshot = DriveByWireSnapshotNbt.normalizeCapturedSnapshot(
                DriveByWireApiBridge.captureSnapshot(level, anchor, Direction.NORTH, context)
        );
        if (!snapshot.isEmpty()) {
            DriveByWireSnapshotNbt.markCenterRelativeCoordinates(snapshot);
            int unsupported = snapshot.getInt(DriveByWireSnapshotNbt.UNSUPPORTED_CONNECTIONS);
            if (unsupported > 0) {
                AeronauticsStructureToolMod.LOGGER.warn(
                        "DriveByWire capture for '{}' sublevel {} omitted {} connection(s) outside the captured structure",
                        blueprintName,
                        subLevel.getUniqueId(),
                        unsupported
                );
            }
            DriveByWireBlueprintDiagnostics.logSnapshot("save", blueprintName, BlockPos.ZERO, snapshot);
        }
        return snapshot;
    }

    public void finishPlot(CompoundTag plotTag, CompoundTag syntheticSnapshot) throws IOException {
        if (!syntheticSnapshot.isEmpty()) {
            plotTag.put(DriveByWireSnapshotNbt.SYNTHETIC_SNAPSHOT, syntheticSnapshot.copy());
        }
        DriveByWireSnapshotNbt.removeOwnerOnlyEmptyNetworks(plotTag);
        summary.include(plotTag);
        DriveByWireSnapshotNbt.forEachMutableSnapshot(plotTag, (position, snapshot) -> {
            DriveByWireSnapshotNbt.markCenterRelativeCoordinates(snapshot);
            DriveByWireBlueprintDiagnostics.logSnapshot("save", blueprintName, position, snapshot);
        });
        validateSavedSnapshots(plotTag);
    }

    public void complete(boolean logSummary) {
        if (logSummary) {
            DriveByWireBlueprintDiagnostics.logSummary("save", blueprintName, summary);
        }
    }

    private void validateSavedSnapshots(CompoundTag plotTag) throws IOException {
        IOException[] failure = new IOException[1];
        DriveByWireSnapshotNbt.forEachMutableSnapshot(plotTag, (position, snapshot) -> {
            if (failure[0] != null) {
                return;
            }
            try {
                DriveByWireSnapshotNbt.validate(
                        snapshot,
                        "DriveByWire snapshot for '" + blueprintName + "' at " + position,
                        false
                );
            } catch (IOException exception) {
                failure[0] = exception;
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }
        if (plotTag.contains(DriveByWireSnapshotNbt.SYNTHETIC_SNAPSHOT, Tag.TAG_COMPOUND)
                && !DriveByWireApiBridge.isInstalled()) {
            throw new IOException("DriveByWire snapshot was produced while DriveByWire is not loaded");
        }
    }
}
