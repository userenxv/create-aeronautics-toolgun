package com.enxv.aeronauticsstructuretool;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncLocalBlueprintPayload(String fileName, byte[] fileContents) implements CustomPacketPayload {
    public static final Type<SyncLocalBlueprintPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "sync_local_blueprint"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncLocalBlueprintPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            SyncLocalBlueprintPayload::fileName,
            ByteBufCodecs.BYTE_ARRAY,
            SyncLocalBlueprintPayload::fileContents,
            SyncLocalBlueprintPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
