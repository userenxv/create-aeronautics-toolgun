package com.enxv.aeronauticsstructuretool.compat.sableschematicapi;

import com.enxv.aeronauticsstructuretool.blueprint.codec.NativeBlueprintFormat;
import com.enxv.aeronauticsstructuretool.blueprint.compat.BlockEntityCompatibilityPipeline;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.LoadedSubLevelCoordinates;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockTransform;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturePlan;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockPlaceContext;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockRef;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockSaveContext;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlacePhase;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlaceSession;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintSavePhase;
import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintSaveSession;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintBlockMapper;
import dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintMapperRegistry;
import dev.rew1nd.sableschematicapi.api.blueprint.SubLevelSaveFrame;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

import java.util.Map;
import java.util.UUID;

/** Reuses Fire Control's own binding codecs without applying unrelated API mappers twice. */
public final class FireControlBlueprintCompat {
    private static final String FRAMES = "AST_FireControlFrames";
    private static final String MAPPER = "AST_FireControlMapper";

    private FireControlBlueprintCompat() {}

    public static void capture(CompoundTag root, CapturePlan plan) {
        if (!ModList.get().isLoaded("create_fire_control")) return;
        if (!ModList.get().isLoaded(SableBlueprintApiCompat.MOD_ID)) {
            // Do not silently produce broken Fire Control bindings when its optional codec API is absent.
            for (Tag entry : root.getList(NativeBlueprintFormat.SUBLEVELS_TAG, Tag.TAG_COMPOUND)) {
                BlockEntityCompatibilityPipeline.forEachBlockEntity(((CompoundTag) entry).getCompound(NativeBlueprintFormat.PLOT_TAG), tag -> {
                    if (tag.getString("id").startsWith("create_fire_control:")) {
                        throw new IllegalStateException("Saving Fire Control devices requires Sable Schematic API 0.4.0 or newer");
                    }
                });
            }
            return;
        }
        Impl.capture(root, plan);
    }

    @FunctionalInterface
    public interface Placement {
        void preparePlot(CompoundTag plot, LoadedSubLevel loaded);
    }

    public static Placement preparePlacement(CompoundTag root, Map<UUID, LoadedSubLevel> loaded) {
        if (!root.contains(FRAMES, Tag.TAG_LIST)) return (plot, sublevel) -> {};
        if (!ModList.get().isLoaded("create_fire_control") || !ModList.get().isLoaded(SableBlueprintApiCompat.MOD_ID)) {
            throw new IllegalStateException("Fire Control blueprint bindings require Fire Control and Sable Schematic API");
        }
        return Impl.prepare(root, loaded);
    }

    private static final class Impl {
        // Lists omitted by Fire Control 1.0.1's controller mapper.
        private static final java.util.List<String> EXTRA_BINDING_LISTS = java.util.List.of(
                "AircraftRocketPodBindings", "UnassignedBearingBindings", "MissileDirectionAnchorBindings");

        private static void saveExtraBindings(BlueprintBlockSaveContext context, CompoundTag tag) {
            if (!"create_fire_control:stabilizer_controller".equals(tag.getString("id"))) return;
            for (String key : EXTRA_BINDING_LISTS) {
                ListTag portable = new ListTag();
                for (Tag entry : tag.getList(key, Tag.TAG_COMPOUND)) {
                    CompoundTag binding = (CompoundTag) entry;
                    if (!binding.contains("Pos", Tag.TAG_LONG)) throw new IllegalArgumentException("Invalid Fire Control binding " + key);
                    context.blockRef(BlockPos.of(binding.getLong("Pos"))).ifPresent(ref -> {
                        CompoundTag mapped = binding.copy();
                        mapped.remove("Pos");
                        mapped.remove("SubLevel");
                        mapped.remove("Dimension");
                        mapped.putInt("frame", ref.subLevelId());
                        mapped.putLong("pos", ref.localPos().asLong());
                        portable.add(mapped);
                    });
                }
                tag.remove(key);
                tag.put("AST_FireControl_" + key, portable);
            }
        }

        private static void loadExtraBindings(BlueprintBlockPlaceContext context, CompoundTag tag) {
            if (!"create_fire_control:stabilizer_controller".equals(tag.getString("id"))) return;
            for (String key : EXTRA_BINDING_LISTS) {
                ListTag mapped = new ListTag();
                for (Tag entry : tag.getList("AST_FireControl_" + key, Tag.TAG_COMPOUND)) {
                    CompoundTag binding = ((CompoundTag) entry).copy();
                    int frame = binding.getInt("frame");
                    BlockPos pos = context.mapBlock(new BlueprintBlockRef(frame, BlockPos.of(binding.getLong("pos"))));
                    if (pos == null) throw new IllegalArgumentException("Missing Fire Control binding frame");
                    binding.remove("frame");
                    binding.remove("pos");
                    binding.putLong("Pos", pos.asLong());
                    binding.putUUID("SubLevel", context.session().placedSubLevel(frame).getUniqueId());
                    binding.putString("Dimension", context.level().dimension().location().toString());
                    if (binding.hasUUID("WirelessProcessorIdentity")) {
                        binding.putUUID("WirelessProcessorIdentity", context.allocateMappedUuid(binding.getUUID("WirelessProcessorIdentity")));
                    }
                    mapped.add(binding);
                }
                tag.put(key, mapped);
                tag.remove("AST_FireControl_" + key);
            }
        }

