package com.enxv.aeronauticsstructuretool.compat.simulated;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class SimulatedReflectionBridge {
    private SimulatedReflectionBridge() {
    }

    static Class<?> findOptionalClass(String className, String label) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException optionalDependencyMissing) {
            return null;
        } catch (LinkageError linkageError) {
            throw new IllegalStateException(label + " could not be linked", linkageError);
        }
    }

    static Object invokeRequired(Object target, String methodName, Object... args) {
        if (target == null) {
            throw new IllegalStateException("cannot invoke " + methodName + " on null");
        }
        try {
            Method method = findCompatibleMethod(target.getClass(), methodName, args);
            if (method == null) {
                throw new NoSuchMethodException(target.getClass().getName() + "#" + methodName);
            }
            return method.invoke(target, args);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Simulated compatibility call failed: "
                            + target.getClass().getName() + "#" + methodName,
                    exception
            );
        }
    }

    static boolean invokeRequiredBoolean(Object target, String methodName) {
        Object value = invokeRequired(target, methodName);
        if (value instanceof Boolean result) {
            return result;
        }
        throw new IllegalStateException(methodName + " did not return boolean");
    }

    static Field requireField(Class<?> type, String name) throws NoSuchFieldException {
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

    private static Method findCompatibleMethod(Class<?> type, String name, Object[] args) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                boolean compatible = true;
                for (int i = 0; i < parameterTypes.length; i++) {
                    if (args[i] == null) {
                        compatible = !parameterTypes[i].isPrimitive();
                    } else {
                        compatible = wrapPrimitive(parameterTypes[i]).isInstance(args[i]);
                    }
                    if (!compatible) {
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

    private static Class<?> wrapPrimitive(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }
}
