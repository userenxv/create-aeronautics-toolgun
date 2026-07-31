package com.enxv.aeronauticsstructuretool.blueprint.runtime;

import com.enxv.aeronauticsstructuretool.RuntimeContraptionBlueprint;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.LoadedSubLevelCoordinates;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import com.enxv.aeronauticsstructuretool.compat.cbc.CbcRuntimeContraptionCompat;
import com.enxv.aeronauticsstructuretool.compat.create.ControlledRuntimeContraptionCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class RuntimeContraptionRestoreService {
    private RuntimeContraptionRestoreService() {
    }

    public static RuntimeContraptionRestoreResult restore(
            RuntimeContraptionBlueprint blueprint,
            ServerLevel level,
            LoadedSubLevel loaded
    ) {
        BlockPos controllerPos = LoadedSubLevelCoordinates.toGlobalBlockPos(
                loaded,
                blueprint.controllerLocalPos()
        );
        BlockEntity blockEntity = level.getBlockEntity(controllerPos);
        if (blockEntity == null) {
            return RuntimeContraptionRestoreResult.retry(
                    "controller block entity is not ready at " + controllerPos
            );
        }
        try {
            return switch (blueprint.kind()) {
                case RuntimeContraptionCodec.CREATE_CONTROLLED_KIND ->
                        ControlledRuntimeContraptionCompat.restore(blueprint, level, controllerPos, blockEntity);
                case RuntimeContraptionCodec.CBC_PITCH_KIND ->
                        CbcRuntimeContraptionCompat.restore(blueprint, level, controllerPos, blockEntity);
                default -> RuntimeContraptionRestoreResult.permanentFailure(
                        "unsupported runtime contraption kind '" + blueprint.kind() + "'"
                );
            };
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return RuntimeContraptionRestoreResult.permanentFailure(
                    "failed to restore runtime contraption kind=" + blueprint.kind()
                            + " controller=" + controllerPos,
                    exception
            );
        }
    }
}