        // Fire Control 1.0.1 has no registered mapper for its display console.
        private static final SableBlueprintBlockMapper CONSOLE_MAPPER = new SableBlueprintBlockMapper() {
            private static final String PORTABLE = "AST_FireControlDisplay";

            @Override
            public CompoundTag save(BlueprintBlockSaveContext context, CompoundTag original) {
                CompoundTag result = original.copy();
                CompoundTag binding = result.getCompound("Display");
                result.remove("Display");
                result.remove(PORTABLE);
                if (binding.contains("Pos", Tag.TAG_LONG)) {
                    context.blockRef(BlockPos.of(binding.getLong("Pos"))).ifPresent(ref -> {
                        CompoundTag portable = binding.copy();
                        portable.remove("Pos");
                        portable.remove("SubLevel");
                        portable.remove("Dimension");
                        portable.putInt("frame", ref.subLevelId());
                        portable.putLong("pos", ref.localPos().asLong());
                        result.put(PORTABLE, portable);
                    });
                }
                return result;
            }

            @Override
            public void beforeLoadBlockEntity(BlueprintBlockPlaceContext context, CompoundTag tag) {
                CompoundTag binding = tag.getCompound(PORTABLE).copy();
                tag.remove(PORTABLE);
                tag.remove("Display");
                if (binding.isEmpty()) return;
                int frame = binding.getInt("frame");
                BlockPos mapped = context.mapBlock(new BlueprintBlockRef(frame, BlockPos.of(binding.getLong("pos"))));
                if (mapped == null) throw new IllegalArgumentException("Missing Fire Control display frame");
                binding.remove("frame");
                binding.remove("pos");
                binding.putLong("Pos", mapped.asLong());
                binding.putUUID("SubLevel", context.session().placedSubLevel(frame).getUniqueId());
                binding.putString("Dimension", context.level().dimension().location().toString());
                tag.put("Display", binding);
            }
        };

        // The native Simulated pipeline already remaps bearings; only discard Fire Control's
        // absolute assembly-pivot cache, just as its own wrapper mapper does.
        private static final SableBlueprintBlockMapper BEARING_CACHE_MAPPER = new SableBlueprintBlockMapper() {
            private void clear(CompoundTag tag) {
                for (String key : java.util.List.of("CreateFireControlAssemblyPivotX",
                        "CreateFireControlAssemblyPivotY", "CreateFireControlAssemblyPivotZ")) {
                    tag.remove(key);
                    tag.getCompound("NeoForgeData").remove(key);
                }
            }

            @Override
            public CompoundTag save(BlueprintBlockSaveContext context, CompoundTag original) {
                CompoundTag result = original.copy();
                clear(result);
                return result;
            }

            @Override
            public void beforeLoadBlockEntity(BlueprintBlockPlaceContext context, CompoundTag tag) {
                clear(tag);
            }
        };

        private static SableBlueprintBlockMapper mapper(CompoundTag tag) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
            if (ResourceLocation.parse("simulated:swivel_bearing").equals(id)) return BEARING_CACHE_MAPPER;
            if (ResourceLocation.parse("create_fire_control:connected_display_console").equals(id)) return CONSOLE_MAPPER;
            if (id == null || !id.getNamespace().equals("create_fire_control")) return null;
            return BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(id)
                    .map(SableBlueprintMapperRegistry::get).orElse(null);
        }

