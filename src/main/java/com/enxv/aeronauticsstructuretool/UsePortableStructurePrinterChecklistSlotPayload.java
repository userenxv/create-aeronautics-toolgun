package com.enxv.aeronauticsstructuretool;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UsePortableStructurePrinterChecklistSlotPayload(BlockPos printerPos) implements CustomPacketPayload {
    public static final Type<UsePortableStructurePrinterChecklistSlotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "use_portable_structure_printer_checklist_slot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UsePortableStructurePrinterChecklistSlotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    UsePortableStructurePrinterChecklistSlotPayload::printerPos,
                    UsePortableStructurePrinterChecklistSlotPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
