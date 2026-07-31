package com.enxv.aeronauticsstructuretool;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record RequestQueryVehiclePreviewPayload(UUID subLevelId) implements CustomPacketPayload {
    public static final Type<RequestQueryVehiclePreviewPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "request_query_vehicle_preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestQueryVehiclePreviewPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            RequestQueryVehiclePreviewPayload::subLevelId,
            RequestQueryVehiclePreviewPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
