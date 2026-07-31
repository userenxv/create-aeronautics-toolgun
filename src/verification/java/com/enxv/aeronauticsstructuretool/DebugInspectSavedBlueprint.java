package com.enxv.aeronauticsstructuretool;

import com.enxv.aeronauticsstructuretool.blueprint.security.BlueprintBlockEntitySanitizer;
import com.enxv.aeronauticsstructuretool.blueprint.codec.BlueprintArchiveCodec;
import com.enxv.aeronauticsstructuretool.blueprint.codec.NativeBlueprintReader;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class DebugInspectSavedBlueprint {
    private DebugInspectSavedBlueprint() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("usage: -PinspectFile=<path>");
            return;
        }
        byte[] fileContents = Files.readAllBytes(Path.of(args[0]));
        {
            CompoundTag root = BlueprintArchiveCodec.decode(fileContents);
            NativeBlueprintReader.read(root);
            System.out.println("native_validation=ok");
            if (args.length > 1 && "--validate-only".equals(args[1])) {
                return;
            }
            System.out.println("has_format=" + root.contains("format", Tag.TAG_STRING));
            if (root.contains("format", Tag.TAG_STRING)) {
                System.out.println("format=" + root.getString("format"));
            }
            System.out.println("has_name=" + root.contains("name", Tag.TAG_STRING));
            if (root.contains("name", Tag.TAG_STRING)) {
                System.out.println("name=" + root.getString("name"));
            }
            System.out.println("has_source_min_build_height=" + root.contains("source_min_build_height", Tag.TAG_INT));
            if (root.contains("source_min_build_height", Tag.TAG_INT)) {
                System.out.println("source_min_build_height=" + root.getInt("source_min_build_height"));
            }
            System.out.println("has_root_sublevel=" + root.hasUUID("root_sublevel"));
            if (root.hasUUID("root_sublevel")) {
                System.out.println("root_sublevel=" + root.getUUID("root_sublevel"));
            }
            System.out.println("has_root_orientation=" + root.contains("root_orientation", Tag.TAG_COMPOUND));
            System.out.println("has_root_rotation_offset=" + root.contains("root_rotation_offset", Tag.TAG_COMPOUND));
            CompoundTag sanitizedRoot = root.copy();
            BlueprintBlockEntitySanitizer.Result sanitization = BlueprintBlockEntitySanitizer.sanitize(sanitizedRoot);
            System.out.println("sanitization_sign_click_events_removed=" + sanitization.signClickEventsRemoved());
            System.out.println("sanitization_invalid_sign_messages_cleared=" + sanitization.invalidSignMessagesCleared());
            System.out.println("sanitization_diesel_shafts_reset=" + sanitization.dieselShaftsReset());
            System.out.println("sanitization_diesel_engine_references_removed=" + sanitization.dieselEngineReferencesRemoved());
            System.out.println("sublevels=" + root.getList("sublevels", Tag.TAG_COMPOUND).size());
            System.out.println("has_material_summary=" + root.contains("AST_MaterialSummary", Tag.TAG_COMPOUND));
            printRopeMatches(root, "root");
            System.out.println("block_entities:");
            ListTag sublevels = root.getList("sublevels", Tag.TAG_COMPOUND);
            for (int i = 0; i < sublevels.size(); i++) {
                CompoundTag sublevel = sublevels.getCompound(i);
                CompoundTag plot = sublevel.getCompound("plot");
                CompoundTag chunks = plot.getCompound("chunks");
                printDriveByWireSnapshot(plot, i);
                System.out.println("  sublevel[" + i + "] has_sublevel_id=" + sublevel.hasUUID("sublevel_id"));
                if (sublevel.hasUUID("sublevel_id")) {
                    System.out.println("  sublevel[" + i + "] sublevel_id=" + sublevel.getUUID("sublevel_id"));
                }
                System.out.println("  sublevel[" + i + "] has_relative_position="
                        + sublevel.contains("relative_position", Tag.TAG_COMPOUND));
                System.out.println("  sublevel[" + i + "] has_relative_rotation_offset="
                        + sublevel.contains("relative_rotation_offset", Tag.TAG_COMPOUND));
                System.out.println("  sublevel[" + i + "] has_relative_orientation="
                        + sublevel.contains("relative_orientation", Tag.TAG_COMPOUND));
                System.out.println("  sublevel[" + i + "] relative_position=" + sublevel.getCompound("relative_position"));
                System.out.println("  sublevel[" + i + "] local_anchor="
                        + (sublevel.contains("local_anchor", Tag.TAG_COMPOUND) ? sublevel.getCompound("local_anchor") : "<missing>"));
                if (!chunks.isEmpty()) {
                    int minChunkX = Integer.MAX_VALUE;
                    int minChunkZ = Integer.MAX_VALUE;
                    int maxChunkX = Integer.MIN_VALUE;
                    int maxChunkZ = Integer.MIN_VALUE;
                    for (String chunkKey : chunks.getAllKeys()) {
                        long packedChunkPos = Long.parseLong(chunkKey);
                        minChunkX = Math.min(minChunkX, ChunkPos.getX(packedChunkPos));
                        minChunkZ = Math.min(minChunkZ, ChunkPos.getZ(packedChunkPos));
                        maxChunkX = Math.max(maxChunkX, ChunkPos.getX(packedChunkPos));
                        maxChunkZ = Math.max(maxChunkZ, ChunkPos.getZ(packedChunkPos));
                    }
                    System.out.println("  sublevel[" + i + "] chunk_bounds=[" + minChunkX + "," + minChunkZ + "]..[" + maxChunkX + "," + maxChunkZ + "]");
                }
                int count = 0;
                for (String chunkKey : chunks.getAllKeys()) {
                    ListTag blockEntities = chunks.getCompound(chunkKey).getList("block_entities", Tag.TAG_COMPOUND);
                    count += blockEntities.size();
                }
                System.out.println("  sublevel[" + i + "] chunks=" + chunks.getAllKeys().size() + " block_entities=" + count);
                for (String chunkKey : chunks.getAllKeys()) {
                    ListTag blockEntities = chunks.getCompound(chunkKey).getList("block_entities", Tag.TAG_COMPOUND);
                    for (int j = 0; j < blockEntities.size(); j++) {
                        CompoundTag blockEntity = blockEntities.getCompound(j);
                        System.out.println("    chunk=" + chunkKey + " be[" + j + "] id=" + blockEntity.getString("id") + " keys=" + blockEntity.getAllKeys());
                        if (blockEntity.contains("Upgrade", Tag.TAG_STRING)) {
                            System.out.println("      upgrade=" + blockEntity.getString("Upgrade")
                                    + " material_item=" + BlueprintMaterialSummary.knownDieselUpgradeItemId(blockEntity.getString("Upgrade")));
                        }
                        printRopeData(blockEntity, "      ");
                        printInterestingItemData(blockEntity, "      ");
                    }
                }
                ListTag runtimeContraptions = sublevel.getList("runtime_contraptions", Tag.TAG_COMPOUND);
                System.out.println("  sublevel[" + i + "] runtime_contraptions=" + runtimeContraptions.size());
                for (int j = 0; j < runtimeContraptions.size(); j++) {
                    CompoundTag runtime = runtimeContraptions.getCompound(j);
                    boolean hasSnapshot = runtime.contains(BlueprintMaterialSummary.RUNTIME_ITEMS_TAG, Tag.TAG_LIST);
                    Map<String, Long> runtimeItems = hasSnapshot
                            ? BlueprintMaterialSummary.readItemCounts(runtime.getList(BlueprintMaterialSummary.RUNTIME_ITEMS_TAG, Tag.TAG_COMPOUND))
                            : Map.of();
                    CompoundTag contraption = runtime.getCompound("contraption");
                    Tag mountedItems = contraption.get("items");
                    Tag legacyStorage = contraption.get("Storage");
                    System.out.println("    runtime[" + j + "] kind=" + runtime.getString("kind")
                            + " material_snapshot=" + hasSnapshot
                            + " item_types=" + runtimeItems.size()
                            + " total_items=" + runtimeItems.values().stream().mapToLong(Long::longValue).sum()
                            + " mounted_items_tag=" + (mountedItems == null ? "missing" : mountedItems.getType().getName())
                            + " legacy_storage_tag=" + (legacyStorage == null ? "missing" : legacyStorage.getType().getName()));
                }
            }
        }
    }

    private static void printDriveByWireSnapshot(CompoundTag plot, int sublevelIndex) {
        if (!plot.contains("AST_DriveByWireWireNetwork", Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag snapshot = plot.getCompound("AST_DriveByWireWireNetwork");
        ListTag connections = snapshot.getList("Connections", Tag.TAG_COMPOUND);
        System.out.println("  sublevel[" + sublevelIndex + "] drivebywire_snapshot_version="
                + snapshot.getInt("SnapshotVersion") + " connections=" + connections.size()
                + " owner=" + (snapshot.hasUUID("OwnerSubLevel")
                ? snapshot.getUUID("OwnerSubLevel") : "<missing>"));
        for (int i = 0; i < connections.size(); i++) {
            CompoundTag connection = connections.getCompound(i);
            System.out.println("    connection[" + i + "] source=" + BlockPos.of(connection.getLong("Source"))
                    + " source_owner=" + (connection.hasUUID("SourceOwnerSubLevel")
                    ? connection.getUUID("SourceOwnerSubLevel") : "<world>"));
            System.out.println("      sink=" + BlockPos.of(connection.getLong("Sink"))
                    + " sink_owner=" + (connection.hasUUID("SinkOwnerSubLevel")
                    ? connection.getUUID("SinkOwnerSubLevel") : "<world>")
                    + " direction=" + connection.getByte("Direction")
                    + " channel=" + connection.getString("Channel"));
        }
    }

    private static void printRopeMatches(Tag tag, String path) {
        if (tag instanceof CompoundTag compound) {
            if (compound.contains("HasRopeAttached") || compound.contains("OwnStrand") || compound.contains("Strand")) {
                System.out.println("rope_match=" + path + " keys=" + compound.getAllKeys());
                printRopeData(compound, "  ");
            }
            for (String key : compound.getAllKeys()) {
                Tag child = compound.get(key);
                if (child instanceof CompoundTag || child instanceof ListTag) {
                    printRopeMatches(child, path + "." + key);
                }
            }
        } else if (tag instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                Tag child = list.get(i);
                if (child instanceof CompoundTag || child instanceof ListTag) {
                    printRopeMatches(child, path + "[" + i + "]");
                }
            }
        }
    }

    private static void printRopeData(CompoundTag blockEntity, String indent) {
        if (!blockEntity.contains("HasRopeAttached") && !blockEntity.contains("OwnStrand") && !blockEntity.contains("Strand")) {
            return;
        }
        System.out.println(indent + "HasRopeAttached=" + (blockEntity.contains("HasRopeAttached") ? blockEntity.get("HasRopeAttached") : "<missing>"));
        System.out.println(indent + "OwnStrand=" + (blockEntity.contains("OwnStrand") ? blockEntity.get("OwnStrand") : "<missing>"));
        if (!blockEntity.contains("Strand", Tag.TAG_COMPOUND)) {
            System.out.println(indent + "Strand=<missing>");
            return;
        }

        CompoundTag strand = blockEntity.getCompound("Strand");
        System.out.println(indent + "Strand keys=" + strand.getAllKeys());
        System.out.println(indent + "  uuid=" + (strand.contains("uuid") ? strand.get("uuid") : "<missing>"));
        System.out.println(indent + "  extension_goal=" + (strand.contains("extension_goal") ? strand.get("extension_goal") : "<missing>"));
        ListTag points = strand.getList("points", Tag.TAG_LIST);
        System.out.println(indent + "  points=" + points.size());
        for (int i = 0; i < points.size(); i++) {
            System.out.println(indent + "    point[" + i + "]=" + points.get(i));
        }
        ListTag attachments = strand.getList("attachments", Tag.TAG_COMPOUND);
        System.out.println(indent + "  attachments=" + attachments.size());
        for (int i = 0; i < attachments.size(); i++) {
            CompoundTag attachment = attachments.getCompound(i);
            System.out.println(indent + "    attachment[" + i + "] keys=" + attachment.getAllKeys());
            System.out.println(indent + "      point=" + (attachment.contains("point") ? attachment.get("point") : "<missing>"));
            System.out.println(indent + "      subLevelID=" + (attachment.contains("subLevelID") ? attachment.get("subLevelID") : "<missing>"));
            System.out.println(indent + "      blockAttachment=" + (attachment.contains("blockAttachment") ? attachment.get("blockAttachment") : "<missing>"));
        }
    }

    private static void printInterestingItemData(Tag tag, String indent) {
        if (tag instanceof CompoundTag compound) {
            boolean printed = false;
            if (compound.contains("Items", Tag.TAG_LIST)) {
                ListTag items = compound.getList("Items", Tag.TAG_COMPOUND);
                System.out.println(indent + "Items=" + items.size());
                for (int i = 0; i < items.size(); i++) {
                    CompoundTag entry = items.getCompound(i);
                    System.out.println(indent + "  " + entry.getString("id") + " x" + readCount(entry));
                }
                printed = true;
            }
            if (compound.contains("inventory", Tag.TAG_LIST)) {
                ListTag items = compound.getList("inventory", Tag.TAG_COMPOUND);
                System.out.println(indent + "inventory=" + items.size());
                for (int i = 0; i < items.size(); i++) {
                    CompoundTag entry = items.getCompound(i);
                    System.out.println(indent + "  " + entry.getString("id") + " x" + readCount(entry));
                }
                printed = true;
            }
            if (!printed) {
                for (String key : compound.getAllKeys()) {
                    Tag child = compound.get(key);
                    if (child instanceof CompoundTag || child instanceof ListTag) {
                        printInterestingItemData(child, indent + key + ".");
                    }
                }
            }
        } else if (tag instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                Tag child = list.get(i);
                if (child instanceof CompoundTag || child instanceof ListTag) {
                    printInterestingItemData(child, indent + "[" + i + "].");
                }
            }
        }
    }

    private static long readCount(CompoundTag entry) {
        if (entry.contains("count", Tag.TAG_ANY_NUMERIC)) {
            return entry.getLong("count");
        }
        if (entry.contains("Count", Tag.TAG_ANY_NUMERIC)) {
            return entry.getLong("Count");
        }
        return 0L;
    }
}
