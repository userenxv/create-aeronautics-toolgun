package com.enxv.aeronauticsstructuretool;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record WeldSubLevelsPayload(
        UUID firstSubLevelId,
        UUID secondSubLevelId,
        BlockPos firstBlockPos,
        double firstX,
        double firstY,
        double firstZ,
        Direction firstFace,
        BlockPos secondBlockPos,
        double secondLocalX,
        double secondLocalY,
        double secondLocalZ,
        double originalSecondX,
        double originalSecondY,
        double originalSecondZ,
        double adjustedSecondX,
        double adjustedSecondY,
        double adjustedSecondZ,
        Direction secondFace,
        String connectionMode,
        String bearingAxisMode
) implements CustomPacketPayload {
    public static final Type<WeldSubLevelsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "weld_sublevels"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WeldSubLevelsPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                UUIDUtil.STREAM_CODEC.encode(buffer, payload.firstSubLevelId());
                UUIDUtil.STREAM_CODEC.encode(buffer, payload.secondSubLevelId());
                BlockPos.STREAM_CODEC.encode(buffer, payload.firstBlockPos());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.firstX());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.firstY());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.firstZ());
                Direction.STREAM_CODEC.encode(buffer, payload.firstFace());
                BlockPos.STREAM_CODEC.encode(buffer, payload.secondBlockPos());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.secondLocalX());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.secondLocalY());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.secondLocalZ());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.originalSecondX());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.originalSecondY());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.originalSecondZ());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.adjustedSecondX());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.adjustedSecondY());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.adjustedSecondZ());
                Direction.STREAM_CODEC.encode(buffer, payload.secondFace());
                ByteBufCodecs.STRING_UTF8.encode(buffer, payload.connectionMode());
                ByteBufCodecs.STRING_UTF8.encode(buffer, payload.bearingAxisMode());
            },
            buffer -> new WeldSubLevelsPayload(
                    UUIDUtil.STREAM_CODEC.decode(buffer),
                    UUIDUtil.STREAM_CODEC.decode(buffer),
                    BlockPos.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    Direction.STREAM_CODEC.decode(buffer),
                    BlockPos.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    Direction.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
