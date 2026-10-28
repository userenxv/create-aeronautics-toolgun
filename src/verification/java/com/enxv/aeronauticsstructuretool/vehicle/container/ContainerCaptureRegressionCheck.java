package com.enxv.aeronauticsstructuretool.vehicle.container;

import com.enxv.aeronauticsstructuretool.client.render.ContainerCaptureAnimation;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ContainerCaptureRegressionCheck {
    public static void main(String[] args) throws Exception {
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        verifyBackups();
        check(ContainerCaptureConfig.durationTicks(50, true, 50) == 20, "50 blocks must take one second");
        check(ContainerCaptureConfig.durationTicks(500, true, 50) == 200, "500 blocks must take ten seconds");
        check(ContainerCaptureConfig.durationTicks(51, true, 50) == 21, "fractional ticks must round up");
        check(ContainerCaptureConfig.durationTicks(500, false, 50) == 0, "disabled timer must be instant");
        check(ContainerCaptureConfig.durationTicks(100, true, 100) == 20, "configured speed must apply");
        check(ContainerCaptureConfig.durationTicks(Long.MAX_VALUE, true, 0.1) > 0, "large counts must not overflow");
        ContainerCaptureProgress progress = new ContainerCaptureProgress();
        check(progress.advance(false, 0, 200) == 1, "standing must progress normally");
        check(progress.advance(true, 0, 200) == 2, "sneaking must double progress");
        double previousProgress = progress.completed();
        check(progress.advance(false, 0.02, 200) == 0.5, "slow movement must reduce progress");
        check(progress.completed() > previousProgress, "slow movement must still advance");
        previousProgress = progress.completed();
        check(progress.advance(false, 0.2, 200) == -1, "fast movement must reverse progress");
        check(progress.completed() < previousProgress, "reverse progress must really decrease");
        for (int i = 0; i < 100; i++) progress.advance(false, 0.2, 200);
        check(progress.completed() == 0, "reversal must stop at zero");
        for (int i = 0; i < 100; i++) progress.advance(true, 0, 200);
        check(progress.completed() == 200, "accelerated capture must finish at its target");

        for (int failAt = 0; failAt <= 3; failAt++) {
            FailureStore store = new FailureStore(failAt);
            try {
                ContainerCaptureCommit.execute(List.of(1, 2, 3), store);
                check(failAt == 3, "injected failure was not reached");
            } catch (IOException expected) {
                check(failAt < 3, "unexpected completion failure");
            }
            check(store.saved, "an archive must exist before deleting anything");
            check(store.usable == store.remaining.isEmpty(), "a partial archive must not be usable");
            for (int id : List.of(1, 2, 3)) {
                check(store.world.contains(id) == store.remaining.contains(id), "receipt must track undeleted originals");
            }
        }
        boolean[] removed = {false};
        try {
            ContainerCaptureCommit.execute(List.of(1), new ContainerCaptureCommit.Store<Integer>() {
                public void prepare(List<Integer> originals) throws IOException { throw new IOException("storage failed"); }
                public void removeOriginal(Integer original) { removed[0] = true; }
                public void confirmRemoved(Integer original) { throw new AssertionError("unreachable"); }
                public void finish() { throw new AssertionError("unreachable"); }
            });
            throw new AssertionError("failed preparation must abort");
        } catch (IOException expected) {
            check(!removed[0], "failed preparation must preserve all originals");
        }

        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ItemStack receipt = new ItemStack(Items.STICK);
        ContainerCaptureReceipt.prepare(receipt, "minecraft:overworld", List.of(first, second));
        check(ContainerCaptureReceipt.placementBlocked(receipt), "unfinished archive must be locked");
        ItemStack persisted = receipt.copy();
        check(ContainerCaptureReceipt.remaining(persisted).equals(List.of(first, second)), "receipt must survive stack copying");
        check(ContainerCaptureReceipt.dimension(persisted).equals("minecraft:overworld"), "recovery must retain the original dimension");
        ContainerCaptureReceipt.confirmRemoved(persisted, first);
        try {
            ContainerCaptureReceipt.finish(persisted);
            throw new AssertionError("partial removal must remain locked");
        } catch (IllegalStateException expected) {
            check(ContainerCaptureReceipt.placementBlocked(persisted), "failed finalization must retain lock");
        }
        ContainerCaptureReceipt.confirmRemoved(persisted, second);
        ContainerCaptureReceipt.finish(persisted);
        check(!ContainerCaptureReceipt.placementBlocked(persisted), "completed archive must unlock");
        check(!persisted.get(DataComponents.CUSTOM_DATA).copyTag().contains("PendingContainerCapture"), "completed receipt must be cleared");

        // Removal can succeed before an integration throws while confirming it.
        // The archive must stay locked rather than exposing a second copy.
        for (boolean failDuringConfirmation : List.of(true, false)) {
            ItemStack interrupted = new ItemStack(Items.STICK);
            Set<UUID> originals = new HashSet<>(List.of(first, second));
            try {
                ContainerCaptureCommit.execute(List.of(first, second), new ContainerCaptureCommit.Store<UUID>() {
                    public void prepare(List<UUID> ids) {
                        ContainerCaptureReceipt.prepare(interrupted, "minecraft:overworld", ids);
                    }
                    public void removeOriginal(UUID id) { originals.remove(id); }
                    public void confirmRemoved(UUID id) throws IOException {
                        if (failDuringConfirmation) throw new IOException("injected receipt confirmation failure");
                        ContainerCaptureReceipt.confirmRemoved(interrupted, id);
                    }
                    public void finish() throws IOException { throw new IOException("injected finalization failure"); }
                });
                throw new AssertionError("injected commit failure was not reached");
            } catch (IOException expected) {
                check(ContainerCaptureReceipt.placementBlocked(interrupted.copy()), "interrupted commit must retain a persistent lock");
                check(failDuringConfirmation ? originals.size() == 1 : originals.isEmpty(), "fault injection must reach the intended commit boundary");
            }
        }

        ItemStack malformed = new ItemStack(Items.STICK);
        net.minecraft.nbt.CompoundTag malformedTag = new net.minecraft.nbt.CompoundTag();
        malformedTag.putString("PendingContainerCapture", "invalid");
        malformed.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(malformedTag));
        check(ContainerCaptureReceipt.placementBlocked(malformed), "malformed receipt must not bypass the lock");
        try {
            ContainerCaptureReceipt.finish(malformed);
            throw new AssertionError("malformed receipt must not finalize");
        } catch (IllegalStateException expected) {
            check(ContainerCaptureReceipt.placementBlocked(malformed), "malformed receipt must remain locked");
        }
        ContainerCaptureReceipt.blockPlacement(persisted);
        check(ContainerCaptureReceipt.placementBlocked(persisted.copy()), "incomplete placement rollback must persistently block another placement");

        ContainerCaptureAnimation animation = new ContainerCaptureAnimation();
        animation.advance(0.01, true, false);
        check(animation.rpm() > 60 && animation.rpm() < 300, "acceleration must be gradual");
        for (int i = 0; i < 30; i++) animation.advance(0.01, true, false);
        check(animation.rpm() >= 300, "capture speed must reach at least 300 RPM");
        double fast = animation.rpm();
        animation.advance(0.01, false, true);
        check(animation.rpm() < fast && animation.rpm() > 120, "deceleration must be gradual");
        for (int i = 0; i < 300; i++) animation.advance(0.01, false, true);
        check(Math.abs(animation.rpm() - 120) < 0.01, "filled idle speed must settle");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void verifyBackups() throws Exception {
        java.nio.file.Path directory = java.nio.file.Files.createTempDirectory("container-backup-check");
        UUID id = UUID.randomUUID();
        ContainerBackupWriter writer = new ContainerBackupWriter(directory);
        try {
            writer.submit(id, new byte[]{1, 2, 3}, 256);
            writer.submit(id, new byte[]{4, 5}, 256);
            writer.close();
            check(java.util.Arrays.equals(java.nio.file.Files.readAllBytes(directory.resolve(id + ".excraft")),
                    new byte[]{4, 5}), "same ID must replace the old backup in submission order");
            try (var files = java.nio.file.Files.list(directory)) {
                check(files.count() == 1, "completed backups must not leave duplicate files or temporary files");
            }
            try {
                writer.submit(id, new byte[]{6}, 256);
                throw new AssertionError("closed backup writer must reject new work");
            } catch (ContainerBackupWriter.QueueFullException expected) {
                check(java.nio.file.Files.size(directory.resolve(id + ".excraft")) == 2,
                        "rejected work must leave the existing backup intact");
            }
            UUID oldest = UUID.randomUUID();
            UUID middle = UUID.randomUUID();
            UUID newest = UUID.randomUUID();
            java.nio.file.Files.delete(directory.resolve(id + ".excraft"));
            java.nio.file.Path oldFile = directory.resolve(oldest + ".excraft");
            java.nio.file.Path middleFile = directory.resolve(middle + ".excraft");
            java.nio.file.Files.write(oldFile, new byte[4]);
            java.nio.file.Files.write(middleFile, new byte[4]);
            java.nio.file.Files.setLastModifiedTime(oldFile, java.nio.file.attribute.FileTime.fromMillis(1000));
            java.nio.file.Files.setLastModifiedTime(middleFile, java.nio.file.attribute.FileTime.fromMillis(2000));
            try (ContainerBackupWriter retention = new ContainerBackupWriter(directory)) {
                retention.submit(newest, new byte[2], 10);
            }
            check(java.nio.file.Files.exists(oldFile) && java.nio.file.Files.exists(middleFile),
                    "backups at the limit must be retained");
            UUID extra = UUID.randomUUID();
            try (ContainerBackupWriter retention = new ContainerBackupWriter(directory)) {
                retention.submit(extra, new byte[5], 10);
            }
            check(!java.nio.file.Files.exists(oldFile) && !java.nio.file.Files.exists(middleFile),
                    "overflow must delete oldest files until within limit");
            check(java.nio.file.Files.exists(directory.resolve(newest + ".excraft"))
                    && java.nio.file.Files.exists(directory.resolve(extra + ".excraft")),
                    "newest backups must survive oldest-first cleanup");
        } finally {
            writer.close();
            try (var files = java.nio.file.Files.list(directory)) {
                for (var file : files.toList()) java.nio.file.Files.delete(file);
            }
            java.nio.file.Files.delete(directory);
        }
    }

    private static final class FailureStore implements ContainerCaptureCommit.Store<Integer> {
        final Set<Integer> world = new HashSet<>(List.of(1, 2, 3));
        final List<Integer> remaining = new ArrayList<>();
        final int failAt;
        int deletions;
        boolean saved;
        boolean usable;

        FailureStore(int failAt) { this.failAt = failAt; }
        public void prepare(List<Integer> originals) { saved = true; remaining.addAll(originals); }
        public void removeOriginal(Integer original) throws IOException {
            check(saved, "removal must follow archive preparation");
            if (deletions++ == failAt) throw new IOException("injected removal failure");
            world.remove(original);
        }
        public void confirmRemoved(Integer original) { remaining.remove(original); }
        public void finish() { check(remaining.isEmpty(), "all originals must be removed"); usable = true; }
    }
}
