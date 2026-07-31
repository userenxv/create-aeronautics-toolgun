package com.enxv.aeronauticsstructuretool.vehicle.container;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

public final class PortableContainerAuditLogger {
    private static final Path LOG_PATH = FMLPaths.GAMEDIR.get()
            .resolve("logs")
            .resolve("portable_structure_container_audit.txt");
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private PortableContainerAuditLogger() {
    }

    public static void logCapture(
            ServerPlayer player,
            UUID targetStructureId,
            int structureCount,
            double totalMass
    ) {
        writeLine("CAPTURE", player, targetStructureId, structureCount, totalMass);
    }

    public static void logPlace(
            ServerPlayer player,
            UUID targetStructureId,
            int structureCount,
            double totalMass
    ) {
        writeLine("PLACE", player, targetStructureId, structureCount, totalMass);
    }

    private static synchronized void writeLine(
            String action,
            ServerPlayer player,
            UUID targetStructureId,
            int structureCount,
            double totalMass
    ) {
        try {
            Files.createDirectories(LOG_PATH.getParent());
            BlockPos pos = player.blockPosition();
            String line = String.format(
                    Locale.ROOT,
                    "[%s] player=%s action=%s structureId=%s structureCount=%d totalMass=%.3f playerPos=%d,%d,%d%n",
                    LocalDateTime.now().format(TIME_FORMAT),
                    player.getGameProfile().getName(),
                    action,
                    targetStructureId == null ? "<unknown>" : targetStructureId,
                    structureCount,
                    totalMass,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ()
            );
            Files.writeString(
                    LOG_PATH,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            AeronauticsStructureToolMod.LOGGER.error(
                    "Failed to write portable structure container audit log",
                    exception
            );
        }
    }
}
