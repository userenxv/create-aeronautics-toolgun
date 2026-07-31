package com.enxv.aeronauticsstructuretool.blueprint.placement;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.PlacementSnapMode;
import com.enxv.aeronauticsstructuretool.PortableStructurePreviewData;
import com.enxv.aeronauticsstructuretool.blueprint.codec.BlueprintArchiveCodec;
import com.enxv.aeronauticsstructuretool.blueprint.security.BlueprintBlockEntitySanitizer;
import com.enxv.aeronauticsstructuretool.blueprint.security.MissingRegistryContentSanitizer;
import com.enxv.aeronauticsstructuretool.blueprint.storage.BlueprintFileRepository;
import com.enxv.aeronauticsstructuretool.server.BlueprintPlacementWarningNotifier;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.schematics.SchematicPrinter;
import com.simibubi.create.foundation.utility.BlockHelper;
import com.simibubi.create.foundation.utility.CreatePaths;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class CreatePhysicalBlueprintService {
    public static final String FILE_EXTENSION = ".nbt";
    private static final String SUB_LEVELS_TAG = "sub_levels";

    private CreatePhysicalBlueprintService() {
    }

    public static boolean isCreatePhysicalPath(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return false;
        }
        String fileName = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (!fileName.endsWith(FILE_EXTENSION)) {
            return false;
        }
        try {
            return hasCreatePhysicalLayout(Files.readAllBytes(path));
        } catch (IOException exception) {
            AeronauticsStructureToolMod.LOGGER.warn("Failed to inspect Create schematic file {}", path, exception);
            return false;
        }
    }

    public static boolean isCreatePhysicalBlueprint(byte[] bytes) {
        try {
            return hasCreatePhysicalLayout(bytes);
        } catch (IOException exception) {
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Create physical blueprint probe received malformed NBT",
                    exception
            );
            return false;
        }
    }

    public static boolean hasCreatePhysicalLayout(byte[] bytes) throws IOException {
        CompoundTag root = BlueprintArchiveCodec.decodeCompressedOrRaw(bytes);
        return root.contains(SUB_LEVELS_TAG, Tag.TAG_LIST)
                && !root.getList(SUB_LEVELS_TAG, Tag.TAG_COMPOUND).isEmpty();
    }

    public static void placeAtHit(
            ServerPlayer player,
            ServerLevel level,
            BlockPos clickedPos,
            Direction face,
            String blueprintName,
            byte[] blueprintBytes,
            double hitX,
            double hitY,
            double hitZ,
            int offsetX,
            int offsetY,
            int offsetZ,
            PlacementSnapMode snapMode
    ) throws IOException {
        SanitizedBlueprint sanitized = sanitizeForPlacement(blueprintName, blueprintBytes);
        blueprintBytes = sanitized.bytes();
        PortableStructurePreviewData preview = PortableStructurePreviewData.fromBlueprintBytes(
                blueprintName,
                blueprintBytes,
                level
        );
        if (!preview.hasPreview()) {
            throw new IOException("empty physical schematic");
        }

        Vector3dc basePlacementTarget = PlacementTargetMath.computePlacementTarget(
                clickedPos,
                face,
                hitX,
                hitY,
                hitZ,
                snapMode,
                offsetX,
                offsetY,
                offsetZ
        );
        double minimumCenteredY = preview.previewBlocks().stream()
                .mapToDouble(block -> block.position().y)
                .min()
                .orElse(Double.NaN);
        Vector3d placementTarget = BlueprintVerticalPlacement
                .keepAboveSurface(face, hitY)
                .apply(new Vector3d(basePlacementTarget), minimumCenteredY);
        BlockPos anchorPos = computeAnchorPos(preview, placementTarget);
        validateAnchor(level, anchorPos);
        Vector3d centerOffset = preview.previewCenterOffset();
        AeronauticsStructureToolMod.LOGGER.info(
                "Toolgun physical blueprint load: name='{}' clickedPos={} face={} target=({},{},{}) centerOffset=({},{},{}) maxSpan={} anchor={}",
                blueprintName,
                clickedPos,
                face,
                formatDouble(placementTarget.x),
                formatDouble(placementTarget.y),
                formatDouble(placementTarget.z),
                formatDouble(centerOffset.x),
                formatDouble(centerOffset.y),
                formatDouble(centerOffset.z),
                formatDouble(preview.maxSpan()),
                anchorPos
        );
        placeCreatePhysicalSchematic(player, level, anchorPos, blueprintName, blueprintBytes);
        BlueprintPlacementWarningNotifier.notifySkippedContent(
                level,
                player.getUUID(),
                sanitized.skippedContent()
        );
    }

    public static void placeAlignedToMinimum(
            ServerPlayer player,
            ServerLevel level,
            String blueprintName,
            byte[] blueprintBytes,
            double centerX,
            double centerZ,
            double desiredMinimumBlockCenterY
    ) throws IOException {
        SanitizedBlueprint sanitized = sanitizeForPlacement(blueprintName, blueprintBytes);
        blueprintBytes = sanitized.bytes();
        PortableStructurePreviewData preview = PortableStructurePreviewData.fromBlueprintBytes(
                blueprintName,
                blueprintBytes,
                level
        );
        if (!preview.hasPreview()) {
            throw new IOException("empty physical schematic");
        }

        double previewBottomY = preview.previewBlocks().stream()
                .mapToDouble(block -> block.position().y)
                .min()
                .orElse(0.0D);
        Vector3d centeredTarget = BlueprintVerticalPlacement
                .alignMinimumCenter(desiredMinimumBlockCenterY, previewBottomY)
                .apply(new Vector3d(centerX, desiredMinimumBlockCenterY, centerZ), Double.NaN);
        BlockPos anchorPos = computeAnchorPos(preview, centeredTarget);
        validateAnchor(level, anchorPos);
        placeCreatePhysicalSchematic(player, level, anchorPos, blueprintName, blueprintBytes);
        BlueprintPlacementWarningNotifier.notifySkippedContent(
                level,
                player.getUUID(),
                sanitized.skippedContent()
        );
    }

    private static BlockPos computeAnchorPos(
            PortableStructurePreviewData preview,
            Vector3dc centeredTarget
    ) throws IOException {
        Vector3d centerOffset = preview.previewCenterOffset();
        if (!isFinite(centerOffset) || !isFinite(centeredTarget)) {
            throw new IOException("physical schematic placement contains a non-finite anchor or center offset");
        }
        return BlockPos.containing(
                Math.round(centeredTarget.x() - centerOffset.x),
                Math.round(centeredTarget.y() - centerOffset.y),
                Math.round(centeredTarget.z() - centerOffset.z)
        );
    }

    private static void placeCreatePhysicalSchematic(
            ServerPlayer player,
            ServerLevel level,
            BlockPos anchorPos,
            String blueprintName,
            byte[] blueprintBytes
    ) throws IOException {
        Path ownerDirectory = CreatePaths.UPLOADED_SCHEMATICS_DIR.resolve(player.getGameProfile().getName());
        Files.createDirectories(ownerDirectory);
        String fileName = BlueprintFileRepository.normalizeName(blueprintName);
        if (fileName.isBlank()) {
            fileName = "portable_physical";
        }
        fileName = fileName + "-" + UUID.nameUUIDFromBytes(blueprintBytes) + FILE_EXTENSION;
        Path file = ownerDirectory.resolve(fileName);
        Files.write(file, blueprintBytes);

        ItemStack schematic = AllItems.SCHEMATIC.asStack();
        schematic.set(AllDataComponents.SCHEMATIC_OWNER, player.getGameProfile().getName());
        schematic.set(AllDataComponents.SCHEMATIC_FILE, fileName);
        schematic.set(AllDataComponents.SCHEMATIC_DEPLOYED, true);
        schematic.set(AllDataComponents.SCHEMATIC_ANCHOR, anchorPos);
        schematic.set(AllDataComponents.SCHEMATIC_ROTATION, Rotation.NONE);
        schematic.set(AllDataComponents.SCHEMATIC_MIRROR, Mirror.NONE);

        placeSchematicSynchronously(player, level, schematic);
        AeronauticsStructureToolMod.LOGGER.info(
                "Create physical schematic placement delegated: name='{}' owner='{}' anchor={} file='{}'",
                blueprintName,
                player.getGameProfile().getName(),
                anchorPos,
                fileName
        );
    }

    private static void placeSchematicSynchronously(
            ServerPlayer player,
            ServerLevel level,
            ItemStack schematic
    ) throws IOException {
        SchematicPrinter printer = new SchematicPrinter();
        try {
            printer.loadSchematic(schematic, level, !player.canUseGameMasterBlocks());
            if (!printer.isLoaded() || printer.isErrored()) {
                throw new IOException("Create could not load the physical schematic for placement");
            }
            boolean includeAir = AllConfigs.server().schematics.creativePrintIncludesAir.get();
            int[] placedTargets = {0};
            while (printer.advanceCurrentPos()) {
                if (!printer.shouldPlaceCurrent(level)) {
                    continue;
                }
                printer.handleCurrentTarget(
                        (pos, state, blockEntity) -> {
                            if (state.isAir() && !includeAir) {
                                return;
                            }
                            CompoundTag blockEntityData = BlockHelper.prepareBlockEntityData(
                                    level,
                                    state,
                                    blockEntity
                            );
                            BlockHelper.placeSchematicBlock(level, state, pos, null, blockEntityData);
                            placedTargets[0]++;
                        },
                        (pos, entity) -> {
                            if (!level.addFreshEntity(entity)) {
                                throw new IllegalStateException("Create could not add a schematic entity at " + pos);
                            }
                            placedTargets[0]++;
                        }
                );
            }
            printer.sendBlockUpdates(level);
            if (placedTargets[0] == 0) {
                throw new IOException("Create physical schematic produced no placement targets");
            }
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IOException("Create physical schematic placement failed", exception);
        }
    }

    private static SanitizedBlueprint sanitizeForPlacement(
            String blueprintName,
            byte[] blueprintBytes
    ) throws IOException {
        CompoundTag root = BlueprintArchiveCodec.decodeCompressedOrRaw(blueprintBytes);
        BlueprintBlockEntitySanitizer.Result result = BlueprintBlockEntitySanitizer.sanitize(root);
        MissingRegistryContentSanitizer.Result skippedContent =
                MissingRegistryContentSanitizer.sanitizeCreatePhysical(root);
        if (!result.changed() && skippedContent.isEmpty()) {
            return new SanitizedBlueprint(blueprintBytes, skippedContent);
        }
        AeronauticsStructureToolMod.LOGGER.warn(
                "Sanitized Create physical blueprint before placement: name='{}', signClickEventsRemoved={}, invalidSignMessagesCleared={}, dieselShaftsReset={}, dieselEngineReferencesRemoved={}, skippedBlocks={}, skippedBlockEntities={}",
                blueprintName,
                result.signClickEventsRemoved(),
                result.invalidSignMessagesCleared(),
                result.dieselShaftsReset(),
                result.dieselEngineReferencesRemoved(),
                skippedContent.skippedBlocks(),
                skippedContent.skippedBlockEntities()
        );
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(root, output);
            return new SanitizedBlueprint(output.toByteArray(), skippedContent);
        }
    }

    private static boolean isFinite(Vector3dc vector) {
        return Double.isFinite(vector.x())
                && Double.isFinite(vector.y())
                && Double.isFinite(vector.z());
    }

    private static void validateAnchor(ServerLevel level, BlockPos anchorPos) throws IOException {
        if (!level.getWorldBorder().isWithinBounds(anchorPos)) {
            throw new IOException("physical schematic anchor is outside the world border: " + anchorPos);
        }
    }

    private static String formatDouble(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private record SanitizedBlueprint(
            byte[] bytes,
            MissingRegistryContentSanitizer.Result skippedContent
    ) {
    }
}
