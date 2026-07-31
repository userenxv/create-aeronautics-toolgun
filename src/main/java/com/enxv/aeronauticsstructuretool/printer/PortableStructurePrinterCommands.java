package com.enxv.aeronauticsstructuretool.printer;

import com.enxv.aeronauticsstructuretool.blueprint.placement.CreatePhysicalBlueprintService;
import com.enxv.aeronauticsstructuretool.blueprint.storage.BlueprintFileRepository;
import com.enxv.aeronauticsstructuretool.core.FailureMessages;
import com.enxv.aeronauticsstructuretool.core.ModConstants;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PortableStructurePrinterCommands {
    private PortableStructurePrinterCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("aerotoolgun")
                .requires(source -> source.hasPermission(4))
                .then(Commands.literal("portable_printer_strip_links")
                        .executes(command -> query(command.getSource()))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(command -> set(
                                        command.getSource(),
                                        BoolArgumentType.getBool(command, "enabled")
                                ))))
                .then(Commands.literal("portable_container_crafting")
                        .executes(command -> queryPortableContainerCrafting(command.getSource()))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(command -> setPortableContainerCrafting(
                                        command.getSource(),
                                        BoolArgumentType.getBool(command, "enabled")
                                ))))
                .then(Commands.literal("print_blueprint")
                        .then(Commands.argument("file", StringArgumentType.string())
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(command -> tryPrintBlueprint(
                                                command.getSource(),
                                                StringArgumentType.getString(command, "file"),
                                                BlockPosArgument.getLoadedBlockPos(command, "pos")
                                        ))))));
    }

    private static int query(CommandSourceStack source) {
        boolean enabled = PortableStructurePrinterSettings.shouldStripToolgunLinks(source.getLevel());
        source.sendSuccess(() -> Component.translatable(
                enabled
                        ? "message.create_aeronautics_toolgun.command.portable_printer_strip_links.on"
                        : "message.create_aeronautics_toolgun.command.portable_printer_strip_links.off"
        ), true);
        return enabled ? 1 : 0;
    }

    private static int set(CommandSourceStack source, boolean enabled) {
        ServerLevel level = source.getLevel();
        PortableStructurePrinterSettings.setStripToolgunLinks(level, enabled);
        source.sendSuccess(() -> Component.translatable(
                enabled
                        ? "message.create_aeronautics_toolgun.command.portable_printer_strip_links.on"
                        : "message.create_aeronautics_toolgun.command.portable_printer_strip_links.off"
        ), true);
        return 1;
    }

    private static int queryPortableContainerCrafting(CommandSourceStack source) {
        boolean enabled = PortableStructurePrinterSettings.canCraftPortableStructureContainer(source.getLevel());
        source.sendSuccess(() -> Component.translatable(
                enabled
                        ? "message.create_aeronautics_toolgun.command.portable_container_crafting.on"
                        : "message.create_aeronautics_toolgun.command.portable_container_crafting.off"
        ), true);
        return enabled ? 1 : 0;
    }

    private static int setPortableContainerCrafting(CommandSourceStack source, boolean enabled) {
        ServerLevel level = source.getLevel();
        PortableStructurePrinterSettings.setPortableStructureContainerCraftingEnabled(level, enabled);
        source.sendSuccess(() -> Component.translatable(
                enabled
                        ? "message.create_aeronautics_toolgun.command.portable_container_crafting.on"
                        : "message.create_aeronautics_toolgun.command.portable_container_crafting.off"
        ), true);
        return 1;
    }

    private static int printBlueprint(CommandSourceStack source, String rawName, BlockPos pos) throws IOException {
        ServerLevel level = source.getLevel();
        String safeName = BlueprintFileRepository.normalizeName(rawName);
        if (safeName.isBlank()) {
            throw new IOException("empty name");
        }

        Path file = BlueprintFileRepository.clientDirectory(FMLPaths.GAMEDIR.get())
                .resolve(safeName + BlueprintFileRepository.FILE_EXTENSION);
        if (Files.exists(file)) {
            ServerPlayer player = source.getEntity() instanceof ServerPlayer serverPlayer
                    ? serverPlayer
                    : null;
            PortableStructurePrinterPlacement.placeBlueprint(
                    level,
                    pos,
                    player,
                    safeName,
                    Files.readAllBytes(file),
                    player == null ? null : player.getUUID(),
                    null
            );
            source.sendSuccess(() -> Component.translatable(
                    "message.create_aeronautics_toolgun.command.print_blueprint.success",
                    safeName,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ()
            ), true);
            return 1;
        }

        Path createFile = FMLPaths.GAMEDIR.get().resolve("schematics").resolve(safeName + CreatePhysicalBlueprintService.FILE_EXTENSION);
        if (!Files.exists(createFile)) {
            throw new IOException("file not found");
        }
        byte[] bytes = Files.readAllBytes(createFile);
        if (!CreatePhysicalBlueprintService.hasCreatePhysicalLayout(bytes)) {
            throw new IOException("unsupported schematic");
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            throw new IOException("Create physical schematic printing requires a player command source");
        }
        PortableStructurePrinterPlacement.placeBlueprint(
                level,
                pos,
                player,
                safeName,
                bytes,
                player.getUUID(),
                null
        );
        source.sendSuccess(() -> Component.translatable(
                "message.create_aeronautics_toolgun.command.print_blueprint.success",
                safeName,
                pos.getX(),
                pos.getY(),
                pos.getZ()
        ), true);
        return 1;
    }

    private static int tryPrintBlueprint(CommandSourceStack source, String rawName, BlockPos pos) {
        try {
            return printBlueprint(source, rawName, pos);
        } catch (IOException exception) {
            ModConstants.LOGGER.error(
                    "Administrative blueprint print command failed for '{}' at {}",
                    rawName,
                    pos,
                    exception
            );
            source.sendFailure(Component.translatable(
                    "message.create_aeronautics_toolgun.printer_print_failed",
                    FailureMessages.describe(exception, "blueprint print command failed")
            ));
            return 0;
        }
    }
}
