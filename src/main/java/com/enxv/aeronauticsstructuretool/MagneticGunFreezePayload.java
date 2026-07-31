package com.enxv.aeronauticsstructuretool;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MagneticGunFreezePayload(boolean frozen) implements CustomPacketPayload {
    public static final Type<MagneticGunFreezePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "magnetic_gun_freeze"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MagneticGunFreezePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            MagneticGunFreezePayload::frozen,
            MagneticGunFreezePayload::new
    );

    @Override
    public Type<MagneticGunFreezePayload> type() {
        return TYPE;
    }
}
