package com.enxv.aeronauticsstructuretool.network.handler;

import com.enxv.aeronauticsstructuretool.PrintPortableStructurePrinterPayload;
import com.enxv.aeronauticsstructuretool.RequestPortableStructurePrinterStatePayload;
import com.enxv.aeronauticsstructuretool.SelectPortableStructurePrinterBlueprintPayload;
import com.enxv.aeronauticsstructuretool.UsePortableStructurePrinterChecklistSlotPayload;
import com.enxv.aeronauticsstructuretool.printer.PortableStructurePrinterService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class PortableStructurePrinterPayloadHandlers {
    private PortableStructurePrinterPayloadHandlers() {
    }

    public static void handleStateRequest(RequestPortableStructurePrinterStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PortableStructurePrinterService.syncState(player, payload.printerPos());
            }
        });
    }

    public static void handleSelect(SelectPortableStructurePrinterBlueprintPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PortableStructurePrinterService.selectBlueprint(
                        player,
                        payload.printerPos(),
                        payload.displayName(),
                        payload.blueprintName(),
                        payload.blueprintBytes(),
                        payload.previewBottomY()
                );
            }
        });
    }

    public static void handlePrint(PrintPortableStructurePrinterPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PortableStructurePrinterService.startPrint(player, payload.printerPos());
            }
        });
    }

    public static void handleChecklistSlot(UsePortableStructurePrinterChecklistSlotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PortableStructurePrinterService.useChecklistSlot(player, payload.printerPos());
            }
        });
    }
}
