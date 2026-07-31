package com.enxv.aeronauticsstructuretool;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MagneticGunPrecisionPayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<MagneticGunPrecisionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "magnetic_gun_precision")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MagneticGunPrecisionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    MagneticGunPrecisionPayload::enabled,
                    MagneticGunPrecisionPayload::new
            );

    @Override
    public Type<MagneticGunPrecisionPayload> type() {
        return TYPE;
    }
}
