package com.enxv.aeronauticsstructuretool.compat.sableschematicapi;

import com.enxv.aeronauticsstructuretool.blueprint.model.CapturePlan;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturedSubLevel;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlaceSession;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlacePhase;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintSaveSession;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintSavePhase;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintEvent;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintEventRegistry;
import dev.rew1nd.sableschematicapi.api.blueprint.SubLevelSaveFrame;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SableBlueprintApiCompatImpl {
    private static final int FORMAT_V1 = 1;
    private static final int FORMAT_V2 = 2;
    private static final String FORMAT_TAG = "format";
    private static final String EVENT_DATA_TAG = "event_data";
    private static final String EVENTS_TAG = "events";
    private static final String FRAMES_TAG = "frames";
    private static final String SABLE_ID_TAG = "sable_id";
    private static final String NATIVE_ID_TAG = "native_id";
    private static final String SOURCE_ID_TAG = "source_id";
    private static final String STORAGE_BOUNDS_TAG = "storage_bounds";
    private static final String BLOCKS_ORIGIN_TAG = "blocks_origin";
    private static final ResourceLocation WELD_EVENT_ID = ResourceLocation.fromNamespaceAndPath(
            "synaxis",
            "sable_blueprint/weld_constraints"
    );
    private static final ResourceLocation NO_CONTACT_EVENT_ID = ResourceLocation.fromNamespaceAndPath(
            "synaxis",
            "sable_blueprint/no_contact"
    );
    private static final List<ResourceLocation> SUPPORTED_EVENT_IDS = List.of(
            NO_CONTACT_EVENT_ID,
            WELD_EVENT_ID
    );

    private SableBlueprintApiCompatImpl() {
    }

    static CompoundTag capture(CapturePlan plan, BoundingBox3i aggregateBounds) {
        Map<ResourceLocation, SableBlueprintEvent> events = findSupportedEvents();
        if (events.isEmpty()) {
            return new CompoundTag();
        }

        Vector3d rootOrigin = new Vector3d();
        if (!plan.sublevels().isEmpty()) {
            rootOrigin.set(plan.sublevels().getFirst().subLevel().logicalPose().position());
        }
        BlueprintSaveSession session = new BlueprintSaveSession(
                (ServerLevel) plan.sublevels().getFirst().subLevel().getLevel(),
                rootOrigin,
                new BoundingBox3d(aggregateBounds)
        );
        CompoundTag frameData = new CompoundTag();
        for (int sableId = 0; sableId < plan.sublevels().size(); sableId++) {
            CapturedSubLevel captured = plan.sublevels().get(sableId);
            BoundingBox3i bounds = new BoundingBox3i(captured.subLevel().getPlot().getBoundingBox());
            BlockPos blocksOrigin = new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ());
            session.addFrame(new SubLevelSaveFrame(
                    sableId,
                    captured.subLevel().getUniqueId(),
                    (ServerSubLevel) captured.subLevel(),
                    bounds,
                    blocksOrigin,
                    new Pose3d(captured.subLevel().logicalPose())
            ));

            CompoundTag frame = new CompoundTag();
            frame.putInt(SABLE_ID_TAG, sableId);
            frame.putUUID(NATIVE_ID_TAG, captured.blueprintId());
            frame.putUUID(SOURCE_ID_TAG, captured.subLevel().getUniqueId());
            frame.putIntArray(STORAGE_BOUNDS_TAG, new int[]{
                    bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ()
            });
            frame.putIntArray(BLOCKS_ORIGIN_TAG, new int[]{
                    blocksOrigin.getX(), blocksOrigin.getY(), blocksOrigin.getZ()
            });
            frameData.put(String.valueOf(sableId), frame);
        }

        session.setPhase(BlueprintSavePhase.AFTER_BLOCKS);
        CompoundTag eventDataById = new CompoundTag();
        for (ResourceLocation eventId : SUPPORTED_EVENT_IDS) {
            SableBlueprintEvent event = events.get(eventId);
            if (event == null) {
                continue;
            }
            CompoundTag eventData = new CompoundTag();
            event.onSaveAfterBlocks(session, eventData);
            if (!eventData.isEmpty()) {
                eventDataById.put(eventId.toString(), eventData);
            }
        }
        if (eventDataById.isEmpty()) {
            return new CompoundTag();
        }

        CompoundTag sidecar = new CompoundTag();
        sidecar.putInt(FORMAT_TAG, FORMAT_V2);
        sidecar.put(EVENTS_TAG, eventDataById);
        sidecar.put(FRAMES_TAG, frameData);
        return sidecar;
    }

    static void restore(
            CompoundTag sidecar,
            Map<UUID, LoadedSubLevel> loadedSublevels,
            double scaleFactor
    ) {
        if (!sidecar.contains(FORMAT_TAG, Tag.TAG_INT)) {
            throw new IllegalArgumentException("Sable Blueprint API sidecar has no format");
        }
        int format = sidecar.getInt(FORMAT_TAG);
        if (format != FORMAT_V1 && format != FORMAT_V2) {
            throw new IllegalArgumentException("unsupported Sable Blueprint API weld sidecar format");
        }
        if (Math.abs(scaleFactor - 1.0D) > 1.0E-9D) {
            throw new IllegalArgumentException(
                    "scaled native placement is unsupported for Sable Blueprint API welds"
            );
        }
        CompoundTag eventDataById = readEventData(sidecar, format);
        if (eventDataById.isEmpty()) {
            return;
        }

        BlueprintPlaceSession session = new BlueprintPlaceSession(
                loadedSublevels.values().stream().findFirst().orElseThrow().subLevel().getLevel(),
                new Vector3d(),
                eventDataById
        );
        Map<Integer, CompoundTag> frames = readFrames(sidecar.getCompound(FRAMES_TAG));
        for (CompoundTag frame : frames.values()) {
            UUID nativeId = requireUuid(frame, NATIVE_ID_TAG);
            LoadedSubLevel loaded = loadedSublevels.get(nativeId);
            if (loaded == null) {
                throw new IllegalArgumentException("Sable weld frame references an unplaced native sublevel " + nativeId);
            }
            int sableId = frame.getInt(SABLE_ID_TAG);
            UUID sourceId = requireUuid(frame, SOURCE_ID_TAG);
            BlockPos placedOrigin = placedOrigin(loaded, frame);
            session.mapSubLevel(sableId, sourceId, loaded.subLevel(), placedOrigin);
        }
        session.setPhase(BlueprintPlacePhase.AFTER_BLOCK_ENTITIES);
        for (ResourceLocation eventId : SUPPORTED_EVENT_IDS) {
            CompoundTag eventData = eventDataById.getCompound(eventId.toString());
            if (eventData.isEmpty()) {
                continue;
            }
            SableBlueprintEvent event = findEvent(eventId);
            if (event == null) {
                throw new IllegalStateException("Synaxis Sable Blueprint event is unavailable: " + eventId);
            }
            event.onPlaceAfterBlockEntities(session, eventData);
        }
    }

    private static SableBlueprintEvent findEvent(ResourceLocation eventId) {
        return SableBlueprintEventRegistry.events().stream()
                .filter(event -> eventId.equals(event.id()))
                .findFirst()
                .orElse(null);
    }

    private static Map<ResourceLocation, SableBlueprintEvent> findSupportedEvents() {
        Map<ResourceLocation, SableBlueprintEvent> result = new HashMap<>();
        for (ResourceLocation eventId : SUPPORTED_EVENT_IDS) {
            SableBlueprintEvent event = findEvent(eventId);
            if (event != null) {
                result.put(eventId, event);
            }
        }
        return result;
    }

    private static CompoundTag readEventData(CompoundTag sidecar, int format) {
        if (format == FORMAT_V2) {
            return sidecar.getCompound(EVENTS_TAG);
        }
        CompoundTag legacyWeldData = sidecar.getCompound(EVENT_DATA_TAG);
        if (legacyWeldData.isEmpty()) {
            return new CompoundTag();
        }
        CompoundTag eventDataById = new CompoundTag();
        eventDataById.put(WELD_EVENT_ID.toString(), legacyWeldData.copy());
        return eventDataById;
    }

    private static Map<Integer, CompoundTag> readFrames(CompoundTag framesTag) {
        Map<Integer, CompoundTag> frames = new HashMap<>();
        for (String key : framesTag.getAllKeys()) {
            CompoundTag frame = framesTag.getCompound(key);
            int sableId = frame.getInt(SABLE_ID_TAG);
            if (frames.put(sableId, frame) != null) {
                throw new IllegalArgumentException("duplicate Sable weld frame id " + sableId);
            }
        }
        return frames;
    }

    private static BlockPos placedOrigin(LoadedSubLevel loaded, CompoundTag frame) {
        int[] bounds = frame.getIntArray(STORAGE_BOUNDS_TAG);
        int[] origin = frame.getIntArray(BLOCKS_ORIGIN_TAG);
        if (bounds.length != 6 || origin.length != 3) {
            throw new IllegalArgumentException("Sable weld frame bounds are malformed");
        }
        int minX = bounds[0] - origin[0];
        int minY = bounds[1] - origin[1];
        int minZ = bounds[2] - origin[2];
        int maxX = bounds[3] - origin[0];
        int maxY = bounds[4] - origin[1];
        int maxZ = bounds[5] - origin[2];
        BlockPos center = loaded.subLevel().getPlot().getCenterBlock();
        return new BlockPos(
                center.getX() - (minX + maxX) / 2,
                center.getY() - (minY + maxY) / 2,
                center.getZ() - (minZ + maxZ) / 2
        );
    }

    private static UUID requireUuid(CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) {
            throw new IllegalArgumentException("Sable weld frame is missing UUID '" + key + "'");
        }
        return tag.getUUID(key);
    }
}
