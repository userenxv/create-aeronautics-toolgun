package com.enxv.aeronauticsstructuretool.blueprint.material;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Locale;
import java.util.Map;

final class EmbeddedMaterialScanner {
    private static final String CHUNKS_TAG = "chunks";
    private static final String BLOCK_ENTITIES_TAG = "block_entities";
    private static final String CDG_SILENCER_UPGRADE = "createdieselgenerators:silencer";
    private static final String CDG_TURBOCHARGER_UPGRADE = "createdieselgenerators:turbocharger";
    private static final String CDG_SILENCER_ITEM = "createdieselgenerators:engine_silencer";
    private static final String CDG_TURBOCHARGER_ITEM = "createdieselgenerators:engine_turbocharger";

    private EmbeddedMaterialScanner() {
    }

    static void addFromPlot(
            CompoundTag plotTag,
            Map<String, Long> blockCounts,
            Map<String, Long> itemCounts
    ) {
        if (plotTag == null || plotTag.isEmpty()) {
            return;
        }
        CompoundTag chunks = plotTag.getCompound(CHUNKS_TAG);
        for (String chunkKey : chunks.getAllKeys()) {
            ListTag blockEntities = chunks.getCompound(chunkKey)
                    .getList(BLOCK_ENTITIES_TAG, Tag.TAG_COMPOUND);
            for (int i = 0; i < blockEntities.size(); i++) {
                addBlockEntityCosts(blockEntities.getCompound(i), blockCounts, itemCounts);
            }
        }
    }

    static void addFromCreateStructure(
            CompoundTag structureTag,
            Map<String, Long> blockCounts,
            Map<String, Long> itemCounts
    ) {
        if (structureTag == null || structureTag.isEmpty()) {
            return;
        }
        ListTag blocks = structureTag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            addBlockEntityCosts(blocks.getCompound(i).getCompound("nbt"), blockCounts, itemCounts);
        }
    }

    static void addKnownRuntimeBlockEntityCosts(ListTag runtimeContraptions, Map<String, Long> itemCounts) {
        if (runtimeContraptions == null || runtimeContraptions.isEmpty()) {
            return;
        }
        for (int i = 0; i < runtimeContraptions.size(); i++) {
            ListTag blockList = runtimeContraptions.getCompound(i)
                    .getCompound("contraption")
                    .getCompound("Blocks")
                    .getList("BlockList", Tag.TAG_COMPOUND);
            for (int blockIndex = 0; blockIndex < blockList.size(); blockIndex++) {
                addKnownBlockEntityMaterialCosts(
                        blockList.getCompound(blockIndex).getCompound("Data"),
                        itemCounts
                );
            }
        }
    }

    static String knownDieselUpgradeItemId(String upgradeId) {
        return switch (upgradeId) {
            case CDG_SILENCER_UPGRADE -> CDG_SILENCER_ITEM;
            case CDG_TURBOCHARGER_UPGRADE -> CDG_TURBOCHARGER_ITEM;
            default -> null;
        };
    }

    private static void addBlockEntityCosts(
            CompoundTag blockEntity,
            Map<String, Long> blockCounts,
            Map<String, Long> itemCounts
    ) {
        addKnownBlockEntityMaterialCosts(blockEntity, itemCounts);
        if (!containsExplicitItemCost(blockEntity)) {
            scanEmbeddedMaterials(blockEntity, blockCounts, itemCounts, 0, "");
        }
    }

    private static void addKnownBlockEntityMaterialCosts(
            CompoundTag blockEntity,
            Map<String, Long> itemCounts
    ) {
        if (blockEntity == null || !blockEntity.contains("Upgrade", Tag.TAG_STRING)) {
            return;
        }
        String upgradeItem = knownDieselUpgradeItemId(blockEntity.getString("Upgrade"));
        if (upgradeItem != null) {
            itemCounts.merge(upgradeItem, 1L, Long::sum);
        }
    }

    private static void scanEmbeddedMaterials(
            Tag tag,
            Map<String, Long> blockCounts,
            Map<String, Long> itemCounts,
            int depth,
            String pathKey
    ) {
        if (tag == null || depth > 32) {
            return;
        }
        if (tag instanceof CompoundTag compound) {
            if (looksLikeMaterialPath(pathKey)) {
                addEmbeddedMaterialIfPresent(compound, blockCounts, itemCounts);
            }
            for (String key : compound.getAllKeys()) {
                scanEmbeddedMaterials(compound.get(key), blockCounts, itemCounts, depth + 1, key);
            }
        } else if (tag instanceof ListTag listTag) {
            for (Tag tag1 : listTag) {
                scanEmbeddedMaterials(tag1, blockCounts, itemCounts, depth + 1, pathKey);
            }
        }
    }

    private static void addEmbeddedMaterialIfPresent(
            CompoundTag compound,
            Map<String, Long> blockCounts,
            Map<String, Long> itemCounts
    ) {
        String blockId = BlueprintBlockCounter.normalizeBlockId(compound.getString("Name"));
        if (blockId != null) {
            blockCounts.merge(blockId, 1L, Long::sum);
            return;
        }
        String itemId = MaterialCountCodec.normalizeItemId(compound.getString("id"));
        if (itemId == null) {
            itemId = MaterialCountCodec.normalizeItemId(compound.getString("item"));
        }
        if (itemId != null) {
            itemCounts.merge(itemId, Math.max(1L, MaterialCountCodec.readStackCount(compound)), Long::sum);
        }
    }

    private static boolean containsExplicitItemCost(CompoundTag compound) {
        if (compound == null || compound.isEmpty()) {
            return false;
        }
        if (MaterialCountCodec.normalizeItemId(compound.getString("id")) != null
                || MaterialCountCodec.normalizeItemId(compound.getString("item")) != null) {
            return MaterialCountCodec.readStackCount(compound) > 0L;
        }
        for (String key : compound.getAllKeys()) {
            Tag child = compound.get(key);
            if (child instanceof CompoundTag childCompound && containsExplicitItemCost(childCompound)) {
                return true;
            }
            if (child instanceof ListTag childList) {
                for (int i = 0; i < childList.size(); i++) {
                    if (childList.get(i) instanceof CompoundTag entry && containsExplicitItemCost(entry)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean looksLikeMaterialPath(String key) {
        return key != null
                && !key.isBlank()
                && key.toLowerCase(Locale.ROOT).contains("material");
    }
}
