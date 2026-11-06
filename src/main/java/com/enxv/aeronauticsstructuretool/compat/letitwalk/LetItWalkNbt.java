package com.enxv.aeronauticsstructuretool.compat.letitwalk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;

/** Only Let it Walk's persisted references; pose/IK data is owned by its Sable schematic hooks. */
public final class LetItWalkNbt {
    public static final String CORE = "let_it_walk:walker_core";
    public static final String JOINT = "let_it_walk:leg_joint";
    public static final String CONTROLS = "let_it_walk:pilot_controls";
    public static final String MOUNT = "let_it_walk:jet_mount";
    // Consumed by the core read hook after WalkerCopy.freshCopy removes the old walkerId.
    public static final String COPY_ID = "AST_LetItWalkCopyId";

    private LetItWalkNbt() {}

    public static Set<UUID> references(String id, CompoundTag tag) {
        Set<UUID> references = new LinkedHashSet<>();
        if (JOINT.equals(id) && tag.contains("child")) references.add(uuid(tag, "child"));
        if (!CORE.equals(id)) return references;
        for (Tag entry : compounds(tag, "legs")) {
            CompoundTag leg = (CompoundTag) entry;
            if (!leg.contains("built", Tag.TAG_COMPOUND)) throw invalid("leg has no built data");
            CompoundTag built = leg.getCompound("built");
            ListTag joints = compounds(built, "joints");
            ListTag segments = compounds(built, "segments");
            ListTag rest = compounds(built, "rest");
            if (joints.isEmpty() || joints.size() != segments.size() || rest.size() != joints.size()
                    || built.getCompound("segOffsets").getInt("n") != segments.size()) {
                throw invalid("leg chain is incomplete");
            }
            for (Tag joint : joints) {
                references.add(uuid((CompoundTag) joint, "parent"));
                references.add(uuid((CompoundTag) joint, "child"));
            }
            for (Tag segment : segments) references.add(uuid((CompoundTag) segment, "id"));
        }
        return references;
    }

    public static void validateReferences(String id, CompoundTag tag, Set<UUID> included) {
        for (UUID reference : references(id, tag)) {
            if (!included.contains(reference)) throw invalid("missing walker body " + reference
                    + "; save the complete walker again");
        }
    }

    /** The mod's schematic hook translates Plot origins; world-height relocation is ours. */
    static void shiftHeight(CompoundTag tag, ToIntFunction<UUID> shift) {
        for (Tag entry : compounds(tag, "legs")) {
            CompoundTag built = ((CompoundTag) entry).getCompound("built");
            for (Tag joint : compounds(built, "joints")) {
                CompoundTag j = (CompoundTag) joint;
                j.putDouble("ppy", j.getDouble("ppy") + shift.applyAsInt(uuid(j, "parent")));
                j.putDouble("cpy", j.getDouble("cpy") + shift.applyAsInt(uuid(j, "child")));
            }
            ListTag segments = compounds(built, "segments");
            CompoundTag offsets = built.getCompound("segOffsets");
            for (int i = 0; i < segments.size(); i++) {
                int dy = shift.applyAsInt(uuid(segments.getCompound(i), "id"));
                String key = "o" + i + "y";
                offsets.putDouble(key, offsets.getDouble(key) - dy);
                if (i == segments.size() - 1) built.putDouble("footPloty", built.getDouble("footPloty") + dy);
            }
        }
    }

    static ListTag compounds(CompoundTag tag, String key) {
        if (!tag.contains(key)) return new ListTag();
        if (!(tag.get(key) instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            throw invalid("invalid list " + key);
        }
        return list;
    }

    static UUID uuid(CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) throw invalid("invalid UUID " + key);
        return tag.getUUID(key);
    }

    static IllegalArgumentException invalid(String reason) {
        return new IllegalArgumentException("Let it Walk!: " + reason);
    }
}
