package com.enxv.aeronauticsstructuretool.printer;

import com.enxv.aeronauticsstructuretool.BlueprintMaterialSummary;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PortableStructurePrinterInventory {
    private static final String CREATE_ANDESITE_ENCASED_SHAFT = "create:andesite_encased_shaft";
    private static final String CREATE_BRASS_ENCASED_SHAFT = "create:brass_encased_shaft";
    private static final String CREATE_ANDESITE_ENCASED_COGWHEEL = "create:andesite_encased_cogwheel";
    private static final String CREATE_BRASS_ENCASED_COGWHEEL = "create:brass_encased_cogwheel";
    private static final String CREATE_ANDESITE_ENCASED_LARGE_COGWHEEL = "create:andesite_encased_large_cogwheel";
    private static final String CREATE_BRASS_ENCASED_LARGE_COGWHEEL = "create:brass_encased_large_cogwheel";
    private static final String CREATE_METAL_GIRDER_ENCASED_SHAFT = "create:metal_girder_encased_shaft";
    private static final String CREATE_POWERED_SHAFT = "create:powered_shaft";
    private static final String CREATE_SHAFT = "create:shaft";
    private static final String CREATE_COGWHEEL = "create:cogwheel";
    private static final String CREATE_LARGE_COGWHEEL = "create:large_cogwheel";
    private static final String CREATE_METAL_GIRDER = "create:metal_girder";

    private static final String DIESEL_POWERED_SHAFT = "createdieselgenerators:powered_engine_shaft";

    private static final String CAL_ANDESITE_ENCASED_FLEXIBLE_SHAFT = "createadditionallogistics:andesite_encased_flexible_shaft";
    private static final String CAL_ANDESITE_ENCASED_LAZY_LARGE_COGWHEEL = "createadditionallogistics:andesite_encased_lazy_large_cogwheel";
    private static final String CAL_ANDESITE_ENCASED_LAZY_COGWHEEL = "createadditionallogistics:andesite_encased_lazy_cogwheel";
    private static final String CAL_ANDESITE_ENCASED_LAZY_SHAFT = "createadditionallogistics:andesite_encased_lazy_shaft";
    private static final String CAL_BRASS_ENCASED_FLEXIBLE_SHAFT = "createadditionallogistics:brass_encased_flexible_shaft";
    private static final String CAL_BRASS_ENCASED_LAZY_LARGE_COGWHEEL = "createadditionallogistics:brass_encased_lazy_large_cogwheel";
    private static final String CAL_BRASS_ENCASED_LAZY_COGWHEEL = "createadditionallogistics:brass_encased_lazy_cogwheel";
    private static final String CAL_BRASS_ENCASED_LAZY_SHAFT = "createadditionallogistics:brass_encased_lazy_shaft";
    private static final String CAL_FLEXIBLE_SHAFT = "createadditionallogistics:flexible_shaft";
    private static final String CAL_LAZY_LARGE_COGWHEEL = "createadditionallogistics:lazy_large_cogwheel";
    private static final String CAL_LAZY_COGWHEEL = "createadditionallogistics:lazy_cogwheel";
    private static final String CAL_LAZY_SHAFT = "createadditionallogistics:lazy_shaft";

    private PortableStructurePrinterInventory() {
    }

    static MaterialStatus evaluate(BlueprintMaterialSummary summary, Level level, BlockPos printerPos) {
        Map<String, Long> required = buildRequiredItems(summary);
        Map<String, Long> available = scanAvailableItems(level, printerPos);
        Map<String, Long> missing = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : required.entrySet()) {
            long availableCount = available.getOrDefault(entry.getKey(), 0L);
            long missingCount = entry.getValue() - availableCount;
            if (missingCount > 0L) {
                missing.put(entry.getKey(), missingCount);
            }
        }
        return new MaterialStatus(Map.copyOf(required), Map.copyOf(missing));
    }

    static ConsumptionResult consume(BlueprintMaterialSummary summary, ServerLevel level, BlockPos printerPos) {
        MaterialStatus status = evaluate(summary, level, printerPos);
        if (!status.ready()) {
            return ConsumptionResult.failed(status);
        }

        Map<String, Long> remaining = new LinkedHashMap<>(status.required());
        List<ItemStack> removedStacks = new ArrayList<>();
        for (HandlerRef ref : adjacentHandlers(level, printerPos)) {
            IItemHandler handler = ref.handler();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                String itemId = itemId(stack.getItem());
                long needed = remaining.getOrDefault(itemId, 0L);
                if (needed <= 0L) {
                    continue;
                }
                int take = (int) Math.min(needed, stack.getCount());
                if (take <= 0) {
                    continue;
                }
                ItemStack removed = handler.extractItem(slot, take, false);
                if (!removed.isEmpty()) {
                    removedStacks.add(removed.copy());
                    long left = needed - removed.getCount();
                    if (left > 0L) {
                        remaining.put(itemId, left);
                    } else {
                        remaining.remove(itemId);
                    }
                }
            }
            markHandlerChanged(level, ref.pos());
        }

        if (!remaining.isEmpty()) {
            restoreRemoved(level, printerPos, removedStacks);
            return ConsumptionResult.failed(status);
        }
        return ConsumptionResult.success(status, List.copyOf(removedStacks));
    }

    static void restoreRemoved(ServerLevel level, BlockPos printerPos, List<ItemStack> removedStacks) {
        if (removedStacks == null || removedStacks.isEmpty()) {
            return;
        }
        List<HandlerRef> refs = adjacentHandlers(level, printerPos);
        for (ItemStack removed : removedStacks) {
            ItemStack remaining = removed.copy();
            for (HandlerRef ref : refs) {
                remaining = ItemHandlerHelper.insertItem(ref.handler(), remaining, false);
                markHandlerChanged(level, ref.pos());
                if (remaining.isEmpty()) {
                    break;
                }
            }
            if (!remaining.isEmpty()) {
                ItemEntity itemEntity = new ItemEntity(
                        level,
                        printerPos.getX() + 0.5D,
                        printerPos.getY() + 1.1D,
                        printerPos.getZ() + 0.5D,
                        remaining
                );
                level.addFreshEntity(itemEntity);
            }
        }
    }

    static List<ItemStack> reconstructReservedStacks(BlueprintMaterialSummary summary) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Map.Entry<String, Long> entry : buildRequiredItems(summary).entrySet()) {
            ResourceLocation itemId = ResourceLocation.tryParse(entry.getKey());
            if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
                com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod.LOGGER.error(
                        "Cannot reconstruct portable-printer reservation for unknown item {}",
                        entry.getKey()
                );
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(itemId);
            long remaining = entry.getValue();
            int maxStackSize = Math.max(1, item.getDefaultMaxStackSize());
            while (remaining > 0L) {
                int count = (int) Math.min(remaining, maxStackSize);
                stacks.add(new ItemStack(item, count));
                remaining -= count;
            }
        }
        return List.copyOf(stacks);
    }

    private static Map<String, Long> buildRequiredItems(BlueprintMaterialSummary summary) {
        Map<String, Long> required = new LinkedHashMap<>();
        if (summary == null) {
            return required;
        }
        for (Map.Entry<String, Long> entry : summary.blockCounts().entrySet()) {
            if (expandKnownBlockRequirement(entry.getKey(), entry.getValue(), required)) {
                continue;
            }
            ResourceLocation blockId = ResourceLocation.tryParse(entry.getKey());
            if (blockId == null || !BuiltInRegistries.BLOCK.containsKey(blockId)) {
                continue;
            }
            Block block = BuiltInRegistries.BLOCK.get(blockId);
            Item item = block.asItem();
            if (item == Items.AIR) {
                continue;
            }
            required.merge(itemId(item), entry.getValue(), Long::sum);
        }
        for (Map.Entry<String, Long> entry : summary.itemCounts().entrySet()) {
            required.merge(entry.getKey(), entry.getValue(), Long::sum);
        }
        return required;
    }

    private static boolean expandKnownBlockRequirement(String blockId, long count, Map<String, Long> required) {
        if (count <= 0L || blockId == null || blockId.isBlank()) {
            return true;
        }
        switch (blockId) {
            case CREATE_ANDESITE_ENCASED_SHAFT, CREATE_BRASS_ENCASED_SHAFT,
                 CREATE_POWERED_SHAFT, DIESEL_POWERED_SHAFT -> {
                required.merge(CREATE_SHAFT, count, Long::sum);
                return true;
            }
            case CREATE_ANDESITE_ENCASED_COGWHEEL, CREATE_BRASS_ENCASED_COGWHEEL -> {
                required.merge(CREATE_COGWHEEL, count, Long::sum);
                return true;
            }
            case CREATE_ANDESITE_ENCASED_LARGE_COGWHEEL, CREATE_BRASS_ENCASED_LARGE_COGWHEEL -> {
                required.merge(CREATE_LARGE_COGWHEEL, count, Long::sum);
                return true;
            }
            case CREATE_METAL_GIRDER_ENCASED_SHAFT -> {
                required.merge(CREATE_METAL_GIRDER, count, Long::sum);
                required.merge(CREATE_SHAFT, count, Long::sum);
                return true;
            }
            case CAL_ANDESITE_ENCASED_FLEXIBLE_SHAFT, CAL_BRASS_ENCASED_FLEXIBLE_SHAFT -> {
                required.merge(CAL_FLEXIBLE_SHAFT, count, Long::sum);
                return true;
            }
            case CAL_ANDESITE_ENCASED_LAZY_LARGE_COGWHEEL, CAL_BRASS_ENCASED_LAZY_LARGE_COGWHEEL -> {
                required.merge(CAL_LAZY_LARGE_COGWHEEL, count, Long::sum);
                return true;
            }
            case CAL_ANDESITE_ENCASED_LAZY_COGWHEEL, CAL_BRASS_ENCASED_LAZY_COGWHEEL -> {
                required.merge(CAL_LAZY_COGWHEEL, count, Long::sum);
                return true;
            }
            case CAL_ANDESITE_ENCASED_LAZY_SHAFT, CAL_BRASS_ENCASED_LAZY_SHAFT -> {
                required.merge(CAL_LAZY_SHAFT, count, Long::sum);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static Map<String, Long> scanAvailableItems(Level level, BlockPos printerPos) {
        Map<String, Long> available = new LinkedHashMap<>();
        for (HandlerRef ref : adjacentHandlers(level, printerPos)) {
            IItemHandler handler = ref.handler();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    available.merge(itemId(stack.getItem()), (long) stack.getCount(), Long::sum);
                }
            }
        }
        return available;
    }

    private static List<HandlerRef> adjacentHandlers(Level level, BlockPos printerPos) {
        List<HandlerRef> handlers = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            BlockPos targetPos = printerPos.relative(direction);
            if (!level.isLoaded(targetPos)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(targetPos);
            if (blockEntity == null) {
                continue;
            }
            IItemHandler handler = level.getCapability(
                    Capabilities.ItemHandler.BLOCK,
                    targetPos,
                    direction.getOpposite()
            );
            if (handler != null) {
                handlers.add(new HandlerRef(targetPos, handler));
            }
        }
        return handlers;
    }

    private static void markHandlerChanged(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            blockEntity.setChanged();
            level.sendBlockUpdated(pos, blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
        }
    }

    private static String itemId(Item item) {
        return String.valueOf(BuiltInRegistries.ITEM.getKey(item));
    }

    record MaterialStatus(Map<String, Long> required, Map<String, Long> missing) {
        boolean ready() {
            return this.missing.isEmpty() && !this.required.isEmpty();
        }

        float progress() {
            long requiredCount = this.required.values().stream().mapToLong(Long::longValue).sum();
            if (requiredCount <= 0L) {
                return 0.0F;
            }
            long missingCount = this.missing.values().stream().mapToLong(Long::longValue).sum();
            long gatheredCount = Math.max(0L, requiredCount - missingCount);
            return Math.min(1.0F, (float) gatheredCount / (float) requiredCount);
        }
    }

    record ConsumptionResult(boolean success, MaterialStatus status, List<ItemStack> removedStacks) {
        static ConsumptionResult success(MaterialStatus status, List<ItemStack> removedStacks) {
            return new ConsumptionResult(true, status, removedStacks);
        }

        static ConsumptionResult failed(MaterialStatus status) {
            return new ConsumptionResult(false, status, List.of());
        }
    }

    private record HandlerRef(BlockPos pos, IItemHandler handler) {
    }
}
