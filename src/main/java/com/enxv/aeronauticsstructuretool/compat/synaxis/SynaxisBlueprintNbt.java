package com.enxv.aeronauticsstructuretool.compat.synaxis;

import com.enxv.aeronauticsstructuretool.blueprint.geometry.FlexibleBlockPosCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class SynaxisBlueprintNbt {
    static final String CONTROLLER_WIRES = "AST_SynaxisControllerWires";
    static final String COMPANION_JOINTS = "AST_SynaxisCompanionJoints";
    static final String CIMULINK = "AST_SynaxisCimulink";

    private static final String ENTRIES = "entries";
    private static final String SOURCE_SUBLEVEL = "source_sublevel";
    private static final String SOURCE_POS = "source_pos";
    private static final String SINK_SUBLEVEL = "sink_sublevel";
    private static final String SINK_POS = "sink_pos";
    private static final String CONTROLLER_DIRECTION = "Direction";
    private static final String CONTROLLER_CHANNEL = "Channel";
    private static final String OWNER_SUBLEVEL = "owner_sublevel";
    private static final String OWNER_POS = "owner_pos";
    private static final String TARGET_SUBLEVEL = "target_sublevel";
    private static final String TARGET_POS = "target_pos";
    private static final String EXPECTED_SUBLEVEL = "expected_sublevel";
    private static final String DIRECTION = "direction";
    private static final String ALIGNMENT_DIRECTION = "alignment_direction";
    private static final String ENDPOINTS = "endpoints";
    private static final String LINKS = "links";
    private static final String OLD_ENDPOINT_ID = "old_endpoint_id";
    private static final String REF_SUBLEVEL = "ref_sublevel";
    private static final String REF_POS = "ref_pos";
    private static final String FROM_ENDPOINT = "from_endpoint";
    private static final String FROM_PORT = "from_port";
    private static final String TO_ENDPOINT = "to_endpoint";
    private static final String TO_PORT = "to_port";

    private SynaxisBlueprintNbt() {
    }

    static void writeControllerWires(CompoundTag plotTag, List<ControllerWire> connections) {
        if (connections.isEmpty()) {
            return;
        }
        ListTag entries = new ListTag();
        for (ControllerWire connection : connections) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(SOURCE_SUBLEVEL, connection.sourceSublevelId());
            entry.put(SOURCE_POS, NbtUtils.writeBlockPos(connection.sourceLocalPos()));
            entry.putUUID(SINK_SUBLEVEL, connection.sinkSublevelId());
            entry.put(SINK_POS, NbtUtils.writeBlockPos(connection.sinkLocalPos()));
            entry.putString(CONTROLLER_CHANNEL, connection.channel());
            entry.putString(CONTROLLER_DIRECTION, connection.direction().getName());
            entries.add(entry);
        }
        CompoundTag manifest = new CompoundTag();
        manifest.put(ENTRIES, entries);
        plotTag.put(CONTROLLER_WIRES, manifest);
    }

    static List<ControllerWire> readControllerWires(CompoundTag plotTag) throws IOException {
        CompoundTag manifest = readOptionalManifest(plotTag, CONTROLLER_WIRES);
        if (manifest == null) {
            return List.of();
        }
        ListTag entries = readCompoundList(manifest, ENTRIES, CONTROLLER_WIRES);
        List<ControllerWire> result = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            UUID sourceSublevel = readUuid(entry, SOURCE_SUBLEVEL, owner(CONTROLLER_WIRES, i));
            UUID sinkSublevel = readUuid(entry, SINK_SUBLEVEL, owner(CONTROLLER_WIRES, i));
            BlockPos sourcePos = readBlockPos(entry, SOURCE_POS, owner(CONTROLLER_WIRES, i));
            BlockPos sinkPos = readBlockPos(entry, SINK_POS, owner(CONTROLLER_WIRES, i));
            Direction direction = readDirection(entry, CONTROLLER_DIRECTION, owner(CONTROLLER_WIRES, i));
            String channel = readNonBlankString(entry, CONTROLLER_CHANNEL, owner(CONTROLLER_WIRES, i));
            result.add(new ControllerWire(sourceSublevel, sourcePos, sinkSublevel, sinkPos, direction, channel));
        }
        return List.copyOf(result);
    }

    static void writeCompanionJoints(CompoundTag plotTag, List<CompanionJoint> joints) {
        if (joints.isEmpty()) {
            return;
        }
        ListTag entries = new ListTag();
        for (CompanionJoint joint : joints) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(OWNER_SUBLEVEL, joint.ownerSublevelId());
            entry.put(OWNER_POS, NbtUtils.writeBlockPos(joint.ownerLocalPos()));
            entry.putUUID(TARGET_SUBLEVEL, joint.targetSublevelId());
            entry.put(TARGET_POS, NbtUtils.writeBlockPos(joint.targetLocalPos()));
            entry.putString(DIRECTION, joint.direction().getName());
            entry.putString(ALIGNMENT_DIRECTION, joint.alignmentDirection().getName());
            if (joint.expectedSublevelId() != null) {
                entry.putUUID(EXPECTED_SUBLEVEL, joint.expectedSublevelId());
            }
            entries.add(entry);
        }
        CompoundTag manifest = new CompoundTag();
        manifest.put(ENTRIES, entries);
        plotTag.put(COMPANION_JOINTS, manifest);
    }

    static List<CompanionJoint> readCompanionJoints(CompoundTag plotTag) throws IOException {
        CompoundTag manifest = readOptionalManifest(plotTag, COMPANION_JOINTS);
        if (manifest == null) {
            return List.of();
        }
        ListTag entries = readCompoundList(manifest, ENTRIES, COMPANION_JOINTS);
        List<CompanionJoint> result = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            String owner = owner(COMPANION_JOINTS, i);
            UUID expected = entry.contains(EXPECTED_SUBLEVEL)
                    ? readUuid(entry, EXPECTED_SUBLEVEL, owner)
                    : null;
            result.add(new CompanionJoint(
                    readUuid(entry, OWNER_SUBLEVEL, owner),
                    readBlockPos(entry, OWNER_POS, owner),
                    readUuid(entry, TARGET_SUBLEVEL, owner),
                    readBlockPos(entry, TARGET_POS, owner),
                    readDirection(entry, DIRECTION, owner),
                    readDirection(entry, ALIGNMENT_DIRECTION, owner),
                    expected
            ));
        }
        return List.copyOf(result);
    }

    static void writeCimulink(CompoundTag root, CimulinkManifest manifest) {
        if (manifest.endpoints().isEmpty() && manifest.links().isEmpty()) {
            return;
        }
        CompoundTag manifestTag = new CompoundTag();
        if (!manifest.endpoints().isEmpty()) {
            ListTag endpoints = new ListTag();
            for (CimulinkEndpoint endpoint : manifest.endpoints()) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID(OLD_ENDPOINT_ID, endpoint.oldEndpointId());
                entry.putUUID(REF_SUBLEVEL, endpoint.sublevelId());
                entry.put(REF_POS, NbtUtils.writeBlockPos(endpoint.localPos()));
                endpoints.add(entry);
            }
            manifestTag.put(ENDPOINTS, endpoints);
        }
        if (!manifest.links().isEmpty()) {
            ListTag links = new ListTag();
            for (CimulinkLink link : manifest.links()) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID(FROM_ENDPOINT, link.fromEndpointId());
                entry.putString(FROM_PORT, link.fromPort());
                entry.putUUID(TO_ENDPOINT, link.toEndpointId());
                entry.putString(TO_PORT, link.toPort());
                links.add(entry);
            }
            manifestTag.put(LINKS, links);
        }
        root.put(CIMULINK, manifestTag);
    }

    static boolean hasCimulinkManifest(CompoundTag root) throws IOException {
        if (!root.contains(CIMULINK)) {
            return false;
        }
        if (!root.contains(CIMULINK, Tag.TAG_COMPOUND)) {
            throw new IOException(CIMULINK + " must be a compound tag");
        }
        return true;
    }

    static CimulinkManifest readCimulink(CompoundTag ownerTag) throws IOException {
        CompoundTag manifest = readOptionalManifest(ownerTag, CIMULINK);
        if (manifest == null) {
            return CimulinkManifest.EMPTY;
        }
        ListTag endpointEntries = readCompoundList(manifest, ENDPOINTS, CIMULINK);
        List<CimulinkEndpoint> endpoints = new ArrayList<>(endpointEntries.size());
        for (int i = 0; i < endpointEntries.size(); i++) {
            CompoundTag entry = endpointEntries.getCompound(i);
            String owner = owner(CIMULINK + "." + ENDPOINTS, i);
            endpoints.add(new CimulinkEndpoint(
                    readUuid(entry, OLD_ENDPOINT_ID, owner),
                    readUuid(entry, REF_SUBLEVEL, owner),
                    readBlockPos(entry, REF_POS, owner)
            ));
        }

        ListTag linkEntries = readCompoundList(manifest, LINKS, CIMULINK);
        List<CimulinkLink> links = new ArrayList<>(linkEntries.size());
        for (int i = 0; i < linkEntries.size(); i++) {
            CompoundTag entry = linkEntries.getCompound(i);
            String owner = owner(CIMULINK + "." + LINKS, i);
            links.add(new CimulinkLink(
                    readUuid(entry, FROM_ENDPOINT, owner),
                    readNonBlankString(entry, FROM_PORT, owner),
                    readUuid(entry, TO_ENDPOINT, owner),
                    readNonBlankString(entry, TO_PORT, owner)
            ));
        }
        return new CimulinkManifest(endpoints, links);
    }

    private static CompoundTag readOptionalManifest(CompoundTag parent, String key) throws IOException {
        if (!parent.contains(key)) {
            return null;
        }
        if (!parent.contains(key, Tag.TAG_COMPOUND)) {
            throw new IOException(key + " must be a compound tag");
        }
        return parent.getCompound(key);
    }

    private static ListTag readCompoundList(CompoundTag parent, String key, String owner) throws IOException {
        if (!parent.contains(key)) {
            return new ListTag();
        }
        Tag raw = parent.get(key);
        if (!(raw instanceof ListTag list) || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IOException(owner + " has an invalid compound list '" + key + "'");
        }
        return list;
    }

    private static UUID readUuid(CompoundTag tag, String key, String owner) throws IOException {
        if (!tag.hasUUID(key)) {
            throw new IOException(owner + " has an invalid UUID '" + key + "'");
        }
        return tag.getUUID(key);
    }

    private static BlockPos readBlockPos(CompoundTag tag, String key, String owner) throws IOException {
        try {
            return FlexibleBlockPosCodec.readRequired(tag, key, owner);
        } catch (IllegalArgumentException exception) {
            throw new IOException(exception.getMessage(), exception);
        }
    }

    private static Direction readDirection(CompoundTag tag, String key, String owner) throws IOException {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            throw new IOException(owner + " has an invalid direction '" + key + "'");
        }
        Direction direction = Direction.byName(tag.getString(key));
        if (direction == null) {
            throw new IOException(owner + " has an unknown direction '" + key + "'");
        }
        return direction;
    }

    private static String readNonBlankString(CompoundTag tag, String key, String owner) throws IOException {
        if (!tag.contains(key, Tag.TAG_STRING) || tag.getString(key).isBlank()) {
            throw new IOException(owner + " has an invalid string '" + key + "'");
        }
        return tag.getString(key);
    }

    private static String owner(String manifest, int index) {
        return manifest + " entry " + index;
    }

    record ControllerWire(
            UUID sourceSublevelId,
            BlockPos sourceLocalPos,
            UUID sinkSublevelId,
            BlockPos sinkLocalPos,
            Direction direction,
            String channel
    ) {
    }

    record CompanionJoint(
            UUID ownerSublevelId,
            BlockPos ownerLocalPos,
            UUID targetSublevelId,
            BlockPos targetLocalPos,
            Direction direction,
            Direction alignmentDirection,
            UUID expectedSublevelId
    ) {
    }

    record CimulinkEndpoint(UUID oldEndpointId, UUID sublevelId, BlockPos localPos) {
    }

    record CimulinkLink(UUID fromEndpointId, String fromPort, UUID toEndpointId, String toPort) {
    }

    record CimulinkManifest(List<CimulinkEndpoint> endpoints, List<CimulinkLink> links) {
        static final CimulinkManifest EMPTY = new CimulinkManifest(List.of(), List.of());

        CimulinkManifest {
            endpoints = List.copyOf(endpoints);
            links = List.copyOf(links);
        }
    }
}
