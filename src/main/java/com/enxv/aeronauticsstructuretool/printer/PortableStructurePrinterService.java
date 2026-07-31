package com.enxv.aeronauticsstructuretool.printer;

import com.enxv.aeronauticsstructuretool.blueprint.placement.CreatePhysicalBlueprintService;
import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.PortableStructurePreviewData;
import com.enxv.aeronauticsstructuretool.SpawnPortableStructurePrinterEffectPayload;
import com.enxv.aeronauticsstructuretool.SyncPortableStructurePrinterStatePayload;
import com.enxv.aeronauticsstructuretool.core.FailureMessages;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.util.List;

public final class PortableStructurePrinterService {
    private static final int BLOCKS_PER_SECOND = 5;
    private static final int EMIT_INTERVAL_TICKS = 20 / BLOCKS_PER_SECOND;
    private static final int EFFECT_TRAVEL_TICKS = 12;

    private PortableStructurePrinterService() {
    }

    public static void syncState(ServerPlayer player, BlockPos printerPos) {
        PrinterAccess access = resolvePrinter(player, printerPos);
        if (access != null) {
            syncState(player, access.printer());
        }
    }

    public static void syncState(ServerPlayer player, PortableStructurePrinterBlockEntity printer) {
        if (player == null || printer == null) {
            return;
        }
        PortableStructurePrinterInventory.MaterialStatus status = printer.materialStatus();
        boolean busy = player.level() instanceof ServerLevel level && isBusy(level, printer);
        String statusMessage;
        if (!printer.hasBlueprint()) {
            statusMessage = "screen.create_aeronautics_toolgun.printer.status.no_blueprint";
        } else if (printer.hasStoredLoadFailure()) {
            statusMessage = "screen.create_aeronautics_toolgun.printer.status.invalid";
        } else if (busy) {
            statusMessage = "screen.create_aeronautics_toolgun.printer.status.printing";
        } else if (status.ready()) {
            statusMessage = "screen.create_aeronautics_toolgun.printer.status.ready";
        } else if (!status.required().isEmpty()) {
            statusMessage = "screen.create_aeronautics_toolgun.printer.status.missing";
        } else {
            statusMessage = "screen.create_aeronautics_toolgun.printer.status.invalid";
        }
        PacketDistributor.sendToPlayer(player, new SyncPortableStructurePrinterStatePayload(
                printer.getBlockPos(),
                printer.blueprintDisplayName(),
                printer.blueprintName(),
                printer.hasBlueprint(),
                !printer.hasStoredLoadFailure() && status.ready(),
                busy,
                statusMessage,
                status.progress(),
                printer.printProgress(),
                printer.checklistStack(),
                status.required(),
                status.missing()
        ));
    }

    public static void selectBlueprint(
            ServerPlayer player,
            BlockPos printerPos,
            String displayName,
            String blueprintName,
            byte[] blueprintBytes,
            double ignoredPreviewBottomY
    ) {
        PrinterAccess access = resolvePrinter(player, printerPos);
        if (access == null) {
            return;
        }
        ServerLevel level = access.level();
        PortableStructurePrinterBlockEntity printer = access.printer();
        if (isBusy(level, printer)) {
            player.sendSystemMessage(Component.translatable("message.create_aeronautics_toolgun.printer_busy"));
            syncState(player, printer);
            return;
        }
        try {
            double serverPreviewBottomY = PortableStructurePreviewData
                    .fromBlueprintBytes(blueprintName, blueprintBytes, level)
                    .bottomY();
            printer.setBlueprint(player.getUUID(), displayName, blueprintName, blueprintBytes, serverPreviewBottomY);
            syncState(player, printer);
        } catch (Exception exception) {
            AeronauticsStructureToolMod.LOGGER.error(
                    "Portable-printer blueprint selection failed at {} for player {}",
                    printerPos,
                    player.getGameProfile().getName(),
                    exception
            );
            player.sendSystemMessage(Component.translatable(
                    "message.create_aeronautics_toolgun.printer_select_failed",
                    FailureMessages.describe(exception, "blueprint selection failed")
            ));
        }
    }

