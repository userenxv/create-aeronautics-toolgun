package com.enxv.aeronauticsstructuretool;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenPortableStructurePrinterPayload(BlockPos printerPos, String displayName, boolean hasBlueprint) implements CustomPacketPayload {
    public static final Type<OpenPortableStructurePrinterPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "open_portable_structure_printer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenPortableStructurePrinterPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenPortableStructurePrinterPayload::printerPos,
            ByteBufCodecs.STRING_UTF8, OpenPortableStructurePrinterPayload::displayName,
            ByteBufCodecs.BOOL, OpenPortableStructurePrinterPayload::hasBlueprint,
            OpenPortableStructurePrinterPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
