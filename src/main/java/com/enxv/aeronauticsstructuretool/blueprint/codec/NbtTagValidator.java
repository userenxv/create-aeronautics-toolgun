package com.enxv.aeronauticsstructuretool.blueprint.codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;

public final class NbtTagValidator {
    private NbtTagValidator() {
    }

    public static ListTag requireCompoundList(
            CompoundTag parent,
            String key,
            String label,
            boolean nonEmpty
    ) throws IOException {
        Tag raw = parent.get(key);
        if (!(raw instanceof ListTag list)) {
            throw new IOException(label + " is not a list");
        }
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IOException(label + " does not contain compounds");
        }
        if (nonEmpty && list.isEmpty()) {
            throw new IOException(label + " is empty");
        }
        return list;
    }

    public static ListTag optionalCompoundList(
            CompoundTag parent,
            String key,
            String label
    ) throws IOException {
        return parent.contains(key)
                ? requireCompoundList(parent, key, label, false)
                : new ListTag();
    }

    public static CompoundTag requireCompound(
            CompoundTag parent,
            String key,
            String label,
            boolean nonEmpty
    ) throws IOException {
        if (!parent.contains(key, Tag.TAG_COMPOUND)) {
            throw new IOException(label + " is not a compound");
        }
        CompoundTag compound = parent.getCompound(key);
        if (nonEmpty && compound.isEmpty()) {
            throw new IOException(label + " is empty");
        }
        return compound;
    }

    public static void requireOptionalType(
            CompoundTag parent,
            String key,
            int tagType,
            String label
    ) throws IOException {
        if (parent.contains(key) && !parent.contains(key, tagType)) {
            throw new IOException(label + " has the wrong NBT type");
        }
    }

    public static void requireNumeric(CompoundTag parent, String key, String label) throws IOException {
        if (!parent.contains(key, Tag.TAG_ANY_NUMERIC)) {
            throw new IOException(label + " is missing numeric '" + key + "'");
        }
    }
}
