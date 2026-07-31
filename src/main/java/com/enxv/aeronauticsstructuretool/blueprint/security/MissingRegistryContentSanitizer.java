package com.enxv.aeronauticsstructuretool.blueprint.security;

import com.enxv.aeronauticsstructuretool.blueprint.codec.NativeBlueprintFormat;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biomes;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

public final class MissingRegistryContentSanitizer {
    private static final String AIR_BLOCK = "minecraft:air";

    private MissingRegistryContentSanitizer() {
    }

    public static Result sanitizeNative(CompoundTag root) throws IOException {
        return sanitizeNative(root, (Predicate<ResourceLocation>) null);
    }

    public static Result sanitizeNative(CompoundTag root, RegistryAccess registryAccess) throws IOException {
        var biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);
        return sanitizeNative(root, biomeRegistry::containsKey);
    }

    static Result sanitizeNative(
            CompoundTag root,
            Predicate<ResourceLocation> biomeAvailable
    ) throws IOException {
        MutableResult skipped = new MutableResult();
        int sourceMinBuildHeight = root.contains(
                NativeBlueprintFormat.SOURCE_MIN_BUILD_HEIGHT_TAG,
                Tag.TAG_INT
        )
                ? root.getInt(NativeBlueprintFormat.SOURCE_MIN_BUILD_HEIGHT_TAG)
                : NativeBlueprintFormat.LEGACY_V8_MIN_BUILD_HEIGHT;
        ListTag sublevels = root.getList(NativeBlueprintFormat.SUBLEVELS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < sublevels.size(); i++) {
            CompoundTag sublevel = sublevels.getCompound(i);
            sanitizePlot(
                    sublevel.getCompound(NativeBlueprintFormat.PLOT_TAG),
                    sourceMinBuildHeight,
                    skipped,
                    biomeAvailable
            );
            sanitizeRuntimeContraptions(
                    sublevel.getList(NativeBlueprintFormat.RUNTIME_CONTRAPTIONS_TAG, Tag.TAG_COMPOUND),
                    skipped
            );
        }
        return skipped.toResult();
    }

    public static Result sanitizeCreatePhysical(CompoundTag root) throws IOException {
        MutableResult skipped = new MutableResult();
        sanitizeIndexedStructure(root, "palette", "blocks", "state", skipped);
        ListTag sublevels = root.getList("sub_levels", Tag.TAG_COMPOUND);
        for (int i = 0; i < sublevels.size(); i++) {
            sanitizeIndexedStructure(sublevels.getCompound(i), "palette", "blocks", "state", skipped);
        }
        return skipped.toResult();
    }

    private static void sanitizeRuntimeContraptions(ListTag runtimeContraptions, MutableResult skipped)
            throws IOException {
        for (int i = 0; i < runtimeContraptions.size(); i++) {
            CompoundTag blocks = runtimeContraptions.getCompound(i)
                    .getCompound("contraption")
                    .getCompound("Blocks");
            if (!blocks.isEmpty()) {
                sanitizeIndexedStructure(blocks, "Palette", "BlockList", "State", skipped);
            }
        }
    }

    private static void sanitizePlot(
            CompoundTag plot,
            int sourceMinBuildHeight,
            MutableResult skipped,
            Predicate<ResourceLocation> biomeAvailable
    ) throws IOException {
        sanitizePlotBiome(plot, skipped, biomeAvailable);
        CompoundTag chunks = plot.getCompound("chunks");
        int sourceMinSection = SectionPos.blockToSectionCoord(sourceMinBuildHeight);
        for (String chunkKey : chunks.getAllKeys()) {
            CompoundTag chunk = chunks.getCompound(chunkKey);
            CompoundTag sections = chunk.getCompound("sections");
            Map<Integer, MissingSection> missingBySection = new HashMap<>();
            for (String sectionKey : sections.getAllKeys()) {
                int sectionIndex = parseSectionIndex(sectionKey);
                CompoundTag blockStates = sections.getCompound(sectionKey).getCompound("block_states");
                ListTag palette = blockStates.getList("palette", Tag.TAG_COMPOUND);
                if (palette.isEmpty()) {
                    continue;
                }
                MissingSection missing = inspectPlotPalette(blockStates, palette, skipped, sectionKey);
                if (missing.hasMissingBlocks()) {
                    missingBySection.put(sectionIndex, missing);
                }
            }
            removeMissingPlotBlockEntities(chunk, missingBySection, sourceMinSection, skipped);
        }
    }

    private static void sanitizePlotBiome(
            CompoundTag plot,
            MutableResult skipped,
            Predicate<ResourceLocation> biomeAvailable
    ) throws IOException {
        if (biomeAvailable == null || !plot.contains("biome", Tag.TAG_STRING)) {
            return;
        }
        String rawBiome = plot.getString("biome");
        ResourceLocation biomeId = ResourceLocation.tryParse(rawBiome);
        if (biomeId != null && biomeAvailable.test(biomeId)) {
            return;
        }
        ResourceLocation fallback = Biomes.PLAINS.location();
        if (!biomeAvailable.test(fallback)) {
            throw new IOException("fallback biome is unavailable: " + fallback);
        }
        plot.putString("biome", fallback.toString());
        skipped.biome(rawBiome.isBlank() ? "<invalid>" : rawBiome);
    }

    private static MissingSection inspectPlotPalette(
            CompoundTag blockStates,
            ListTag palette,
            MutableResult skipped,
            String sectionKey
    ) throws IOException {
        boolean[] missing = new boolean[palette.size()];
        String[] blockIds = new String[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag entry = palette.getCompound(i);
            String rawId = entry.getString("Name");
            if (isMissingBlock(rawId)) {
                missing[i] = true;
                blockIds[i] = rawId;
            }
        }
        if (!containsTrue(missing)) {
            return new MissingSection(missing, blockIds, new long[0], palette.size());
        }

        long[] data = blockStates.getLongArray("data");
        int bits = bitsPerEntry(palette.size());
        int valuesPerLong = Math.max(1, 64 / bits);
        if (palette.size() > 1) {
            int requiredLongs = (4096 + valuesPerLong - 1) / valuesPerLong;
            if (data.length < requiredLongs) {
                throw new IOException(
                        "plot section '" + sectionKey + "' block-state data is truncated while skipping missing blocks"
                );
            }
        }
        for (int index = 0; index < 4096; index++) {
            int paletteIndex = paletteIndex(data, index, valuesPerLong, bits);
            if (paletteIndex < 0 || paletteIndex >= palette.size()) {
                throw new IOException(
                        "plot section '" + sectionKey + "' references invalid palette index " + paletteIndex
                );
            }
            if (missing[paletteIndex]) {
                skipped.block(blockIds[paletteIndex]);
            }
        }
        for (int i = 0; i < palette.size(); i++) {
            if (missing[i]) {
                CompoundTag replacement = palette.getCompound(i);
                replacement.putString("Name", AIR_BLOCK);
                replacement.remove("Properties");
            }
        }
        return new MissingSection(missing, blockIds, data, palette.size());
    }

    private static void removeMissingPlotBlockEntities(
            CompoundTag chunk,
            Map<Integer, MissingSection> missingBySection,
            int sourceMinSection,
            MutableResult skipped
    ) {
        ListTag blockEntities = chunk.getList("block_entities", Tag.TAG_COMPOUND);
        for (int i = blockEntities.size() - 1; i >= 0; i--) {
            CompoundTag blockEntity = blockEntities.getCompound(i);
            String id = blockEntity.getString("id");
            boolean missingBlock = isMissingBlockAt(blockEntity, missingBySection, sourceMinSection);
            boolean missingType = isMissingBlockEntity(id);
            if (!missingBlock && !missingType) {
                continue;
            }
            blockEntities.remove(i);
            skipped.blockEntity(id.isBlank() ? "unknown" : id);
        }
    }

    private static boolean isMissingBlockAt(
            CompoundTag blockEntity,
            Map<Integer, MissingSection> missingBySection,
            int sourceMinSection
    ) {
        int sectionIndex = SectionPos.blockToSectionCoord(blockEntity.getInt("y")) - sourceMinSection;
        MissingSection section = missingBySection.get(sectionIndex);
        if (section == null) {
            return false;
        }
        int localX = Math.floorMod(blockEntity.getInt("x"), 16);
        int localY = Math.floorMod(blockEntity.getInt("y"), 16);
        int localZ = Math.floorMod(blockEntity.getInt("z"), 16);
        int blockIndex = (localY << 8) | (localZ << 4) | localX;
        int bits = bitsPerEntry(section.paletteSize());
        int valuesPerLong = Math.max(1, 64 / bits);
        int paletteIndex = paletteIndex(section.data(), blockIndex, valuesPerLong, bits);
        return paletteIndex >= 0
                && paletteIndex < section.missing().length
                && section.missing()[paletteIndex];
    }

    private static void sanitizeIndexedStructure(
            CompoundTag structure,
            String paletteKey,
            String blocksKey,
            String stateKey,
            MutableResult skipped
    ) throws IOException {
        ListTag palette = structure.getList(paletteKey, Tag.TAG_COMPOUND);
        ListTag blocks = structure.getList(blocksKey, Tag.TAG_COMPOUND);
        if (palette.isEmpty() || blocks.isEmpty()) {
            return;
        }
        boolean[] missing = new boolean[palette.size()];
        String[] blockIds = new String[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            String id = palette.getCompound(i).getString("Name");
            if (isMissingBlock(id)) {
                missing[i] = true;
                blockIds[i] = id;
            }
        }
        for (int i = blocks.size() - 1; i >= 0; i--) {
            CompoundTag block = blocks.getCompound(i);
            if (!block.contains(stateKey, Tag.TAG_ANY_NUMERIC)) {
                continue;
            }
            int paletteIndex = block.getInt(stateKey);
            if (paletteIndex < 0 || paletteIndex >= palette.size()) {
                throw new IOException("structure block references invalid palette index " + paletteIndex);
            }
            if (missing[paletteIndex]) {
                skipped.block(blockIds[paletteIndex]);
                countAndRemoveBlockEntityData(block, skipped);
                blocks.remove(i);
                continue;
            }
            removeUnknownBlockEntityData(block, skipped);
        }
    }

    private static void countAndRemoveBlockEntityData(CompoundTag block, MutableResult skipped) {
        for (String key : new String[]{"nbt", "Data"}) {
            if (!block.contains(key, Tag.TAG_COMPOUND)) {
                continue;
            }
            String id = block.getCompound(key).getString("id");
            if (!id.isBlank()) {
                skipped.blockEntity(id);
            }
            block.remove(key);
        }
    }

    private static void removeUnknownBlockEntityData(CompoundTag block, MutableResult skipped) {
        for (String key : new String[]{"nbt", "Data"}) {
            if (!block.contains(key, Tag.TAG_COMPOUND)) {
                continue;
            }
            String id = block.getCompound(key).getString("id");
            if (!isMissingBlockEntity(id)) {
                continue;
            }
            block.remove(key);
            skipped.blockEntity(id);
        }
    }

    private static boolean isMissingBlock(String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        return id != null && !BuiltInRegistries.BLOCK.containsKey(id);
    }

    private static boolean isMissingBlockEntity(String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        return id != null && !BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id);
    }

    private static int parseSectionIndex(String value) throws IOException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IOException("invalid plot section index: " + value, exception);
        }
    }

    private static int bitsPerEntry(int paletteSize) {
        return Math.max(4, 32 - Integer.numberOfLeadingZeros(Math.max(0, paletteSize - 1)));
    }

    private static int paletteIndex(long[] data, int index, int valuesPerLong, int bits) {
        if (data.length == 0) {
            return 0;
        }
        int arrayIndex = index / valuesPerLong;
        if (arrayIndex < 0 || arrayIndex >= data.length) {
            return -1;
        }
        int bitIndex = (index % valuesPerLong) * bits;
        long mask = (1L << bits) - 1L;
        return (int) ((data[arrayIndex] >>> bitIndex) & mask);
    }

    private static boolean containsTrue(boolean[] values) {
        for (boolean value : values) {
            if (value) {
                return true;
            }
        }
        return false;
    }

    public record Result(
            Map<String, Long> skippedBlocks,
            Map<String, Long> skippedBlockEntities,
            Map<String, Long> replacedBiomes
    ) {
        public Result {
            skippedBlocks = immutableCopy(skippedBlocks);
            skippedBlockEntities = immutableCopy(skippedBlockEntities);
            replacedBiomes = immutableCopy(replacedBiomes);
        }

        public boolean isEmpty() {
            return skippedBlocks.isEmpty()
                    && skippedBlockEntities.isEmpty()
                    && replacedBiomes.isEmpty();
        }

        private static Map<String, Long> immutableCopy(Map<String, Long> values) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }

    private record MissingSection(boolean[] missing, String[] blockIds, long[] data, int paletteSize) {
        boolean hasMissingBlocks() {
            return containsTrue(this.missing);
        }
    }

    private static final class MutableResult {
        private final Map<String, Long> blocks = new LinkedHashMap<>();
        private final Map<String, Long> blockEntities = new LinkedHashMap<>();
        private final Map<String, Long> biomes = new LinkedHashMap<>();

        void block(String id) {
            this.blocks.merge(id, 1L, Long::sum);
        }

        void blockEntity(String id) {
            this.blockEntities.merge(id, 1L, Long::sum);
        }

        void biome(String id) {
            this.biomes.merge(id, 1L, Long::sum);
        }

        Result toResult() {
            return new Result(this.blocks, this.blockEntities, this.biomes);
        }
    }
}
