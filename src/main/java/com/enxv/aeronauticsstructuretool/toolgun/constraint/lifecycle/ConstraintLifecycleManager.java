package com.enxv.aeronauticsstructuretool.toolgun.constraint.lifecycle;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.server.ConstraintVisualPublisher;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.persistence.ConstraintPersistenceService;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.persistence.ConstraintRestoreResult;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.persistence.PersistentConstraint;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.ConstraintRuntimeRepository;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.neoforge.event.ForgeSableSubLevelContainerReadyEvent;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class ConstraintLifecycleManager {
    private static final int MAX_RESTORE_ATTEMPTS = 30;
    private static final long INITIAL_RESTORE_DELAY_TICKS = 5L;
    private static final long RESTORE_RETRY_INTERVAL_TICKS = 5L;
    private static final long CHUNK_LIFECYCLE_CHECK_INTERVAL_TICKS = 10L;

    private final Map<ServerLevel, RestoreState> restoreStates = new WeakHashMap<>();
    private final Set<SubLevelContainer> observedContainers = Collections.newSetFromMap(new WeakHashMap<>());
    private final ConstraintChunkLifecycleMonitor chunkLifecycle = new ConstraintChunkLifecycleMonitor();

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        observe(level, SubLevelContainer.getContainer(level));
        restoreStates.put(level, RestoreState.active(level.getGameTime(), "level loaded"));
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        observedContainers.remove(SubLevelContainer.getContainer(level));
        restoreStates.remove(level);
        chunkLifecycle.clear(level);
        ConstraintRuntimeRepository.clearDimension(level.dimension().location().toString());
    }

    @SubscribeEvent
    public void onSubLevelContainerReady(ForgeSableSubLevelContainerReadyEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            observe(level, event.getContainer());
        }
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ConstraintRuntimeRepository.cleanupInvalid();
        List<PersistentConstraint> persisted = ConstraintPersistenceService.constraintsFor(level);
        RestoreState state = restoreStates.computeIfAbsent(
                level,
                ignored -> RestoreState.active(level.getGameTime(), "level first observed")
        );
        if (persisted.isEmpty()) {
            chunkLifecycle.clear(level);
            state.complete();
            return;
        }

        long now = level.getGameTime();
        if (state.shouldCheckChunks(now)) {
            state.markChunksChecked(now);
            ConstraintChunkLifecycleMonitor.ChangeSet chunkChanges = chunkLifecycle.collectChanges(level, persisted);
            Set<UUID> changedSubLevels = chunkChanges.changedSubLevels();
            if (!changedSubLevels.isEmpty()) {
                if (ConstraintRuntimeRepository.removeForSubLevels(
                        level.dimension().location().toString(),
                        changedSubLevels
                )) {
                    ConstraintVisualPublisher.sync(level);
                }
                if (!chunkChanges.availabilityImprovedSubLevels().isEmpty()) {
                    state.restart(
                            now,
                            "loaded chunk availability reached a new high for "
                                    + chunkChanges.availabilityImprovedSubLevels()
                    );
                } else {
                    state.wake(now, "loaded chunk set changed for " + changedSubLevels);
                }
            }
        }

        if (!ConstraintPersistenceService.hasMissingRuntimeConstraints(level)) {
            state.complete();
            return;
        }
        if (!state.active()) {
            state.wake(now, "a persisted constraint is missing at runtime");
        }
        if (!state.readyForAttempt(now)) {
            return;
        }

        state.markAttempt(now);
        ConstraintRestoreResult result = ConstraintPersistenceService.restoreMissing(level);
        if (result.restoredCount() > 0) {
            ConstraintVisualPublisher.sync(level);
            state.restart(now, "restored " + result.restoredCount() + " constraint(s)");
        }
        if (result.complete()) {
            state.complete();
            return;
        }
        if (state.attempts() >= MAX_RESTORE_ATTEMPTS) {
            state.suspend();
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Stopped retrying toolgun constraint restore in {} after {} attempts; "
                            + "retry will resume only after concrete sublevel/chunk availability progress; "
                            + "trigger='{}'; unresolved={}",
                    level.dimension().location(),
                    state.attempts(),
                    state.trigger(),
                    result.unresolved()
            );
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            ConstraintVisualPublisher.syncTo(player, level);
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            ConstraintVisualPublisher.syncTo(player, level);
        }
    }

    private void observe(ServerLevel level, SubLevelContainer container) {
        if (container != null && observedContainers.add(container)) {
            container.addObserver(new ConstraintSubLevelObserver(level));
        }
    }

    private final class ConstraintSubLevelObserver implements SubLevelObserver {
        private final ServerLevel level;

        private ConstraintSubLevelObserver(ServerLevel level) {
            this.level = level;
        }

        @Override
        public void onSubLevelAdded(SubLevel subLevel) {
            UUID subLevelId = subLevel.getUniqueId();
            if (!ConstraintPersistenceService.hasConstraintForSubLevel(level, subLevelId)) {
                return;
            }
            chunkLifecycle.forget(level, subLevelId);
            if (ConstraintRuntimeRepository.removeForSubLevels(
                    level.dimension().location().toString(),
                    List.of(subLevelId)
            )) {
                ConstraintVisualPublisher.sync(level);
            }
            restoreStates.computeIfAbsent(
                    level,
                    ignored -> RestoreState.inactive()
            ).restart(level.getGameTime(), "sublevel loaded: " + subLevelId);
        }

        @Override
        public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
            UUID subLevelId = subLevel.getUniqueId();
            chunkLifecycle.forget(level, subLevelId);
            if (reason == SubLevelRemovalReason.REMOVED) {
                int removed = ConstraintPersistenceService.removeForSubLevel(level, subLevelId).size();
                if (removed > 0) {
                    ConstraintVisualPublisher.sync(level);
                    AeronauticsStructureToolMod.LOGGER.info(
                            "Removed {} toolgun constraint(s) with deleted sublevel {} in {}",
                            removed,
                            subLevelId,
                            level.dimension().location()
                    );
                }
                return;
            }
            if (ConstraintRuntimeRepository.removeForSubLevels(
                    level.dimension().location().toString(),
                    List.of(subLevelId)
            )) {
                ConstraintVisualPublisher.sync(level);
            }
        }
    }

    private static final class RestoreState {
        private boolean active;
        private long firstSeenTick;
        private long lastAttemptTick = Long.MIN_VALUE;
        private long lastChunkCheckTick = Long.MIN_VALUE;
        private int attempts;
        private String trigger = "not armed";

        static RestoreState active(long now, String trigger) {
            RestoreState state = new RestoreState();
            state.restart(now, trigger);
            return state;
        }

        static RestoreState inactive() {
            return new RestoreState();
        }

        void restart(long now, String trigger) {
            this.active = true;
            this.firstSeenTick = now;
            this.lastAttemptTick = Long.MIN_VALUE;
            this.attempts = 0;
            this.trigger = trigger;
        }

        void wake(long now, String trigger) {
            if (this.attempts >= MAX_RESTORE_ATTEMPTS) {
                return;
            }
            this.active = true;
            this.firstSeenTick = now;
            this.lastAttemptTick = Long.MIN_VALUE;
            this.trigger = trigger;
        }

        boolean readyForAttempt(long now) {
            return active
                    && attempts < MAX_RESTORE_ATTEMPTS
                    && now - firstSeenTick >= INITIAL_RESTORE_DELAY_TICKS
                    && (lastAttemptTick == Long.MIN_VALUE
                    || now - lastAttemptTick >= RESTORE_RETRY_INTERVAL_TICKS);
        }

        boolean shouldCheckChunks(long now) {
            return lastChunkCheckTick == Long.MIN_VALUE
                    || now - lastChunkCheckTick >= CHUNK_LIFECYCLE_CHECK_INTERVAL_TICKS;
        }

        void markChunksChecked(long now) {
            this.lastChunkCheckTick = now;
        }

        void markAttempt(long now) {
            this.lastAttemptTick = now;
            this.attempts++;
        }

        void complete() {
            this.active = false;
            this.attempts = 0;
            this.trigger = "complete";
        }

        void suspend() {
            this.active = false;
        }

        boolean active() {
            return active;
        }

        int attempts() {
            return attempts;
        }

        String trigger() {
            return trigger;
        }
    }
}
