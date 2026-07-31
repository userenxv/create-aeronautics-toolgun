package com.enxv.aeronauticsstructuretool.blueprint.preview;

import com.enxv.aeronauticsstructuretool.PortableStructurePreviewData;
import com.enxv.aeronauticsstructuretool.RuntimeContraptionBlueprint;
import com.enxv.aeronauticsstructuretool.blueprint.codec.BlueprintArchiveCodec;
import com.enxv.aeronauticsstructuretool.blueprint.codec.NativeBlueprintFormat;
import com.enxv.aeronauticsstructuretool.blueprint.codec.NativeBlueprintReader;
import com.enxv.aeronauticsstructuretool.blueprint.codec.NbtTransformCodec;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockDataReader;
import com.enxv.aeronauticsstructuretool.blueprint.model.NativeBlueprintDocument;
import com.enxv.aeronauticsstructuretool.blueprint.model.SavedSubLevelBlueprint;
import com.enxv.aeronauticsstructuretool.blueprint.security.MissingRegistryContentSanitizer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PortableStructurePreviewReader {
    private PortableStructurePreviewReader() {
    }

    public static PortableStructurePreviewData read(
            String fallbackName,
            byte[] blueprintBytes,
            @Nullable Level level
    ) throws IOException {
        CompoundTag root = BlueprintArchiveCodec.decodeCompressedOrRaw(blueprintBytes);
        if (root.isEmpty()) {
            throw new IOException("blueprint root is empty");
        }
        if (root.contains(NativeBlueprintFormat.FORMAT_TAG)
                || root.contains(NativeBlueprintFormat.SUBLEVELS_TAG, Tag.TAG_LIST)) {
            MissingRegistryContentSanitizer.sanitizeNative(root);
            return readNative(fallbackName, root, level);
        }
        if (root.contains("sub_levels", Tag.TAG_LIST)) {
            MissingRegistryContentSanitizer.sanitizeCreatePhysical(root);
            return readCreatePhysical(fallbackName, root);
        }
        throw new IOException("unsupported blueprint format");
    }

    private static PortableStructurePreviewData readNative(
            String fallbackName,
            CompoundTag root,
            @Nullable Level level
    ) throws IOException {
        NativeBlueprintDocument document = NativeBlueprintReader.read(root);
        SavedSubLevelBlueprint rootSublevel = document.sublevels().stream()
                .filter(saved -> saved.blueprintId().equals(document.rootBlueprintId()))
                .findFirst()
                .orElseThrow(() -> new IOException(
                        "native blueprint root sublevel entry is missing: " + document.rootBlueprintId()
                ));

        List<RawSublevel> rawSublevels = new ArrayList<>();
        for (SavedSubLevelBlueprint saved : document.sublevels()) {
            List<PlotBlockDataReader.PlotBlock> decodedPlotBlocks = PlotBlockDataReader.read(
                    saved.plotTag(),
                    saved.sourceMinBuildHeight()
            );
            List<PortableStructurePreviewData.PreviewBlock> sublevelBlocks = toPreviewBlocks(decodedPlotBlocks);
            sublevelBlocks.addAll(readRuntimeContraptionBlocks(
                    saved.runtimeContraptions(),
                    level,
                    PlotBlockDataReader.indexStates(decodedPlotBlocks)
            ));
            if (!sublevelBlocks.isEmpty()) {
                rawSublevels.add(new RawSublevel(
                        saved.relativePosition(),
                        saved.relativeOrientation(),
                        saved.localAnchor(),
                        sublevelBlocks
                ));
            }
        }
        return finalizePreview(
                resolveName(root, fallbackName),
                rawSublevels,
                document.rootOrientation(),
                rootSublevel.relativePosition()
        );
    }

    private static PortableStructurePreviewData readCreatePhysical(
            String fallbackName,
            CompoundTag root
    ) throws IOException {
        List<RawSublevel> rawSublevels = new ArrayList<>();
        List<PortableStructurePreviewData.PreviewBlock> rootBlocks = readStructureTemplateBlocks(root, "root");
        if (!rootBlocks.isEmpty()) {
            rawSublevels.add(new RawSublevel(
                    new Vector3d(),
                    new Quaterniond(),
                    computeCenter(rootBlocks),
                    rootBlocks
            ));
        }

        ListTag subLevels = root.getList("sub_levels", Tag.TAG_COMPOUND);
        for (int i = 0; i < subLevels.size(); i++) {
            CompoundTag subLevel = subLevels.getCompound(i);
            List<PortableStructurePreviewData.PreviewBlock> sublevelBlocks = readStructureTemplateBlocks(
                    subLevel,
                    "sub_levels[" + i + "]"
            );
            if (sublevelBlocks.isEmpty()) {
                continue;
            }
            rawSublevels.add(new RawSublevel(
                    readOptionalVector(subLevel.getCompound("position"), "sub_levels[" + i + "].position"),
                    readOptionalQuaternion(
                            subLevel.getCompound("orientation"),
                            "sub_levels[" + i + "].orientation"
                    ),
                    computeCenter(sublevelBlocks),
                    sublevelBlocks
            ));
        }

        return finalizePreview(
                resolveName(root, fallbackName),
                rawSublevels,
                new Quaterniond(),
                new Vector3d()
        );
    }

    private static PortableStructurePreviewData finalizePreview(
            String name,
            List<RawSublevel> rawSublevels,
            Quaterniond rootOrientation,
            Vector3d rootRelativePosition
    ) {
        if (rawSublevels.isEmpty()) {
            return empty(name);
        }

        int blockCount = rawSublevels.stream().mapToInt(sublevel -> sublevel.blocks().size()).sum();
        List<PortableStructurePreviewData.PreviewBlock> transformedBlocks = new ArrayList<>(blockCount);
        Vector3d min = null;
        Vector3d max = null;
        for (RawSublevel sublevel : rawSublevels) {
            Vector3d subAnchor = new Vector3d(sublevel.relativePosition()).sub(rootRelativePosition);
            rootOrientation.transform(subAnchor);
            Quaterniond subOrientation = new Quaterniond(rootOrientation).mul(sublevel.relativeOrientation());
            for (PortableStructurePreviewData.PreviewBlock block : sublevel.blocks()) {
                Vector3d localOffset = new Vector3d(block.position()).sub(sublevel.localAnchor());
                subOrientation.transform(localOffset);
                Vector3d previewPosition = subAnchor.add(localOffset, new Vector3d());
                transformedBlocks.add(new PortableStructurePreviewData.PreviewBlock(
                        previewPosition,
                        block.state(),
                        new Quaterniond(subOrientation),
                        copyBlockEntityTag(block.blockEntityTag())
                ));
                if (min == null) {
                    min = new Vector3d(previewPosition);
                    max = new Vector3d(previewPosition);
                } else {
                    min.min(previewPosition);
                    max.max(previewPosition);
                }
            }
        }

        if (min == null || max == null) {
            return empty(name);
        }
        int width = Math.max(1, (int) Math.round(max.x - min.x + 1.0D));
        int height = Math.max(1, (int) Math.round(max.y - min.y + 1.0D));
        int depth = Math.max(1, (int) Math.round(max.z - min.z + 1.0D));
        Vector3d overallCenter = min.add(max, new Vector3d()).mul(0.5D);
        List<PortableStructurePreviewData.PreviewBlock> centeredBlocks = new ArrayList<>(transformedBlocks.size());
        for (PortableStructurePreviewData.PreviewBlock block : transformedBlocks) {
            centeredBlocks.add(new PortableStructurePreviewData.PreviewBlock(
                    new Vector3d(block.position()).sub(overallCenter),
                    block.state(),
                    block.orientation(),
                    copyBlockEntityTag(block.blockEntityTag())
            ));
        }
        return PortableStructurePreviewData.decoded(
                name,
                rawSublevels.size(),
                centeredBlocks.size(),
                width,
                height,
                depth,
                Math.max(width, Math.max(height, depth)),
                min.y,
                overallCenter,
                centeredBlocks
        );
    }

    private static PortableStructurePreviewData empty(String name) {
        return PortableStructurePreviewData.decoded(
                name,
                0,
                0,
                0,
                0,
                0,
                1.0D,
                0.0D,
                new Vector3d(),
                List.of()
        );
    }

    private static List<PortableStructurePreviewData.PreviewBlock> toPreviewBlocks(
            List<PlotBlockDataReader.PlotBlock> plotBlocks
    ) {
        List<PortableStructurePreviewData.PreviewBlock> blocks = new ArrayList<>(plotBlocks.size());
        for (PlotBlockDataReader.PlotBlock block : plotBlocks) {
            blocks.add(new PortableStructurePreviewData.PreviewBlock(
                    new Vector3d(block.center()),
                    block.state(),
                    new Quaterniond(),
                    copyBlockEntityTag(block.blockEntityTag())
            ));
        }
        return blocks;
    }

    private static List<PortableStructurePreviewData.PreviewBlock> readStructureTemplateBlocks(
            CompoundTag structureTag,
            String location
    ) throws IOException {
        if (structureTag == null || structureTag.isEmpty()) {
            return List.of();
        }
        ListTag palette = structureTag.getList("palette", Tag.TAG_COMPOUND);
        ListTag blockList = structureTag.getList("blocks", Tag.TAG_COMPOUND);
        if (blockList.isEmpty()) {
            return List.of();
        }
        if (palette.isEmpty()) {
            throw new IOException(location + " has blocks but no palette");
        }

        BlockState[] states = new BlockState[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag paletteEntry = palette.getCompound(i);
            if (PlotBlockDataReader.isRenderablePaletteEntry(paletteEntry)) {
                states[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), paletteEntry);
            }
        }

        List<PortableStructurePreviewData.PreviewBlock> blocks = new ArrayList<>(blockList.size());
        for (int i = 0; i < blockList.size(); i++) {
            CompoundTag blockTag = blockList.getCompound(i);
            if (!blockTag.contains("state", Tag.TAG_ANY_NUMERIC)) {
                throw new IOException(location + ".blocks[" + i + "] is missing state");
            }
            int stateIndex = blockTag.getInt("state");
            if (stateIndex < 0 || stateIndex >= states.length) {
                throw new IOException(location + ".blocks[" + i + "] has invalid palette index " + stateIndex);
            }
            if (states[stateIndex] == null) {
                continue;
            }
            if (!blockTag.contains("pos", Tag.TAG_LIST)) {
                throw new IOException(location + ".blocks[" + i + "] is missing position");
            }
            ListTag posTag = blockTag.getList("pos", Tag.TAG_INT);
            if (posTag.size() != 3) {
                throw new IOException(location + ".blocks[" + i + "] has invalid position");
            }
            BlockPos blockPos = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
            blocks.add(new PortableStructurePreviewData.PreviewBlock(
                    new Vector3d(
                            blockPos.getX() + 0.5D,
                            blockPos.getY() + 0.5D,
                            blockPos.getZ() + 0.5D
                    ),
                    states[stateIndex],
                    new Quaterniond(),
                    copyBlockEntityTag(blockTag.getCompound("nbt"))
            ));
        }
        return blocks;
    }

    private static List<PortableStructurePreviewData.PreviewBlock> readRuntimeContraptionBlocks(
            List<RuntimeContraptionBlueprint> runtimeContraptions,
            @Nullable Level level,
            Map<BlockPos, BlockState> controllerStates
    ) throws IOException {
        return RuntimeContraptionPreviewReader.read(runtimeContraptions, level, controllerStates).stream()
                .map(block -> new PortableStructurePreviewData.PreviewBlock(
                        new Vector3d(
                                block.position().getX() + 0.5D,
                                block.position().getY() + 0.5D,
                                block.position().getZ() + 0.5D
                        ),
                        block.state(),
                        new Quaterniond(),
                        block.blockEntityTag()
                ))
                .toList();
    }

    private static Vector3d readOptionalVector(CompoundTag tag, String field) throws IOException {
        if (tag == null || tag.isEmpty()) {
            return new Vector3d();
        }
        try {
            return NbtTransformCodec.readVector(tag, field);
        } catch (IllegalArgumentException exception) {
            throw new IOException("invalid " + field + ": " + exception.getMessage(), exception);
        }
    }

    private static Quaterniond readOptionalQuaternion(CompoundTag tag, String field) throws IOException {
        if (tag == null || tag.isEmpty()) {
            return new Quaterniond();
        }
        try {
            return NbtTransformCodec.readQuaternion(tag, field);
        } catch (IllegalArgumentException exception) {
            throw new IOException("invalid " + field + ": " + exception.getMessage(), exception);
        }
    }

    private static CompoundTag copyBlockEntityTag(CompoundTag blockEntityTag) {
        return blockEntityTag == null || blockEntityTag.isEmpty() ? null : blockEntityTag.copy();
    }

    private static String resolveName(CompoundTag root, String fallbackName) {
        String rootName = root.getString("name");
        if (!rootName.isBlank()) {
            return rootName;
        }
        if (fallbackName != null && !fallbackName.isBlank()) {
            return fallbackName;
        }
        return "vehicle";
    }

    private static Vector3d computeCenter(List<PortableStructurePreviewData.PreviewBlock> blocks) {
        Vector3d min = new Vector3d(blocks.getFirst().position());
        Vector3d max = new Vector3d(blocks.getFirst().position());
        for (PortableStructurePreviewData.PreviewBlock block : blocks) {
            min.min(block.position());
            max.max(block.position());
        }
        return min.add(max, new Vector3d()).mul(0.5D);
    }

    private record RawSublevel(
            Vector3d relativePosition,
            Quaterniond relativeOrientation,
            Vector3d localAnchor,
            List<PortableStructurePreviewData.PreviewBlock> blocks
    ) {
    }
}
