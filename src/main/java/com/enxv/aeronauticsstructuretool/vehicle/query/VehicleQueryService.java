package com.enxv.aeronauticsstructuretool.vehicle.query;

import com.enxv.aeronauticsstructuretool.ToolgunConstraintTracker;
import com.enxv.aeronauticsstructuretool.blueprint.capture.NativeBlueprintCaptureService;
import com.enxv.aeronauticsstructuretool.toolgun.transform.StructureTransformService;
import com.enxv.aeronauticsstructuretool.vehicle.storage.StoredVehicleRepository;
import com.enxv.aeronauticsstructuretool.vehicle.storage.StoredVehicleSnapshot;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import com.enxv.aeronauticsstructuretool.blueprint.lifecycle.SubLevelRemovalCoordinator;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VehicleQueryService {
    private static final long RESULT_CACHE_TTL_MS = 1000L;
    private static final int INFINITE_QUERY_RANGE = 0;
    private static final int SURVIVAL_DEFAULT_QUERY_RANGE = 64;
    private static final Map<QueryResultCacheKey, QueryResultCache> RESULT_CACHE = new HashMap<>();

    private VehicleQueryService() {
    }

    public static List<VehicleQueryEntry> query(
            ServerLevel level,
            BlockPos playerBlockPos,
            Vector3d playerPosition,
            int rawRange,
            boolean survivalTool
    ) throws IOException {
        ServerSubLevelContainer container = requireContainer(level);
        int range = sanitizeRange(rawRange, survivalTool);
        QueryResultCacheKey cacheKey = new QueryResultCacheKey(
                level.dimension().location().toString(),
                container.getHoldingChunkMap().getStorage().getFolder().toAbsolutePath().normalize().toString(),
                playerBlockPos,
                range
        );
        long now = System.currentTimeMillis();
        RESULT_CACHE.entrySet().removeIf(entry -> now - entry.getValue().createdAtMillis() > RESULT_CACHE_TTL_MS);
        QueryResultCache cached = RESULT_CACHE.get(cacheKey);
        if (cached != null && now - cached.createdAtMillis() <= RESULT_CACHE_TTL_MS) {
            return cached.entries();
        }

        List<VehicleQueryEntry> entries = new ArrayList<>();
        Set<UUID> loadedIds = new HashSet<>();
        for (ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel == null || subLevel.isRemoved()) {
                continue;
            }
            loadedIds.add(subLevel.getUniqueId());
            Vector3d center = new Vector3d(subLevel.logicalPose().position());
            double distance = center.distance(playerPosition);
            if (range > 0 && distance > range) {
                continue;
            }
            String fullName = resolveLoadedName(subLevel);
            String displayName = fullName.equals(subLevel.getUniqueId().toString())
                    ? fullName.substring(0, 14)
                    : fullName;
            entries.add(new VehicleQueryEntry(
                    subLevel.getUniqueId(),
                    displayName,
                    fullName,
                    distance,
                    BlockPos.containing(center.x, center.y, center.z),
                    true,
                    false
            ));
        }

        for (StoredVehicleSnapshot snapshot : StoredVehicleRepository.snapshots(container)) {
            if (loadedIds.contains(snapshot.id())) {
                continue;
            }
            double distance = snapshot.position().distance(playerPosition);
            if (range > 0 && distance > range) {
                continue;
            }
            entries.add(new VehicleQueryEntry(
                    snapshot.id(),
                    snapshot.displayName(),
                    snapshot.fullName(),
                    distance,
                    BlockPos.containing(snapshot.position().x, snapshot.position().y, snapshot.position().z),
                    false,
                    snapshot.broken()
            ));
        }

        entries.sort(Comparator.comparingDouble(VehicleQueryEntry::distance)
                .thenComparing(VehicleQueryEntry::displayName));
        List<VehicleQueryEntry> result = List.copyOf(entries);
        RESULT_CACHE.put(cacheKey, new QueryResultCache(now, result));
        return result;
    }

    public static VehiclePreview createPreview(ServerLevel level, UUID subLevelId) throws IOException {
        ServerSubLevelContainer container = requireContainer(level);
        SubLevel subLevel = container.getSubLevel(subLevelId);
        if (subLevel instanceof ServerSubLevel serverSubLevel) {
            String name = resolveLoadedName(serverSubLevel);
            return new VehiclePreview(
                    name,
                    NativeBlueprintCaptureService.capturePreview(
                            level,
                            serverSubLevel.getUniqueId(),
                            name
                    ).fileContents()
            );
        }

        SubLevelData data = StoredVehicleRepository.findData(container, subLevelId);
        if (data == null) {
            throw new IllegalArgumentException("target is not available");
        }
        requireHealthyStoredVehicle(container, subLevelId);
        String name = StoredVehicleRepository.resolveName(data);
        return new VehiclePreview(
                name,
                NativeBlueprintCaptureService.captureStoredPreview(level, data, name)
        );
    }

    public static void teleportVehicle(ServerLevel level, UUID subLevelId, Vector3d targetPosition) throws Exception {
        ServerSubLevelContainer container = requireContainer(level);
        SubLevel subLevel = container.getSubLevel(subLevelId);
        if (subLevel instanceof ServerSubLevel) {
            StructureTransformService.teleportToWorldPosition(level, subLevelId, targetPosition);
        } else {
            requireHealthyStoredVehicle(container, subLevelId);
            StoredVehicleRepository.move(container, subLevelId, targetPosition);
        }
        clearCaches();
    }

    public static void teleportPlayerAboveVehicle(
            ServerLevel level,
            ServerPlayer player,
            UUID subLevelId
    ) throws IOException {
        BoundingBox3d bounds = findBounds(level, subLevelId);
        if (bounds == null) {
            throw new IllegalArgumentException("target is not available");
        }
        player.teleportTo(
                level,
                (bounds.minX() + bounds.maxX()) * 0.5D,
                bounds.maxY() + 2.0D,
                (bounds.minZ() + bounds.maxZ()) * 0.5D,
                player.getYRot(),
                player.getXRot()
        );
    }

    public static StoredVehicleRepository.RecoveryResult recoverStoredVehicle(
            ServerLevel level,
            UUID subLevelId
    ) throws Exception {
        ServerSubLevelContainer container = requireContainer(level);
        if (container.getSubLevel(subLevelId) instanceof ServerSubLevel) {
            throw new IllegalArgumentException("vehicle is already loaded and does not need storage recovery");
        }
        StoredVehicleRepository.RecoveryResult result = StoredVehicleRepository.recover(
                container,
                subLevelId
        );
        clearCaches();
        return result;
    }

    public static StoredVehicleRepository.GhostCreationResult createGhostVehicle(
            ServerLevel level,
            UUID subLevelId
    ) throws Exception {
        ServerSubLevelContainer container = requireContainer(level);
        if (container.getSubLevel(subLevelId) instanceof ServerSubLevel serverSubLevel) {
            return StoredVehicleRepository.createGhostFromLoaded(container, serverSubLevel);
        }
        StoredVehicleRepository.GhostCreationResult result = StoredVehicleRepository.createGhost(
                container,
                subLevelId
        );
        clearCaches();
        return result;
    }

    public static String rename(ServerLevel level, UUID subLevelId, String rawName) {
        ServerSubLevel subLevel = requireLoaded(level, subLevelId);
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("missing name");
        }
        subLevel.setName(name);
        clearCaches();
        return name;
    }

    public static void delete(ServerLevel level, UUID subLevelId) {
        ServerSubLevel subLevel = requireLoaded(level, subLevelId);
        SubLevelRemovalCoordinator.remove(level, requireContainer(level), List.of(subLevel));
        clearCaches();
    }

    public static void requireLoadedVehicle(ServerLevel level, UUID subLevelId) {
        requireLoaded(level, subLevelId);
    }

    public static void clearCaches() {
        StoredVehicleRepository.clearCache();
        RESULT_CACHE.clear();
    }

    private static BoundingBox3d findBounds(ServerLevel level, UUID subLevelId) throws IOException {
        ServerSubLevelContainer container = requireContainer(level);
        SubLevel subLevel = container.getSubLevel(subLevelId);
        if (subLevel instanceof ServerSubLevel serverSubLevel) {
            return new BoundingBox3d(serverSubLevel.boundingBox());
        }
        SubLevelData data = StoredVehicleRepository.findData(container, subLevelId);
        if (data != null) {
            requireHealthyStoredVehicle(container, subLevelId);
        }
        return data == null ? null : new BoundingBox3d(data.bounds());
    }

    private static void requireHealthyStoredVehicle(
            ServerSubLevelContainer container,
            UUID subLevelId
    ) throws IOException {
        StoredVehicleSnapshot snapshot = StoredVehicleRepository.findSnapshot(container, subLevelId);
        if (snapshot == null) {
            throw new IllegalArgumentException("target is not available");
        }
        if (snapshot.broken()) {
            throw new IllegalStateException("Sable storage index exists, but the vehicle data is incomplete");
        }
    }

    private static ServerSubLevel requireLoaded(ServerLevel level, UUID subLevelId) {
        SubLevel subLevel = requireContainer(level).getSubLevel(subLevelId);
        if (!(subLevel instanceof ServerSubLevel serverSubLevel)) {
            throw new IllegalArgumentException("target is not a physical structure");
        }
        return serverSubLevel;
    }

    private static ServerSubLevelContainer requireContainer(ServerLevel level) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            throw new IllegalStateException("sublevel container unavailable");
        }
        return container;
    }

    private static int sanitizeRange(int rawRange, boolean survivalTool) {
        int range = Math.max(INFINITE_QUERY_RANGE, rawRange);
        if (survivalTool && range == INFINITE_QUERY_RANGE) {
            return SURVIVAL_DEFAULT_QUERY_RANGE;
        }
        return range;
    }

    private static String resolveLoadedName(ServerSubLevel subLevel) {
        String name = subLevel.getName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return subLevel.getUniqueId().toString();
    }

    private record QueryResultCacheKey(
            String dimensionId,
            String storageFolder,
            BlockPos playerPosition,
            int range
    ) {
    }

    private record QueryResultCache(long createdAtMillis, List<VehicleQueryEntry> entries) {
    }
}