    public static void startPrint(ServerPlayer player, BlockPos printerPos) {
        PrinterAccess access = resolvePrinter(player, printerPos);
        if (access == null) {
            return;
        }
        ServerLevel level = access.level();
        PortableStructurePrinterBlockEntity printer = access.printer();
        if (!printer.hasBlueprint()) {
            player.sendSystemMessage(Component.translatable(
                    "message.create_aeronautics_toolgun.printer_print_failed",
                    Component.translatable("message.create_aeronautics_toolgun.printer_no_blueprint")
            ));
            return;
        }
        if (printer.hasStoredLoadFailure()) {
            player.sendSystemMessage(Component.translatable(
                    "message.create_aeronautics_toolgun.printer_print_failed",
                    printer.storedLoadFailure()
            ));
            syncState(player, printer);
            return;
        }
        if (isBusy(level, printer)) {
            player.sendSystemMessage(Component.translatable("message.create_aeronautics_toolgun.printer_busy"));
            syncState(player, printer);
            return;
        }

        PortableStructurePrinterInventory.ConsumptionResult consumption = PortableStructurePrinterInventory.consume(
                printer.materialSummary(),
                level,
                printer.getBlockPos()
        );
        if (!consumption.success()) {
            syncState(player, printer);
            player.sendSystemMessage(Component.translatable(
                    "message.create_aeronautics_toolgun.printer_print_failed",
                    Component.translatable("message.create_aeronautics_toolgun.printer_missing_materials")
            ));
            return;
        }

        BlockPos clickedPos = printer.getBlockPos();
        try {
            printer.setReservedMaterialStacks(consumption.removedStacks());
            printer.startPrintJob(level);
            syncState(player, printer);
        } catch (Exception exception) {
            PortableStructurePrinterInventory.restoreRemoved(level, clickedPos, consumption.removedStacks());
            printer.clearPrintJob();
            printer.setChanged();
            AeronauticsStructureToolMod.LOGGER.error(
                    "Portable-printer job could not start at {}",
                    clickedPos,
                    exception
            );
            player.sendSystemMessage(Component.translatable(
                    "message.create_aeronautics_toolgun.printer_print_failed",
                    FailureMessages.describe(exception, "print job could not start")
            ));
            syncState(player, printer);
        }
    }

    public static void useChecklistSlot(ServerPlayer player, BlockPos printerPos) {
        PrinterAccess access = resolvePrinter(player, printerPos);
        if (access == null) {
            return;
        }
        ServerLevel level = access.level();
        PortableStructurePrinterBlockEntity printer = access.printer();
        if (!printer.checklistStack().isEmpty()) {
            ItemStack extracted = printer.removeChecklistStack();
            if (!extracted.isEmpty() && !player.getInventory().add(extracted)) {
                player.drop(extracted, false);
            }
            syncState(player, printer);
            return;
        }
        if (!printer.hasBlueprint()) {
            player.sendSystemMessage(Component.translatable("message.create_aeronautics_toolgun.printer_no_blueprint"));
            return;
        }
        if (printer.hasStoredLoadFailure()) {
            player.sendSystemMessage(Component.translatable(
                    "message.create_aeronautics_toolgun.printer_print_failed",
                    printer.storedLoadFailure()
            ));
            syncState(player, printer);
            return;
        }

        ItemStack held = player.getMainHandItem();
        if (!PortableStructurePrinterChecklistHelper.isSupportedTemplate(held)) {
            player.sendSystemMessage(Component.translatable("message.create_aeronautics_toolgun.printer_checklist_need_template"));
            return;
        }
        ItemStack generated = PortableStructurePrinterChecklistHelper.createChecklistItem(
                printer.materialSummary(),
                printer.materialStatus(),
                held
        );
        if (generated.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.create_aeronautics_toolgun.printer_checklist_failed"));
            return;
        }

        held.shrink(1);
        printer.setChecklistStack(generated);
        syncState(player, printer);
    }

