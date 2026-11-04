package com.enxv.aeronauticsstructuretool.compat.coasters;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** One-shot invalidations consumed inside Coasters' package-private cache implementations. */
public final class CoastersCartCacheRefresh implements AutoCloseable {
    private static final ThreadLocal<CoastersCartCacheRefresh> ACTIVE = new ThreadLocal<>();
    private final Set<UUID> bodies;
    private final ResourceKey<Level> dimension;
    private boolean indexPending = true;

    private CoastersCartCacheRefresh(ResourceKey<Level> dimension, Set<UUID> bodies) {
        if (ACTIVE.get() != null) throw new IllegalStateException("Nested coaster cache refresh");
        this.dimension = dimension;
        this.bodies = new HashSet<>(bodies);
        ACTIVE.set(this);
    }

    public static CoastersCartCacheRefresh begin(ResourceKey<Level> dimension, Set<UUID> bodies) {
        return new CoastersCartCacheRefresh(dimension, bodies);
    }

    public static boolean consumeBody(UUID id) {
        CoastersCartCacheRefresh scope = ACTIVE.get();
        return scope != null && scope.bodies.remove(id);
    }

    public static boolean consumeIndex(ResourceKey<Level> dimension) {
        CoastersCartCacheRefresh scope = ACTIVE.get();
        if (scope == null || !scope.indexPending || !scope.dimension.equals(dimension)) return false;
        scope.indexPending = false;
        return true;
    }

    @Override public void close() { ACTIVE.remove(); }
}
