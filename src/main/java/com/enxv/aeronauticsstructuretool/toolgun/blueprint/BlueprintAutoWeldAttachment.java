package com.enxv.aeronauticsstructuretool.toolgun.blueprint;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.ConnectionMode;
import com.enxv.aeronauticsstructuretool.blueprint.placement.BlueprintPlacementAttachment;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.ConstraintRuntimeFactory;
import com.enxv.aeronauticsstructuretool.toolgun.weld.WeldAxisGeometry;
import net.minecraft.core.BlockPos;
import org.joml.Vector3d;

import java.io.IOException;

public final class BlueprintAutoWeldAttachment {
    private BlueprintAutoWeldAttachment() {
    }

    public static BlueprintPlacementAttachment create(
            String blueprintName,
            BlockPos clickedPos,
            ConnectionMode connectionMode
    ) throws IOException {
        if (connectionMode == null) {
            throw new IOException("auto-weld connection mode is missing");
        }
        return (level, target, placedRoot, worldPoint, face) -> {
            if (target == null) {
                AeronauticsStructureToolMod.LOGGER.warn(
                        "Auto-weld was requested for blueprint '{}' at {}, but no loaded target structure was found",
                        blueprintName,
                        clickedPos
                );
                return;
            }
            Vector3d worldAxis = WeldAxisGeometry.worldAxisFromFace(face);
            ConstraintRuntimeFactory.weldAtWorldPoint(
                    level,
                    target,
                    placedRoot,
                    worldPoint,
                    worldPoint,
                    worldPoint,
                    worldAxis,
                    connectionMode
            );
        };
    }
}
