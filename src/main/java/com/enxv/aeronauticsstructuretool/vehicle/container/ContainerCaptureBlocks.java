package com.enxv.aeronauticsstructuretool.vehicle.container;

import com.enxv.aeronauticsstructuretool.PreviewBlueprintData;
import com.enxv.aeronauticsstructuretool.RuntimeContraptionBlueprint;
import com.enxv.aeronauticsstructuretool.blueprint.codec.BlueprintArchiveCodec;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockTransform;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ContainerCaptureBlocks {
    private ContainerCaptureBlocks() {
    }

    public static long countLive(ServerLevel level, List<ServerSubLevel> structures) {
        long[] count = {0};
        Set<UUID> contraptions = new HashSet<>();
        for (ServerSubLevel structure : structures) {
            for (var holder : structure.getPlot().getLoadedChunks()) {
                for (var blockEntity : holder.getChunk().getBlockEntities().values()) {
                    if (blockEntity instanceof MechanicalBearingBlockEntity bearing) {
                        count[0] += countContraption(bearing.getMovedContraption(), contraptions);
                    }
                }
                for (var section : holder.getChunk().getSections()) {
                    if (section.hasOnlyAir()) {
                        continue;
                    }
                    section.getStates().count((state, amount) -> {
                        if (!state.isAir()) {
                            count[0] += amount;
                        }
                    });
                }
            }
            PlotBlockTransform transform = PlotBlockTransform.capture(structure);
            for (Entity entity : transform.findEntities(level)) {
                var controller = RuntimeContraptionBlueprint.getControllerPos(entity);
                if (controller != null && transform.containsPlotAbsolute(controller)) {
                    count[0] += countContraption(entity, contraptions);
                }
            }
        }
        return count[0];
    }

    public static long countArchive(ServerLevel level, byte[] blueprint) throws IOException {
        return PreviewBlueprintData.parse(BlueprintArchiveCodec.decode(blueprint), level).subLevels().stream()
                .mapToLong(sub -> sub.previewBlocks().size()).sum();
    }

    private static long countContraption(Entity entity, Set<UUID> counted) {
        if (!(entity instanceof AbstractContraptionEntity contraption)
                || !RuntimeContraptionBlueprint.isSupportedRuntimeEntity(entity)
                || !counted.add(entity.getUUID()) || contraption.getContraption() == null) {
            return 0;
        }
        return contraption.getContraption().getBlocks().values().stream()
                .filter(block -> !block.state().isAir()).count();
    }
}
