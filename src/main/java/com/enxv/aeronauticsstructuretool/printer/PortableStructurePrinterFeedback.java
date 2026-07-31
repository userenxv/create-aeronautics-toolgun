package com.enxv.aeronauticsstructuretool.printer;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.CompletePortableStructurePrinterEffectPayload;
import com.enxv.aeronauticsstructuretool.blueprint.runtime.BlueprintPlacementObserver;
import com.enxv.aeronauticsstructuretool.core.FailureMessages;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

final class PortableStructurePrinterFeedback {
    private PortableStructurePrinterFeedback() {
    }

    static PlacementObserver createPlacementObserver(
            ServerLevel level,
            BlockPos printerPos,
            UUID ownerId,
            String blueprintDisplayName,
            List<ItemStack> reservedMaterials,
            UUID placementToken
    ) {
        return new PlacementObserver(
                level,
                printerPos,
                ownerId,
                blueprintDisplayName,
                reservedMaterials,
                placementToken
        );
    }

    static void failPrint(
            ServerLevel level,
            BlockPos pos,
            PortableStructurePrinterBlockEntity printer,
            List<ItemStack> reservedMaterials,
            Exception exception
    ) {
        PortableStructurePrinterInventory.restoreRemoved(level, pos, reservedMaterials);
        printer.clearPrintJob();
        printer.setChanged();
        AeronauticsStructureToolMod.LOGGER.error("Portable-printer placement failed at {}", pos, exception);
        notifyPrintFailed(level, pos, printer.owner(), failureMessage(exception));
    }

    static void sendCompletionEffect(
            ServerLevel level,
            BlockPos pos,
            PortableStructurePrinterBlockEntity printer
    ) {
        for (ServerPlayer player : level.players()) {
            if (!player.blockPosition().closerThan(pos, 64.0D)) {
                continue;
            }
            try {
                PacketDistributor.sendToPlayer(player, new CompletePortableStructurePrinterEffectPayload(pos));
                PortableStructurePrinterService.syncState(player, printer);
            } catch (RuntimeException exception) {
                AeronauticsStructureToolMod.LOGGER.warn(
                        "Failed to send portable-printer completion effect to {}",
                        player.getGameProfile().getName(),
                        exception
                );
            }
        }
    }

    static void notifyPrintCompleted(
            ServerLevel level,
            BlockPos pos,
            UUID ownerId,
            String blueprintDisplayName
    ) {
        for (ServerPlayer player : notificationPlayers(level, pos, ownerId)) {
            try {
                player.sendSystemMessage(Component.translatable(
                        "message.create_aeronautics_toolgun.printer_printed",
                        blueprintDisplayName
                ));
                syncPrinterState(level, pos, player);
            } catch (RuntimeException exception) {
                AeronauticsStructureToolMod.LOGGER.warn(
                        "Failed to notify {} that portable printing completed",
                        player.getGameProfile().getName(),
                        exception
                );
            }
        }
    }

    static void notifyPrintFailed(
            ServerLevel level,
            BlockPos pos,
            UUID ownerId,
            String reason
    ) {
        for (ServerPlayer player : notificationPlayers(level, pos, ownerId)) {
            try {
                player.sendSystemMessage(Component.translatable(
                        "message.create_aeronautics_toolgun.printer_print_failed",
                        reason
                ));
                syncPrinterState(level, pos, player);
            } catch (RuntimeException exception) {
                AeronauticsStructureToolMod.LOGGER.warn(
                        "Failed to notify {} that portable printing failed",
                        player.getGameProfile().getName(),
                        exception
                );
            }
        }
    }

    private static void syncPrinterState(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (level.getBlockEntity(pos) instanceof PortableStructurePrinterBlockEntity printer) {
            PortableStructurePrinterService.syncState(player, printer);
        }
    }

    private static List<ServerPlayer> notificationPlayers(ServerLevel level, BlockPos pos, UUID ownerId) {
        LinkedHashSet<ServerPlayer> players = new LinkedHashSet<>();
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().closerThan(pos, 64.0D)) {
                players.add(player);
            }
        }
        if (ownerId != null) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
            if (owner != null) {
                players.add(owner);
            }
        }
        return List.copyOf(players);
    }

    private static String failureMessage(Exception exception) {
        return FailureMessages.describe(exception, exception.getClass().getSimpleName());
    }

    static final class PlacementObserver implements BlueprintPlacementObserver {
        private final ServerLevel level;
        private final BlockPos printerPos;
        private final UUID ownerId;
        private final String blueprintDisplayName;
        private final List<ItemStack> reservedMaterials;
        private final UUID placementToken;

        private PlacementObserver(
                ServerLevel level,
                BlockPos printerPos,
                UUID ownerId,
                String blueprintDisplayName,
                List<ItemStack> reservedMaterials,
                UUID placementToken
        ) {
            this.level = level;
            this.printerPos = printerPos.immutable();
            this.ownerId = ownerId;
            this.blueprintDisplayName = blueprintDisplayName;
            this.reservedMaterials = List.copyOf(reservedMaterials);
            this.placementToken = placementToken;
        }

        @Override
        public void onCompleted() {
            releaseGate();
            notifyPrintCompleted(this.level, this.printerPos, this.ownerId, this.blueprintDisplayName);
        }

        @Override
        public void onFailed(String reason) {
            releaseGate();
            PortableStructurePrinterInventory.restoreRemoved(this.level, this.printerPos, this.reservedMaterials);
            AeronauticsStructureToolMod.LOGGER.error(
                    "Portable-printer placement at {} was rolled back asynchronously: {}",
                    this.printerPos,
                    reason
            );
            notifyPrintFailed(this.level, this.printerPos, this.ownerId, reason);
        }

        void releaseGate() {
            PortableStructurePrinterPlacementGate.complete(this.level, this.printerPos, this.placementToken);
        }
    }
}
