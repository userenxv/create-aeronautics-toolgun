package com.enxv.aeronauticsstructuretool;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

public record BeginRotateSubLevelPayload(
        UUID subLevelId,
        double pivotLocalX,
        double pivotLocalY,
        double pivotLocalZ
) implements CustomPacketPayload {
    public static final Type<BeginRotateSubLevelPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "begin_rotate_sublevel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BeginRotateSubLevelPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                UUIDUtil.STREAM_CODEC.encode(buffer, payload.subLevelId());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.pivotLocalX());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.pivotLocalY());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.pivotLocalZ());
            },
            buffer -> new BeginRotateSubLevelPayload(
                    UUIDUtil.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
