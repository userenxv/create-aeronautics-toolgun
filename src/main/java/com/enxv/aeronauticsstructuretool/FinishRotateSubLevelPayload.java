package com.enxv.aeronauticsstructuretool;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FinishRotateSubLevelPayload(
        boolean confirm
) implements CustomPacketPayload {
    public static final Type<FinishRotateSubLevelPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "finish_rotate_sublevel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FinishRotateSubLevelPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            FinishRotateSubLevelPayload::confirm,
            FinishRotateSubLevelPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
