package com.enxv.aeronauticsstructuretool;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MagneticGunStopPayload() implements CustomPacketPayload {
    public static final Type<MagneticGunStopPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "magnetic_gun_stop"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MagneticGunStopPayload> STREAM_CODEC = StreamCodec.unit(new MagneticGunStopPayload());

    @Override
    public Type<MagneticGunStopPayload> type() {
        return TYPE;
    }
}
