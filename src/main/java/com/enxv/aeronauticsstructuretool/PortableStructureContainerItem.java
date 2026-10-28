package com.enxv.aeronauticsstructuretool;

import com.enxv.aeronauticsstructuretool.blueprint.capture.NativeBlueprintCaptureService;
import com.enxv.aeronauticsstructuretool.blueprint.lifecycle.ConnectedStructureRemovalService;
import com.enxv.aeronauticsstructuretool.blueprint.lifecycle.ConnectedStructureSnapshot;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturedBlueprintArchive;
import com.enxv.aeronauticsstructuretool.blueprint.model.NativeBlueprintPlacementResult;
import com.enxv.aeronauticsstructuretool.blueprint.placement.BlueprintPlacementTargetResolver;
import com.enxv.aeronauticsstructuretool.blueprint.placement.NativeBlueprintPlacementService;
import com.enxv.aeronauticsstructuretool.blueprint.placement.BlueprintVerticalPlacement;
import com.enxv.aeronauticsstructuretool.blueprint.storage.BlueprintFileRepository;
import com.enxv.aeronauticsstructuretool.core.FailureMessages;
import com.enxv.aeronauticsstructuretool.vehicle.container.PortableContainerAuditLogger;
import com.enxv.aeronauticsstructuretool.vehicle.container.PortableContainerStorage;
import com.enxv.aeronauticsstructuretool.vehicle.container.ContainerCaptureBlocks;
import com.enxv.aeronauticsstructuretool.vehicle.container.ContainerCaptureCommit;
import com.enxv.aeronauticsstructuretool.vehicle.container.ContainerCaptureConfig;
import com.enxv.aeronauticsstructuretool.vehicle.container.ContainerCaptureReceipt;
import com.enxv.aeronauticsstructuretool.server.ServerServices;
import com.enxv.aeronauticsstructuretool.blueprint.codec.BlueprintArchiveCodec;
import com.enxv.aeronauticsstructuretool.blueprint.codec.NativeBlueprintReader;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.joml.Vector3d;

import java.util.List;
import java.util.HashSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.UseAnim;

public final class PortableStructureContainerItem extends Item {
    private static final String DEFAULT_BLUEPRINT_NAME = "portable_structure";
    private static final double CONNECTED_SUBLEVEL_AABB_EXPANSION_BLOCKS = 1.0D;
    private final boolean canPlacePrinter;
    private final boolean repairIntegration;
    private final boolean consumeAfterPlacement;
    private final String tooltipPrefix;

    public PortableStructureContainerItem(Properties properties) {
        this(properties, true, true, false, "item.create_aeronautics_toolgun.portable_structure_container");
    }

    private PortableStructureContainerItem(Properties properties, boolean canPlacePrinter, boolean repairIntegration, boolean consumeAfterPlacement, String tooltipPrefix) {
        super(properties);
        this.canPlacePrinter = canPlacePrinter;
        this.repairIntegration = repairIntegration;
        this.consumeAfterPlacement = consumeAfterPlacement;
        this.tooltipPrefix = tooltipPrefix;
    }

    static PortableStructureContainerItem disposable(Properties properties) {
        return new PortableStructureContainerItem(
                properties,
                false,
                false,
                true,
                "item.create_aeronautics_toolgun.disposable_vehicle_container"
        );
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        if (ContainerCaptureReceipt.placementBlocked(stack)) {
            if (context.getPlayer() instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.translatable("message.create_aeronautics_toolgun.capture_recovery"));
            }
            return InteractionResult.CONSUME;
        }
        if (this.canPlacePrinter
                && !PortableContainerStorage.hasStoredBlueprint(stack)
                && context.getPlayer() != null
                && context.getPlayer().isCrouching()
                && Sable.HELPER.getContaining(level, context.getClickedPos()) == null
                && context.getClickedFace() == Direction.UP) {
            return placePrinter(context);
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            if (!PortableContainerStorage.hasStoredBlueprint(stack)
                    && context.getPlayer() != null) {
                context.getPlayer().startUsingItem(context.getHand());
            }
            return InteractionResult.CONSUME;
        }

