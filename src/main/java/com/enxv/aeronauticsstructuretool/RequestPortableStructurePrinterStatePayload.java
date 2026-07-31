package com.enxv.aeronauticsstructuretool;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestPortableStructurePrinterStatePayload(BlockPos printerPos) implements CustomPacketPayload {
    public static final Type<RequestPortableStructurePrinterStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "request_portable_structure_printer_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestPortableStructurePrinterStatePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            RequestPortableStructurePrinterStatePayload::printerPos,
            RequestPortableStructurePrinterStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
