package com.enxv.aeronauticsstructuretool.compat.sableschematicapi;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturePlan;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.fml.ModList;

import java.util.Map;
import java.util.UUID;

public final class SableBlueprintApiCompat {
    public static final String MOD_ID = "sable_schematic_api";

    private SableBlueprintApiCompat() {
    }

    public static CompoundTag capture(CapturePlan plan, BoundingBox3i aggregateBounds) {
        if (!isAvailable()) {
            return new CompoundTag();
        }
        try {
            return SableBlueprintApiCompatImpl.capture(plan, aggregateBounds);
        } catch (RuntimeException exception) {
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Synaxis Sable Blueprint API sidecar was not saved; native blueprint save will continue",
                    exception
            );
            return new CompoundTag();
        }
    }

    public static void restore(
            CompoundTag sidecar,
            Map<UUID, LoadedSubLevel> loadedSublevels,
            double scaleFactor
    ) {
        if (!isAvailable() || sidecar == null || sidecar.isEmpty()) {
            return;
        }
        try {
            SableBlueprintApiCompatImpl.restore(sidecar, loadedSublevels, scaleFactor);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Synaxis Sable Blueprint API sidecar could not be restored", exception);
        }
    }

    private static boolean isAvailable() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
