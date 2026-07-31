package com.enxv.aeronauticsstructuretool;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CompletePortableStructurePrinterEffectPayload(BlockPos printerPos) implements CustomPacketPayload {
    public static final Type<CompletePortableStructurePrinterEffectPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "complete_portable_structure_printer_effect"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CompletePortableStructurePrinterEffectPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CompletePortableStructurePrinterEffectPayload decode(RegistryFriendlyByteBuf buf) {
            return new CompletePortableStructurePrinterEffectPayload(BlockPos.STREAM_CODEC.decode(buf));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, CompletePortableStructurePrinterEffectPayload payload) {
            BlockPos.STREAM_CODEC.encode(buf, payload.printerPos());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
