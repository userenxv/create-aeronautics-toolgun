package com.enxv.aeronauticsstructuretool.blueprint.storage;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class BlueprintFileRepository {
    public static final String FILE_EXTENSION = ".excraft";
    private static final String CLIENT_DIRECTORY = "enxv_aeronautics_structures";
    private static final String SERVER_DIRECTORY = "enxv_aeronautics_server_structures";

    private BlueprintFileRepository() {
    }

    public static Path clientDirectory(Path gameDirectory) {
        return gameDirectory.resolve(CLIENT_DIRECTORY);
    }

    public static Path serverDirectory(Path serverRoot, UUID playerId) {
        return serverRoot.resolve(SERVER_DIRECTORY).resolve(playerId.toString());
    }

    public static String normalizeName(String rawName) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < rawName.length(); i++) {
            char character = rawName.charAt(i);
            if (Character.isLetterOrDigit(character)
                    || character == '_'
                    || character == '-'
                    || character == ' ') {
                builder.append(character);
            }
        }
        return builder.toString().trim();
    }

    public static String displayName(String fileName) {
        return fileName.endsWith(FILE_EXTENSION)
                ? fileName.substring(0, fileName.length() - FILE_EXTENSION.length())
                : fileName;
    }

    public static byte[] read(Path directory, String rawName) throws IOException {
        Path file = resolveExistingFile(directory, rawName);
        return Files.readAllBytes(file);
    }

    public static void write(Path directory, String rawName, byte[] fileContents) throws IOException {
        String safeName = requireName(rawName);
        Files.createDirectories(directory);
        Path file = directory.resolve(safeName + FILE_EXTENSION);
        try (OutputStream output = Files.newOutputStream(file)) {
            output.write(fileContents);
        }
    }

    public static Path resolveExistingFile(Path directory, String rawName) throws IOException {
        Path file = directory.resolve(requireName(rawName) + FILE_EXTENSION);
        if (!Files.exists(file)) {
            throw new IOException("file not found");
        }
        return file;
    }

    private static String requireName(String rawName) throws IOException {
        String safeName = normalizeName(rawName);
        if (safeName.isEmpty()) {
            throw new IOException("empty name");
        }
        return safeName;
    }
}
