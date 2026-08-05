package com.enxv.aeronauticsstructuretool.toolgun.constraint;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.ConnectionMode;
import com.enxv.aeronauticsstructuretool.blueprint.codec.NbtTransformCodec;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.LoadedSubLevelCoordinates;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockTransform;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturePlan;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturedSubLevel;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import com.enxv.aeronauticsstructuretool.core.FailureMessages;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static com.enxv.aeronauticsstructuretool.toolgun.constraint.persistence.ConstraintNbtKeys.*;

public final class ConstraintBlueprintService {
    private static final String SAVED_PLOT_LOCAL_V1 = "saved_plot_local_v1";
    private static final double ENDPOINT_ALIGNMENT_WARNING_THRESHOLD = 0.5D;

    private ConstraintBlueprintService() {
    }

    public static ListTag write(
            CapturePlan plan,
            Collection<SubLevel> includedSublevels
    ) {
        ConstraintRuntimeRepository.cleanupInvalid();
        Map<UUID, UUID> blueprintIds = new LinkedHashMap<>();
        for (SubLevel subLevel : includedSublevels) {
            CapturedSubLevel captured = plan.findByOriginalId(subLevel.getUniqueId());
            if (captured != null) {
                blueprintIds.put(subLevel.getUniqueId(), captured.blueprintId());
            }
        }

        ListTag constraints = new ListTag();
        for (TrackedConstraint constraint : ConstraintRuntimeRepository.all()) {
            UUID firstBlueprintId = blueprintIds.get(constraint.firstSubLevelId());
            UUID secondBlueprintId = blueprintIds.get(constraint.secondSubLevelId());
            if (firstBlueprintId == null && secondBlueprintId == null) {
                continue;
            }
            if (firstBlueprintId == null || secondBlueprintId == null) {
                throw new IllegalStateException(
                        "Cannot capture constraint " + constraint.constraintId()
                                + " because only one endpoint is present in the capture plan"
                );
            }
            CapturedSubLevel firstCaptured = plan.findByBlueprintId(firstBlueprintId);
            CapturedSubLevel secondCaptured = plan.findByBlueprintId(secondBlueprintId);
            if (firstCaptured == null || secondCaptured == null) {
                throw new IllegalStateException("Capture plan lost a mapped constraint sublevel");
            }

            CompoundTag tag = new CompoundTag();
            tag.putString(COORDINATE_SPACE_TAG, SAVED_PLOT_LOCAL_V1);
            tag.putUUID(CONSTRAINT_ID_TAG, constraint.constraintId());
            tag.putUUID(FIRST_SUBLEVEL_TAG, firstBlueprintId);
            tag.putUUID(SECOND_SUBLEVEL_TAG, secondBlueprintId);
            tag.putString(MODE_TAG, constraint.connectionMode().name());
            putOptionalVector(
                    tag,
                    FIRST_DISPLAY_LOCAL_TAG,
                    toSavedLocal(firstCaptured, constraint.firstDisplayLocalPoint())
            );
            putOptionalVector(
                    tag,
                    SECOND_DISPLAY_LOCAL_TAG,
                    toSavedLocal(secondCaptured, constraint.secondDisplayLocalPoint())
            );
            tag.put(FIRST_LOCAL_TAG, NbtTransformCodec.writeVector(
                    toSavedLocal(firstCaptured, constraint.firstLocalPoint())
            ));
            tag.put(SECOND_LOCAL_TAG, NbtTransformCodec.writeVector(
                    toSavedLocal(secondCaptured, constraint.secondLocalPoint())
            ));
            tag.put(RELATIVE_ORIENTATION_TAG, NbtTransformCodec.writeQuaternion(
                    constraint.relativeOrientation()
            ));
            putOptionalVector(tag, FIRST_AXIS_LOCAL_TAG, constraint.firstAxisLocal());
            putOptionalVector(tag, SECOND_AXIS_LOCAL_TAG, constraint.secondAxisLocal());
            constraints.add(tag);
        }
        return constraints;
    }

