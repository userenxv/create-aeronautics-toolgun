package com.enxv.aeronauticsstructuretool.blueprint.capture;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.blueprint.material.BlueprintInventoryMaterialCapture;
import com.enxv.aeronauticsstructuretool.BlueprintMaterialSummary;
import com.enxv.aeronauticsstructuretool.RuntimeContraptionBlueprint;
import com.enxv.aeronauticsstructuretool.server.SubLevelCollisionToggleManager;
import com.enxv.aeronauticsstructuretool.ToolgunConstraintTracker;
import com.enxv.aeronauticsstructuretool.blueprint.codec.BlueprintArchiveCodec;
import com.enxv.aeronauticsstructuretool.blueprint.codec.NativeBlueprintFormat;
import com.enxv.aeronauticsstructuretool.blueprint.codec.NbtTransformCodec;
import com.enxv.aeronauticsstructuretool.blueprint.compat.BlockEntityCompatibilityPipeline;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.BlueprintLocalAnchorResolver;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockEntityPositionRemapper;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockTransform;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturePlan;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturedBlueprintArchive;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturedSubLevel;
import com.enxv.aeronauticsstructuretool.blueprint.security.BlueprintBlockEntitySanitizer;
import com.enxv.aeronauticsstructuretool.blueprint.security.BlueprintSanitizationReporter;
import com.enxv.aeronauticsstructuretool.blueprint.security.PlotBlockEntityCompatibilityGuard;
import com.enxv.aeronauticsstructuretool.blueprint.storage.BlueprintFileRepository;
import com.enxv.aeronauticsstructuretool.compat.create.CreateBlueprintCompat;
import com.enxv.aeronauticsstructuretool.compat.drivebywire.DriveByWireCaptureSession;
import com.enxv.aeronauticsstructuretool.compat.sable.SableSchematicContextFactory;
import com.enxv.aeronauticsstructuretool.compat.sableschematicapi.SableBlueprintApiCompat;
import com.enxv.aeronauticsstructuretool.compat.simulated.SimulatedGlueBlueprintCompat;
import com.enxv.aeronauticsstructuretool.compat.synaxis.SynaxisCaptureSession;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class NativeBlueprintCaptureService {
    public static final double DEFAULT_CONNECTED_SUBLEVEL_PROXIMITY_BLOCKS = 0.5D;

    private NativeBlueprintCaptureService() {
    }

    public static CapturedBlueprintArchive captureAtBlock(
            ServerLevel level,
            BlockPos clickedPos,
            String rawName,
            double connectedSublevelProximityBlocks
    ) throws IOException {
        SubLevel containing = Sable.HELPER.getContaining(level, clickedPos);
        if (!(containing instanceof ServerSubLevel rootSubLevel)) {
            throw new IOException("not a physical structure");
        }
        return capture(level, rootSubLevel, rawName, connectedSublevelProximityBlocks, true);
    }

    public static CapturedBlueprintArchive captureById(
            ServerLevel level,
            UUID rootStructureId,
            String rawName,
            double connectedSublevelProximityBlocks
    ) throws IOException {
        return capture(
                level,
                requireSubLevel(level, rootStructureId),
                rawName,
                connectedSublevelProximityBlocks,
                true
        );
    }

    public static CapturedBlueprintArchive capturePreview(
            ServerLevel level,
            UUID rootStructureId,
            String rawName
    ) throws IOException {
        return capture(
                level,
                requireSubLevel(level, rootStructureId),
                rawName,
                DEFAULT_CONNECTED_SUBLEVEL_PROXIMITY_BLOCKS,
                false
        );
    }

    public static byte[] captureStoredPreview(ServerLevel level, SubLevelData data, String name) throws IOException {
        CompoundTag plotTag = data.fullTag().getCompound(NativeBlueprintFormat.PLOT_TAG).copy();
        if (plotTag.isEmpty()) {
            throw new IOException("stored structure has no plot data");
        }
        PlotBlockEntityPositionRemapper.localizeForBlueprint(plotTag);

        CompoundTag root = new CompoundTag();
        root.putString(NativeBlueprintFormat.FORMAT_TAG, NativeBlueprintFormat.CURRENT_FORMAT);
        root.putString(NativeBlueprintFormat.NAME_TAG, name);
        root.putUUID(NativeBlueprintFormat.ROOT_SUBLEVEL_TAG, data.uuid());
        root.put(NativeBlueprintFormat.ROOT_ORIENTATION_TAG, NbtTransformCodec.writeQuaternion(new Quaterniond()));
        root.put(NativeBlueprintFormat.ROOT_ROTATION_OFFSET_TAG, NbtTransformCodec.writeVector(new Vector3d()));
        root.putInt(NativeBlueprintFormat.SOURCE_MIN_BUILD_HEIGHT_TAG, level.getMinBuildHeight());

        CompoundTag sublevelTag = new CompoundTag();
        sublevelTag.putUUID(NativeBlueprintFormat.SUBLEVEL_ID_TAG, data.uuid());
        sublevelTag.putString(NativeBlueprintFormat.NAME_TAG, name);
        sublevelTag.put(NativeBlueprintFormat.PLOT_TAG, plotTag);
        sublevelTag.put(NativeBlueprintFormat.RELATIVE_POSITION_TAG, NbtTransformCodec.writeVector(new Vector3d()));
        sublevelTag.put(NativeBlueprintFormat.RELATIVE_ROTATION_OFFSET_TAG, NbtTransformCodec.writeVector(new Vector3d()));
        sublevelTag.put(NativeBlueprintFormat.RELATIVE_ORIENTATION_TAG, NbtTransformCodec.writeQuaternion(new Quaterniond()));

        ListTag sublevels = new ListTag();
        sublevels.add(sublevelTag);
        root.put(NativeBlueprintFormat.SUBLEVELS_TAG, sublevels);
        return BlueprintArchiveCodec.encode(root);
    }

    private static CapturedBlueprintArchive capture(
            ServerLevel level,
            ServerSubLevel rootSubLevel,
            String rawName,
            double connectedSublevelProximityBlocks,
            boolean logSummary
    ) throws IOException {
        String safeName = BlueprintFileRepository.normalizeName(rawName);
        if (safeName.isEmpty()) {
            throw new IOException("empty name");
        }

        List<ServerSubLevel> collectedSubLevels = ConnectedSubLevelCollector.collect(
                level,
                rootSubLevel,
                connectedSublevelProximityBlocks
        );
        if (collectedSubLevels.isEmpty()) {
            throw new IOException("no connected sublevels found");
        }

        CapturePlan plan = CapturePlan.create(rootSubLevel, collectedSubLevels);
        SynaxisCaptureSession synaxisCapture = new SynaxisCaptureSession(level, plan);
        CompoundTag root = new CompoundTag();
        root.putString(NativeBlueprintFormat.FORMAT_TAG, NativeBlueprintFormat.CURRENT_FORMAT);
        root.putString(NativeBlueprintFormat.NAME_TAG, safeName);
        root.putUUID(NativeBlueprintFormat.ROOT_SUBLEVEL_TAG, plan.rootBlueprintId());
        root.put(NativeBlueprintFormat.ROOT_ORIENTATION_TAG, NbtTransformCodec.writeQuaternion(plan.rootOrientation()));
        root.put(NativeBlueprintFormat.ROOT_ROTATION_OFFSET_TAG, NbtTransformCodec.writeVector(plan.rootRotationOffset()));
        root.putInt(NativeBlueprintFormat.SOURCE_MIN_BUILD_HEIGHT_TAG, level.getMinBuildHeight());

        ListTag sublevelsTag = new ListTag();
        int totalBlockEntities = 0;
        int totalRuntimeContraptions = 0;
        DriveByWireCaptureSession driveByWireCapture = new DriveByWireCaptureSession(safeName);
        BoundingBox3i aggregateBounds = new BoundingBox3i(new BoundingBox3d(rootSubLevel.boundingBox()));
        for (CapturedSubLevel captured : plan.sublevels()) {
            aggregateBounds.expandTo(new BoundingBox3i(new BoundingBox3d(captured.subLevel().boundingBox())));
        }

        for (CapturedSubLevel captured : plan.sublevels()) {
            PlotBlockTransform transform = PlotBlockTransform.capture(captured.subLevel());
            SubLevelSchematicSerializationContext context = SableSchematicContextFactory.createSaveContext(
                    plan,
                    aggregateBounds
            );
            CompoundTag astDriveByWireSnapshot = driveByWireCapture.captureSnapshot(
                    level,
                    captured.subLevel(),
                    context
            );
            CompoundTag plotTag;
            try {
                SubLevelSchematicSerializationContext.setCurrentContext(context);
                plotTag = captured.subLevel().getPlot().save();
            } finally {
                SubLevelSchematicSerializationContext.setCurrentContext(null);
            }

            BlueprintSanitizationReporter.report(
                    "save",
                    safeName,
                    BlueprintBlockEntitySanitizer.sanitize(plotTag)
            );
            PlotBlockEntityCompatibilityGuard.removeKnownMismatches(plotTag, "save");
            synaxisCapture.processPlot(plotTag, captured);
            BlockEntityCompatibilityPipeline.remapForSave(plotTag, plan, captured);
            CreateBlueprintCompat.captureSuperGlue(level, transform, plotTag);
            SimulatedGlueBlueprintCompat.captureHoneyGlue(level, transform, plotTag);
            driveByWireCapture.finishPlot(plotTag, astDriveByWireSnapshot);
            Map<String, Long> additionalMaterialItems = BlueprintInventoryMaterialCapture.captureAdditionalLiveItems(
                    level,
                    transform,
                    plotTag
            );
            int blockEntityCount = PlotBlockEntityPositionRemapper.localizeForBlueprint(plotTag);
            List<RuntimeContraptionBlueprint> runtimeContraptions = RuntimeContraptionBlueprint.capture(level, transform);
            totalBlockEntities += blockEntityCount;
            totalRuntimeContraptions += runtimeContraptions.size();

            CompoundTag sublevelTag = new CompoundTag();
            sublevelTag.putUUID(NativeBlueprintFormat.SUBLEVEL_ID_TAG, captured.blueprintId());
            sublevelTag.putUUID(NativeBlueprintFormat.ORIGINAL_SUBLEVEL_ID_TAG, captured.subLevel().getUniqueId());
            sublevelTag.putString(
                    NativeBlueprintFormat.NAME_TAG,
                    Objects.requireNonNullElse(captured.subLevel().getName(), "")
            );
            sublevelTag.put(NativeBlueprintFormat.PLOT_TAG, plotTag);
            sublevelTag.put(
                    NativeBlueprintFormat.RUNTIME_CONTRAPTIONS_TAG,
                    RuntimeContraptionBlueprint.writeList(runtimeContraptions)
            );
            if (!additionalMaterialItems.isEmpty()) {
                sublevelTag.put(
                        BlueprintMaterialSummary.ADDITIONAL_ITEMS_TAG,
                        BlueprintMaterialSummary.writeItemCounts(additionalMaterialItems)
                );
            }
            if (SubLevelCollisionToggleManager.isCollisionDisabled(captured.subLevel())) {
                sublevelTag.putBoolean(NativeBlueprintFormat.DISABLE_STRUCTURE_COLLISION_TAG, true);
            }
            sublevelTag.put(
                    NativeBlueprintFormat.RELATIVE_POSITION_TAG,
                    NbtTransformCodec.writeVector(captured.relativePosition())
            );
            sublevelTag.put(
                    NativeBlueprintFormat.RELATIVE_ROTATION_OFFSET_TAG,
                    NbtTransformCodec.writeVector(captured.relativeRotationOffset())
            );
            sublevelTag.put(
                    NativeBlueprintFormat.RELATIVE_ORIENTATION_TAG,
                    NbtTransformCodec.writeQuaternion(captured.relativeOrientation())
            );
            if (captured.localAnchor() != null) {
                sublevelTag.put(
                        NativeBlueprintFormat.LOCAL_ANCHOR_TAG,
                        NbtTransformCodec.writeVector(transform.toSavedLocalPosition(captured.localAnchor()))
                );
                sublevelTag.putString(
                        BlueprintLocalAnchorResolver.LOCAL_ANCHOR_SPACE_TAG,
                        BlueprintLocalAnchorResolver.SAVED_PLOT_LOCAL_SPACE
                );
            }
            sublevelsTag.add(sublevelTag);
        }

        root.put(NativeBlueprintFormat.SUBLEVELS_TAG, sublevelsTag);
        CompoundTag sableBlueprintApiSidecar = SableBlueprintApiCompat.capture(plan, aggregateBounds);
        if (!sableBlueprintApiSidecar.isEmpty()) {
            root.put(NativeBlueprintFormat.SABLE_BLUEPRINT_API_SIDECAR_TAG, sableBlueprintApiSidecar);
        }
        BlueprintSanitizationReporter.report(
                "save-final",
                safeName,
                BlueprintBlockEntitySanitizer.sanitize(root)
        );
        BlueprintMaterialSummary materialSummary = BlueprintMaterialSummary.captureFromSublevels(
                sublevelsTag,
                level.getMinBuildHeight()
        );
        root.put(BlueprintMaterialSummary.ROOT_TAG, materialSummary.toTag());
        synaxisCapture.finishRoot(root);
        root.put(
                ToolgunConstraintTracker.constraintsTagName(),
                ToolgunConstraintTracker.writeConstraintsForSave(plan, collectedSubLevels)
        );
        driveByWireCapture.complete(logSummary);
        if (logSummary) {
            AeronauticsStructureToolMod.LOGGER.info(
                    "Saved structure '{}' with {} sublevels, {} block entities and {} runtime contraptions",
                    safeName,
                    plan.sublevels().size(),
                    totalBlockEntities,
                    totalRuntimeContraptions
            );
        }
        return new CapturedBlueprintArchive(
                safeName,
                BlueprintArchiveCodec.encode(root),
                materialSummary
        );
    }

    private static ServerSubLevel requireSubLevel(ServerLevel level, UUID rootStructureId) throws IOException {
        if (rootStructureId == null) {
            throw new IOException("missing structure id");
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            throw new IOException("sublevel container unavailable");
        }
        SubLevel subLevel = container.getSubLevel(rootStructureId);
        if (!(subLevel instanceof ServerSubLevel serverSubLevel)) {
            throw new IOException("structure not found: " + rootStructureId);
        }
        return serverSubLevel;
    }
}
