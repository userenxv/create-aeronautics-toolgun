package com.enxv.aeronauticsstructuretool;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SelectPortableStructurePrinterBlueprintPayload(
        BlockPos printerPos,
        String displayName,
        String blueprintName,
        byte[] blueprintBytes,
        double previewBottomY
) implements CustomPacketPayload {
    public static final Type<SelectPortableStructurePrinterBlueprintPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "select_portable_structure_printer_blueprint"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectPortableStructurePrinterBlueprintPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SelectPortableStructurePrinterBlueprintPayload::printerPos,
            ByteBufCodecs.STRING_UTF8, SelectPortableStructurePrinterBlueprintPayload::displayName,
            ByteBufCodecs.STRING_UTF8, SelectPortableStructurePrinterBlueprintPayload::blueprintName,
            ByteBufCodecs.BYTE_ARRAY, SelectPortableStructurePrinterBlueprintPayload::blueprintBytes,
            ByteBufCodecs.DOUBLE, SelectPortableStructurePrinterBlueprintPayload::previewBottomY,
            SelectPortableStructurePrinterBlueprintPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
