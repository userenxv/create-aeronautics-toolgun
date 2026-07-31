package com.enxv.aeronauticsstructuretool;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SpawnPortableStructurePrinterEffectPayload(
        BlockPos printerPos,
        double targetX,
        double targetY,
        double targetZ,
        double orientationX,
        double orientationY,
        double orientationZ,
        double orientationW,
        int seed
) implements CustomPacketPayload {
    public static final Type<SpawnPortableStructurePrinterEffectPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "spawn_portable_structure_printer_effect"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpawnPortableStructurePrinterEffectPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SpawnPortableStructurePrinterEffectPayload decode(RegistryFriendlyByteBuf buf) {
            return new SpawnPortableStructurePrinterEffectPayload(
                    BlockPos.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf),
                    ByteBufCodecs.INT.decode(buf)
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, SpawnPortableStructurePrinterEffectPayload payload) {
            BlockPos.STREAM_CODEC.encode(buf, payload.printerPos());
            ByteBufCodecs.DOUBLE.encode(buf, payload.targetX());
            ByteBufCodecs.DOUBLE.encode(buf, payload.targetY());
            ByteBufCodecs.DOUBLE.encode(buf, payload.targetZ());
            ByteBufCodecs.DOUBLE.encode(buf, payload.orientationX());
            ByteBufCodecs.DOUBLE.encode(buf, payload.orientationY());
            ByteBufCodecs.DOUBLE.encode(buf, payload.orientationZ());
            ByteBufCodecs.DOUBLE.encode(buf, payload.orientationW());
            ByteBufCodecs.INT.encode(buf, payload.seed());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
