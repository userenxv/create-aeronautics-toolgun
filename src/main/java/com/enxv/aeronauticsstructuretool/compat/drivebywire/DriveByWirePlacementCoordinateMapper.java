package com.enxv.aeronauticsstructuretool.compat.drivebywire;

import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Maps center-relative DriveByWire endpoints to a loaded Sable plot. Blueprint
 * content may retain its source Y, so its anchor can differ from the plot center.
 */
final class DriveByWirePlacementCoordinateMapper {
    private DriveByWirePlacementCoordinateMapper() {
    }

    static int alignOwnerEndpoints(
            CompoundTag snapshot,
            Map<UUID, LoadedSubLevel> loadedSublevels
    ) throws IOException {
        if (snapshot.getInt(DriveByWireSnapshotNbt.SNAPSHOT_VERSION) < 3
                || snapshot.getInt(DriveByWireSnapshotNbt.AST_COORDINATE_SPACE_VERSION)
                != DriveByWireSnapshotNbt.AST_CENTER_RELATIVE_COORDINATES
                || !snapshot.contains(DriveByWireSnapshotNbt.CONNECTIONS, Tag.TAG_LIST)) {
            return 0;
        }

        int adjusted = 0;
        ListTag connections = snapshot.getList(DriveByWireSnapshotNbt.CONNECTIONS, Tag.TAG_COMPOUND);
        for (int i = 0; i < connections.size(); i++) {
            CompoundTag connection = connections.getCompound(i);
            adjusted += alignEndpoint(
                    connection,
                    DriveByWireSnapshotNbt.SOURCE,
                    DriveByWireSnapshotNbt.SOURCE_OWNER,
                    loadedSublevels,
                    i
            );
            adjusted += alignEndpoint(
                    connection,
                    DriveByWireSnapshotNbt.SINK,
                    DriveByWireSnapshotNbt.SINK_OWNER,
                    loadedSublevels,
                    i
            );
        }
        return adjusted;
    }

    private static int alignEndpoint(
            CompoundTag connection,
            String positionKey,
            String ownerKey,
            Map<UUID, LoadedSubLevel> loadedSublevels,
            int connectionIndex
    ) throws IOException {
        if (!connection.contains(positionKey, Tag.TAG_LONG) || !connection.hasUUID(ownerKey)) {
            return 0;
        }

        UUID ownerId = connection.getUUID(ownerKey);
        LoadedSubLevel loaded = findByRuntimeId(loadedSublevels, ownerId);
        if (loaded == null) {
            throw new IOException(
                    "DriveByWire connection " + connectionIndex
                            + " references missing placed sublevel " + ownerId
            );
        }

        int sourceAnchorY = (int) Math.round(loaded.saved().localAnchor().y);
        int yShift = contentToPlotCenterYShift(
                sourceAnchorY,
                loaded.verticalLayout().blockYShift(),
                loaded.subLevel().getPlot().getCenterBlock().getY()
        );
        if (yShift == 0) {
            return 0;
        }

        BlockPos localEndpoint = BlockPos.of(connection.getLong(positionKey));
        connection.putLong(
                positionKey,
                new BlockPos(localEndpoint.getX(), localEndpoint.getY() + yShift, localEndpoint.getZ()).asLong()
        );
        return 1;
    }

    static int contentToPlotCenterYShift(
            int sourceContentAnchorY,
            int blockYShift,
            int targetPlotCenterY
    ) {
        return sourceContentAnchorY + blockYShift - targetPlotCenterY;
    }

    private static LoadedSubLevel findByRuntimeId(
            Map<UUID, LoadedSubLevel> loadedSublevels,
            UUID runtimeId
    ) {
        for (LoadedSubLevel loaded : loadedSublevels.values()) {
            if (loaded.subLevel().getUniqueId().equals(runtimeId)) {
                return loaded;
            }
        }
        return null;
    }
}
