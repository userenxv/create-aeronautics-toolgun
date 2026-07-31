package com.enxv.aeronauticsstructuretool.compat.drivebywire;

import com.enxv.aeronauticsstructuretool.blueprint.model.SavedSubLevelBlueprint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.List;

final class DriveByWireBlueprintSummary {
    private static final String PLOT_CHUNKS = "chunks";
    private static final String PLOT_BLOCK_ENTITIES = "block_entities";

    private int blockCount;
    private int backupBlockCount;
    private int controllerHubCount;
    private int tweakedControllerHubCount;
    private int backupSnapshotCount;
    private int syntheticSnapshotCount;

    static DriveByWireBlueprintSummary from(List<SavedSubLevelBlueprint> sublevels) {
        DriveByWireBlueprintSummary summary = new DriveByWireBlueprintSummary();
        for (SavedSubLevelBlueprint sublevel : sublevels) {
            summary.include(sublevel.plotTag());
        }
        return summary;
    }

    void include(CompoundTag plotTag) {
        if (plotTag.contains(DriveByWireSnapshotNbt.SYNTHETIC_SNAPSHOT, Tag.TAG_COMPOUND)) {
            syntheticSnapshotCount++;
        }
        CompoundTag chunks = plotTag.getCompound(PLOT_CHUNKS);
        for (String key : chunks.getAllKeys()) {
            CompoundTag chunk = chunks.getCompound(key);
            includePaletteBlocks(chunk);
            includeBlockEntities(chunk);
        }
    }

    boolean hasBlocks() {
        return blockCount > 0;
    }

    boolean hasSnapshots() {
        return backupSnapshotCount > 0 || syntheticSnapshotCount > 0;
    }

    int blockCount() {
        return blockCount;
    }

    int backupBlockCount() {
        return backupBlockCount;
    }

    int controllerHubCount() {
        return controllerHubCount;
    }

    int tweakedControllerHubCount() {
        return tweakedControllerHubCount;
    }

    int backupSnapshotCount() {
        return backupSnapshotCount;
    }

    int syntheticSnapshotCount() {
        return syntheticSnapshotCount;
    }

    private void includePaletteBlocks(CompoundTag chunk) {
        CompoundTag sections = chunk.getCompound("sections");
        for (String sectionKey : sections.getAllKeys()) {
            ListTag palette = sections.getCompound(sectionKey)
                    .getCompound("block_states")
                    .getList("palette", Tag.TAG_COMPOUND);
            for (int i = 0; i < palette.size(); i++) {
                includeBlockName(palette.getCompound(i).getString("Name"));
            }
        }
    }

    private void includeBlockEntities(CompoundTag chunk) {
        ListTag blockEntities = chunk.getList(PLOT_BLOCK_ENTITIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < blockEntities.size(); i++) {
            CompoundTag blockEntity = blockEntities.getCompound(i);
            if (DriveByWireSnapshotNbt.BACKUP_BLOCK_ID.equals(
                    blockEntity.getString(DriveByWireSnapshotNbt.BLOCK_ENTITY_ID)
            ) && blockEntity.contains(DriveByWireSnapshotNbt.BLOCK_ENTITY_SNAPSHOT, Tag.TAG_COMPOUND)) {
                backupSnapshotCount++;
            }
        }
    }

    private void includeBlockName(String blockName) {
        if (blockName == null || !blockName.startsWith(DriveByWireSnapshotNbt.NAMESPACE_PREFIX)) {
            return;
        }
        blockCount++;
        if (DriveByWireSnapshotNbt.BACKUP_BLOCK_ID.equals(blockName)) {
            backupBlockCount++;
        } else if (DriveByWireSnapshotNbt.CONTROLLER_HUB_ID.equals(blockName)) {
            controllerHubCount++;
        } else if (DriveByWireSnapshotNbt.TWEAKED_CONTROLLER_HUB_ID.equals(blockName)) {
            tweakedControllerHubCount++;
        }
    }
}
