package com.enxv.aeronauticsstructuretool.printer;

import com.enxv.aeronauticsstructuretool.BlueprintMaterialSummary;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.schematics.cannon.MaterialChecklist;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;

final class PortableStructurePrinterChecklistHelper {
    private PortableStructurePrinterChecklistHelper() {
    }

    static boolean isSupportedTemplate(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return isBookTemplate(stack) || isClipboardTemplate(stack);
    }

    static ItemStack createChecklistItem(BlueprintMaterialSummary summary, PortableStructurePrinterInventory.MaterialStatus status, ItemStack template) {
        if (summary == null || status == null || template == null || template.isEmpty() || !isSupportedTemplate(template)) {
            return ItemStack.EMPTY;
        }
        MaterialChecklist checklist = new MaterialChecklist();
        fillRequirements(checklist.required, status.required());
        fillGathered(checklist.gathered, status.required(), status.missing());
        return isClipboardTemplate(template)
                ? checklist.createWrittenClipboard()
                : checklist.createWrittenBook();
    }

    private static void fillRequirements(Object2IntMap<Item> target, Map<String, Long> required) {
        for (Map.Entry<String, Long> entry : required.entrySet()) {
            Item item = resolveItem(entry.getKey());
            if (item == Items.AIR) {
                continue;
            }
            target.put(item, safeCount(entry.getValue()));
        }
    }

    private static void fillGathered(Object2IntMap<Item> target, Map<String, Long> required, Map<String, Long> missing) {
        for (Map.Entry<String, Long> entry : required.entrySet()) {
            long gathered = entry.getValue() - missing.getOrDefault(entry.getKey(), 0L);
            if (gathered <= 0L) {
                continue;
            }
            Item item = resolveItem(entry.getKey());
            if (item == Items.AIR) {
                continue;
            }
            target.put(item, safeCount(gathered));
        }
    }

    private static Item resolveItem(String itemId) {
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            return Items.AIR;
        }
        return BuiltInRegistries.ITEM.get(key);
    }

    private static boolean isBookTemplate(ItemStack stack) {
        return stack.is(Items.BOOK) || stack.is(Items.WRITABLE_BOOK);
    }

    private static boolean isClipboardTemplate(ItemStack stack) {
        return stack.is(AllBlocks.CLIPBOARD.asStack().getItem());
    }

    private static int safeCount(long value) {
        return (int) Math.clamp(value, 0L, Integer.MAX_VALUE);
    }
}
