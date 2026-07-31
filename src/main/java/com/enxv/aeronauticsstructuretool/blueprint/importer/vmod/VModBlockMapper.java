package com.enxv.aeronauticsstructuretool.blueprint.importer.vmod;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;

final class VModBlockMapper {
    private static final String AERO_PROPELLER_BEARING_BLOCK = "aeronautics:propeller_bearing";
    private static final String AERO_GYRO_BEARING_BLOCK = "aeronautics:gyroscopic_propeller_bearing";

    private VModBlockMapper() {
    }

    static BlockState mapState(BlockState sourceState) {
        String sourceId = blockId(sourceState);
        List<String> candidates = new ArrayList<>();
        candidates.add(sourceId);

        String path = sourceState.getBlock().builtInRegistryHolder().key().location().getPath();
        if (sourceId.startsWith("vmod:")) {
            candidates.add("aeronautics:" + sourceId.substring("vmod:".length()));
        }
        if (sourceId.endsWith("balloon")) {
            candidates.add("aeronautics:balloon");
        }
        if (path.contains("gyroscopic_propeller_bearing") || path.contains("gyro_bearing")) {
            candidates.add(AERO_GYRO_BEARING_BLOCK);
        }
        if (path.contains("propeller_bearing")
                || path.contains("phys_bearing")
                || path.equals("mechanical_bearing")) {
            candidates.add(AERO_PROPELLER_BEARING_BLOCK);
        }

        for (String candidate : candidates) {
            BlockState mapped = tryMapState(candidate, sourceState);
            if (!mapped.isAir()) {
                return mapped;
            }
        }
        return Blocks.AIR.defaultBlockState();
    }

    static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    static CompoundTag normalizeBlockEntityTag(CompoundTag tag, BlockState targetState) {
        CompoundTag sourceTag = extractBlockEntityTag(tag);
        String targetBlockEntityId = canonicalBlockEntityId(targetState);
        if (targetBlockEntityId == null) {
            return sourceTag;
        }
        if (isAeronauticsBearingId(targetBlockEntityId)) {
            CompoundTag normalized = new CompoundTag();
            normalized.putString("id", targetBlockEntityId);
            return normalized;
        }
        CompoundTag normalized = sourceTag != null ? sourceTag.copy() : new CompoundTag();
        normalized.putString("id", targetBlockEntityId);
        return normalized;
    }

    private static BlockState tryMapState(String blockId, BlockState sourceState) {
        ResourceLocation location = ResourceLocation.tryParse(blockId);
        if (location == null || !BuiltInRegistries.BLOCK.containsKey(location)) {
            return Blocks.AIR.defaultBlockState();
        }
        BlockState targetState = BuiltInRegistries.BLOCK.get(location).defaultBlockState();
        for (Property<?> property : sourceState.getProperties()) {
            if (targetState.hasProperty(property)) {
                targetState = copyPropertyValue(sourceState, targetState, property);
            }
        }
        return targetState;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState copyPropertyValue(
            BlockState sourceState,
            BlockState targetState,
            Property property
    ) {
        Comparable value = sourceState.getValue(property);
        return targetState.trySetValue(property, value);
    }

    private static CompoundTag extractBlockEntityTag(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        if (tag.contains("blockEntity", Tag.TAG_COMPOUND)) {
            return tag.getCompound("blockEntity").copy();
        }
        if (tag.contains("BlockEntityTag", Tag.TAG_COMPOUND)) {
            return tag.getCompound("BlockEntityTag").copy();
        }
        return tag.contains("id", Tag.TAG_STRING) ? tag.copy() : null;
    }

    private static String canonicalBlockEntityId(BlockState state) {
        String blockId = blockId(state);
        if (AERO_PROPELLER_BEARING_BLOCK.equals(blockId)
                || AERO_GYRO_BEARING_BLOCK.equals(blockId)) {
            return blockId;
        }
        if (!(blockId.startsWith("minecraft:") || blockId.startsWith("create:"))) {
            return null;
        }
        if (blockId.endsWith("_shulker_box")) {
            return "minecraft:shulker_box";
        }
        return switch (blockId) {
            case "minecraft:chest",
                 "minecraft:trapped_chest",
                 "minecraft:barrel",
                 "minecraft:hopper",
                 "minecraft:dispenser",
                 "minecraft:dropper",
                 "minecraft:furnace",
                 "minecraft:blast_furnace",
                 "minecraft:smoker",
                 "minecraft:brewing_stand",
                 "minecraft:beehive",
                 "minecraft:campfire",
                 "minecraft:soul_campfire",
                 "minecraft:crafter",
                 "minecraft:lectern",
                 "minecraft:jukebox",
                 "minecraft:spawner",
                 "minecraft:decorated_pot",
                 "minecraft:chiseled_bookshelf",
                 "minecraft:ender_chest",
                 "create:mechanical_bearing",
                 "create:windmill_bearing",
                 "create:clockwork_bearing",
                 "create:item_vault",
                 "create:fluid_tank",
                 "create:creative_fluid_tank",
                 "create:depot",
                 "create:belt",
                 "create:stock_ticker",
                 "create:portable_storage_interface",
                 "create:portable_fluid_interface",
                 "create:content_observer",
                 "create:threshold_switch" -> blockId;
            default -> null;
        };
    }

    private static boolean isAeronauticsBearingId(String blockEntityId) {
        return AERO_PROPELLER_BEARING_BLOCK.equals(blockEntityId)
                || AERO_GYRO_BEARING_BLOCK.equals(blockEntityId);
    }
}
