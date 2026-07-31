package com.enxv.aeronauticsstructuretool.blueprint.importer.vmod;

import com.enxv.aeronauticsstructuretool.blueprint.codec.BlueprintArchiveCodec;
import com.enxv.aeronauticsstructuretool.blueprint.codec.NativeBlueprintFormat;
import com.enxv.aeronauticsstructuretool.blueprint.codec.NbtTransformCodec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.Comparator;
import java.util.UUID;

public final class VModNativeBlueprintEncoder {
    private VModNativeBlueprintEncoder() {
    }

    public static byte[] importToNative(String name, byte[] rawBytes) throws IOException {
        if (name == null || name.isBlank()) {
            throw new IOException("empty VMod blueprint name");
        }
        VModSchematic schematic = VModSchematicReader.read(rawBytes);
        if (schematic.ships().isEmpty()) {
            throw new IOException("empty VMod schematic");
        }

        VModShip rootShip = chooseRootShip(schematic);
        Vector3d rootOffset = rootShip.info() == null
                ? new Vector3d()
                : new Vector3d(rootShip.info().relativePositionToCenter());
        Vector3d plotCenter = new Vector3d(
                VModPlotWriter.PLOT_CENTER_XZ,
                VModPlotWriter.PLOT_CENTER_Y,
                VModPlotWriter.PLOT_CENTER_XZ
        );

        ListTag sublevels = new ListTag();
        UUID rootSublevelId = null;
        int shipIndex = 1;
        for (VModShip ship : schematic.ships().values()) {
            if (ship.blocks().isEmpty()) {
                continue;
            }
            UUID sublevelId = UUID.randomUUID();
            if (ship == rootShip) {
                rootSublevelId = sublevelId;
            }
            Vector3d relativeOffset = ship.info() == null
                    ? new Vector3d()
                    : new Vector3d(ship.info().relativePositionToCenter()).sub(rootOffset);
            Vector3d placement = new Vector3d(plotCenter).add(relativeOffset);
            Quaterniond orientation = ship.info() == null
                    ? new Quaterniond()
                    : new Quaterniond(ship.info().rotation());

            CompoundTag sublevelTag = new CompoundTag();
            sublevelTag.putUUID(NativeBlueprintFormat.SUBLEVEL_ID_TAG, sublevelId);
            sublevelTag.putUUID(NativeBlueprintFormat.ORIGINAL_SUBLEVEL_ID_TAG, sublevelId);
            sublevelTag.putString(
                    NativeBlueprintFormat.NAME_TAG,
                    ship == rootShip ? name : name + " [" + shipIndex + "]"
            );
            sublevelTag.put(NativeBlueprintFormat.PLOT_TAG, VModPlotWriter.write(ship));
            sublevelTag.put(NativeBlueprintFormat.RUNTIME_CONTRAPTIONS_TAG, new ListTag());
            sublevelTag.put(
                    NativeBlueprintFormat.RELATIVE_POSITION_TAG,
                    NbtTransformCodec.writeVector(placement)
            );
            sublevelTag.put(
                    NativeBlueprintFormat.RELATIVE_ROTATION_OFFSET_TAG,
                    NbtTransformCodec.writeVector(placement)
            );
            sublevelTag.put(
                    NativeBlueprintFormat.RELATIVE_ORIENTATION_TAG,
                    NbtTransformCodec.writeQuaternion(orientation)
            );
            sublevels.add(sublevelTag);
            shipIndex++;
        }

        if (sublevels.isEmpty() || rootSublevelId == null) {
            throw new IOException("no supported VMod blocks");
        }

        CompoundTag root = new CompoundTag();
        root.putString(NativeBlueprintFormat.FORMAT_TAG, NativeBlueprintFormat.CURRENT_FORMAT);
        root.putString(NativeBlueprintFormat.NAME_TAG, name);
        root.putUUID(NativeBlueprintFormat.ROOT_SUBLEVEL_TAG, rootSublevelId);
        root.put(
                NativeBlueprintFormat.ROOT_ORIENTATION_TAG,
                NbtTransformCodec.writeQuaternion(new Quaterniond())
        );
        root.put(
                NativeBlueprintFormat.ROOT_ROTATION_OFFSET_TAG,
                NbtTransformCodec.writeVector(plotCenter)
        );
        root.put(NativeBlueprintFormat.SUBLEVELS_TAG, sublevels);
        return BlueprintArchiveCodec.encode(root);
    }

    private static VModShip chooseRootShip(VModSchematic schematic) throws IOException {
        return schematic.ships().values().stream()
                .filter(ship -> !ship.blocks().isEmpty())
                .min(Comparator.comparingDouble(ship -> ship.info() == null
                        ? 0.0D
                        : ship.info().relativePositionToCenter().lengthSquared()))
                .orElseThrow(() -> new IOException("VMod schematic contains no blocks"));
    }
}
