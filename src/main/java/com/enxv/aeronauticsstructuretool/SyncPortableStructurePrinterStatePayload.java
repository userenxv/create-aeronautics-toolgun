package com.enxv.aeronauticsstructuretool;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

public record SyncPortableStructurePrinterStatePayload(
        BlockPos printerPos,
        String displayName,
        String blueprintName,
        boolean hasBlueprint,
        boolean ready,
        boolean printing,
        String statusMessage,
        float materialProgress,
        float printProgress,
        ItemStack checklistStack,
        Map<String, Long> requiredItems,
        Map<String, Long> missingItems
) implements CustomPacketPayload {
    public static final Type<SyncPortableStructurePrinterStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "sync_portable_structure_printer_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPortableStructurePrinterStatePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncPortableStructurePrinterStatePayload decode(RegistryFriendlyByteBuf buf) {
            BlockPos printerPos = BlockPos.STREAM_CODEC.decode(buf);
            String displayName = ByteBufCodecs.STRING_UTF8.decode(buf);
            String blueprintName = ByteBufCodecs.STRING_UTF8.decode(buf);
            boolean hasBlueprint = ByteBufCodecs.BOOL.decode(buf);
            boolean ready = ByteBufCodecs.BOOL.decode(buf);
            boolean printing = ByteBufCodecs.BOOL.decode(buf);
            String statusMessage = ByteBufCodecs.STRING_UTF8.decode(buf);
            float materialProgress = ByteBufCodecs.FLOAT.decode(buf);
            float printProgress = ByteBufCodecs.FLOAT.decode(buf);
            ItemStack checklistStack = ByteBufCodecs.BOOL.decode(buf)
                    ? ItemStack.STREAM_CODEC.decode(buf)
                    : ItemStack.EMPTY;
            Map<String, Long> required = readLongMap(buf);
            Map<String, Long> missing = readLongMap(buf);
            return new SyncPortableStructurePrinterStatePayload(printerPos, displayName, blueprintName, hasBlueprint, ready, printing, statusMessage, materialProgress, printProgress, checklistStack, Map.copyOf(required), Map.copyOf(missing));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, SyncPortableStructurePrinterStatePayload payload) {
            BlockPos.STREAM_CODEC.encode(buf, payload.printerPos());
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.displayName());
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.blueprintName());
            ByteBufCodecs.BOOL.encode(buf, payload.hasBlueprint());
            ByteBufCodecs.BOOL.encode(buf, payload.ready());
            ByteBufCodecs.BOOL.encode(buf, payload.printing());
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.statusMessage());
            ByteBufCodecs.FLOAT.encode(buf, payload.materialProgress());
            ByteBufCodecs.FLOAT.encode(buf, payload.printProgress());
            boolean hasChecklistStack = payload.checklistStack() != null && !payload.checklistStack().isEmpty();
            ByteBufCodecs.BOOL.encode(buf, hasChecklistStack);
            if (hasChecklistStack) {
                ItemStack.STREAM_CODEC.encode(buf, payload.checklistStack());
            }
            writeLongMap(buf, payload.requiredItems());
            writeLongMap(buf, payload.missingItems());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static Map<String, Long> readLongMap(RegistryFriendlyByteBuf buf) {
        int size = ByteBufCodecs.INT.decode(buf);
        Map<String, Long> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            map.put(ByteBufCodecs.STRING_UTF8.decode(buf), buf.readLong());
        }
        return map;
    }

    private static void writeLongMap(RegistryFriendlyByteBuf buf, Map<String, Long> map) {
        ByteBufCodecs.INT.encode(buf, map.size());
        for (Map.Entry<String, Long> entry : map.entrySet()) {
            ByteBufCodecs.STRING_UTF8.encode(buf, entry.getKey());
            buf.writeLong(entry.getValue());
        }
    }
}
