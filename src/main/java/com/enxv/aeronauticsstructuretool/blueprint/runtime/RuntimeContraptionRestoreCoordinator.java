package com.enxv.aeronauticsstructuretool.blueprint.runtime;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.RuntimeContraptionBlueprint;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.LoadedSubLevelCoordinates;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import com.enxv.aeronauticsstructuretool.core.FailureMessages;
import com.enxv.aeronauticsstructuretool.server.BlueprintPlacementWarningNotifier;

import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

public final class RuntimeContraptionRestoreCoordinator {
    private static final int MAX_ATTEMPTS = 40;
    private static final int HARD_FREEZE_TICKS = 4;
    private static final int SOFT_RELEASE_TICKS = 18;

    private final List<PendingRestore> pending = new ArrayList<>();

    public boolean restoreOrQueue(
            ServerLevel level,
            Collection<LoadedSubLevel> loadedSublevels,
            UUID notificationPlayerId,
            String blueprintName
    ) throws IOException {
        return restoreOrQueue(level, loadedSublevels, notificationPlayerId, blueprintName, null);
    }

    public boolean restoreOrQueue(
            ServerLevel level,
            Collection<LoadedSubLevel> loadedSublevels,
            UUID notificationPlayerId,
            String blueprintName,
            BlueprintPlacementObserver observer
    ) throws IOException {
        PendingRestore restore = new PendingRestore(
                level,
                loadedSublevels,
                notificationPlayerId,
                blueprintName,
                observer
        );
        if (restore.tasks.isEmpty() && restore.subLevelIds.isEmpty()) {
            notifyCompleted(restore);
            return false;
        }

        restoreReadyContraptions(restore);

        AeronauticsStructureToolMod.LOGGER.debug(
                "Runtime restore queued: blueprint={} contraptions={} pending={} stabilizationTargets={}",
                restore.blueprintName,
                restore.originalCount,
                restore.tasks.size(),
                restore.subLevelIds
        );
        pending.add(restore);
        return true;
    }

