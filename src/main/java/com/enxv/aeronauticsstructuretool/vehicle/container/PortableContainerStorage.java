package com.enxv.aeronauticsstructuretool.vehicle.container;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.BlueprintMaterialSummary;
import com.enxv.aeronauticsstructuretool.blueprint.codec.BlueprintArchiveCodec;
import com.enxv.aeronauticsstructuretool.blueprint.codec.NativeBlueprintReader;
import com.enxv.aeronauticsstructuretool.blueprint.lifecycle.ConnectedStructureSnapshot;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

import java.io.IOException;
import java.util.UUID;

public final class PortableContainerStorage {
    private static final String STORED_BLUEPRINT_TAG = "StoredBlueprint";
    private static final String STORED_NAME_TAG = "StoredName";
    private static final String CONTAINER_ID_TAG = "ContainerId";
    private static final String STORED_MATERIAL_SUMMARY_TAG = "StoredMaterialSummary";
    private static final String ITEM_REPAIR_VEHICLE_ID_TAG = "RepairVehicleId";
    private static final String ACTIVE_PLACED_REPAIR_VEHICLE_ID_TAG = "ActivePlacedRepairVehicleId";
    private static final String ACTIVE_PLACED_ROOT_STRUCTURE_ID_TAG = "ActivePlacedRootStructureId";
    private static final String BLUEPRINT_REPAIR_VEHICLE_ID_TAG = "MyVehicleRepairVehicleId";
    private static final CustomModelData EMPTY_MODEL = new CustomModelData(0);
    private static final CustomModelData FULL_MODEL = new CustomModelData(1);

    private PortableContainerStorage() {
    }

    public static boolean hasStoredBlueprint(ItemStack stack) {
        return customTag(stack).contains(STORED_BLUEPRINT_TAG);
    }

    public static byte[] readStoredBlueprint(ItemStack stack) throws IOException {
        CompoundTag tag = customTag(stack);
        if (!tag.contains(STORED_BLUEPRINT_TAG, Tag.TAG_BYTE_ARRAY)) {
            throw new IOException("portable container StoredBlueprint is not a byte array");
        }
        byte[] blueprint = tag.getByteArray(STORED_BLUEPRINT_TAG);
        if (blueprint.length == 0) {
            throw new IOException("portable container StoredBlueprint is empty");
        }
        return blueprint;
    }

    public static String readStoredName(ItemStack stack, String fallbackName) throws IOException {
        CompoundTag tag = customTag(stack);
        if (tag.contains(STORED_NAME_TAG) && !tag.contains(STORED_NAME_TAG, Tag.TAG_STRING)) {
            throw new IOException("portable container StoredName is not a string");
        }
        String storedName = tag.getString(STORED_NAME_TAG);
        return storedName.isBlank() ? fallbackName : storedName;
    }

    public static String readContainerId(ItemStack stack) {
        return customTag(stack).getString(CONTAINER_ID_TAG);
    }

    public static String ensureContainerId(ItemStack stack) {
        CompoundTag tag = customTag(stack);
        if (tag.contains(CONTAINER_ID_TAG) && !tag.contains(CONTAINER_ID_TAG, Tag.TAG_STRING)) {
            AeronauticsStructureToolMod.LOGGER.warn("Replacing malformed portable-container id");
            tag.remove(CONTAINER_ID_TAG);
        }
        String containerId = tag.getString(CONTAINER_ID_TAG);
        if (containerId == null || containerId.isBlank()) {
            containerId = UUID.randomUUID().toString();
            tag.putString(CONTAINER_ID_TAG, containerId);
            writeCustomTag(stack, tag);
        }
        return containerId;
    }

    public static String readItemRepairVehicleId(ItemStack stack) {
        return customTag(stack).getString(ITEM_REPAIR_VEHICLE_ID_TAG);
    }

    public static int countStoredStructures(byte[] fileContents) throws IOException {
        return NativeBlueprintReader.read(BlueprintArchiveCodec.decode(fileContents)).sublevels().size();
    }

