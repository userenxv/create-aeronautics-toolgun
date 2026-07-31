package com.enxv.aeronauticsstructuretool;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record MagneticGunStartPayload(
        UUID subLevelId,
        double hitX,
        double hitY,
        double hitZ,
        double initialDistance,
        boolean precisionMode
) implements CustomPacketPayload {
    public static final Type<MagneticGunStartPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "magnetic_gun_start"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MagneticGunStartPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeLong(payload.subLevelId().getMostSignificantBits());
                buffer.writeLong(payload.subLevelId().getLeastSignificantBits());
                buffer.writeDouble(payload.hitX());
                buffer.writeDouble(payload.hitY());
                buffer.writeDouble(payload.hitZ());
                buffer.writeDouble(payload.initialDistance());
                buffer.writeBoolean(payload.precisionMode());
            },
            buffer -> new MagneticGunStartPayload(
                    new UUID(buffer.readLong(), buffer.readLong()),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readBoolean()
            )
    );

    @Override
    public Type<MagneticGunStartPayload> type() {
        return TYPE;
    }
}
