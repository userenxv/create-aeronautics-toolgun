package com.enxv.aeronauticsstructuretool;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record CompleteLoadSubLevelPayload(UUID transferId) implements CustomPacketPayload {
    public static final Type<CompleteLoadSubLevelPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            AeronauticsStructureToolMod.MOD_ID,
            "complete_load_sublevel"
    ));
    public static final StreamCodec<RegistryFriendlyByteBuf, CompleteLoadSubLevelPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            CompleteLoadSubLevelPayload::transferId,
            CompleteLoadSubLevelPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
