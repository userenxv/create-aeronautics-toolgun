package com.enxv.aeronauticsstructuretool;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MagneticGunRotatePayload(
        double yawDegrees,
        double pitchDegrees,
        double rollDegrees
) implements CustomPacketPayload {
    public static final Type<MagneticGunRotatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "magnetic_gun_rotate")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MagneticGunRotatePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeDouble(payload.yawDegrees());
                buffer.writeDouble(payload.pitchDegrees());
                buffer.writeDouble(payload.rollDegrees());
            },
            buffer -> new MagneticGunRotatePayload(
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble()
            )
    );

    @Override
    public Type<MagneticGunRotatePayload> type() {
        return TYPE;
    }
}
