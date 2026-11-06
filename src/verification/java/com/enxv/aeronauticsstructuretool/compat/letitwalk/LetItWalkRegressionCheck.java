package com.enxv.aeronauticsstructuretool.compat.letitwalk;

import com.enxv.aeronauticsstructuretool.blueprint.model.SavedSubLevelBlueprint;
import com.enxv.aeronauticsstructuretool.blueprint.placement.BlueprintPlacementDiagnostics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class LetItWalkRegressionCheck {
    public static void main(String[] args) throws Exception {
        UUID hull = UUID.randomUUID(), segment = UUID.randomUUID(), walker = UUID.randomUUID();
        CompoundTag core = block(LetItWalkNbt.CORE);
        core.putUUID("walkerId", walker);
        CompoundTag built = new CompoundTag(), leg = new CompoundTag(), joint = new CompoundTag();
        joint.putUUID("parent", hull); joint.putUUID("child", segment);
        joint.putDouble("ppy", 1.5); joint.putDouble("cpy", 2.5);
        built.put("joints", list(joint));
        CompoundTag seg = new CompoundTag(); seg.putUUID("id", segment);
        built.put("segments", list(seg)); built.put("rest", list(new CompoundTag()));
        CompoundTag offsets = new CompoundTag(); offsets.putInt("n", 1); offsets.putDouble("o0y", -3);
        built.put("segOffsets", offsets); built.putDouble("footPloty", 4.5);
        leg.put("built", built); core.put("legs", list(leg));
        require(LetItWalkNbt.references(LetItWalkNbt.CORE, core).equals(Set.of(hull, segment)), "collect both ends of every joint");
        reject(() -> LetItWalkNbt.validateReferences(LetItWalkNbt.CORE, core, Set.of(hull)));
        List<SavedSubLevelBlueprint> saved = List.of(saved(hull, plot(core)), saved(segment, plot(block(LetItWalkNbt.JOINT))));
        LetItWalkPlacement first = new LetItWalkPlacement(saved), second = new LetItWalkPlacement(saved);
        BlueprintPlacementDiagnostics diagnostics = new BlueprintPlacementDiagnostics("walker regression");
        CompoundTag c1 = core.copy(), c2 = core.copy();
        CompoundTag controls = block(LetItWalkNbt.CONTROLS); controls.putUUID("linkedWalker", walker);
        CompoundTag copiedControls = controls.copy();
        // Controls can occur before the core or in another plot.
        first.prepare(copiedControls, diagnostics); first.prepare(c1, diagnostics); second.prepare(c2, diagnostics);
        UUID fresh = c1.getUUID(LetItWalkNbt.COPY_ID);
        require(fresh.equals(copiedControls.getUUID("linkedWalker")), "copy controls must address their own core");
        require(!fresh.equals(walker) && !fresh.equals(c2.getUUID(LetItWalkNbt.COPY_ID)), "separate placements need independent identities");
        require(!core.contains(LetItWalkNbt.COPY_ID) && controls.getUUID("linkedWalker").equals(walker), "never mutate source archives");
        CompoundTag external = controls.copy(); external.putUUID("linkedWalker", UUID.randomUUID());
        first.prepare(external, diagnostics);
        require(!external.contains("linkedWalker") && diagnostics.warnings().size() == 1, "external bindings must be detached with a warning");
        CompoundTag item = block(LetItWalkNbt.CONTROLS), components = new CompoundTag(), data = new CompoundTag();
        data.putUUID("linkedWalker", walker); components.put("minecraft:block_entity_data", data); item.put("components", components);
        CompoundTag inventory = new CompoundTag(); inventory.put("Items", list(item));
        first.remapStoredControls(inventory, diagnostics);
        require(data.getUUID("linkedWalker").equals(fresh), "inventory control items must follow the new core");
        CompoundTag unrelated = block("other:controls"); unrelated.put("components", components.copy());
        CompoundTag unrelatedBefore = unrelated.copy(); first.remapStoredControls(unrelated, diagnostics);
        require(unrelated.equals(unrelatedBefore), "other mods' data must remain untouched");
        CompoundTag mount = block(LetItWalkNbt.MOUNT); mount.putBoolean("gripping", true); mount.putLong("firing", 1000000);
        mount.putString("attachment", "JET"); mount.putInt("yaw", 15);
        first.prepare(mount, diagnostics);
        require(!mount.getBoolean("gripping") && mount.getLong("firing") == Long.MIN_VALUE
                && mount.getString("attachment").equals("JET") && mount.getInt("yaw") == 15, "reset runtime grabs/firing, preserve build settings");
        CompoundTag shifted = core.copy(); LetItWalkNbt.shiftHeight(shifted, id -> id.equals(hull) ? -16 : 32);
        CompoundTag moved = shifted.getList("legs", 10).getCompound(0).getCompound("built");
        CompoundTag movedJoint = moved.getList("joints", 10).getCompound(0);
        require(movedJoint.getDouble("ppy") == -14.5 && movedJoint.getDouble("cpy") == 34.5
                && moved.getDouble("footPloty") == 36.5 && moved.getCompound("segOffsets").getDouble("o0y") == -35,
                "each joint/foot/segment offset must follow its own body's height shift");
        reject(() -> new LetItWalkPlacement(List.of(saved(hull, plot(core)))));
        reject(() -> new LetItWalkPlacement(List.of(saved(hull, plot(core)), saved(segment, plot(core)))));
        CompoundTag bad = core.copy(); bad.getList("legs", 10).getCompound(0).getCompound("built").getCompound("segOffsets").putInt("n", Integer.MAX_VALUE);
        reject(() -> LetItWalkNbt.references(LetItWalkNbt.CORE, bad));
        CompoundTag badId = core.copy(); badId.getList("legs", 10).getCompound(0).getCompound("built").getList("segments", 10).getCompound(0).putString("id", "broken");
        reject(() -> LetItWalkNbt.references(LetItWalkNbt.CORE, badId));
        Class.forName(LetItWalkPlacement.class.getName()); // Optional mod is intentionally absent here.
    }

    private static CompoundTag block(String id) { CompoundTag tag = new CompoundTag(); tag.putString("id", id); return tag; }
    private static ListTag list(CompoundTag tag) { ListTag list = new ListTag(); list.add(tag); return list; }
    private static CompoundTag plot(CompoundTag be) {
        CompoundTag chunk = new CompoundTag(); chunk.put("block_entities", list(be));
        CompoundTag chunks = new CompoundTag(); chunks.put("0", chunk);
        CompoundTag plot = new CompoundTag(); plot.put("chunks", chunks); return plot;
    }
    private static SavedSubLevelBlueprint saved(UUID id, CompoundTag plot) {
        return new SavedSubLevelBlueprint(id, UUID.randomUUID(), "walker", plot, List.of(), new Vector3d(),
                new Vector3d(), new Quaterniond(), false, -64, new Vector3d(), List.of(), List.of());
    }
    private static void require(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
    private static void reject(Action action) throws Exception {
        try { action.run(); } catch (IllegalArgumentException | IOException expected) { return; }
        throw new AssertionError("invalid walker data was accepted");
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }
}
