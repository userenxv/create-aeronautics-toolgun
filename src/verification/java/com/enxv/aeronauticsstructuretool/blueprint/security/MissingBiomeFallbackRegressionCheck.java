package com.enxv.aeronauticsstructuretool.blueprint.security;

import com.enxv.aeronauticsstructuretool.blueprint.codec.NativeBlueprintFormat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biomes;

public final class MissingBiomeFallbackRegressionCheck {
    private MissingBiomeFallbackRegressionCheck() {
    }

    public static void main(String[] args) throws Exception {
        verifyMissingBiomeFallsBackWithoutRejectingBlueprint();
        verifyAvailableBiomeRemainsUntouched();
    }

    private static void verifyMissingBiomeFallsBackWithoutRejectingBlueprint() throws Exception {
        CompoundTag root = rootWithBiome("biomesoplenty:prairie");
        MissingRegistryContentSanitizer.Result result = MissingRegistryContentSanitizer.sanitizeNative(
                root,
                id -> id.equals(Biomes.PLAINS.location())
        );
        CompoundTag plot = plot(root);
        require(
                plot.getString("biome").equals(Biomes.PLAINS.location().toString()),
                "missing Plot biome was not replaced with minecraft:plains"
        );
        require(
                result.replacedBiomes().getOrDefault("biomesoplenty:prairie", 0L) == 1L,
                "missing Plot biome replacement was not reported"
        );

        MissingRegistryContentSanitizer.Result repeated = MissingRegistryContentSanitizer.sanitizeNative(
                root,
                id -> id.equals(Biomes.PLAINS.location())
        );
        require(repeated.isEmpty(), "biome fallback must be idempotent");
    }

    private static void verifyAvailableBiomeRemainsUntouched() throws Exception {
        CompoundTag root = rootWithBiome("minecraft:desert");
        MissingRegistryContentSanitizer.Result result = MissingRegistryContentSanitizer.sanitizeNative(
                root,
                id -> id.equals(ResourceLocation.withDefaultNamespace("desert"))
                        || id.equals(Biomes.PLAINS.location())
        );
        require(plot(root).getString("biome").equals("minecraft:desert"),
                "available Plot biome was changed");
        require(result.isEmpty(), "available Plot biome produced a warning");
    }

    private static CompoundTag rootWithBiome(String biome) {
        CompoundTag plot = new CompoundTag();
        plot.putString("biome", biome);
        plot.put("chunks", new CompoundTag());
        CompoundTag sublevel = new CompoundTag();
        sublevel.put(NativeBlueprintFormat.PLOT_TAG, plot);
        ListTag sublevels = new ListTag();
        sublevels.add(sublevel);
        CompoundTag root = new CompoundTag();
        root.putInt(NativeBlueprintFormat.SOURCE_MIN_BUILD_HEIGHT_TAG, -64);
        root.put(NativeBlueprintFormat.SUBLEVELS_TAG, sublevels);
        return root;
    }

    private static CompoundTag plot(CompoundTag root) {
        return root.getList(NativeBlueprintFormat.SUBLEVELS_TAG, Tag.TAG_COMPOUND)
                .getCompound(0)
                .getCompound(NativeBlueprintFormat.PLOT_TAG);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
