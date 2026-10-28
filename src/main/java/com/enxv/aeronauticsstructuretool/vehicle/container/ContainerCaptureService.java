package com.enxv.aeronauticsstructuretool.vehicle.container;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.PortableStructureContainerItem;
import com.enxv.aeronauticsstructuretool.SyncContainerCapturePayload;
import com.enxv.aeronauticsstructuretool.blueprint.capture.ConnectedSubLevelCollector;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ContainerCaptureService {
    public static final double CONNECTED_GAP = 1D;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<Target> reserved = new HashSet<>();
    private static final String INTERRUPTED = "AstContainerCaptureInProgress";
    private ContainerBackupWriter backups;

    public void backup(UUID vehicleId, byte[] archive) throws IOException {
        if (!ContainerCaptureConfig.backupsEnabled()) {
            return;
        }
        if (backups == null) {
            backups = new ContainerBackupWriter(FMLPaths.GAMEDIR.get().resolve("container-backups"));
        }
        backups.submit(vehicleId, archive);
    }

    public void begin(ServerPlayer player, UseOnContext context, PortableStructureContainerItem item) {
        if (sessions.containsKey(player.getUUID())) {
            return;
        }
        ItemStack stack = player.getItemInHand(context.getHand());
        if (ContainerCaptureReceipt.placementBlocked(stack)) {
            message(player, "recovery");
            return;
        }
        try {
            if (stack.getCount() != 1 || PortableContainerStorage.hasStoredBlueprint(stack)) {
                message(player, "item_changed");
                return;
            }
            ServerLevel level = player.serverLevel();
            if (!(Sable.HELPER.getContaining(level, context.getClickedPos()) instanceof ServerSubLevel root)) {
                message(player, "target_changed");
                return;
            }
            List<ServerSubLevel> connected = ConnectedSubLevelCollector.collect(level, root, CONNECTED_GAP);
            List<Target> targets = connected.stream().map(sub -> new Target(level, sub.getUniqueId())).toList();
            if (targets.stream().anyMatch(reserved::contains)) {
                message(player, "busy");
                return;
            }
            HitResult hit = player.pick(player.blockInteractionRange(), 1F, false);
            if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK
                    || Sable.HELPER.getContaining(level, blockHit.getBlockPos()) != root) {
                message(player, "out_of_reach");
                return;
            }
            long blocks = ContainerCaptureBlocks.countLive(level, connected);
            int ticks = ContainerCaptureConfig.durationTicks(blocks);
            Session session = new Session(player, level, context.getHand(), stack, stack.copy(), item, root,
                    connected, targets, blocks, ticks);
            sessions.put(player.getUUID(), session);
            reserved.addAll(targets);
            player.getPersistentData().putBoolean(INTERRUPTED, true);
            player.startUsingItem(context.getHand());
            sync(session);
            if (ticks == 0) {
                complete(session);
                release(session);
                sessions.remove(player.getUUID());
            }
        } catch (Exception exception) {
            Session session = sessions.remove(player.getUUID());
            if (session != null) {
                release(session);
            }
            fail(player, exception);
        }
    }

    @SubscribeEvent
    public void tick(ServerTickEvent.Post event) {
        Iterator<Session> iterator = sessions.values().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            if (session.level.getServer() != event.getServer()) {
                continue;
            }
            try {
                String invalid = validate(session);
                if (invalid != null) {
                    iterator.remove();
                    release(session);
                    message(session.player, invalid);
                    continue;
                }
                session.elapsed++;
                var movement = session.player.position().subtract(session.lastPosition);
                session.lastPosition = session.player.position();
                session.movementSpeed += (Math.sqrt(movement.x * movement.x + movement.z * movement.z)
                        - session.movementSpeed) * 0.35D;
                session.rate = (float) session.progress.advance(session.player.isShiftKeyDown(), session.movementSpeed, session.total);
                HitResult hit = session.player.pick(session.player.blockInteractionRange(), 1F, false);
                if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK
                        || Sable.HELPER.getContaining(session.level, blockHit.getBlockPos()) != session.root) {
                    iterator.remove();
                    release(session);
                    message(session.player, "out_of_reach");
                    continue;
                }
                if (session.elapsed % 10 == 0 || session.progress.completed() >= session.total) {
                    List<ServerSubLevel> current = ConnectedSubLevelCollector.collectLoaded(session.level, session.root, CONNECTED_GAP);
                    if (!new HashSet<>(current).equals(new HashSet<>(session.structures))
                            || ContainerCaptureBlocks.countLive(session.level, current) != session.blocks) {
                        iterator.remove();
                        release(session);
                        message(session.player, "target_changed");
                        continue;
                    }
                }
                if (session.elapsed % 4 == 0) {
                    sync(session);
                }
                if (session.progress.completed() >= session.total) {
                    // Drop the session before the commit so completion cannot be entered twice.
                    iterator.remove();
                    complete(session);
                    release(session);
                }
            } catch (Exception exception) {
                if (sessions.containsKey(session.player.getUUID())) {
                    iterator.remove();
                }
                release(session);
                fail(session.player, exception);
            }
        }
    }

    private static String validate(Session session) {
        ServerPlayer player = session.player;
        if (!player.isAlive() || player.hasDisconnected() || player.serverLevel() != session.level) {
            return "player_changed";
        }
        if (player.getItemInHand(session.hand) != session.stack
                || !ItemStack.matches(session.stack, session.initial)) {
            return "item_changed";
        }
        if (!player.isUsingItem() || player.getUsedItemHand() != session.hand) {
            return "released";
        }
        var container = SubLevelContainer.getContainer(session.level);
        if (container == null || session.structures.stream().anyMatch(sub -> container.getSubLevel(sub.getUniqueId()) != sub)) {
            return "target_changed";
        }
        if (player.containerMenu != player.inventoryMenu) {
            return "menu_opened";
        }
        return null;
    }

    private static void complete(Session session) throws Exception {
        session.item.captureVehicle(session.level, session.player, session.stack, session.root,
                session.structures, session.blocks, session.progress.completed());
    }

    private void release(Session session) {
        reserved.removeAll(session.targets);
        session.player.getPersistentData().remove(INTERRUPTED);
        session.player.stopUsingItem();
        PacketDistributor.sendToPlayer(session.player, new SyncContainerCapturePayload(0, 0, 0, 0));
    }

    private static void sync(Session session) {
        PacketDistributor.sendToPlayer(session.player,
                new SyncContainerCapturePayload((float) session.progress.completed(), session.total, session.blocks, session.rate));
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        Session session = sessions.remove(event.getEntity().getUUID());
        if (session != null) {
            reserved.removeAll(session.targets);
            AeronauticsStructureToolMod.LOGGER.info("Container capture cancelled on logout for {}", event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getPersistentData().getBoolean(INTERRUPTED)) {
            player.getPersistentData().remove(INTERRUPTED);
            message(player, "player_changed");
        }
    }

    @SubscribeEvent
    public void stopped(ServerStoppedEvent event) {
        sessions.values().removeIf(session -> session.level.getServer() == event.getServer());
        reserved.removeIf(target -> target.level.getServer() == event.getServer());
        if (backups != null) {
            try {
                backups.close();
            } catch (IOException failure) {
                AeronauticsStructureToolMod.LOGGER.error("Failed to finish container backups on shutdown", failure);
            }
            backups = null;
        }
    }

    private static void message(ServerPlayer player, String reason) {
        player.stopUsingItem();
        PacketDistributor.sendToPlayer(player, new SyncContainerCapturePayload(0, 0, 0, 0));
        player.sendSystemMessage(Component.translatable("message.create_aeronautics_toolgun.capture_" + reason));
    }

    private static void fail(ServerPlayer player, Exception exception) {
        AeronauticsStructureToolMod.LOGGER.error("Container capture failed for {}", player.getUUID(), exception);
        message(player, exception instanceof ContainerBackupWriter.QueueFullException ? "backup_busy"
                : ContainerCaptureReceipt.pending(player.getMainHandItem())
                || ContainerCaptureReceipt.pending(player.getOffhandItem()) ? "recovery" : "failed");
    }

    private record Target(ServerLevel level, UUID id) {
    }

    private static final class Session {
        final ServerPlayer player;
        final ServerLevel level;
        final InteractionHand hand;
        final ItemStack stack;
        final ItemStack initial;
        final PortableStructureContainerItem item;
        final ServerSubLevel root;
        final List<ServerSubLevel> structures;
        final List<Target> targets;
        final long blocks;
        final int total;
        int elapsed;
        final ContainerCaptureProgress progress = new ContainerCaptureProgress();
        net.minecraft.world.phys.Vec3 lastPosition;
        double movementSpeed;
        float rate;

        Session(ServerPlayer player, ServerLevel level, InteractionHand hand, ItemStack stack, ItemStack initial,
                PortableStructureContainerItem item, ServerSubLevel root, List<ServerSubLevel> structures,
                List<Target> targets, long blocks, int total) {
            this.player = player;
            this.level = level;
            this.hand = hand;
            this.stack = stack;
            this.initial = initial;
            this.item = item;
            this.root = root;
            this.structures = List.copyOf(structures);
            this.targets = List.copyOf(targets);
            this.blocks = blocks;
            this.total = total;
            this.lastPosition = player.position();
            this.rate = player.isShiftKeyDown() ? 2F : 1F;
        }
    }
}
