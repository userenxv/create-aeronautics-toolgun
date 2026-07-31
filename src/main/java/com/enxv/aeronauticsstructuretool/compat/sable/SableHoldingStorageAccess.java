package com.enxv.aeronauticsstructuretool.compat.sable;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import net.minecraft.world.level.ChunkPos;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SableHoldingStorageAccess {
    private SableHoldingStorageAccess() {
    }

    public static SubLevelHoldingChunk getOrLoadHoldingChunk(
            ServerSubLevelContainer container,
            ChunkPos chunkPos,
            boolean create
    ) throws ReflectiveOperationException {
        Object holdingChunkMap = container.getHoldingChunkMap();
        Method method = findDeclaredMethod(
                holdingChunkMap.getClass(),
                "getOrLoadHoldingChunk",
                ChunkPos.class,
                boolean.class
        );
        return (SubLevelHoldingChunk) method.invoke(holdingChunkMap, chunkPos, create);
    }

    public static Collection<HoldingSubLevel> holdingSubLevels(
            ServerSubLevelContainer container
    ) throws ReflectiveOperationException {
        return List.copyOf(allHoldingSubLevels(container).values());
    }

    public static void registerHoldingSubLevel(
            ServerSubLevelContainer container,
            HoldingSubLevel holdingSubLevel
    ) throws ReflectiveOperationException {
        allHoldingSubLevels(container).put(holdingSubLevel.data().uuid(), holdingSubLevel);
    }

    public static void unregisterHoldingSubLevel(
            ServerSubLevelContainer container,
            UUID subLevelId
    ) throws ReflectiveOperationException {
        allHoldingSubLevels(container).remove(subLevelId);
    }

    public static void removeLoadedHoldingSubLevel(
            SubLevelHoldingChunk holdingChunk,
            UUID subLevelId
    ) throws ReflectiveOperationException {
        Field field = findDeclaredField(holdingChunk.getClass(), "loadedHoldingSubLevels");
        Object value = field.get(holdingChunk);
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Sable loadedHoldingSubLevels has an unexpected type");
        }
        map.remove(subLevelId);
    }

    public static void markHoldingChunkDirty(
            ServerSubLevelContainer container,
            ChunkPos chunkPos
    ) throws ReflectiveOperationException {
        Object holdingChunkMap = container.getHoldingChunkMap();
        Method method = findDeclaredMethod(holdingChunkMap.getClass(), "setDirty", ChunkPos.class);
        method.invoke(holdingChunkMap, chunkPos);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, HoldingSubLevel> allHoldingSubLevels(
            ServerSubLevelContainer container
    ) throws ReflectiveOperationException {
        Object holdingChunkMap = container.getHoldingChunkMap();
        Field field = findDeclaredField(holdingChunkMap.getClass(), "allHoldingSubLevels");
        Object value = field.get(holdingChunkMap);
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Sable allHoldingSubLevels has an unexpected type");
        }
        return (Map<UUID, HoldingSubLevel>) map;
    }

    private static Method findDeclaredMethod(Class<?> type, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException notDeclaredOnCurrentType) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }

    private static Field findDeclaredField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException notDeclaredOnCurrentType) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(type.getName() + "#" + name);
    }
}
