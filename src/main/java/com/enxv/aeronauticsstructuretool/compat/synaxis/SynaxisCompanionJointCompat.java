package com.enxv.aeronauticsstructuretool.compat.synaxis;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.LoadedSubLevelCoordinates;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockTransform;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturePlan;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturedSubLevel;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class SynaxisCompanionJointCompat {
    private static final String COMPANION_BLOCK_ENTITY =
            "com.verr1.synaxis.foundation.blockentity.CompanionPhysicsBlockEntity";

    private SynaxisCompanionJointCompat() {
    }

    static void capture(
            ServerLevel level,
            CompoundTag plotTag,
            CapturePlan plan,
            CapturedSubLevel currentSubLevel
    ) throws IOException {
        if (!SynaxisReflection.isInstalled()) {
            return;
        }
        Class<?> companionType = SynaxisReflection.requireClass(COMPANION_BLOCK_ENTITY);
        PlotBlockTransform ownerTransform = PlotBlockTransform.capture(currentSubLevel.subLevel());
        List<SynaxisBlueprintNbt.CompanionJoint> captured = new ArrayList<>();
        int outsideCapture = 0;

        for (BlockEntity blockEntity : ownerTransform.findBlockEntities(level)) {
            if (!companionType.isInstance(blockEntity)) {
                continue;
            }
            Object hasIntentValue = SynaxisReflection.invoke(blockEntity, "hasConnectionIntent");
            if (!(hasIntentValue instanceof Boolean hasIntent)) {
                throw new IOException("Synaxis hasConnectionIntent returned a non-boolean value");
            }
            if (!hasIntent) {
                continue;
            }

            Object targetValue = SynaxisReflection.invokeAccessor(
                    blockEntity,
                    "connectionTarget",
                    "connectionTargetPosition"
            );
            Object directionValue = SynaxisReflection.invoke(blockEntity, "companionDirection");
            Object alignmentValue = SynaxisReflection.invoke(blockEntity, "companionAlignmentDirection");
            Object expectedValue = SynaxisReflection.invoke(blockEntity, "expectedCompanionUuid");
            if (!(targetValue instanceof BlockPos targetPos)
                    || !(directionValue instanceof Direction direction)
                    || !(alignmentValue instanceof Direction alignmentDirection)
                    || !(expectedValue instanceof Optional<?> expectedOptional)) {
                throw new IOException(
                        "Synaxis companion block entity returned an invalid connection intent at "
                                + blockEntity.getBlockPos()
                );
            }

            CapturedSubLevel targetSubLevel = findCaptured(plan, targetPos);
            if (targetSubLevel == null) {
                outsideCapture++;
                continue;
            }
            UUID expectedBlueprintId = null;
            if (expectedOptional.isPresent()) {
                Object rawExpected = expectedOptional.get();
                if (!(rawExpected instanceof UUID expectedOriginalId)) {
                    throw new IOException("Synaxis expected companion UUID has an invalid runtime type");
                }
                CapturedSubLevel expectedSubLevel = plan.findByOriginalId(expectedOriginalId);
                if (expectedSubLevel == null) {
                    outsideCapture++;
                    continue;
                }
                expectedBlueprintId = expectedSubLevel.blueprintId();
            }

            captured.add(new SynaxisBlueprintNbt.CompanionJoint(
                    currentSubLevel.blueprintId(),
                    ownerTransform.toSavedLocalBlockPos(blockEntity.getBlockPos()),
                    targetSubLevel.blueprintId(),
                    PlotBlockTransform.capture(targetSubLevel.subLevel()).toSavedLocalBlockPos(targetPos),
                    direction,
                    alignmentDirection,
                    expectedBlueprintId
            ));
        }
        SynaxisBlueprintNbt.writeCompanionJoints(plotTag, captured);
        if (outsideCapture > 0) {
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Synaxis companion capture omitted {} joint(s) that reference structures outside the blueprint",
                    outsideCapture
            );
        }
    }

    static void restore(
            ServerLevel level,
            String blueprintName,
            Map<UUID, LoadedSubLevel> loadedSublevels,
            List<SynaxisBlueprintNbt.CompanionJoint> requests
    ) throws IOException {
        if (requests.isEmpty()) {
            return;
        }
        if (!SynaxisReflection.isInstalled()) {
            throw new IOException("Blueprint '" + blueprintName + "' contains Synaxis companion joints but Synaxis is not loaded");
        }
        Class<?> companionType = SynaxisReflection.requireClass(COMPANION_BLOCK_ENTITY);
        Method withoutExpected = null;
        Method withExpected = null;

        int restored = 0;
        for (SynaxisBlueprintNbt.CompanionJoint request : requests) {
            LoadedSubLevel ownerLoaded = requireLoaded(loadedSublevels, request.ownerSublevelId(), "owner");
            LoadedSubLevel targetLoaded = requireLoaded(loadedSublevels, request.targetSublevelId(), "target");
            BlockPos ownerWorldPos = LoadedSubLevelCoordinates.toGlobalBlockPos(
                    ownerLoaded,
                    request.ownerLocalPos()
            );
            BlockPos targetWorldPos = LoadedSubLevelCoordinates.toGlobalBlockPos(
                    targetLoaded,
                    request.targetLocalPos()
            );
            BlockEntity ownerBlockEntity = level.getBlockEntity(ownerWorldPos);
            if (ownerBlockEntity == null || !companionType.isInstance(ownerBlockEntity)) {
                throw new IOException(
                        "Synaxis companion owner block entity is missing at " + ownerWorldPos
                );
            }

            Method connectionIntentMethod;
            UUID expectedRuntimeSublevelId = null;
            if (request.expectedSublevelId() == null) {
                if (withoutExpected == null) {
                    withoutExpected = SynaxisReflection.requirePublicMethod(
                            companionType,
                            "setConnectionIntent",
                            BlockPos.class,
                            Direction.class,
                            Direction.class
                    );
                }
                connectionIntentMethod = withoutExpected;
            } else {
                if (withExpected == null) {
                    withExpected = SynaxisReflection.requirePublicMethod(
                            companionType,
                            "setConnectionIntent",
                            BlockPos.class,
                            Direction.class,
                            Direction.class,
                            UUID.class
                    );
                }
                LoadedSubLevel expectedLoaded = requireLoaded(
                        loadedSublevels,
                        request.expectedSublevelId(),
                        "expected companion"
                );
                connectionIntentMethod = withExpected;
                expectedRuntimeSublevelId = expectedLoaded.subLevel().getUniqueId();
            }
            invokeConnectionIntent(
                    connectionIntentMethod,
                    ownerBlockEntity,
                    targetWorldPos,
                    request,
                    expectedRuntimeSublevelId
            );
            ownerBlockEntity.setChanged();
            BlockState state = level.getBlockState(ownerWorldPos);
            level.sendBlockUpdated(ownerWorldPos, state, state, 3);
            restored++;
        }
        AeronauticsStructureToolMod.LOGGER.info(
                "Synaxis companion restore for '{}': requested={} restored={}",
                blueprintName,
                requests.size(),
                restored
        );
    }

    static void invokeConnectionIntent(
            Method method,
            Object owner,
            BlockPos target,
            SynaxisBlueprintNbt.CompanionJoint request,
            UUID expectedCompanionUuid
    ) throws IOException {
        // Synaxis's public API takes alignment before companion direction.
        if (expectedCompanionUuid == null) {
            SynaxisReflection.invokeMethod(
                    method,
                    owner,
                    target,
                    request.alignmentDirection(),
                    request.direction()
            );
            return;
        }
        SynaxisReflection.invokeMethod(
                method,
                owner,
                target,
                request.alignmentDirection(),
                request.direction(),
                expectedCompanionUuid
        );
    }

    private static LoadedSubLevel requireLoaded(
            Map<UUID, LoadedSubLevel> loadedSublevels,
            UUID id,
            String role
    ) throws IOException {
        LoadedSubLevel loaded = loadedSublevels.get(id);
        if (loaded == null) {
            throw new IOException("Synaxis companion " + role + " references missing sublevel " + id);
        }
        return loaded;
    }

    private static CapturedSubLevel findCaptured(CapturePlan plan, BlockPos worldPos) {
        for (CapturedSubLevel captured : plan.sublevels()) {
            if (PlotBlockTransform.capture(captured.subLevel()).containsPlotAbsolute(worldPos)) {
                return captured;
            }
        }
        return null;
    }
}
