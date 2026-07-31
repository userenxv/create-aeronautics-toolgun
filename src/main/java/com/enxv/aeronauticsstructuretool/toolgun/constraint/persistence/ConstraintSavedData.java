package com.enxv.aeronauticsstructuretool.toolgun.constraint.persistence;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.enxv.aeronauticsstructuretool.toolgun.constraint.persistence.ConstraintNbtKeys.CONSTRAINTS_TAG;

public final class ConstraintSavedData extends SavedData {
    private static final String SAVE_ID = "create_aeronautics_toolgun_constraints";
    private final Map<UUID, PersistentConstraint> constraints = new LinkedHashMap<>();

    public static ConstraintSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), SAVE_ID);
    }

    private static Factory<ConstraintSavedData> factory() {
        return new Factory<>(ConstraintSavedData::new, ConstraintSavedData::load);
    }

    private static ConstraintSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ConstraintSavedData data = new ConstraintSavedData();
        if (!tag.contains(CONSTRAINTS_TAG)) {
            return data;
        }
        if (!(tag.get(CONSTRAINTS_TAG) instanceof ListTag list)) {
            AeronauticsStructureToolMod.LOGGER.error(
                    "Persistent toolgun constraint root '{}' is not a list; no constraints were loaded",
                    CONSTRAINTS_TAG
            );
            return data;
        }
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            AeronauticsStructureToolMod.LOGGER.error(
                    "Persistent toolgun constraint root '{}' does not contain compound entries; no constraints were loaded",
                    CONSTRAINTS_TAG
            );
            return data;
        }
        for (int i = 0; i < list.size(); i++) {
            try {
                PersistentConstraint constraint = ConstraintPersistentCodec.read(list.getCompound(i));
                PersistentConstraint previous = data.constraints.putIfAbsent(
                        constraint.constraintId(),
                        constraint
                );
                if (previous != null) {
                    AeronauticsStructureToolMod.LOGGER.error(
                            "Ignoring duplicate persistent toolgun constraint ID {} at entry {}",
                            constraint.constraintId(),
                            i
                    );
                }
            } catch (RuntimeException exception) {
                AeronauticsStructureToolMod.LOGGER.error(
                        "Skipping invalid persistent toolgun constraint entry {}",
                        i,
                        exception
                );
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (PersistentConstraint constraint : constraints.values()) {
            list.add(ConstraintPersistentCodec.write(constraint));
        }
        tag.put(CONSTRAINTS_TAG, list);
        return tag;
    }

    public PersistentConstraint put(PersistentConstraint constraint) {
        PersistentConstraint previous = constraints.put(constraint.constraintId(), constraint);
        try {
            setDirty();
            return previous;
        } catch (RuntimeException exception) {
            if (previous == null) {
                constraints.remove(constraint.constraintId());
            } else {
                constraints.put(previous.constraintId(), previous);
            }
            throw exception;
        }
    }

    public Set<UUID> removeForSubLevel(ServerLevel level, UUID subLevelId) {
        String dimensionId = level.dimension().location().toString();
        Set<UUID> removed = new HashSet<>();
        Iterator<Map.Entry<UUID, PersistentConstraint>> iterator = constraints.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PersistentConstraint> entry = iterator.next();
            PersistentConstraint constraint = entry.getValue();
            if (dimensionId.equals(constraint.dimensionId())
                    && (subLevelId.equals(constraint.firstSubLevelId())
                    || subLevelId.equals(constraint.secondSubLevelId()))) {
                removed.add(entry.getKey());
                iterator.remove();
            }
        }
        if (!removed.isEmpty()) {
            setDirty();
        }
        return removed;
    }

    public boolean hasConstraintForSubLevel(ServerLevel level, UUID subLevelId) {
        String dimensionId = level.dimension().location().toString();
        for (PersistentConstraint constraint : constraints.values()) {
            if (dimensionId.equals(constraint.dimensionId())
                    && (subLevelId.equals(constraint.firstSubLevelId())
                    || subLevelId.equals(constraint.secondSubLevelId()))) {
                return true;
            }
        }
        return false;
    }

    public List<PersistentConstraint> constraintsFor(ServerLevel level) {
        String dimensionId = level.dimension().location().toString();
        List<PersistentConstraint> results = new ArrayList<>();
        for (PersistentConstraint constraint : constraints.values()) {
            if (dimensionId.equals(constraint.dimensionId())) {
                results.add(constraint);
            }
        }
        return List.copyOf(results);
    }
}
