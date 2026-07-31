package com.enxv.aeronauticsstructuretool.compat.cbc;

import com.enxv.aeronauticsstructuretool.blueprint.material.BlueprintInventoryMaterialCapture;
import com.enxv.aeronauticsstructuretool.RuntimeContraptionBlueprint;
import com.enxv.aeronauticsstructuretool.blueprint.runtime.RuntimeContraptionCodec;
import com.enxv.aeronauticsstructuretool.blueprint.runtime.RuntimeContraptionRestoreResult;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public final class CbcRuntimeContraptionCompat {
    private static final String PITCH_ENTITY_CLASS =
            "rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity";
    private static final String MOUNTED_CONTRAPTION_CLASS =
            "rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption";
    private static final String FIXED_MOUNT_BLOCK_ENTITY_CLASS =
            "rbasamoyai.createbigcannons.cannon_control.fixed_cannon_mount.FixedCannonMountBlockEntity";
    private static final String CANNON_MOUNT_BLOCK_ENTITY_CLASS =
            "rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity";
    private static final String FIREPOWER_COMPACT_MOUNT_BLOCK_ENTITY_CLASS =
            "com.cbcfirepowercomponents.content.compact_cannon_mount.CompactCannonMountBlockEntity";
    private static final String FIREPOWER_COMPACT_AUTOCANNON_MOUNT_BLOCK_CLASS =
            "com.cbcfirepowercomponents.content.compact_cannon_mount.CompactAutocannonMountBlock";
    private static final String CUBESTER_COMPACT_MOUNT_BLOCK_ENTITY_CLASS =
            "com.cubester.cbc_compact_mount.content.CompactCannonMountBlockEntity";

    private static volatile ClassSet classes;

    private CbcRuntimeContraptionCompat() {
    }

    public static boolean isPitchEntity(Entity entity) {
        Class<?> pitchEntityClass = optionalClasses().pitchEntity();
        return pitchEntityClass != null && pitchEntityClass.isInstance(entity);
    }

    public static RuntimeContraptionBlueprint capture(
            ServerLevel level,
            Entity entity,
            BlockPos controllerLocalPos
    ) {
        if (!isPitchEntity(entity)) {
            throw new IllegalArgumentException("entity is not a CBC pitch contraption: " + entity.getType());
        }
        try {
            Method getContraption = requireMethod(entity.getClass(), "getContraption");
            Object value = getContraption.invoke(entity);
            if (!(value instanceof Contraption contraption)) {
                throw new ReflectiveOperationException("CBC getContraption returned an incompatible value");
            }
            Method getInitialOrientation = requireMethod(entity.getClass(), "getInitialOrientation");
            Object initialValue = getInitialOrientation.invoke(entity);
            if (!(initialValue instanceof Direction initialOrientation)) {
                throw new ReflectiveOperationException("CBC getInitialOrientation returned an incompatible value");
            }

            CompoundTag contraptionTag = contraption.writeNBT(level.registryAccess(), false);
            Map<String, Long> materialItems = BlueprintInventoryMaterialCapture.captureHandler(
                    contraption.getStorage().getAllItems(),
                    level.registryAccess()
            );
            return new RuntimeContraptionBlueprint(
                    RuntimeContraptionCodec.CBC_PITCH_KIND,
                    controllerLocalPos,
                    contraptionTag,
                    entity.getClass().getName(),
                    0.0F,
                    getFloatField(entity, "yaw"),
                    getFloatField(entity, "pitch"),
                    initialOrientation.getSerializedName(),
                    materialItems
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "failed to capture CBC runtime contraption " + entity.getType(),
                    exception
            );
        }
    }

    public static RuntimeContraptionRestoreResult restore(
            RuntimeContraptionBlueprint blueprint,
            ServerLevel level,
            BlockPos controllerPos,
            BlockEntity blockEntity
    ) throws ReflectiveOperationException {
        ClassSet requiredClasses = requireClasses();
        Contraption contraption = Contraption.fromNBT(level, blueprint.contraptionTag().copy(), false);
        if (!requiredClasses.mountedContraption().isInstance(contraption)) {
            return RuntimeContraptionRestoreResult.permanentFailure(
                    "CBC runtime contraption data is not a mounted cannon at " + controllerPos
            );
        }

        Direction initialOrientation = blueprint.initialOrientation().isBlank()
                ? readInitialOrientation(contraption)
                : RuntimeContraptionCodec.parseDirection(blueprint.initialOrientation());
        if (requiredClasses.fixedMountBlockEntity() != null
                && requiredClasses.fixedMountBlockEntity().isInstance(blockEntity)) {
            return restoreFixedMount(
                    blueprint,
                    level,
                    controllerPos,
                    blockEntity,
                    contraption,
                    initialOrientation,
                    requiredClasses.pitchEntity()
            );
        }
        if (requiredClasses.cannonMountBlockEntity() != null
                && requiredClasses.cannonMountBlockEntity().isInstance(blockEntity)) {
            return restoreCannonMount(
                    blueprint,
                    level,
                    controllerPos,
                    blockEntity,
                    contraption,
                    initialOrientation,
                    requiredClasses.pitchEntity()
            );
        }
        if (requiredClasses.firepowerCompactMountBlockEntity() != null
                && requiredClasses.firepowerCompactMountBlockEntity().isInstance(blockEntity)) {
            return restoreFirepowerCompactMount(
                    blueprint,
                    level,
                    controllerPos,
                    blockEntity,
                    contraption,
                    initialOrientation,
                    requiredClasses.pitchEntity()
            );
        }
        if (requiredClasses.cubesterCompactMountBlockEntity() != null
                && requiredClasses.cubesterCompactMountBlockEntity().isInstance(blockEntity)) {
            return restoreCubesterCompactMount(
                    blueprint,
                    level,
                    controllerPos,
                    blockEntity,
                    contraption,
                    initialOrientation,
                    requiredClasses.pitchEntity()
            );
        }
        return RuntimeContraptionRestoreResult.permanentFailure(
                "unsupported CBC controller block entity at " + controllerPos + ": "
                        + blockEntity.getClass().getName()
        );
    }

    private static RuntimeContraptionRestoreResult restoreFixedMount(
            RuntimeContraptionBlueprint blueprint,
            ServerLevel level,
            BlockPos controllerPos,
            BlockEntity blockEntity,
            Contraption contraption,
            Direction initialOrientation,
            Class<?> pitchEntityClass
    ) throws ReflectiveOperationException {
        BlockState state = level.getBlockState(controllerPos);
        if (!state.hasProperty(BlockStateProperties.FACING)) {
            return RuntimeContraptionRestoreResult.permanentFailure(
                    "CBC fixed mount has no facing property at " + controllerPos
            );
        }
        Entity entity = createPitchEntity(
                pitchEntityClass,
                level,
                contraption,
                initialOrientation,
                blockEntity
        );
        Direction facing = state.getValue(BlockStateProperties.FACING);
        Vec3 anchor = Vec3.atBottomCenterOf(controllerPos.relative(facing));
        entity.setPos(anchor);
        syncPitchEntityAngles(entity, blueprint.yaw(), blueprint.pitch());
        attach(blockEntity, entity);
        if (!level.addFreshEntity(entity)) {
            return RuntimeContraptionRestoreResult.permanentFailure(
                    "server rejected CBC fixed-mount entity at " + controllerPos
            );
        }
        return RuntimeContraptionRestoreResult.success();
    }

    private static RuntimeContraptionRestoreResult restoreCannonMount(
            RuntimeContraptionBlueprint blueprint,
            ServerLevel level,
            BlockPos controllerPos,
            BlockEntity blockEntity,
            Contraption contraption,
            Direction initialOrientation,
            Class<?> pitchEntityClass
    ) throws ReflectiveOperationException {
        BlockState state = level.getBlockState(controllerPos);
        if (!state.hasProperty(BlockStateProperties.VERTICAL_DIRECTION)) {
            return RuntimeContraptionRestoreResult.permanentFailure(
                    "CBC cannon mount has no vertical direction at " + controllerPos
            );
        }
        Entity entity = createPitchEntity(
                pitchEntityClass,
                level,
                contraption,
                initialOrientation,
                blockEntity
        );
        Direction vertical = state.getValue(BlockStateProperties.VERTICAL_DIRECTION);
        entity.setPos(Vec3.atBottomCenterOf(controllerPos.relative(vertical, -2)));
        attach(blockEntity, entity);
        applyCannonMountRotation(blockEntity, entity, blueprint.yaw(), blueprint.pitch());
        if (!level.addFreshEntity(entity)) {
            return RuntimeContraptionRestoreResult.permanentFailure(
                    "server rejected CBC cannon-mount entity at " + controllerPos
            );
        }
        return RuntimeContraptionRestoreResult.success();
    }

    private static RuntimeContraptionRestoreResult restoreFirepowerCompactMount(
            RuntimeContraptionBlueprint blueprint,
            ServerLevel level,
            BlockPos controllerPos,
            BlockEntity blockEntity,
            Contraption contraption,
            Direction initialOrientation,
            Class<?> pitchEntityClass
    ) throws ReflectiveOperationException {
        BlockState state = level.getBlockState(controllerPos);
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return RuntimeContraptionRestoreResult.permanentFailure(
                    "CBC Firepower Components compact mount has no horizontal facing at " + controllerPos
            );
        }
        Entity entity = createPitchEntity(
                pitchEntityClass,
                level,
                contraption,
                initialOrientation,
                blockEntity
        );
        // Firepower mounts use counter-clockwise HORIZONTAL_FACING; the autocannon
        // subtype exposes its vertical cannon side instead.
        Direction cannonSide = resolveFirepowerCompactCannonSide(
                state,
                requiredFirepowerAutocannonBlockClass()
        );
        entity.setPos(Vec3.atBottomCenterOf(controllerPos.relative(cannonSide)));
        attach(blockEntity, entity);
        applyCannonMountRotation(blockEntity, entity, blueprint.yaw(), blueprint.pitch());
        if (!level.addFreshEntity(entity)) {
            return RuntimeContraptionRestoreResult.permanentFailure(
                    "server rejected CBC Firepower Components compact-mount entity at " + controllerPos
            );
        }
        return RuntimeContraptionRestoreResult.success();
    }

    private static Direction resolveFirepowerCompactCannonSide(
            BlockState state,
            Class<?> autocannonBlockClass
    ) throws ReflectiveOperationException {
        if (autocannonBlockClass != null && autocannonBlockClass.isInstance(state.getBlock())) {
            Method getCannonSide = state.getBlock().getClass().getMethod("getCannonSide", BlockState.class);
            Object value = getCannonSide.invoke(state.getBlock(), state);
            if (value instanceof Direction direction) {
                return direction;
            }
            throw new ReflectiveOperationException(
                    "CBC Firepower Components autocannon mount returned an invalid cannon side"
            );
        }
        return state.getValue(BlockStateProperties.HORIZONTAL_FACING).getCounterClockWise();
    }

    private static Class<?> requiredFirepowerAutocannonBlockClass() {
        return optionalClasses().firepowerCompactAutocannonMountBlock();
    }

    private static RuntimeContraptionRestoreResult restoreCubesterCompactMount(
            RuntimeContraptionBlueprint blueprint,
            ServerLevel level,
            BlockPos controllerPos,
            BlockEntity blockEntity,
            Contraption contraption,
            Direction initialOrientation,
            Class<?> pitchEntityClass
    ) throws ReflectiveOperationException {
        BlockState state = level.getBlockState(controllerPos);
        if (!state.hasProperty(BlockStateProperties.FACING)) {
            return RuntimeContraptionRestoreResult.permanentFailure(
                    "CBC Compact Mount has no horizontal facing at " + controllerPos
            );
        }
        Entity entity = createPitchEntity(
                pitchEntityClass,
                level,
                contraption,
                initialOrientation,
                blockEntity
        );
        // CBC Compact Mount assembles clockwise from FACING and exposes pitch only.
        Direction cannonSide = state.getValue(BlockStateProperties.FACING).getClockWise();
        entity.setPos(Vec3.atBottomCenterOf(controllerPos.relative(cannonSide)));
        attach(blockEntity, entity);
        applyPitchOnlyMountRotation(blockEntity, entity, initialOrientation, blueprint.pitch());
        if (!level.addFreshEntity(entity)) {
            return RuntimeContraptionRestoreResult.permanentFailure(
                    "server rejected CBC Compact Mount entity at " + controllerPos
            );
        }
        return RuntimeContraptionRestoreResult.success();
    }

    private static Direction readInitialOrientation(Contraption contraption)
            throws ReflectiveOperationException {
        Object value = requireMethod(contraption.getClass(), "initialOrientation").invoke(contraption);
        if (value instanceof Direction direction) {
            return direction;
        }
        throw new ReflectiveOperationException("CBC initialOrientation returned an incompatible value");
    }

    private static Entity createPitchEntity(
            Class<?> pitchEntityClass,
            ServerLevel level,
            Contraption contraption,
            Direction initialOrientation,
            Object mount
    ) throws ReflectiveOperationException {
        for (Method method : pitchEntityClass.getMethods()) {
            if (!method.getName().equals("create") || method.getParameterCount() != 4) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (!parameterTypes[0].isInstance(level)
                    || !parameterTypes[1].isInstance(contraption)
                    || !parameterTypes[2].isInstance(initialOrientation)
                    || !parameterTypes[3].isInstance(mount)) {
                continue;
            }
            Object created = method.invoke(null, level, contraption, initialOrientation, mount);
            if (created instanceof Entity entity) {
                return entity;
            }
            throw new ReflectiveOperationException("CBC pitch factory returned an incompatible entity");
        }
        throw new NoSuchMethodException(pitchEntityClass.getName() + "#create");
    }

    private static void applyCannonMountRotation(Object mount, Entity entity, float yaw, float pitch)
            throws ReflectiveOperationException {
        setFloatField(mount, "cannonYaw", yaw);
        setFloatField(mount, "cannonPitch", pitch);
        setFloatField(mount, "prevYaw", yaw);
        setFloatField(mount, "prevPitch", pitch);
        requireMethod(mount.getClass(), "applyRotation").invoke(mount);
        syncPitchEntityAngles(entity, getFloatField(entity, "yaw"), getFloatField(entity, "pitch"));
    }

    private static void applyPitchOnlyMountRotation(
            Object mount,
            Entity entity,
            Direction initialOrientation,
            float pitch
    ) throws ReflectiveOperationException {
        setFloatField(mount, "cannonPitch", pitch);
        setFloatField(mount, "prevPitch", pitch);
        requireMethod(mount.getClass(), "applyRotation").invoke(mount);
        syncPitchEntityAngles(entity, initialOrientation.toYRot(), pitch);
    }

    private static void syncPitchEntityAngles(Entity entity, float yaw, float pitch)
            throws ReflectiveOperationException {
        setFloatField(entity, "yaw", yaw);
        setFloatField(entity, "pitch", pitch);
        setFloatField(entity, "prevYaw", yaw);
        setFloatField(entity, "prevPitch", pitch);
        entity.setYRot(yaw);
        entity.setXRot(pitch);
        entity.yRotO = yaw;
        entity.xRotO = pitch;
    }

    private static void attach(Object mount, Entity entity) throws ReflectiveOperationException {
        for (Method method : mount.getClass().getMethods()) {
            if (method.getName().equals("attach")
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isInstance(entity)) {
                method.invoke(mount, entity);
                return;
            }
        }
        throw new NoSuchMethodException(
                mount.getClass().getName() + "#attach(" + entity.getClass().getName() + ")"
        );
    }

    private static float getFloatField(Object target, String name) throws ReflectiveOperationException {
        Field field = requireField(target.getClass(), name);
        return field.getFloat(target);
    }

    private static void setFloatField(Object target, String name, float value)
            throws ReflectiveOperationException {
        requireField(target.getClass(), name).setFloat(target, value);
    }

    private static Field requireField(Class<?> type, String name) throws NoSuchFieldException {
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

    private static Method requireMethod(Class<?> type, String name) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException notDeclaredOnCurrentType) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }

    private static ClassSet optionalClasses() {
        ClassSet resolved = classes;
        if (resolved != null) {
            return resolved;
        }
        synchronized (CbcRuntimeContraptionCompat.class) {
            if (classes == null) {
                classes = new ClassSet(
                        tryLoad(PITCH_ENTITY_CLASS),
                        tryLoad(MOUNTED_CONTRAPTION_CLASS),
                        tryLoad(FIXED_MOUNT_BLOCK_ENTITY_CLASS),
                        tryLoad(CANNON_MOUNT_BLOCK_ENTITY_CLASS),
                        tryLoad(FIREPOWER_COMPACT_MOUNT_BLOCK_ENTITY_CLASS),
                        tryLoad(CUBESTER_COMPACT_MOUNT_BLOCK_ENTITY_CLASS),
                        tryLoad(FIREPOWER_COMPACT_AUTOCANNON_MOUNT_BLOCK_CLASS)
                );
            }
            return classes;
        }
    }

    private static ClassSet requireClasses() {
        ClassSet resolved = optionalClasses();
        if (resolved.pitchEntity() == null
                || resolved.mountedContraption() == null) {
            throw new IllegalStateException("CBC runtime contraption adapter classes are unavailable");
        }
        return resolved;
    }

    private static Class<?> tryLoad(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException optionalDependencyMissing) {
            return null;
        } catch (LinkageError linkageError) {
            throw new IllegalStateException("CBC class could not be linked: " + className, linkageError);
        }
    }

    private record ClassSet(
            Class<?> pitchEntity,
            Class<?> mountedContraption,
            Class<?> fixedMountBlockEntity,
            Class<?> cannonMountBlockEntity,
            Class<?> firepowerCompactMountBlockEntity,
            Class<?> cubesterCompactMountBlockEntity,
            Class<?> firepowerCompactAutocannonMountBlock
    ) {
    }
}
