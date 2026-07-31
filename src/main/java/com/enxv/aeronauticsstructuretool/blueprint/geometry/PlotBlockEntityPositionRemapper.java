package com.enxv.aeronauticsstructuretool.blueprint.geometry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

public final class PlotBlockEntityPositionRemapper {
    private static final String PLOT_CHUNKS_TAG = "chunks";
    private static final String PLOT_BLOCK_ENTITIES_TAG = "block_entities";

    private PlotBlockEntityPositionRemapper() {
    }

    public static void setHorizontalPosition(CompoundTag blockEntityTag, int x, int z) {
        blockEntityTag.putInt("x", x);
        blockEntityTag.putInt("z", z);
    }

    public static int localizeForBlueprint(CompoundTag plotTag) {
        CompoundTag chunks = plotTag.getCompound(PLOT_CHUNKS_TAG);
        int count = 0;
        for (String key : chunks.getAllKeys()) {
            CompoundTag chunkTag = chunks.getCompound(key);
            ListTag blockEntities = chunkTag.getList(PLOT_BLOCK_ENTITIES_TAG, 10);
            long packedChunkPos = Long.parseLong(key);
            int localChunkX = ChunkPos.getX(packedChunkPos);
            int localChunkZ = ChunkPos.getZ(packedChunkPos);
            for (int i = 0; i < blockEntities.size(); i++) {
                CompoundTag blockEntityTag = blockEntities.getCompound(i);
                stripTransientData(blockEntityTag);
                int localBlockX = Math.floorMod(blockEntityTag.getInt("x"), 16);
                int localBlockZ = Math.floorMod(blockEntityTag.getInt("z"), 16);
                setHorizontalPosition(
                        blockEntityTag,
                        (localChunkX << 4) + localBlockX,
                        (localChunkZ << 4) + localBlockZ
                );
                count++;
            }
        }
        return count;
    }

    public static int mapToAllocatedPlot(CompoundTag plotTag, ServerSubLevel subLevel) {
        CompoundTag chunks = plotTag.getCompound(PLOT_CHUNKS_TAG);
        int count = 0;
        for (String key : chunks.getAllKeys()) {
            CompoundTag chunkTag = chunks.getCompound(key);
            ListTag blockEntities = chunkTag.getList(PLOT_BLOCK_ENTITIES_TAG, 10);
            ChunkPos localChunkPos = new ChunkPos(Long.parseLong(key));
            ChunkPos globalChunkPos = subLevel.getPlot().toGlobal(localChunkPos);
            for (int i = 0; i < blockEntities.size(); i++) {
                CompoundTag blockEntityTag = blockEntities.getCompound(i);
                int localBlockX = Math.floorMod(blockEntityTag.getInt("x"), 16);
                int localBlockZ = Math.floorMod(blockEntityTag.getInt("z"), 16);
                setHorizontalPosition(
                        blockEntityTag,
                        globalChunkPos.getMinBlockX() + localBlockX,
                        globalChunkPos.getMinBlockZ() + localBlockZ
                );
                count++;
            }
        }
        return count;
    }

    private static void stripTransientData(CompoundTag blockEntityTag) {
        blockEntityTag.remove("Network");
        blockEntityTag.remove("Source");
        blockEntityTag.remove("Speed");
        blockEntityTag.remove("NeedsSpeedUpdate");
        blockEntityTag.remove("network");
        blockEntityTag.remove("source");
        blockEntityTag.remove("speed");
    }
}
