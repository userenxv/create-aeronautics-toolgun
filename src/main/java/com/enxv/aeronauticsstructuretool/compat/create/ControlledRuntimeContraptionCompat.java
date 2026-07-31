package com.enxv.aeronauticsstructuretool.compat.create;

import com.enxv.aeronauticsstructuretool.blueprint.material.BlueprintInventoryMaterialCapture;
import com.enxv.aeronauticsstructuretool.RuntimeContraptionBlueprint;
import com.enxv.aeronauticsstructuretool.blueprint.runtime.RuntimeContraptionCodec;
import com.enxv.aeronauticsstructuretool.blueprint.runtime.RuntimeContraptionEntities;
import com.enxv.aeronauticsstructuretool.blueprint.runtime.RuntimeContraptionRestoreResult;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.lang.reflect.Method;
import java.util.Map;

public final class ControlledRuntimeContraptionCompat {
    private static final String AERONAUTICS_ENTITY_CLASS =
            "dev.eriksonn.aeronautics.content.blocks.propeller.bearing.contraption.PropellerBearingContraptionEntity";
    private static final String AERONAUTICS_BEARING_CLASS =
            "dev.eriksonn.aeronautics.content.blocks.propeller.bearing.propeller_bearing.PropellerBearingBlockEntity";
    private static final String OFFROAD_ENTITY_CLASS =
            "dev.ryanhcode.offroad.content.entities.BoreheadContraptionEntity";
    private static final String OFFROAD_BEARING_CLASS =
            "dev.ryanhcode.offroad.content.blocks.borehead_bearing.BoreheadBearingBlockEntity";

    private static volatile SpecialClasses specialClasses;

    private ControlledRuntimeContraptionCompat() {
    }

    public static RuntimeContraptionBlueprint capture(
            ServerLevel level,
            MechanicalBearingBlockEntity bearing,
            BlockPos controllerLocalPos
    ) {
        ControlledContraptionEntity moved = bearing.getMovedContraption();
        if (moved == null || !moved.isAlive()) {
            return null;
        }
        Contraption contraption = moved.getContraption();
        CompoundTag contraptionTag = contraption.writeNBT(level.registryAccess(), false);
        Map<String, Long> materialItems = BlueprintInventoryMaterialCapture.captureHandler(
                contraption.getStorage().getAllItems(),
                level.registryAccess()
        );
        return new RuntimeContraptionBlueprint(
                RuntimeContraptionCodec.CREATE_CONTROLLED_KIND,
                controllerLocalPos,
                contraptionTag,
                moved.getClass().getName(),
                moved.getAngle(1.0F),
                0.0F,
                0.0F,
                "",
                materialItems
        );
    }

    public static RuntimeContraptionRestoreResult restore(
            RuntimeContraptionBlueprint blueprint,
            ServerLevel level,
            BlockPos controllerPos,
            BlockEntity blockEntity
    ) throws ReflectiveOperationException {
        if (!(blockEntity instanceof MechanicalBearingBlockEntity bearing)) {
            return RuntimeContraptionRestoreResult.permanentFailure(
                    "expected a Create mechanical bearing at " + controllerPos
                            + " but found " + blockEntity.getClass().getName()
            );
        }
        ControlledContraptionEntity attached = bearing.getMovedContraption();
        if (attached != null && attached.isAlive()) {
            return RuntimeContraptionRestoreResult.success();
        }

        BlockState state = level.getBlockState(controllerPos);
        if (!state.hasProperty(BlockStateProperties.FACING)) {
            return RuntimeContraptionRestoreResult.permanentFailure(
                    "bearing state has no facing at " + controllerPos + ": " + state
            );
        }
        RuntimeContraptionEntities.purgeForController(level, controllerPos);
        Contraption contraption = Contraption.fromNBT(level, blueprint.contraptionTag().copy(), false);
        ControlledContraptionEntity entity = createEntity(
                blueprint.entityClassName(),
                level,
                bearing,
                contraption
        );
        if (entity == null) {
            return RuntimeContraptionRestoreResult.permanentFailure(
                    "no matching controlled contraption entity factory for bearing="
                            + blockEntity.getClass().getName() + " entity=" + blueprint.entityClassName()
            );
        }

        var facing = state.getValue(BlockStateProperties.FACING);
        BlockPos anchor = controllerPos.relative(facing);
        entity.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
        entity.setRotationAxis(facing.getAxis());
        entity.setAngle(blueprint.angle());
        bearing.attach(entity);
        if (!level.addFreshEntity(entity)) {
            return RuntimeContraptionRestoreResult.permanentFailure(
                    "server rejected controlled contraption entity at " + controllerPos
            );
        }
        return RuntimeContraptionRestoreResult.success();
    }

