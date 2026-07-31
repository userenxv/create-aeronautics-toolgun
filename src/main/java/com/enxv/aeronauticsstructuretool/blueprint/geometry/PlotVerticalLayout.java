package com.enxv.aeronauticsstructuretool.blueprint.geometry;

import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;

public record PlotVerticalLayout(int sectionIndexShift, int blockYShift) {
    private static final String CHUNKS_TAG = "chunks";
    private static final String SECTIONS_TAG = "sections";

    public static PlotVerticalLayout plan(
            ServerLevel targetLevel,
            CompoundTag plotTag,
            int sourceMinBuildHeight
    ) throws IOException {
        return plan(
                targetLevel.getMinSection(),
                targetLevel.getSectionsCount(),
                plotTag,
                sourceMinBuildHeight
        );
    }

    public static PlotVerticalLayout plan(
            int targetMinSection,
            int targetSectionCount,
            CompoundTag plotTag,
            int sourceMinBuildHeight
    ) throws IOException {
        CompoundTag chunks = plotTag.getCompound(CHUNKS_TAG);
        int minSavedIndex = Integer.MAX_VALUE;
        int maxSavedIndex = Integer.MIN_VALUE;
        for (String chunkKey : chunks.getAllKeys()) {
            CompoundTag sections = chunks.getCompound(chunkKey).getCompound(SECTIONS_TAG);
            for (String sectionKey : sections.getAllKeys()) {
                int index = parseSectionIndex(sectionKey);
                minSavedIndex = Math.min(minSavedIndex, index);
                maxSavedIndex = Math.max(maxSavedIndex, index);
            }
        }
        if (minSavedIndex == Integer.MAX_VALUE) {
            return new PlotVerticalLayout(0, (targetMinSection << 4) - sourceMinBuildHeight);
        }

        int minimumShift = -minSavedIndex;
        int maximumShift = targetSectionCount - 1 - maxSavedIndex;
        if (minimumShift > maximumShift) {
            throw new IOException(
                    "blueprint vertical span exceeds target world height: savedSections="
                            + (maxSavedIndex - minSavedIndex + 1)
                            + ", targetSections=" + targetSectionCount
            );
        }

        int sourceMinSection = SectionPos.blockToSectionCoord(sourceMinBuildHeight);
        int absolutePreservingShift = sourceMinSection - targetMinSection;
        int chosenShift = Math.max(minimumShift, Math.min(absolutePreservingShift, maximumShift));
        int blockYShift = (targetMinSection + chosenShift - sourceMinSection) << 4;
        return new PlotVerticalLayout(chosenShift, blockYShift);
    }

    public void apply(CompoundTag plotTag, ServerLevel targetLevel) throws IOException {
        apply(plotTag, targetLevel.getMinSection());
    }

    public void apply(CompoundTag plotTag, int targetMinSection) throws IOException {
        CompoundTag chunks = plotTag.getCompound(CHUNKS_TAG);
        for (String chunkKey : chunks.getAllKeys()) {
            CompoundTag chunkTag = chunks.getCompound(chunkKey);
            removeWorldGenerationData(chunkTag);
            remapSections(chunkTag);
            shiftPositionList(chunkTag.getList("block_entities", Tag.TAG_COMPOUND));
            shiftPositionList(chunkTag.getList("block_ticks", Tag.TAG_COMPOUND));
            shiftPositionList(chunkTag.getList("fluid_ticks", Tag.TAG_COMPOUND));
            chunkTag.putInt("yPos", targetMinSection);
        }
    }

    public int shiftBlockY(int sourceY) {
        return sourceY + this.blockYShift;
    }

    private void remapSections(CompoundTag chunkTag) throws IOException {
        CompoundTag sections = chunkTag.getCompound(SECTIONS_TAG);
        CompoundTag remapped = new CompoundTag();
        for (String sectionKey : sections.getAllKeys()) {
            int savedIndex = parseSectionIndex(sectionKey);
            remapped.put(String.valueOf(savedIndex + this.sectionIndexShift), sections.getCompound(sectionKey));
        }
        chunkTag.put(SECTIONS_TAG, remapped);
    }

    private void shiftPositionList(ListTag list) {
        if (this.blockYShift == 0) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.contains("y", Tag.TAG_INT)) {
                entry.putInt("y", shiftBlockY(entry.getInt("y")));
            } else if (entry.contains("Y", Tag.TAG_INT)) {
                entry.putInt("Y", shiftBlockY(entry.getInt("Y")));
            }
        }
    }

    private static void removeWorldGenerationData(CompoundTag chunkTag) {
        chunkTag.remove("Heightmaps");
        chunkTag.remove("heightmaps");
        chunkTag.remove("below_zero_retrogen");
        chunkTag.remove("blending_data");
    }

    private static int parseSectionIndex(String value) throws IOException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IOException("invalid plot section index: " + value, exception);
        }
    }
}