    public void cancelForSublevels(Collection<UUID> subLevelIds) {
        if (subLevelIds == null || subLevelIds.isEmpty()) {
            return;
        }
        pending.removeIf(restore -> restore.subLevelIds.stream().anyMatch(subLevelIds::contains));
    }

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        Iterator<PendingRestore> iterator = pending.iterator();
        while (iterator.hasNext()) {
            PendingRestore restore = iterator.next();
            if (restore.level.getServer() != event.getServer()) {
                continue;
            }

            applySpawnStabilization(restore);
            restore.attempts++;
            restoreReadyContraptions(restore);

            if (restore.tasks.isEmpty() && stabilizationFinished(restore)) {
                AeronauticsStructureToolMod.LOGGER.debug(
                        "Runtime restore completed: blueprint={} restored={}",
                        restore.blueprintName,
                        restore.restoredCount
                );
                notifyCompleted(restore);
                iterator.remove();
                continue;
            }

            if (restore.attempts >= MAX_ATTEMPTS && stabilizationFinished(restore)) {
                String reason = "runtime controller was not ready after " + restore.attempts
                        + " attempts: " + restore.lastRetryMessage;
                for (RestoreTask task : List.copyOf(restore.tasks)) {
                    skipRuntimeEntity(restore, task, reason, null);
                }
                restore.tasks.clear();
                notifyCompleted(restore);
                iterator.remove();
            }
        }
    }

    private static void restoreReadyContraptions(PendingRestore restore) {
        Iterator<RestoreTask> iterator = restore.tasks.iterator();
        while (iterator.hasNext()) {
            RestoreTask task = iterator.next();
            RuntimeContraptionRestoreResult result = RuntimeContraptionRestoreService.restore(
                    task.blueprint,
                    restore.level,
                    task.loadedSubLevel
            );
            if (result.succeeded()) {
                restore.restoredCount++;
                iterator.remove();
                continue;
            }
            if (result.retryable()) {
                restore.lastRetryMessage = result.message();
                continue;
            }
            iterator.remove();
            skipRuntimeEntity(restore, task, result.message(), result.cause());
        }
    }

    private static void skipRuntimeEntity(
            PendingRestore restore,
            RestoreTask task,
            String reason,
            Throwable cause
    ) {
        BlockPos controllerPos = LoadedSubLevelCoordinates.toGlobalBlockPos(
                task.loadedSubLevel(),
                task.blueprint().controllerLocalPos()
        );
        RuntimeContraptionEntities.purgeForController(restore.level, controllerPos);
        String entityName = task.blueprint().entityClassName();
        if (entityName == null || entityName.isBlank()) {
            entityName = task.blueprint().kind();
        }
        String detail = FailureMessages.describe(
                cause == null ? new IOException(reason) : new IOException(reason, cause),
                "runtime entity restoration failed"
        );
        BlueprintPlacementWarningNotifier.notifySkippedRuntimeEntity(
                restore.level,
                restore.notificationPlayerId,
                entityName,
                controllerPos,
                detail
        );
        if (cause == null) {
            AeronauticsStructureToolMod.LOGGER.error(
                    "Skipped runtime entity '{}' at {} in blueprint '{}': {}",
                    entityName,
                    controllerPos,
                    restore.blueprintName,
                    detail
            );
            return;
        }
        AeronauticsStructureToolMod.LOGGER.error(
                "Skipped runtime entity '{}' at {} in blueprint '{}': {}",
                entityName,
                controllerPos,
                restore.blueprintName,
                detail,
                cause
        );
    }

    private static void notifyCompleted(PendingRestore restore) {
        if (restore.observer == null) {
            return;
        }
        try {
            restore.observer.onCompleted();
        } catch (RuntimeException exception) {
            AeronauticsStructureToolMod.LOGGER.error(
                    "Blueprint completion observer failed for '{}'",
                    restore.blueprintName,
                    exception
            );
        }
    }

    private static boolean stabilizationFinished(PendingRestore restore) {
        return restore.stabilizationTicks >= HARD_FREEZE_TICKS + SOFT_RELEASE_TICKS;
    }

    private static void applySpawnStabilization(PendingRestore restore) {
        if (stabilizationFinished(restore)) {
            return;
        }
        if (!(SubLevelContainer.getContainer(restore.level) instanceof ServerSubLevelContainer container)) {
            AeronauticsStructureToolMod.LOGGER.error(
                    "Sable container unavailable while stabilizing blueprint '{}'",
                    restore.blueprintName
            );
            restore.stabilizationTicks++;
            return;
        }

        double dampingFactor = computeDampingFactor(restore.stabilizationTicks);
        if (dampingFactor <= 1.0E-4D) {
            restore.stabilizationTicks++;
            return;
        }

        Vector3d angularVelocity = new Vector3d();
        Vector3d linearVelocity = new Vector3d();
        for (UUID subLevelId : restore.subLevelIds) {
            if (!(container.getSubLevel(subLevelId) instanceof ServerSubLevel serverSubLevel)) {
                continue;
            }

            RigidBodyHandle rigidBodyHandle = container.physicsSystem().getPhysicsHandle(serverSubLevel);
            rigidBodyHandle.getAngularVelocity(angularVelocity).negate().mul(dampingFactor);
            rigidBodyHandle.getLinearVelocity(linearVelocity).negate().mul(dampingFactor);
            serverSubLevel.logicalPose().orientation().transformInverse(angularVelocity);
            serverSubLevel.logicalPose().orientation().transformInverse(linearVelocity);

            ForceTotal forceTotal = new ForceTotal();
            forceTotal.applyLinearImpulse(new Vector3d(linearVelocity).mul(serverSubLevel.getMassTracker().getMass()));
            forceTotal.applyTorqueImpulse(serverSubLevel.getMassTracker().getInertiaTensor().transform(new Vector3d(angularVelocity)));
            rigidBodyHandle.applyForcesAndReset(forceTotal);
        }
        restore.stabilizationTicks++;
    }

    private static double computeDampingFactor(int stabilizationTicks) {
        if (stabilizationTicks < HARD_FREEZE_TICKS) {
            return 1.0D;
        }
        double releaseProgress = (stabilizationTicks - HARD_FREEZE_TICKS) / (double) SOFT_RELEASE_TICKS;
        double clamped = Math.max(0.0D, Math.min(1.0D, releaseProgress));
        double fade = 1.0D - (clamped * clamped * (3.0D - 2.0D * clamped));
        return fade * 0.88D;
    }

    private record RestoreTask(LoadedSubLevel loadedSubLevel, RuntimeContraptionBlueprint blueprint) {
    }

    private static final class PendingRestore {
        private final ServerLevel level;
        private final List<LoadedSubLevel> loadedSublevels;
        private final List<RestoreTask> tasks;
        private final LinkedHashSet<UUID> subLevelIds;
        private final UUID notificationPlayerId;
        private final String blueprintName;
        private final BlueprintPlacementObserver observer;
        private final int originalCount;
        private int attempts;
        private int stabilizationTicks;
        private int restoredCount;
        private String lastRetryMessage = "controller block entity is not ready";

        private PendingRestore(
                ServerLevel level,
                Collection<LoadedSubLevel> loadedSublevels,
                UUID notificationPlayerId,
                String blueprintName,
                BlueprintPlacementObserver observer
        ) {
            this.level = level;
            this.loadedSublevels = List.copyOf(loadedSublevels);
            this.tasks = new ArrayList<>();
            this.subLevelIds = new LinkedHashSet<>();
            this.notificationPlayerId = notificationPlayerId;
            this.blueprintName = blueprintName == null || blueprintName.isBlank() ? "blueprint" : blueprintName;
            this.observer = observer;
            for (LoadedSubLevel loaded : this.loadedSublevels) {
                subLevelIds.add(loaded.subLevel().getUniqueId());
                for (RuntimeContraptionBlueprint blueprint : loaded.saved().runtimeContraptions()) {
                    tasks.add(new RestoreTask(loaded, blueprint));
                }
            }
            this.originalCount = tasks.size();
        }
    }
}
