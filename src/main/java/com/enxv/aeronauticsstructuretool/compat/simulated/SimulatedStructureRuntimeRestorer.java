package com.enxv.aeronauticsstructuretool.compat.simulated;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;

import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SimulatedStructureRuntimeRestorer {
    private static final String ROPE_HOLDER_CLASS =
            "dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBlockEntity";
    private static final String ROPE_MANAGER_CLASS =
            "dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager";
    private static final String ROPE_STRAND_CLASS =
            "dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand";
    private static final String ROPE_ATTACHMENT_POINT_CLASS =
            "dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint";
    private static final String ROPE_ATTACHMENT_CLASS =
            "dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment";

    private SimulatedStructureRuntimeRestorer() {
    }

    static void repairSwivelPlate(BlockEntity blockEntity) {
        try {
            Method repair = blockEntity.getClass().getMethod("fixParentLinkingWhenMoved");
            repair.invoke(blockEntity);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "failed to repair Simulated swivel plate at " + blockEntity.getBlockPos(),
                    exception
            );
        }
    }

    static void refreshRopeAttachments(
            ServerLevel level,
            Map<UUID, LoadedSubLevel> loadedSublevels
    ) {
        Class<?> holderClass = SimulatedReflectionBridge.findOptionalClass(
                ROPE_HOLDER_CLASS,
                "Simulated rope holder class"
        );
        if (holderClass == null) {
            return;
        }

        Map<UUID, RopeHolderRef> owners = new LinkedHashMap<>();
        Map<UUID, RopeHolderRef> attached = new LinkedHashMap<>();
        for (LoadedSubLevel loaded : loadedSublevels.values()) {
            UUID subLevelId = loaded.subLevel().getUniqueId();
            for (BlockEntity blockEntity : loaded.transform().findBlockEntities(level)) {
                if (!holderClass.isInstance(blockEntity)) {
                    continue;
                }
                Object behavior = SimulatedReflectionBridge.invokeRequired(blockEntity, "getBehavior");
                if (!SimulatedReflectionBridge.invokeRequiredBoolean(behavior, "isAttached")) {
                    continue;
                }
                if (SimulatedReflectionBridge.invokeRequiredBoolean(behavior, "ownsRope")) {
                    Object strand = requireValue(
                            SimulatedReflectionBridge.invokeRequired(behavior, "getOwnedStrand"),
                            "owned rope strand"
                    );
                    UUID ropeId = requireUuidValue(
                            SimulatedReflectionBridge.invokeRequired(strand, "getUUID"),
                            "owned rope UUID"
                    );
                    registerRopeStrand(level, strand, ropeId);
                    putUnique(
                            owners,
                            ropeId,
                            new RopeHolderRef(subLevelId, blockEntity, behavior, strand),
                            "owner"
                    );
                } else {
                    UUID ropeId = readAttachedRopeId(behavior);
                    putUnique(
                            attached,
                            ropeId,
                            new RopeHolderRef(subLevelId, blockEntity, behavior, null),
                            "attached holder"
                    );
                }
            }
        }

        for (UUID ropeId : attached.keySet()) {
            if (!owners.containsKey(ropeId)) {
                throw new IllegalStateException(
                        "Simulated rope " + ropeId + " has an attached holder but no owner"
                );
            }
        }
        for (Map.Entry<UUID, RopeHolderRef> entry : owners.entrySet()) {
            UUID ropeId = entry.getKey();
            RopeHolderRef owner = entry.getValue();
            RopeHolderRef attachedHolder = attached.get(ropeId);
            updateRopeAttachment(
                    owner.strand(),
                    level,
                    "START",
                    owner.subLevelId(),
                    owner.blockEntity().getBlockPos()
            );
            if (attachedHolder != null) {
                updateRopeAttachment(
                        owner.strand(),
                        level,
                        "END",
                        attachedHolder.subLevelId(),
                        attachedHolder.blockEntity().getBlockPos()
                );
                rebuildRopePoints(level, owner, attachedHolder, ropeId);
                notifyBlockEntityUpdate(attachedHolder.blockEntity());
            }
            reattachRopeConstraints(owner.strand(), level, ropeId);
            notifyBlockEntityUpdate(owner.blockEntity());
        }
    }

    private static void rebuildRopePoints(
            ServerLevel level,
            RopeHolderRef owner,
            RopeHolderRef attached,
            UUID ropeId
    ) {
        Vec3 start = requireVec3(
                SimulatedReflectionBridge.invokeRequired(owner.behavior(), "getAttachmentPoint"),
                "rope owner attachment point"
        );
        Vec3 end = requireVec3(
                SimulatedReflectionBridge.invokeRequired(attached.behavior(), "getAttachmentPoint"),
                "rope end attachment point"
        );
        Vec3 projectedStart = Sable.HELPER.projectOutOfSubLevel(level, start);
        Vec3 projectedEnd = Sable.HELPER.projectOutOfSubLevel(level, end);
        replaceRopePoints(owner.strand(), projectedStart, projectedEnd, ropeId);
    }

    private static void replaceRopePoints(Object strand, Vec3 start, Vec3 end, UUID ropeId) {
        try {
            Field pointsField = SimulatedReflectionBridge.requireField(strand.getClass(), "points");
            Object pointsValue = pointsField.get(strand);
            if (!(pointsValue instanceof List<?> points)) {
                throw new IllegalStateException(
                        "Simulated rope points field is not a list for " + ropeId
                );
            }
            @SuppressWarnings("unchecked")
            List<Object> mutablePoints = (List<Object>) points;
            mutablePoints.clear();
            double distance = start.distanceTo(end);
            int wholeSegments = (int) Math.floor(distance);
            int pointCount = Math.max(1, wholeSegments + 1);
            double firstSegmentExtension = distance - wholeSegments;
            Vec3 direction = distance > 1.0E-6D
                    ? end.subtract(start).normalize()
                    : Vec3.ZERO;

            mutablePoints.add(new Vector3d(start.x, start.y, start.z));
            for (int i = 0; i < pointCount; i++) {
                double offset = i + firstSegmentExtension;
                Vec3 point = start.add(direction.scale(offset));
                mutablePoints.add(new Vector3d(point.x, point.y, point.z));
            }
            SimulatedReflectionBridge.invokeRequired(
                    strand,
                    "updateFirstSegmentExtension",
                    firstSegmentExtension
            );
            if (SimulatedReflectionBridge.invokeRequiredBoolean(strand, "isActive")) {
                SimulatedReflectionBridge.invokeRequired(strand, "updatePose");
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "failed to rebuild Simulated rope points " + ropeId,
                    exception
            );
        }
    }

    private static void registerRopeStrand(ServerLevel level, Object strand, UUID ropeId) {
        try {
            Class<?> managerClass = Class.forName(ROPE_MANAGER_CLASS);
            Object manager = requireValue(
                    managerClass.getMethod("getOrCreate", net.minecraft.world.level.Level.class)
                            .invoke(null, level),
                    "Simulated server rope manager"
            );
            Class<?> strandClass = Class.forName(ROPE_STRAND_CLASS);
            if (!strandClass.isInstance(strand)) {
                throw new IllegalStateException(
                        "incompatible Simulated rope strand " + strand.getClass().getName()
                );
            }
            managerClass.getMethod("addStrand", strandClass).invoke(manager, strand);
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException(
                    "failed to register Simulated rope strand " + ropeId,
                    exception
            );
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void updateRopeAttachment(
            Object strand,
            ServerLevel level,
            String attachmentPointName,
            UUID subLevelId,
            BlockPos blockPos
    ) {
        try {
            Class<?> pointClass = Class.forName(ROPE_ATTACHMENT_POINT_CLASS);
            Class<?> attachmentClass = Class.forName(ROPE_ATTACHMENT_CLASS);
            Object point = Enum.valueOf(
                    (Class<? extends Enum>) pointClass.asSubclass(Enum.class),
                    attachmentPointName
            );
            Object attachment = attachmentClass
                    .getConstructor(pointClass, UUID.class, BlockPos.class)
                    .newInstance(point, subLevelId, blockPos);
            strand.getClass()
                    .getMethod("addAttachment", ServerLevel.class, pointClass, attachmentClass)
                    .invoke(strand, level, point, attachment);
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException(
                    "failed to update Simulated rope attachment " + attachmentPointName,
                    exception
            );
        }
    }

    private static void reattachRopeConstraints(Object strand, ServerLevel level, UUID ropeId) {
        try {
            strand.getClass().getMethod("reattachConstraints", ServerLevel.class).invoke(strand, level);
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException(
                    "failed to reattach Simulated rope constraints " + ropeId,
                    exception
            );
        }
    }

    private static UUID readAttachedRopeId(Object behavior) {
        try {
            Object value = SimulatedReflectionBridge
                    .requireField(behavior.getClass(), "attachedRopeID")
                    .get(behavior);
            return requireUuidValue(value, "attached rope UUID");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("failed to read Simulated attached rope UUID", exception);
        }
    }

    private static void notifyBlockEntityUpdate(BlockEntity blockEntity) {
        try {
            Method notifyUpdate = SimulatedReflectionBridge.findMethod(
                    blockEntity.getClass(),
                    "notifyUpdate",
                    0
            );
            if (notifyUpdate == null) {
                blockEntity.setChanged();
            } else {
                notifyUpdate.invoke(blockEntity);
            }
        } catch (ReflectiveOperationException exception) {
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Simulated rope block entity update notification failed at {}; marking changed instead",
                    blockEntity.getBlockPos(),
                    exception
            );
            blockEntity.setChanged();
        }
    }

    private static UUID requireUuidValue(Object value, String label) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        throw new IllegalStateException(label + " is missing or has an incompatible type");
    }

    private static Vec3 requireVec3(Object value, String label) {
        if (value instanceof Vec3 point) {
            return point;
        }
        throw new IllegalStateException(label + " is missing or has an incompatible type");
    }

    private static Object requireValue(Object value, String label) {
        if (value == null) {
            throw new IllegalStateException(label + " is unavailable");
        }
        return value;
    }

    private static void putUnique(
            Map<UUID, RopeHolderRef> refs,
            UUID ropeId,
            RopeHolderRef ref,
            String role
    ) {
        RopeHolderRef previous = refs.putIfAbsent(ropeId, ref);
        if (previous != null) {
            throw new IllegalStateException(
                    "duplicate Simulated rope " + role + " for UUID " + ropeId
            );
        }
    }

    private record RopeHolderRef(
            UUID subLevelId,
            BlockEntity blockEntity,
            Object behavior,
            Object strand
    ) {
    }
}
