package com.enxv.aeronauticsstructuretool;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record SyncQueryVehiclePreviewPayload(
        UUID subLevelId,
        boolean success,
        String name,
        byte[] blueprintBytes,
        String error
) implements CustomPacketPayload {
    public static final Type<SyncQueryVehiclePreviewPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "sync_query_vehicle_preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncQueryVehiclePreviewPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                UUIDUtil.STREAM_CODEC.encode(buffer, payload.subLevelId());
                ByteBufCodecs.BOOL.encode(buffer, payload.success());
                ByteBufCodecs.STRING_UTF8.encode(buffer, payload.name());
                ByteBufCodecs.BYTE_ARRAY.encode(buffer, payload.blueprintBytes());
                ByteBufCodecs.STRING_UTF8.encode(buffer, payload.error());
            },
            buffer -> new SyncQueryVehiclePreviewPayload(
                    UUIDUtil.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.BYTE_ARRAY.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
