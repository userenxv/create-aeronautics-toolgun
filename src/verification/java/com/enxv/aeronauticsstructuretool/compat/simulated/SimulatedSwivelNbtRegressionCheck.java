package com.enxv.aeronauticsstructuretool.compat.simulated;

import com.enxv.aeronauticsstructuretool.blueprint.model.CapturePlan;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturedSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

public final class SimulatedSwivelNbtRegressionCheck {
    private static final String CHILD_ID_TAG = "SubLevelID";
    private static final String PLATE_POS_TAG = "SwivelPlate";

    private SimulatedSwivelNbtRegressionCheck() {
    }

    public static void main(String[] args) {
        verifySableMappedBearingIsAcceptedWithoutMutation();
        verifyRuntimeIdIsNotTreatedAsAnotherMappingPath();
        verifyPartialBearingLinkIsRejected();
    }

    private static void verifySableMappedBearingIsAcceptedWithoutMutation() {
        UUID blueprintId = UUID.randomUUID();
        BlockPos platePos = new BlockPos(12, -48, 37);
        CompoundTag tag = bearingTag(blueprintId, platePos);

        SimulatedStructureNbtRemapper.validateSwivelBearingForSave(tag, planWith(blueprintId));

        require(tag.getUUID(CHILD_ID_TAG).equals(blueprintId), "Sable-mapped swivel UUID was changed");
        require(
                NbtUtils.readBlockPos(tag, PLATE_POS_TAG).orElseThrow().equals(platePos),
                "Sable-mapped swivel position was changed"
        );
    }

    private static void verifyRuntimeIdIsNotTreatedAsAnotherMappingPath() {
        UUID blueprintId = UUID.randomUUID();
        UUID runtimeId = UUID.randomUUID();
        CompoundTag tag = bearingTag(runtimeId, BlockPos.ZERO);

        expectIllegalArgument(
                () -> SimulatedStructureNbtRemapper.validateSwivelBearingForSave(tag, planWith(blueprintId)),
                "unknown Sable blueprint sublevel"
        );
    }

    private static void verifyPartialBearingLinkIsRejected() {
        UUID blueprintId = UUID.randomUUID();
        CompoundTag tag = new CompoundTag();
        tag.putUUID(CHILD_ID_TAG, blueprintId);

        expectIllegalArgument(
                () -> SimulatedStructureNbtRemapper.validateSwivelBearingForSave(tag, planWith(blueprintId)),
                "block position"
        );
    }

    private static CompoundTag bearingTag(UUID childId, BlockPos platePos) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(CHILD_ID_TAG, childId);
        tag.put(PLATE_POS_TAG, NbtUtils.writeBlockPos(platePos));
        return tag;
    }

    private static CapturePlan planWith(UUID blueprintId) {
        CapturedSubLevel captured = new CapturedSubLevel(
                blueprintId,
                null,
                new Vector3d(),
                new Vector3d(),
                new Quaterniond(),
                null
        );
        return new CapturePlan(blueprintId, new Quaterniond(), new Vector3d(), List.of(captured));
    }

    private static void expectIllegalArgument(Runnable action, String expectedMessage) {
        try {
            action.run();
        } catch (IllegalArgumentException exception) {
            require(
                    exception.getMessage() != null && exception.getMessage().contains(expectedMessage),
                    "unexpected error: " + exception.getMessage()
            );
            return;
        }
        throw new IllegalStateException("expected IllegalArgumentException containing: " + expectedMessage);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
