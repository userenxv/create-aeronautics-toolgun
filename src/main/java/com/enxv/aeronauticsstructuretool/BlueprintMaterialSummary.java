package com.enxv.aeronauticsstructuretool;

import com.enxv.aeronauticsstructuretool.blueprint.material.BlueprintMaterialAnalyzer;
import com.enxv.aeronauticsstructuretool.blueprint.material.MaterialCountCodec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BlueprintMaterialSummary {
    public static final String ROOT_TAG = "AST_MaterialSummary";
    public static final String ADDITIONAL_ITEMS_TAG = "AST_AdditionalMaterialItems";
    public static final String RUNTIME_ITEMS_TAG = "AST_MaterialItems";
    private static final String BLOCKS_TAG = "blocks";
    private static final String ITEMS_TAG = "items";
    private static final String TOTAL_BLOCKS_TAG = "total_blocks";
    private static final String TOTAL_ITEMS_TAG = "total_items";
    private static final String UNIQUE_BLOCK_TYPES_TAG = "unique_block_types";
    private static final String UNIQUE_ITEM_TYPES_TAG = "unique_item_types";

    private final Map<String, Long> blockCounts;
    private final Map<String, Long> itemCounts;

    private BlueprintMaterialSummary(Map<String, Long> blockCounts, Map<String, Long> itemCounts) {
        this.blockCounts = new LinkedHashMap<>(blockCounts);
        this.itemCounts = new LinkedHashMap<>(itemCounts);
    }

    public static BlueprintMaterialSummary of(
            Map<String, Long> blockCounts,
            Map<String, Long> itemCounts
    ) {
        return new BlueprintMaterialSummary(
                blockCounts == null ? Map.of() : blockCounts,
                itemCounts == null ? Map.of() : itemCounts
        );
    }

    static BlueprintMaterialSummary captureFromBlueprintRoot(CompoundTag root) throws IOException {
        return BlueprintMaterialAnalyzer.captureFromBlueprintRoot(root);
    }

    public static BlueprintMaterialSummary captureFromSublevels(
            ListTag sublevelsTag,
            int sourceMinBuildHeight
    ) throws IOException {
        return BlueprintMaterialAnalyzer.captureFromSublevels(sublevelsTag, sourceMinBuildHeight);
    }

    static BlueprintMaterialSummary readFromRoot(CompoundTag root) throws IOException {
        return BlueprintMaterialAnalyzer.readFromRoot(root);
    }

    static BlueprintMaterialSummary readFromCreatePhysicalRoot(CompoundTag root) throws IOException {
        return BlueprintMaterialAnalyzer.readFromCreatePhysicalRoot(root);
    }

    public static BlueprintMaterialSummary fromTag(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return empty();
        }
        requireCountList(tag, BLOCKS_TAG);
        requireCountList(tag, ITEMS_TAG);
        return of(
                MaterialCountCodec.readCounts(tag.getList(BLOCKS_TAG, Tag.TAG_COMPOUND)),
                MaterialCountCodec.readCounts(tag.getList(ITEMS_TAG, Tag.TAG_COMPOUND))
        );
    }

    public static BlueprintMaterialSummary empty() {
        return of(Map.of(), Map.of());
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.put(BLOCKS_TAG, MaterialCountCodec.writeCounts(this.blockCounts));
        tag.put(ITEMS_TAG, MaterialCountCodec.writeCounts(this.itemCounts));
        tag.putLong(TOTAL_BLOCKS_TAG, totalCount(this.blockCounts));
        tag.putLong(TOTAL_ITEMS_TAG, totalCount(this.itemCounts));
        tag.putInt(UNIQUE_BLOCK_TYPES_TAG, this.blockCounts.size());
        tag.putInt(UNIQUE_ITEM_TYPES_TAG, this.itemCounts.size());
        return tag;
    }

    long totalBlockCount() {
        return totalCount(this.blockCounts);
    }

    long totalItemCount() {
        return totalCount(this.itemCounts);
    }

    public Map<String, Long> blockCounts() {
        return Map.copyOf(this.blockCounts);
    }

    public Map<String, Long> itemCounts() {
        return Map.copyOf(this.itemCounts);
    }

    public static Map<String, Long> captureItemCounts(Tag tag) {
        return MaterialCountCodec.captureItemCounts(tag);
    }

    public static ListTag writeItemCounts(Map<String, Long> counts) {
        return MaterialCountCodec.writeCounts(counts);
    }

    public static Map<String, Long> readItemCounts(ListTag list) {
        return MaterialCountCodec.readCounts(list);
    }

    int uniqueBlockTypes() {
        return this.blockCounts.size();
    }

    int uniqueItemTypes() {
        return this.itemCounts.size();
    }

    static Map<String, Long> captureRuntimeStorageItemCounts(CompoundTag contraptionTag) {
        return MaterialCountCodec.captureRuntimeStorageItemCounts(contraptionTag);
    }

    static String knownDieselUpgradeItemId(String upgradeId) {
        return BlueprintMaterialAnalyzer.knownDieselUpgradeItemId(upgradeId);
    }

    private static long totalCount(Map<String, Long> counts) {
        long total = 0L;
        for (long count : counts.values()) {
            total = Math.addExact(total, count);
        }
        return total;
    }

    private static void requireCountList(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LIST)) {
            throw new IllegalArgumentException("material summary '" + key + "' list is missing");
        }
    }
}
