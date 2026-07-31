package com.enxv.aeronauticsstructuretool;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MagneticGunAdjustDistancePayload(double delta) implements CustomPacketPayload {
    public static final Type<MagneticGunAdjustDistancePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "magnetic_gun_adjust_distance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MagneticGunAdjustDistancePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE,
            MagneticGunAdjustDistancePayload::delta,
            MagneticGunAdjustDistancePayload::new
    );

    @Override
    public Type<MagneticGunAdjustDistancePayload> type() {
        return TYPE;
    }
}
