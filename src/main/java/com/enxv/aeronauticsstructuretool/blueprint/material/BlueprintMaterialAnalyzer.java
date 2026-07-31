package com.enxv.aeronauticsstructuretool.blueprint.material;

import com.enxv.aeronauticsstructuretool.BlueprintMaterialSummary;
import com.enxv.aeronauticsstructuretool.blueprint.codec.NativeBlueprintReader;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.enxv.aeronauticsstructuretool.blueprint.codec.NbtTagValidator.requireCompoundList;

public final class BlueprintMaterialAnalyzer {
    private static final String PLOT_TAG = "plot";
    private static final String RUNTIME_CONTRAPTIONS_TAG = "runtime_contraptions";

    private BlueprintMaterialAnalyzer() {
    }

    public static BlueprintMaterialSummary captureFromBlueprintRoot(CompoundTag root) throws IOException {
        if (root == null || root.isEmpty()) {
            return BlueprintMaterialSummary.empty();
        }
        int sourceMinBuildHeight = NativeBlueprintReader.read(root).sourceMinBuildHeight();
        return captureFromSublevels(
                root.getList("sublevels", Tag.TAG_COMPOUND),
                sourceMinBuildHeight
        );
    }

    public static BlueprintMaterialSummary captureFromSublevels(
            ListTag sublevelsTag,
            int sourceMinBuildHeight
    ) throws IOException {
        if (sublevelsTag == null
                || (!sublevelsTag.isEmpty() && sublevelsTag.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IOException("blueprint material sublevels are not a compound list");
        }
        Map<String, Long> blockCounts = new LinkedHashMap<>();
        Map<String, Long> itemCounts = new LinkedHashMap<>();
        for (int i = 0; i < sublevelsTag.size(); i++) {
            CompoundTag sublevelTag = sublevelsTag.getCompound(i);
            CompoundTag plotTag = sublevelTag.getCompound(PLOT_TAG);
            BlueprintBlockCounter.addPlotBlocks(plotTag, sourceMinBuildHeight, blockCounts);
            MaterialCountCodec.addItemsFromPlot(plotTag, itemCounts);
            EmbeddedMaterialScanner.addFromPlot(plotTag, blockCounts, itemCounts);

            ListTag runtimeContraptions = sublevelTag.getList(
                    RUNTIME_CONTRAPTIONS_TAG,
                    Tag.TAG_COMPOUND
            );
            BlueprintBlockCounter.addRuntimeContraptionBlocks(runtimeContraptions, blockCounts);
            MaterialCountCodec.addItemsFromRuntimeContraptions(runtimeContraptions, itemCounts);
            EmbeddedMaterialScanner.addKnownRuntimeBlockEntityCosts(runtimeContraptions, itemCounts);
            MaterialCountCodec.mergeCounts(itemCounts, readAdditionalItems(sublevelTag, i));
        }
        return BlueprintMaterialSummary.of(blockCounts, itemCounts);
    }

    public static BlueprintMaterialSummary readFromRoot(CompoundTag root) throws IOException {
        if (root == null || root.isEmpty()) {
            return BlueprintMaterialSummary.empty();
        }
        if (root.contains("sub_levels", Tag.TAG_LIST)) {
            return readFromCreatePhysicalRoot(root);
        }
        return captureFromBlueprintRoot(root);
    }

    public static BlueprintMaterialSummary readFromCreatePhysicalRoot(CompoundTag root) throws IOException {
        if (root == null || root.isEmpty()) {
            return BlueprintMaterialSummary.empty();
        }
        Map<String, Long> blockCounts = new LinkedHashMap<>();
        Map<String, Long> itemCounts = new LinkedHashMap<>();
        addCreateStructure(root, "root", blockCounts, itemCounts);
        ListTag subLevels = requireCompoundList(
                root,
                "sub_levels",
                "Create physical sub_levels",
                false
        );
        for (int i = 0; i < subLevels.size(); i++) {
            addCreateStructure(subLevels.getCompound(i), "sub_levels[" + i + "]", blockCounts, itemCounts);
        }
        return BlueprintMaterialSummary.of(blockCounts, itemCounts);
    }

    public static String knownDieselUpgradeItemId(String upgradeId) {
        return EmbeddedMaterialScanner.knownDieselUpgradeItemId(upgradeId);
    }

    private static void addCreateStructure(
            CompoundTag structureTag,
            String location,
            Map<String, Long> blockCounts,
            Map<String, Long> itemCounts
    ) throws IOException {
        BlueprintBlockCounter.addCreateStructureBlocks(structureTag, location, blockCounts);
        MaterialCountCodec.addItemsFromCreateStructure(structureTag, itemCounts);
        EmbeddedMaterialScanner.addFromCreateStructure(structureTag, blockCounts, itemCounts);
    }

    private static Map<String, Long> readAdditionalItems(
            CompoundTag sublevelTag,
            int sublevelIndex
    ) throws IOException {
        if (!sublevelTag.contains(BlueprintMaterialSummary.ADDITIONAL_ITEMS_TAG)) {
            return Map.of();
        }
        ListTag list = requireCompoundList(
                sublevelTag,
                BlueprintMaterialSummary.ADDITIONAL_ITEMS_TAG,
                "sublevel " + sublevelIndex + " additional material items",
                false
        );
        return MaterialCountCodec.readCountsStrict(
                list,
                "sublevel " + sublevelIndex + " additional material items"
        );
    }
}
