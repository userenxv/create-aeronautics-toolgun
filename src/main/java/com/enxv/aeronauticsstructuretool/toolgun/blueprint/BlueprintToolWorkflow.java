package com.enxv.aeronauticsstructuretool.toolgun.blueprint;

import com.enxv.aeronauticsstructuretool.ConnectionMode;
import com.enxv.aeronauticsstructuretool.blueprint.capture.NativeBlueprintCaptureService;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturedBlueprintArchive;
import com.enxv.aeronauticsstructuretool.blueprint.placement.BlueprintPlacementAttachment;
import com.enxv.aeronauticsstructuretool.blueprint.placement.CreatePhysicalBlueprintService;
import com.enxv.aeronauticsstructuretool.PlacementSnapMode;
import com.enxv.aeronauticsstructuretool.blueprint.placement.BlueprintVerticalPlacement;
import com.enxv.aeronauticsstructuretool.blueprint.placement.BlueprintPlacementTargetResolver;
import com.enxv.aeronauticsstructuretool.blueprint.placement.NativeBlueprintPlacementService;
import com.enxv.aeronauticsstructuretool.blueprint.storage.BlueprintFileRepository;
import com.enxv.aeronauticsstructuretool.core.FailureMessages;
import com.enxv.aeronauticsstructuretool.core.ModConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

public final class BlueprintToolWorkflow {
    private BlueprintToolWorkflow() {
    }

    public static BlueprintSaveResult save(ServerPlayer player, SaveBlueprintRequest request) {
        if (!(player.level() instanceof ServerLevel level)) {
            return BlueprintSaveResult.failure("server level unavailable");
        }
        try {
            CapturedBlueprintArchive saved = NativeBlueprintCaptureService.captureAtBlock(
                    level,
                    request.clickedPos(),
                    request.fileName(),
                    request.connectedSublevelProximity()
            );
            BlueprintFileRepository.write(
                    BlueprintFileRepository.serverDirectory(
                            player.server.getWorldPath(LevelResource.ROOT),
                            player.getUUID()
                    ),
                    saved.fileName(),
                    saved.fileContents()
            );
            return BlueprintSaveResult.success(saved.fileName(), saved.fileContents());
        } catch (Exception exception) {
            ModConstants.LOGGER.error(
                    "Toolgun blueprint save failed for player {} at {}",
                    player.getGameProfile().getName(),
                    request.clickedPos(),
                    exception
            );
            return BlueprintSaveResult.failure(
                    FailureMessages.describe(exception, "blueprint save failed")
            );
        }
    }

    public static BlueprintLoadResult load(ServerPlayer player, LoadBlueprintRequest request) {
        if (!(player.level() instanceof ServerLevel level)) {
            return BlueprintLoadResult.failure("server level unavailable");
        }
        try {
            BlueprintFileRepository.write(
                    BlueprintFileRepository.serverDirectory(
                            player.server.getWorldPath(LevelResource.ROOT),
                            player.getUUID()
                    ),
                    request.fileName(),
                    request.fileContents()
            );
            PlacementSnapMode snapMode = PlacementSnapMode.fromName(request.snapMode());
            BlueprintPlacementTargetResolver.Target placementTarget =
                    BlueprintPlacementTargetResolver.resolve(
                            level,
                            request.clickedPos(),
                            request.face(),
                            new Vec3(request.hitX(), request.hitY(), request.hitZ())
                    );
            var placementHit = placementTarget.hit();
            if (CreatePhysicalBlueprintService.hasCreatePhysicalLayout(request.fileContents())) {
                CreatePhysicalBlueprintService.placeAtHit(
                        player,
                        level,
                        placementTarget.clickedPos(),
                        placementTarget.face(),
                        request.fileName(),
                        request.fileContents(),
                        placementHit.x,
                        placementHit.y,
                        placementHit.z,
                        request.offsetX(),
                        request.offsetY(),
                        request.offsetZ(),
                        snapMode
                );
            } else {
                ConnectionMode connectionMode = ConnectionMode.fromName(request.connectionMode());
                BlueprintPlacementAttachment attachment = request.autoWeld()
                        ? BlueprintAutoWeldAttachment.create(
                                request.fileName(),
                                placementTarget.clickedPos(),
                                connectionMode
                        )
                        : null;
                NativeBlueprintPlacementService.place(
                        level,
                        placementTarget.clickedPos(),
                        placementTarget.face(),
                        request.fileName(),
                        request.fileContents(),
                        placementHit.x,
                        placementHit.y,
                        placementHit.z,
                        request.rotationDegrees(),
                        request.scalePercent(),
                        request.offsetX(),
                        request.offsetY(),
                        request.offsetZ(),
                        snapMode,
                        attachment,
                        BlueprintVerticalPlacement.keepAboveSurface(
                                placementTarget.face(),
                                placementHit.y
                        ),
                        player.getUUID(),
                        null
                );
            }
            return BlueprintLoadResult.success();
        } catch (Exception exception) {
            ModConstants.LOGGER.error(
                    "Toolgun blueprint load failed for player {} at {}",
                    player.getGameProfile().getName(),
                    request.clickedPos(),
                    exception
            );
            return BlueprintLoadResult.failure(
                    FailureMessages.describe(exception, "blueprint load failed")
            );
        }
    }
}
