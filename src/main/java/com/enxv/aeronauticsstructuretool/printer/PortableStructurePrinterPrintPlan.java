package com.enxv.aeronauticsstructuretool.printer;

import com.enxv.aeronauticsstructuretool.PortableStructurePreviewData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

final class PortableStructurePrinterPrintPlan {
    private PortableStructurePrinterPrintPlan() {
    }

    static List<PrintTarget> createTargets(ServerLevel level, BlockPos printerPos, byte[] blueprintBytes) throws IOException {
        PortableStructurePreviewData preview = PortableStructurePreviewData.fromBlueprintBytes("printer", blueprintBytes, level);
        if (preview.previewBlocks().isEmpty()) {
            return List.of();
        }

        double minimumCenteredBlockY = preview.previewBlocks().stream()
                .mapToDouble(block -> block.position().y)
                .min()
                .orElse(0.0D);
        Vector3d centeredTarget = PortableStructurePrinterPlacement.centeredPreviewTarget(
                printerPos,
                minimumCenteredBlockY
        );
        Vec3 baseOffset = new Vec3(centeredTarget.x, centeredTarget.y, centeredTarget.z);

        List<PrintTarget> targets = new ArrayList<>(preview.previewBlocks().size());
        for (PortableStructurePreviewData.PreviewBlock block : preview.previewBlocks()) {
            Vector3d position = new Vector3d(block.position());
            targets.add(new PrintTarget(
                    baseOffset.add(position.x, position.y, position.z),
                    new Quaterniond(block.orientation())
            ));
        }

        long seed = 31L * printerPos.asLong() + java.util.Arrays.hashCode(blueprintBytes);
        Collections.shuffle(targets, new Random(seed));
        // The plan is never mutated after shuffling; avoid copying the entire
        // target array a second time for large blueprints.
        return Collections.unmodifiableList(targets);
    }

    record PrintTarget(Vec3 position, Quaterniond orientation) {
    }
}
