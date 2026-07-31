package com.enxv.aeronauticsstructuretool.vehicle.storage;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import net.minecraft.world.level.ChunkPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class StoredVehicleIndex {
    private static final Pattern REGION_FILE_PATTERN = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)\\.slvlr$");
    private static final Map<Path, CachedIndex> CACHE = new HashMap<>();

    private StoredVehicleIndex() {
    }

    static Map<UUID, Entry> entries(SubLevelStorage storage) throws IOException {
        return snapshot(storage).entries();
    }

    static Snapshot snapshot(SubLevelStorage storage) throws IOException {
        Path folder = storage.getFolder().toAbsolutePath().normalize();
        StorageFiles storageFiles = discoverStorageFiles(folder);
        CachedIndex cached = CACHE.get(folder);
        if (cached != null && cached.files().equals(storageFiles.fingerprints())) {
            return cached.snapshot();
        }

        Snapshot snapshot = scan(storage, storageFiles.regionFiles());
        CACHE.put(folder, new CachedIndex(storageFiles.fingerprints(), snapshot));
        return snapshot;
    }

    static void invalidate(SubLevelStorage storage) {
        CACHE.remove(storage.getFolder().toAbsolutePath().normalize());
    }

    static void clear() {
        CACHE.clear();
    }

    private static StorageFiles discoverStorageFiles(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return new StorageFiles(List.of(), List.of());
        }
        List<RegionFile> regionFiles = new ArrayList<>();
        List<FileFingerprint> fingerprints = new ArrayList<>();
        try (var paths = Files.list(folder)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String fileName = path.getFileName().toString();
                if (!fileName.endsWith(".slvlr") && !fileName.endsWith(".slvls")) {
                    continue;
                }
                BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
                fingerprints.add(new FileFingerprint(
                        fileName,
                        attributes.size(),
                        attributes.lastModifiedTime().toMillis()
                ));
                Matcher matcher = REGION_FILE_PATTERN.matcher(path.getFileName().toString());
                if (!matcher.matches()) {
                    continue;
                }
                regionFiles.add(new RegionFile(
                        Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2))
                ));
            }
        }
        regionFiles.sort(Comparator.comparingInt(RegionFile::regionX).thenComparingInt(RegionFile::regionZ));
        fingerprints.sort(Comparator.comparing(FileFingerprint::name));
        return new StorageFiles(List.copyOf(regionFiles), List.copyOf(fingerprints));
    }

    private static Snapshot scan(SubLevelStorage storage, List<RegionFile> regionFiles) {
        Map<UUID, Entry> entries = new LinkedHashMap<>();
        for (RegionFile region : regionFiles) {
            int chunkBaseX = region.regionX() << 5;
            int chunkBaseZ = region.regionZ() << 5;
            for (int localZ = 0; localZ < 32; localZ++) {
                for (int localX = 0; localX < 32; localX++) {
                    ChunkPos chunkPos = new ChunkPos(chunkBaseX + localX, chunkBaseZ + localZ);
                    SubLevelHoldingChunk holdingChunk = storage.attemptLoadHoldingChunk(chunkPos);
                    if (holdingChunk == null) {
                        continue;
                    }
                    for (SavedSubLevelPointer pointer : holdingChunk.getSubLevelPointers()) {
                        GlobalSavedSubLevelPointer globalPointer = new GlobalSavedSubLevelPointer(
                                chunkPos,
                                pointer.storageIndex(),
                                pointer.subLevelIndex()
                        );
                        SubLevelData data = storage.attemptLoadSubLevel(chunkPos, pointer);
                        if (data == null) {
                            AeronauticsStructureToolMod.LOGGER.warn(
                                    "Stored sublevel pointer {} in holding chunk {} could not be loaded",
                                    pointer,
                                    chunkPos
                            );
                            continue;
                        }
                        if (data.uuid() == null) {
                            AeronauticsStructureToolMod.LOGGER.warn(
                                    "Stored sublevel pointer {} in holding chunk {} has no UUID",
                                    pointer,
                                    chunkPos
                            );
                            continue;
                        }
                        entries.put(data.uuid(), new Entry(
                                data,
                                globalPointer
                        ));
                    }
                }
            }
        }
        return new Snapshot(Map.copyOf(entries));
    }

    record Entry(SubLevelData data, GlobalSavedSubLevelPointer pointer) {
    }

    record Snapshot(Map<UUID, Entry> entries) {
    }

    private record RegionFile(int regionX, int regionZ) {
    }

    private record FileFingerprint(String name, long size, long modifiedMillis) {
    }

    private record StorageFiles(List<RegionFile> regionFiles, List<FileFingerprint> fingerprints) {
    }

    private record CachedIndex(List<FileFingerprint> files, Snapshot snapshot) {
    }
}
