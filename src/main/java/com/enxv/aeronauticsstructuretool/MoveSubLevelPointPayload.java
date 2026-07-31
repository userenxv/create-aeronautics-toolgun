package com.enxv.aeronauticsstructuretool;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

public record MoveSubLevelPointPayload(
        UUID subLevelId,
        double localX,
        double localY,
        double localZ,
        double targetX,
        double targetY,
        double targetZ
) implements CustomPacketPayload {
    public static final Type<MoveSubLevelPointPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "move_sublevel_point"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MoveSubLevelPointPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                UUIDUtil.STREAM_CODEC.encode(buffer, payload.subLevelId());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.localX());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.localY());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.localZ());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.targetX());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.targetY());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.targetZ());
            },
            buffer -> new MoveSubLevelPointPayload(
                    UUIDUtil.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
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
