package com.enxv.aeronauticsstructuretool;

import com.enxv.aeronauticsstructuretool.blueprint.preview.PortableStructurePreviewReader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.List;

public final class PortableStructurePreviewData {
    private final String name;
    private final int substructureCount;
    private final int blockCount;
    private final int width;
    private final int height;
    private final int depth;
    private final double maxSpan;
    private final double bottomY;
    private final Vector3d previewCenterOffset;
    private final List<PreviewBlock> previewBlocks;

    private PortableStructurePreviewData(
            String name,
            int substructureCount,
            int blockCount,
            int width,
            int height,
            int depth,
            double maxSpan,
            double bottomY,
            Vector3d previewCenterOffset,
            List<PreviewBlock> previewBlocks
    ) {
        this.name = name;
        this.substructureCount = substructureCount;
        this.blockCount = blockCount;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.maxSpan = maxSpan;
        this.bottomY = bottomY;
        this.previewCenterOffset = new Vector3d(previewCenterOffset);
        this.previewBlocks = List.copyOf(previewBlocks);
    }

    public static PortableStructurePreviewData fromBlueprintBytes(
            String fallbackName,
            byte[] blueprintBytes,
            @Nullable Level level
    ) throws IOException {
        return PortableStructurePreviewReader.read(fallbackName, blueprintBytes, level);
    }

    public static PortableStructurePreviewData decoded(
            String name,
            int substructureCount,
            int blockCount,
            int width,
            int height,
            int depth,
            double maxSpan,
            double bottomY,
            Vector3d previewCenterOffset,
            List<PreviewBlock> previewBlocks
    ) {
        return new PortableStructurePreviewData(
                name,
                substructureCount,
                blockCount,
                width,
                height,
                depth,
                maxSpan,
                bottomY,
                previewCenterOffset,
                previewBlocks
        );
    }

    public String name() {
        return this.name;
    }

    public int substructureCount() {
        return this.substructureCount;
    }

    public int blockCount() {
        return this.blockCount;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public int depth() {
        return this.depth;
    }

    public double maxSpan() {
        return this.maxSpan;
    }

    public double bottomY() {
        return this.bottomY;
    }

    public List<PreviewBlock> previewBlocks() {
        return this.previewBlocks;
    }

    public Vector3d previewCenterOffset() {
        return new Vector3d(this.previewCenterOffset);
    }

    public boolean hasPreview() {
        return !this.previewBlocks.isEmpty();
    }

    public record PreviewBlock(
            Vector3d position,
            BlockState state,
            Quaterniond orientation,
            CompoundTag blockEntityTag
    ) {
    }
}
