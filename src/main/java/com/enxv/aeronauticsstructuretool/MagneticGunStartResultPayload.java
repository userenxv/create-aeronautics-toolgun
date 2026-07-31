package com.enxv.aeronauticsstructuretool;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MagneticGunStartResultPayload(UUID subLevelId, boolean accepted) implements CustomPacketPayload {
    public static final Type<MagneticGunStartResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "magnetic_gun_start_result")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MagneticGunStartResultPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUUID(payload.subLevelId());
                buffer.writeBoolean(payload.accepted());
            },
            buffer -> new MagneticGunStartResultPayload(buffer.readUUID(), buffer.readBoolean())
    );

    @Override
    public Type<MagneticGunStartResultPayload> type() {
        return TYPE;
    }
}
