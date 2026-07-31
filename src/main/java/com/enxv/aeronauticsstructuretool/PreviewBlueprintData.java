package com.enxv.aeronauticsstructuretool;

import com.enxv.aeronauticsstructuretool.blueprint.codec.BlueprintArchiveCodec;
import com.enxv.aeronauticsstructuretool.blueprint.codec.NativeBlueprintReader;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockDataReader;
import com.enxv.aeronauticsstructuretool.blueprint.model.NativeBlueprintDocument;
import com.enxv.aeronauticsstructuretool.blueprint.model.SavedSubLevelBlueprint;
import com.enxv.aeronauticsstructuretool.blueprint.preview.RuntimeContraptionPreviewReader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PreviewBlueprintData {
    private static final int MAX_RENDER_SAMPLES = 8192;

    private final Quaterniond rootOrientation;
    private final Vector3d rootRotationOffset;
    private final Vector3d rootRelativePosition;
    private final List<PreviewSubLevel> subLevels;

    private PreviewBlueprintData(
            Quaterniond rootOrientation,
            Vector3d rootRotationOffset,
            Vector3d rootRelativePosition,
            List<PreviewSubLevel> subLevels
    ) {
        this.rootOrientation = new Quaterniond(rootOrientation);
        this.rootRotationOffset = new Vector3d(rootRotationOffset);
        this.rootRelativePosition = new Vector3d(rootRelativePosition);
        this.subLevels = List.copyOf(subLevels);
    }

    public Quaterniond rootOrientation() {
        return new Quaterniond(this.rootOrientation);
    }

    public List<PreviewSubLevel> subLevels() {
        return this.subLevels;
    }

    public Vector3d rootRotationOffset() {
        return new Vector3d(this.rootRotationOffset);
    }

    public Vector3d rootRelativePosition() {
        return new Vector3d(this.rootRelativePosition);
    }

    public boolean hasRenderableSamples() {
        for (PreviewSubLevel subLevel : this.subLevels) {
            if (!subLevel.sampleBlocks().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public double minimumRelativeBlockCenterY(Quaterniond extraRotation, double scaleFactor) {
        if (this.subLevels.isEmpty() || !Double.isFinite(scaleFactor) || scaleFactor <= 0.0D) {
            return Double.NaN;
        }

        Quaterniond placedRootOrientation = new Quaterniond(this.rootOrientation).mul(extraRotation).normalize();
        double minimumY = Double.POSITIVE_INFINITY;
        for (PreviewSubLevel subLevel : this.subLevels) {
            Vector3d subAnchor = new Vector3d(subLevel.relativePosition())
                    .sub(this.rootRelativePosition)
                    .mul(scaleFactor);
            placedRootOrientation.transform(subAnchor);

            Quaterniond subOrientation = new Quaterniond(placedRootOrientation).mul(subLevel.relativeOrientation());
            Vector3d boundsMin = subLevel.boundsMin();
            Vector3d boundsMax = subLevel.boundsMax();
            for (int xIndex = 0; xIndex < 2; xIndex++) {
                for (int yIndex = 0; yIndex < 2; yIndex++) {
                    for (int zIndex = 0; zIndex < 2; zIndex++) {
                        Vector3d localOffset = new Vector3d(
                                xIndex == 0 ? boundsMin.x : boundsMax.x,
                                yIndex == 0 ? boundsMin.y : boundsMax.y,
                                zIndex == 0 ? boundsMin.z : boundsMax.z
                        ).sub(subLevel.localPlotAnchor()).mul(scaleFactor);
                        subOrientation.transform(localOffset);
                        minimumY = Math.min(minimumY, subAnchor.y + localOffset.y);
                    }
                }
            }
        }
        return minimumY;
    }

    public static PreviewBlueprintData parse(byte[] bytes, Level level) throws IOException {
        return parse(BlueprintArchiveCodec.decode(bytes), level);
    }

    public static PreviewBlueprintData parse(CompoundTag root, Level level) throws IOException {
        NativeBlueprintDocument document = NativeBlueprintReader.read(root);
        SavedSubLevelBlueprint rootSublevel = document.sublevels().stream()
                .filter(saved -> saved.blueprintId().equals(document.rootBlueprintId()))
                .findFirst()
                .orElseThrow(() -> new IOException(
                        "native blueprint root sublevel entry is missing: " + document.rootBlueprintId()
                ));

        List<PreviewSubLevel> subLevels = new ArrayList<>();
        for (SavedSubLevelBlueprint saved : document.sublevels()) {
            List<PlotBlockDataReader.PlotBlock> decodedPlotBlocks = level == null
                    ? PlotBlockDataReader.readGeometry(saved.plotTag(), saved.sourceMinBuildHeight())
                    : PlotBlockDataReader.read(saved.plotTag(), saved.sourceMinBuildHeight());
            List<PreviewBlock> previewBlocks = new ArrayList<>(decodedPlotBlocks.stream()
                    .map(block -> new PreviewBlock(
                            new Vector3d(block.center()),
                            block.state(),
                            copyBlockEntityTag(block.blockEntityTag())
                    ))
                    .toList());
            previewBlocks.addAll(extractRuntimeContraptionBlocks(
                    saved.runtimeContraptions(),
                    level,
                    level == null ? Map.of() : PlotBlockDataReader.indexStates(decodedPlotBlocks)
            ));
            if (previewBlocks.isEmpty()) {
                continue;
            }
            List<Vector3d> blockCenters = previewBlocks.stream()
                    .map(block -> new Vector3d(block.center()))
                    .toList();

            subLevels.add(new PreviewSubLevel(
                    saved.relativePosition(),
                    saved.relativeRotationData(),
                    saved.relativeOrientation(),
                    saved.localAnchor(),
                    computeMinimum(blockCenters),
                    computeMaximum(blockCenters),
                    downsampleEvenly(blockCenters, MAX_RENDER_SAMPLES),
                    previewBlocks
            ));
        }
        return new PreviewBlueprintData(
                document.rootOrientation(),
                document.rootRotationOffset(),
                rootSublevel.relativePosition(),
                subLevels
        );
    }

    private static List<PreviewBlock> extractRuntimeContraptionBlocks(
            List<RuntimeContraptionBlueprint> runtimeContraptions,
            Level level,
            Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> controllerStates
    ) throws IOException {
        return RuntimeContraptionPreviewReader.read(runtimeContraptions, level, controllerStates).stream()
                .map(block -> new PreviewBlock(
                        new Vector3d(
                                block.position().getX() + 0.5D,
                                block.position().getY() + 0.5D,
                                block.position().getZ() + 0.5D
                        ),
                        block.state(),
                        copyBlockEntityTag(block.blockEntityTag())
                ))
                .toList();
    }

    private static @Nullable CompoundTag copyBlockEntityTag(@Nullable CompoundTag tag) {
        return tag == null || tag.isEmpty() ? null : tag.copy();
    }

    private static List<Vector3d> downsampleEvenly(List<Vector3d> blocks, int maxSamples) {
        if (blocks.size() <= maxSamples) {
            return blocks;
        }
        List<Vector3d> samples = new ArrayList<>(maxSamples);
        double lastIndex = blocks.size() - 1.0D;
        for (int i = 0; i < maxSamples; i++) {
            int index = (int) Math.round(i * lastIndex / Math.max(1, maxSamples - 1));
            samples.add(blocks.get(index));
        }
        return samples;
    }

    private static Vector3d computeMinimum(List<Vector3d> sampleBlocks) {
        Vector3d min = new Vector3d(sampleBlocks.getFirst());
        for (Vector3d sample : sampleBlocks) {
            min.min(sample);
        }
        return min;
    }

    private static Vector3d computeMaximum(List<Vector3d> sampleBlocks) {
        Vector3d max = new Vector3d(sampleBlocks.getFirst());
        for (Vector3d sample : sampleBlocks) {
            max.max(sample);
        }
        return max;
    }

    public record PreviewSubLevel(
            Vector3d relativePosition,
            Vector3d relativeRotationOffset,
            Quaterniond relativeOrientation,
            Vector3d localPlotAnchor,
            Vector3d boundsMin,
            Vector3d boundsMax,
            List<Vector3d> sampleBlocks,
            List<PreviewBlock> previewBlocks
    ) {
    }

    public record PreviewBlock(
            Vector3d center,
            BlockState state,
            @Nullable CompoundTag blockEntityTag
    ) {
    }
}
