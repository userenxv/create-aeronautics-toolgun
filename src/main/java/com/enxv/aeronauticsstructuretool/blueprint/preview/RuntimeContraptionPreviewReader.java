package com.enxv.aeronauticsstructuretool.blueprint.preview;

import com.enxv.aeronauticsstructuretool.RuntimeContraptionBlueprint;
import com.enxv.aeronauticsstructuretool.blueprint.runtime.RuntimeContraptionCodec;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RuntimeContraptionPreviewReader {
    private static final String RUNTIME_CONTRAPTIONS_TAG = "runtime_contraptions";

    private RuntimeContraptionPreviewReader() {
    }

    public static List<RuntimeBlock> read(
            ListTag runtimeContraptions,
            @Nullable Level level,
            Map<BlockPos, BlockState> controllerStates
    ) throws IOException {
        if (runtimeContraptions == null || runtimeContraptions.isEmpty()) {
            return List.of();
        }
        CompoundTag wrapper = new CompoundTag();
        wrapper.put(RUNTIME_CONTRAPTIONS_TAG, runtimeContraptions.copy());
        return read(RuntimeContraptionCodec.readList(wrapper, RUNTIME_CONTRAPTIONS_TAG), level, controllerStates);
    }

    public static List<RuntimeBlock> read(
            List<RuntimeContraptionBlueprint> runtimeContraptions,
            @Nullable Level level,
            Map<BlockPos, BlockState> controllerStates
    ) throws IOException {
        if (runtimeContraptions == null || runtimeContraptions.isEmpty()) {
            return List.of();
        }
        if (level == null) {
            throw new IOException("runtime contraption preview requires a loaded world");
        }

        List<RuntimeBlock> blocks = new ArrayList<>();
        for (int i = 0; i < runtimeContraptions.size(); i++) {
            RuntimeContraptionBlueprint blueprint = runtimeContraptions.get(i);
            BlockPos controllerPos = blueprint.controllerLocalPos();
            BlockPos anchorPos = resolveAnchor(blueprint, controllerStates.get(controllerPos));
            try {
                Contraption contraption = Contraption.fromNBT(level, blueprint.contraptionTag().copy(), false);
                if (contraption == null || contraption.getBlocks() == null) {
                    throw new IOException("Create returned no blocks for runtime contraption entry " + i);
                }
                for (StructureTemplate.StructureBlockInfo blockInfo : contraption.getBlocks().values()) {
                    if (blockInfo.state() == null || blockInfo.state().isAir()) {
                        continue;
                    }
                    BlockPos blockPos = anchorPos.offset(blockInfo.pos());
                    blocks.add(new RuntimeBlock(
                            blockPos,
                            blockInfo.state(),
                            withPosition(blockInfo.nbt(), blockPos)
                    ));
                }
            } catch (IOException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IOException("failed to decode runtime contraption entry " + i, exception);
            }
        }
        return blocks;
    }

    private static BlockPos resolveAnchor(
            RuntimeContraptionBlueprint blueprint,
            @Nullable BlockState controllerState
    ) throws IOException {
        if (RuntimeContraptionCodec.CBC_PITCH_KIND.equals(blueprint.kind())) {
            return resolveCbcPitchAnchor(blueprint, controllerState);
        }
        if (!RuntimeContraptionCodec.CREATE_CONTROLLED_KIND.equals(blueprint.kind())) {
            throw new IOException("unsupported runtime contraption kind: " + blueprint.kind());
        }
        BlockPos controllerPos = blueprint.controllerLocalPos();
        if (controllerState == null || !controllerState.hasProperty(BlockStateProperties.FACING)) {
            throw new IOException("runtime contraption controller has no facing state at " + controllerPos.toShortString());
        }
        return controllerPos.relative(controllerState.getValue(BlockStateProperties.FACING));
    }

    private static BlockPos resolveCbcPitchAnchor(
            RuntimeContraptionBlueprint blueprint,
            @Nullable BlockState controllerState
    ) throws IOException {
        BlockPos controllerPos = blueprint.controllerLocalPos();
        if (controllerState != null && controllerState.hasProperty(BlockStateProperties.VERTICAL_DIRECTION)) {
            return controllerPos.relative(controllerState.getValue(BlockStateProperties.VERTICAL_DIRECTION), -2);
        }
        if (controllerState != null && controllerState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return controllerPos.relative(controllerState.getValue(BlockStateProperties.HORIZONTAL_FACING));
        }
        Direction initial = Direction.byName(blueprint.initialOrientation());
        if (blueprint.entityClassName().contains("PitchOrientedContraptionEntity")) {
            if (initial == null) {
                throw new IOException("CBC runtime contraption has invalid initial orientation");
            }
            return controllerPos.relative(initial);
        }
        throw new IOException("CBC runtime contraption controller state is unsupported at " + controllerPos.toShortString());
    }

    private static CompoundTag withPosition(@Nullable CompoundTag tag, BlockPos pos) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        CompoundTag copy = tag.copy();
        copy.putInt("x", pos.getX());
        copy.putInt("y", pos.getY());
        copy.putInt("z", pos.getZ());
        return copy;
    }

    public record RuntimeBlock(BlockPos position, BlockState state, @Nullable CompoundTag blockEntityTag) {
    }
}
