package com.enxv.aeronauticsstructuretool.printer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

final class PortableStructurePrinterPlacementGate {
    private static final Map<ServerLevel, Map<BlockPos, UUID>> PENDING = new WeakHashMap<>();

    private PortableStructurePrinterPlacementGate() {
    }

    static boolean isPending(ServerLevel level, BlockPos printerPos) {
        Map<BlockPos, UUID> levelPending = PENDING.get(level);
        return levelPending != null && levelPending.containsKey(printerPos);
    }

    static UUID begin(ServerLevel level, BlockPos printerPos) {
        Map<BlockPos, UUID> levelPending = PENDING.computeIfAbsent(level, ignored -> new HashMap<>());
        if (levelPending.containsKey(printerPos)) {
            throw new IllegalStateException("portable-printer placement is already pending at " + printerPos);
        }
        UUID token = UUID.randomUUID();
        levelPending.put(printerPos.immutable(), token);
        return token;
    }

    static void complete(ServerLevel level, BlockPos printerPos, UUID token) {
        Map<BlockPos, UUID> levelPending = PENDING.get(level);
        if (levelPending == null || token == null || !token.equals(levelPending.get(printerPos))) {
            return;
        }
        levelPending.remove(printerPos);
        if (levelPending.isEmpty()) {
            PENDING.remove(level);
        }
    }
}
