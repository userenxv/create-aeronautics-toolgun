package com.enxv.aeronauticsstructuretool.blueprint.runtime;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.RuntimeContraptionBlueprint;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockTransform;
import com.enxv.aeronauticsstructuretool.compat.cbc.CbcRuntimeContraptionCompat;
import com.enxv.aeronauticsstructuretool.compat.create.ControlledRuntimeContraptionCompat;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RuntimeContraptionCaptureService {
    private RuntimeContraptionCaptureService() {
    }

    public static List<RuntimeContraptionBlueprint> capture(
            ServerLevel level,
            PlotBlockTransform transform
    ) {
        Map<String, RuntimeContraptionBlueprint> blueprints = new LinkedHashMap<>();
        for (BlockEntity blockEntity : transform.findBlockEntities(level)) {
            if (!(blockEntity instanceof MechanicalBearingBlockEntity bearing)) {
                continue;
            }
            BlockPos localController = transform.toSavedLocalBlockPos(blockEntity.getBlockPos());
            RuntimeContraptionBlueprint blueprint = ControlledRuntimeContraptionCompat.capture(
                    level,
                    bearing,
                    localController
            );
            if (blueprint != null) {
                blueprints.putIfAbsent(key(blueprint), blueprint);
            }
        }

        int scannedEntities = 0;
        int supportedEntities = 0;
        for (Entity entity : transform.findEntities(level)) {
            scannedEntities++;
            if (!CbcRuntimeContraptionCompat.isPitchEntity(entity)) {
                continue;
            }
            supportedEntities++;
            BlockPos controllerPos = RuntimeContraptionEntities.controllerPos(entity);
            if (controllerPos == null) {
                throw new IllegalStateException(
                        "supported runtime contraption has no readable controller: " + entity.getType()
                );
            }
            if (!transform.containsPlotAbsolute(controllerPos)) {
                AeronauticsStructureToolMod.LOGGER.warn(
                        "Runtime contraption {} was excluded because controller {} is outside the captured plot",
                        entity.getType(),
                        controllerPos
                );
                continue;
            }
            RuntimeContraptionBlueprint blueprint = CbcRuntimeContraptionCompat.capture(
                    level,
                    entity,
                    transform.toSavedLocalBlockPos(controllerPos)
            );
            blueprints.putIfAbsent(key(blueprint), blueprint);
        }
        AeronauticsStructureToolMod.LOGGER.debug(
                "Runtime contraption capture complete: scannedEntities={} supportedEntities={} captured={}",
                scannedEntities,
                supportedEntities,
                blueprints.size()
        );
        return new ArrayList<>(blueprints.values());
    }

    private static String key(RuntimeContraptionBlueprint blueprint) {
        return blueprint.kind() + "|" + blueprint.controllerLocalPos().toShortString();
    }
}
