package com.enxv.aeronauticsstructuretool.vehicle.storage;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.compat.sable.SableHoldingStorageAccess;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import dev.ryanhcode.sable.util.SableNBTUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.joml.Vector3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class StoredVehicleRepository {
    private static final DateTimeFormatter RECOVERY_BACKUP_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final String RECOVERY_BACKUP_FOLDER = "ast_vehicle_recovery_backups";

    private StoredVehicleRepository() {
    }

    public static List<StoredVehicleSnapshot> snapshots(ServerSubLevelContainer container) throws IOException {
        SubLevelStorage storage = container.getHoldingChunkMap().getStorage();
        StoredVehicleIndex.Snapshot index = StoredVehicleIndex.snapshot(storage);
        Map<UUID, HoldingSubLevel> inMemory = inMemorySubLevels(container);
        List<StoredVehicleSnapshot> snapshots = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        appendInMemorySnapshots(storage, index, inMemory, snapshots, seen);
        for (StoredVehicleIndex.Entry entry : index.entries().values()) {
            appendSnapshot(
                    entry.data(),
                    entry.pointer(),
                    hasBrokenStorageChain(storage, index, inMemory, entry.data(), entry.pointer()),
                    snapshots,
                    seen
            );
        }
        return List.copyOf(snapshots);
    }

    public static StoredVehicleSnapshot findSnapshot(
            ServerSubLevelContainer container,
            UUID subLevelId
    ) throws IOException {
        if (subLevelId == null) {
            return null;
        }
        for (StoredVehicleSnapshot snapshot : snapshots(container)) {
            if (subLevelId.equals(snapshot.id())) {
                return snapshot;
            }
        }
        return null;
    }

    public static SubLevelData findData(ServerSubLevelContainer container, UUID subLevelId) throws IOException {
        if (subLevelId == null) {
            return null;
        }
        HoldingSubLevel holdingSubLevel = container.getHoldingChunkMap().getHoldingSubLevel(subLevelId);
        if (holdingSubLevel != null) {
            return holdingSubLevel.data();
        }

        StoredVehicleIndex.Entry entry = StoredVehicleIndex.entries(
                container.getHoldingChunkMap().getStorage()
        ).get(subLevelId);
        return entry == null ? null : entry.data();
    }

    public static void move(
            ServerSubLevelContainer container,
            UUID subLevelId,
            Vector3d targetPosition
    ) throws Exception {
        List<StoredSubLevelRecord> records = collectChain(container, subLevelId);
        StoredSubLevelRecord root = records.stream()
                .filter(record -> subLevelId.equals(record.data().uuid()))
                .findFirst()
                .orElse(null);
        if (root == null || root.data() == null) {
            throw new IllegalArgumentException("target is not available");
        }

        Pose3d pose = root.data().pose();
        Vector3d delta = targetPosition.sub(pose.position(), new Vector3d());
        ChunkPos targetChunk = new ChunkPos(BlockPos.containing(targetPosition.x, targetPosition.y, targetPosition.z));
        SubLevelStorage storage = container.getHoldingChunkMap().getStorage();
        List<StoredSubLevelMove> moves = new ArrayList<>();
        for (StoredSubLevelRecord record : records) {
            SubLevelData data = record.data();
            moveData(data, delta);
            data.setOriginLoadedChunk(targetChunk);
            GlobalSavedSubLevelPointer oldPointer = record.pointer();
            GlobalSavedSubLevelPointer newPointer;
            if (oldPointer != null && targetChunk.equals(oldPointer.chunkPos())) {
                storage.attemptSaveSubLevel(oldPointer, data);
                newPointer = oldPointer;
            } else {
                newPointer = storage.attemptSaveSubLevel(targetChunk, data);
                if (newPointer == null) {
                    throw new IOException("failed to save moved structure");
                }
            }
            moves.add(new StoredSubLevelMove(data, oldPointer, newPointer));
        }

        updateMovedSubLevels(container, storage, targetChunk, moves);
        storage.flush();
        container.getHoldingChunkMap().processChanges();
        StoredVehicleIndex.invalidate(storage);
    }

    public static RecoveryResult recover(
            ServerSubLevelContainer container,
            UUID subLevelId
    ) throws Exception {
        SubLevelStorage storage = container.getHoldingChunkMap().getStorage();
        StoredVehicleIndex.Snapshot index = StoredVehicleIndex.snapshot(storage);
        Map<UUID, HoldingSubLevel> inMemory = inMemorySubLevels(container);
        StoredVehicleSnapshot snapshot = findSnapshot(container, subLevelId);
        if (snapshot == null) {
            throw new IOException("vehicle is no longer present in Sable storage");
        }
        if (!snapshot.broken()) {
            throw new IOException("vehicle storage is already healthy");
        }

        List<StoredSubLevelRecord> records = collectRecoverableChain(subLevelId, index, inMemory);
        StoredSubLevelRecord root = records.stream()
                .filter(record -> subLevelId.equals(record.data().uuid()))
                .findFirst()
                .orElseThrow(() -> new IOException("cached root vehicle data is missing"));
        ChunkPos targetChunk = chunkAt(root.data().pose().position());
        Set<ChunkPos> affectedChunks = new LinkedHashSet<>();
        affectedChunks.add(targetChunk);
        for (StoredSubLevelRecord record : records) {
            if (record.pointer() != null) {
                affectedChunks.add(record.pointer().chunkPos());
            }
        }
        storage.flush();
        Path backup = createStorageBackup(storage, affectedChunks, subLevelId, "recover");
        List<RecoveryWrite> writes = new ArrayList<>();
        RecoveryTransaction transaction = null;
        boolean committed = false;
        try {
            for (StoredSubLevelRecord record : records) {
                SubLevelData data = record.data();
                ChunkPos oldOriginLoadedChunk = data.getOriginLoadedChunk();
                data.setOriginLoadedChunk(targetChunk);
                GlobalSavedSubLevelPointer newPointer = storage.attemptSaveSubLevel(targetChunk, data);
                if (newPointer == null) {
                    data.setOriginLoadedChunk(oldOriginLoadedChunk);
                    throw new IOException("failed to allocate replacement storage for " + data.uuid());
                }
                writes.add(new RecoveryWrite(
                        data,
                        record.pointer(),
                        newPointer,
                        oldOriginLoadedChunk,
                        canReplaceOldPointer(storage, index, data.uuid(), record.pointer())
                ));
                SubLevelData verification = storage.attemptLoadSubLevel(
                        newPointer.chunkPos(),
                        newPointer.local()
                );
                if (verification == null || !data.uuid().equals(verification.uuid())) {
                    throw new IOException("replacement storage verification failed for " + data.uuid());
                }
            }

            transaction = prepareRecoveryTransaction(
                    container,
                    storage,
                    targetChunk,
                    writes,
                    inMemory
            );
            applyRecoveryTransaction(container, storage, transaction);
            storage.flush();
            verifyRecoveryWrites(storage, transaction);
            committed = true;

            try {
                for (RecoveryWrite write : writes) {
                    if (shouldRetireOldPointer(write, writes)
                            && !write.oldPointer().equals(write.newPointer())) {
                        storage.attemptSaveSubLevel(write.oldPointer(), null);
                    }
                }
                storage.flush();
            } catch (Exception cleanupFailure) {
                AeronauticsStructureToolMod.LOGGER.warn(
                        "Recovered ghost vehicle {}, but old Sable storage slots could not be fully cleaned",
                        subLevelId,
                        cleanupFailure
                );
            }
            StoredVehicleIndex.invalidate(storage);
            try {
                container.getHoldingChunkMap().processChanges();
            } catch (RuntimeException loadFailure) {
                AeronauticsStructureToolMod.LOGGER.warn(
                        "Recovered ghost vehicle {}, but Sable did not immediately process the repaired holding chunk",
                        subLevelId,
                        loadFailure
                );
            }
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Rebuilt Sable storage for ghost vehicle {} and {} sublevel(s); backup is at {}",
                    subLevelId,
                    records.size(),
                    backup
            );
            return new RecoveryResult(records.size(), backup);
        } catch (Exception failure) {
            if (!committed) {
                rollbackRecovery(container, storage, transaction, writes);
            }
            StoredVehicleIndex.invalidate(storage);
            AeronauticsStructureToolMod.LOGGER.error(
                    "Failed to rebuild Sable storage for ghost vehicle {}; backup is at {}",
                    subLevelId,
                    backup,
                    failure
            );
            throw failure;
        }
    }

    public static GhostCreationResult createGhost(
            ServerSubLevelContainer container,
            UUID subLevelId
    ) throws Exception {
        SubLevelStorage storage = container.getHoldingChunkMap().getStorage();
        StoredVehicleSnapshot snapshot = findSnapshot(container, subLevelId);
        if (snapshot == null) {
            throw new IOException("vehicle is no longer present in Sable storage");
        }
        if (snapshot.broken()) {
            throw new IOException("vehicle is already a ghost vehicle");
        }

        StoredSubLevelRecord record = findRecord(container, subLevelId);
        if (record == null || !isUsableData(record.data()) || record.pointer() == null) {
            throw new IOException("vehicle has no recoverable cached storage record");
        }
        SubLevelData stored = storage.attemptLoadSubLevel(
                record.pointer().chunkPos(),
                record.pointer().local()
        );
        if (stored == null || !subLevelId.equals(stored.uuid())) {
            throw new IOException("vehicle storage pointer is not healthy enough for the test");
        }

        storage.flush();
        Path backup = createStorageBackup(
                storage,
                Set.of(record.pointer().chunkPos()),
                subLevelId,
                "create_ghost"
        );
        SubLevelHoldingChunk holdingChunk = SableHoldingStorageAccess.getOrLoadHoldingChunk(
                container,
                record.pointer().chunkPos(),
                true
        );
        if (holdingChunk == null) {
            throw new IOException("failed to load the Sable holding chunk");
        }
        HoldingSubLevel holding = new HoldingSubLevel(record.data(), record.pointer());
        if (!holdingChunk.getSubLevelPointers().contains(record.pointer().local())) {
            holdingChunk.getSubLevelPointers().add(record.pointer().local());
        }
        holdingChunk.acceptHoldingSubLevel(holding);
        SableHoldingStorageAccess.registerHoldingSubLevel(container, holding);
        storage.attemptSaveHoldingChunk(record.pointer().chunkPos(), holdingChunk);
        SableHoldingStorageAccess.markHoldingChunkDirty(container, record.pointer().chunkPos());
        storage.flush();

        try {
            storage.attemptSaveSubLevel(record.pointer(), null);
            storage.flush();
            if (storage.attemptLoadSubLevel(record.pointer().chunkPos(), record.pointer().local()) != null) {
                throw new IOException("Sable storage slot could not be cleared");
            }
        } catch (Exception failure) {
            storage.attemptSaveSubLevel(record.pointer(), record.data());
            storage.flush();
            throw failure;
        }

        StoredVehicleIndex.invalidate(storage);
        AeronauticsStructureToolMod.LOGGER.warn(
                "Intentionally created Sable ghost vehicle {} at pointer {}; backup is at {}",
                subLevelId,
                record.pointer(),
                backup
        );
        return new GhostCreationResult(backup);
    }

    public static GhostCreationResult createGhostFromLoaded(
            ServerSubLevelContainer container,
            ServerSubLevel root
    ) throws Exception {
        if (root == null || root.isRemoved() || root.getUniqueId() == null) {
            throw new IOException("target physical structure is no longer available");
        }

        Map<UUID, ServerSubLevel> uniqueChain = new LinkedHashMap<>();
        for (ServerSubLevel subLevel : SubLevelHelper.getLoadingDependencyChain(root)) {
            if (subLevel != null && subLevel.getUniqueId() != null) {
                uniqueChain.put(subLevel.getUniqueId(), subLevel);
            }
        }
        uniqueChain.putIfAbsent(root.getUniqueId(), root);

        UUID rootId = root.getUniqueId();
        Set<UUID> chainIds = new LinkedHashSet<>(uniqueChain.keySet());
        ChunkPos holdingChunkPos = chunkAt(root.logicalPose().position());
        var holdingMap = container.getHoldingChunkMap();
        SubLevelStorage storage = holdingMap.getStorage();

        // Persist the exact live state before deliberately breaking its storage pointer.
        holdingMap.saveAll();
        StoredVehicleIndex.invalidate(storage);
        for (ServerSubLevel subLevel : uniqueChain.values()) {
            GlobalSavedSubLevelPointer pointer = subLevel.getLastSerializationPointer();
            if (pointer == null) {
                throw new IOException("Sable did not assign a storage pointer to " + subLevel.getUniqueId());
            }
            SubLevelData saved = storage.attemptLoadSubLevel(pointer.chunkPos(), pointer.local());
            if (saved == null || !subLevel.getUniqueId().equals(saved.uuid())) {
                throw new IOException("Sable did not save a verifiable copy of " + subLevel.getUniqueId());
            }
        }

        try {
            holdingMap.moveToUnloaded(root, holdingChunkPos);
            GhostCreationResult result = createGhost(container, rootId);
            detachLoadedHoldingChain(container, chainIds, holdingChunkPos);
            return result;
        } catch (Exception failure) {
            boolean anyUnloaded = chainIds.stream().anyMatch(id -> container.getSubLevel(id) == null);
            if (anyUnloaded) {
                try {
                    restoreLoadedGhostCreation(container, chainIds, holdingChunkPos);
                } catch (Exception rollbackFailure) {
                    AeronauticsStructureToolMod.LOGGER.error(
                            "Failed to restore loaded vehicle {} after ghost creation failed",
                            rootId,
                            rollbackFailure
                    );
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        }
    }

    private static void detachLoadedHoldingChain(
            ServerSubLevelContainer container,
            Set<UUID> chainIds,
            ChunkPos holdingChunkPos
    ) throws Exception {
        Set<ChunkPos> chunks = holdingChunkPositions(container, chainIds, holdingChunkPos);
        for (ChunkPos chunkPos : chunks) {
            SubLevelHoldingChunk holdingChunk = SableHoldingStorageAccess.getOrLoadHoldingChunk(
                    container,
                    chunkPos,
                    false
            );
            if (holdingChunk == null) {
                continue;
            }
            for (UUID id : chainIds) {
                SableHoldingStorageAccess.removeLoadedHoldingSubLevel(holdingChunk, id);
            }
        }
    }

    private static Set<ChunkPos> holdingChunkPositions(
            ServerSubLevelContainer container,
            Set<UUID> chainIds,
            ChunkPos holdingChunkPos
    ) throws IOException {
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        chunks.add(holdingChunkPos);
        for (UUID id : chainIds) {
            HoldingSubLevel holding = container.getHoldingChunkMap().getHoldingSubLevel(id);
            if (holding == null || holding.data() == null || holding.pointer() == null) {
                throw new IOException("Sable did not retain the unloaded data for " + id);
            }
            chunks.add(holding.pointer().chunkPos());
        }
        return chunks;
    }

    private static void restoreLoadedGhostCreation(
            ServerSubLevelContainer container,
            Set<UUID> chainIds,
            ChunkPos holdingChunkPos
    ) throws Exception {
        var holdingMap = container.getHoldingChunkMap();
        SubLevelStorage storage = holdingMap.getStorage();
        Map<UUID, HoldingSubLevel> holdings = new LinkedHashMap<>();
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        chunks.add(holdingChunkPos);

        for (UUID id : chainIds) {
            if (container.getSubLevel(id) instanceof ServerSubLevel) {
                continue;
            }
            HoldingSubLevel holding = holdingMap.getHoldingSubLevel(id);
            if (holding == null || holding.data() == null || holding.pointer() == null) {
                throw new IOException("cannot restore missing cached data for " + id);
            }
            holdings.put(id, holding);
            chunks.add(holding.pointer().chunkPos());
        }
        if (holdings.isEmpty()) {
            return;
        }

        for (ChunkPos chunkPos : chunks) {
            SubLevelHoldingChunk holdingChunk = SableHoldingStorageAccess.getOrLoadHoldingChunk(
                    container,
                    chunkPos,
                    chunkPos.equals(holdingChunkPos)
            );
            if (holdingChunk == null) {
                continue;
            }
            for (UUID id : chainIds) {
                SableHoldingStorageAccess.removeLoadedHoldingSubLevel(holdingChunk, id);
            }
        }

        SubLevelHoldingChunk reloadChunk = SableHoldingStorageAccess.getOrLoadHoldingChunk(
                container,
                holdingChunkPos,
                true
        );
        if (reloadChunk == null) {
            throw new IOException("failed to prepare Sable rollback holding chunk");
        }
        for (HoldingSubLevel holding : holdings.values()) {
            GlobalSavedSubLevelPointer pointer = holding.pointer();
            storage.attemptSaveSubLevel(pointer, holding.data());
            SubLevelHoldingChunk pointerChunk = SableHoldingStorageAccess.getOrLoadHoldingChunk(
                    container,
                    pointer.chunkPos(),
                    true
            );
            if (pointerChunk == null) {
                throw new IOException("failed to restore Sable pointer chunk for " + holding.data().uuid());
            }
            if (!pointerChunk.getSubLevelPointers().contains(pointer.local())) {
                pointerChunk.getSubLevelPointers().add(pointer.local());
            }
            reloadChunk.acceptHoldingSubLevel(holding);
            SableHoldingStorageAccess.registerHoldingSubLevel(container, holding);
        }
        for (ChunkPos chunkPos : chunks) {
            SubLevelHoldingChunk holdingChunk = SableHoldingStorageAccess.getOrLoadHoldingChunk(
                    container,
                    chunkPos,
                    true
            );
            storage.attemptSaveHoldingChunk(chunkPos, holdingChunk);
            SableHoldingStorageAccess.markHoldingChunkDirty(container, chunkPos);
        }
        storage.flush();
        StoredVehicleIndex.invalidate(storage);
        holdingMap.processChanges();
    }

    private static List<StoredSubLevelRecord> collectRecoverableChain(
            UUID rootSubLevelId,
            StoredVehicleIndex.Snapshot index,
            Map<UUID, HoldingSubLevel> inMemory
    ) throws IOException {
        Map<UUID, StoredSubLevelRecord> records = new LinkedHashMap<>();
        List<UUID> queue = new ArrayList<>();
        queue.add(rootSubLevelId);
        for (int i = 0; i < queue.size(); i++) {
            UUID subLevelId = queue.get(i);
            if (records.containsKey(subLevelId)) {
                continue;
            }
            StoredSubLevelRecord record = resolveRecoverableRecord(subLevelId, index, inMemory);
            if (record == null) {
                throw new IOException("cached dependency data is missing for " + subLevelId);
            }
            if (!isUsableData(record.data())) {
                throw new IOException("cached pose, bounds, or NBT is invalid for " + subLevelId);
            }
            records.put(subLevelId, record);
            List<UUID> dependencies = record.data().dependencies();
            if (dependencies == null) {
                throw new IOException("cached dependency list is missing for " + subLevelId);
            }
            for (UUID dependency : dependencies) {
                if (dependency == null) {
                    throw new IOException("cached dependency UUID is missing for " + subLevelId);
                }
                if (!records.containsKey(dependency) && !queue.contains(dependency)) {
                    queue.add(dependency);
                }
            }
        }
        return new ArrayList<>(records.values());
    }

    private static StoredSubLevelRecord resolveRecoverableRecord(
            UUID subLevelId,
            StoredVehicleIndex.Snapshot index,
            Map<UUID, HoldingSubLevel> inMemory
    ) {
        HoldingSubLevel holding = inMemory.get(subLevelId);
        if (holding != null && holding.data() != null) {
            return new StoredSubLevelRecord(holding.data(), holding.pointer());
        }
        StoredVehicleIndex.Entry indexed = index.entries().get(subLevelId);
        return indexed == null ? null : new StoredSubLevelRecord(indexed.data(), indexed.pointer());
    }

    private static boolean canReplaceOldPointer(
            SubLevelStorage storage,
            StoredVehicleIndex.Snapshot index,
            UUID expectedId,
            GlobalSavedSubLevelPointer pointer
    ) {
        if (pointer == null) {
            return false;
        }
        for (Map.Entry<UUID, StoredVehicleIndex.Entry> entry : index.entries().entrySet()) {
            if (pointer.equals(entry.getValue().pointer()) && !expectedId.equals(entry.getKey())) {
                return false;
            }
        }
        SubLevelData stored = storage.attemptLoadSubLevel(pointer.chunkPos(), pointer.local());
        return stored == null || expectedId.equals(stored.uuid());
    }

    private static boolean shouldRetireOldPointer(
            RecoveryWrite write,
            List<RecoveryWrite> writes
    ) {
        if (!write.replaceOldPointer() || write.oldPointer() == null) {
            return false;
        }
        for (RecoveryWrite other : writes) {
            if (other != write && write.oldPointer().equals(other.newPointer())) {
                return false;
            }
        }
        return true;
    }

    private static ChunkPos chunkAt(Vector3d position) {
        return new ChunkPos(BlockPos.containing(position.x, position.y, position.z));
    }

    private static Path createStorageBackup(
            SubLevelStorage storage,
            Collection<ChunkPos> chunks,
            UUID subLevelId,
            String operation
    ) throws IOException {
        Path folder = storage.getFolder().toAbsolutePath().normalize();
        Path backup = folder.resolve(RECOVERY_BACKUP_FOLDER).resolve(
                RECOVERY_BACKUP_TIME.format(LocalDateTime.now())
                        + "_" + operation + "_" + subLevelId
        );
        Files.createDirectories(backup);
        Set<String> regionPrefixes = new LinkedHashSet<>();
        for (ChunkPos chunk : chunks) {
            regionPrefixes.add("r." + (chunk.x >> 5) + "." + (chunk.z >> 5));
        }
        try (var paths = Files.list(folder)) {
            for (Path source : paths.filter(Files::isRegularFile).toList()) {
                String fileName = source.getFileName().toString();
                boolean relevant = regionPrefixes.stream().anyMatch(prefix ->
                        fileName.equals(prefix + ".slvlr")
                                || (fileName.startsWith(prefix + ".") && fileName.endsWith(".slvls"))
                );
                if (relevant) {
                    Files.copy(
                            source,
                            backup.resolve(fileName),
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES
                    );
                }
            }
        }
        return backup;
    }

    private static RecoveryTransaction prepareRecoveryTransaction(
            ServerSubLevelContainer container,
            SubLevelStorage storage,
            ChunkPos targetChunk,
            List<RecoveryWrite> writes,
            Map<UUID, HoldingSubLevel> inMemory
    ) throws Exception {
        Set<ChunkPos> chunkPositions = new LinkedHashSet<>();
        chunkPositions.add(targetChunk);
        for (RecoveryWrite write : writes) {
            if (write.oldPointer() != null) {
                chunkPositions.add(write.oldPointer().chunkPos());
            }
        }

        Map<ChunkPos, HoldingChunkSnapshot> chunks = new LinkedHashMap<>();
        for (ChunkPos chunkPos : chunkPositions) {
            SubLevelHoldingChunk holdingChunk = SableHoldingStorageAccess.getOrLoadHoldingChunk(
                    container,
                    chunkPos,
                    chunkPos.equals(targetChunk)
            );
            if (holdingChunk == null) {
                holdingChunk = storage.attemptLoadHoldingChunk(chunkPos);
            }
            if (holdingChunk != null) {
                chunks.put(chunkPos, new HoldingChunkSnapshot(
                        holdingChunk,
                        List.copyOf(holdingChunk.getSubLevelPointers())
                ));
            }
        }
        if (!chunks.containsKey(targetChunk)) {
            throw new IOException("failed to prepare the replacement Sable holding chunk");
        }

        Map<UUID, HoldingSubLevel> originalHolding = new LinkedHashMap<>();
        for (RecoveryWrite write : writes) {
            HoldingSubLevel original = container.getHoldingChunkMap().getHoldingSubLevel(write.data().uuid());
            if (original == null) {
                original = inMemory.get(write.data().uuid());
            }
            if (original != null) {
                originalHolding.put(write.data().uuid(), original);
            }
        }
        return new RecoveryTransaction(targetChunk, writes, chunks, originalHolding);
    }

    private static void applyRecoveryTransaction(
            ServerSubLevelContainer container,
            SubLevelStorage storage,
            RecoveryTransaction transaction
    ) throws Exception {
        SubLevelHoldingChunk target = transaction.chunks().get(transaction.targetChunk()).chunk();
        for (RecoveryWrite write : transaction.writes()) {
            if (shouldRetireOldPointer(write, transaction.writes())) {
                HoldingChunkSnapshot oldChunk = transaction.chunks().get(write.oldPointer().chunkPos());
                if (oldChunk != null) {
                    oldChunk.chunk().getSubLevelPointers().remove(write.oldPointer().local());
                    SableHoldingStorageAccess.removeLoadedHoldingSubLevel(
                            oldChunk.chunk(),
                            write.data().uuid()
                    );
                }
            }
            if (!target.getSubLevelPointers().contains(write.newPointer().local())) {
                target.getSubLevelPointers().add(write.newPointer().local());
            }
            HoldingSubLevel replacement = new HoldingSubLevel(write.data(), write.newPointer());
            target.acceptHoldingSubLevel(replacement);
            SableHoldingStorageAccess.registerHoldingSubLevel(container, replacement);
        }
        saveRecoveryHoldingChunks(container, storage, transaction.chunks());
    }

    private static void verifyRecoveryWrites(
            SubLevelStorage storage,
            RecoveryTransaction transaction
    ) throws IOException {
        SubLevelHoldingChunk target = transaction.chunks().get(transaction.targetChunk()).chunk();
        for (RecoveryWrite write : transaction.writes()) {
            if (!target.getSubLevelPointers().contains(write.newPointer().local())) {
                throw new IOException("replacement pointer was not saved for " + write.data().uuid());
            }
            SubLevelData data = storage.attemptLoadSubLevel(
                    write.newPointer().chunkPos(),
                    write.newPointer().local()
            );
            if (data == null || !write.data().uuid().equals(data.uuid())) {
                throw new IOException("replacement data became unreadable for " + write.data().uuid());
            }
        }
    }

    private static void rollbackRecovery(
            ServerSubLevelContainer container,
            SubLevelStorage storage,
            RecoveryTransaction transaction,
            List<RecoveryWrite> writes
    ) {
        try {
            if (transaction != null) {
                for (HoldingChunkSnapshot snapshot : transaction.chunks().values()) {
                    snapshot.chunk().getSubLevelPointers().clear();
                    snapshot.chunk().getSubLevelPointers().addAll(snapshot.originalPointers());
                    for (RecoveryWrite write : transaction.writes()) {
                        SableHoldingStorageAccess.removeLoadedHoldingSubLevel(
                                snapshot.chunk(),
                                write.data().uuid()
                        );
                    }
                }
                for (RecoveryWrite write : transaction.writes()) {
                    UUID id = write.data().uuid();
                    SableHoldingStorageAccess.unregisterHoldingSubLevel(container, id);
                    HoldingSubLevel original = transaction.originalHolding().get(id);
                    if (original != null) {
                        if (original.pointer() != null) {
                            HoldingChunkSnapshot originalChunk = transaction.chunks().get(
                                    original.pointer().chunkPos()
                            );
                            if (originalChunk != null) {
                                originalChunk.chunk().acceptHoldingSubLevel(original);
                            }
                        }
                        SableHoldingStorageAccess.registerHoldingSubLevel(container, original);
                    }
                }
                saveRecoveryHoldingChunks(container, storage, transaction.chunks());
            }
            for (RecoveryWrite write : writes) {
                write.data().setOriginLoadedChunk(write.oldOriginLoadedChunk());
                storage.attemptSaveSubLevel(write.newPointer(), null);
            }
            storage.flush();
        } catch (Exception rollbackFailure) {
            AeronauticsStructureToolMod.LOGGER.error(
                    "Failed to roll back a Sable ghost-vehicle recovery transaction",
                    rollbackFailure
            );
        }
    }

    private static void saveRecoveryHoldingChunks(
            ServerSubLevelContainer container,
            SubLevelStorage storage,
            Map<ChunkPos, HoldingChunkSnapshot> chunks
    ) throws Exception {
        for (Map.Entry<ChunkPos, HoldingChunkSnapshot> entry : chunks.entrySet()) {
            storage.attemptSaveHoldingChunk(entry.getKey(), entry.getValue().chunk());
            SableHoldingStorageAccess.markHoldingChunkDirty(container, entry.getKey());
        }
    }

    public static String resolveName(SubLevelData data) {
        String name = data.fullTag() == null ? "" : data.fullTag().getString("display_name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        return data.uuid() == null ? "unknown vehicle" : data.uuid().toString();
    }

    public static void clearCache() {
        StoredVehicleIndex.clear();
    }

    private static void appendInMemorySnapshots(
            SubLevelStorage storage,
            StoredVehicleIndex.Snapshot index,
            Map<UUID, HoldingSubLevel> inMemory,
            List<StoredVehicleSnapshot> snapshots,
            Set<UUID> seen
    ) {
        for (HoldingSubLevel holdingSubLevel : inMemory.values()) {
            appendSnapshot(
                    holdingSubLevel.data(),
                    holdingSubLevel.pointer(),
                    hasBrokenStorageChain(
                            storage,
                            index,
                            inMemory,
                            holdingSubLevel.data(),
                            holdingSubLevel.pointer()
                    ),
                    snapshots,
                    seen
            );
        }
    }

    private static void appendSnapshot(
            SubLevelData data,
            GlobalSavedSubLevelPointer pointer,
            boolean broken,
            List<StoredVehicleSnapshot> snapshots,
            Set<UUID> seen
    ) {
        if (data == null || data.uuid() == null || !seen.add(data.uuid())) {
            return;
        }
        Vector3d position = safePosition(data, pointer);
        String fullName = resolveName(data);
        String displayName = fullName.equals(data.uuid().toString()) ? fullName.substring(0, 14) : fullName;
        snapshots.add(new StoredVehicleSnapshot(data.uuid(), displayName, fullName, position, broken));
    }

    private static Map<UUID, HoldingSubLevel> inMemorySubLevels(
            ServerSubLevelContainer container
    ) throws IOException {
        Map<UUID, HoldingSubLevel> inMemory = new LinkedHashMap<>();
        try {
            for (HoldingSubLevel holdingSubLevel : SableHoldingStorageAccess.holdingSubLevels(container)) {
                if (holdingSubLevel != null
                        && holdingSubLevel.data() != null
                        && holdingSubLevel.data().uuid() != null) {
                    inMemory.put(holdingSubLevel.data().uuid(), holdingSubLevel);
                }
            }
            return inMemory;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            throw new IOException("failed to enumerate in-memory Sable holding sublevels", exception);
        }
    }

    private static boolean hasBrokenStorageChain(
            SubLevelStorage storage,
            StoredVehicleIndex.Snapshot index,
            Map<UUID, HoldingSubLevel> inMemory,
            SubLevelData rootData,
            GlobalSavedSubLevelPointer rootPointer
    ) {
        if (rootPointer == null && rootData != null && rootData.uuid() != null) {
            StoredVehicleIndex.Entry indexedRoot = index.entries().get(rootData.uuid());
            if (indexedRoot != null) {
                rootData = indexedRoot.data();
                rootPointer = indexedRoot.pointer();
            }
        }
        List<StoredSubLevelRecord> queue = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        queue.add(new StoredSubLevelRecord(rootData, rootPointer));
        for (int i = 0; i < queue.size(); i++) {
            StoredSubLevelRecord record = queue.get(i);
            SubLevelData data = record.data();
            if (!isUsableData(data)) {
                return true;
            }
            if (!visited.add(data.uuid())) {
                continue;
            }
            boolean heldInMemory = inMemory.containsKey(data.uuid());
            if (record.pointer() == null) {
                // Sable publishes holding data before its first persistence pass assigns a storage pointer.
                if (!heldInMemory) {
                    return true;
                }
            } else if (!pointerResolves(storage, data.uuid(), record.pointer())) {
                return true;
            }
            List<UUID> dependencies = data.dependencies();
            if (dependencies == null) {
                return true;
            }
            for (UUID dependency : dependencies) {
                if (dependency == null || visited.contains(dependency)) {
                    if (dependency == null) {
                        return true;
                    }
                    continue;
                }
                StoredSubLevelRecord dependencyRecord = resolveRecord(dependency, inMemory, index);
                if (dependencyRecord == null) {
                    return true;
                }
                queue.add(dependencyRecord);
            }
        }
        return false;
    }

    private static StoredSubLevelRecord resolveRecord(
            UUID subLevelId,
            Map<UUID, HoldingSubLevel> inMemory,
            StoredVehicleIndex.Snapshot index
    ) {
        HoldingSubLevel holdingSubLevel = inMemory.get(subLevelId);
        if (holdingSubLevel != null) {
            return new StoredSubLevelRecord(holdingSubLevel.data(), holdingSubLevel.pointer());
        }
        StoredVehicleIndex.Entry entry = index.entries().get(subLevelId);
        return entry == null ? null : new StoredSubLevelRecord(entry.data(), entry.pointer());
    }

    private static boolean pointerResolves(
            SubLevelStorage storage,
            UUID expectedId,
            GlobalSavedSubLevelPointer pointer
    ) {
        if (pointer == null) {
            return false;
        }
        SubLevelData stored = storage.attemptLoadSubLevel(pointer.chunkPos(), pointer.local());
        return stored != null && expectedId.equals(stored.uuid());
    }

    private static boolean isUsableData(SubLevelData data) {
        if (data == null
                || data.uuid() == null
                || data.pose() == null
                || data.bounds() == null
                || data.fullTag() == null) {
            return false;
        }
        try {
            Vector3d position = data.pose().position();
            var orientation = data.pose().orientation();
            return position != null
                    && Double.isFinite(position.x)
                    && Double.isFinite(position.y)
                    && Double.isFinite(position.z)
                    && orientation != null
                    && Double.isFinite(orientation.x)
                    && Double.isFinite(orientation.y)
                    && Double.isFinite(orientation.z)
                    && Double.isFinite(orientation.w)
                    && Double.isFinite(data.bounds().minX())
                    && Double.isFinite(data.bounds().minY())
                    && Double.isFinite(data.bounds().minZ())
                    && Double.isFinite(data.bounds().maxX())
                    && Double.isFinite(data.bounds().maxY())
                    && Double.isFinite(data.bounds().maxZ())
                    && data.bounds().minX() <= data.bounds().maxX()
                    && data.bounds().minY() <= data.bounds().maxY()
                    && data.bounds().minZ() <= data.bounds().maxZ();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static Vector3d safePosition(SubLevelData data, GlobalSavedSubLevelPointer pointer) {
        if (isUsableData(data)) {
            return new Vector3d(data.pose().position());
        }
        if (pointer != null) {
            return new Vector3d(
                    pointer.chunkPos().getMiddleBlockX(),
                    0.0D,
                    pointer.chunkPos().getMiddleBlockZ()
            );
        }
        return new Vector3d();
    }

    private static List<StoredSubLevelRecord> collectChain(
            ServerSubLevelContainer container,
            UUID rootSubLevelId
    ) throws IOException {
        Map<UUID, StoredSubLevelRecord> records = new LinkedHashMap<>();
        List<UUID> queue = new ArrayList<>();
        queue.add(rootSubLevelId);
        for (int i = 0; i < queue.size(); i++) {
            UUID subLevelId = queue.get(i);
            if (records.containsKey(subLevelId)) {
                continue;
            }
            StoredSubLevelRecord record = findRecord(container, subLevelId);
            if (record == null || record.data() == null) {
                throw new IllegalArgumentException("stored dependency is not available");
            }
            records.put(subLevelId, record);
            for (UUID dependency : record.data().dependencies()) {
                if (dependency != null && !records.containsKey(dependency) && !queue.contains(dependency)) {
                    queue.add(dependency);
                }
            }
        }
        return new ArrayList<>(records.values());
    }

    private static StoredSubLevelRecord findRecord(
            ServerSubLevelContainer container,
            UUID subLevelId
    ) throws IOException {
        if (subLevelId == null) {
            return null;
        }
        HoldingSubLevel holdingSubLevel = container.getHoldingChunkMap().getHoldingSubLevel(subLevelId);
        if (holdingSubLevel != null && holdingSubLevel.pointer() != null) {
            return new StoredSubLevelRecord(holdingSubLevel.data(), holdingSubLevel.pointer());
        }

        StoredVehicleIndex.Entry entry = StoredVehicleIndex.entries(
                container.getHoldingChunkMap().getStorage()
        ).get(subLevelId);
        return entry == null ? null : new StoredSubLevelRecord(entry.data(), entry.pointer());
    }

    private static void moveData(SubLevelData data, Vector3d delta) {
        data.pose().position().add(delta);
        data.bounds().move(delta.x, delta.y, delta.z);
        CompoundTag fullTag = data.fullTag();
        fullTag.put("pose", SableNBTUtils.writePose3d(data.pose()));
        fullTag.put("world_bounds", SableNBTUtils.writeBoundingBox(data.bounds()));
    }

    private static void updateMovedSubLevels(
            ServerSubLevelContainer container,
            SubLevelStorage storage,
            ChunkPos targetChunk,
            List<StoredSubLevelMove> moves
    ) throws ReflectiveOperationException {
        SubLevelHoldingChunk targetHoldingChunk = SableHoldingStorageAccess.getOrLoadHoldingChunk(
                container,
                targetChunk,
                true
        );
        if (targetHoldingChunk == null) {
            throw new IllegalStateException("failed to prepare target holding chunk");
        }

        for (StoredSubLevelMove move : moves) {
            if (move.oldPointer() != null && !move.oldPointer().equals(move.newPointer())) {
                SubLevelHoldingChunk oldHoldingChunk = SableHoldingStorageAccess.getOrLoadHoldingChunk(
                        container,
                        move.oldPointer().chunkPos(),
                        false
                );
                if (oldHoldingChunk == null) {
                    oldHoldingChunk = storage.attemptLoadHoldingChunk(move.oldPointer().chunkPos());
                }
                if (oldHoldingChunk != null) {
                    oldHoldingChunk.getSubLevelPointers().remove(move.oldPointer().local());
                    SableHoldingStorageAccess.removeLoadedHoldingSubLevel(oldHoldingChunk, move.data().uuid());
                    storage.attemptSaveHoldingChunk(move.oldPointer().chunkPos(), oldHoldingChunk);
                    SableHoldingStorageAccess.markHoldingChunkDirty(container, move.oldPointer().chunkPos());
                }
                storage.attemptSaveSubLevel(move.oldPointer(), null);
            }

            if (!targetHoldingChunk.getSubLevelPointers().contains(move.newPointer().local())) {
                targetHoldingChunk.getSubLevelPointers().add(move.newPointer().local());
            }
            HoldingSubLevel moved = new HoldingSubLevel(move.data(), move.newPointer());
            targetHoldingChunk.acceptHoldingSubLevel(moved);
            SableHoldingStorageAccess.registerHoldingSubLevel(container, moved);
        }
        storage.attemptSaveHoldingChunk(targetChunk, targetHoldingChunk);
        SableHoldingStorageAccess.markHoldingChunkDirty(container, targetChunk);
    }

    private record StoredQueryCache(long createdAtMillis, List<StoredVehicleSnapshot> snapshots) {
    }

    private record StoredSubLevelRecord(SubLevelData data, GlobalSavedSubLevelPointer pointer) {
    }

    private record StoredSubLevelMove(
            SubLevelData data,
            GlobalSavedSubLevelPointer oldPointer,
            GlobalSavedSubLevelPointer newPointer
    ) {
    }

    private record RecoveryWrite(
            SubLevelData data,
            GlobalSavedSubLevelPointer oldPointer,
            GlobalSavedSubLevelPointer newPointer,
            ChunkPos oldOriginLoadedChunk,
            boolean replaceOldPointer
    ) {
    }

    private record HoldingChunkSnapshot(
            SubLevelHoldingChunk chunk,
            List<SavedSubLevelPointer> originalPointers
    ) {
    }

    private record RecoveryTransaction(
            ChunkPos targetChunk,
            List<RecoveryWrite> writes,
            Map<ChunkPos, HoldingChunkSnapshot> chunks,
            Map<UUID, HoldingSubLevel> originalHolding
    ) {
    }

    public record RecoveryResult(int subLevelCount, Path backupDirectory) {
    }

    public record GhostCreationResult(Path backupDirectory) {
    }
}
