package com.enxv.aeronauticsstructuretool.vehicle.container;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Bounded, ordered disk writes. Never performs filesystem operations on the caller's thread. */
public final class ContainerBackupWriter implements AutoCloseable {
    private static final long MAX_PENDING_BYTES = 64L * 1024 * 1024;
    private final Path directory;
    private final ThreadPoolExecutor executor;
    private long pendingBytes;

    public ContainerBackupWriter(Path directory) {
        this.directory = directory;
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(16), task -> {
                    Thread thread = new Thread(task, "toolgun-container-backups");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    /** The caller transfers ownership of the freshly encoded archive and must not mutate it. */
    public synchronized void submit(UUID vehicleId, byte[] archive) throws IOException {
        if (archive.length == 0) {
            throw new IOException("cannot back up an empty vehicle archive");
        }
        if (archive.length > MAX_PENDING_BYTES - pendingBytes) {
            throw new QueueFullException();
        }
        pendingBytes += archive.length;
        try {
            executor.execute(() -> {
                try {
                    write(vehicleId, archive);
                } catch (IOException | RuntimeException failure) {
                    AeronauticsStructureToolMod.LOGGER.error("Failed to back up captured vehicle {} in {}", vehicleId, directory, failure);
                } finally {
                    release(archive.length);
                }
            });
        } catch (RejectedExecutionException failure) {
            pendingBytes -= archive.length;
            throw new QueueFullException();
        }
    }

    private synchronized void release(int bytes) {
        pendingBytes -= bytes;
    }

    private Path write(UUID vehicleId, byte[] archive) throws IOException {
        Files.createDirectories(directory);
        Path target = directory.resolve(vehicleId + ".excraft");
        Path temporary = Files.createTempFile(directory, vehicleId + "-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer data = ByteBuffer.wrap(archive);
                while (data.hasRemaining()) {
                    channel.write(data);
                }
                channel.force(true);
            }
            // A failed write must leave the previous complete backup intact.
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException | RuntimeException failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    @Override
    public void close() throws IOException {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IOException("container backups did not finish within the shutdown deadline");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while finishing container backups", interrupted);
        }
    }

    public static final class QueueFullException extends IOException {
        private QueueFullException() {
            super("container backup queue is full or closed; retry capture later");
        }
    }
}
