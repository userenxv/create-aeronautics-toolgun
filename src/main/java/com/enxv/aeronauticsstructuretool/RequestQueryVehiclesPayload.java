package com.enxv.aeronauticsstructuretool;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestQueryVehiclesPayload(int range) implements CustomPacketPayload {
    public static final Type<RequestQueryVehiclesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "request_query_vehicles"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestQueryVehiclesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            RequestQueryVehiclesPayload::range,
            RequestQueryVehiclesPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
