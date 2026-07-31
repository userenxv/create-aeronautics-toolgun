package com.enxv.aeronauticsstructuretool.blueprint.codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class BlueprintArchiveCodec {
    private static final int MAX_ARCHIVE_BYTES = 64 * 1024 * 1024;
    private static final long MAX_DECODED_NBT_BYTES = 256L * 1024L * 1024L;

    private BlueprintArchiveCodec() {
    }

    public static byte[] encode(CompoundTag root) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(root, output);
            byte[] encoded = output.toByteArray();
            validateArchiveBytes(encoded);
            return encoded;
        }
    }

    public static CompoundTag decode(byte[] fileContents) throws IOException {
        validateArchiveBytes(fileContents);
        try (InputStream input = new ByteArrayInputStream(fileContents)) {
            CompoundTag root = NbtIo.readCompressed(input, newAccounter());
            if (root == null) {
                throw new IOException("compressed blueprint NBT is empty");
            }
            return root;
        } catch (RuntimeException exception) {
            throw new IOException("compressed blueprint NBT exceeds its decode budget or is malformed", exception);
        }
    }

    public static CompoundTag decodeCompressedOrRaw(byte[] fileContents) throws IOException {
        validateArchiveBytes(fileContents);

        IOException compressedFailure;
        try (InputStream input = new ByteArrayInputStream(fileContents)) {
            CompoundTag compressed = NbtIo.readCompressed(input, newAccounter());
            if (compressed != null) {
                return compressed;
            }
            compressedFailure = new IOException("compressed NBT is empty");
        } catch (IOException | RuntimeException exception) {
            compressedFailure = new IOException("invalid compressed blueprint NBT", exception);
        }

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(fileContents))) {
            CompoundTag raw = NbtIo.read(input, newAccounter());
            if (raw == null) {
                throw new IOException("raw NBT is empty");
            }
            return raw;
        } catch (IOException | RuntimeException exception) {
            IOException failure = new IOException("invalid raw blueprint NBT", exception);
            failure.addSuppressed(compressedFailure);
            throw failure;
        }
    }

    private static NbtAccounter newAccounter() {
        return NbtAccounter.create(MAX_DECODED_NBT_BYTES);
    }

    private static void validateArchiveBytes(byte[] fileContents) throws IOException {
        if (fileContents == null || fileContents.length == 0) {
            throw new IOException("blueprint archive is empty");
        }
        if (fileContents.length > MAX_ARCHIVE_BYTES) {
            throw new IOException(
                    "blueprint archive exceeds " + MAX_ARCHIVE_BYTES + " bytes"
            );
        }
    }
}
