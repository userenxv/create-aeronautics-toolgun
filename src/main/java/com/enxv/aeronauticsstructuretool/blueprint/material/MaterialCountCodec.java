package com.enxv.aeronauticsstructuretool.blueprint.material;

import com.enxv.aeronauticsstructuretool.BlueprintMaterialSummary;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.checkerframework.checker.units.qual.C;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MaterialCountCodec {
    private static final String ENTRY_ID_TAG = "id";
    private static final String ENTRY_COUNT_TAG = "count";
    private static final String CHUNKS_TAG = "chunks";
    private static final String BLOCK_ENTITIES_TAG = "block_entities";

    private MaterialCountCodec() {
    }

    public static Map<String, Long> captureItemCounts(Tag tag) {
        Map<String, Long> itemCounts = new LinkedHashMap<>();
        scanItems(tag, itemCounts, 0);
        return itemCounts;
    }

    public static Map<String, Long> captureRuntimeStorageItemCounts(CompoundTag contraptionTag) {
        Map<String, Long> itemCounts = new LinkedHashMap<>();
        if (contraptionTag == null || contraptionTag.isEmpty()) {
            return itemCounts;
        }

        boolean hasMountedStorage = false;
        Tag mountedItems = contraptionTag.get("items");
        if (mountedItems != null) {
            scanItems(mountedItems, itemCounts, 0);
            hasMountedStorage = true;
        }
        Tag legacyStorage = contraptionTag.get("Storage");
        if (legacyStorage != null) {
            scanItems(legacyStorage, itemCounts, 0);
            hasMountedStorage = true;
        }
        if (hasMountedStorage) {
            return itemCounts;
        }

        CompoundTag blocksTag = contraptionTag.getCompound("Blocks");
        ListTag blockList = blocksTag.getList("BlockList", Tag.TAG_COMPOUND);
        for (int i = 0; i < blockList.size(); i++) {
            CompoundTag blockEntry = blockList.getCompound(i);
            if (blockEntry.contains("Data", Tag.TAG_COMPOUND)) {
                scanItems(blockEntry.getCompound("Data"), itemCounts, 0);
            }
        }
        return itemCounts;
    }

    public static ListTag writeCounts(Map<String, Long> counts) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("material count id is empty");
            }
            if (entry.getValue() == null || entry.getValue() <= 0L) {
                throw new IllegalArgumentException(
                        "material count for '" + entry.getKey() + "' must be positive"
                );
            }
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString(ENTRY_ID_TAG, entry.getKey());
            entryTag.putLong(ENTRY_COUNT_TAG, entry.getValue());
            list.add(entryTag);
        }
        return list;
    }

    public static Map<String, Long> readCounts(ListTag list) {
        try {
            return readCountsStrict(list, "material counts");
        } catch (IOException exception) {
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
    }

    public static Map<String, Long> readCountsStrict(ListTag list, String label) throws IOException {
        if (list == null) {
            throw new IOException(label + " list is missing");
        }
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IOException(label + " entries are not compounds");
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            if (!entryTag.contains(ENTRY_ID_TAG, Tag.TAG_STRING)) {
                throw new IOException(label + " entry " + i + " has no string id");
            }
            String id = entryTag.getString(ENTRY_ID_TAG);
            if (id.isBlank()) {
                throw new IOException(label + " entry " + i + " has an empty id");
            }
            if (!entryTag.contains(ENTRY_COUNT_TAG, Tag.TAG_ANY_NUMERIC)) {
                throw new IOException(label + " entry " + i + " has no numeric count");
            }
            long count = entryTag.getLong(ENTRY_COUNT_TAG);
            if (count <= 0L) {
                throw new IOException(label + " entry " + i + " has a non-positive count");
            }
            try {
                counts.merge(id, count, Math::addExact);
            } catch (ArithmeticException exception) {
                throw new IOException(label + " count overflow for '" + id + "'", exception);
            }
        }
        return Map.copyOf(counts);
    }

    static void addItemsFromPlot(CompoundTag plotTag, Map<String, Long> itemCounts) {
        if (plotTag == null || plotTag.isEmpty()) {
            return;
        }
        CompoundTag chunks = plotTag.getCompound(CHUNKS_TAG);
        for (String chunkKey : chunks.getAllKeys()) {
            ListTag blockEntities = chunks.getCompound(chunkKey)
                    .getList(BLOCK_ENTITIES_TAG, Tag.TAG_COMPOUND);
            for (int i = 0; i < blockEntities.size(); i++) {
                CompoundTag blockEntity = blockEntities.getCompound(i);
                if (blockEntity.getString("id").equals("create:belt")) continue; //已被AdditionalItems存储
                scanItems(blockEntity, itemCounts, 0);
            }
        }
    }

    static void addItemsFromRuntimeContraptions(ListTag runtimeContraptions, Map<String, Long> itemCounts) {
        if (runtimeContraptions == null || runtimeContraptions.isEmpty()) {
            return;
        }
        for (int i = 0; i < runtimeContraptions.size(); i++) {
            CompoundTag runtimeTag = runtimeContraptions.getCompound(i);
            if (runtimeTag.contains(BlueprintMaterialSummary.RUNTIME_ITEMS_TAG, Tag.TAG_LIST)) {
                mergeCounts(
                        itemCounts,
                        readCounts(runtimeTag.getList(BlueprintMaterialSummary.RUNTIME_ITEMS_TAG, Tag.TAG_COMPOUND))
                );
            } else {
                mergeCounts(itemCounts, captureRuntimeStorageItemCounts(runtimeTag.getCompound("contraption")));
            }
        }
    }

    static void addItemsFromCreateStructure(CompoundTag structureTag, Map<String, Long> itemCounts) {
        if (structureTag == null || structureTag.isEmpty()) {
            return;
        }
        ListTag blocks = structureTag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            scanItems(blocks.getCompound(i).getCompound("nbt"), itemCounts, 0);
        }
    }

    static void scanItems(Tag tag, Map<String, Long> itemCounts, int depth) {
        if (tag == null || depth > 32) {
            return;
        }
        if (tag instanceof CompoundTag compound) {
            addItemIfPresent(compound, itemCounts);
            for (String key : compound.getAllKeys()) {
                switch (key) {
                    case "FrequencyFirst", "FrequencyLast", "consumedItem": continue;
                    case "Filter": {
                        CompoundTag item = compound.getCompound(key);
                        switch (item.getString("id")) {
                            case "create:filter",
                                 "create:attribute:filter",
                                 "create:package_filter",
                                 "createdieselgenerators:entity_filter": {
                                addItemIfPresent(item, itemCounts);
                                continue;
                            }
                        }
                    }
                    case "material": { //copycats
                        CompoundTag material = new CompoundTag();
                        material.putString("id", compound.getCompound(key).getString("Name"));
                        material.putInt("count", 1);
                        addItemIfPresent(material, itemCounts);
                    }
                }
                scanItems(compound.get(key), itemCounts, depth + 1);
            }
        } else if (tag instanceof ListTag listTag) {
            for (Tag value : listTag) {
                scanItems(value, itemCounts, depth + 1);
            }
        }
    }

    static String normalizeItemId(String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return null;
        }
        return id.toString();
    }

    static long readStackCount(CompoundTag compound) {
        if (compound.contains("count", Tag.TAG_ANY_NUMERIC)) {
            return Math.max(0L, compound.getLong("count"));
        }
        if (compound.contains("Count", Tag.TAG_ANY_NUMERIC)) {
            return Math.max(0L, compound.getLong("Count"));
        }
        return 0L;
    }

    static void mergeCounts(Map<String, Long> target, Map<String, Long> source) {
        for (Map.Entry<String, Long> entry : source.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0L) {
                try {
                    target.merge(entry.getKey(), entry.getValue(), Math::addExact);
                } catch (ArithmeticException exception) {
                    throw new IllegalArgumentException(
                            "material count overflow for '" + entry.getKey() + "'",
                            exception
                    );
                }
            }
        }
    }

    private static void addItemIfPresent(CompoundTag compound, Map<String, Long> itemCounts) {
        String itemId = normalizeItemId(compound.getString("id"));
        if (itemId == null) {
            itemId = normalizeItemId(compound.getString("item"));
        }
        if (itemId == null) {
            return;
        }
        long count = readStackCount(compound);
        if (count > 0L) {
            itemCounts.merge(itemId, count, Long::sum);
        }
    }
}
