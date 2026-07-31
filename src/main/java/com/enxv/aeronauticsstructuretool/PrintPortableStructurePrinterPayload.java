package com.enxv.aeronauticsstructuretool;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PrintPortableStructurePrinterPayload(BlockPos printerPos) implements CustomPacketPayload {
    public static final Type<PrintPortableStructurePrinterPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "print_portable_structure_printer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PrintPortableStructurePrinterPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            PrintPortableStructurePrinterPayload::printerPos,
            PrintPortableStructurePrinterPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
