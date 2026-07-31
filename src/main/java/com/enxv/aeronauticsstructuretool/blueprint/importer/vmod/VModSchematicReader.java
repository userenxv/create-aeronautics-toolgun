package com.enxv.aeronauticsstructuretool.blueprint.importer.vmod;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.blueprint.codec.BlueprintArchiveCodec;
import com.enxv.aeronauticsstructuretool.blueprint.codec.NbtTransformCodec;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.enxv.aeronauticsstructuretool.blueprint.codec.NbtTagValidator.*;

final class VModSchematicReader {
    private static final String VSCHEM_IDENTIFIER = "vschem";

    private VModSchematicReader() {
    }

    static VModSchematic read(byte[] rawBytes) throws IOException {
        if (rawBytes == null || rawBytes.length == 0) {
            throw new IOException("empty VMod blueprint");
        }
        DecodedPayload decoded = decodeContainer(rawBytes);
        CompoundTag root = decodePayload(decoded.schematicType(), decoded.payload());
        ListTag paletteTag = requireCompoundList(root, "blockPalette", "VMod blockPalette", true);
        ListTag extraDataTag = optionalCompoundList(root, "extraBlockData", "VMod extraBlockData");
        requireOptionalType(root, "shipData", Tag.TAG_COMPOUND, "VMod shipData");
        List<BlockState> palette = readPalette(paletteTag);
        List<CompoundTag> extraData = readExtraData(extraDataTag);
        Map<Long, VModShipInfo> shipInfo = readShipInfo(root.getCompound("shipData"));
        CompoundTag gridData = requireCompound(root, "gridData", "VMod schematic gridData", true);
        VModSchematic schematic = new VModSchematic();
        Map<String, Integer> unsupportedBlocks = new LinkedHashMap<>();

        for (String key : gridData.getAllKeys()) {
            long shipId = parseShipId(key);
            VModShip ship = schematic.ships().computeIfAbsent(
                    shipId,
                    ignored -> new VModShip(shipId, shipInfo.get(shipId))
            );
            ListTag blockList = requireCompoundList(
                    gridData,
                    key,
                    "VMod ship " + shipId + " block grid",
                    false
            );
            Set<BlockPos> seenPositions = new HashSet<>();
            for (int i = 0; i < blockList.size(); i++) {
                CompoundTag blockTag = blockList.getCompound(i);
                requireNumeric(blockTag, "pid", "VMod ship " + shipId + " block " + i);
                requireNumeric(blockTag, "x", "VMod ship " + shipId + " block " + i);
                requireNumeric(blockTag, "y", "VMod ship " + shipId + " block " + i);
                requireNumeric(blockTag, "z", "VMod ship " + shipId + " block " + i);
                int paletteId = blockTag.getInt("pid");
                if (paletteId < 0 || paletteId >= palette.size()) {
                    throw new IOException(
                            "invalid VMod palette index " + paletteId + " for ship " + shipId
                    );
                }
                BlockState sourceState = palette.get(paletteId);
                BlockState mappedState = VModBlockMapper.mapState(sourceState);
                if (mappedState.isAir()) {
                    if (!sourceState.isAir()) {
                        unsupportedBlocks.merge(VModBlockMapper.blockId(sourceState), 1, Integer::sum);
                    }
                    continue;
                }

                if (blockTag.contains("edi") && !blockTag.contains("edi", Tag.TAG_ANY_NUMERIC)) {
                    throw new IOException("VMod ship " + shipId + " block " + i + " has a non-numeric edi");
                }
                int extraDataId = blockTag.contains("edi", Tag.TAG_ANY_NUMERIC)
                        ? blockTag.getInt("edi")
                        : -1;
                CompoundTag blockEntityTag = null;
                if (mappedState.hasBlockEntity()) {
                    if (extraDataId < 0 || extraDataId >= extraData.size()) {
                        throw new IOException(
                                "invalid VMod block-entity data index " + extraDataId
                                        + " for ship " + shipId + " block " + i
                        );
                    }
                    blockEntityTag = VModBlockMapper.normalizeBlockEntityTag(
                            extraData.get(extraDataId),
                            mappedState
                    );
                }
                BlockPos position = new BlockPos(
                        blockTag.getInt("x"),
                        blockTag.getInt("y"),
                        blockTag.getInt("z")
                );
                if (!seenPositions.add(position)) {
                    throw new IOException(
                            "duplicate VMod block position " + position.toShortString() + " for ship " + shipId
                    );
                }
                ship.blocks().add(new VModBlock(
                        position,
                        mappedState,
                        blockEntityTag
                ));
            }
        }

        if (!unsupportedBlocks.isEmpty()) {
            AeronauticsStructureToolMod.LOGGER.warn(
                    "VMod import skipped unsupported blocks: {}",
                    unsupportedBlocks
            );
        }
        return schematic;
    }

