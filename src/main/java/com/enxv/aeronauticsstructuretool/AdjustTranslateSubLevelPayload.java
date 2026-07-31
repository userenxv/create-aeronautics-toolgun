package com.enxv.aeronauticsstructuretool;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record AdjustTranslateSubLevelPayload(
        UUID subLevelId,
        String axisName,
        double distanceDelta
) implements CustomPacketPayload {
    public static final Type<AdjustTranslateSubLevelPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "adjust_translate_sublevel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdjustTranslateSubLevelPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                UUIDUtil.STREAM_CODEC.encode(buffer, payload.subLevelId());
                ByteBufCodecs.STRING_UTF8.encode(buffer, payload.axisName());
                ByteBufCodecs.DOUBLE.encode(buffer, payload.distanceDelta());
            },
            buffer -> new AdjustTranslateSubLevelPayload(
                    UUIDUtil.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