        static void capture(CompoundTag root, CapturePlan plan) {
            var first = plan.sublevels().getFirst().subLevel();
            BlueprintSaveSession session = new BlueprintSaveSession(first.getLevel(),
                    first.logicalPose().position(), new BoundingBox3d(first.boundingBox()));
            ListTag frames = new ListTag();
            for (int index = 0; index < plan.sublevels().size(); index++) {
                var captured = plan.sublevels().get(index);
                var sub = captured.subLevel();
                BoundingBox3i bounds = new BoundingBox3i(sub.getPlot().getBoundingBox());
                BlockPos origin = new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ());
                session.addFrame(new SubLevelSaveFrame(index, sub.getUniqueId(), sub, bounds, origin,
                        new Pose3d(sub.logicalPose())));
                CompoundTag frame = new CompoundTag();
                frame.putUUID("native", captured.blueprintId());
                frame.putUUID("source", sub.getUniqueId());
                frame.putLong("origin", PlotBlockTransform.capture(sub).toSavedLocalBlockPos(origin).asLong());
                frames.add(frame);
            }
            session.setPhase(BlueprintSavePhase.SAVE_BLOCKS);
            ListTag plots = root.getList(NativeBlueprintFormat.SUBLEVELS_TAG, Tag.TAG_COMPOUND);
            boolean[] used = {false};
            for (int index = 0; index < plan.sublevels().size(); index++) {
                var frame = session.frames().get(index);
                var transform = PlotBlockTransform.capture(frame.subLevel());
                CompoundTag plot = plots.getCompound(index).getCompound(NativeBlueprintFormat.PLOT_TAG);
                BlockEntityCompatibilityPipeline.forEachBlockEntity(plot, tag -> {
                    var mapper = mapper(tag);
                    if (mapper == null) return;
                    BlockPos local = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
                    BlockPos storage = transform.toGlobalBlockPos(local);
                    var entity = session.level().getBlockEntity(storage);
                    if (entity == null) throw new IllegalStateException("Missing live Fire Control block entity at " + storage);
                    var context = new BlueprintBlockSaveContext(session, frame, storage,
                            frame.toLocal(storage), entity.getBlockState(), entity);
                    CompoundTag converted = mapper.save(context, tag.copy());
                    if (converted == null) throw new IllegalStateException("Fire Control mapper discarded block entity at " + storage);
                    saveExtraBindings(context, converted);
                    for (String key : java.util.Set.copyOf(tag.getAllKeys())) tag.remove(key);
                    tag.merge(converted);
                    CompoundTag marker = new CompoundTag();
                    marker.putInt("frame", frame.blueprintId());
                    marker.put("state", NbtUtils.writeBlockState(entity.getBlockState()));
                    tag.put(MAPPER, marker);
                    used[0] = true;
                });
            }
            if (used[0]) root.put(FRAMES, frames);
        }

        static Placement prepare(CompoundTag root, Map<UUID, LoadedSubLevel> loaded) {
            var level = loaded.values().iterator().next().subLevel().getLevel();
            BlueprintPlaceSession session = new BlueprintPlaceSession(level, new org.joml.Vector3d(), new CompoundTag());
            ListTag frames = root.getList(FRAMES, Tag.TAG_COMPOUND);
            if (frames.size() != loaded.size()) throw new IllegalArgumentException("Fire Control frame count mismatch");
            java.util.Set<UUID> nativeIds = new java.util.HashSet<>();
            java.util.Set<UUID> sourceIds = new java.util.HashSet<>();
            for (int index = 0; index < frames.size(); index++) {
                CompoundTag frame = frames.getCompound(index);
                if (!frame.hasUUID("native") || !frame.hasUUID("source") || !frame.contains("origin", Tag.TAG_LONG)) {
                    throw new IllegalArgumentException("Invalid Fire Control blueprint frame");
                }
                var placed = loaded.get(frame.getUUID("native"));
                if (placed == null) throw new IllegalArgumentException("Unplaced Fire Control blueprint frame");
                if (!nativeIds.add(frame.getUUID("native")) || !sourceIds.add(frame.getUUID("source"))) {
                    throw new IllegalArgumentException("Duplicate Fire Control blueprint frame");
                }
                session.mapSubLevel(index, frame.getUUID("source"), placed.subLevel(),
                        LoadedSubLevelCoordinates.toGlobalBlockPos(placed, BlockPos.of(frame.getLong("origin"))));
            }
            session.setPhase(BlueprintPlacePhase.LOAD_BLOCK_ENTITIES);
            return (plot, sublevel) -> preparePlot(session, plot, sublevel);
        }

        static void preparePlot(BlueprintPlaceSession session, CompoundTag plot, LoadedSubLevel loaded) {
            BlockEntityCompatibilityPipeline.forEachBlockEntity(plot, tag -> {
                if (!tag.contains(MAPPER, Tag.TAG_COMPOUND)) return;
                var mapper = mapper(tag);
                if (mapper == null) throw new IllegalStateException("Fire Control blueprint mapper unavailable for " + tag.getString("id"));
                CompoundTag marker = tag.getCompound(MAPPER);
                if (!marker.contains("frame", Tag.TAG_INT) || !marker.contains("state", Tag.TAG_COMPOUND)) {
                    throw new IllegalArgumentException("Invalid Fire Control block mapper data");
                }
                int frame = marker.getInt("frame");
                if (session.placedSubLevel(frame) != loaded.subLevel()) throw new IllegalArgumentException("Fire Control block/frame mismatch");
                BlockPos storage = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
                var state = NbtUtils.readBlockState(session.level().holderLookup(net.minecraft.core.registries.Registries.BLOCK), marker.getCompound("state"));
                var context = new BlueprintBlockPlaceContext(session, loaded.subLevel(), frame,
                        session.subLevelUuidMap().entrySet().stream()
                                .filter(entry -> entry.getValue().equals(loaded.subLevel().getUniqueId()))
                                .findFirst().orElseThrow().getKey(),
                        storage.subtract(session.placedBlockOrigin(frame)), storage, state);
                mapper.beforeLoadBlockEntity(context, tag);
                loadExtraBindings(context, tag);
                tag.remove(MAPPER);
            });
        }
    }
}
