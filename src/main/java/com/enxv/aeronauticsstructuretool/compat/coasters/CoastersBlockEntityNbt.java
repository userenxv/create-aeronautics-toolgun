package com.enxv.aeronauticsstructuretool.compat.coasters;

import com.enxv.aeronauticsstructuretool.blueprint.geometry.LegacyPlotBlockCoordinates;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.LoadedSubLevelCoordinates;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockTransform;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturePlan;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturedSubLevel;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import com.enxv.aeronauticsstructuretool.blueprint.model.SavedSubLevelBlueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Exact 0.1.5 persistence fields. Relative Bezier geometry and visual settings stay untouched. */
public final class CoastersBlockEntityNbt {
    public static final String RIVET = "simulatedcoasters:rivet";
    public static final String BALLOON = "simulatedcoasters:red_balloon";
    public static final String ANCHOR = "simulatedcoasters:coaster_anchorpoint";
    static final String SPACE = "AST_CoastersReferenceVersion";

    private CoastersBlockEntityNbt() {}

    public static void save(String id, CompoundTag tag, CapturePlan plan, CapturedSubLevel current) {
        if (RIVET.equals(id) || BALLOON.equals(id)) {
            BlockPos pos = position(tag, "HostPos");
            CapturedSubLevel host;
            if (BALLOON.equals(id)) {
                host = plan.findByOriginalId(uuid(tag, "HostSubLevel"));
                if (host == null) throw invalid("balloon host is outside the saved structure");
                pos = host.subLevel().getPlot().getCenterBlock().offset(pos);
            } else {
                host = null;
                for (CapturedSubLevel candidate : plan.sublevels()) {
                    if (PlotBlockTransform.capture(candidate.subLevel()).containsPlotAbsolute(pos)) {
                        if (host != null) throw invalid("rivet host belongs to multiple plots");
                        host = candidate;
                    }
                }
                if (host == null) throw invalid("rivet host is outside the saved structure; include its supporting structure");
            }
            if (host.blueprintId().equals(current.blueprintId())) throw invalid("attachment hosts itself");
            PlotBlockTransform transform = PlotBlockTransform.capture(host.subLevel());
            if (!transform.containsPlotAbsolute(pos)) throw invalid("host position is outside its plot");
            tag.putLong("HostPos", transform.toSavedLocalBlockPos(pos).asLong());
            tag.putUUID("HostSubLevel", host.blueprintId());
            tag.remove("HostLatticePos"); // Recomputed from the placed balloon host, never copied.
            tag.putInt(SPACE, 1);
        } else if (ANCHOR.equals(id)) {
            PlotBlockTransform transform = PlotBlockTransform.capture(current.subLevel());
            mapAnchorPositions(tag, pos -> {
                if (!transform.containsPlotAbsolute(pos)) throw invalid("track endpoint is outside its saved plot");
                return transform.toSavedLocalBlockPos(pos);
            });
            tag.putInt(SPACE, 1);
        }
    }

    public static void load(String id, CompoundTag tag, Map<UUID, LoadedSubLevel> loaded, LoadedSubLevel current) {
        if (RIVET.equals(id) || BALLOON.equals(id)) {
            HostReference reference = readHost(id, tag, loaded.values().stream().map(LoadedSubLevel::saved).toList());
            if (reference.blueprintId().equals(current.saved().blueprintId())) throw invalid("attachment hosts itself");
            LoadedSubLevel host = loaded.get(reference.blueprintId());
            BlockPos absolute = LoadedSubLevelCoordinates.toGlobalBlockPos(host, reference.localPos());
            tag.putUUID("HostSubLevel", host.subLevel().getUniqueId());
            tag.putLong("HostPos", (BALLOON.equals(id)
                    ? absolute.subtract(host.subLevel().getPlot().getCenterBlock()) : absolute).asLong());
            if (BALLOON.equals(id)) tag.putLong("HostLatticePos", absolute.asLong());
            tag.remove(SPACE);
        } else if (ANCHOR.equals(id)) {
            boolean local = isPortable(tag);
            mapAnchorPositions(tag, pos -> LoadedSubLevelCoordinates.toGlobalBlockPos(current,
                    checkedLocal(local ? pos : LegacyPlotBlockCoordinates.toSavedLocal(pos, current.saved().plotTag()),
                            current.saved().plotTag())));
            tag.remove(SPACE);
        }
    }

