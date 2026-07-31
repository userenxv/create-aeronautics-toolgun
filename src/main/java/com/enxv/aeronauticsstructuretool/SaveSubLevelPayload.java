package com.enxv.aeronauticsstructuretool;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SaveSubLevelPayload(BlockPos clickedPos, String fileName, double connectedSublevelProximity) implements CustomPacketPayload {
    public static final Type<SaveSubLevelPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "save_sublevel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveSubLevelPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            SaveSubLevelPayload::clickedPos,
            ByteBufCodecs.STRING_UTF8,
            SaveSubLevelPayload::fileName,
            ByteBufCodecs.DOUBLE,
            SaveSubLevelPayload::connectedSublevelProximity,
            SaveSubLevelPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
