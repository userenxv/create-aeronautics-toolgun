package com.enxv.aeronauticsstructuretool.blueprint.geometry;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

public final class FlexibleBlockPosCodec {
    private FlexibleBlockPosCodec() {
    }

    public static BlockPos readRequired(CompoundTag parent, String key, String owner) {
        if (!parent.contains(key)) {
            throw new IllegalArgumentException(owner + " is missing block position '" + key + "'");
        }
        Tag raw = parent.get(key);
        if (raw instanceof LongTag longTag) {
            return BlockPos.of(longTag.getAsLong());
        }
        if (raw instanceof IntArrayTag intArrayTag) {
            int[] values = intArrayTag.getAsIntArray();
            if (values.length == 3) {
                return new BlockPos(values[0], values[1], values[2]);
            }
            throw new IllegalArgumentException(owner + " has an invalid int-array position '" + key + "'");
        }
        if (raw instanceof CompoundTag) {
            return NbtUtils.readBlockPos(parent, key)
                    .orElseThrow(() -> new IllegalArgumentException(
                            owner + " has an invalid compound position '" + key + "'"
                    ));
        }
        throw new IllegalArgumentException(
                owner + " has unsupported block position type for '" + key + "': " + raw.getType().getName()
        );
    }

    public static void write(CompoundTag parent, String key, BlockPos pos, Encoding encoding) {
        if (encoding == Encoding.PACKED_LONG) {
            parent.putLong(key, pos.asLong());
        } else {
            parent.put(key, NbtUtils.writeBlockPos(pos));
        }
    }

    public enum Encoding {
        NBT_BLOCK_POS,
        PACKED_LONG
    }
}
