package com.enxv.aeronauticsstructuretool.blueprint.material;

import com.copycatsplus.copycats.content.copycat.board.CopycatBoardBlock;
import com.copycatsplus.copycats.content.copycat.byte_panel.CopycatBytePanelBlock;
import com.copycatsplus.copycats.content.copycat.bytes.CopycatByteBlock;
import com.copycatsplus.copycats.content.copycat.half_layer.CopycatHalfLayerBlock;
import com.enxv.aeronauticsstructuretool.RuntimeContraptionBlueprint;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockDataReader;
import com.enxv.aeronauticsstructuretool.blueprint.runtime.RuntimeContraptionCodec;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.enxv.aeronauticsstructuretool.blueprint.codec.NbtTagValidator.optionalCompoundList;
import static com.enxv.aeronauticsstructuretool.blueprint.codec.NbtTagValidator.requireCompoundList;

final class BlueprintBlockCounter {

    private static final BooleanProperty[] COPYCAT_BOARD_STATES = {
            CopycatBoardBlock.UP,
            CopycatBoardBlock.DOWN,
            CopycatBoardBlock.EAST,
            CopycatBoardBlock.NORTH,
            CopycatBoardBlock.WEST,
            CopycatBoardBlock.SOUTH
    };

    private static final BooleanProperty[] COPYCAT_BYTE_STATES = {
            CopycatByteBlock.BOTTOM_NE, CopycatByteBlock.BOTTOM_NW,
            CopycatByteBlock.BOTTOM_SE, CopycatByteBlock.BOTTOM_SW,
            CopycatByteBlock.TOP_NE, CopycatByteBlock.TOP_NW,
            CopycatByteBlock.TOP_SE, CopycatByteBlock.TOP_SW
    };

    private static final BooleanProperty[] COPYCAT_BYTE_PANEL_STATES = {
            CopycatBytePanelBlock.BOTTOM_LEFT,
            CopycatBytePanelBlock.BOTTOM_RIGHT,
            CopycatBytePanelBlock.TOP_LEFT,
            CopycatBytePanelBlock.TOP_RIGHT
    };

    private BlueprintBlockCounter() {
    }

