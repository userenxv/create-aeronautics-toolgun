package com.enxv.aeronauticsstructuretool.compat.coasters;

import com.enxv.aeronauticsstructuretool.blueprint.model.CapturePlan;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturedSubLevel;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Data conversion is independent of the optional mod; live restoration is isolated behind its presence. */
public final class CoastersBlueprintCompat {
    private static final String CARTS = "AST_CoastersCartLinks";

    private CoastersBlueprintCompat() {}

    public static Map<UUID, Set<UUID>> connections(Collection<ServerSubLevel> sublevels) {
        return ModList.get().isLoaded("simulatedcoasters") ? CoastersRuntimeBridge.connections(sublevels) : Map.of();
    }

    public static void capture(CompoundTag root, CapturePlan plan) {
        ListTag carts = new ListTag();
        for (CapturedSubLevel captured : plan.sublevels()) {
            ListTag links = CoastersCartLinkNbt.read(captured.subLevel().getUserDataTag());
            if (links.isEmpty()) continue;
            CompoundTag cart = new CompoundTag();
            cart.putUUID("Owner", captured.blueprintId());
            cart.put("Links", CoastersCartLinkNbt.remap(links, captured.subLevel().getUniqueId(), id -> {
                CapturedSubLevel partner = plan.findByOriginalId(id);
                return partner == null ? null : partner.blueprintId();
            }, 1));
            carts.add(cart);
        }
        if (!carts.isEmpty()) root.put(CARTS, carts);
    }

    public static void restoreCartData(CompoundTag root, Map<UUID, LoadedSubLevel> loaded, double scale) {
        if (!root.contains(CARTS)) return;
        Set<UUID> owners = new java.util.HashSet<>();
        for (Tag entry : CoastersBlockEntityNbt.compounds(root, CARTS)) {
            CompoundTag cart = (CompoundTag) entry;
            UUID owner = CoastersBlockEntityNbt.uuid(cart, "Owner");
            LoadedSubLevel current = loaded.get(owner);
            if (current == null || !owners.add(owner) || !cart.contains("Links", Tag.TAG_LIST)) {
                throw CoastersBlockEntityNbt.invalid("missing or duplicate cart in manifest");
            }
            ListTag links = CoastersCartLinkNbt.remap(CoastersBlockEntityNbt.compounds(cart, "Links"), owner, id -> {
                LoadedSubLevel partner = loaded.get(id);
                return partner == null ? null : partner.subLevel().getUniqueId();
            }, scale);
            CompoundTag userData = current.subLevel().getUserDataTag();
            userData = userData == null ? new CompoundTag() : userData.copy();
            userData.put(CoastersCartLinkNbt.LINKS, links);
            current.subLevel().setUserDataTag(userData);
        }
    }

    public static void complete(ServerLevel level, Collection<LoadedSubLevel> loaded) {
        if (ModList.get().isLoaded("simulatedcoasters")) CoastersRuntimeBridge.complete(level, loaded);
    }
}
