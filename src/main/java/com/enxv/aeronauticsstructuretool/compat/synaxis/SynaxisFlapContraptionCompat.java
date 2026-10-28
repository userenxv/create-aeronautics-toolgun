package com.enxv.aeronauticsstructuretool.compat.synaxis;

import com.enxv.aeronauticsstructuretool.RuntimeContraptionBlueprint;
import com.enxv.aeronauticsstructuretool.blueprint.material.BlueprintInventoryMaterialCapture;
import com.enxv.aeronauticsstructuretool.blueprint.runtime.RuntimeContraptionCodec;
import com.enxv.aeronauticsstructuretool.blueprint.runtime.RuntimeContraptionEntities;
import com.enxv.aeronauticsstructuretool.blueprint.runtime.RuntimeContraptionRestoreResult;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.io.IOException;

/** Compact flaps own a controlled contraption without extending Create's mechanical bearing. */
public final class SynaxisFlapContraptionCompat {
    public static final String ENTITY_CLASS = "com.verr1.synaxis.content.blocks.flap.FlapContraptionEntity";
    public static final String CONTROLLER_CLASS = "com.verr1.synaxis.content.blocks.flap.CompactFlapBlockEntity";

    private SynaxisFlapContraptionCompat() {
    }

    public static boolean isFlap(Entity entity) {
        return entity instanceof ControlledContraptionEntity && entity.getClass().getName().equals(ENTITY_CLASS);
    }

    public static boolean isController(BlockEntity blockEntity) {
        return blockEntity.getClass().getName().equals(CONTROLLER_CLASS);
    }

    public static ControlledContraptionEntity attached(BlockEntity blockEntity) {
        if (!isController(blockEntity)) {
            return null;
        }
        try {
            var field = blockEntity.getClass().getDeclaredField("physicalWing");
            field.setAccessible(true);
            Object value = field.get(blockEntity);
            if (value == null) return null;
            if (!(value instanceof ControlledContraptionEntity flap) || !isFlap(flap)) {
                throw new IllegalStateException("Synaxis compact flap owns an incompatible contraption");
            }
            return flap.isAlive() ? flap : null;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("cannot read Synaxis compact flap contraption", failure);
        }
    }

    private static java.lang.reflect.Field tiltField(ControlledContraptionEntity entity, String name)
            throws ReflectiveOperationException {
        var field = entity.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    public static RuntimeContraptionBlueprint capture(ServerLevel level, Entity entity, BlockPos localController) {
        if (!isFlap(entity)) {
            throw new IllegalArgumentException("not a Synaxis flap contraption");
        }
        ControlledContraptionEntity flap = (ControlledContraptionEntity) entity;
        Contraption contraption = flap.getContraption();
        if (contraption == null) {
            throw new IllegalStateException("assembled Synaxis flap has no contraption data");
        }
        try {
            // The runtime blueprint's pitch slot retains the flap's second rotation (tilt).
            float tilt = (float) tiltField(flap, "tilt").getDouble(flap);
            return new RuntimeContraptionBlueprint(RuntimeContraptionCodec.SYNAXIS_FLAP_KIND,
                    localController, contraption.writeNBT(level.registryAccess(), false), ENTITY_CLASS,
                    flap.getAngle(1F), 0F, tilt, "",
                    BlueprintInventoryMaterialCapture.captureHandler(contraption.getStorage().getAllItems(), level.registryAccess()));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("failed to capture Synaxis flap at " + localController, failure);
        }
    }

    public static RuntimeContraptionRestoreResult restore(RuntimeContraptionBlueprint blueprint,
            ServerLevel level, BlockPos controllerPos, BlockEntity blockEntity) throws IOException, ReflectiveOperationException {
        if (!blockEntity.getClass().getName().equals(CONTROLLER_CLASS)
                || !(blockEntity instanceof IControlContraption controller)) {
            return RuntimeContraptionRestoreResult.permanentFailure("expected a Synaxis compact flap at " + controllerPos);
        }
        if ((Boolean) SynaxisReflection.invoke(blockEntity, "isAssembled")) {
            return RuntimeContraptionRestoreResult.success();
        }
        Direction facing = (Direction) SynaxisReflection.invoke(blockEntity, "getDirection");
        Contraption contraption = Contraption.fromNBT(level, blueprint.contraptionTag().copy(), false);
        // Registry factory avoids resolving unrelated client-only renderer method signatures.
        var entityType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(
                net.minecraft.resources.ResourceLocation.parse("synaxis:flap_contraption"))
                .orElseThrow(() -> new IOException("Synaxis flap entity type is unavailable"));
        Entity created = entityType.create(level);
        if (!(created instanceof ControlledContraptionEntity flap) || !isFlap(flap)) {
            throw new IOException("Synaxis flap factory returned an incompatible entity");
        }
        RuntimeContraptionEntities.purgeForController(level, controllerPos);
        // Flap inherits Create's protected controllerPos; the base class is server-safe to inspect.
        var controllerField = ControlledContraptionEntity.class.getDeclaredField("controllerPos");
        controllerField.setAccessible(true);
        controllerField.set(flap, controllerPos);
        var setContraption = ControlledContraptionEntity.class.getDeclaredMethod("setContraption", Contraption.class);
        setContraption.setAccessible(true);
        setContraption.invoke(flap, contraption);
        BlockPos anchor = controllerPos.relative(facing);
        flap.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
        flap.setRotationAxis(facing.getAxis());
        flap.setAngle(blueprint.angle());
        tiltField(flap, "tilt").setDouble(flap, blueprint.pitch());
        tiltField(flap, "prevTilt").setDouble(flap, blueprint.pitch());
        controller.attach(flap);
        if (!controller.isAttachedTo(flap)) {
            return RuntimeContraptionRestoreResult.permanentFailure("Synaxis compact flap rejected restored contraption at " + controllerPos);
        }
        if (!level.addFreshEntity(flap)) {
            flap.discard();
            return RuntimeContraptionRestoreResult.permanentFailure("server rejected Synaxis flap contraption at " + controllerPos);
        }
        return RuntimeContraptionRestoreResult.success();
    }
}
