package com.enxv.aeronauticsstructuretool;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record MagneticGunLaunchPayload(
        UUID subLevelId,
        double hitX,
        double hitY,
        double hitZ
) implements CustomPacketPayload {
    public static final Type<MagneticGunLaunchPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "magnetic_gun_launch")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MagneticGunLaunchPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeLong(payload.subLevelId().getMostSignificantBits());
                buffer.writeLong(payload.subLevelId().getLeastSignificantBits());
                buffer.writeDouble(payload.hitX());
                buffer.writeDouble(payload.hitY());
                buffer.writeDouble(payload.hitZ());
            },
            buffer -> new MagneticGunLaunchPayload(
                    new UUID(buffer.readLong(), buffer.readLong()),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble()
            )
    );

    @Override
    public Type<MagneticGunLaunchPayload> type() {
        return TYPE;
    }
}
