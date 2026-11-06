package com.enxv.aeronauticsstructuretool.compat.letitwalk;

import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockTransform;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturePlan;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.fml.ModList;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LetItWalkBlueprintCompat {
    private LetItWalkBlueprintCompat() {}

    /** Walkers pose their segments directly, so these links need not be physics constraints. */
    public static Map<UUID, Set<UUID>> connections(Collection<ServerSubLevel> sublevels) throws IOException {
        if (!ModList.get().isLoaded("let_it_walk")) return Map.of();
        Map<UUID, Set<UUID>> graph = new HashMap<>();
        for (ServerSubLevel sub : sublevels) {
            for (var holder : sub.getPlot().getLoadedChunks()) {
                for (BlockEntity be : holder.getChunk().getBlockEntities().values()) {
                    String id = String.valueOf(BlockEntityType.getKey(be.getType()));
                    if (!LetItWalkNbt.CORE.equals(id) && !LetItWalkNbt.JOINT.equals(id)) continue;
                    if (LetItWalkNbt.CORE.equals(id) && be instanceof BlockEntitySubLevelActor actor) {
                        // Use the public dependency API here: collection runs during capture progress
                        // too, and must not serialize every leg's IK state on every check.
                        var dependencies = actor.sable$getLoadingDependencies();
                        if (dependencies != null) for (var other : dependencies) {
                            if (other != null) connect(graph, sub.getUniqueId(), other.getUniqueId());
                        }
                    } else if (LetItWalkNbt.JOINT.equals(id)) {
                        CompoundTag tag = be.saveWithoutMetadata(sub.getLevel().registryAccess());
                        if (tag.hasUUID("child")) connect(graph, sub.getUniqueId(), tag.getUUID("child"));
                    }
                }
            }
        }
        return graph;
    }

    private static void connect(Map<UUID, Set<UUID>> graph, UUID first, UUID second) {
        if (first.equals(second)) return;
        graph.computeIfAbsent(first, key -> new HashSet<>()).add(second);
        graph.computeIfAbsent(second, key -> new HashSet<>()).add(first);
    }

    public static void validateCapture(CapturePlan plan) throws IOException {
        if (!ModList.get().isLoaded("let_it_walk")) return;
        Set<UUID> included = new HashSet<>();
        for (var captured : plan.sublevels()) included.add(captured.subLevel().getUniqueId());
        for (var captured : plan.sublevels()) {
            ServerSubLevel sub = captured.subLevel();
            for (BlockEntity be : PlotBlockTransform.capture(sub).findBlockEntities(sub.getLevel())) {
                String id = String.valueOf(BlockEntityType.getKey(be.getType()));
                if (!LetItWalkNbt.CORE.equals(id) && !LetItWalkNbt.JOINT.equals(id)) continue;
                try {
                    LetItWalkNbt.validateReferences(id, be.saveWithoutMetadata(sub.getLevel().registryAccess()), included);
                } catch (IllegalArgumentException exception) {
                    throw new IOException("Cannot save incomplete walker at " + be.getBlockPos(), exception);
                }
            }
        }
    }
}
