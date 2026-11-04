package com.enxv.aeronauticsstructuretool.compat.coasters;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashSet;
import java.util.UUID;
import java.util.function.UnaryOperator;

final class CoastersCartLinkNbt {
    static final String LINKS = "simulatedcoasters:cart_train_links";
    private static final String LEGACY = "simulatedcoasters:cart_train_link";

    private CoastersCartLinkNbt() {}

    static ListTag read(CompoundTag userData) {
        if (userData == null) return new ListTag();
        if (userData.contains(LINKS)) {
            if (!userData.contains(LINKS, Tag.TAG_LIST)) throw CoastersBlockEntityNbt.invalid("invalid cart links");
            ListTag list = userData.getList(LINKS, Tag.TAG_COMPOUND);
            if (list.size() != ((ListTag) userData.get(LINKS)).size()) throw CoastersBlockEntityNbt.invalid("invalid cart link entries");
            return list.copy();
        }
        ListTag list = new ListTag();
        if (userData.contains(LEGACY)) {
            if (!userData.contains(LEGACY, Tag.TAG_COMPOUND)) throw CoastersBlockEntityNbt.invalid("invalid legacy cart link");
            list.add(userData.getCompound(LEGACY).copy());
        }
        return list;
    }

    static ListTag remap(ListTag links, UUID owner, UnaryOperator<UUID> map, double scale) {
        if (links.size() > 2) throw CoastersBlockEntityNbt.invalid("cart has more than two couplings");
        ListTag result = new ListTag();
        HashSet<UUID> seen = new HashSet<>();
        for (Tag entry : links) {
            if (!(entry instanceof CompoundTag link)) throw CoastersBlockEntityNbt.invalid("invalid cart coupling");
            UUID partner = CoastersBlockEntityNbt.uuid(link, "partner");
            double distance = link.getDouble("distance") * scale;
            if (partner.equals(owner) || !seen.add(partner) || !Double.isFinite(distance) || distance <= 0) {
                throw CoastersBlockEntityNbt.invalid("invalid cart coupling partner or distance");
            }
            UUID mapped = map.apply(partner);
            if (mapped == null) throw CoastersBlockEntityNbt.invalid("coupled cart is outside blueprint");
            CompoundTag mappedLink = link.copy();
            mappedLink.putUUID("partner", mapped);
            mappedLink.putDouble("distance", distance);
            result.add(mappedLink);
        }
        return result;
    }
}