    static void addPlotBlocks(
            CompoundTag plotTag,
            int sourceMinBuildHeight,
            Map<String, Long> blockCounts
    ) throws IOException {
        for (PlotBlockDataReader.PlotBlock block : PlotBlockDataReader.read(plotTag, sourceMinBuildHeight)) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block.state().getBlock());
            String nameSpace = id.getNamespace();
            String path = id.getPath();
            String fullName = nameSpace.concat(":").concat(path);
            switch (fullName) {
                case "create:belt": {
                    switch (block.state().getValue(BeltBlock.PART)) {
                        case END, START, PULLEY : mergeBlock("create:shaft", blockCounts, "plot block " + block.blockPos().toShortString());
                    }
                    if (block.state().getValue(BeltBlock.PART) != BeltPart.START) continue;
                    break;
                }
                //case "create_connected:kinetic_bridge_destination": continue;
                case "minecraft:large_fern", "minecraft:rose_bush",
                     "minecraft:lilac", "minecraft:pitcher_plant",
                     "minecraft:peony", "minecraft:tall_grass": {
                    if (block.state().getValue(TallFlowerBlock.HALF) == DoubleBlockHalf.UPPER) continue;
                    break;
                }
                case "create:shaft", "create:fluid_pipe",
                     "create:cogwheel", "create:large_cogwheel",
                     "copycats:copycat_shaft", "copycats:copycat_fluid_pipe",
                     "copycats:copycat_cogwheel", "copycats:copycat_large_cogwheel": {
                    String bracket = block.blockEntityTag().getCompound("Bracket").getString("Name");
                    if (!bracket.isEmpty()) {
                        mergeBlock(bracket, blockCounts, "plot block " + block.blockPos().toShortString());
                    }
                    break;
                }
                case "copycats:copycat_slice", "copycats:copycat_vertical_slice",
                     "copycats:copycat_corner_slice", "copycats:copycat_slope_layer",
                     "copycats:copycat_layer": {
                    for(int i = 1; i < block.state().getValue(SnowLayerBlock.LAYERS); i = i+1) {
                        mergeBlock(id.toString(), blockCounts, "plot block " + block.blockPos().toShortString());
                    }
                    break;
                }
                case "copycats:copycat_half_layer", "copycats:copycat_vertical_half_layer",
                     "copycats:copycat_stacked_half_layer": {
                    int positiveLayers = block.state().getValue(CopycatHalfLayerBlock.POSITIVE_LAYERS);
                    int negativeLayers = block.state().getValue(CopycatHalfLayerBlock.NEGATIVE_LAYERS);
                    for(int i = 1; i < positiveLayers + negativeLayers; i = i+1) {
                        mergeBlock(id.toString(), blockCounts, "plot block " + block.blockPos().toShortString());
                    }
                    break;
                }
                case "copycats:copycat_board": {
                    for(BooleanProperty blockState : COPYCAT_BOARD_STATES) {
                        if (block.state().getValue(blockState)) {
                            mergeBlock(id.toString(), blockCounts, "plot block " + block.blockPos().toShortString());
                        }
                    }
                    continue;
                }
                case "copycats:copycat_byte": {
                    for(BooleanProperty blockState: COPYCAT_BYTE_STATES) {
                        if (block.state().getValue(blockState)) {
                            mergeBlock(id.toString(), blockCounts, "plot block " + block.blockPos().toShortString());
                        }
                    }
                    continue;
                }
                case "copycats:copycat_byte_panel": {
                    for(BooleanProperty blockState: COPYCAT_BYTE_PANEL_STATES) {
                        if (block.state().getValue(blockState)) {
                            mergeBlock(id.toString(), blockCounts, "plot block " + block.blockPos().toShortString());
                        }
                    }
                    continue;
                }
                default: {
                    if (fullName.contains("bed") && block.state().getValue(BedBlock.PART) == BedPart.FOOT) continue;
                    if (fullName.contains("_door") && block.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) continue;
                    if (fullName.contains("_slab") && block.state().getValue(SlabBlock.TYPE) == SlabType.DOUBLE) {
                        mergeBlock(id.toString(), blockCounts, "plot block " + block.blockPos().toShortString());
                    }
                }
            }
            mergeBlock(id.toString(), blockCounts, "plot block " + block.blockPos().toShortString());
        }
    }

    static void addRuntimeContraptionBlocks(
            ListTag runtimeContraptions,
            Map<String, Long> blockCounts
    ) throws IOException {
        if (runtimeContraptions == null || runtimeContraptions.isEmpty()) {
            return;
        }
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("runtime_contraptions", runtimeContraptions.copy());
        List<RuntimeContraptionBlueprint> blueprints = RuntimeContraptionCodec.readList(
                wrapper,
                "runtime_contraptions"
        );
        for (int i = 0; i < blueprints.size(); i++) {
            CompoundTag blocksTag = blueprints.get(i).contraptionTag().getCompound("Blocks");
            if (blocksTag.isEmpty()) {
                throw new IOException("runtime contraption " + i + " has no Blocks data");
            }
            addStructureBlocks(
                    blocksTag,
                    "Palette",
                    "BlockList",
                    "State",
                    "runtime contraption " + i,
                    blockCounts
            );
        }
    }

    static void addCreateStructureBlocks(
            CompoundTag structureTag,
            String location,
            Map<String, Long> blockCounts
    ) throws IOException {
        if (structureTag == null || structureTag.isEmpty()) {
            return;
        }
        addStructureBlocks(
                structureTag,
                "palette",
                "blocks",
                "state",
                location,
                blockCounts
        );
    }

    static String normalizeBlockId(String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return null;
        }
        String normalized = id.toString();
        return switch (normalized) {
            case "minecraft:air", "minecraft:cave_air", "minecraft:void_air" -> null;
            default -> normalized;
        };
    }

    private static void addStructureBlocks(
            CompoundTag structureTag,
            String paletteKey,
            String blocksKey,
            String stateKey,
            String location,
            Map<String, Long> blockCounts
    ) throws IOException {
        ListTag blocks = optionalCompoundList(structureTag, blocksKey, location + "." + blocksKey);
        if (blocks.isEmpty()) {
            return;
        }
        ListTag palette = requireCompoundList(
                structureTag,
                paletteKey,
                location + "." + paletteKey,
                false
        );
        if (palette.isEmpty()) {
            throw new IOException(location + " has blocks but an empty palette");
        }
        String[] paletteIds = readPaletteIds(palette, location);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            if (!block.contains(stateKey, Tag.TAG_ANY_NUMERIC)) {
                throw new IOException(location + " block " + i + " has no numeric " + stateKey);
            }
            mergePaletteEntry(paletteIds, block.getInt(stateKey), blockCounts, location + " block " + i);
        }
    }

    private static String[] readPaletteIds(ListTag palette, String location) throws IOException {
        String[] paletteIds = new String[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag entry = palette.getCompound(i);
            if (!entry.contains("Name", Tag.TAG_STRING) || entry.getString("Name").isBlank()) {
                throw new IOException(location + " palette entry " + i + " has no block name");
            }
            String rawId = entry.getString("Name");
            if (ResourceLocation.tryParse(rawId) == null) {
                throw new IOException(location + " palette entry " + i + " has an invalid block name: " + rawId);
            }
            String normalized = normalizeBlockId(rawId);
            paletteIds[i] = normalized;
        }
        return paletteIds;
    }

    private static void mergePaletteEntry(
            String[] paletteIds,
            int paletteIndex,
            Map<String, Long> blockCounts,
            String location
    ) throws IOException {
        if (paletteIndex < 0 || paletteIndex >= paletteIds.length) {
            throw new IOException(location + " references invalid palette index " + paletteIndex);
        }
        String blockId = paletteIds[paletteIndex];
        if (blockId != null) {
            mergeBlock(blockId, blockCounts, location);
        }
    }

    private static void mergeBlock(
            String blockId,
            Map<String, Long> blockCounts,
            String location
    ) throws IOException {
        try {
            blockCounts.merge(blockId, 1L, Math::addExact);
        } catch (ArithmeticException exception) {
            throw new IOException("block count overflow at " + location, exception);
        }
    }

}
