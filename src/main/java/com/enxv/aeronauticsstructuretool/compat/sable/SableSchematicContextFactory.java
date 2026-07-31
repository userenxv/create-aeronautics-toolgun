package com.enxv.aeronauticsstructuretool.compat.sable;

import com.enxv.aeronauticsstructuretool.blueprint.geometry.LoadedSubLevelCoordinates;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturePlan;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturedSubLevel;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import com.enxv.aeronauticsstructuretool.blueprint.placement.LoadedSubLevelAlignment;

import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public final class SableSchematicContextFactory {
    private SableSchematicContextFactory() {
    }

    public static SubLevelSchematicSerializationContext createSaveContext(
            CapturePlan plan,
            BoundingBox3i aggregateBounds
    ) {
        SubLevelSchematicSerializationContext context = new SubLevelSchematicSerializationContext(
                SubLevelSchematicSerializationContext.Type.SAVE,
                aggregateBounds
        );
        it.unimi.dsi.fastutil.Function<BlockPos, BlockPos> identity = wrap(position -> position);
        context.setPlaceTransform(identity);
        context.setSetupTransform(identity);
        for (CapturedSubLevel captured : plan.sublevels()) {
            BlockPos plotCenter = captured.subLevel().getPlot().getCenterBlock();
            context.getMappings().put(
                    captured.subLevel().getUniqueId(),
                    new SubLevelSchematicSerializationContext.SchematicMapping(
                            captured.relativePosition(),
                            captured.relativeOrientation(),
                            captured.blueprintId(),
                            wrap(position -> position.subtract(plotCenter))
                    )
            );
        }
        return context;
    }

    public static SubLevelSchematicSerializationContext createPlaceContext(
            Map<UUID, LoadedSubLevel> loadedSublevels,
            BoundingBox3i aggregateBounds,
            UUID rootBlueprintId
    ) throws IOException {
        SubLevelSchematicSerializationContext context = new SubLevelSchematicSerializationContext(
                SubLevelSchematicSerializationContext.Type.PLACE,
                aggregateBounds
        );
        LoadedSubLevel rootLoaded = LoadedSubLevelAlignment.requireRoot(loadedSublevels, rootBlueprintId);
        context.setPlaceTransform(wrap(position -> LoadedSubLevelCoordinates.toGlobalBlockPos(rootLoaded, position)));
        context.setSetupTransform(wrap(position -> LoadedSubLevelCoordinates.toGlobalBlockPos(rootLoaded, position)));
        for (LoadedSubLevel loaded : loadedSublevels.values()) {
            BlockPos plotCenter = loaded.subLevel().getPlot().getCenterBlock();
            context.getMappings().put(
                    loaded.saved().blueprintId(),
                    new SubLevelSchematicSerializationContext.SchematicMapping(
                            loaded.subLevel().logicalPose().position(),
                            loaded.subLevel().logicalPose().orientation(),
                            loaded.subLevel().getUniqueId(),
                            wrap(plotCenter::offset)
                    )
            );
        }
        return context;
    }

    private static it.unimi.dsi.fastutil.Function<BlockPos, BlockPos> wrap(Function<BlockPos, BlockPos> function) {
        return new it.unimi.dsi.fastutil.Function<>() {
            @Override
            public BlockPos get(Object key) {
                return function.apply((BlockPos) key);
            }
        };
    }
}