    public static void tick(ServerLevel level, BlockPos pos, PortableStructurePrinterBlockEntity printer) {
        if (printer.hasStoredLoadFailure()) {
            if (printer.printing() || !printer.reservedMaterialStacks().isEmpty()) {
                PortableStructurePrinterFeedback.failPrint(
                        level,
                        pos,
                        printer,
                        printer.reservedMaterialStacks(),
                        new IOException(printer.storedLoadFailure())
                );
            }
            return;
        }
        if (!printer.printing()) {
            if (!printer.reservedMaterialStacks().isEmpty()) {
                refundReservedMaterials(level, pos, printer, "a stale non-printing reservation was loaded");
            }
            return;
        }
        try {
            printer.ensurePrintTargets(level);
        } catch (IOException exception) {
            PortableStructurePrinterFeedback.failPrint(
                    level,
                    pos,
                    printer,
                    printer.reservedMaterialStacks(),
                    exception
            );
            return;
        }
        long gameTime = level.getGameTime();
        if (printer.shouldEmitAt(gameTime)) {
            PortableStructurePrinterPrintPlan.PrintTarget target = printer.emitNextPrintTarget();
            if (target != null) {
                SpawnPortableStructurePrinterEffectPayload payload = new SpawnPortableStructurePrinterEffectPayload(
                        pos,
                        target.position().x,
                        target.position().y,
                        target.position().z,
                        target.orientation().x,
                        target.orientation().y,
                        target.orientation().z,
                        target.orientation().w,
                        (int) (gameTime * 31L + printer.emittedPrintBlocks() * 17L + pos.asLong())
                );
                for (ServerPlayer player : level.players()) {
                    if (player.blockPosition().closerThan(pos, 64.0D)) {
                        PacketDistributor.sendToPlayer(player, payload);
                        syncState(player, printer);
                    }
                }
            }
            if (printer.emittedPrintBlocks() >= printer.totalPrintBlocks()) {
                printer.scheduleFinalize(gameTime + EFFECT_TRAVEL_TICKS);
            } else {
                printer.scheduleNextEmit(gameTime + EMIT_INTERVAL_TICKS);
            }
        }
        if (!printer.shouldFinalizeAt(gameTime)) {
            return;
        }

        BlockPos clickedPos = printer.getBlockPos();
        List<ItemStack> reservedMaterials = printer.reservedMaterialStacks();
        try {
            byte[] blueprintBytes = PortableStructurePrinterBlueprintSanitizer.sanitizeForPortablePrinter(
                    printer.blueprintBytes(),
                    PortableStructurePrinterSettings.shouldStripToolgunLinks(level)
            );
            if (CreatePhysicalBlueprintService.hasCreatePhysicalLayout(blueprintBytes)) {
                ServerPlayer placementPlayer = printer.owner() == null
                        ? null
                        : level.getServer().getPlayerList().getPlayer(printer.owner());
                PortableStructurePrinterPlacement.placeBlueprint(
                        level,
                        clickedPos,
                        placementPlayer,
                        printer.blueprintName(),
                        blueprintBytes,
                        printer.owner(),
                        null
                );
                printer.finishPrintJob();
                PortableStructurePrinterFeedback.sendCompletionEffect(level, pos, printer);
                PortableStructurePrinterFeedback.notifyPrintCompleted(
                        level,
                        pos,
                        printer.owner(),
                        printer.blueprintDisplayName()
                );
            } else {
                PortableStructurePrinterFeedback.PlacementObserver observer =
                        PortableStructurePrinterFeedback.createPlacementObserver(
                                level,
                                pos,
                                printer.owner(),
                                printer.blueprintDisplayName(),
                                reservedMaterials,
                                PortableStructurePrinterPlacementGate.begin(level, pos)
                        );
                try {
                    PortableStructurePrinterPlacement.placeBlueprint(
                            level,
                            clickedPos,
                            null,
                            printer.blueprintName(),
                            blueprintBytes,
                            printer.owner(),
                            observer
                    );
                } catch (Exception exception) {
                    observer.releaseGate();
                    throw exception;
                }
                printer.finishPrintJob();
                PortableStructurePrinterFeedback.sendCompletionEffect(level, pos, printer);
            }
        } catch (Exception exception) {
            PortableStructurePrinterFeedback.failPrint(level, pos, printer, reservedMaterials, exception);
        }
    }

    static void refundReservedMaterials(
            ServerLevel level,
            BlockPos pos,
            PortableStructurePrinterBlockEntity printer,
            String reason
    ) {
        List<ItemStack> reserved = printer.reservedMaterialStacks();
        if (reserved.isEmpty()) {
            return;
        }
        PortableStructurePrinterInventory.restoreRemoved(level, pos, reserved);
        printer.clearPrintJob();
        printer.setChanged();
        AeronauticsStructureToolMod.LOGGER.warn(
                "Refunded portable-printer materials at {} because {}",
                pos,
                reason
        );
    }

    private static boolean isBusy(ServerLevel level, PortableStructurePrinterBlockEntity printer) {
        return printer.printing()
                || PortableStructurePrinterPlacementGate.isPending(level, printer.getBlockPos());
    }

    private static PrinterAccess resolvePrinter(ServerPlayer player, BlockPos printerPos) {
        if (!(player.level() instanceof ServerLevel level)) {
            player.sendSystemMessage(Component.translatable(
                    "message.create_aeronautics_toolgun.printer_missing"
            ));
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Portable-printer request from {} had no server level",
                    player.getGameProfile().getName()
            );
            return null;
        }
        if (!(level.getBlockEntity(printerPos) instanceof PortableStructurePrinterBlockEntity printer)) {
            player.sendSystemMessage(Component.translatable(
                    "message.create_aeronautics_toolgun.printer_missing"
            ));
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Portable-printer request from {} targeted missing printer at {} in {}",
                    player.getGameProfile().getName(),
                    printerPos,
                    level.dimension().location()
            );
            return null;
        }
        return new PrinterAccess(level, printer);
    }

    private record PrinterAccess(
            ServerLevel level,
            PortableStructurePrinterBlockEntity printer
    ) {
    }

}
