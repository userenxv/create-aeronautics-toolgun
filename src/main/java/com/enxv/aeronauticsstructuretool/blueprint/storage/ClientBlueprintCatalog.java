package com.enxv.aeronauticsstructuretool.blueprint.storage;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.BlueprintListEntry;
import com.enxv.aeronauticsstructuretool.BlueprintSourceType;
import com.enxv.aeronauticsstructuretool.blueprint.placement.CreatePhysicalBlueprintService;
import com.enxv.aeronauticsstructuretool.blueprint.importer.vmod.VModNativeBlueprintEncoder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ClientBlueprintCatalog {
    private static final String VMOD_EXTENSION = ".vschem";

    private ClientBlueprintCatalog() {
    }

    public static List<BlueprintListEntry> listEntries() {
        Path gameDirectory = FMLPaths.GAMEDIR.get();
        List<BlueprintListEntry> entries = new ArrayList<>();
        listNativeEntries(gameDirectory, entries);
        listCreatePhysicalEntries(gameDirectory, entries);
        listVModEntries(gameDirectory, entries);
        entries.sort(Comparator
                .comparing((BlueprintListEntry entry) -> entry.sourceType() != BlueprintSourceType.NATIVE)
                .thenComparing(BlueprintListEntry::displayName, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    public static Path nativeDirectory() {
        return BlueprintFileRepository.clientDirectory(FMLPaths.GAMEDIR.get());
    }

    public static BlueprintListEntry find(String selectionKey) {
        if (selectionKey == null || selectionKey.isBlank()) {
            return null;
        }
        for (BlueprintListEntry entry : listEntries()) {
            if (entry.selectionKey().equals(selectionKey)) {
                return entry;
            }
        }
        return null;
    }

    public static byte[] read(BlueprintListEntry entry) throws IOException {
        if (entry == null) {
            throw new IOException("no file selected");
        }
        byte[] rawBytes = Files.readAllBytes(entry.path());
        if (entry.sourceType() == BlueprintSourceType.NATIVE
                || entry.sourceType() == BlueprintSourceType.CREATE_PHYSICAL) {
            return rawBytes;
        }
        return VModNativeBlueprintEncoder.importToNative(entry.fileName(), rawBytes);
    }

    private static void listNativeEntries(Path gameDirectory, List<BlueprintListEntry> entries) {
        Path directory = BlueprintFileRepository.clientDirectory(gameDirectory);
        try {
            Files.createDirectories(directory);
            try (var stream = Files.list(directory)) {
                stream.filter(path -> path.getFileName().toString().endsWith(BlueprintFileRepository.FILE_EXTENSION))
                        .forEach(path -> {
                            String fileName = path.getFileName().toString();
                            String displayName = BlueprintFileRepository.displayName(fileName);
                            entries.add(new BlueprintListEntry(
                                    "native:" + fileName.toLowerCase(Locale.ROOT),
                                    displayName,
                                    displayName,
                                    path,
                                    BlueprintSourceType.NATIVE
                            ));
                        });
            }
        } catch (IOException exception) {
            AeronauticsStructureToolMod.LOGGER.warn("Failed to list native blueprints in {}", directory, exception);
        }
    }

    private static void listVModEntries(Path gameDirectory, List<BlueprintListEntry> entries) {
        Path directory = BlueprintFileRepository.clientDirectory(gameDirectory);
        try {
            Files.createDirectories(directory);
            try (var stream = Files.list(directory)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(VMOD_EXTENSION))
                        .forEach(path -> {
                            String fileName = path.getFileName().toString();
                            String baseName = fileName.substring(0, fileName.length() - VMOD_EXTENSION.length());
                            String nativeName = BlueprintFileRepository.normalizeName(baseName);
                            if (nativeName.isBlank()) {
                                nativeName = "vmod_import";
                            }
                            entries.add(new BlueprintListEntry(
                                    "vmod:" + fileName.toLowerCase(Locale.ROOT),
                                    "[VMod] " + baseName,
                                    nativeName,
                                    path,
                                    BlueprintSourceType.VMOD
                            ));
                        });
            }
        } catch (IOException exception) {
            AeronauticsStructureToolMod.LOGGER.warn("Failed to list VMod blueprints in {}", directory, exception);
        }
    }

    private static void listCreatePhysicalEntries(Path gameDirectory, List<BlueprintListEntry> entries) {
        Path directory = gameDirectory.resolve("schematics");
        try {
            Files.createDirectories(directory);
            try (var stream = Files.list(directory)) {
                stream.filter(CreatePhysicalBlueprintService::isCreatePhysicalPath)
                        .forEach(path -> {
                            String fileName = path.getFileName().toString();
                            String baseName = fileName.substring(
                                    0,
                                    fileName.length() - CreatePhysicalBlueprintService.FILE_EXTENSION.length()
                            );
                            String nativeName = BlueprintFileRepository.normalizeName(baseName);
                            if (nativeName.isBlank()) {
                                nativeName = "create_physical";
                            }
                            entries.add(new BlueprintListEntry(
                                    "create_physical:" + fileName.toLowerCase(Locale.ROOT),
                                    baseName,
                                    nativeName,
                                    path,
                                    BlueprintSourceType.CREATE_PHYSICAL
                            ));
                        });
            }
        } catch (IOException exception) {
            AeronauticsStructureToolMod.LOGGER.warn("Failed to list Create physical schematics in {}", directory, exception);
        }
    }
}
