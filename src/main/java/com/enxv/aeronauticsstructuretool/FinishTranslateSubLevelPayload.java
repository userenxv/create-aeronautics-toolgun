package com.enxv.aeronauticsstructuretool;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FinishTranslateSubLevelPayload(
        boolean confirm
) implements CustomPacketPayload {
    public static final Type<FinishTranslateSubLevelPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "finish_translate_sublevel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FinishTranslateSubLevelPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            FinishTranslateSubLevelPayload::confirm,
            FinishTranslateSubLevelPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