        try {
            if (PortableContainerStorage.hasStoredBlueprint(stack)) {
                placeStoredBlueprint(serverLevel, context, stack);
            } else {
                if (context.getPlayer() instanceof ServerPlayer player) {
                    ServerServices.CONTAINER_CAPTURE.begin(player, context, this);
                }
            }
            return InteractionResult.SUCCESS;
        } catch (Exception exception) {
            if (PortableContainerStorage.hasStoredBlueprint(stack) && exception.getSuppressed().length > 0) {
                ContainerCaptureReceipt.blockPlacement(stack);
            }
            AeronauticsStructureToolMod.LOGGER.error(
                    "Portable container operation failed at {} in {}",
                    context.getClickedPos(),
                    serverLevel.dimension().location(),
                    exception
            );
            if (context.getPlayer() != null) {
                context.getPlayer().sendSystemMessage(Component.translatable(
                        "message.create_aeronautics_toolgun.portable_container_failed",
                        FailureMessages.describe(exception, "portable container operation failed")
                ));
            }
            return InteractionResult.FAIL;
        }
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (!PortableContainerStorage.hasStoredBlueprint(stack)
                && Sable.HELPER.getContaining(context.getLevel(), context.getClickedPos()) != null) {
            return useOn(context);
        }
        return InteractionResult.PASS;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return Integer.MAX_VALUE;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    private static InteractionResult placePrinter(UseOnContext context) {
        BlockPos placePos = context.getClickedPos().relative(context.getClickedFace());
        Level level = context.getLevel();
        if (!level.getBlockState(placePos).canBeReplaced()) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.sidedSuccess(true);
        }
        ItemStack stack = context.getItemInHand();
        if (!serverLevel.setBlock(placePos, ModBlocks.PORTABLE_STRUCTURE_PRINTER.get().defaultBlockState(), Block.UPDATE_ALL)) {
            return InteractionResult.FAIL;
        }
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable(this.tooltipPrefix + ".tooltip.summary").withStyle(ChatFormatting.GRAY));
        if (this.canPlacePrinter) {
            tooltipComponents.add(Component.translatable(this.tooltipPrefix + ".tooltip.printer").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (PortableContainerStorage.hasStoredBlueprint(stack)) {
            tooltipComponents.add(Component.translatable(this.tooltipPrefix + ".tooltip.filled").withStyle(ChatFormatting.AQUA));
            tooltipComponents.add(Component.translatable(this.tooltipPrefix + ".tooltip.place").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltipComponents.add(Component.translatable(this.tooltipPrefix + ".tooltip.empty").withStyle(ChatFormatting.YELLOW));
            tooltipComponents.add(Component.translatable(this.tooltipPrefix + ".tooltip.capture").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public void captureVehicle(ServerLevel level, ServerPlayer player, ItemStack stack, ServerSubLevel containing,
                               List<ServerSubLevel> expected, long expectedBlocks, double progressTicks) throws Exception {
        String blueprintName = resolveBlueprintName(containing);
        ConnectedStructureSnapshot snapshot = new ConnectedStructureSnapshot(containing.getUniqueId(),
                expected.size(), expected.stream().mapToDouble(sub -> sub.getMassTracker().getMass()).sum());
        CapturedBlueprintArchive saved = NativeBlueprintCaptureService.captureById(
                level,
                containing.getUniqueId(),
                blueprintName,
                CONNECTED_SUBLEVEL_AABB_EXPANSION_BLOCKS
        );
        var document = NativeBlueprintReader.read(BlueprintArchiveCodec.decode(saved.fileContents()));
        var capturedIds = document.sublevels().stream().map(sub -> sub.originalId()).toList();
        var expectedIds = expected.stream().map(ServerSubLevel::getUniqueId).toList();
        if (!new HashSet<>(capturedIds).equals(new HashSet<>(expectedIds))
                || ContainerCaptureBlocks.countArchive(level, saved.fileContents()) != expectedBlocks
                || progressTicks < ContainerCaptureConfig.durationTicks(expectedBlocks, player)) {
            throw new IllegalStateException("structure changed during capture; no originals were removed");
        }
        ItemStack prepared = stack.copy();
        byte[] normalizedBlueprint = PortableContainerStorage.prepareCapturedBlueprint(
                level,
                prepared,
                snapshot,
                saved.fileContents(),
                this.repairIntegration
        );
        PortableContainerStorage.writeStoredBlueprint(
                prepared,
                saved.fileName(),
                normalizedBlueprint,
                saved.materialSummary(),
                PortableContainerStorage.readContainerId(prepared),
                this.repairIntegration
        );
        if (this.repairIntegration) {
            PortableContainerStorage.clearActivePlacedRepairTracking(prepared);
        }
        ContainerCaptureReceipt.prepare(prepared, level.dimension().location().toString(), expectedIds);
        ServerServices.CONTAINER_CAPTURE.backup(snapshot.rootStructureId(), normalizedBlueprint);
        ContainerCaptureCommit.execute(expected, new ContainerCaptureCommit.Store<ServerSubLevel>() {
            @Override
            public void prepare(List<ServerSubLevel> originals) {
                stack.applyComponents(prepared.getComponents());
            }

            @Override
            public void removeOriginal(ServerSubLevel original) throws Exception {
                ConnectedStructureRemovalService.removeExact(level, List.of(original));
            }

            @Override
            public void confirmRemoved(ServerSubLevel original) {
                ContainerCaptureReceipt.confirmRemoved(stack, original.getUniqueId());
            }

            @Override
            public void finish() {
                ContainerCaptureReceipt.finish(stack);
            }
        });
        if (this.repairIntegration) {
            PortableContainerAuditLogger.logCapture(player, snapshot.rootStructureId(), snapshot.structureCount(), snapshot.totalMass());
        }
        player.sendSystemMessage(Component.translatable("message.create_aeronautics_toolgun.portable_container_captured"));
    }

    private void placeStoredBlueprint(ServerLevel level, UseOnContext context, ItemStack stack) throws Exception {
        byte[] storedBlueprint = PortableContainerStorage.readStoredBlueprint(stack);
        if (storedBlueprint.length == 0) {
            throw new IllegalStateException("stored blueprint data is empty");
        }
        if (this.repairIntegration) {
            PortableContainerStorage.ensureContainerId(stack);
        }
        String blueprintName = PortableContainerStorage.readStoredName(
                stack,
                DEFAULT_BLUEPRINT_NAME
        );
        BlueprintPlacementTargetResolver.Target placementTarget = BlueprintPlacementTargetResolver.resolve(
                level,
                context.getClickedPos(),
                context.getClickedFace(),
                context.getClickLocation()
        );
        Direction face = placementTarget.face();
        Vector3d hit = placementTarget.hit();
        BlueprintVerticalPlacement verticalPlacement = BlueprintVerticalPlacement.unchanged();
        if (face == Direction.UP) {
            double minimumRelativeY = PortableStructurePreviewData
                    .fromBlueprintBytes(blueprintName, storedBlueprint, level)
                    .bottomY();
            verticalPlacement = BlueprintVerticalPlacement.alignMinimumCenter(
                    hit.y + 0.5D + BlueprintVerticalPlacement.SURFACE_GAP,
                    minimumRelativeY
            );
        }
        NativeBlueprintPlacementResult placementResult = NativeBlueprintPlacementService.place(
                level,
                placementTarget.clickedPos(),
                face,
                blueprintName,
                storedBlueprint,
                hit.x,
                hit.y,
                hit.z,
                0,
                100,
                0,
                0,
                0,
                PlacementSnapMode.HIT,
                null,
                verticalPlacement,
                context.getPlayer() instanceof ServerPlayer serverPlayer ? serverPlayer.getUUID() : null,
                null
        );
        // Placement has committed. Ancillary repair tracking must not leave a second usable archive behind.
        PortableContainerStorage.clearStoredBlueprint(stack);
        if (this.consumeAfterPlacement) {
            stack.shrink(1);
            if (context.getPlayer() != null) {
                context.getPlayer().setItemInHand(context.getHand(), ItemStack.EMPTY);
            }
        }
        ConnectedStructureSnapshot snapshot = new ConnectedStructureSnapshot(
                placementResult.rootSubLevel().getUniqueId(),
                placementResult.placedSubLevelCount(),
                placementResult.placedTotalMass()
        );
        if (this.repairIntegration && context.getPlayer() instanceof ServerPlayer serverPlayer) {
            BlockPos placedPos = context.getClickedPos().relative(face);
            if (snapshot != null) {
                PortableContainerAuditLogger.logPlace(serverPlayer, snapshot.rootStructureId(), snapshot.structureCount(), snapshot.totalMass());
            } else {
                PortableContainerAuditLogger.logPlace(
                        serverPlayer,
                        null,
                        PortableContainerStorage.countStoredStructures(storedBlueprint),
                        -1.0D
                );
            }
            PortableContainerStorage.rememberPlacedRepairTracking(stack, storedBlueprint, snapshot);
            String repairVehicleId = PortableContainerStorage.readBlueprintRepairVehicleId(storedBlueprint);
            PortableContainerStorage.rememberRepairVehicleId(level, snapshot, repairVehicleId);
            AeronauticsStructureToolMod.LOGGER.info(
                    "Portable container place repair-id tracking: placedPos={} rootStructureId={} blueprintRepairId='{}' itemRepairId='{}'",
                    placedPos,
                    snapshot != null ? snapshot.rootStructureId() : null,
                    repairVehicleId,
                    PortableContainerStorage.readItemRepairVehicleId(stack)
            );
        } else if (this.repairIntegration) {
            PortableContainerStorage.rememberPlacedRepairTracking(stack, storedBlueprint, null);
        }
        if (context.getPlayer() != null) {
            context.getPlayer().sendSystemMessage(Component.translatable("message.create_aeronautics_toolgun.portable_container_placed"));
        }
    }

    private static String resolveBlueprintName(SubLevel containing) {
        String rawName = containing.getName();
        if (rawName == null || rawName.isBlank()) {
            return DEFAULT_BLUEPRINT_NAME;
        }
        String sanitized = BlueprintFileRepository.normalizeName(rawName);
        return sanitized.isBlank() ? DEFAULT_BLUEPRINT_NAME : sanitized;
    }

    public static boolean hasStoredVehicle(ItemStack stack) {
        return PortableContainerStorage.hasStoredBlueprint(stack);
    }

}
