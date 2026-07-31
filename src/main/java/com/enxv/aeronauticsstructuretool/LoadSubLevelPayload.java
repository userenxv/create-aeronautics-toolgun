package com.enxv.aeronauticsstructuretool;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

public record LoadSubLevelPayload(
        UUID transferId,
        BlockPos clickedPos,
        Direction face,
        double hitX,
        double hitY,
        double hitZ,
        int rotationDegrees,
        int scalePercent,
        int offsetX,
        int offsetY,
        int offsetZ,
        boolean autoWeld,
        String connectionMode,
        String snapMode,
        String fileName,
        int totalBytes,
        byte[] sha256
) implements CustomPacketPayload {
    public static final Type<LoadSubLevelPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "load_sublevel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LoadSubLevelPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                UUIDUtil.STREAM_CODEC.encode(buffer, payload.transferId());
                BlockPos.STREAM_CODEC.encode(buffer, payload.clickedPos());
                Direction.STREAM_CODEC.encode(buffer, payload.face());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.hitX());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.hitY());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.hitZ());
                ByteBufCodecs.INT.encode(buffer, payload.rotationDegrees());
                ByteBufCodecs.INT.encode(buffer, payload.scalePercent());
                ByteBufCodecs.INT.encode(buffer, payload.offsetX());
                ByteBufCodecs.INT.encode(buffer, payload.offsetY());
                ByteBufCodecs.INT.encode(buffer, payload.offsetZ());
                ByteBufCodecs.BOOL.encode(buffer, payload.autoWeld());
                ByteBufCodecs.STRING_UTF8.encode(buffer, payload.connectionMode());
                ByteBufCodecs.STRING_UTF8.encode(buffer, payload.snapMode());
                ByteBufCodecs.STRING_UTF8.encode(buffer, payload.fileName());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.totalBytes());
                buffer.writeByteArray(payload.sha256());
            },
            buffer -> new LoadSubLevelPayload(
                    UUIDUtil.STREAM_CODEC.decode(buffer),
                    BlockPos.STREAM_CODEC.decode(buffer),
                    Direction.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.INT.decode(buffer),
                    ByteBufCodecs.INT.decode(buffer),
                    ByteBufCodecs.INT.decode(buffer),
                    ByteBufCodecs.INT.decode(buffer),
                    ByteBufCodecs.INT.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    buffer.readByteArray(32)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
