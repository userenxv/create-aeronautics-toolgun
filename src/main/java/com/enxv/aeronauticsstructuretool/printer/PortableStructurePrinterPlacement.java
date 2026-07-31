package com.enxv.aeronauticsstructuretool.printer;

import com.enxv.aeronauticsstructuretool.blueprint.placement.CreatePhysicalBlueprintService;
import com.enxv.aeronauticsstructuretool.PlacementSnapMode;
import com.enxv.aeronauticsstructuretool.PortableStructurePreviewData;
import com.enxv.aeronauticsstructuretool.blueprint.placement.BlueprintVerticalPlacement;
import com.enxv.aeronauticsstructuretool.blueprint.placement.NativeBlueprintPlacementService;
import com.enxv.aeronauticsstructuretool.blueprint.runtime.BlueprintPlacementObserver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.UUID;

final class PortableStructurePrinterPlacement {
    private static final double MINIMUM_BLOCK_CENTER_OFFSET = 3.0D;

    private PortableStructurePrinterPlacement() {
    }

    static double desiredMinimumBlockCenterY(BlockPos printerPos) {
        return printerPos.getY() + MINIMUM_BLOCK_CENTER_OFFSET;
    }

    static BlueprintVerticalPlacement nativePlacement(BlockPos printerPos, double minimumRelativeBlockCenterY) {
        return BlueprintVerticalPlacement.alignMinimumCenter(
                desiredMinimumBlockCenterY(printerPos),
                minimumRelativeBlockCenterY
        );
    }

    static Vector3d centeredPreviewTarget(BlockPos printerPos, double minimumCenteredBlockY) {
        double desiredMinimumY = desiredMinimumBlockCenterY(printerPos);
        Vector3d nominalCenter = new Vector3d(
                printerPos.getX() + 0.5D,
                desiredMinimumY,
                printerPos.getZ() + 0.5D
        );
        return BlueprintVerticalPlacement
                .alignMinimumCenter(desiredMinimumY, minimumCenteredBlockY)
                .apply(nominalCenter, Double.NaN);
    }

    static BlueprintFormat placeBlueprint(
            ServerLevel level,
            BlockPos printerPos,
            ServerPlayer createPlacementPlayer,
            String blueprintName,
            byte[] blueprintBytes,
            UUID notificationPlayerId,
            BlueprintPlacementObserver observer
    ) throws IOException {
        if (CreatePhysicalBlueprintService.hasCreatePhysicalLayout(blueprintBytes)) {
            if (createPlacementPlayer == null) {
                throw new IOException(
                        "portable printer owner must be online for Create physical placement"
                );
            }
            CreatePhysicalBlueprintService.placeAlignedToMinimum(
                    createPlacementPlayer,
                    level,
                    blueprintName,
                    blueprintBytes,
                    printerPos.getX() + 0.5D,
                    printerPos.getZ() + 0.5D,
                    desiredMinimumBlockCenterY(printerPos)
            );
            return BlueprintFormat.CREATE_PHYSICAL;
        }

        double previewBottomY = PortableStructurePreviewData
                .fromBlueprintBytes(blueprintName, blueprintBytes, level)
                .bottomY();
        NativeBlueprintPlacementService.place(
                level,
                printerPos,
                Direction.UP,
                blueprintName,
                blueprintBytes,
                printerPos.getX() + 0.5D,
                printerPos.getY() + 1.0D,
                printerPos.getZ() + 0.5D,
                0,
                100,
                0,
                0,
                0,
                PlacementSnapMode.HIT,
                null,
                nativePlacement(printerPos, previewBottomY),
                notificationPlayerId,
                observer
        );
        return BlueprintFormat.NATIVE;
    }

    enum BlueprintFormat {
        NATIVE,
        CREATE_PHYSICAL
    }
}
