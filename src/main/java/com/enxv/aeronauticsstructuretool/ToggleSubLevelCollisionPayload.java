package com.enxv.aeronauticsstructuretool;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ToggleSubLevelCollisionPayload(BlockPos clickedPos) implements CustomPacketPayload {
    public static final Type<ToggleSubLevelCollisionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            AeronauticsStructureToolMod.MOD_ID,
            "toggle_sublevel_collision"
    ));
    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleSubLevelCollisionPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> BlockPos.STREAM_CODEC.encode(buffer, payload.clickedPos()),
            buffer -> new ToggleSubLevelCollisionPayload(BlockPos.STREAM_CODEC.decode(buffer))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
