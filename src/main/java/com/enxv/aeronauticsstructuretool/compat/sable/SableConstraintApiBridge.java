
package com.enxv.aeronauticsstructuretool.compat.sable;

import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class SableConstraintApiBridge {
    private static final String NEW_FIXED_CONFIGURATION_CLASS = "dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration";
    private static final String OLD_FIXED_CONFIGURATION_CLASS = "dev.ryanhcode.sable.api.physics.constraint.fixed.FixedConstraintConfiguration";
    private static final String NEW_GENERIC_CONFIGURATION_CLASS = "dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration";
    private static final String OLD_GENERIC_CONFIGURATION_CLASS = "dev.ryanhcode.sable.api.physics.constraint.generic.GenericConstraintConfiguration";
    private static final String NEW_ROTARY_CONFIGURATION_CLASS = "dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintConfiguration";
    private static final String OLD_ROTARY_CONFIGURATION_CLASS = "dev.ryanhcode.sable.api.physics.constraint.rotary.RotaryConstraintConfiguration";
    private static final String NEW_FREE_CONFIGURATION_CLASS = "dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration";
    private static final String OLD_FREE_CONFIGURATION_CLASS = "dev.ryanhcode.sable.api.physics.constraint.free.FreeConstraintConfiguration";

    private SableConstraintApiBridge() {
    }

    public static PhysicsConstraintHandle addFixed(
            PhysicsPipeline pipeline,
            ServerSubLevel first,
            ServerSubLevel second,
            Vector3d firstLocal,
            Vector3d secondLocal,
            Quaterniond relativeOrientation
    ) throws IOException {
        return addConstraint(pipeline, first, second, constructFirstCompatible(
                new String[]{NEW_FIXED_CONFIGURATION_CLASS, OLD_FIXED_CONFIGURATION_CLASS},
                new Vector3d(firstLocal),
                new Vector3d(secondLocal),
                new Quaterniond(relativeOrientation)
        ));
    }

    public static PhysicsConstraintHandle addGeneric(
            PhysicsPipeline pipeline,
            ServerSubLevel first,
            ServerSubLevel second,
            Vector3d firstLocal,
            Vector3d secondLocal,
            Quaterniond relativeOrientation
    ) throws IOException {
        return addConstraint(pipeline, first, second, constructFirstCompatible(
                new String[]{NEW_GENERIC_CONFIGURATION_CLASS, OLD_GENERIC_CONFIGURATION_CLASS},
                new Vector3d(firstLocal),
                new Vector3d(secondLocal),
                new Quaterniond(),
                new Quaterniond(relativeOrientation),
                Set.of(
                        ConstraintJointAxis.LINEAR_X,
                        ConstraintJointAxis.LINEAR_Y,
                        ConstraintJointAxis.LINEAR_Z
                )
        ));
    }

    public static PhysicsConstraintHandle addRotary(
            PhysicsPipeline pipeline,
            ServerSubLevel first,
            ServerSubLevel second,
            Vector3d firstLocal,
            Vector3d secondLocal,
            Vector3d firstAxisLocal,
            Vector3d secondAxisLocal
    ) throws IOException {
        return addConstraint(pipeline, first, second, constructFirstCompatible(
                new String[]{NEW_ROTARY_CONFIGURATION_CLASS, OLD_ROTARY_CONFIGURATION_CLASS},
                new Vector3d(firstLocal),
                new Vector3d(secondLocal),
                new Vector3d(firstAxisLocal),
                new Vector3d(secondAxisLocal)
        ));
    }

    public static PhysicsConstraintHandle addFree(
            PhysicsPipeline pipeline,
            ServerSubLevel second,
            Vector3d worldAnchor,
            Vector3d secondLocal,
            Quaterniond orientation
    ) throws IOException {
        return addConstraint(pipeline, null, second, constructFirstCompatible(
                new String[]{NEW_FREE_CONFIGURATION_CLASS, OLD_FREE_CONFIGURATION_CLASS},
                new Vector3d(worldAnchor),
                new Vector3d(secondLocal),
                new Quaterniond(orientation)
        ));
    }

    private static PhysicsConstraintHandle addConstraint(
            PhysicsPipeline pipeline,
            ServerSubLevel first,
            ServerSubLevel second,
            Object configuration
    ) throws IOException {
        Method method = findAddConstraintMethod(pipeline.getClass(), first, second, configuration);
        try {
            Object handle = method.invoke(pipeline, first, second, configuration);
            if (handle instanceof PhysicsConstraintHandle physicsConstraintHandle) {
                return physicsConstraintHandle;
            }
            throw new IOException("Sable addConstraint returned " + (handle == null ? "null" : handle.getClass().getName()));
        } catch (ReflectiveOperationException exception) {
            throw new IOException("failed to invoke Sable addConstraint", exception);
        }
    }

    private static Method findAddConstraintMethod(
            Class<?> pipelineClass,
            ServerSubLevel first,
            ServerSubLevel second,
            Object configuration
    ) throws IOException {
        for (Method method : pipelineClass.getMethods()) {
            if (!"addConstraint".equals(method.getName()) || method.getParameterCount() != 3) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            if ((first == null || types[0].isInstance(first))
                    && (second == null || types[1].isInstance(second))
                    && types[2].isInstance(configuration)) {
                return method;
            }
        }
        throw new IOException("no compatible Sable addConstraint overload on " + pipelineClass.getName());
    }

    private static Object construct(String className, Object... args) throws IOException {
        try {
            Class<?> type = Class.forName(className);
            for (Constructor<?> constructor : type.getConstructors()) {
                if (matches(constructor.getParameterTypes(), args)) {
                    return constructor.newInstance(args);
                }
            }
            throw new IOException("no compatible constructor for " + className + "; constructors=" + describeConstructors(type));
        } catch (ReflectiveOperationException exception) {
            throw new IOException("failed to construct " + className, exception);
        }
    }

    private static Object constructFirstCompatible(String[] classNames, Object... args) throws IOException {
        IOException lastFailure = null;
        for (String className : classNames) {
            try {
                return construct(className, args);
            } catch (IOException exception) {
                lastFailure = exception;
            }
        }
        throw new IOException("no compatible Sable constraint configuration among " + String.join(", ", classNames), lastFailure);
    }

    private static boolean matches(Class<?>[] parameterTypes, Object[] args) {
        if (parameterTypes.length != args.length) {
            return false;
        }
        for (int i = 0; i < parameterTypes.length; i++) {
            if (args[i] != null && !parameterTypes[i].isInstance(args[i])) {
                return false;
            }
        }
        return true;
    }

    private static String describeConstructors(Class<?> type) {
        return Arrays.stream(type.getConstructors())
                .map(constructor -> Arrays.stream(constructor.getParameterTypes())
                        .map(Class::getName)
                        .collect(Collectors.joining(", ", "(", ")")))
                .collect(Collectors.joining("; "));
    }

}
