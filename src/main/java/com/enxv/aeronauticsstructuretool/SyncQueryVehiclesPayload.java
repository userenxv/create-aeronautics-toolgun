package com.enxv.aeronauticsstructuretool;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SyncQueryVehiclesPayload(List<Entry> entries) implements CustomPacketPayload {
    public static final Type<SyncQueryVehiclesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "sync_query_vehicles"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_CODEC = StreamCodec.of(
            (buffer, entry) -> {
                UUIDUtil.STREAM_CODEC.encode(buffer, entry.id());
                ByteBufCodecs.STRING_UTF8.encode(buffer, entry.displayName());
                ByteBufCodecs.STRING_UTF8.encode(buffer, entry.fullName());
                ByteBufCodecs.DOUBLE.encode(buffer, entry.distance());
                BlockPos.STREAM_CODEC.encode(buffer, entry.position());
                ByteBufCodecs.BOOL.encode(buffer, entry.loaded());
                ByteBufCodecs.BOOL.encode(buffer, entry.broken());
            },
            buffer -> new Entry(
                    UUIDUtil.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    BlockPos.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer)
            )
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncQueryVehiclesPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                ByteBufCodecs.VAR_INT.encode(buffer, payload.entries().size());
                for (Entry entry : payload.entries()) {
                    ENTRY_CODEC.encode(buffer, entry);
                }
            },
            buffer -> {
                int size = ByteBufCodecs.VAR_INT.decode(buffer);
                List<Entry> entries = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    entries.add(ENTRY_CODEC.decode(buffer));
                }
                return new SyncQueryVehiclesPayload(List.copyOf(entries));
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(
            UUID id,
            String displayName,
            String fullName,
            double distance,
            BlockPos position,
            boolean loaded,
            boolean broken
    ) {
    }
}
