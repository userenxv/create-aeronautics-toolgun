package com.enxv.aeronauticsstructuretool;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SyncConstraintVisualsPayload(List<Entry> entries) implements CustomPacketPayload {
    public static final Type<SyncConstraintVisualsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "sync_constraint_visuals"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Vector3d> VECTOR_CODEC = StreamCodec.of(
            (buffer, vector) -> {
                ByteBufCodecs.DOUBLE.encode(buffer, vector.x);
                ByteBufCodecs.DOUBLE.encode(buffer, vector.y);
                ByteBufCodecs.DOUBLE.encode(buffer, vector.z);
            },
            buffer -> new Vector3d(
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer)
            )
    );

    private static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_CODEC = StreamCodec.of(
            (buffer, entry) -> {
                UUIDUtil.STREAM_CODEC.encode(buffer, entry.firstSubLevelId());
                UUIDUtil.STREAM_CODEC.encode(buffer, entry.secondSubLevelId());
                ByteBufCodecs.STRING_UTF8.encode(buffer, entry.connectionMode());
                VECTOR_CODEC.encode(buffer, entry.firstDisplayLocalPoint());
                VECTOR_CODEC.encode(buffer, entry.secondDisplayLocalPoint());
                VECTOR_CODEC.encode(buffer, entry.firstConstraintLocalPoint());
                VECTOR_CODEC.encode(buffer, entry.secondConstraintLocalPoint());
                ByteBufCodecs.BOOL.encode(buffer, entry.firstAxisLocal() != null);
                if (entry.firstAxisLocal() != null) {
                    VECTOR_CODEC.encode(buffer, entry.firstAxisLocal());
                }
            },
            buffer -> {
                UUID firstSubLevelId = UUIDUtil.STREAM_CODEC.decode(buffer);
                UUID secondSubLevelId = UUIDUtil.STREAM_CODEC.decode(buffer);
                String connectionMode = ByteBufCodecs.STRING_UTF8.decode(buffer);
                Vector3d firstDisplayLocalPoint = VECTOR_CODEC.decode(buffer);
                Vector3d secondDisplayLocalPoint = VECTOR_CODEC.decode(buffer);
                Vector3d firstConstraintLocalPoint = VECTOR_CODEC.decode(buffer);
                Vector3d secondConstraintLocalPoint = VECTOR_CODEC.decode(buffer);
                Vector3d firstAxisLocal = ByteBufCodecs.BOOL.decode(buffer) ? VECTOR_CODEC.decode(buffer) : null;
                return new Entry(firstSubLevelId, secondSubLevelId, connectionMode, firstDisplayLocalPoint, secondDisplayLocalPoint, firstConstraintLocalPoint, secondConstraintLocalPoint, firstAxisLocal);
            }
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncConstraintVisualsPayload> STREAM_CODEC = StreamCodec.of(
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
                return new SyncConstraintVisualsPayload(entries);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(
            UUID firstSubLevelId,
            UUID secondSubLevelId,
            String connectionMode,
            Vector3d firstDisplayLocalPoint,
            Vector3d secondDisplayLocalPoint,
            Vector3d firstConstraintLocalPoint,
            Vector3d secondConstraintLocalPoint,
            Vector3d firstAxisLocal
    ) {
    }
}
