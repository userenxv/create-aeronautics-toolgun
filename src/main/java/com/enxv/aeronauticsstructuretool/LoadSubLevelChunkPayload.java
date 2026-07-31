package com.enxv.aeronauticsstructuretool;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record LoadSubLevelChunkPayload(UUID transferId, int offset, byte[] contents)
        implements CustomPacketPayload {
    public static final int MAX_CHUNK_BYTES = 24 * 1024;
    public static final Type<LoadSubLevelChunkPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            AeronauticsStructureToolMod.MOD_ID,
            "load_sublevel_chunk"
    ));
    public static final StreamCodec<RegistryFriendlyByteBuf, LoadSubLevelChunkPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                UUIDUtil.STREAM_CODEC.encode(buffer, payload.transferId());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.offset());
                buffer.writeByteArray(payload.contents());
            },
            buffer -> new LoadSubLevelChunkPayload(
                    UUIDUtil.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    buffer.readByteArray(MAX_CHUNK_BYTES)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
