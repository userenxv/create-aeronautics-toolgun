package com.enxv.aeronauticsstructuretool.vehicle.container;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ContainerCaptureReceipt {
    private static final String PENDING = "PendingContainerCapture";
    private static final String BLOCKED_PLACEMENT = "ContainerPlacementRecoveryRequired";

    private ContainerCaptureReceipt() {
    }

    public static boolean pending(ItemStack stack) {
        return tag(stack).contains(PENDING);
    }

    public static boolean placementBlocked(ItemStack stack) {
        return pending(stack) || tag(stack).getBoolean(BLOCKED_PLACEMENT);
    }

    public static void blockPlacement(ItemStack stack) {
        CompoundTag tag = tag(stack);
        tag.putBoolean(BLOCKED_PLACEMENT, true);
        write(stack, tag);
    }

    public static void prepare(ItemStack stack, String dimension, List<UUID> ids) {
        CompoundTag tag = tag(stack);
        CompoundTag receipt = new CompoundTag();
        receipt.putString("Dimension", dimension);
        receipt.put("Remaining", writeIds(ids));
        tag.put(PENDING, receipt);
        write(stack, tag);
    }

    public static String dimension(ItemStack stack) {
        return receipt(stack).getString("Dimension");
    }

    public static List<UUID> remaining(ItemStack stack) {
        ListTag list = (ListTag) receipt(stack).get("Remaining");
        List<UUID> ids = new ArrayList<>();
        for (Tag entry : list) {
            ids.add(NbtUtils.loadUUID(entry));
        }
        return List.copyOf(ids);
    }

    private static CompoundTag receipt(ItemStack stack) {
        CompoundTag tag = tag(stack);
        if (!tag.contains(PENDING, Tag.TAG_COMPOUND)) {
            throw new IllegalStateException("missing or malformed container capture receipt");
        }
        CompoundTag receipt = tag.getCompound(PENDING);
        if (!receipt.contains("Dimension", Tag.TAG_STRING) || receipt.getString("Dimension").isBlank()
                || !receipt.contains("Remaining", Tag.TAG_LIST)
                || !(receipt.get("Remaining") instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_INT_ARRAY)) {
            throw new IllegalStateException("malformed container capture receipt");
        }
        return receipt;
    }

    public static void confirmRemoved(ItemStack stack, UUID id) {
        List<UUID> remaining = new ArrayList<>(remaining(stack));
        if (!remaining.remove(id)) {
            throw new IllegalStateException("container capture receipt does not contain " + id);
        }
        CompoundTag tag = tag(stack);
        tag.getCompound(PENDING).put("Remaining", writeIds(remaining));
        write(stack, tag);
    }

    public static void finish(ItemStack stack) {
        if (!remaining(stack).isEmpty()) {
            throw new IllegalStateException("original structures still need removal");
        }
        CompoundTag tag = tag(stack);
        tag.remove(PENDING);
        write(stack, tag);
    }

    private static ListTag writeIds(List<UUID> ids) {
        ListTag list = new ListTag();
        ids.forEach(id -> list.add(NbtUtils.createUUID(id)));
        return list;
    }

    private static CompoundTag tag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void write(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
