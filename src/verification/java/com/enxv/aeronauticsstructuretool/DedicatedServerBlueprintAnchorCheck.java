package com.enxv.aeronauticsstructuretool;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;
import org.joml.Quaterniond;

import java.util.UUID;

public final class DedicatedServerBlueprintAnchorCheck {
    private static final double EPSILON = 1.0E-9D;

    private DedicatedServerBlueprintAnchorCheck() {
    }

    public static void run() throws Exception {
        CompoundTag withSavedAnchor = createSingleBlockBlueprint(true);
        PreviewBlueprintData exactPreview = PreviewBlueprintData.parse(withSavedAnchor, null);
        requireClose(
                0.5D,
                exactPreview.minimumRelativeBlockCenterY(new Quaterniond(), 1.0D),
                "saved dedicated-server plot anchor"
        );

        CompoundTag legacyBlueprint = createSingleBlockBlueprint(false);
        PreviewBlueprintData legacyPreview = PreviewBlueprintData.parse(legacyBlueprint, null);
        requireClose(
                0.5D,
                legacyPreview.minimumRelativeBlockCenterY(new Quaterniond(), 1.0D),
                "legacy root-relative plot anchor"
        );
    }

    private static CompoundTag createSingleBlockBlueprint(boolean includeLocalAnchor) {
        int chunkX = 1_280_064;
        int chunkZ = 1_286_336;
        double anchorX = chunkX * 16.0D + 8.0D;
        double anchorY = 448.0D;
        double anchorZ = chunkZ * 16.0D + 8.0D;

        CompoundTag blockStates = new CompoundTag();
        ListTag palette = new ListTag();
        palette.add(paletteEntry("minecraft:air"));
        palette.add(paletteEntry("minecraft:stone"));
        blockStates.put("palette", palette);
        long[] data = new long[256];
        data[0] = 1L;
        blockStates.putLongArray("data", data);

        CompoundTag section = new CompoundTag();
        section.put("block_states", blockStates);
        CompoundTag sections = new CompoundTag();
        sections.put("32", section);
        CompoundTag chunk = new CompoundTag();
        chunk.put("sections", sections);
        CompoundTag chunks = new CompoundTag();
        chunks.put(Long.toString(ChunkPos.asLong(chunkX, chunkZ)), chunk);
        CompoundTag plot = new CompoundTag();
        plot.put("chunks", chunks);

        UUID sublevelId = UUID.randomUUID();
        CompoundTag sublevel = new CompoundTag();
        sublevel.putUUID("sublevel_id", sublevelId);
        sublevel.put("plot", plot);
        sublevel.put("relative_position", vector(anchorX, anchorY, anchorZ));
        if (includeLocalAnchor) {
            sublevel.put("local_anchor", vector(anchorX, anchorY, anchorZ));
        }
        ListTag sublevels = new ListTag();
        sublevels.add(sublevel);

        CompoundTag root = new CompoundTag();
        root.putString("format", "enxv_aeronautics_plot_print_v8");
        root.putUUID("root_sublevel", sublevelId);
        root.putInt("source_min_build_height", -64);
        root.put("sublevels", sublevels);
        return root;
    }

    private static CompoundTag paletteEntry(String name) {
        CompoundTag entry = new CompoundTag();
        entry.putString("Name", name);
        return entry;
    }

    private static CompoundTag vector(double x, double y, double z) {
        CompoundTag vector = new CompoundTag();
        vector.putDouble("x", x);
        vector.putDouble("y", y);
        vector.putDouble("z", z);
        return vector;
    }

    private static void requireClose(double expected, double actual, String label) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new IllegalStateException(label + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
