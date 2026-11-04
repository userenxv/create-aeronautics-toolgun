package com.enxv.aeronauticsstructuretool.compat.coasters;

import com.enxv.aeronauticsstructuretool.blueprint.model.SavedSubLevelBlueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CoastersBlueprintRegressionCheck {
    public static void main(String[] args) throws Exception {
        UUID original = UUID.randomUUID(), blueprint = UUID.randomUUID();
        CompoundTag plot = new CompoundTag();
        plot.putInt("log_size", 7);
        SavedSubLevelBlueprint host = new SavedSubLevelBlueprint(blueprint, original, "host", plot,
                List.of(), new Vector3d(), new Vector3d(), new Quaterniond(), false, -64,
                new Vector3d(1024, 0, 1024), List.of(), List.of());
        CompoundTag rivet = new CompoundTag();
        rivet.putUUID("HostSubLevel", original);
        rivet.putLong("HostPos", new BlockPos(20_481_032, 128, 20_503_560).asLong());
        var legacy = CoastersBlockEntityNbt.readHost(CoastersBlockEntityNbt.RIVET, rivet, List.of(host));
        require(legacy.blueprintId().equals(blueprint) && legacy.localPos().equals(new BlockPos(1032,128,1032)),
                "legacy rivet host must resolve through original UUID and exact plot-local coordinates");
        CompoundTag balloon = new CompoundTag();
        balloon.putUUID("HostSubLevel", original);
        balloon.putLong("HostPos", new BlockPos(-3, 80, 4).asLong());
        require(CoastersBlockEntityNbt.readHost(CoastersBlockEntityNbt.BALLOON, balloon, List.of(host))
                .localPos().equals(new BlockPos(1021,80,1028)), "balloon HostPos is center-relative, not a rivet coordinate");
        rivet.putInt(CoastersBlockEntityNbt.SPACE, 1);
        rivet.putUUID("HostSubLevel", blueprint);
        rivet.putLong("HostPos", legacy.localPos().asLong());
        require(CoastersBlockEntityNbt.readHost(CoastersBlockEntityNbt.RIVET, rivet, List.of(host)).equals(legacy),
                "new and legacy blueprint hosts must normalize identically");
        reject(() -> CoastersBlockEntityNbt.readHost(CoastersBlockEntityNbt.RIVET, rivet, List.of()));
        CompoundTag bad = rivet.copy(); bad.putInt(CoastersBlockEntityNbt.SPACE, 2);
        reject(() -> CoastersBlockEntityNbt.readHost(CoastersBlockEntityNbt.RIVET, bad, List.of(host)));
        CompoundTag outOfBounds = rivet.copy(); outOfBounds.putLong("HostPos", new BlockPos(-1, 0, 0).asLong());
        reject(() -> CoastersBlockEntityNbt.readHost(CoastersBlockEntityNbt.RIVET, outOfBounds, List.of(host)));
        verifyTrackCoordinates();
        verifyCartCopies();
        UUID allocated = UUID.randomUUID(), untouched = UUID.randomUUID();
        require(!CoastersPlacementScope.defers(allocated), "scope must start empty");
        try {
            try (var scope = CoastersPlacementScope.begin(java.util.Set.of(allocated))) {
                require(CoastersPlacementScope.defers(allocated) && !CoastersPlacementScope.defers(untouched),
                        "loading scope must not suspend unrelated originals");
                throw new IllegalArgumentException("simulated load failure");
            }
        } catch (IllegalArgumentException expected) {
            require(!CoastersPlacementScope.defers(allocated), "failed placement must release loading scope");
        }
        try {
            try (var refresh = CoastersCartCacheRefresh.begin(net.minecraft.world.level.Level.OVERWORLD, java.util.Set.of(allocated))) {
                require(!CoastersCartCacheRefresh.consumeBody(untouched), "do not invalidate original body caches");
                require(CoastersCartCacheRefresh.consumeBody(allocated) && !CoastersCartCacheRefresh.consumeBody(allocated), "invalidate a new body exactly once");
                require(!CoastersCartCacheRefresh.consumeIndex(net.minecraft.world.level.Level.NETHER), "do not invalidate another dimension");
                require(CoastersCartCacheRefresh.consumeIndex(net.minecraft.world.level.Level.OVERWORLD)
                        && !CoastersCartCacheRefresh.consumeIndex(net.minecraft.world.level.Level.OVERWORLD), "invalidate dimension lookup exactly once");
                throw new IllegalArgumentException("simulated refresh failure");
            }
        } catch (IllegalArgumentException expected) {
            require(!CoastersCartCacheRefresh.consumeBody(allocated) && !CoastersCartCacheRefresh.consumeIndex(net.minecraft.world.level.Level.OVERWORLD), "failed refresh must release scope");
        }
        // Ordinary data paths must be loadable with the optional mod absent.
        Class.forName(CoastersBlueprintCompat.class.getName());
        CoastersBlueprintCompat.restoreCartData(new CompoundTag(), Map.of(), 1);
    }

    private static void verifyTrackCoordinates() {
        CompoundTag anchor = new CompoundTag();
        CompoundTag curve = new CompoundTag(); curve.putLong("Peer", new BlockPos(100,12,200).asLong());
        CompoundTag geometry = new CompoundTag(); geometry.putString("Keep", "relative Bezier and colors");
        curve.put("Bezier", geometry);
        ListTag curves = new ListTag(); curves.add(curve); anchor.put("AnchorPeerCurves", curves);
        CompoundTag span = new CompoundTag();
        for (String key : List.of("EndAnchor", "EdgeFrom", "EdgeTo")) span.putLong(key, new BlockPos(120,20,210).asLong());
        anchor.put("ChainLiftSpan", span);
        anchor.putLong("ChainLiftFirstHop", new BlockPos(110,15,205).asLong());
        CompoundTag before = anchor.copy();
        CoastersBlockEntityNbt.mapAnchorPositions(anchor, pos -> pos.offset(900,32,-700));
        require(BlockPos.of(curve.getLong("Peer")).equals(new BlockPos(1000,44,-500)), "peer must follow owner plot and height shift");
        require(curve.getCompound("Bezier").equals(geometry), "relative geometry must not be transformed twice");
        CoastersBlockEntityNbt.mapAnchorPositions(anchor, pos -> pos.offset(-900,-32,700));
        require(anchor.equals(before), "track, tint and lift position mapping must be reversible");
        span.putLong("EdgeFrom", 0L); span.putLong("EdgeTo", 0L);
        anchor.putLong("ChainLiftFirstHop", 0L);
        CoastersBlockEntityNbt.mapAnchorPositions(anchor, pos -> pos.offset(900,32,-700));
        require(span.getLong("EdgeFrom") == 0 && span.getLong("EdgeTo") == 0
                && anchor.getLong("ChainLiftFirstHop") == 0, "unused chain lift positions must stay ZERO");
        anchor.remove("ChainLiftFirstHop");
        CoastersBlockEntityNbt.mapAnchorPositions(anchor, pos -> pos.offset(-900,-32,700));
        require(!anchor.contains("ChainLiftFirstHop"), "optional first hop must stay absent");
        ListTag malformed = new ListTag(); malformed.add(net.minecraft.nbt.StringTag.valueOf("bad"));
        anchor.put("AnchorPeerCurves", malformed);
        reject(() -> CoastersBlockEntityNbt.mapAnchorPositions(anchor, pos -> pos));
    }

    private static void verifyCartCopies() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        ListTag list = new ListTag(); CompoundTag link = new CompoundTag();
        link.putUUID("partner", b); link.putDouble("distance", 3.5); list.add(link);
        UUID b1 = UUID.randomUUID(), b2 = UUID.randomUUID();
        ListTag first = CoastersCartLinkNbt.remap(list, a, id -> b1, 2);
        ListTag second = CoastersCartLinkNbt.remap(list, a, id -> b2, 1);
        require(first.getCompound(0).getUUID("partner").equals(b1) && second.getCompound(0).getUUID("partner").equals(b2),
                "separate placements must couple only to their own copies");
        require(first.getCompound(0).getDouble("distance") == 7 && link.getUUID("partner").equals(b), "scale distance without mutating source links");
        reject(() -> CoastersCartLinkNbt.remap(list, a, id -> null, 1));
        link.putUUID("partner", a); reject(() -> CoastersCartLinkNbt.remap(list, a, id -> id, 1));
    }

    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("malformed reference was accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
