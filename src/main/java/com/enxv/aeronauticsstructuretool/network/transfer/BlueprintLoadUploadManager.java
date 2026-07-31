package com.enxv.aeronauticsstructuretool.network.transfer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class BlueprintLoadUploadManager {
    public static final int MAX_BLUEPRINT_BYTES = 128 * 1024 * 1024;
    private static final int MAX_IDLE_TICKS = 20 * 30;
    private static final int SHA_256_BYTES = 32;

    private final Map<UUID, PendingUpload> pendingByPlayer = new HashMap<>();

    public void begin(ServerPlayer player, BlueprintLoadUploadRequest request) throws IOException {
        if (request.totalBytes() <= 0 || request.totalBytes() > MAX_BLUEPRINT_BYTES) {
            throw new IOException("blueprint size must be between 1 byte and "
                    + (MAX_BLUEPRINT_BYTES / (1024 * 1024)) + " MiB");
        }
        if (request.sha256().length != SHA_256_BYTES) {
            throw new IOException("blueprint upload has an invalid SHA-256 digest");
        }
        if (request.fileName().isBlank() || request.fileName().length() > 255) {
            throw new IOException("blueprint file name is invalid");
        }
        pendingByPlayer.put(player.getUUID(), new PendingUpload(
                player.getServer(),
                request,
                new ByteArrayOutputStream(Math.min(request.totalBytes(), 1024 * 1024))
        ));
    }

    public void append(ServerPlayer player, UUID transferId, int offset, byte[] contents) throws IOException {
        PendingUpload pending = require(player, transferId);
        if (contents.length == 0 || contents.length > 24 * 1024) {
            cancel(player);
            throw new IOException("blueprint upload chunk size is invalid");
        }
        if (offset != pending.contents.size()) {
            cancel(player);
            throw new IOException("blueprint upload chunks arrived out of order");
        }
        if ((long) pending.contents.size() + contents.length > pending.request.totalBytes()) {
            cancel(player);
            throw new IOException("blueprint upload exceeds its declared size");
        }
        pending.contents.writeBytes(contents);
        pending.idleTicks = 0;
    }

    public CompletedUpload complete(ServerPlayer player, UUID transferId) throws IOException {
        PendingUpload pending = require(player, transferId);
        pendingByPlayer.remove(player.getUUID());
        byte[] contents = pending.contents.toByteArray();
        if (contents.length != pending.request.totalBytes()) {
            throw new IOException("blueprint upload ended at " + contents.length
                    + " of " + pending.request.totalBytes() + " bytes");
        }
        if (!MessageDigest.isEqual(pending.request.sha256(), sha256(contents))) {
            throw new IOException("blueprint upload failed SHA-256 verification");
        }
        return new CompletedUpload(pending.request, contents);
    }

    public void cancel(ServerPlayer player) {
        pendingByPlayer.remove(player.getUUID());
    }

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        Iterator<Map.Entry<UUID, PendingUpload>> iterator = pendingByPlayer.entrySet().iterator();
        while (iterator.hasNext()) {
            PendingUpload pending = iterator.next().getValue();
            if (pending.server != event.getServer()) {
                continue;
            }
            if (++pending.idleTicks > MAX_IDLE_TICKS) {
                iterator.remove();
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        pendingByPlayer.remove(event.getEntity().getUUID());
    }

    private PendingUpload require(ServerPlayer player, UUID transferId) throws IOException {
        PendingUpload pending = pendingByPlayer.get(player.getUUID());
        if (pending == null || !pending.request.transferId().equals(transferId)) {
            throw new IOException("blueprint upload did not start or expired");
        }
        if (pending.server != player.getServer()) {
            cancel(player);
            throw new IOException("blueprint upload belongs to another server session");
        }
        return pending;
    }

    private static byte[] sha256(byte[] contents) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(contents);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record CompletedUpload(BlueprintLoadUploadRequest request, byte[] contents) {
        public CompletedUpload {
            contents = Arrays.copyOf(contents, contents.length);
        }
    }

    private static final class PendingUpload {
        private final MinecraftServer server;
        private final BlueprintLoadUploadRequest request;
        private final ByteArrayOutputStream contents;
        private int idleTicks;

        private PendingUpload(
                MinecraftServer server,
                BlueprintLoadUploadRequest request,
                ByteArrayOutputStream contents
        ) {
            this.server = server;
            this.request = request;
            this.contents = contents;
        }
    }
}
