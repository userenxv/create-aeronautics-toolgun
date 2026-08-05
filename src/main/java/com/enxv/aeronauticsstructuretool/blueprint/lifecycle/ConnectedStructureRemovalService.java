package com.enxv.aeronauticsstructuretool.blueprint.lifecycle;

import com.enxv.aeronauticsstructuretool.RuntimeContraptionBlueprint;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockTransform;

import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.io.IOException;
import java.util.Collection;

public final class ConnectedStructureRemovalService {
    private ConnectedStructureRemovalService() {
    }

    public static void removeAt(
            ServerLevel level,
            BlockPos worldPos
    ) throws IOException {
        SubLevel containing = Sable.HELPER.getContaining(level, worldPos);
        if (!(containing instanceof ServerSubLevel rootSubLevel)) {
            throw new IOException("not a physical structure");
        }
        Collection<SubLevel> connected = SubLevelHelper.getConnectedChain(containing);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            throw new IOException("Sable sublevel container is unavailable");
        }

        purgeRuntimeContraptions(level, connected);
        SubLevelRemovalCoordinator.remove(level, container, connected);
    }

    private static void purgeRuntimeContraptions(ServerLevel level, Collection<SubLevel> subLevels) {
        for (SubLevel subLevel : subLevels) {
            PlotBlockTransform transform = PlotBlockTransform.capture(subLevel);
            for (BlockEntity blockEntity : transform.findBlockEntities(level)) {
                if (!(blockEntity instanceof MechanicalBearingBlockEntity bearing)) {
                    continue;
                }
                Entity movedContraption = bearing.getMovedContraption();
                if (movedContraption != null && movedContraption.isAlive()) {
                    movedContraption.discard();
                }
            }
            for (Entity entity : transform.findEntities(level)) {
                if (!RuntimeContraptionBlueprint.isSupportedRuntimeEntity(entity)) {
                    continue;
                }
                BlockPos controllerPos = RuntimeContraptionBlueprint.getControllerPos(entity);
                if (controllerPos != null && transform.containsPlotAbsolute(controllerPos)) {
                    entity.discard();
                }
            }
        }
    }
}
