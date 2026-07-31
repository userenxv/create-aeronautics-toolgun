package com.enxv.aeronauticsstructuretool;

import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockTransform;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import com.enxv.aeronauticsstructuretool.blueprint.runtime.RuntimeContraptionCaptureService;
import com.enxv.aeronauticsstructuretool.blueprint.runtime.RuntimeContraptionCodec;
import com.enxv.aeronauticsstructuretool.blueprint.runtime.RuntimeContraptionEntities;
import com.enxv.aeronauticsstructuretool.blueprint.runtime.RuntimeContraptionRestoreResult;
import com.enxv.aeronauticsstructuretool.blueprint.runtime.RuntimeContraptionRestoreService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record RuntimeContraptionBlueprint(
        String kind,
        BlockPos controllerLocalPos,
        CompoundTag contraptionTag,
        String entityClassName,
        float angle,
        float yaw,
        float pitch,
        String initialOrientation,
        Map<String, Long> materialItemCounts
) {
    public RuntimeContraptionBlueprint {
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("runtime contraption kind is empty");
        }
        controllerLocalPos = Objects.requireNonNull(controllerLocalPos, "controllerLocalPos");
        if (contraptionTag == null || contraptionTag.isEmpty()) {
            throw new IllegalArgumentException("runtime contraption data is empty");
        }
        contraptionTag = contraptionTag.copy();
        entityClassName = Objects.requireNonNullElse(entityClassName, "");
        initialOrientation = Objects.requireNonNullElse(initialOrientation, "");
        if (!(Float.isFinite(angle) && Float.isFinite(yaw) && Float.isFinite(pitch))) {
            throw new IllegalArgumentException("runtime contraption angles must be finite");
        }
        materialItemCounts = materialItemCounts == null ? Map.of() : Map.copyOf(materialItemCounts);
    }

    public static ListTag writeList(List<RuntimeContraptionBlueprint> blueprints) {
        return RuntimeContraptionCodec.writeList(blueprints);
    }

    static List<RuntimeContraptionBlueprint> readList(CompoundTag root, String key) throws IOException {
        return RuntimeContraptionCodec.readList(root, key);
    }

    public static List<RuntimeContraptionBlueprint> capture(ServerLevel level, PlotBlockTransform transform) {
        return RuntimeContraptionCaptureService.capture(level, transform);
    }

    RuntimeContraptionRestoreResult restore(ServerLevel level, LoadedSubLevel loaded) {
        return RuntimeContraptionRestoreService.restore(this, level, loaded);
    }

    public static BlockPos getControllerPos(Entity entity) {
        return RuntimeContraptionEntities.controllerPos(entity);
    }

    public static boolean isSupportedRuntimeEntity(Entity entity) {
        return RuntimeContraptionEntities.isSupported(entity);
    }

    static int purgeEntitiesForController(ServerLevel level, BlockPos controllerPos) {
        return RuntimeContraptionEntities.purgeForController(level, controllerPos);
    }
}
