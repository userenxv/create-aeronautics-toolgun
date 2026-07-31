package com.enxv.aeronauticsstructuretool.server;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SubLevelCollisionToggleManager {
    private static final String USER_TAG_DISABLE_COLLISION = "AstDisableStructureCollision";
    private static final int DISCOVERY_INTERVAL_TICKS = 40;

    private final Map<UUID, ActiveCollisionState> activeStates = new HashMap<>();
    private int discoveryCooldown;

    public boolean toggle(ServerLevel level, ServerSubLevel subLevel) {
        boolean disableCollision = !isCollisionDisabled(subLevel);
        if (disableCollision) {
            applyDisabledCollisionState(level, subLevel);
        } else {
            ActiveCollisionState state = activeStates.get(subLevel.getUniqueId());
            restoreLoadedSections(requirePipeline(level), state, subLevel);
            setCollisionDisabled(subLevel, false);
            activeStates.remove(subLevel.getUniqueId());
        }
        return disableCollision;
    }

    public void applyDisabledCollisionState(ServerLevel level, ServerSubLevel subLevel) {
        PhysicsPipeline pipeline = requirePipeline(level);
        ActiveCollisionState state = activeStates.computeIfAbsent(subLevel.getUniqueId(), ignored -> new ActiveCollisionState(level, subLevel.getUniqueId()));
        state.level = level;
        state.removedSections.clear();
        suppressLoadedSections(pipeline, state, subLevel);
        setCollisionDisabled(subLevel, true);
    }

    public static boolean isCollisionDisabled(ServerSubLevel subLevel) {
        CompoundTag userData = subLevel.getUserDataTag();
        return userData != null && userData.getBoolean(USER_TAG_DISABLE_COLLISION);
    }

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        if (--discoveryCooldown <= 0) {
            discoverPersistedDisabledSubLevels(event);
            discoveryCooldown = DISCOVERY_INTERVAL_TICKS;
        }
        reconcileActiveStates();
    }

    private void discoverPersistedDisabledSubLevels(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!(SubLevelContainer.getContainer(level) instanceof ServerSubLevelContainer container)) {
                continue;
            }
            for (ServerSubLevel subLevel : container.getAllSubLevels()) {
                if (subLevel != null && isCollisionDisabled(subLevel)) {
                    ActiveCollisionState state = activeStates.computeIfAbsent(subLevel.getUniqueId(), ignored -> new ActiveCollisionState(level, subLevel.getUniqueId()));
                    state.level = level;
                    try {
                        suppressLoadedSections(requirePipeline(level), state, subLevel);
                    } catch (RuntimeException exception) {
                        AeronauticsStructureToolMod.LOGGER.error(
                                "Failed to reapply disabled collision state for sublevel {}",
                                subLevel.getUniqueId(),
                                exception
                        );
                    }
                }
            }
        }
    }

    private void reconcileActiveStates() {
        Iterator<Map.Entry<UUID, ActiveCollisionState>> iterator = activeStates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveCollisionState> entry = iterator.next();
            ActiveCollisionState state = entry.getValue();
            if (!(SubLevelContainer.getContainer(state.level) instanceof ServerSubLevelContainer container)) {
                AeronauticsStructureToolMod.LOGGER.warn(
                        "Discarded collision state for sublevel {} because its Sable container is unavailable",
                        state.subLevelId
                );
                iterator.remove();
                continue;
            }
            if (!(container.getSubLevel(state.subLevelId) instanceof ServerSubLevel subLevel)) {
                iterator.remove();
                continue;
            }
            if (!isCollisionDisabled(subLevel)) {
                try {
                    restoreLoadedSections(requirePipeline(state.level), state, subLevel);
                    iterator.remove();
                } catch (RuntimeException exception) {
                    AeronauticsStructureToolMod.LOGGER.error(
                            "Failed to restore collision sections for sublevel {}",
                            subLevel.getUniqueId(),
                            exception
                    );
                }
            }
        }
    }

    private static void setCollisionDisabled(ServerSubLevel subLevel, boolean disabled) {
        CompoundTag userData = subLevel.getUserDataTag();
        if (userData == null) {
            userData = new CompoundTag();
        }
        userData.putBoolean(USER_TAG_DISABLE_COLLISION, disabled);
        subLevel.setUserDataTag(userData);
    }

    private static void suppressLoadedSections(
            PhysicsPipeline pipeline,
            ActiveCollisionState state,
            ServerSubLevel subLevel
    ) {
        LevelPlot plot = subLevel.getPlot();
        int removedCount = 0;
        for (PlotChunkHolder chunkHolder : plot.getLoadedChunks()) {
            LevelChunk chunk = chunkHolder.getChunk();
            ChunkPos globalChunkPos = chunkHolder.getPos();
            LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < chunk.getSectionsCount(); i++) {
                LevelChunkSection section = sections[i];
                if (section == null || section.hasOnlyAir()) {
                    continue;
                }
                int sectionY = chunk.getSectionYFromSectionIndex(i);
                long sectionKey = SectionPos.asLong(globalChunkPos.x, sectionY, globalChunkPos.z);
                if (!state.removedSections.contains(sectionKey)) {
                    pipeline.handleChunkSectionRemoval(globalChunkPos.x, sectionY, globalChunkPos.z);
                    state.removedSections.add(sectionKey);
                    removedCount++;
                }
            }
        }
        if (removedCount > 0) {
            pipeline.onStatsChanged(subLevel);
            AeronauticsStructureToolMod.LOGGER.info(
                    "Toolgun no-collision disabled for sublevel {}: removed {} collision sections",
                    subLevel.getUniqueId(),
                    removedCount
            );
        }
    }

    private static void restoreLoadedSections(
            PhysicsPipeline pipeline,
            ActiveCollisionState state,
            ServerSubLevel subLevel
    ) {
        if (state == null) {
            return;
        }
        LevelPlot plot = subLevel.getPlot();
        int restoredCount = 0;
        for (PlotChunkHolder chunkHolder : plot.getLoadedChunks()) {
            LevelChunk chunk = chunkHolder.getChunk();
            ChunkPos globalChunkPos = chunkHolder.getPos();
            LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < chunk.getSectionsCount(); i++) {
                LevelChunkSection section = sections[i];
                if (section == null || section.hasOnlyAir()) {
                    continue;
                }
                int sectionY = chunk.getSectionYFromSectionIndex(i);
                long sectionKey = SectionPos.asLong(globalChunkPos.x, sectionY, globalChunkPos.z);
                if (state.removedSections.contains(sectionKey)) {
                    pipeline.handleChunkSectionAddition(section, globalChunkPos.x, sectionY, globalChunkPos.z, true);
                    state.removedSections.remove(sectionKey);
                    restoredCount++;
                }
            }
        }
        state.removedSections.clear();
        if (restoredCount > 0) {
            pipeline.onStatsChanged(subLevel);
            AeronauticsStructureToolMod.LOGGER.info(
                    "Toolgun no-collision restored for sublevel {}: restored {} collision sections",
                    subLevel.getUniqueId(),
                    restoredCount
            );
        }
    }

    private static PhysicsPipeline requirePipeline(ServerLevel level) {
        if (!(SubLevelContainer.getContainer(level) instanceof ServerSubLevelContainer container)) {
            throw new IllegalStateException(
                    "Sable physics container is unavailable for " + level.dimension().location()
            );
        }
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        if (pipeline == null) {
            throw new IllegalStateException(
                    "Sable physics pipeline is unavailable for " + level.dimension().location()
            );
        }
        return pipeline;
    }

    private static final class ActiveCollisionState {
        private ServerLevel level;
        private final UUID subLevelId;
        private final Set<Long> removedSections = new HashSet<>();

        private ActiveCollisionState(ServerLevel level, UUID subLevelId) {
            this.level = level;
            this.subLevelId = subLevelId;
        }
    }
}
