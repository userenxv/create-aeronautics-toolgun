package com.enxv.aeronauticsstructuretool.verification;

import com.enxv.aeronauticsstructuretool.BearingAxisMode;
import com.enxv.aeronauticsstructuretool.BlueprintMaterialSummary;
import com.enxv.aeronauticsstructuretool.ConnectionMode;
import com.enxv.aeronauticsstructuretool.PlacementSnapMode;
import com.enxv.aeronauticsstructuretool.RotationAxisMode;
import com.enxv.aeronauticsstructuretool.blueprint.importer.vmod.VModNativeBlueprintEncoder;
import com.enxv.aeronauticsstructuretool.WeldSelectionMode;
import com.enxv.aeronauticsstructuretool.blueprint.codec.BlueprintArchiveCodec;
import com.enxv.aeronauticsstructuretool.blueprint.codec.NativeBlueprintFormat;
import com.enxv.aeronauticsstructuretool.blueprint.codec.NativeBlueprintReader;
import com.enxv.aeronauticsstructuretool.blueprint.codec.NbtTransformCodec;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockDataReader;
import com.enxv.aeronauticsstructuretool.blueprint.model.NativeBlueprintDocument;
import com.enxv.aeronauticsstructuretool.blueprint.placement.BlueprintVerticalPlacement;
import com.enxv.aeronauticsstructuretool.blueprint.placement.CreatePhysicalBlueprintService;
import com.enxv.aeronauticsstructuretool.blueprint.security.BlueprintBlockEntitySanitizer;
import com.enxv.aeronauticsstructuretool.blueprint.security.MissingRegistryContentSanitizer;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.fml.loading.LoadingModList;
import org.joml.Vector3d;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class BlueprintDataRegressionCheck {
    private BlueprintDataRegressionCheck() {
    }

    public static void main(String[] args) throws Exception {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        verifyStrictModeParsing();
        verifyLegacyNativeTransformsAndStrictAnchorSpace();
        verifyStrictPlotDecoding();
        verifyStrictVerticalPlacement();
        verifyCreatePhysicalProbe();
        verifyBlueprintSanitization();
        verifyMissingRegistryContentFallback();
        verifyMaterialAccounting();
        verifyVModImport();
    }

    private static void verifyStrictModeParsing() {
        require(BearingAxisMode.fromName("") == BearingAxisMode.AUTO, "blank bearing mode must keep legacy default");
        require(ConnectionMode.fromName(null) == ConnectionMode.FIXED, "missing connection mode must keep legacy default");
        require(RotationAxisMode.fromName("z") == RotationAxisMode.Z, "rotation mode parsing must be case-insensitive");
        require(PlacementSnapMode.fromName("hit") == PlacementSnapMode.HIT, "snap mode parsing must be case-insensitive");
        require(WeldSelectionMode.fromName("free") == WeldSelectionMode.FREE, "weld mode parsing must be case-insensitive");

        expectIllegalArgument(() -> BearingAxisMode.fromName("sideways"), "bearing");
        expectIllegalArgument(() -> ConnectionMode.fromName("spring"), "connection");
        expectIllegalArgument(() -> RotationAxisMode.fromName("w"), "rotation");
        expectIllegalArgument(() -> PlacementSnapMode.fromName("nearest"), "snap");
        expectIllegalArgument(() -> WeldSelectionMode.fromName("random"), "weld");
    }

    private static void verifyLegacyNativeTransformsAndStrictAnchorSpace() throws Exception {
        CompoundTag legacyRoot = minimalNativeRoot(singleBlockPlot(false));
        NativeBlueprintDocument document = NativeBlueprintReader.read(legacyRoot);
        require(document.rootOrientation().w == 1.0D, "missing legacy root orientation must use identity");
        require(document.rootRotationOffset().lengthSquared() == 0.0D, "missing legacy root rotation offset must use zero");
        require(document.sublevels().getFirst().relativeOrientation().w == 1.0D,
                "missing legacy sublevel orientation must use identity");

        CompoundTag badAnchorSpace = legacyRoot.copy();
        CompoundTag sublevel = badAnchorSpace.getList(
                NativeBlueprintFormat.SUBLEVELS_TAG,
                Tag.TAG_COMPOUND
        ).getCompound(0);
        sublevel.put(
                NativeBlueprintFormat.LOCAL_ANCHOR_TAG,
                NbtTransformCodec.writeVector(new Vector3d(0.0D, -64.0D, 0.0D))
        );
        sublevel.putString("local_anchor_space", "unknown_space");
        expectIOException(() -> NativeBlueprintReader.read(badAnchorSpace), "local_anchor_space");

        CompoundTag wrongHeightType = legacyRoot.copy();
        wrongHeightType.putString(NativeBlueprintFormat.SOURCE_MIN_BUILD_HEIGHT_TAG, "-64");
        expectIOException(() -> NativeBlueprintReader.read(wrongHeightType), "source_min_build_height");

        CompoundTag unsupported = legacyRoot.copy();
        unsupported.putString(NativeBlueprintFormat.FORMAT_TAG, "future_format");
        expectIOException(() -> NativeBlueprintReader.read(unsupported), "unsupported native blueprint format");
    }

    private static void verifyStrictPlotDecoding() throws Exception {
        CompoundTag valid = singleBlockPlot(false);
        require(PlotBlockDataReader.read(valid, -64).size() == 1, "valid packed Plot section must decode one block");

        CompoundTag truncated = singleBlockPlot(true);
        expectIOException(() -> PlotBlockDataReader.read(truncated, -64), "truncated");
    }

    private static void verifyStrictVerticalPlacement() {
        BlueprintVerticalPlacement placement = BlueprintVerticalPlacement.alignMinimumCenter(10.0D, Double.NaN);
        expectIllegalArgument(
                () -> placement.apply(new Vector3d(0.0D, 0.0D, 0.0D), Double.NaN),
                "minimum block height"
        );
        expectIllegalArgument(
                () -> BlueprintVerticalPlacement.unchanged().apply(
                        new Vector3d(Double.NaN, 0.0D, 0.0D),
                        Double.NaN
                ),
                "finite"
        );
    }

    private static void verifyCreatePhysicalProbe() {
        expectIOException(
                () -> CreatePhysicalBlueprintService.hasCreatePhysicalLayout(new byte[]{1, 2, 3, 4}),
                ""
        );
    }

    private static void verifyBlueprintSanitization() {
        CompoundTag sign = new CompoundTag();
        sign.putString("id", "minecraft:sign");
        CompoundTag frontText = new CompoundTag();
        ListTag messages = new ListTag();
        messages.add(net.minecraft.nbt.StringTag.valueOf(
                "{\"text\":\"unsafe\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/op @s\"}}"
        ));
        messages.add(net.minecraft.nbt.StringTag.valueOf("{invalid"));
        frontText.put("messages", messages);
        sign.put("front_text", frontText);

        CompoundTag shaft = new CompoundTag();
        shaft.putString("id", "createdieselgenerators:powered_engine_shaft_block_entity");
        ListTag engines = new ListTag();
        engines.add(new CompoundTag());
        engines.add(new CompoundTag());
        shaft.put("Engines", engines);
        shaft.putFloat("GeneratedSpeed", 128.0F);
        shaft.putInt("Direction", -1);
        shaft.putInt("Warmup", 20);

        ListTag blockEntities = new ListTag();
        blockEntities.add(sign);
        blockEntities.add(shaft);
        CompoundTag root = new CompoundTag();
        root.put("block_entities", blockEntities);

        BlueprintBlockEntitySanitizer.Result result = BlueprintBlockEntitySanitizer.sanitize(root);
        require(result.signClickEventsRemoved() == 1, "sign click event must be removed");
        require(result.invalidSignMessagesCleared() == 1, "malformed sign JSON must be cleared");
        require(result.dieselShaftsReset() == 1, "diesel shaft runtime state must be reset");
        require(result.dieselEngineReferencesRemoved() == 2, "all stale diesel engine references must be removed");
        require(!messages.getString(0).contains("clickEvent"), "sanitized sign retained clickEvent");
        require(messages.getString(1).equals("\"\""), "malformed sign message was not replaced with empty text");
        require(shaft.getList("Engines", Tag.TAG_COMPOUND).isEmpty(), "diesel engine references survived sanitization");
        require(shaft.getFloat("GeneratedSpeed") == 0.0F, "diesel generated speed survived sanitization");
        require(shaft.getInt("Direction") == 0, "diesel movement direction survived sanitization");
        require(!shaft.contains("Warmup"), "legacy diesel warmup state survived sanitization");

        BlueprintBlockEntitySanitizer.Result repeated = BlueprintBlockEntitySanitizer.sanitize(root);
        require(!repeated.changed(), "blueprint sanitization must be idempotent");
    }

    private static void verifyMissingRegistryContentFallback() throws Exception {
        CompoundTag plot = singleBlockPlot(false);
        CompoundTag chunk = plot.getCompound("chunks").getCompound(Long.toString(ChunkPos.asLong(0, 0)));
        CompoundTag blockStates = chunk.getCompound("sections")
                .getCompound("0")
                .getCompound("block_states");
        blockStates.getList("palette", Tag.TAG_COMPOUND)
                .getCompound(1)
                .putString("Name", "missing_mod:cable");
        CompoundTag missingBlockEntity = new CompoundTag();
        missingBlockEntity.putString("id", "missing_mod:cable_block_entity");
        missingBlockEntity.putInt("x", 0);
        missingBlockEntity.putInt("y", -64);
        missingBlockEntity.putInt("z", 0);
        chunk.getList("block_entities", Tag.TAG_COMPOUND).add(missingBlockEntity);

        CompoundTag root = minimalNativeRoot(plot);
        CompoundTag sublevel = root.getList(NativeBlueprintFormat.SUBLEVELS_TAG, Tag.TAG_COMPOUND)
                .getCompound(0);
        ListTag runtimeContraptions = runtimeContraptions();
        CompoundTag runtimeBlock = runtimeContraptions.getCompound(0)
                .getCompound("contraption")
                .getCompound("Blocks")
                .getList("BlockList", Tag.TAG_COMPOUND)
                .getCompound(0);
        runtimeContraptions.getCompound(0)
                .getCompound("contraption")
                .getCompound("Blocks")
                .getList("Palette", Tag.TAG_COMPOUND)
                .getCompound(0)
                .putString("Name", "missing_mod:motor");
        CompoundTag runtimeBlockEntity = new CompoundTag();
        runtimeBlockEntity.putString("id", "missing_mod:motor_block_entity");
        runtimeBlock.put("Data", runtimeBlockEntity);
        sublevel.put(NativeBlueprintFormat.RUNTIME_CONTRAPTIONS_TAG, runtimeContraptions);

        BlueprintMaterialSummary unsanitizedSummary = BlueprintMaterialSummary.captureFromSublevels(
                root.getList(NativeBlueprintFormat.SUBLEVELS_TAG, Tag.TAG_COMPOUND),
                -64
        );
        require(unsanitizedSummary.blockCounts().isEmpty(),
                "missing registry blocks must not make a blueprint material check fail or require placeholder items");

        MissingRegistryContentSanitizer.Result skipped =
                MissingRegistryContentSanitizer.sanitizeNative(root);
        require(skipped.skippedBlocks().getOrDefault("missing_mod:cable", 0L) == 1L,
                "missing Plot block count must match actual palette references");
        require(skipped.skippedBlocks().getOrDefault("missing_mod:motor", 0L) == 1L,
                "missing runtime block count must match actual block-list references");
        require(skipped.skippedBlockEntities().getOrDefault("missing_mod:cable_block_entity", 0L) == 1L,
                "missing Plot block entity must be reported");
        require(skipped.skippedBlockEntities().getOrDefault("missing_mod:motor_block_entity", 0L) == 1L,
                "missing runtime block entity must be reported");
        require(chunk.getList("block_entities", Tag.TAG_COMPOUND).isEmpty(),
                "missing Plot block entity survived fallback sanitization");
        require(PlotBlockDataReader.read(plot, -64).isEmpty(),
                "missing Plot block was not replaced with air");
        require(runtimeContraptions.getCompound(0)
                        .getCompound("contraption")
                        .getCompound("Blocks")
                        .getList("BlockList", Tag.TAG_COMPOUND)
                        .isEmpty(),
                "missing runtime block survived fallback sanitization");
        NativeBlueprintReader.read(root);

        CompoundTag createRoot = new CompoundTag();
        ListTag createPalette = new ListTag();
        createPalette.add(blockState("missing_mod:create_part"));
        createRoot.put("palette", createPalette);
        ListTag createBlocks = new ListTag();
        CompoundTag createBlock = new CompoundTag();
        createBlock.putInt("state", 0);
        CompoundTag createBlockEntity = new CompoundTag();
        createBlockEntity.putString("id", "missing_mod:create_part_block_entity");
        createBlock.put("nbt", createBlockEntity);
        createBlocks.add(createBlock);
        createRoot.put("blocks", createBlocks);
        MissingRegistryContentSanitizer.Result createSkipped =
                MissingRegistryContentSanitizer.sanitizeCreatePhysical(createRoot);
        require(createBlocks.isEmpty(), "missing Create physical block survived fallback sanitization");
        require(createSkipped.skippedBlocks().getOrDefault("missing_mod:create_part", 0L) == 1L,
                "missing Create physical block was not reported");
        require(createSkipped.skippedBlockEntities()
                        .getOrDefault("missing_mod:create_part_block_entity", 0L) == 1L,
                "missing Create physical block entity was not reported");
    }

    private static void verifyMaterialAccounting() throws Exception {
        CompoundTag plot = singleBlockPlot(false);
        CompoundTag chunk = plot.getCompound("chunks").getCompound(Long.toString(ChunkPos.asLong(0, 0)));
        ListTag blockEntities = new ListTag();
        blockEntities.add(dieselUpgradeBlockEntity(0, -64, 0, "createdieselgenerators:silencer"));
        blockEntities.add(dieselUpgradeBlockEntity(1, -64, 0, "createdieselgenerators:turbocharger"));
        chunk.put("block_entities", blockEntities);

        CompoundTag sublevel = new CompoundTag();
        sublevel.put("plot", plot);
        sublevel.put("runtime_contraptions", runtimeContraptions());
        sublevel.put(
                BlueprintMaterialSummary.ADDITIONAL_ITEMS_TAG,
                BlueprintMaterialSummary.writeItemCounts(Map.of("minecraft:diamond", 2L))
        );
        ListTag sublevels = new ListTag();
        sublevels.add(sublevel);

        BlueprintMaterialSummary summary = BlueprintMaterialSummary.captureFromSublevels(sublevels, -64);
        require(summary.blockCounts().getOrDefault("minecraft:stone", 0L) == 2L,
                "plot and runtime stone blocks must both be counted");
        require(summary.itemCounts().getOrDefault("createdieselgenerators:engine_silencer", 0L) == 1L,
                "diesel silencer upgrade must be included in survival materials");
        require(summary.itemCounts().getOrDefault("createdieselgenerators:engine_turbocharger", 0L) == 1L,
                "diesel turbocharger upgrade must be included in survival materials");
        require(summary.itemCounts().getOrDefault("minecraft:iron_ingot", 0L) == 3L,
                "runtime contraption inventory must be included in survival materials");
        require(summary.itemCounts().getOrDefault("minecraft:diamond", 0L) == 2L,
                "captured additional items must be included in survival materials");

        CompoundTag malformedSummary = new CompoundTag();
        malformedSummary.put("blocks", new ListTag());
        ListTag badItems = new ListTag();
        CompoundTag badItem = new CompoundTag();
        badItem.putString("id", "minecraft:iron_ingot");
        badItem.putLong("count", -1L);
        badItems.add(badItem);
        malformedSummary.put("items", badItems);
        expectIllegalArgument(() -> BlueprintMaterialSummary.fromTag(malformedSummary), "non-positive");
    }

    private static void verifyVModImport() throws Exception {
        CompoundTag validRoot = vmodRoot(vmodBlock(0, 0, 0, 0));
        byte[] imported = VModNativeBlueprintEncoder.importToNative("vmod_regression", encodeVMod(validRoot));
        NativeBlueprintDocument importedDocument = NativeBlueprintReader.read(BlueprintArchiveCodec.decode(imported));
        require(importedDocument.sublevels().size() == 1, "valid VMod import must create one native sublevel");
        require(PlotBlockDataReader.read(
                importedDocument.sublevels().getFirst().plotTag(),
                importedDocument.sourceMinBuildHeight()
        ).size() == 1, "valid VMod import must preserve its block");

        CompoundTag badPalette = vmodRoot(vmodBlock(1, 0, 0, 0));
        expectIOException(
                () -> VModNativeBlueprintEncoder.importToNative("bad_palette", encodeVMod(badPalette)),
                "palette index"
        );

        CompoundTag missingCoordinate = vmodRoot(vmodBlock(0, 0, 0, 0));
        missingCoordinate.getCompound("gridData").getList("1", Tag.TAG_COMPOUND).getCompound(0).remove("x");
        expectIOException(
                () -> VModNativeBlueprintEncoder.importToNative("missing_coordinate", encodeVMod(missingCoordinate)),
                "numeric 'x'"
        );

        CompoundTag duplicate = vmodRoot(vmodBlock(0, 0, 0, 0));
        duplicate.getCompound("gridData").getList("1", Tag.TAG_COMPOUND).add(vmodBlock(0, 0, 0, 0));
        expectIOException(
                () -> VModNativeBlueprintEncoder.importToNative("duplicate", encodeVMod(duplicate)),
                "duplicate VMod block position"
        );

        CompoundTag invalidPose = vmodRoot(vmodBlock(0, 0, 0, 0));
        CompoundTag shipData = new CompoundTag();
        ListTag shipInfo = new ListTag();
        CompoundTag info = new CompoundTag();
        info.putLong("id", 1L);
        CompoundTag position = vectorTag(Double.NaN, 0.0D, 0.0D);
        info.put("rptc", position);
        shipInfo.add(info);
        shipData.put("data", shipInfo);
        invalidPose.put("shipData", shipData);
        expectIOException(
                () -> VModNativeBlueprintEncoder.importToNative("invalid_pose", encodeVMod(invalidPose)),
                "must be finite"
        );

        CompoundTag oversized = vmodRoot(
                vmodBlock(0, 0, 0, 0),
                vmodBlock(0, 5000, 0, 0)
        );
        expectIOException(
                () -> VModNativeBlueprintEncoder.importToNative("oversized", encodeVMod(oversized)),
                "exceeds native plot bounds"
        );

        CompoundTag missingName = vmodRoot(vmodBlock(0, 0, 0, 0));
        missingName.getList("blockPalette", Tag.TAG_COMPOUND).getCompound(0).remove("Name");
        expectIOException(
                () -> VModNativeBlueprintEncoder.importToNative("missing_name", encodeVMod(missingName)),
                "block name"
        );
    }

    private static CompoundTag minimalNativeRoot(CompoundTag plot) {
        java.util.UUID id = java.util.UUID.randomUUID();
        CompoundTag sublevel = new CompoundTag();
        sublevel.putUUID(NativeBlueprintFormat.SUBLEVEL_ID_TAG, id);
        sublevel.put(NativeBlueprintFormat.PLOT_TAG, plot);
        sublevel.put(
                NativeBlueprintFormat.RELATIVE_POSITION_TAG,
                NbtTransformCodec.writeVector(new Vector3d(0.0D, -64.0D, 0.0D))
        );
        ListTag sublevels = new ListTag();
        sublevels.add(sublevel);

        CompoundTag root = new CompoundTag();
        root.putString(NativeBlueprintFormat.FORMAT_TAG, NativeBlueprintFormat.FORMAT_V8);
        root.putUUID(NativeBlueprintFormat.ROOT_SUBLEVEL_TAG, id);
        root.put(NativeBlueprintFormat.SUBLEVELS_TAG, sublevels);
        return root;
    }

    private static CompoundTag singleBlockPlot(boolean truncateData) {
        ListTag palette = new ListTag();
        palette.add(blockState("minecraft:air"));
        palette.add(blockState("minecraft:stone"));
        CompoundTag blockStates = new CompoundTag();
        blockStates.put("palette", palette);
        long[] packed = new long[truncateData ? 1 : 256];
        packed[0] = 1L;
        blockStates.putLongArray("data", packed);

        CompoundTag section = new CompoundTag();
        section.put("block_states", blockStates);
        CompoundTag sections = new CompoundTag();
        sections.put("0", section);
        CompoundTag chunk = new CompoundTag();
        chunk.put("sections", sections);
        chunk.put("block_entities", new ListTag());
        CompoundTag chunks = new CompoundTag();
        chunks.put(Long.toString(ChunkPos.asLong(0, 0)), chunk);
        CompoundTag plot = new CompoundTag();
        plot.put("chunks", chunks);
        return plot;
    }

    private static CompoundTag dieselUpgradeBlockEntity(int x, int y, int z, String upgrade) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        tag.putString("Upgrade", upgrade);
        return tag;
    }

    private static ListTag runtimeContraptions() {
        CompoundTag blocks = new CompoundTag();
        ListTag palette = new ListTag();
        palette.add(blockState("minecraft:stone"));
        blocks.put("Palette", palette);
        ListTag blockList = new ListTag();
        CompoundTag block = new CompoundTag();
        block.putInt("State", 0);
        blockList.add(block);
        blocks.put("BlockList", blockList);

        CompoundTag contraption = new CompoundTag();
        contraption.put("Blocks", blocks);
        CompoundTag runtime = new CompoundTag();
        runtime.putString("kind", "create_controlled");
        runtime.put("controller", NbtUtils.writeBlockPos(new BlockPos(0, -64, 0)));
        runtime.put("contraption", contraption);
        runtime.put(
                BlueprintMaterialSummary.RUNTIME_ITEMS_TAG,
                BlueprintMaterialSummary.writeItemCounts(Map.of("minecraft:iron_ingot", 3L))
        );
        ListTag list = new ListTag();
        list.add(runtime);
        return list;
    }

    private static CompoundTag vmodRoot(CompoundTag... blocks) {
        CompoundTag root = new CompoundTag();
        ListTag palette = new ListTag();
        palette.add(blockState("minecraft:stone"));
        root.put("blockPalette", palette);
        root.put("extraBlockData", new ListTag());
        ListTag blockList = new ListTag();
        for (CompoundTag block : blocks) {
            blockList.add(block);
        }
        CompoundTag gridData = new CompoundTag();
        gridData.put("1", blockList);
        root.put("gridData", gridData);
        return root;
    }

    private static CompoundTag vmodBlock(int paletteId, int x, int y, int z) {
        CompoundTag block = new CompoundTag();
        block.putInt("pid", paletteId);
        block.putInt("edi", -1);
        block.putInt("x", x);
        block.putInt("y", y);
        block.putInt("z", z);
        return block;
    }

    private static byte[] encodeVMod(CompoundTag root) throws IOException {
        ByteArrayOutputStream payloadOutput = new ByteArrayOutputStream();
        NbtIo.writeCompressed(root, payloadOutput);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeUtf("vschem");
            buffer.writeUtf("regression");
            buffer.writeBytes(payloadOutput.toByteArray());
            byte[] raw = new byte[buffer.readableBytes()];
            buffer.readBytes(raw);
            return raw;
        } finally {
            buffer.release();
        }
    }

    private static CompoundTag blockState(String name) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        return tag;
    }

    private static CompoundTag vectorTag(double x, double y, double z) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        tag.putDouble("z", z);
        return tag;
    }

    private static void expectIOException(ThrowingRunnable action, String messageFragment) {
        try {
            action.run();
        } catch (IOException exception) {
            require(messageFragment.isEmpty() || containsMessage(exception, messageFragment),
                    "IOException message must contain '" + messageFragment + "': " + exception);
            return;
        } catch (Exception exception) {
            throw new IllegalStateException("expected IOException, got " + exception, exception);
        }
        throw new IllegalStateException("expected IOException");
    }

    private static void expectIllegalArgument(ThrowingRunnable action, String messageFragment) {
        try {
            action.run();
        } catch (IllegalArgumentException exception) {
            require(containsMessage(exception, messageFragment),
                    "IllegalArgumentException message must contain '" + messageFragment + "': " + exception);
            return;
        } catch (Exception exception) {
            throw new IllegalStateException("expected IllegalArgumentException, got " + exception, exception);
        }
        throw new IllegalStateException("expected IllegalArgumentException");
    }

    private static boolean containsMessage(Throwable throwable, String fragment) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(fragment)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