    private static ControlledContraptionEntity createEntity(
            String entityClassName,
            ServerLevel level,
            MechanicalBearingBlockEntity bearing,
            Contraption contraption
    ) throws ReflectiveOperationException {
        SpecialClasses classes = optionalSpecialClasses();
        if (classes.offroadBearing() != null && classes.offroadBearing().isInstance(bearing)) {
            return invokeFactory(classes.offroadEntity(), level, bearing, contraption);
        }
        if (classes.aeronauticsBearing() != null && classes.aeronauticsBearing().isInstance(bearing)) {
            return invokeFactory(classes.aeronauticsEntity(), level, bearing, contraption);
        }
        if (entityClassName == null || entityClassName.isBlank()
                || ControlledContraptionEntity.class.getName().equals(entityClassName)) {
            return ControlledContraptionEntity.create(level, bearing, contraption);
        }
        Class<?> entityClass = Class.forName(entityClassName);
        if (!ControlledContraptionEntity.class.isAssignableFrom(entityClass)) {
            return null;
        }
        return invokeFactory(entityClass, level, bearing, contraption);
    }

    private static ControlledContraptionEntity invokeFactory(
            Class<?> entityClass,
            ServerLevel level,
            MechanicalBearingBlockEntity bearing,
            Contraption contraption
    ) throws ReflectiveOperationException {
        if (entityClass == null) {
            throw new ClassNotFoundException("controlled contraption entity class is unavailable");
        }
        for (Method method : entityClass.getMethods()) {
            if (!method.getName().equals("create") || method.getParameterCount() != 3) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (!parameterTypes[0].isInstance(level)
                    || !parameterTypes[1].isInstance(bearing)
                    || !parameterTypes[2].isInstance(contraption)) {
                continue;
            }
            Object created = method.invoke(null, level, bearing, contraption);
            if (created instanceof ControlledContraptionEntity entity) {
                return entity;
            }
            throw new ReflectiveOperationException("controlled contraption factory returned an incompatible entity");
        }
        throw new NoSuchMethodException(entityClass.getName() + "#create");
    }

    private static SpecialClasses optionalSpecialClasses() {
        SpecialClasses resolved = specialClasses;
        if (resolved != null) {
            return resolved;
        }
        synchronized (ControlledRuntimeContraptionCompat.class) {
            if (specialClasses == null) {
                specialClasses = new SpecialClasses(
                        tryLoad(AERONAUTICS_ENTITY_CLASS),
                        tryLoad(AERONAUTICS_BEARING_CLASS),
                        tryLoad(OFFROAD_ENTITY_CLASS),
                        tryLoad(OFFROAD_BEARING_CLASS)
                );
            }
            return specialClasses;
        }
    }

    private static Class<?> tryLoad(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException optionalDependencyMissing) {
            return null;
        } catch (LinkageError linkageError) {
            throw new IllegalStateException("optional controlled contraption class could not be linked: " + className, linkageError);
        }
    }

    private record SpecialClasses(
            Class<?> aeronauticsEntity,
            Class<?> aeronauticsBearing,
            Class<?> offroadEntity,
            Class<?> offroadBearing
    ) {
    }
}
