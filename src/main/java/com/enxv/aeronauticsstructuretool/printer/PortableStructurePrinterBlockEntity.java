package com.enxv.aeronauticsstructuretool.printer;

import com.enxv.aeronauticsstructuretool.BlueprintMaterialSummary;
import com.enxv.aeronauticsstructuretool.ModBlockEntities;
import com.enxv.aeronauticsstructuretool.blueprint.codec.BlueprintArchiveCodec;
import com.enxv.aeronauticsstructuretool.blueprint.material.BlueprintMaterialAnalyzer;
import com.enxv.aeronauticsstructuretool.blueprint.security.MissingRegistryContentSanitizer;
import com.enxv.aeronauticsstructuretool.core.FailureMessages;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PortableStructurePrinterBlockEntity extends BlockEntity {
    private static final String OWNER_TAG = "Owner";
    private static final String DISPLAY_NAME_TAG = "BlueprintDisplayName";
    private static final String BLUEPRINT_NAME_TAG = "BlueprintName";
    private static final String BLUEPRINT_BYTES_TAG = "BlueprintBytes";
    private static final String MATERIAL_SUMMARY_TAG = "MaterialSummary";
    private static final String PREVIEW_BOTTOM_Y_TAG = "PreviewBottomY";
    private static final String CHECKLIST_STACK_TAG = "ChecklistStack";
    private static final String PRINTING_TAG = "Printing";
    private static final String PRINT_TOTAL_BLOCKS_TAG = "PrintTotalBlocks";
    private static final String PRINT_EMITTED_BLOCKS_TAG = "PrintEmittedBlocks";
    private static final String PRINT_NEXT_EMIT_TICK_TAG = "PrintNextEmitTick";
    private static final String PRINT_FINALIZE_TICK_TAG = "PrintFinalizeTick";
    private static final String RESERVED_MATERIAL_STACKS_TAG = "ReservedMaterialStacks";

    private UUID owner;
    private String blueprintDisplayName = "";
    private String blueprintName = "";
    private byte[] blueprintBytes = new byte[0];
    private BlueprintMaterialSummary materialSummary = BlueprintMaterialSummary.empty();
    private double previewBottomY;
    private ItemStack checklistStack = ItemStack.EMPTY;
    private boolean printing;
    private int totalPrintBlocks;
    private int emittedPrintBlocks;
    private long nextEmitTick;
    private long finalizeTick = -1L;
    private List<PortableStructurePrinterPrintPlan.PrintTarget> printTargets = List.of();
    private List<ItemStack> reservedMaterialStacks = List.of();
    private String storedLoadFailure = "";

    public PortableStructurePrinterBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.PORTABLE_STRUCTURE_PRINTER.get(), pos, blockState);
    }

    public boolean hasBlueprint() {
        return this.blueprintBytes.length > 0 && !this.blueprintName.isBlank();
    }

    public UUID owner() {
        return this.owner;
    }

    public String blueprintDisplayName() {
        return this.blueprintDisplayName;
    }

    public String blueprintName() {
        return this.blueprintName;
    }

    public byte[] blueprintBytes() {
        return this.blueprintBytes;
    }

    public BlueprintMaterialSummary materialSummary() {
        return this.materialSummary;
    }

    public boolean hasStoredLoadFailure() {
        return !this.storedLoadFailure.isBlank();
    }

    public String storedLoadFailure() {
        return this.storedLoadFailure;
    }

    public double previewBottomY() {
        return this.previewBottomY;
    }

    public ItemStack checklistStack() {
        return this.checklistStack.copy();
    }

    public boolean printing() {
        return this.printing;
    }

    public float printProgress() {
        if (!this.printing || this.totalPrintBlocks <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, (float) this.emittedPrintBlocks / (float) this.totalPrintBlocks);
    }

    public void setBlueprint(UUID owner, String displayName, String blueprintName, byte[] blueprintBytes, double previewBottomY) throws Exception {
        String nextDisplayName = displayName == null ? "" : displayName;
        String nextBlueprintName = blueprintName == null ? "" : blueprintName;
        byte[] nextBlueprintBytes = blueprintBytes == null ? new byte[0] : blueprintBytes.clone();
        BlueprintMaterialSummary nextMaterialSummary = nextBlueprintBytes.length == 0
                ? BlueprintMaterialSummary.empty()
                : readMaterialSummary(nextBlueprintBytes);

        this.owner = owner;
        this.blueprintDisplayName = nextDisplayName;
        this.blueprintName = nextBlueprintName;
        this.blueprintBytes = nextBlueprintBytes;
        this.previewBottomY = previewBottomY;
        this.storedLoadFailure = "";
        this.materialSummary = nextMaterialSummary;
        setChangedAndSync();
    }

    public PortableStructurePrinterInventory.MaterialStatus materialStatus() {
        if (!hasBlueprint() || this.level == null) {
            return new PortableStructurePrinterInventory.MaterialStatus(java.util.Map.of(), java.util.Map.of());
        }
        return PortableStructurePrinterInventory.evaluate(this.materialSummary, this.level, this.worldPosition);
    }

    public void setChecklistStack(ItemStack stack) {
        this.checklistStack = stack == null ? ItemStack.EMPTY : stack.copy();
        setChangedAndSync();
    }

    public ItemStack removeChecklistStack() {
        ItemStack stack = this.checklistStack.copy();
        this.checklistStack = ItemStack.EMPTY;
        setChangedAndSync();
        return stack;
    }

    public void clearBlueprint() {
        this.blueprintDisplayName = "";
        this.blueprintName = "";
        this.blueprintBytes = new byte[0];
        this.previewBottomY = 0.0D;
        this.materialSummary = BlueprintMaterialSummary.empty();
        this.checklistStack = ItemStack.EMPTY;
        clearPrintJob();
        setChangedAndSync();
    }

    public void startPrintJob(ServerLevel level) throws IOException {
        List<PortableStructurePrinterPrintPlan.PrintTarget> targets = PortableStructurePrinterPrintPlan.createTargets(level, this.worldPosition, this.blueprintBytes);
        if (targets.isEmpty()) {
            throw new IOException("empty print plan");
        }
        this.printTargets = targets;
        this.printing = true;
        this.totalPrintBlocks = targets.size();
        this.emittedPrintBlocks = 0;
        this.nextEmitTick = level.getGameTime();
        this.finalizeTick = -1L;
        setChangedAndSync();
    }

    public void setReservedMaterialStacks(List<ItemStack> stacks) {
        this.reservedMaterialStacks = copyStacks(stacks);
    }

    public List<ItemStack> reservedMaterialStacks() {
        return copyStacks(this.reservedMaterialStacks);
    }

    public @org.jetbrains.annotations.Nullable PortableStructurePrinterPrintPlan.PrintTarget emitNextPrintTarget() {
        if (!this.printing || this.emittedPrintBlocks >= this.printTargets.size()) {
            return null;
        }
        PortableStructurePrinterPrintPlan.PrintTarget target = this.printTargets.get(this.emittedPrintBlocks);
        this.emittedPrintBlocks++;
        return target;
    }

    public void scheduleNextEmit(long tick) {
        this.nextEmitTick = tick;
        setChangedAndSync();
    }

    public void scheduleFinalize(long tick) {
        this.finalizeTick = tick;
        setChangedAndSync();
    }

    public boolean shouldEmitAt(long tick) {
        return this.printing && this.emittedPrintBlocks < this.totalPrintBlocks && tick >= this.nextEmitTick;
    }

    public boolean shouldFinalizeAt(long tick) {
        return this.printing && this.emittedPrintBlocks >= this.totalPrintBlocks && this.finalizeTick >= 0L && tick >= this.finalizeTick;
    }

    public int totalPrintBlocks() {
        return this.totalPrintBlocks;
    }

    public int emittedPrintBlocks() {
        return this.emittedPrintBlocks;
    }

    public void finishPrintJob() {
        clearPrintJob();
        setChangedAndSync();
    }

    public void clearPrintJob() {
        this.printing = false;
        this.totalPrintBlocks = 0;
        this.emittedPrintBlocks = 0;
        this.nextEmitTick = 0L;
        this.finalizeTick = -1L;
        this.printTargets = List.of();
        this.reservedMaterialStacks = List.of();
    }

    public void ensurePrintTargets(ServerLevel level) throws IOException {
        if (!this.printing || !this.printTargets.isEmpty() || this.blueprintBytes.length == 0) {
            return;
        }
        this.printTargets = PortableStructurePrinterPrintPlan.createTargets(
                level,
                this.worldPosition,
                this.blueprintBytes
        );
        if (this.printTargets.isEmpty()) {
            throw new IOException("stored portable-printer job has an empty print plan");
        }
        this.totalPrintBlocks = this.printTargets.size();
        if (this.emittedPrintBlocks > this.totalPrintBlocks) {
            this.emittedPrintBlocks = this.totalPrintBlocks;
        }
    }

    private void setChangedAndSync() {
        setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.owner != null) {
            tag.putUUID(OWNER_TAG, this.owner);
        }
        if (!this.blueprintDisplayName.isBlank()) {
            tag.putString(DISPLAY_NAME_TAG, this.blueprintDisplayName);
        }
        if (!this.blueprintName.isBlank()) {
            tag.putString(BLUEPRINT_NAME_TAG, this.blueprintName);
        }
        if (this.blueprintBytes.length > 0) {
            tag.putByteArray(BLUEPRINT_BYTES_TAG, this.blueprintBytes);
            tag.put(MATERIAL_SUMMARY_TAG, this.materialSummary.toTag());
            tag.putDouble(PREVIEW_BOTTOM_Y_TAG, this.previewBottomY);
        }
        if (!this.checklistStack.isEmpty()) {
            tag.put(CHECKLIST_STACK_TAG, this.checklistStack.saveOptional(registries));
        }
        if (this.printing) {
            tag.putBoolean(PRINTING_TAG, true);
            tag.putInt(PRINT_TOTAL_BLOCKS_TAG, this.totalPrintBlocks);
            tag.putInt(PRINT_EMITTED_BLOCKS_TAG, this.emittedPrintBlocks);
            tag.putLong(PRINT_NEXT_EMIT_TICK_TAG, this.nextEmitTick);
            tag.putLong(PRINT_FINALIZE_TICK_TAG, this.finalizeTick);
        }
        if (!this.reservedMaterialStacks.isEmpty()) {
            ListTag reserved = new ListTag();
            for (ItemStack stack : this.reservedMaterialStacks) {
                if (!stack.isEmpty()) {
                    reserved.add(stack.save(registries));
                }
            }
            tag.put(RESERVED_MATERIAL_STACKS_TAG, reserved);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.owner = tag.hasUUID(OWNER_TAG) ? tag.getUUID(OWNER_TAG) : null;
        this.blueprintDisplayName = tag.getString(DISPLAY_NAME_TAG);
        this.blueprintName = tag.getString(BLUEPRINT_NAME_TAG);
        this.blueprintBytes = tag.getByteArray(BLUEPRINT_BYTES_TAG);
        this.previewBottomY = tag.getDouble(PREVIEW_BOTTOM_Y_TAG);
        this.checklistStack = tag.contains(CHECKLIST_STACK_TAG, net.minecraft.nbt.Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(registries, tag.getCompound(CHECKLIST_STACK_TAG))
                : ItemStack.EMPTY;
        this.printing = tag.getBoolean(PRINTING_TAG);
        this.totalPrintBlocks = tag.getInt(PRINT_TOTAL_BLOCKS_TAG);
        this.emittedPrintBlocks = tag.getInt(PRINT_EMITTED_BLOCKS_TAG);
        this.nextEmitTick = tag.getLong(PRINT_NEXT_EMIT_TICK_TAG);
        this.finalizeTick = tag.contains(PRINT_FINALIZE_TICK_TAG, Tag.TAG_LONG) ? tag.getLong(PRINT_FINALIZE_TICK_TAG) : -1L;
        this.printTargets = List.of();
        this.storedLoadFailure = "";
        if (this.blueprintBytes.length > 0) {
            try {
                this.materialSummary = readMaterialSummary(this.blueprintBytes);
            } catch (Exception exception) {
                com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod.LOGGER.error(
                        "Stored portable-printer blueprint is invalid at {}",
                        this.worldPosition,
                        exception
                );
                addStoredLoadFailure("stored blueprint is invalid: " + failureMessage(exception));
                this.materialSummary = readCachedMaterialSummary(tag);
            }
        } else {
            this.materialSummary = BlueprintMaterialSummary.empty();
        }
        ReservedMaterialsLoadResult reservation = readReservedMaterialStacks(tag, registries);
        this.reservedMaterialStacks = reservation.stacks();
        if (!reservation.failure().isBlank()) {
            addStoredLoadFailure(reservation.failure());
            com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod.LOGGER.error(
                    "Stored portable-printer reservation is invalid at {}: {}",
                    this.worldPosition,
                    reservation.failure()
            );
        }
        if (this.printing && !reservation.present() && !hasStoredLoadFailure()) {
            this.reservedMaterialStacks = PortableStructurePrinterInventory.reconstructReservedStacks(this.materialSummary);
            com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod.LOGGER.warn(
                    "Reconstructed legacy portable-printer material reservation at {} because the running job had no saved reservation",
                    this.worldPosition
            );
        } else if (this.printing
                && reservation.present()
                && this.reservedMaterialStacks.isEmpty()
                && (!this.materialSummary.blockCounts().isEmpty() || !this.materialSummary.itemCounts().isEmpty())) {
            addStoredLoadFailure("running print job has an empty material reservation");
        }
    }

    private BlueprintMaterialSummary readCachedMaterialSummary(CompoundTag tag) {
        if (!tag.contains(MATERIAL_SUMMARY_TAG)) {
            return BlueprintMaterialSummary.empty();
        }
        if (!tag.contains(MATERIAL_SUMMARY_TAG, Tag.TAG_COMPOUND)) {
            addStoredLoadFailure("cached material summary is not a compound");
            return BlueprintMaterialSummary.empty();
        }
        try {
            return BlueprintMaterialSummary.fromTag(tag.getCompound(MATERIAL_SUMMARY_TAG));
        } catch (IllegalArgumentException exception) {
            addStoredLoadFailure("cached material summary is invalid: " + failureMessage(exception));
            return BlueprintMaterialSummary.empty();
        }
    }

    private static BlueprintMaterialSummary readMaterialSummary(byte[] blueprintBytes) throws IOException {
        CompoundTag root = BlueprintArchiveCodec.decode(blueprintBytes);
        if (root.contains("sub_levels", Tag.TAG_LIST)) {
            MissingRegistryContentSanitizer.sanitizeCreatePhysical(root);
        } else {
            MissingRegistryContentSanitizer.sanitizeNative(root);
        }
        return BlueprintMaterialAnalyzer.readFromRoot(root);
    }

    private static ReservedMaterialsLoadResult readReservedMaterialStacks(
            CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries
    ) {
        if (!tag.contains(RESERVED_MATERIAL_STACKS_TAG)) {
            return new ReservedMaterialsLoadResult(false, List.of(), "");
        }
        if (!(tag.get(RESERVED_MATERIAL_STACKS_TAG) instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            return new ReservedMaterialsLoadResult(true, List.of(), "reserved materials are malformed");
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(i));
            if (stack.isEmpty()) {
                return new ReservedMaterialsLoadResult(
                        true,
                        List.of(),
                        "reserved material entry " + i + " is invalid"
                );
            }
            stacks.add(stack);
        }
        return new ReservedMaterialsLoadResult(true, List.copyOf(stacks), "");
    }

    private void addStoredLoadFailure(String reason) {
        if (reason == null || reason.isBlank()) {
            return;
        }
        this.storedLoadFailure = this.storedLoadFailure.isBlank()
                ? reason
                : this.storedLoadFailure + "; " + reason;
    }

    private static String failureMessage(Exception exception) {
        return FailureMessages.describe(exception, exception.getClass().getSimpleName());
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return List.of();
        }
        List<ItemStack> copies = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                copies.add(stack.copy());
            }
        }
        return List.copyOf(copies);
    }

    private record ReservedMaterialsLoadResult(boolean present, List<ItemStack> stacks, String failure) {
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }
}
