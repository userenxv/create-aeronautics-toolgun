package com.enxv.aeronauticsstructuretool.client.render;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.PortableStructurePreviewData;
import com.enxv.aeronauticsstructuretool.PreviewBlueprintData;
import com.enxv.aeronauticsstructuretool.vehicle.container.ContainerCaptureReceipt;
import com.enxv.aeronauticsstructuretool.vehicle.container.PortableContainerStorage;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.io.IOException;

/** One held archive, invalidated by immutable item-component or world replacement. */
final class ClientContainerPlacementPreview {
    private static CustomData cachedData;
    private static Level cachedLevel;
    private static PreviewBlueprintData preview;
    private static double bottomY;

    private ClientContainerPlacementPreview() {
    }

    static PreviewBlueprintData get(ItemStack stack, Level level) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == cachedData && level == cachedLevel) {
            return preview;
        }
        clear();
        cachedData = data;
        cachedLevel = level;
        if (data == null || ContainerCaptureReceipt.placementBlocked(stack)
                || !PortableContainerStorage.hasStoredBlueprint(stack)) {
            return null;
        }
        try {
            byte[] bytes = PortableContainerStorage.readStoredBlueprint(stack);
            String name = PortableContainerStorage.readStoredName(stack, "vehicle");
            // Use the same exact minimum as server placement, including runtime contraptions.
            bottomY = PortableStructurePreviewData.fromBlueprintBytes(name, bytes, level).bottomY();
            preview = PreviewBlueprintData.parse(bytes, level);
        } catch (IOException | IllegalArgumentException exception) {
            AeronauticsStructureToolMod.LOGGER.warn("Unable to load held container placement preview", exception);
        }
        return preview;
    }

    static double bottomY() {
        return bottomY;
    }

    static void clear() {
        cachedData = null;
        cachedLevel = null;
        preview = null;
        bottomY = 0.0D;
    }
}
