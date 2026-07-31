package com.enxv.aeronauticsstructuretool.blueprint.placement;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.PlacementSnapMode;
import com.enxv.aeronauticsstructuretool.PreviewBlueprintData;
import com.enxv.aeronauticsstructuretool.ToolgunConstraintTracker;
import com.enxv.aeronauticsstructuretool.blueprint.codec.BlueprintArchiveCodec;
import com.enxv.aeronauticsstructuretool.blueprint.codec.NativeBlueprintReader;
import com.enxv.aeronauticsstructuretool.blueprint.codec.NativeBlueprintFormat;
import com.enxv.aeronauticsstructuretool.blueprint.compat.BlockEntityCompatibilityPipeline;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockEntityPositionRemapper;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockTransform;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotVerticalLayout;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import com.enxv.aeronauticsstructuretool.blueprint.model.NativeBlueprintDocument;
import com.enxv.aeronauticsstructuretool.blueprint.model.NativeBlueprintPlacementResult;
import com.enxv.aeronauticsstructuretool.blueprint.model.SavedSubLevelBlueprint;
import com.enxv.aeronauticsstructuretool.blueprint.runtime.BlueprintPlacementObserver;
import com.enxv.aeronauticsstructuretool.blueprint.security.BlueprintBlockEntitySanitizer;
import com.enxv.aeronauticsstructuretool.blueprint.security.BlueprintSanitizationReporter;
import com.enxv.aeronauticsstructuretool.blueprint.security.MissingRegistryContentSanitizer;
import com.enxv.aeronauticsstructuretool.blueprint.security.PlotBlockEntityCompatibilityGuard;
import com.enxv.aeronauticsstructuretool.blueprint.storage.BlueprintFileRepository;
import com.enxv.aeronauticsstructuretool.compat.create.CreateBlueprintCompat;
import com.enxv.aeronauticsstructuretool.compat.drivebywire.DriveByWireLoadResult;
import com.enxv.aeronauticsstructuretool.compat.drivebywire.DriveByWireLoadSession;
import com.enxv.aeronauticsstructuretool.compat.sable.SableSchematicContextFactory;
import com.enxv.aeronauticsstructuretool.compat.sableschematicapi.SableBlueprintApiCompat;
import com.enxv.aeronauticsstructuretool.compat.simulated.SimulatedGlueBlueprintCompat;
import com.enxv.aeronauticsstructuretool.compat.simulated.SimulatedStructureCompat;
import com.enxv.aeronauticsstructuretool.compat.synaxis.SynaxisLoadResult;
import com.enxv.aeronauticsstructuretool.compat.synaxis.SynaxisLoadSession;
import com.enxv.aeronauticsstructuretool.server.BlueprintPlacementWarningNotifier;
import com.enxv.aeronauticsstructuretool.server.ServerServices;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NativeBlueprintPlacementService {
    private NativeBlueprintPlacementService() {
    }

    public static NativeBlueprintPlacementResult place(
            ServerLevel level,
            BlockPos clickedPos,
            Direction face,
            String rawName,
            byte[] fileContents,
            double hitX,
            double hitY,
            double hitZ,
            int rotationDegrees,
            int scalePercent,
            int offsetX,
            int offsetY,
            int offsetZ,
            PlacementSnapMode snapMode,
            BlueprintPlacementAttachment attachment,
            BlueprintVerticalPlacement verticalPlacement,
            UUID notificationPlayerId,
            BlueprintPlacementObserver observer
    ) throws IOException {
        String safeName = BlueprintFileRepository.normalizeName(rawName);
        if (safeName.isEmpty()) {
            throw new IOException("empty name");
        }
        if (snapMode == null) {
            throw new IOException("placement snap mode is missing");
        }
        if (scalePercent < 25 || scalePercent > 400) {
            throw new IOException("blueprint scale must be between 25 and 400 percent");
        }
        CompoundTag root = BlueprintArchiveCodec.decode(fileContents);
        BlueprintSanitizationReporter.report(
                "load",
                safeName,
                BlueprintBlockEntitySanitizer.sanitize(root)
        );
        MissingRegistryContentSanitizer.Result skippedContent =
                MissingRegistryContentSanitizer.sanitizeNative(root, level.registryAccess());
        return placeCurrent(
                level,
                clickedPos,
                face,
                safeName,
                root,
                hitX,
                hitY,
                hitZ,
                rotationDegrees,
                scalePercent,
                offsetX,
                offsetY,
                offsetZ,
                snapMode,
                attachment,
                verticalPlacement,
                notificationPlayerId,
                observer,
                skippedContent
        );
    }

    private static NativeBlueprintPlacementResult placeCurrent(
            ServerLevel level,
            BlockPos clickedPos,
            Direction face,
            String safeName,
            CompoundTag root,
            double hitX,
            double hitY,
            double hitZ,
            int rotationDegrees,
            int scalePercent,
            int offsetX,
            int offsetY,
            int offsetZ,
            PlacementSnapMode snapMode,
            BlueprintPlacementAttachment attachment,
            BlueprintVerticalPlacement verticalPlacement,
            UUID notificationPlayerId,
            BlueprintPlacementObserver observer,
            MissingRegistryContentSanitizer.Result skippedContent
    ) throws IOException {
        NativeBlueprintDocument document = NativeBlueprintReader.read(root);
        List<SavedSubLevelBlueprint> savedSublevels = document.sublevels();
        DriveByWireLoadSession driveByWireLoad = new DriveByWireLoadSession(safeName, savedSublevels);
        SynaxisLoadSession synaxisLoad = new SynaxisLoadSession(safeName, root);
        boolean hasRuntimeContraptions = savedSublevels.stream()
                .anyMatch(saved -> !saved.runtimeContraptions().isEmpty());
        if (scalePercent != 100 && hasRuntimeContraptions) {
            throw new IOException("scaled printing is not supported for dynamic structures yet");
        }

        UUID rootBlueprintId = document.rootBlueprintId();
        ServerSubLevel attachmentTarget = attachment != null
                && Sable.HELPER.getContaining(level, clickedPos) instanceof ServerSubLevel existing
                ? existing
                : null;
        double scaleFactor = scalePercent / 100.0D;
        Quaterniond rootOrientation = new Quaterniond(document.rootOrientation());
        Quaterniond extraRotation = PlacementTargetMath.computeExtraRotation(face, rotationDegrees);
        rootOrientation.mul(extraRotation).normalize();
        Vector3d rootRotationOffset = new Vector3d(document.rootRotationOffset()).mul(scaleFactor);
        rootOrientation.transform(rootRotationOffset);
        Vector3d target = PlacementTargetMath.computePlacementTarget(
                clickedPos,
                face,
                hitX,
                hitY,
                hitZ,
                snapMode,
                offsetX,
                offsetY,
                offsetZ
        );
        BlueprintVerticalPlacement safeVerticalPlacement = verticalPlacement == null
                ? BlueprintVerticalPlacement.keepAboveSurface(face, hitY)
                : verticalPlacement;
        double computedMinimumRelativeY = safeVerticalPlacement.needsComputedMinimum()
                ? PreviewBlueprintData.parse(root, level).minimumRelativeBlockCenterY(extraRotation, scaleFactor)
                : Double.NaN;
        try {
            target = safeVerticalPlacement.apply(target, computedMinimumRelativeY);
        } catch (IllegalArgumentException exception) {
            throw new IOException("invalid blueprint vertical placement: " + exception.getMessage(), exception);
        }
        validatePlacementTarget(level, target);

        Pose3d rootPlacementPose = new Pose3d();
        rootPlacementPose.position().set(target);
        rootPlacementPose.orientation().set(rootOrientation);
        rootPlacementPose.rotationPoint().set(rootRotationOffset);
        rootPlacementPose.scale().set(scaleFactor, scaleFactor, scaleFactor);

        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            throw new IOException("Sable sublevel container is unavailable");
        }
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        Map<UUID, LoadedSubLevel> loadedSublevels = new LinkedHashMap<>();
        BlueprintPlacementDiagnostics diagnostics = new BlueprintPlacementDiagnostics(safeName);

        try {
            allocateSublevels(
                    level,
                    container,
                    savedSublevels,
                    rootPlacementPose,
                    rootOrientation,
                    scaleFactor,
                    loadedSublevels
            );
            BoundingBox3i aggregateBounds = aggregateBounds(loadedSublevels);
            SubLevelSchematicSerializationContext placeContext = SableSchematicContextFactory.createPlaceContext(
                    loadedSublevels,
                    aggregateBounds,
                    rootBlueprintId
            );
            int[] totals = loadPlots(
                    level,
                    loadedSublevels,
                    placeContext,
                    synaxisLoad,
                    driveByWireLoad
            );

            LoadedSubLevelAlignment.alignAll(loadedSublevels, rootBlueprintId, target);
            diagnostics.run(
                    "Sable Blueprint API welds and no-contact relations",
                    () -> SableBlueprintApiCompat.restore(
                            root.getCompound(NativeBlueprintFormat.SABLE_BLUEPRINT_API_SIDECAR_TAG),
                            loadedSublevels,
                            scaleFactor
                    )
            );
            diagnostics.run(
                    "Simulated rope attachments",
                    () -> SimulatedStructureCompat.refreshRopeAttachments(level, loadedSublevels)
            );
            for (LoadedSubLevel loaded : loadedSublevels.values()) {
                diagnostics.run(
                        "block entity compatibility refresh for sublevel "
                                + loaded.subLevel().getUniqueId(),
                        () -> BlockEntityCompatibilityPipeline.refreshAfterLoad(
                                level,
                                loaded.subLevel(),
                                loaded.transform()
                        )
                );
                pipeline.teleport(
                        loaded.subLevel(),
                        loaded.subLevel().logicalPose().position(),
                        loaded.subLevel().logicalPose().orientation()
                );
                loaded.subLevel().updateLastPose();
            }
            restorePostLoadFeatures(
                    level,
                    loadedSublevels,
                    safeName,
                    root,
                    synaxisLoad,
                    driveByWireLoad,
                    notificationPlayerId,
                    diagnostics
            );

            AeronauticsStructureToolMod.LOGGER.info(
                    "Loaded structure '{}' with {} sublevels, {} block entities and {} runtime contraptions queued",
                    safeName,
                    loadedSublevels.size(),
                    totals[0],
                    totals[1]
            );

            LoadedSubLevel rootLoaded = LoadedSubLevelAlignment.requireRoot(loadedSublevels, rootBlueprintId);
            if (attachment != null) {
                diagnostics.run(
                        "blueprint auto-weld",
                        () -> attachment.attach(
                                level,
                                attachmentTarget,
                                rootLoaded.subLevel(),
                                new Vector3d(hitX, hitY, hitZ),
                                face
                        )
                );
            }
            for (LoadedSubLevel loaded : loadedSublevels.values()) {
                if (loaded.saved().collisionDisabled()) {
                    diagnostics.run(
                            "disabled collision state for sublevel "
                                    + loaded.subLevel().getUniqueId(),
                            () -> ServerServices.SUBLEVEL_COLLISION.applyDisabledCollisionState(
                                    level,
                                    loaded.subLevel()
                            )
                    );
                }
            }
            boolean restoredImmediately = ServerServices.RUNTIME_CONTRAPTION_RESTORE.restoreOrQueue(
                    level,
                    loadedSublevels.values(),
                    notificationPlayerId,
                    safeName,
                    observer
            );
            double placedTotalMass = loadedSublevels.values().stream()
                    .mapToDouble(loaded -> loaded.subLevel().getMassTracker().getMass())
                    .sum();
            BlueprintPlacementWarningNotifier.notify(
                    level,
                    notificationPlayerId,
                    diagnostics.warnings()
            );
            BlueprintPlacementWarningNotifier.notifySkippedContent(
                    level,
                    notificationPlayerId,
                    skippedContent
            );
            BlueprintPlacementWarningNotifier.notifySkippedDriveByWire(
                    level,
                    notificationPlayerId,
                    driveByWireLoad.skippedSnapshotCount(),
                    driveByWireLoad.skippedConnectionCount()
            );
            return new NativeBlueprintPlacementResult(
                    restoredImmediately,
                    rootLoaded.subLevel(),
                    loadedSublevels.size(),
                    placedTotalMass
            );
        } catch (Exception exception) {
            ServerServices.RUNTIME_CONTRAPTION_RESTORE.cancelForSublevels(
                    loadedSublevels.values().stream()
                            .map(loaded -> loaded.subLevel().getUniqueId())
                            .toList()
            );
            IOException placementFailure = exception instanceof IOException ioException
                    ? ioException
                    : new IOException("blueprint placement failed", exception);
            BlueprintPlacementRollback.rollback(container, loadedSublevels.values(), safeName, placementFailure);
            throw placementFailure;
        }
    }

    private static void allocateSublevels(
            ServerLevel level,
            ServerSubLevelContainer container,
            List<SavedSubLevelBlueprint> savedSublevels,
            Pose3d rootPlacementPose,
            Quaterniond rootOrientation,
            double scaleFactor,
            Map<UUID, LoadedSubLevel> loadedSublevels
    ) throws IOException {
        for (SavedSubLevelBlueprint saved : savedSublevels) {
            PlotVerticalLayout verticalLayout = PlotVerticalLayout.plan(
                    level,
                    saved.plotTag(),
                    saved.sourceMinBuildHeight()
            );
            Vector3d placementAnchor = new Vector3d(saved.relativePosition());
            rootPlacementPose.transformPosition(placementAnchor);

            Vector3d rotationPoint = new Vector3d(saved.relativeRotationData()).mul(scaleFactor);
            rootOrientation.transform(rotationPoint);
            Quaterniond orientation = new Quaterniond(rootOrientation).mul(saved.relativeOrientation());
            Pose3d pose = new Pose3d();
            pose.position().set(placementAnchor);
            pose.orientation().set(orientation);
            pose.rotationPoint().set(rotationPoint);
            pose.scale().set(scaleFactor, scaleFactor, scaleFactor);

            ServerSubLevel subLevel;
            try {
                subLevel = (ServerSubLevel) container.allocateNewSubLevel(pose);
            } catch (Exception exception) {
                throw new IOException("failed to allocate sublevel for print", exception);
            }
            if (!saved.name().isBlank()) {
                subLevel.setName(saved.name());
            }

            PlotBlockTransform plotTransform = PlotBlockTransform.capture(subLevel);
            Vector3d loadedLocalAnchor = plotTransform.toGlobalPosition(new Vector3d(
                    saved.localAnchor().x,
                    saved.localAnchor().y + verticalLayout.blockYShift(),
                    saved.localAnchor().z
            ));
            LoadedSubLevelAlignment.alignPositionToAnchor(subLevel, placementAnchor, loadedLocalAnchor);
            loadedSublevels.put(saved.blueprintId(), new LoadedSubLevel(
                    saved,
                    subLevel,
                    plotTransform,
                    placementAnchor,
                    verticalLayout
            ));
        }
    }

    private static BoundingBox3i aggregateBounds(Map<UUID, LoadedSubLevel> loadedSublevels) {
        BoundingBox3i aggregateBounds = new BoundingBox3i();
        boolean first = true;
        for (LoadedSubLevel loaded : loadedSublevels.values()) {
            if (first) {
                aggregateBounds.set(new BoundingBox3d(loaded.subLevel().boundingBox()));
                first = false;
            } else {
                aggregateBounds.expandTo(new BoundingBox3i(new BoundingBox3d(loaded.subLevel().boundingBox())));
            }
        }
        return aggregateBounds;
    }

    private static int[] loadPlots(
            ServerLevel level,
            Map<UUID, LoadedSubLevel> loadedSublevels,
            SubLevelSchematicSerializationContext placeContext,
            SynaxisLoadSession synaxisLoad,
            DriveByWireLoadSession driveByWireLoad
    ) throws IOException {
        int totalBlockEntities = 0;
        int totalRuntimeContraptions = 0;
        Map<UUID, UUID> ropeIdRemap = new LinkedHashMap<>();
        try {
            SubLevelSchematicSerializationContext.setCurrentContext(placeContext);
            for (LoadedSubLevel loaded : loadedSublevels.values()) {
                CompoundTag plotTag = loaded.saved().plotTag().copy();
                loaded.verticalLayout().apply(plotTag, level);
                PlotBlockEntityCompatibilityGuard.removeKnownMismatches(plotTag, "load");
                BlockEntityCompatibilityPipeline.remapForLoad(
                        plotTag,
                        loadedSublevels,
                        loaded,
                        ropeIdRemap
                );
                totalBlockEntities += PlotBlockEntityPositionRemapper.mapToAllocatedPlot(
                        plotTag,
                        loaded.subLevel()
                );
                synaxisLoad.collectPlot(plotTag);
                driveByWireLoad.preparePlot(plotTag, placeContext, loadedSublevels);
                loaded.subLevel().getPlot().load(plotTag);
                totalRuntimeContraptions += loaded.saved().runtimeContraptions().size();
            }
        } catch (Exception exception) {
            throw new IOException("plot print failed", exception);
        } finally {
            SubLevelSchematicSerializationContext.setCurrentContext(null);
        }
        return new int[]{totalBlockEntities, totalRuntimeContraptions};
    }

    private static void restorePostLoadFeatures(
            ServerLevel level,
            Map<UUID, LoadedSubLevel> loadedSublevels,
            String safeName,
            CompoundTag root,
            SynaxisLoadSession synaxisLoad,
            DriveByWireLoadSession driveByWireLoad,
            UUID notificationPlayerId,
            BlueprintPlacementDiagnostics diagnostics
    ) {
        diagnostics.run(
                "Create super glue",
                () -> CreateBlueprintCompat.restoreSuperGlue(level, loadedSublevels.values())
        );
        diagnostics.run(
                "Simulated honey glue",
                () -> SimulatedGlueBlueprintCompat.restoreHoneyGlue(level, loadedSublevels.values())
        );
        diagnostics.run(
                "Create belts",
                () -> CreateBlueprintCompat.rebuildBelts(level, loadedSublevels.values())
        );
        diagnostics.run(
                "Create kinetic refresh",
                () -> CreateBlueprintCompat.refreshKinetics(level, loadedSublevels.values())
        );
        diagnostics.run("Synaxis links", () -> {
            SynaxisLoadResult result = synaxisLoad.complete(level, loadedSublevels);
            ServerServices.SYNAXIS_CONTROLLER_WIRE_RESTORE.queue(
                    level,
                    safeName,
                    result.deferredControllerWires(),
                    notificationPlayerId
            );
        });
        diagnostics.run("DriveByWire links", () -> {
            DriveByWireLoadResult result = driveByWireLoad.complete(level, loadedSublevels);
            ServerServices.DRIVEBYWIRE_WORLD_SOURCE_REFRESH.watchSources(level, result.worldSources());
            ServerServices.DRIVEBYWIRE_RESTORE.queue(
                    level,
                    safeName,
                    result,
                    notificationPlayerId
            );
            if (result.requiresFullSync()) {
                ServerServices.DRIVEBYWIRE_FULL_SYNC.schedule(level, safeName);
            }
        });
        diagnostics.run("toolgun constraints", () -> {
            List<String> warnings = ToolgunConstraintTracker.restoreConstraintsFromSave(
                    level,
                    root.getList(ToolgunConstraintTracker.constraintsTagName(), Tag.TAG_COMPOUND),
                    loadedSublevels
            );
            for (String warning : warnings) {
                diagnostics.warn("toolgun constraints", warning);
            }
        });
    }

    private static void validatePlacementTarget(ServerLevel level, Vector3d target) throws IOException {
        if (!Double.isFinite(target.x) || !Double.isFinite(target.y) || !Double.isFinite(target.z)) {
            throw new IOException("blueprint placement target is not finite");
        }
        BlockPos targetBlock = BlockPos.containing(target.x, target.y, target.z);
        if (!level.getWorldBorder().isWithinBounds(targetBlock)) {
            throw new IOException("blueprint placement target is outside the world border: " + targetBlock);
        }
    }
}
