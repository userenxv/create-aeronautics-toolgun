package com.enxv.aeronauticsstructuretool.compat.drivebywire;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.blueprint.compat.BlockEntityCompatibilityPipeline;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.LoadedSubLevelCoordinates;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import com.enxv.aeronauticsstructuretool.blueprint.model.SavedSubLevelBlueprint;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DriveByWireLoadSession {
    private final String blueprintName;
    private final DriveByWireBlueprintSummary summary;
    private final List<DriveByWireRestoreRequest> restoreRequests = new ArrayList<>();
    private int skippedSnapshotCount;
    private int skippedConnectionCount;

    public DriveByWireLoadSession(String blueprintName, List<SavedSubLevelBlueprint> savedSublevels) {
        this.blueprintName = blueprintName;
        this.summary = DriveByWireBlueprintSummary.from(savedSublevels);
        DriveByWireBlueprintDiagnostics.logSummary("load", blueprintName, summary);
        for (SavedSubLevelBlueprint saved : savedSublevels) {
            DriveByWireSnapshotNbt.forEachMutableSnapshot(saved.plotTag(), (position, snapshot) ->
                    DriveByWireBlueprintDiagnostics.logSnapshot("load", blueprintName, position, snapshot)
            );
        }
    }

    public void preparePlot(
            CompoundTag plotTag,
            SubLevelSchematicSerializationContext placeContext,
            Map<UUID, LoadedSubLevel> loadedSublevels
    ) throws IOException {
        if (!DriveByWireApiBridge.isInstalled()) {
            DriveByWireSnapshotNbt.DiscardResult discarded =
                    DriveByWireSnapshotNbt.discardSnapshots(plotTag);
            this.skippedSnapshotCount += discarded.snapshots();
            this.skippedConnectionCount += discarded.connections();
            return;
        }
        transformSyntheticSnapshot(plotTag, placeContext, loadedSublevels);
        transformBackupBlockSnapshots(plotTag, placeContext, loadedSublevels);
    }

    public int skippedSnapshotCount() {
        return this.skippedSnapshotCount;
    }

    public int skippedConnectionCount() {
        return this.skippedConnectionCount;
    }

    public DriveByWireLoadResult complete(
            ServerLevel level,
            Map<UUID, LoadedSubLevel> loadedSublevels
    ) throws IOException {
        DriveByWireRestoreService.RestoreBatchResult restoreResult =
                DriveByWireRestoreService.restore(level, blueprintName, restoreRequests);
        return new DriveByWireLoadResult(
                collectWorldSources(loadedSublevels),
                restoreResult.deferredRequests(),
                !restoreRequests.isEmpty()
        );
    }

    private void transformSyntheticSnapshot(
            CompoundTag plotTag,
            SubLevelSchematicSerializationContext placeContext,
            Map<UUID, LoadedSubLevel> loadedSublevels
    ) throws IOException {
        if (!plotTag.contains(DriveByWireSnapshotNbt.SYNTHETIC_SNAPSHOT, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag source = plotTag.getCompound(DriveByWireSnapshotNbt.SYNTHETIC_SNAPSHOT);
        CompoundTag transformed = transform(source, BlockPos.ZERO, placeContext, loadedSublevels);
        plotTag.put(DriveByWireSnapshotNbt.SYNTHETIC_SNAPSHOT, transformed);
        restoreRequests.add(new DriveByWireRestoreRequest(BlockPos.ZERO, transformed.copy()));
    }

    private void transformBackupBlockSnapshots(
            CompoundTag plotTag,
            SubLevelSchematicSerializationContext placeContext,
            Map<UUID, LoadedSubLevel> loadedSublevels
    ) throws IOException {
        IOException[] failure = new IOException[1];
        BlockEntityCompatibilityPipeline.forEachBlockEntity(plotTag, blockEntityTag -> {
            if (failure[0] != null
                    || !DriveByWireSnapshotNbt.BACKUP_BLOCK_ID.equals(
                            blockEntityTag.getString(DriveByWireSnapshotNbt.BLOCK_ENTITY_ID)
                    )
                    || !blockEntityTag.contains(DriveByWireSnapshotNbt.BLOCK_ENTITY_SNAPSHOT, Tag.TAG_COMPOUND)) {
                return;
            }
            BlockPos backupBlockPos = DriveByWireSnapshotNbt.readBlockEntityPos(blockEntityTag);
            try {
                CompoundTag transformed = transform(
                        blockEntityTag.getCompound(DriveByWireSnapshotNbt.BLOCK_ENTITY_SNAPSHOT),
                        backupBlockPos,
                        placeContext,
                        loadedSublevels
                );
                blockEntityTag.put(DriveByWireSnapshotNbt.BLOCK_ENTITY_SNAPSHOT, transformed);
                restoreRequests.add(new DriveByWireRestoreRequest(backupBlockPos, transformed.copy()));
            } catch (IOException exception) {
                failure[0] = exception;
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }
    }

    private CompoundTag transform(
            CompoundTag source,
            BlockPos backupBlockPos,
            SubLevelSchematicSerializationContext placeContext,
            Map<UUID, LoadedSubLevel> loadedSublevels
    ) throws IOException {
        DriveByWireBlueprintDiagnostics.logSnapshot("placement-before", blueprintName, backupBlockPos, source);
        CompoundTag normalized = source.copy();
        int migratedEndpoints = DriveByWireSnapshotNbt.migrateLegacyOwnerCoordinates(
                normalized,
                (ownerId, savedLocalPosition) -> toCenterRelative(
                        ownerId,
                        savedLocalPosition,
                        loadedSublevels
                )
        );
        if (migratedEndpoints > 0) {
            AeronauticsStructureToolMod.LOGGER.info(
                    "DriveByWire migrated {} legacy AST endpoint coordinate(s) for '{}' at {}",
                    migratedEndpoints,
                    blueprintName,
                    backupBlockPos
            );
        }
        CompoundTag transformed = DriveByWireApiBridge.transformForPlacement(
                normalized,
                backupBlockPos,
                placeContext
        );
        int alignedEndpoints = DriveByWirePlacementCoordinateMapper.alignOwnerEndpoints(
                transformed,
                loadedSublevels
        );
        if (alignedEndpoints > 0) {
            AeronauticsStructureToolMod.LOGGER.info(
                    "DriveByWire aligned {} owner endpoint(s) to the loaded plot content for '{}'",
                    alignedEndpoints,
                    blueprintName
            );
        }
        DriveByWireSnapshotNbt.validate(
                transformed,
                "DriveByWire snapshot for '" + blueprintName + "' at " + backupBlockPos,
                true
        );
        DriveByWireBlueprintDiagnostics.logSnapshot("placement-after", blueprintName, backupBlockPos, transformed);
        return transformed;
    }

    private static BlockPos toCenterRelative(
            UUID ownerId,
            BlockPos savedLocalPosition,
            Map<UUID, LoadedSubLevel> loadedSublevels
    ) throws IOException {
        LoadedSubLevel loaded = loadedSublevels.get(ownerId);
        if (loaded == null) {
            throw new IOException(
                    "DriveByWire connection references missing blueprint sublevel " + ownerId
            );
        }
        BlockPos placedPosition = LoadedSubLevelCoordinates.toGlobalBlockPos(loaded, savedLocalPosition);
        return placedPosition.subtract(loaded.subLevel().getPlot().getCenterBlock());
    }

    private List<DriveByWireWorldSource> collectWorldSources(
            Map<UUID, LoadedSubLevel> loadedSublevels
    ) throws IOException {
        List<DriveByWireWorldSource> sources = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (DriveByWireRestoreRequest request : restoreRequests) {
            ListTag connections = request.snapshot().getList(
                    DriveByWireSnapshotNbt.CONNECTIONS,
                    Tag.TAG_COMPOUND
            );
            for (int i = 0; i < connections.size(); i++) {
                CompoundTag connection = connections.getCompound(i);
                if (!"world".equals(connection.getString(DriveByWireSnapshotNbt.CHANNEL))
                        || !connection.hasUUID(DriveByWireSnapshotNbt.SOURCE_OWNER)) {
                    continue;
                }
                UUID ownerId = connection.getUUID(DriveByWireSnapshotNbt.SOURCE_OWNER);
                if (!containsRuntimeSublevel(loadedSublevels, ownerId)) {
                    throw new IOException(
                            "DriveByWire world-source connection references missing sublevel " + ownerId
                    );
                }
                BlockPos localSource = BlockPos.of(connection.getLong(DriveByWireSnapshotNbt.SOURCE));
                if (seen.add(ownerId + "|" + localSource.asLong())) {
                    sources.add(new DriveByWireWorldSource(ownerId, localSource));
                }
            }
        }
        return sources;
    }

    private static boolean containsRuntimeSublevel(
            Map<UUID, LoadedSubLevel> loadedSublevels,
            UUID runtimeSublevelId
    ) {
        for (LoadedSubLevel loaded : loadedSublevels.values()) {
            if (loaded.subLevel().getUniqueId().equals(runtimeSublevelId)) {
                return true;
            }
        }
        return false;
    }
}
