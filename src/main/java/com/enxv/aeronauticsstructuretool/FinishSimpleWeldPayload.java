package com.enxv.aeronauticsstructuretool;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record FinishSimpleWeldPayload(
        UUID childSubLevelId,
        UUID parentSubLevelId,
        double childLocalX,
        double childLocalY,
        double childLocalZ,
        double parentLocalX,
        double parentLocalY,
        double parentLocalZ,
        double relativeRotationX,
        double relativeRotationY,
        double relativeRotationZ,
        double relativeRotationW,
        double parentOffsetX,
        double parentOffsetY,
        double parentOffsetZ
) implements CustomPacketPayload {
    public static final Type<FinishSimpleWeldPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "finish_simple_weld"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FinishSimpleWeldPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                UUIDUtil.STREAM_CODEC.encode(buffer, payload.childSubLevelId());
                UUIDUtil.STREAM_CODEC.encode(buffer, payload.parentSubLevelId());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.childLocalX());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.childLocalY());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.childLocalZ());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.parentLocalX());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.parentLocalY());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.parentLocalZ());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.relativeRotationX());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.relativeRotationY());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.relativeRotationZ());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.relativeRotationW());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.parentOffsetX());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.parentOffsetY());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.parentOffsetZ());
            },
            buffer -> new FinishSimpleWeldPayload(
                    UUIDUtil.STREAM_CODEC.decode(buffer),
                    UUIDUtil.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
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
