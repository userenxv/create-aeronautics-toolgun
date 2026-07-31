package com.enxv.aeronauticsstructuretool.blueprint.security;

import com.enxv.aeronauticsstructuretool.core.ModConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class PlotBlockEntityCompatibilityGuard {
    private static final String CHUNKS_TAG = "chunks";
    private static final String BLOCK_ENTITIES_TAG = "block_entities";
    private static final String ID_TAG = "id";
    private static final String CREATE_REDSTONE_LINK = "create:redstone_link";
    private static final String TIN_CAN_VEHICLE_REDSTONE_LINK = "tin_can_telegraph:vehicle_redstone_link";

    private PlotBlockEntityCompatibilityGuard() {
    }

    public static void removeKnownMismatches(CompoundTag plotTag, String phase) {
        CompoundTag chunks = plotTag.getCompound(CHUNKS_TAG);
        for (String key : chunks.getAllKeys()) {
            CompoundTag chunkTag = chunks.getCompound(key);
            ListTag blockEntities = chunkTag.getList(BLOCK_ENTITIES_TAG, Tag.TAG_COMPOUND);
            for (int index = blockEntities.size() - 1; index >= 0; index--) {
                CompoundTag blockEntityTag = blockEntities.getCompound(index);
                if (!CREATE_REDSTONE_LINK.equals(blockEntityTag.getString(ID_TAG))) {
                    continue;
                }
                String blockName = blockNameAt(chunkTag, blockEntityTag);
                if (!TIN_CAN_VEHICLE_REDSTONE_LINK.equals(blockName)) {
                    continue;
                }
                blockEntities.remove(index);
                ModConstants.LOGGER.warn(
                        "Removed unsafe block entity '{}' from '{}' during blueprint {} at local {},{},{}",
                        CREATE_REDSTONE_LINK,
                        TIN_CAN_VEHICLE_REDSTONE_LINK,
                        phase,
                        blockEntityTag.getInt("x"),
                        blockEntityTag.getInt("y"),
                        blockEntityTag.getInt("z")
                );
            }
        }
    }

    private static String blockNameAt(CompoundTag chunkTag, CompoundTag blockEntityTag) {
        CompoundTag sections = chunkTag.getCompound("sections");
        if (sections.isEmpty()) {
            return "";
        }
        int y = blockEntityTag.getInt("y");
        CompoundTag sectionTag = sections.getCompound(String.valueOf(SectionPos.blockToSectionCoord(y)));
        if (sectionTag.isEmpty()) {
            return "";
        }
        CompoundTag blockStates = sectionTag.getCompound("block_states");
        ListTag palette = blockStates.getList("palette", Tag.TAG_COMPOUND);
        if (palette.isEmpty()) {
            return "";
        }
        int paletteIndex = paletteIndexAt(blockStates, palette.size(), blockEntityTag);
        if (paletteIndex < 0 || paletteIndex >= palette.size()) {
            return "";
        }
        return palette.getCompound(paletteIndex).getString("Name");
    }

    private static int paletteIndexAt(CompoundTag blockStates, int paletteSize, CompoundTag blockEntityTag) {
        if (paletteSize <= 1 || !blockStates.contains("data", Tag.TAG_LONG_ARRAY)) {
            return 0;
        }
        int localX = Math.floorMod(blockEntityTag.getInt("x"), 16);
        int localY = Math.floorMod(blockEntityTag.getInt("y"), 16);
        int localZ = Math.floorMod(blockEntityTag.getInt("z"), 16);
        int blockIndex = (localY << 8) | (localZ << 4) | localX;
        int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(Math.max(0, paletteSize - 1)));
        long mask = (1L << bits) - 1L;
        long[] data = blockStates.getLongArray("data");
        int bitOffset = blockIndex * bits;
        int arrayIndex = bitOffset / 64;
        int bitIndex = bitOffset % 64;
        if (arrayIndex < 0 || arrayIndex >= data.length) {
            return 0;
        }
        long value = data[arrayIndex] >>> bitIndex;
        int overflowBits = bitIndex + bits - 64;
        if (overflowBits > 0 && arrayIndex + 1 < data.length) {
            value |= data[arrayIndex + 1] << (bits - overflowBits);
        }
        return (int) (value & mask);
    }
}
