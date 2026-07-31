package com.enxv.aeronauticsstructuretool.compat.synaxis;

import net.neoforged.fml.ModList;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class SynaxisReflection {
    private static final String MOD_ID = "synaxis";

    private SynaxisReflection() {
    }

    static boolean isInstalled() {
        return ModList.get().isLoaded(MOD_ID);
    }

    static Class<?> requireClass(String className) throws IOException {
        requireInstalled();
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException | LinkageError exception) {
            throw new IOException("Synaxis runtime class is unavailable: " + className, exception);
        }
    }

    static Object invoke(Object target, String methodName, Object... arguments) throws IOException {
        if (target == null) {
            throw new IOException("Cannot invoke Synaxis method '" + methodName + "' on null");
        }
        Method method = findCompatibleMethod(target.getClass(), methodName, arguments);
        if (method == null) {
            throw new IOException(
                    "Synaxis runtime type " + target.getClass().getName()
                            + " has no compatible method '" + methodName + "'"
            );
        }
        return invokeMethod(method, target, arguments);
    }

    static Object invokeAccessor(Object target, String currentName, String legacyName) throws IOException {
        if (target == null) {
            throw new IOException("Cannot read Synaxis accessor '" + currentName + "' on null");
        }
        Method current = findMethod(target.getClass(), currentName, 0);
        if (current != null) {
            return invokeMethod(current, target);
        }
        Method legacy = findMethod(target.getClass(), legacyName, 0);
        if (legacy != null) {
            return invokeMethod(legacy, target);
        }
        throw new IOException(
                "Synaxis runtime type " + target.getClass().getName()
                        + " has neither '" + currentName + "' nor legacy '" + legacyName + "'"
        );
    }

    static Object invokeStatic(
            String className,
            String methodName,
            Class<?>[] parameterTypes,
            Object... arguments
    ) throws IOException {
        Class<?> type = requireClass(className);
        try {
            return invokeMethod(type.getMethod(methodName, parameterTypes), null, arguments);
        } catch (NoSuchMethodException exception) {
            throw new IOException(
                    "Synaxis runtime type " + className + " has no method '" + methodName + "'",
                    exception
            );
        }
    }

    static Method findMethod(Class<?> type, String name, int parameterCount) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    static Method requirePublicMethod(Class<?> type, String name, Class<?>... parameterTypes) throws IOException {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException exception) {
            throw new IOException(
                    "Synaxis runtime type " + type.getName() + " has no method '" + name + "'",
                    exception
            );
        }
    }

    static Object invokeMethod(Method method, Object target, Object... arguments) throws IOException {
        try {
            return method.invoke(target, arguments);
        } catch (IllegalAccessException exception) {
            throw new IOException("Cannot access Synaxis method '" + method.getName() + "'", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IOException("Synaxis method '" + method.getName() + "' failed", cause);
        }
    }

    private static Method findCompatibleMethod(Class<?> type, String name, Object[] arguments) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) {
                    continue;
                }
                Class<?>[] parameters = method.getParameterTypes();
                boolean compatible = true;
                for (int i = 0; i < parameters.length; i++) {
                    if (arguments[i] != null && !wrap(parameters[i]).isInstance(arguments[i])) {
                        compatible = false;
                        break;
                    }
                }
                if (compatible) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static void requireInstalled() throws IOException {
        if (!isInstalled()) {
            throw new IOException("Synaxis blueprint data is present but Synaxis is not loaded");
        }
    }
}
