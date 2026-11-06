package com.enxv.aeronauticsstructuretool.compat.letitwalk;

import com.enxv.aeronauticsstructuretool.blueprint.compat.BlockEntityCompatibilityPipeline;
import com.enxv.aeronauticsstructuretool.blueprint.model.SavedSubLevelBlueprint;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import com.enxv.aeronauticsstructuretool.blueprint.placement.BlueprintPlacementDiagnostics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Per-placement identities: no global cache and no references back to the source walker. */
public final class LetItWalkPlacement {
    private final Map<UUID, UUID> walkerIds = new HashMap<>();
    private boolean warnedExternal;

    public LetItWalkPlacement(List<SavedSubLevelBlueprint> saved) throws IOException {
        Set<UUID> bodies = new HashSet<>();
        for (var sub : saved) bodies.add(sub.blueprintId());
        try {
            for (var sub : saved) BlockEntityCompatibilityPipeline.forEachBlockEntity(sub.plotTag(), tag -> {
                String id = tag.getString("id");
                LetItWalkNbt.validateReferences(id, tag, bodies);
                if (LetItWalkNbt.CORE.equals(id) && tag.contains("walkerId")) {
                    UUID original = LetItWalkNbt.uuid(tag, "walkerId");
                    if (walkerIds.putIfAbsent(original, UUID.randomUUID()) != null) {
                        throw LetItWalkNbt.invalid("duplicate walker identity " + original);
                    }
                }
            });
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid Let it Walk! blueprint: " + exception.getMessage(), exception);
        }
    }

    public void preparePlot(CompoundTag plot, BlueprintPlacementDiagnostics diagnostics,
                            Map<UUID, LoadedSubLevel> loaded) {
        BlockEntityCompatibilityPipeline.forEachBlockEntity(plot, tag -> {
            if (LetItWalkNbt.CORE.equals(tag.getString("id"))) {
                LetItWalkNbt.shiftHeight(tag, id -> loaded.get(id).verticalLayout().blockYShift());
            }
            prepare(tag, diagnostics);
        });
        remapStoredControls(plot, diagnostics);
    }

    void remapStoredControls(CompoundTag plot, BlueprintPlacementDiagnostics diagnostics) {
        // PilotControlsItem stores its binding in BLOCK_ENTITY_DATA, including in nested inventories.
        // Traverse containers iteratively; never rewrite arbitrary UUIDs or other mods' components.
        ArrayDeque<Tag> pending = new ArrayDeque<>();
        pending.add(plot);
        while (!pending.isEmpty()) {
            Tag next = pending.removeLast();
            if (next instanceof CompoundTag compound) {
                if (LetItWalkNbt.CONTROLS.equals(compound.getString("id")) && compound.contains("components")) {
                    remapBinding(compound.getCompound("components").getCompound("minecraft:block_entity_data"), diagnostics);
                }
                for (String key : compound.getAllKeys()) {
                    Tag child = compound.get(key);
                    if (child instanceof CompoundTag || child instanceof ListTag) pending.add(child);
                }
            } else if (next instanceof ListTag list) {
                for (Tag child : list) if (child instanceof CompoundTag || child instanceof ListTag) pending.add(child);
            }
        }
    }

    void prepare(CompoundTag tag, BlueprintPlacementDiagnostics diagnostics) {
        String id = tag.getString("id");
        if (LetItWalkNbt.CORE.equals(id) && tag.hasUUID("walkerId")) {
            tag.putUUID(LetItWalkNbt.COPY_ID, walkerIds.get(tag.getUUID("walkerId")));
        } else if (LetItWalkNbt.CONTROLS.equals(id) && tag.contains("linkedWalker")) {
            remapBinding(tag, diagnostics);
        } else if (LetItWalkNbt.MOUNT.equals(id)) {
            // Grabs and firing deadlines refer to live constraints/world time, not a saved build.
            tag.putBoolean("gripping", false);
            tag.putLong("firing", Long.MIN_VALUE);
        }
    }

    private void remapBinding(CompoundTag tag, BlueprintPlacementDiagnostics diagnostics) {
        if (tag.contains("linkedWalker")) {
            UUID mapped = walkerIds.get(LetItWalkNbt.uuid(tag, "linkedWalker"));
            if (mapped == null) {
                tag.remove("linkedWalker");
                if (!warnedExternal) {
                    diagnostics.warn("Let it Walk!", "External pilot-control bindings were cleared; their walkers are not in this blueprint.");
                    warnedExternal = true;
                }
            } else tag.putUUID("linkedWalker", mapped);
        }
    }
}
