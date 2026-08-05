package com.enxv.aeronauticsstructuretool.blueprint.material;

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

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.enxv.aeronauticsstructuretool.blueprint.codec.NbtTagValidator.optionalCompoundList;
import static com.enxv.aeronauticsstructuretool.blueprint.codec.NbtTagValidator.requireCompoundList;

final class BlueprintBlockCounter {
    private BlueprintBlockCounter() {
    }

    static void addPlotBlocks(
            CompoundTag plotTag,
            int sourceMinBuildHeight,
            Map<String, Long> blockCounts
    ) throws IOException {
        for (PlotBlockDataReader.PlotBlock block : PlotBlockDataReader.read(plotTag, sourceMinBuildHeight)) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block.state().getBlock());
            if (id.getNamespace().equals("create") && id.getPath().equals("belt")) {
                switch (block.state().getValue(BeltBlock.PART)) {
                    case END, START, PULLEY : mergeBlock("create:shaft", blockCounts, "plot block " + block.blockPos().toShortString());
                }
                if (block.state().getValue(BeltBlock.PART) != BeltPart.START) continue;
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
