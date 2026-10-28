package com.enxv.aeronauticsstructuretool;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncContainerCapturePayload(float progressTicks, int totalTicks, long blockCount, float rate)
        implements CustomPacketPayload {
    public static final Type<SyncContainerCapturePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            AeronauticsStructureToolMod.MOD_ID, "sync_container_capture"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncContainerCapturePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncContainerCapturePayload decode(RegistryFriendlyByteBuf buf) {
            return new SyncContainerCapturePayload(buf.readFloat(), buf.readVarInt(), buf.readVarLong(), buf.readFloat());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, SyncContainerCapturePayload payload) {
            buf.writeFloat(payload.progressTicks());
            buf.writeVarInt(payload.totalTicks());
            buf.writeVarLong(payload.blockCount());
            buf.writeFloat(payload.rate());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
