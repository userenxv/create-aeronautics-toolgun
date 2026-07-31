package com.enxv.aeronauticsstructuretool.blueprint.geometry;

import com.enxv.aeronauticsstructuretool.blueprint.codec.NbtTransformCodec;
import com.enxv.aeronauticsstructuretool.blueprint.codec.NativeBlueprintFormat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.UUID;

public final class BlueprintLocalAnchorResolver {
    public static final String LOCAL_ANCHOR_SPACE_TAG = "local_anchor_space";
    public static final String SAVED_PLOT_LOCAL_SPACE = "saved_plot_local_v1";

    private BlueprintLocalAnchorResolver() {
    }

    public static double resolveLegacyAnchorY(CompoundTag root) throws IOException {
        if (!root.hasUUID(NativeBlueprintFormat.ROOT_SUBLEVEL_TAG)) {
            throw new IOException("native blueprint is missing root_sublevel");
        }
        UUID rootId = root.getUUID(NativeBlueprintFormat.ROOT_SUBLEVEL_TAG);
        ListTag sublevels = root.getList(NativeBlueprintFormat.SUBLEVELS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < sublevels.size(); i++) {
            CompoundTag sublevel = sublevels.getCompound(i);
            if (!sublevel.hasUUID(NativeBlueprintFormat.SUBLEVEL_ID_TAG)
                    || !rootId.equals(sublevel.getUUID(NativeBlueprintFormat.SUBLEVEL_ID_TAG))) {
                continue;
            }
            Vector3d rootRelativePosition;
            try {
                rootRelativePosition = NbtTransformCodec.readVector(
                        sublevel.getCompound(NativeBlueprintFormat.RELATIVE_POSITION_TAG),
                        NativeBlueprintFormat.RELATIVE_POSITION_TAG
                );
            } catch (IllegalArgumentException exception) {
                throw new IOException("invalid native blueprint root relative_position", exception);
            }
            return rootRelativePosition.y;
        }
        throw new IOException("native blueprint root_sublevel does not reference a saved sublevel");
    }

    public static Vector3d resolve(
            CompoundTag sublevelTag,
            CompoundTag plotTag,
        double legacyAnchorY
    ) throws IOException {
        Vector3d chunkCenter = PlotBlockDataReader.plotChunkCenter(plotTag, legacyAnchorY);
        if (!sublevelTag.contains(NativeBlueprintFormat.LOCAL_ANCHOR_TAG)) {
            return new Vector3d(chunkCenter.x, legacyAnchorY, chunkCenter.z);
        }
        if (!sublevelTag.contains(NativeBlueprintFormat.LOCAL_ANCHOR_TAG, Tag.TAG_COMPOUND)) {
            throw new IOException("native blueprint local_anchor is not a compound");
        }

        Vector3d savedAnchor;
        try {
            savedAnchor = NbtTransformCodec.readVector(
                    sublevelTag.getCompound(NativeBlueprintFormat.LOCAL_ANCHOR_TAG),
                    NativeBlueprintFormat.LOCAL_ANCHOR_TAG
            );
        } catch (IllegalArgumentException exception) {
            throw new IOException("invalid native blueprint local_anchor", exception);
        }
        if (sublevelTag.contains(LOCAL_ANCHOR_SPACE_TAG)
                && !sublevelTag.contains(LOCAL_ANCHOR_SPACE_TAG, Tag.TAG_STRING)) {
            throw new IOException("native blueprint local_anchor_space is not a string");
        }
        String anchorSpace = sublevelTag.getString(LOCAL_ANCHOR_SPACE_TAG);
        if (SAVED_PLOT_LOCAL_SPACE.equals(anchorSpace)) {
            return savedAnchor;
        }
        if (!anchorSpace.isBlank()) {
            throw new IOException("unsupported native blueprint local_anchor_space: " + anchorSpace);
        }

        // Early local_anchor files stored runtime-global X/Z. Their Y is still the exact source plot anchor.
        return new Vector3d(chunkCenter.x, savedAnchor.y, chunkCenter.z);
    }

}
