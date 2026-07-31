package com.enxv.aeronauticsstructuretool.blueprint.geometry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PlotBlockDataReader {
    private static final String CHUNKS_TAG = "chunks";
    private static final String SECTIONS_TAG = "sections";
    private static final String BLOCK_STATES_TAG = "block_states";
    private static final String PALETTE_TAG = "palette";
    private static final String DATA_TAG = "data";
    private static final String BLOCK_ENTITIES_TAG = "block_entities";

    private PlotBlockDataReader() {
    }

    public static List<PlotBlock> read(CompoundTag plotTag, int sourceMinBuildHeight) throws IOException {
        return read(plotTag, sourceMinBuildHeight, true);
    }

    public static List<PlotBlock> readGeometry(CompoundTag plotTag, int sourceMinBuildHeight) throws IOException {
        return read(plotTag, sourceMinBuildHeight, false);
    }

    private static List<PlotBlock> read(CompoundTag plotTag, int sourceMinBuildHeight, boolean decodeBlockStates) throws IOException {
        List<PlotBlock> blocks = new ArrayList<>();
        if (plotTag == null || !plotTag.contains(CHUNKS_TAG, Tag.TAG_COMPOUND)) {
            throw new IOException("plot chunks are missing");
        }
        CompoundTag chunks = plotTag.getCompound(CHUNKS_TAG);
        if (chunks.isEmpty()) {
            throw new IOException("plot has no chunks");
        }
        int sourceMinSection = SectionPos.blockToSectionCoord(sourceMinBuildHeight);
        for (String chunkKey : chunks.getAllKeys()) {
            long packedChunkPos = parseLong(chunkKey, "plot chunk key");
            if (!chunks.contains(chunkKey, Tag.TAG_COMPOUND)) {
                throw new IOException("plot chunk '" + chunkKey + "' is not a compound");
            }
            CompoundTag chunkTag = chunks.getCompound(chunkKey);
            Map<BlockPos, CompoundTag> blockEntities = readBlockEntities(chunkTag);
            if (chunkTag.contains(SECTIONS_TAG) && !chunkTag.contains(SECTIONS_TAG, Tag.TAG_COMPOUND)) {
                throw new IOException("plot chunk '" + chunkKey + "' sections are not a compound");
            }
            CompoundTag sections = chunkTag.getCompound(SECTIONS_TAG);
            int baseX = ChunkPos.getX(packedChunkPos) << 4;
            int baseZ = ChunkPos.getZ(packedChunkPos) << 4;
            for (String sectionKey : sections.getAllKeys()) {
                int sectionIndex = parseInt(sectionKey, "plot section key");
                if (!sections.contains(sectionKey, Tag.TAG_COMPOUND)) {
                    throw new IOException("plot section '" + sectionKey + "' is not a compound");
                }
                CompoundTag sectionTag = sections.getCompound(sectionKey);
                if (!sectionTag.contains(BLOCK_STATES_TAG)) {
                    continue;
                }
                if (!sectionTag.contains(BLOCK_STATES_TAG, Tag.TAG_COMPOUND)) {
                    throw new IOException("plot section '" + sectionKey + "' block states are not a compound");
                }
                CompoundTag blockStates = sectionTag.getCompound(BLOCK_STATES_TAG);
                if (!blockStates.contains(PALETTE_TAG, Tag.TAG_LIST)) {
                    throw new IOException("plot section '" + sectionKey + "' block-state palette is missing");
                }
                PaletteEntry[] palette = readPalette(
                        blockStates.getList(PALETTE_TAG, Tag.TAG_COMPOUND),
                        decodeBlockStates,
                        sectionKey
                );
                if (palette.length == 0) {
                    throw new IOException("plot section '" + sectionKey + "' has an empty block-state palette");
                }

                long[] data = blockStates.getLongArray(DATA_TAG);
                int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(Math.max(0, palette.length - 1)));
                int valuesPerLong = Math.max(1, 64 / bits);
                int requiredLongs = (4096 + valuesPerLong - 1) / valuesPerLong;
                if (palette.length > 1 && data.length < requiredLongs) {
                    throw new IOException(
                            "plot section '" + sectionKey + "' block-state data is truncated: expected at least "
                                    + requiredLongs + " longs, found " + data.length
                    );
                }
                long mask = (1L << bits) - 1L;
                int sectionY = sourceMinSection + sectionIndex;
                for (int index = 0; index < 4096; index++) {
                    int paletteIndex = paletteIndex(data, index, valuesPerLong, bits, mask);
                    if (paletteIndex < 0 || paletteIndex >= palette.length) {
                        throw new IOException(
                                "plot section '" + sectionKey + "' references invalid palette index " + paletteIndex
                        );
                    }
                    if (!palette[paletteIndex].renderable()) {
                        continue;
                    }
                    int localX = index & 15;
                    int localZ = (index >> 4) & 15;
                    int localY = (index >> 8) & 15;
                    BlockPos blockPos = new BlockPos(
                            baseX + localX,
                            (sectionY << 4) + localY,
                            baseZ + localZ
                    );
                    CompoundTag blockEntityTag = blockEntities.get(blockPos);
                    blocks.add(new PlotBlock(
                            blockPos,
                            new Vector3d(blockPos.getX() + 0.5D, blockPos.getY() + 0.5D, blockPos.getZ() + 0.5D),
                            palette[paletteIndex].state(),
                            blockEntityTag == null ? null : blockEntityTag.copy()
                    ));
                }
            }
        }
        return List.copyOf(blocks);
    }

    public static Vector3d plotChunkCenter(CompoundTag plotTag, double anchorY) throws IOException {
        CompoundTag chunks = plotTag.getCompound(CHUNKS_TAG);
        if (chunks.isEmpty()) {
            throw new IOException("plot has no chunks");
        }

        boolean foundChunk = false;
        int minChunkX = 0;
        int minChunkZ = 0;
        int maxChunkX = 0;
        int maxChunkZ = 0;
        for (String chunkKey : chunks.getAllKeys()) {
            long packedChunkPos = parseLong(chunkKey, "plot chunk key");
            int chunkX = ChunkPos.getX(packedChunkPos);
            int chunkZ = ChunkPos.getZ(packedChunkPos);
            if (!foundChunk) {
                minChunkX = maxChunkX = chunkX;
                minChunkZ = maxChunkZ = chunkZ;
                foundChunk = true;
            } else {
                minChunkX = Math.min(minChunkX, chunkX);
                minChunkZ = Math.min(minChunkZ, chunkZ);
                maxChunkX = Math.max(maxChunkX, chunkX);
                maxChunkZ = Math.max(maxChunkZ, chunkZ);
            }
        }

        return new Vector3d(
                (minChunkX + maxChunkX + 1.0D) * 8.0D,
                anchorY,
                (minChunkZ + maxChunkZ + 1.0D) * 8.0D
        );
    }

    public static Map<BlockPos, BlockState> indexStates(List<PlotBlock> blocks) {
        Map<BlockPos, BlockState> states = new HashMap<>();
        for (PlotBlock block : blocks) {
            states.put(block.blockPos(), block.state());
        }
        return Map.copyOf(states);
    }

    private static PaletteEntry[] readPalette(
            ListTag palette,
            boolean decodeBlockStates,
            String sectionKey
    ) throws IOException {
        PaletteEntry[] entries = new PaletteEntry[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag entry = palette.getCompound(i);
            if (!entry.contains("Name", Tag.TAG_STRING) || entry.getString("Name").isBlank()) {
                throw new IOException(
                        "plot section '" + sectionKey + "' palette entry " + i + " has no block name"
                );
            }
            String rawId = entry.getString("Name");
            ResourceLocation blockId = ResourceLocation.tryParse(rawId);
            if (blockId == null) {
                throw new IOException(
                        "plot section '" + sectionKey + "' palette entry " + i
                                + " has an invalid block name: " + rawId
                );
            }
            boolean renderable = isRenderablePaletteEntry(entry)
                    && (!decodeBlockStates || BuiltInRegistries.BLOCK.containsKey(blockId));
            BlockState state = renderable && decodeBlockStates
                    ? NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), entry)
                    : null;
            entries[i] = new PaletteEntry(renderable, state);
        }
        return entries;
    }

    private static Map<BlockPos, CompoundTag> readBlockEntities(CompoundTag chunkTag) throws IOException {
        Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();
        if (chunkTag.contains(BLOCK_ENTITIES_TAG) && !chunkTag.contains(BLOCK_ENTITIES_TAG, Tag.TAG_LIST)) {
            throw new IOException("plot block_entities entry is not a list");
        }
        ListTag list = chunkTag.getList(BLOCK_ENTITIES_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            if (!tag.contains("x", Tag.TAG_INT)
                    || !tag.contains("y", Tag.TAG_INT)
                    || !tag.contains("z", Tag.TAG_INT)) {
                throw new IOException("plot block entity " + i + " has no integer position");
            }
            BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
            if (blockEntities.put(pos, tag.copy()) != null) {
                throw new IOException("plot contains duplicate block entities at " + pos.toShortString());
            }
        }
        return blockEntities;
    }

    private static int paletteIndex(long[] data, int index, int valuesPerLong, int bits, long mask) {
        if (data.length == 0) {
            return 0;
        }
        int arrayIndex = index / valuesPerLong;
        int bitIndex = (index % valuesPerLong) * bits;
        return (int) ((data[arrayIndex] >>> bitIndex) & mask);
    }

    public static boolean isRenderablePaletteEntry(CompoundTag entry) {
        if (entry == null || entry.isEmpty()) {
            return false;
        }
        String blockName = entry.getString("Name");
        return !blockName.isBlank()
                && !"minecraft:air".equals(blockName)
                && !"minecraft:cave_air".equals(blockName)
                && !"minecraft:void_air".equals(blockName);
    }

    private static int parseInt(String value, String label) throws IOException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IOException("invalid " + label + ": " + value, exception);
        }
    }

    private static long parseLong(String value, String label) throws IOException {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IOException("invalid " + label + ": " + value, exception);
        }
    }

    public record PlotBlock(
            BlockPos blockPos,
            Vector3d center,
            BlockState state,
            CompoundTag blockEntityTag
    ) {
    }

    private record PaletteEntry(boolean renderable, BlockState state) {
    }
}
