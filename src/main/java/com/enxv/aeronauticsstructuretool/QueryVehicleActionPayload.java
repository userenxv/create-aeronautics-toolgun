package com.enxv.aeronauticsstructuretool;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record QueryVehicleActionPayload(
        UUID subLevelId,
        String action,
        double x,
        double y,
        double z,
        String name
) implements CustomPacketPayload {
    public static final String ACTION_TELEPORT = "teleport";
    public static final String ACTION_TELEPORT_PLAYER_TO_VEHICLE = "teleport_player_to_vehicle";
    public static final String ACTION_RENAME = "rename";
    public static final String ACTION_DELETE = "delete";
    public static final String ACTION_RECOVER = "recover";
    public static final String ACTION_CREATE_GHOST = "create_ghost";

    public static final Type<QueryVehicleActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "query_vehicle_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QueryVehicleActionPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                UUIDUtil.STREAM_CODEC.encode(buffer, payload.subLevelId());
                ByteBufCodecs.STRING_UTF8.encode(buffer, payload.action());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.x());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.y());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.z());
                ByteBufCodecs.STRING_UTF8.encode(buffer, payload.name());
            },
            buffer -> new QueryVehicleActionPayload(
                    UUIDUtil.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