    static HostReference readHost(String id, CompoundTag tag, Collection<SavedSubLevelBlueprint> saved) {
        boolean local = isPortable(tag);
        UUID storedId = uuid(tag, "HostSubLevel");
        SavedSubLevelBlueprint host = null;
        for (SavedSubLevelBlueprint candidate : saved) {
            if ((local ? candidate.blueprintId() : candidate.originalId()).equals(storedId)) {
                if (host != null) throw invalid("ambiguous host UUID");
                host = candidate;
            }
        }
        if (host == null) throw invalid("host is missing from blueprint; re-save with the supporting structure");
        BlockPos pos = position(tag, "HostPos");
        if (!local) {
            if (BALLOON.equals(id)) {
                int center = LegacyPlotBlockCoordinates.sideLengthBlocks(host.plotTag()) / 2;
                pos = pos.offset(center, 0, center);
            } else {
                pos = LegacyPlotBlockCoordinates.toSavedLocal(pos, host.plotTag());
            }
        }
        return new HostReference(host.blueprintId(), checkedLocal(pos, host.plotTag()));
    }

    static boolean isPortable(CompoundTag tag) {
        if (!tag.contains(SPACE)) return false;
        if (!tag.contains(SPACE, Tag.TAG_INT) || tag.getInt(SPACE) != 1) throw invalid("unsupported reference format");
        return true;
    }

    static BlockPos checkedLocal(BlockPos pos, CompoundTag plot) {
        if (!LegacyPlotBlockCoordinates.containsSavedLocal(pos, plot)) throw invalid("reference is outside its plot");
        return pos;
    }

    static void mapAnchorPositions(CompoundTag tag, UnaryOperator<BlockPos> map) {
        for (String list : new String[]{"AnchorPeerCurves", "AnchorPeerCurveTints"}) {
            for (Tag entry : compounds(tag, list)) mapPosition((CompoundTag) entry, "Peer", map);
        }
        if (tag.contains("ChainLiftSpan")) {
            if (!tag.contains("ChainLiftSpan", Tag.TAG_COMPOUND)) throw invalid("invalid chain lift span");
            CompoundTag span = tag.getCompound("ChainLiftSpan");
            for (String key : new String[]{"EndAnchor", "EdgeFrom", "EdgeTo"}) mapOptionalPosition(span, key, map);
            if (tag.contains("ChainLiftFirstHop")) mapOptionalPosition(tag, "ChainLiftFirstHop", map);
            for (Tag entry : compounds(tag, "ChainLiftCommittedSegments")) {
                mapPosition((CompoundTag) entry, "EdgeFrom", map);
                mapPosition((CompoundTag) entry, "EdgeTo", map);
            }
        }
    }

    static ListTag compounds(CompoundTag tag, String key) {
        if (!tag.contains(key)) return new ListTag();
        if (!(tag.get(key) instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) throw invalid("invalid " + key);
        return list;
    }

    private static void mapPosition(CompoundTag tag, String key, UnaryOperator<BlockPos> map) {
        tag.putLong(key, map.apply(position(tag, key)).asLong());
    }

    private static void mapOptionalPosition(CompoundTag tag, String key, UnaryOperator<BlockPos> map) {
        // ChainLiftEndRef uses ZERO for the unused anchor/curve alternative.
        if (!position(tag, key).equals(BlockPos.ZERO)) mapPosition(tag, key, map);
    }

    static BlockPos position(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LONG)) throw invalid("missing or invalid position " + key);
        return BlockPos.of(tag.getLong(key));
    }

    static UUID uuid(CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) throw invalid("missing or invalid UUID " + key);
        return tag.getUUID(key);
    }

    static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Coasters blueprint: " + message);
    }

    record HostReference(UUID blueprintId, BlockPos localPos) {}
}
