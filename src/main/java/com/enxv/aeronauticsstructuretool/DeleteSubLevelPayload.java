package com.enxv.aeronauticsstructuretool;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DeleteSubLevelPayload(BlockPos clickedPos, boolean rangeDeleteEnabled, int deleteRange) implements CustomPacketPayload {
    public static final Type<DeleteSubLevelPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "delete_sublevel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteSubLevelPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            DeleteSubLevelPayload::clickedPos,
            ByteBufCodecs.BOOL,
            DeleteSubLevelPayload::rangeDeleteEnabled,
            ByteBufCodecs.INT,
            DeleteSubLevelPayload::deleteRange,
            DeleteSubLevelPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
