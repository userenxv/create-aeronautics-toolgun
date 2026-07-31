package com.enxv.aeronauticsstructuretool.compat.simulated;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Method;
import java.util.UUID;

public final class SimulatedPhysicsStaffLockBridge {
    private static final String HANDLER_CLASS =
            "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler";
    private static final Access ACCESS = resolveAccess();

    private SimulatedPhysicsStaffLockBridge() {
    }

    public static LockState lockState(ServerLevel level, ServerSubLevel subLevel) {
        if (ACCESS.availability() == Availability.NOT_INSTALLED) {
            return LockState.UNAVAILABLE;
        }
        if (!ACCESS.available()) {
            return LockState.FAILED;
        }
        Invocation invocation = invoke(level, ACCESS.isLocked(), subLevel);
        if (!invocation.successful() || !(invocation.value() instanceof Boolean locked)) {
            return LockState.FAILED;
        }
        return locked ? LockState.LOCKED : LockState.UNLOCKED;
    }

    public static boolean removeLock(ServerLevel level, ServerSubLevel subLevel) {
        return invoke(level, ACCESS.removeLock(), subLevel).successful();
    }

    public static boolean toggleLock(ServerLevel level, UUID subLevelId) {
        return invoke(level, ACCESS.toggleLock(), subLevelId).successful();
    }

    private static Invocation invoke(ServerLevel level, Method operation, Object argument) {
        if (!ACCESS.available() || operation == null) {
            return Invocation.failed();
        }
        try {
            Object handler = ACCESS.getHandler().invoke(null, level);
            if (handler == null) {
                AeronauticsStructureToolMod.LOGGER.warn(
                        "Simulated physics-staff lock handler was unavailable for {}",
                        level.dimension().location()
                );
                return Invocation.failed();
            }
            return Invocation.success(operation.invoke(handler, argument));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Failed to invoke Simulated physics-staff lock operation {}",
                    operation.getName(),
                    exception
            );
            return Invocation.failed();
        }
    }

    private static Access resolveAccess() {
        try {
            Class<?> handlerClass = Class.forName(HANDLER_CLASS);
            return new Access(
                    handlerClass.getMethod("get", ServerLevel.class),
                    requireOperation(handlerClass, "removeLock", ServerSubLevel.class),
                    requireOperation(handlerClass, "isLocked", ServerSubLevel.class),
                    requireOperation(handlerClass, "toggleLock", UUID.class)
            );
        } catch (ClassNotFoundException exception) {
            AeronauticsStructureToolMod.LOGGER.debug(
                    "Simulated physics-staff lock integration is not installed"
            );
            return Access.notInstalled();
        } catch (ReflectiveOperationException | LinkageError exception) {
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Simulated physics-staff lock API could not be initialized",
                    exception
            );
            return Access.broken();
        }
    }

    private static Method requireOperation(Class<?> handlerClass, String name, Class<?> argumentType)
            throws NoSuchMethodException {
        for (Method method : handlerClass.getMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(argumentType)) {
                return method;
            }
        }
        throw new NoSuchMethodException(
                handlerClass.getName() + '#' + name + '(' + argumentType.getName() + ')'
        );
    }

    private record Access(
            Method getHandler,
            Method removeLock,
            Method isLocked,
            Method toggleLock,
            Availability availability
    ) {
        private Access(Method getHandler, Method removeLock, Method isLocked, Method toggleLock) {
            this(getHandler, removeLock, isLocked, toggleLock, Availability.AVAILABLE);
        }

        static Access notInstalled() {
            return new Access(null, null, null, null, Availability.NOT_INSTALLED);
        }

        static Access broken() {
            return new Access(null, null, null, null, Availability.BROKEN);
        }

        boolean available() {
            return this.availability == Availability.AVAILABLE && this.getHandler != null;
        }
    }

    public enum LockState {
        LOCKED,
        UNLOCKED,
        UNAVAILABLE,
        FAILED
    }

    private enum Availability {
        AVAILABLE,
        NOT_INSTALLED,
        BROKEN
    }

    private record Invocation(boolean successful, Object value) {
        static Invocation success(Object value) {
            return new Invocation(true, value);
        }

        static Invocation failed() {
            return new Invocation(false, null);
        }
    }
}