    public static List<String> restore(
            ServerLevel level,
            ListTag constraints,
            Map<UUID, LoadedSubLevel> loadedSublevels,
            Consumer<TrackedConstraint> registrar
    ) {
        if (constraints == null || constraints.isEmpty()) {
            return List.of();
        }
        ConstraintRuntimeRepository.cleanupInvalid();
        List<String> warnings = new ArrayList<>();
        for (int i = 0; i < constraints.size(); i++) {
            try {
                double endpointError = restoreOne(
                        level,
                        constraints.getCompound(i),
                        loadedSublevels,
                        registrar
                );
                if (requiresEndpointAlignmentWarning(endpointError)) {
                    warnings.add(
                            "constraint entry " + i + " was restored with an endpoint offset of "
                                    + endpointError + " blocks; sublevel poses and saved endpoints were left unchanged"
                    );
                }
            } catch (Exception exception) {
                AeronauticsStructureToolMod.LOGGER.warn(
                        "Constraint blueprint entry {} was not restored; structure placement will continue",
                        i,
                        exception
                );
                warnings.add(
                        "constraint entry " + i + " was not restored: " + failureMessage(exception)
                );
            }
        }
        return List.copyOf(warnings);
    }

    private static double restoreOne(
            ServerLevel level,
            CompoundTag tag,
            Map<UUID, LoadedSubLevel> loadedSublevels,
            Consumer<TrackedConstraint> registrar
    ) throws IOException {
        UUID firstBlueprintId = requireUuid(tag, FIRST_SUBLEVEL_TAG);
        UUID secondBlueprintId = requireUuid(tag, SECOND_SUBLEVEL_TAG);
        LoadedSubLevel firstLoaded = requireLoaded(loadedSublevels, firstBlueprintId, "first");
        LoadedSubLevel secondLoaded = requireLoaded(loadedSublevels, secondBlueprintId, "second");

        UUID constraintId = UUID.randomUUID();
        boolean explicitLocalSpace = readCoordinateSpace(tag);
        Vector3d firstPoint = decodePoint(tag, FIRST_LOCAL_TAG, firstLoaded, explicitLocalSpace, true);
        Vector3d secondPoint = decodePoint(tag, SECOND_LOCAL_TAG, secondLoaded, explicitLocalSpace, true);
        Vector3d firstDisplayPoint = decodePoint(
                tag,
                FIRST_DISPLAY_LOCAL_TAG,
                firstLoaded,
                explicitLocalSpace,
                false
        );
        Vector3d secondDisplayPoint = decodePoint(
                tag,
                SECOND_DISPLAY_LOCAL_TAG,
                secondLoaded,
                explicitLocalSpace,
                false
        );
        Quaterniond relativeOrientation = readRequiredQuaternion(tag, RELATIVE_ORIENTATION_TAG);
        Vector3d firstAxis = readOptionalVector(tag, FIRST_AXIS_LOCAL_TAG);
        Vector3d secondAxis = readOptionalVector(tag, SECOND_AXIS_LOCAL_TAG);

        double endpointError = measureEndpointAlignmentError(
                firstLoaded,
                firstPoint,
                secondLoaded,
                secondPoint
        );
        TrackedConstraint restored = ConstraintRuntimeFactory.restoreTrackedConstraint(
                level,
                firstLoaded.subLevel(),
                secondLoaded.subLevel(),
                firstPoint,
                secondPoint,
                firstDisplayPoint,
                secondDisplayPoint,
                relativeOrientation,
                firstAxis,
                secondAxis,
                readConnectionMode(tag),
                constraintId
        );
        if (restored.handle() == null) {
            throw new IOException("Constraint restoration produced no valid physics handle");
        }
        registrar.accept(restored);
        return endpointError;
    }

