package com.enxv.aeronauticsstructuretool.compat.synaxis;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

public final class SynaxisBlueprintNbtRegressionCheck {
    private SynaxisBlueprintNbtRegressionCheck() {
    }

    public static void main(String[] args) throws Exception {
        verifyControllerWireRoundTrip();
        verifyCompanionDirectionRoundTrip();
        verifyLegacyCompanionManifestDecoding();
        verifyConnectionIntentApiOrder();
        verifyCimulinkRoundTrip();
        verifyMalformedControllerWireFails();
    }

    private static void verifyControllerWireRoundTrip() throws Exception {
        SynaxisBlueprintNbt.ControllerWire expected = new SynaxisBlueprintNbt.ControllerWire(
                UUID.randomUUID(),
                new BlockPos(1, 2, 3),
                UUID.randomUUID(),
                new BlockPos(-4, 5, 6),
                Direction.NORTH,
                "throttle"
        );
        CompoundTag plot = new CompoundTag();
        SynaxisBlueprintNbt.writeControllerWires(plot, List.of(expected));
        require(plot.contains(SynaxisBlueprintNbt.CONTROLLER_WIRES), "controller-wire root tag changed");
        require(SynaxisBlueprintNbt.readControllerWires(plot).equals(List.of(expected)),
                "controller-wire manifest did not round-trip");
    }

    private static void verifyCompanionDirectionRoundTrip() throws Exception {
        SynaxisBlueprintNbt.CompanionJoint expected = new SynaxisBlueprintNbt.CompanionJoint(
                UUID.randomUUID(),
                new BlockPos(7, 8, 9),
                UUID.randomUUID(),
                new BlockPos(10, 11, 12),
                Direction.DOWN,
                Direction.EAST,
                UUID.randomUUID()
        );
        CompoundTag plot = new CompoundTag();
        SynaxisBlueprintNbt.writeCompanionJoints(plot, List.of(expected));
        List<SynaxisBlueprintNbt.CompanionJoint> decoded = SynaxisBlueprintNbt.readCompanionJoints(plot);
        require(decoded.equals(List.of(expected)), "companion manifest did not round-trip");
        require(decoded.getFirst().direction() == Direction.DOWN, "companion direction was swapped");
        require(decoded.getFirst().alignmentDirection() == Direction.EAST, "companion alignment was swapped");
    }

    private static void verifyLegacyCompanionManifestDecoding() throws Exception {
        UUID ownerSublevel = UUID.fromString("b7bad741-3713-41cd-a507-72a8cefb1852");
        UUID targetSublevel = UUID.fromString("0c4a66c7-eb23-4786-9ede-e474c1778f73");
        CompoundTag entry = new CompoundTag();
        entry.putUUID("owner_sublevel", ownerSublevel);
        entry.put("owner_pos", NbtUtils.writeBlockPos(new BlockPos(1031, 129, 1028)));
        entry.putUUID("target_sublevel", targetSublevel);
        entry.put("target_pos", NbtUtils.writeBlockPos(new BlockPos(1032, 128, 1032)));
        entry.putUUID("expected_sublevel", targetSublevel);
        entry.putString("direction", "west");
        entry.putString("alignment_direction", "south");

        ListTag entries = new ListTag();
        entries.add(entry);
        CompoundTag manifest = new CompoundTag();
        manifest.put("entries", entries);
        CompoundTag plot = new CompoundTag();
        plot.put(SynaxisBlueprintNbt.COMPANION_JOINTS, manifest);

        SynaxisBlueprintNbt.CompanionJoint decoded = SynaxisBlueprintNbt.readCompanionJoints(plot).getFirst();
        require(decoded.ownerSublevelId().equals(ownerSublevel), "legacy companion owner UUID changed");
        require(decoded.targetSublevelId().equals(targetSublevel), "legacy companion target UUID changed");
        require(decoded.direction() == Direction.WEST, "legacy companion direction was reinterpreted");
        require(decoded.alignmentDirection() == Direction.SOUTH,
                "legacy companion alignment was reinterpreted");
    }

