package com.enxv.aeronauticsstructuretool.blueprint.importer.vmod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class VModPlotWriter {
    static final int PLOT_LOG_SIZE = 7;
    static final int PLOT_CENTER_XZ = ((1 << (PLOT_LOG_SIZE - 1)) << 4) + 8;
    static final int PLOT_CENTER_Y = 128;

    private static final int PLOT_SIZE_IN_BLOCKS = (1 << PLOT_LOG_SIZE) << 4;
    private static final int SECTION_COUNT = 24;
    private static final int MIN_WORLD_Y = -64;
    private static final int MAX_WORLD_Y = MIN_WORLD_Y + (SECTION_COUNT << 4) - 1;
    private static final String PLOT_CHUNKS_TAG = "chunks";
    private static final String PLOT_BLOCK_ENTITIES_TAG = "block_entities";

    private VModPlotWriter() {
    }

    static CompoundTag write(VModShip ship) throws IOException {
        Bounds bounds = Bounds.of(ship.blocks());
        Map<Long, ChunkBuildData> chunks = new LinkedHashMap<>();
        int horizontalOverflow = 0;
        int verticalOverflow = 0;

        for (VModBlock block : ship.blocks()) {
            int localX = block.position().getX() - bounds.centerX() + PLOT_CENTER_XZ;
            int localY = block.position().getY() - bounds.centerY() + PLOT_CENTER_Y;
            int localZ = block.position().getZ() - bounds.centerZ() + PLOT_CENTER_XZ;
            if (localX < 0 || localZ < 0
                    || localX >= PLOT_SIZE_IN_BLOCKS || localZ >= PLOT_SIZE_IN_BLOCKS) {
                horizontalOverflow++;
                continue;
            }
            if (localY < MIN_WORLD_Y || localY > MAX_WORLD_Y) {
                verticalOverflow++;
                continue;
            }

            int chunkX = localX >> 4;
            int chunkZ = localZ >> 4;
            int sectionIndex = (localY - MIN_WORLD_Y) >> 4;
            ChunkBuildData chunk = chunks.computeIfAbsent(
                    ChunkPos.asLong(chunkX, chunkZ),
                    ignored -> new ChunkBuildData()
            );
            SectionBuildData section = chunk.sections.computeIfAbsent(
                    sectionIndex,
                    ignored -> new SectionBuildData()
            );
            section.setBlock(localX & 15, localY & 15, localZ & 15, block.state());

            if (block.blockEntityTag() != null) {
                CompoundTag blockEntityTag = block.blockEntityTag().copy();
                blockEntityTag.putInt("x", localX);
                blockEntityTag.putInt("y", localY);
                blockEntityTag.putInt("z", localZ);
                chunk.blockEntities.add(blockEntityTag);
            }
        }

        if (horizontalOverflow > 0 || verticalOverflow > 0) {
            throw new IOException(
                    "VMod ship " + ship.shipId()
                            + " exceeds native plot bounds: horizontal=" + horizontalOverflow
                            + ", vertical=" + verticalOverflow
            );
        }
        if (chunks.isEmpty()) {
            throw new IOException("no supported VMod blocks");
        }

        CompoundTag plotTag = new CompoundTag();
        plotTag.putInt("plot_x", 0);
        plotTag.putInt("plot_z", 0);
        plotTag.putInt("log_size", PLOT_LOG_SIZE);
        plotTag.putString("biome", Biomes.PLAINS.location().toString());
        plotTag.putInt("data_version", 1);
        plotTag.putBoolean("isLightOn", true);

        CompoundTag chunksTag = new CompoundTag();
        for (Map.Entry<Long, ChunkBuildData> entry : chunks.entrySet()) {
            chunksTag.put(Long.toString(entry.getKey()), entry.getValue().toTag());
        }
        plotTag.put(PLOT_CHUNKS_TAG, chunksTag);
        return plotTag;
    }

    private static final class ChunkBuildData {
        private final Map<Integer, SectionBuildData> sections = new LinkedHashMap<>();
        private final ListTag blockEntities = new ListTag();

        private CompoundTag toTag() {
            CompoundTag chunkTag = new CompoundTag();
            CompoundTag sectionsTag = new CompoundTag();
            for (Map.Entry<Integer, SectionBuildData> entry : this.sections.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    continue;
                }
                CompoundTag sectionTag = new CompoundTag();
                sectionTag.put("block_states", entry.getValue().toBlockStatesTag());
                sectionTag.putByteArray("SkyLight", fullSkyLight());
                sectionsTag.put(Integer.toString(entry.getKey()), sectionTag);
            }
            chunkTag.put("sections", sectionsTag);
            chunkTag.putBoolean("isLightOn", true);
            chunkTag.put(PLOT_BLOCK_ENTITIES_TAG, this.blockEntities);
            chunkTag.put("block_ticks", new ListTag());
            chunkTag.put("fluid_ticks", new ListTag());
            chunkTag.put("heightmaps", new CompoundTag());
            return chunkTag;
        }

        private static byte[] fullSkyLight() {
            byte[] sky = new byte[2048];
            Arrays.fill(sky, (byte) 0xFF);
            return sky;
        }
    }

    private static final class SectionBuildData {
        private final BlockState[] states = new BlockState[4096];

        private void setBlock(int x, int y, int z, BlockState state) {
            this.states[(y << 8) | (z << 4) | x] = state;
        }

        private boolean isEmpty() {
            for (BlockState state : this.states) {
                if (state != null && !state.isAir()) {
                    return false;
                }
            }
            return true;
        }

        private CompoundTag toBlockStatesTag() {
            List<BlockState> palette = new ArrayList<>();
            Map<BlockState, Integer> paletteIds = new LinkedHashMap<>();
            addPaletteState(palette, paletteIds, Blocks.AIR.defaultBlockState());

            int[] indices = new int[this.states.length];
            for (int i = 0; i < this.states.length; i++) {
                BlockState state = this.states[i] == null
                        ? Blocks.AIR.defaultBlockState()
                        : this.states[i];
                indices[i] = addPaletteState(palette, paletteIds, state);
            }

            CompoundTag blockStatesTag = new CompoundTag();
            ListTag paletteTag = new ListTag();
            for (BlockState state : palette) {
                paletteTag.add(NbtUtils.writeBlockState(state));
            }
            blockStatesTag.put("palette", paletteTag);
            int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(Math.max(0, palette.size() - 1)));
            blockStatesTag.putLongArray("data", packBlockStateIndices(indices, bits));
            return blockStatesTag;
        }

        private static int addPaletteState(
                List<BlockState> palette,
                Map<BlockState, Integer> paletteIds,
                BlockState state
        ) {
            Integer existing = paletteIds.get(state);
            if (existing != null) {
                return existing;
            }
            int newId = palette.size();
            palette.add(state);
            paletteIds.put(state, newId);
            return newId;
        }

        private static long[] packBlockStateIndices(int[] indices, int bits) {
            int valuesPerLong = 64 / bits;
            int length = (indices.length + valuesPerLong - 1) / valuesPerLong;
            long[] packed = new long[length];
            long mask = (1L << bits) - 1L;
            for (int i = 0; i < indices.length; i++) {
                int arrayIndex = i / valuesPerLong;
                int bitIndex = (i % valuesPerLong) * bits;
                packed[arrayIndex] |= ((long) indices[i] & mask) << bitIndex;
            }
            return packed;
        }
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private static Bounds of(List<VModBlock> blocks) {
            VModBlock first = blocks.getFirst();
            int minX = first.position().getX();
            int minY = first.position().getY();
            int minZ = first.position().getZ();
            int maxX = minX;
            int maxY = minY;
            int maxZ = minZ;
            for (int i = 1; i < blocks.size(); i++) {
                var pos = blocks.get(i).position();
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        private int centerX() {
            return this.minX + Math.floorDiv(this.maxX - this.minX, 2);
        }

        private int centerY() {
            return this.minY + Math.floorDiv(this.maxY - this.minY, 2);
        }

        private int centerZ() {
            return this.minZ + Math.floorDiv(this.maxZ - this.minZ, 2);
        }
    }
}
