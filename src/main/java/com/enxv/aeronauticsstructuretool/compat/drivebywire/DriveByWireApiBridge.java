package com.enxv.aeronauticsstructuretool.compat.drivebywire;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class DriveByWireApiBridge {
    private static final String MANAGER_CLASS = "edn.stratodonut.drivebywire.wire.WireNetworkManager";
    private static final String BACKUP_BLOCK_ENTITY_CLASS =
            "edn.stratodonut.drivebywire.blocks.WireNetworkBackupBlockEntity";
    private static final String FULL_SYNC_PACKET_CLASS =
            "edn.stratodonut.drivebywire.network.WireNetworkFullSyncPacket";
    private static Method fullSyncSendToMethod;
    private static Method computeWorldSignalMethod;
    private static Method trySetSignalAtMethod;

    private DriveByWireApiBridge() {
    }

    static boolean isInstalled() {
        return ModList.get().isLoaded(DriveByWireSnapshotNbt.MOD_ID);
    }

    static CompoundTag captureSnapshot(
            ServerLevel level,
            BlockPos anchorPos,
            Direction facing,
            SubLevelSchematicSerializationContext context
    ) throws IOException {
        if (!isInstalled()) {
            return new CompoundTag();
        }
        try {
            Class<?> managerClass = Class.forName(MANAGER_CLASS);
            Object manager = managerClass.getMethod("get", net.minecraft.world.level.Level.class)
                    .invoke(null, level);
            Method capture = managerClass.getMethod(
                    "createBackupSnapshot",
                    net.minecraft.world.level.Level.class,
                    BlockPos.class,
                    Direction.class
            );
            Object backupSnapshot;
            try {
                SubLevelSchematicSerializationContext.setCurrentContext(context);
                backupSnapshot = capture.invoke(manager, level, anchorPos, facing);
            } finally {
                SubLevelSchematicSerializationContext.setCurrentContext(null);
            }
            if (backupSnapshot == null) {
                throw new IOException("DriveByWire returned no backup snapshot");
            }
            Object data = backupSnapshot.getClass().getMethod("data").invoke(backupSnapshot);
            if (!(data instanceof CompoundTag snapshot)) {
                throw new IOException("DriveByWire backup snapshot returned invalid NBT data");
            }
            return snapshot.copy();
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw apiFailure("capture a blueprint snapshot", exception);
        }
    }

    static CompoundTag transformForPlacement(
            CompoundTag snapshot,
            BlockPos backupBlockPos,
            SubLevelSchematicSerializationContext context
    ) throws IOException {
        requireInstalled("transform a saved snapshot");
        try {
            Class<?> managerClass = Class.forName(MANAGER_CLASS);
            Method transform = managerClass.getMethod(
                    "transformBackupSnapshotForPlacement",
                    CompoundTag.class,
                    BlockPos.class,
                    SubLevelSchematicSerializationContext.class
            );
            Object result = transform.invoke(null, snapshot, backupBlockPos, context);
            if (!(result instanceof CompoundTag transformed)) {
                throw new IOException("DriveByWire placement transform returned invalid NBT data");
            }
            return transformed.copy();
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw apiFailure("transform a saved snapshot", exception);
        }
    }

    static RestoreResult restoreSnapshot(
            ServerLevel level,
            BlockPos backupBlockPos,
            Direction facing,
            CompoundTag snapshot
    ) throws IOException {
        requireInstalled("restore a saved snapshot");
        try {
            Class<?> managerClass = Class.forName(MANAGER_CLASS);
            Object manager = managerClass.getMethod("get", net.minecraft.world.level.Level.class)
                    .invoke(null, level);
            Method restore = managerClass.getMethod(
                    "restoreBackupSnapshot",
                    net.minecraft.world.level.Level.class,
                    BlockPos.class,
                    Direction.class,
                    CompoundTag.class
            );
            Object rawResult = restore.invoke(manager, level, backupBlockPos, facing, snapshot);
            if (rawResult == null) {
                throw new IOException("DriveByWire restore returned no result");
            }

            int restored = readRequiredInt(rawResult, "restoredConnections");
            int existing = readRequiredInt(rawResult, "existingConnections");
            int deferred = readRequiredInt(rawResult, "deferredConnections");
            int skipped = readRequiredInt(rawResult, "skippedConnections");
            Method expectedMethod = findNoArgMethod(rawResult.getClass(), "expectedConnections");
            Method attemptedMethod = findNoArgMethod(rawResult.getClass(), "attempted");
            int expected = expectedMethod == null
                    ? DriveByWireSnapshotNbt.connectionCount(snapshot)
                    : readInt(rawResult, expectedMethod, "expectedConnections");
            boolean attempted = attemptedMethod == null
                    || readBoolean(rawResult, attemptedMethod, "attempted");
            return new RestoreResult(restored, existing, deferred, skipped, expected, attempted);
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw apiFailure("restore a saved snapshot", exception);
        }
    }

    static void clearPendingBackup(ServerLevel level, BlockPos backupBlockPos) throws IOException {
        BlockEntity blockEntity = level.getBlockEntity(backupBlockPos);
        if (blockEntity == null) {
            return;
        }
        String blockEntityId = String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()));
        if (!DriveByWireSnapshotNbt.BACKUP_BLOCK_ID.equals(blockEntityId)) {
            return;
        }
        requireInstalled("clear restored backup state");
        try {
            Class<?> backupClass = Class.forName(BACKUP_BLOCK_ENTITY_CLASS);
            if (!backupClass.isInstance(blockEntity)) {
                throw new IOException("DriveByWire backup block entity has an unexpected runtime type");
            }
            Field pendingBackupData = requiredField(backupClass, "pendingBackupData");
            Field needsRestore = requiredField(backupClass, "needsRestore");
            Field restoreRetryCooldown = requiredField(backupClass, "restoreRetryCooldown");
            Field restoreAttempts = requiredField(backupClass, "restoreAttempts");
            pendingBackupData.set(blockEntity, null);
            needsRestore.setBoolean(blockEntity, false);
            restoreRetryCooldown.setInt(blockEntity, 0);
            restoreAttempts.setInt(blockEntity, 0);
            blockEntity.setChanged();
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw apiFailure("clear restored backup state", exception);
        }
    }

    static void sendFullSync(ServerPlayer player) throws IOException {
        requireInstalled("send a full network sync");
        try {
            if (fullSyncSendToMethod == null) {
                Class<?> packetClass = Class.forName(FULL_SYNC_PACKET_CLASS);
                fullSyncSendToMethod = packetClass.getMethod("sendTo", ServerPlayer.class);
            }
            fullSyncSendToMethod.invoke(null, player);
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw apiFailure("send a full network sync", exception);
        }
    }

    static int computeWorldSignal(ServerLevel level, BlockPos sourcePos) throws IOException {
        requireInstalled("compute a world-channel signal");
        try {
            if (computeWorldSignalMethod == null) {
                Class<?> managerClass = Class.forName(MANAGER_CLASS);
                computeWorldSignalMethod = managerClass.getMethod(
                        "computeWorldSignal",
                        net.minecraft.world.level.Level.class,
                        BlockPos.class
                );
            }
            Object result = computeWorldSignalMethod.invoke(null, level, sourcePos);
            if (result instanceof Integer signal) {
                return signal;
            }
            throw new IOException("DriveByWire world-signal API returned a non-integer value");
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw apiFailure("compute a world-channel signal", exception);
        }
    }

    static void pushWorldSignal(ServerLevel level, BlockPos sourcePos, int signal) throws IOException {
        requireInstalled("publish a world-channel signal");
        try {
            if (trySetSignalAtMethod == null) {
                Class<?> managerClass = Class.forName(MANAGER_CLASS);
                trySetSignalAtMethod = managerClass.getMethod(
                        "trySetSignalAt",
                        net.minecraft.world.level.Level.class,
                        BlockPos.class,
                        String.class,
                        int.class
                );
            }
            trySetSignalAtMethod.invoke(null, level, sourcePos, "world", signal);
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw apiFailure("publish a world-channel signal", exception);
        }
    }

    private static void requireInstalled(String action) throws IOException {
        if (!isInstalled()) {
            throw new IOException("DriveByWire is required to " + action + " but is not loaded");
        }
    }

    private static int readRequiredInt(Object target, String accessor) throws ReflectiveOperationException, IOException {
        Method method = target.getClass().getMethod(accessor);
        return readInt(target, method, accessor);
    }

    private static int readInt(Object target, Method method, String accessor)
            throws ReflectiveOperationException, IOException {
        Object value = method.invoke(target);
        if (value instanceof Integer integer) {
            return integer;
        }
        throw new IOException("DriveByWire restore result accessor '" + accessor + "' returned a non-integer value");
    }

    private static boolean readBoolean(Object target, Method method, String accessor)
            throws ReflectiveOperationException, IOException {
        Object value = method.invoke(target);
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new IOException("DriveByWire restore result accessor '" + accessor + "' returned a non-boolean value");
    }

    private static Method findNoArgMethod(Class<?> type, String name) {
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    private static Field requiredField(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static IOException apiFailure(String action, Throwable failure) {
        Throwable cause = failure instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause()
                : failure;
        AeronauticsStructureToolMod.LOGGER.error("DriveByWire API failed to {}", action, cause);
        return new IOException("DriveByWire API failed to " + action, cause);
    }

    record RestoreResult(
            int restoredConnections,
            int existingConnections,
            int deferredConnections,
            int skippedConnections,
            int expectedConnections,
            boolean attempted
    ) {
    }
}