    private static DecodedPayload decodeContainer(byte[] rawBytes) throws IOException {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(rawBytes));
        try {
            String identifier = buffer.readUtf();
            if (!VSCHEM_IDENTIFIER.equals(identifier)) {
                throw new IOException("unsupported VMod blueprint");
            }
            String schematicType = buffer.readUtf();
            if (schematicType.isBlank()) {
                throw new IOException("VMod schematic type is empty");
            }
            byte[] payload = new byte[buffer.readableBytes()];
            buffer.readBytes(payload);
            if (payload.length == 0) {
                throw new IOException("VMod schematic payload is empty");
            }
            return new DecodedPayload(schematicType, payload);
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IOException("malformed VMod blueprint container", exception);
        } finally {
            buffer.release();
        }
    }

    private static CompoundTag decodePayload(String schematicType, byte[] payload) throws IOException {
        try {
            return BlueprintArchiveCodec.decodeCompressedOrRaw(payload);
        } catch (IOException exception) {
            throw new IOException("invalid VMod schematic payload for " + schematicType, exception);
        }
    }

    private static Map<Long, VModShipInfo> readShipInfo(CompoundTag shipDataTag) throws IOException {
        Map<Long, VModShipInfo> shipInfo = new LinkedHashMap<>();
        if (shipDataTag == null || shipDataTag.isEmpty()) {
            return shipInfo;
        }
        ListTag shipsData = requireCompoundList(
                shipDataTag,
                "data",
                "VMod shipData.data",
                false
        );
        for (int i = 0; i < shipsData.size(); i++) {
            CompoundTag shipTag = shipsData.getCompound(i);
            requireNumeric(shipTag, "id", "VMod shipData entry " + i);
            long shipId = shipTag.getLong("id");
            VModShipInfo previous = shipInfo.put(shipId, new VModShipInfo(
                    readOptionalVector(shipTag, "rptc", "VMod shipData entry " + i + ".rptc"),
                    readOptionalQuaternion(shipTag, "rot", "VMod shipData entry " + i + ".rot")
            ));
            if (previous != null) {
                throw new IOException("duplicate VMod shipData id " + shipId);
            }
        }
        return shipInfo;
    }

    private static List<BlockState> readPalette(ListTag paletteTag) throws IOException {
        HolderLookup<Block> lookup = BuiltInRegistries.BLOCK.asLookup();
        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) {
            CompoundTag entry = paletteTag.getCompound(i);
            if (!entry.contains("Name", Tag.TAG_STRING) || entry.getString("Name").isBlank()) {
                throw new IOException("VMod palette entry " + i + " has no block name");
            }
            palette.add(NbtUtils.readBlockState(lookup, entry));
        }
        return palette;
    }

    private static List<CompoundTag> readExtraData(ListTag extraDataTag) {
        List<CompoundTag> extraData = new ArrayList<>(extraDataTag.size());
        for (int i = 0; i < extraDataTag.size(); i++) {
            extraData.add(extraDataTag.getCompound(i));
        }
        return extraData;
    }

    private static long parseShipId(String key) throws IOException {
        try {
            return Long.parseLong(key);
        } catch (NumberFormatException exception) {
            throw new IOException("invalid VMod ship id: " + key, exception);
        }
    }

    private static Vector3d readOptionalVector(
            CompoundTag parent,
            String key,
            String label
    ) throws IOException {
        if (!parent.contains(key)) {
            return new Vector3d();
        }
        try {
            return NbtTransformCodec.readVector(requireCompound(parent, key, label, false), label);
        } catch (IllegalArgumentException exception) {
            throw new IOException("invalid " + label + ": " + exception.getMessage(), exception);
        }
    }

    private static Quaterniond readOptionalQuaternion(
            CompoundTag parent,
            String key,
            String label
    ) throws IOException {
        if (!parent.contains(key)) {
            return new Quaterniond();
        }
        try {
            return NbtTransformCodec.readQuaternion(requireCompound(parent, key, label, false), label).normalize();
        } catch (IllegalArgumentException exception) {
            throw new IOException("invalid " + label + ": " + exception.getMessage(), exception);
        }
    }


    private record DecodedPayload(String schematicType, byte[] payload) {
    }
}