    public static void writeStoredBlueprint(
            ItemStack stack,
            String blueprintName,
            byte[] fileContents,
            BlueprintMaterialSummary materialSummary,
            String containerId,
            boolean repairIntegration
    ) throws IOException {
        if (fileContents == null || fileContents.length == 0) {
            throw new IOException("portable container blueprint is empty");
        }
        NativeBlueprintReader.read(BlueprintArchiveCodec.decode(fileContents));
        CompoundTag tag = customTag(stack);
        tag.putByteArray(STORED_BLUEPRINT_TAG, fileContents);
        tag.putString(STORED_NAME_TAG, blueprintName);
        tag.putString(
                CONTAINER_ID_TAG,
                containerId == null || containerId.isBlank()
                        ? UUID.randomUUID().toString()
                        : containerId
        );
        if (repairIntegration) {
            updateItemRepairVehicleId(tag, fileContents);
        } else {
            tag.remove(ITEM_REPAIR_VEHICLE_ID_TAG);
            clearActivePlacedRepairTracking(tag);
        }
        if (materialSummary != null) {
            tag.put(STORED_MATERIAL_SUMMARY_TAG, materialSummary.toTag());
        } else {
            tag.remove(STORED_MATERIAL_SUMMARY_TAG);
        }
        writeCustomTag(stack, tag);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, FULL_MODEL);
    }

    public static void clearStoredBlueprint(ItemStack stack) {
        CompoundTag tag = customTag(stack);
        tag.remove(STORED_BLUEPRINT_TAG);
        tag.remove(STORED_NAME_TAG);
        tag.remove(STORED_MATERIAL_SUMMARY_TAG);
        tag.remove(ITEM_REPAIR_VEHICLE_ID_TAG);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            writeCustomTag(stack, tag);
        }
        stack.set(DataComponents.CUSTOM_MODEL_DATA, EMPTY_MODEL);
    }

    public static byte[] prepareCapturedBlueprint(
            ServerLevel level,
            ItemStack stack,
            ConnectedStructureSnapshot snapshot,
            byte[] blueprintBytes,
            boolean repairIntegration
    ) throws IOException {
        if (!repairIntegration) {
            return stripBlueprintRepairVehicleId(blueprintBytes);
        }
        byte[] normalized = maybeRestoreRepairVehicleId(level, stack, snapshot, blueprintBytes);
        CompoundTag tag = customTag(stack);
        AeronauticsStructureToolMod.LOGGER.info(
                "Portable container capture repair-id check: capturedRootStructureId={} activeRepairId='{}' activeRootStructureId='{}' capturedBlueprintRepairId='{}' finalBlueprintRepairId='{}'",
                snapshot != null ? snapshot.rootStructureId() : null,
                tag.getString(ACTIVE_PLACED_REPAIR_VEHICLE_ID_TAG),
                tag.hasUUID(ACTIVE_PLACED_ROOT_STRUCTURE_ID_TAG)
                        ? tag.getUUID(ACTIVE_PLACED_ROOT_STRUCTURE_ID_TAG)
                        : null,
                readBlueprintRepairVehicleId(blueprintBytes),
                readBlueprintRepairVehicleId(normalized)
        );
        return normalized;
    }

    public static void rememberPlacedRepairTracking(
            ItemStack stack,
            byte[] storedBlueprint,
            ConnectedStructureSnapshot snapshot
    ) throws IOException {
        CompoundTag tag = customTag(stack);
        String repairVehicleId = readBlueprintRepairVehicleId(storedBlueprint);
        if (repairVehicleId.isBlank()) {
            clearActivePlacedRepairTracking(tag);
        } else {
            tag.putString(ACTIVE_PLACED_REPAIR_VEHICLE_ID_TAG, repairVehicleId);
            if (snapshot != null && snapshot.rootStructureId() != null) {
                tag.putUUID(ACTIVE_PLACED_ROOT_STRUCTURE_ID_TAG, snapshot.rootStructureId());
            } else {
                tag.remove(ACTIVE_PLACED_ROOT_STRUCTURE_ID_TAG);
            }
        }
        writeCustomTag(stack, tag);
    }

    public static void rememberRepairVehicleId(
            ServerLevel level,
            ConnectedStructureSnapshot snapshot,
            String repairVehicleId
    ) {
        if (snapshot != null && snapshot.rootStructureId() != null && !repairVehicleId.isBlank()) {
            PortableContainerRepairIdTracker.remember(
                    level,
                    snapshot.rootStructureId(),
                    repairVehicleId
            );
        }
    }

    public static void clearActivePlacedRepairTracking(ItemStack stack) {
        CompoundTag tag = customTag(stack);
        clearActivePlacedRepairTracking(tag);
        writeCustomTag(stack, tag);
    }

    public static String readBlueprintRepairVehicleId(byte[] blueprintBytes) throws IOException {
        if (blueprintBytes == null || blueprintBytes.length == 0) {
            return "";
        }
        CompoundTag root = BlueprintArchiveCodec.decode(blueprintBytes);
        if (root.contains(BLUEPRINT_REPAIR_VEHICLE_ID_TAG)
                && !root.contains(BLUEPRINT_REPAIR_VEHICLE_ID_TAG, Tag.TAG_STRING)) {
            throw new IOException("blueprint repair vehicle id is not a string");
        }
        return root.contains(BLUEPRINT_REPAIR_VEHICLE_ID_TAG, Tag.TAG_STRING)
                ? root.getString(BLUEPRINT_REPAIR_VEHICLE_ID_TAG)
                : "";
    }

    private static byte[] maybeRestoreRepairVehicleId(
            ServerLevel level,
            ItemStack stack,
            ConnectedStructureSnapshot snapshot,
            byte[] blueprintBytes
    ) throws IOException {
        if (blueprintBytes == null || blueprintBytes.length == 0
                || !readBlueprintRepairVehicleId(blueprintBytes).isBlank()) {
            return blueprintBytes;
        }
        if (snapshot == null || snapshot.rootStructureId() == null) {
            CompoundTag tag = customTag(stack);
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Portable container repair-id restore skipped: capturedRootStructureId={} storedRootStructureId={} activeRepairId='{}'",
                    snapshot != null ? snapshot.rootStructureId() : null,
                    tag.hasUUID(ACTIVE_PLACED_ROOT_STRUCTURE_ID_TAG)
                            ? tag.getUUID(ACTIVE_PLACED_ROOT_STRUCTURE_ID_TAG)
                            : null,
                    tag.getString(ACTIVE_PLACED_REPAIR_VEHICLE_ID_TAG)
            );
            return blueprintBytes;
        }
        String trackedRepairVehicleId = PortableContainerRepairIdTracker.consume(
                level,
                snapshot.rootStructureId()
        );
        return trackedRepairVehicleId.isBlank()
                ? blueprintBytes
                : writeBlueprintRepairVehicleId(blueprintBytes, trackedRepairVehicleId);
    }

    private static void updateItemRepairVehicleId(CompoundTag tag, byte[] blueprintBytes) throws IOException {
        String repairVehicleId = readBlueprintRepairVehicleId(blueprintBytes);
        if (repairVehicleId.isBlank()) {
            tag.remove(ITEM_REPAIR_VEHICLE_ID_TAG);
        } else {
            tag.putString(ITEM_REPAIR_VEHICLE_ID_TAG, repairVehicleId);
        }
    }

    private static byte[] writeBlueprintRepairVehicleId(
            byte[] blueprintBytes,
            String repairVehicleId
    ) throws IOException {
        if (blueprintBytes == null || blueprintBytes.length == 0
                || repairVehicleId == null || repairVehicleId.isBlank()) {
            return blueprintBytes;
        }
        CompoundTag root = BlueprintArchiveCodec.decode(blueprintBytes);
        root.putString(BLUEPRINT_REPAIR_VEHICLE_ID_TAG, repairVehicleId);
        return BlueprintArchiveCodec.encode(root);
    }

    private static byte[] stripBlueprintRepairVehicleId(byte[] blueprintBytes) throws IOException {
        if (blueprintBytes == null || blueprintBytes.length == 0) {
            return blueprintBytes;
        }
        CompoundTag root = BlueprintArchiveCodec.decode(blueprintBytes);
        if (!root.contains(BLUEPRINT_REPAIR_VEHICLE_ID_TAG, Tag.TAG_STRING)) {
            return blueprintBytes;
        }
        root.remove(BLUEPRINT_REPAIR_VEHICLE_ID_TAG);
        return BlueprintArchiveCodec.encode(root);
    }

    private static CompoundTag customTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void writeCustomTag(ItemStack stack, CompoundTag tag) {
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    private static void clearActivePlacedRepairTracking(CompoundTag tag) {
        tag.remove(ACTIVE_PLACED_REPAIR_VEHICLE_ID_TAG);
        tag.remove(ACTIVE_PLACED_ROOT_STRUCTURE_ID_TAG);
    }
}