    private static void verifyConnectionIntentApiOrder() throws Exception {
        SynaxisBlueprintNbt.CompanionJoint request = new SynaxisBlueprintNbt.CompanionJoint(
                UUID.randomUUID(),
                new BlockPos(1, 2, 3),
                UUID.randomUUID(),
                new BlockPos(4, 5, 6),
                Direction.WEST,
                Direction.SOUTH,
                UUID.randomUUID()
        );
        BlockPos target = new BlockPos(20, 30, 40);
        FakeCompanion owner = new FakeCompanion();
        Method withoutExpected = FakeCompanion.class.getMethod(
                "setConnectionIntent",
                BlockPos.class,
                Direction.class,
                Direction.class
        );
        SynaxisCompanionJointCompat.invokeConnectionIntent(
                withoutExpected,
                owner,
                target,
                request,
                null
        );
        require(owner.target.equals(target), "connection target changed");
        require(owner.alignmentDirection == Direction.SOUTH,
                "Synaxis alignment argument was not passed first");
        require(owner.companionDirection == Direction.WEST,
                "Synaxis companion direction argument was not passed second");

        UUID expectedCompanion = UUID.randomUUID();
        Method withExpected = FakeCompanion.class.getMethod(
                "setConnectionIntent",
                BlockPos.class,
                Direction.class,
                Direction.class,
                UUID.class
        );
        SynaxisCompanionJointCompat.invokeConnectionIntent(
                withExpected,
                owner,
                target,
                request,
                expectedCompanion
        );
        require(owner.alignmentDirection == Direction.SOUTH,
                "Synaxis alignment argument with expected UUID was not passed first");
        require(owner.companionDirection == Direction.WEST,
                "Synaxis companion direction with expected UUID was not passed second");
        require(owner.expectedCompanionUuid.equals(expectedCompanion), "expected companion UUID changed");
    }

    private static void verifyCimulinkRoundTrip() throws Exception {
        UUID endpointA = UUID.randomUUID();
        UUID endpointB = UUID.randomUUID();
        SynaxisBlueprintNbt.CimulinkManifest expected = new SynaxisBlueprintNbt.CimulinkManifest(
                List.of(
                        new SynaxisBlueprintNbt.CimulinkEndpoint(endpointA, UUID.randomUUID(), new BlockPos(2, 4, 6)),
                        new SynaxisBlueprintNbt.CimulinkEndpoint(endpointB, UUID.randomUUID(), new BlockPos(3, 5, 7))
                ),
                List.of(new SynaxisBlueprintNbt.CimulinkLink(endpointA, "out", endpointB, "in"))
        );
        CompoundTag root = new CompoundTag();
        SynaxisBlueprintNbt.writeCimulink(root, expected);
        require(SynaxisBlueprintNbt.hasCimulinkManifest(root), "Cimulink root tag changed");
        require(SynaxisBlueprintNbt.readCimulink(root).equals(expected), "Cimulink manifest did not round-trip");
    }

    private static void verifyMalformedControllerWireFails() {
        CompoundTag malformedEntry = new CompoundTag();
        malformedEntry.putUUID("source_sublevel", UUID.randomUUID());
        malformedEntry.putUUID("sink_sublevel", UUID.randomUUID());
        malformedEntry.putString("Direction", "north");
        malformedEntry.putString("Channel", "throttle");
        ListTag entries = new ListTag();
        entries.add(malformedEntry);
        CompoundTag manifest = new CompoundTag();
        manifest.put("entries", entries);
        CompoundTag plot = new CompoundTag();
        plot.put(SynaxisBlueprintNbt.CONTROLLER_WIRES, manifest);

        boolean failed = false;
        try {
            SynaxisBlueprintNbt.readControllerWires(plot);
        } catch (IOException expected) {
            failed = true;
        }
        require(failed, "malformed controller-wire manifest was silently accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    public static final class FakeCompanion {
        private BlockPos target;
        private Direction alignmentDirection;
        private Direction companionDirection;
        private UUID expectedCompanionUuid;

        public void setConnectionIntent(
                BlockPos target,
                Direction alignmentDirection,
                Direction companionDirection
        ) {
            setConnectionIntent(target, alignmentDirection, companionDirection, null);
        }

        public void setConnectionIntent(
                BlockPos target,
                Direction alignmentDirection,
                Direction companionDirection,
                UUID expectedCompanionUuid
        ) {
            this.target = target;
            this.alignmentDirection = alignmentDirection;
            this.companionDirection = companionDirection;
            this.expectedCompanionUuid = expectedCompanionUuid;
        }
    }
}
