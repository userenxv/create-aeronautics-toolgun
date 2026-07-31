package com.enxv.aeronauticsstructuretool.blueprint.geometry;

import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PlotBlockTransform {
    private final ServerLevelPlot plot;
    private final ChunkPos plotChunkMin;
    private final ChunkPos plotChunkMax;

    private PlotBlockTransform(ServerLevelPlot plot, ChunkPos plotChunkMin, ChunkPos plotChunkMax) {
        this.plot = plot;
        this.plotChunkMin = plotChunkMin;
        this.plotChunkMax = plotChunkMax;
    }

    public static PlotBlockTransform capture(ServerSubLevel subLevel) {
        ServerLevelPlot plot = subLevel.getPlot();
        return new PlotBlockTransform(plot, plot.getChunkMin(), plot.getChunkMax());
    }

    public boolean containsPlotAbsolute(BlockPos pos) {
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        return chunkX >= this.plotChunkMin.x && chunkX <= this.plotChunkMax.x
                && chunkZ >= this.plotChunkMin.z && chunkZ <= this.plotChunkMax.z;
    }

    public BlockPos toSavedLocalBlockPos(BlockPos plotAbsolutePos) {
        ChunkPos plotAbsoluteChunk = new ChunkPos(plotAbsolutePos);
        ChunkPos localChunk = this.plot.toLocal(plotAbsoluteChunk);
        int localBlockX = Math.floorMod(plotAbsolutePos.getX(), 16);
        int localBlockZ = Math.floorMod(plotAbsolutePos.getZ(), 16);
        return new BlockPos((localChunk.x << 4) + localBlockX, plotAbsolutePos.getY(), (localChunk.z << 4) + localBlockZ);
    }

    public BlockPos toGlobalBlockPos(BlockPos localPos) {
        ChunkPos localChunk = new ChunkPos(localPos);
        int localBlockX = Math.floorMod(localPos.getX(), 16);
        int localBlockZ = Math.floorMod(localPos.getZ(), 16);
        ChunkPos plotAbsoluteChunk = this.plot.toGlobal(localChunk);
        return new BlockPos((plotAbsoluteChunk.x << 4) + localBlockX, localPos.getY(), (plotAbsoluteChunk.z << 4) + localBlockZ);
    }

    public Vector3d toSavedLocalPosition(Vector3d plotAbsolutePos) {
        int blockX = net.minecraft.util.Mth.floor(plotAbsolutePos.x);
        int blockZ = net.minecraft.util.Mth.floor(plotAbsolutePos.z);
        ChunkPos plotAbsoluteChunk = new ChunkPos(new BlockPos(blockX, 0, blockZ));
        ChunkPos localChunk = this.plot.toLocal(plotAbsoluteChunk);
        double localBlockX = plotAbsolutePos.x - plotAbsoluteChunk.getMinBlockX();
        double localBlockZ = plotAbsolutePos.z - plotAbsoluteChunk.getMinBlockZ();
        return new Vector3d((localChunk.x << 4) + localBlockX, plotAbsolutePos.y, (localChunk.z << 4) + localBlockZ);
    }

    public Vector3d toGlobalPosition(Vector3d localPos) {
        int blockX = net.minecraft.util.Mth.floor(localPos.x);
        int blockZ = net.minecraft.util.Mth.floor(localPos.z);
        ChunkPos localChunk = new ChunkPos(new BlockPos(blockX, 0, blockZ));
        double localBlockX = localPos.x - localChunk.getMinBlockX();
        double localBlockZ = localPos.z - localChunk.getMinBlockZ();
        ChunkPos plotAbsoluteChunk = this.plot.toGlobal(localChunk);
        return new Vector3d(plotAbsoluteChunk.getMinBlockX() + localBlockX, localPos.y, plotAbsoluteChunk.getMinBlockZ() + localBlockZ);
    }

    public List<net.minecraft.world.entity.Entity> findEntities(ServerLevel level) {
        List<net.minecraft.world.entity.Entity> entities = new ArrayList<>();
        AABB plotBounds = plotBounds();
        for (net.minecraft.world.level.entity.EntityAccess access : level.getEntities().getAll()) {
            if (access instanceof net.minecraft.world.entity.Entity entity
                    && entity.isAlive()
                    && entity.getBoundingBox().intersects(plotBounds)) {
                entities.add(entity);
            }
        }
        return entities;
    }

    public AABB plotBounds() {
        BoundingBox bounds = toMcBoundingBox(new BoundingBox3i(this.plot.getBoundingBox()));
        return new AABB(
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX() + 1.0D, bounds.maxY() + 1.0D, bounds.maxZ() + 1.0D
        );
    }

    public List<BlockEntity> findBlockEntities(ServerLevel level) {
        List<BlockEntity> blockEntities = new ArrayList<>();
        for (int chunkX = this.plotChunkMin.x; chunkX <= this.plotChunkMax.x; chunkX++) {
            for (int chunkZ = this.plotChunkMin.z; chunkZ <= this.plotChunkMax.z; chunkZ++) {
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    if (containsPlotAbsolute(entry.getKey())) {
                        blockEntities.add(entry.getValue());
                    }
                }
            }
        }
        return blockEntities;
    }

    private static BoundingBox toMcBoundingBox(dev.ryanhcode.sable.companion.math.BoundingBox3ic bounds) {
        return new BoundingBox(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }
}
