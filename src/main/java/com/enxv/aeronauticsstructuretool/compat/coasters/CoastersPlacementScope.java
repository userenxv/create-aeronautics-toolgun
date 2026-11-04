package com.enxv.aeronauticsstructuretool.compat.coasters;

import java.util.Set;
import java.util.UUID;

/** Defers only newly allocated rivets while their hosts' plots are still loading. */
public final class CoastersPlacementScope implements AutoCloseable {
    private static final ThreadLocal<Set<UUID>> LOADING = new ThreadLocal<>();

    private CoastersPlacementScope(Set<UUID> ids) {
        if (LOADING.get() != null) throw new IllegalStateException("Nested coaster plot loading");
        LOADING.set(Set.copyOf(ids));
    }

    public static CoastersPlacementScope begin(Set<UUID> ids) {
        return new CoastersPlacementScope(ids);
    }

    public static boolean defers(UUID id) {
        Set<UUID> ids = LOADING.get();
        return ids != null && ids.contains(id);
    }

    @Override
    public void close() {
        LOADING.remove();
    }
}