    private static Vector3d decodePoint(
            CompoundTag tag,
            String key,
            LoadedSubLevel loaded,
            boolean explicitLocalSpace,
            boolean required
    ) throws IOException {
        if (!tag.contains(key)) {
            if (required) {
                throw new IOException("Missing constraint vector '" + key + "'");
            }
            return null;
        }
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            throw new IOException("Constraint vector '" + key + "' must be a compound");
        }
        Vector3d stored = NbtTransformCodec.readVector(tag.getCompound(key), "constraint " + key);
        if (explicitLocalSpace) {
            return LoadedSubLevelCoordinates.toGlobalPosition(loaded, stored);
        }
        return LegacyConstraintCoordinateDecoder.decode(loaded, stored);
    }

    private static Vector3d toSavedLocal(CapturedSubLevel captured, Vector3d runtimePoint) {
        if (runtimePoint == null) {
            return null;
        }
        PlotBlockTransform transform = PlotBlockTransform.capture(captured.subLevel());
        return transform.toSavedLocalPosition(new Vector3d(runtimePoint));
    }

    private static double measureEndpointAlignmentError(
            LoadedSubLevel firstLoaded,
            Vector3d firstPoint,
            LoadedSubLevel secondLoaded,
            Vector3d secondPoint
    ) throws IOException {
        Vector3d firstWorld = firstLoaded.subLevel().logicalPose()
                .transformPosition(new Vector3d(firstPoint), new Vector3d());
        Vector3d secondWorld = secondLoaded.subLevel().logicalPose()
                .transformPosition(new Vector3d(secondPoint), new Vector3d());
        double distance = firstWorld.distance(secondWorld);
        if (!Double.isFinite(distance)) {
            throw new IOException("Constraint endpoint alignment is not finite");
        }
        return distance;
    }

    static boolean requiresEndpointAlignmentWarning(double distance) throws IOException {
        if (!Double.isFinite(distance) || distance < 0.0D) {
            throw new IOException("Constraint endpoint alignment must be finite and non-negative");
        }
        return distance > ENDPOINT_ALIGNMENT_WARNING_THRESHOLD;
    }

    private static String failureMessage(Exception exception) {
        return FailureMessages.describe(exception, exception.getClass().getSimpleName());
    }

    private static boolean readCoordinateSpace(CompoundTag tag) throws IOException {
        if (!tag.contains(COORDINATE_SPACE_TAG)) {
            return false;
        }
        if (!tag.contains(COORDINATE_SPACE_TAG, Tag.TAG_STRING)) {
            throw new IOException("Constraint coordinate space tag must be a string");
        }
        String raw = tag.getString(COORDINATE_SPACE_TAG);
        if (raw.isBlank()) {
            return false;
        }
        if (SAVED_PLOT_LOCAL_V1.equals(raw)) {
            return true;
        }
        throw new IOException("Unsupported constraint coordinate space '" + raw + "'");
    }

    private static ConnectionMode readConnectionMode(CompoundTag tag) throws IOException {
        if (!tag.contains(MODE_TAG, Tag.TAG_STRING)) {
            throw new IOException("Missing constraint mode '" + MODE_TAG + "'");
        }
        String raw = tag.getString(MODE_TAG);
        try {
            return ConnectionMode.valueOf(raw);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Unsupported constraint mode '" + raw + "'", exception);
        }
    }

    private static LoadedSubLevel requireLoaded(
            Map<UUID, LoadedSubLevel> loadedSublevels,
            UUID blueprintId,
            String endpoint
    ) throws IOException {
        LoadedSubLevel loaded = loadedSublevels.get(blueprintId);
        if (loaded == null) {
            throw new IOException("Constraint " + endpoint + " endpoint references missing sublevel " + blueprintId);
        }
        return loaded;
    }

    private static UUID requireUuid(CompoundTag tag, String key) throws IOException {
        if (!tag.hasUUID(key)) {
            throw new IOException("Missing constraint UUID '" + key + "'");
        }
        return tag.getUUID(key);
    }

    private static Quaterniond readRequiredQuaternion(CompoundTag tag, String key) throws IOException {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            throw new IOException("Missing constraint quaternion '" + key + "'");
        }
        return NbtTransformCodec.readQuaternion(tag.getCompound(key), "constraint " + key);
    }

    private static Vector3d readOptionalVector(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return null;
        }
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Constraint vector '" + key + "' must be a compound");
        }
        return NbtTransformCodec.readVector(tag.getCompound(key), "constraint " + key);
    }

    private static void putOptionalVector(CompoundTag tag, String key, Vector3d value) {
        if (value != null) {
            tag.put(key, NbtTransformCodec.writeVector(value));
        }
    }

    private static final class LegacyConstraintCoordinateDecoder {
        private LegacyConstraintCoordinateDecoder() {
        }

        static Vector3d decode(LoadedSubLevel loaded, Vector3d stored) {
            return LoadedSubLevelCoordinates.toGlobalPosition(loaded, stored);
        }
    }
}
