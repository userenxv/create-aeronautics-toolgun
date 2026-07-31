package com.enxv.aeronauticsstructuretool.compat.hardblock;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class HardBlockMissileCleanupManager {
    private static final String GUIDED_MISSILE_CLASS_NAME = "com.example.hardblock.munitions.GuidedMissileEntity";
    private static final int EMBEDDED_SUBLEVEL_MISSILE_LIFETIME_TICKS = 20;
    private static final int RECENT_SUBLEVEL_WINDOW_TICKS = 2;

    private final Map<String, Map<UUID, TrackedMissile>> trackedMissilesByDimension = new HashMap<>();
    private Class<?> guidedMissileClass;
    private Method isInGroundMethod;
    private Field renderDirectionFrozenField;
    private Field pendingDetonationTicksField;
    private boolean reflectionResolved;
    private boolean reflectionFailed;
    private boolean reflectionWarningLogged;

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Entity entity = event.getEntity();
        if (!isHardBlockGuidedMissile(entity)) {
            return;
        }
        trackedMissiles(level).put(entity.getUUID(), new TrackedMissile(entity, null, RECENT_SUBLEVEL_WINDOW_TICKS + 1, false, null, 0));
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        Map<UUID, TrackedMissile> trackedMissiles = trackedMissilesByDimension.get(dimensionKey(level));
        if (trackedMissiles == null || trackedMissiles.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, TrackedMissile>> iterator = trackedMissiles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TrackedMissile> entry = iterator.next();
            TrackedMissile tracked = entry.getValue();
            Entity entity = tracked.entity();
            if (!isValidTrackedMissile(level, entity)) {
                iterator.remove();
                continue;
            }

            SubLevel containing = Sable.HELPER.getContaining(level, entity.blockPosition());
            tracked = updateRecentSubLevel(tracked, containing);

            boolean embedded = isEmbeddedMissile(entity);
            if (!tracked.embeddedCleanupActive()) {
                if (embedded && (containing != null || tracked.recentSubLevelId() != null)) {
                    UUID attachedSubLevelId = containing != null ? containing.getUniqueId() : tracked.recentSubLevelId();
                    tracked = tracked.startEmbeddedCleanup(attachedSubLevelId);
                }
                entry.setValue(tracked);
                continue;
            }

            if (!embedded) {
                entry.setValue(tracked.stopEmbeddedCleanup());
                continue;
            }

            UUID containingId = containing != null ? containing.getUniqueId() : null;
            if (containingId == null || !containingId.equals(tracked.embeddedSubLevelId()) || tracked.embeddedTicks() >= EMBEDDED_SUBLEVEL_MISSILE_LIFETIME_TICKS) {
                entity.discard();
                iterator.remove();
                continue;
            }

            entry.setValue(tracked.advanceEmbeddedCleanup());
        }

        if (trackedMissiles.isEmpty()) {
            trackedMissilesByDimension.remove(dimensionKey(level));
        }
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            trackedMissilesByDimension.remove(dimensionKey(level));
        }
    }

    private Map<UUID, TrackedMissile> trackedMissiles(ServerLevel level) {
        return trackedMissilesByDimension.computeIfAbsent(dimensionKey(level), ignored -> new HashMap<>());
    }

    private String dimensionKey(ServerLevel level) {
        return level.dimension().location().toString();
    }

    private TrackedMissile updateRecentSubLevel(TrackedMissile tracked, SubLevel containing) {
        if (containing != null) {
            return tracked.withRecentSubLevel(containing.getUniqueId(), 0);
        }
        if (tracked.recentSubLevelId() == null) {
            return tracked;
        }
        int aged = tracked.recentSubLevelAgeTicks() + 1;
        if (aged > RECENT_SUBLEVEL_WINDOW_TICKS) {
            return tracked.withRecentSubLevel(null, aged);
        }
        return tracked.withRecentSubLevel(tracked.recentSubLevelId(), aged);
    }

    private boolean isValidTrackedMissile(ServerLevel level, Entity entity) {
        return entity != null
                && entity.level() == level
                && entity.isAlive()
                && !entity.isRemoved()
                && isHardBlockGuidedMissile(entity);
    }

    private boolean isHardBlockGuidedMissile(Entity entity) {
        if (entity == null || !resolveReflection()) {
            return false;
        }
        return guidedMissileClass.isInstance(entity);
    }

    private boolean isEmbeddedMissile(Entity entity) {
        if (entity == null || !resolveReflection()) {
            return false;
        }

        try {
            if (isInGroundMethod != null) {
                Object result = isInGroundMethod.invoke(entity);
                if (result instanceof Boolean embedded && embedded) {
                    return true;
                }
            }
            if (renderDirectionFrozenField != null) {
                Object result = renderDirectionFrozenField.get(entity);
                if (result instanceof Boolean frozen && frozen) {
                    return true;
                }
            }
            if (pendingDetonationTicksField != null) {
                return pendingDetonationTicksField.getInt(entity) > 0;
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            markReflectionFailed(exception);
        }
        return false;
    }

    private boolean resolveReflection() {
        if (reflectionFailed) {
            return false;
        }
        if (reflectionResolved) {
            return true;
        }

        try {
            guidedMissileClass = Class.forName(GUIDED_MISSILE_CLASS_NAME);
            isInGroundMethod = findOptionalMethod(guidedMissileClass, "isInGround");
            renderDirectionFrozenField = findOptionalField(guidedMissileClass, "renderDirectionFrozen");
            pendingDetonationTicksField = findOptionalField(guidedMissileClass, "pendingDetonationTicks");
            if (isInGroundMethod == null
                    && renderDirectionFrozenField == null
                    && pendingDetonationTicksField == null) {
                throw new NoSuchFieldException(
                        "HardBlock guided missile exposes no supported impact-state member"
                );
            }
            reflectionResolved = true;
            return true;
        } catch (ClassNotFoundException exception) {
            reflectionFailed = true;
            return false;
        } catch (ReflectiveOperationException | LinkageError exception) {
            markReflectionFailed(exception);
            return false;
        }
    }

    private void markReflectionFailed(Throwable exception) {
        reflectionFailed = true;
        if (!reflectionWarningLogged) {
            reflectionWarningLogged = true;
            AeronauticsStructureToolMod.LOGGER.warn("HardBlock missile cleanup manager could not resolve guided missile state reflectively", exception);
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
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
        throw new NoSuchMethodException(name);
    }

    private static Method findOptionalMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return findMethod(type, name, parameterTypes);
        } catch (NoSuchMethodException unavailableInThisVersion) {
            return null;
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
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
        throw new NoSuchFieldException(name);
    }

    private static Field findOptionalField(Class<?> type, String name) {
        try {
            return findField(type, name);
        } catch (NoSuchFieldException unavailableInThisVersion) {
            return null;
        }
    }

    private record TrackedMissile(
            Entity entity,
            UUID recentSubLevelId,
            int recentSubLevelAgeTicks,
            boolean embeddedCleanupActive,
            UUID embeddedSubLevelId,
            int embeddedTicks
    ) {
        private TrackedMissile withRecentSubLevel(UUID recentSubLevelId, int recentSubLevelAgeTicks) {
            return new TrackedMissile(entity, recentSubLevelId, recentSubLevelAgeTicks, embeddedCleanupActive, embeddedSubLevelId, embeddedTicks);
        }

        private TrackedMissile startEmbeddedCleanup(UUID embeddedSubLevelId) {
            return new TrackedMissile(entity, recentSubLevelId, recentSubLevelAgeTicks, true, embeddedSubLevelId, 0);
        }

        private TrackedMissile stopEmbeddedCleanup() {
            return new TrackedMissile(entity, recentSubLevelId, recentSubLevelAgeTicks, false, null, 0);
        }

        private TrackedMissile advanceEmbeddedCleanup() {
            return new TrackedMissile(entity, recentSubLevelId, recentSubLevelAgeTicks, true, embeddedSubLevelId, embeddedTicks + 1);
        }
    }
}
