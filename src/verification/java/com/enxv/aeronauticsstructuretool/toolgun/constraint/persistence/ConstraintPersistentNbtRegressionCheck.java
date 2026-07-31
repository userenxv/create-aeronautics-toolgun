package com.enxv.aeronauticsstructuretool.toolgun.constraint.persistence;

import com.enxv.aeronauticsstructuretool.ConnectionMode;
import net.minecraft.nbt.CompoundTag;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.UUID;

import static com.enxv.aeronauticsstructuretool.toolgun.constraint.persistence.ConstraintNbtKeys.*;

public final class ConstraintPersistentNbtRegressionCheck {
    private ConstraintPersistentNbtRegressionCheck() {
    }

    public static void main(String[] args) {
        verifyVersionedRoundTrip();
        verifyLegacyTaglessRead();
        verifyUnknownCoordinateSpaceFails();
        verifyMalformedOptionalVectorFails();
        verifyUnknownModeFails();
    }

    private static void verifyVersionedRoundTrip() {
        PersistentConstraint expected = sampleConstraint();
        CompoundTag encoded = ConstraintPersistentCodec.write(expected);
        require(
                encoded.getString(COORDINATE_SPACE_TAG).equals("saved_plot_local_v1"),
                "persistent coordinate-space marker changed"
        );
        require(encoded.contains(FIRST_DISPLAY_PLOT_LOCAL_TAG), "first display plot-local point was omitted");
        require(encoded.contains(SECOND_DISPLAY_PLOT_LOCAL_TAG), "second display plot-local point was omitted");
        require(ConstraintPersistentCodec.read(encoded).equals(expected), "versioned persistent constraint did not round-trip");
    }

    private static void verifyLegacyTaglessRead() {
        CompoundTag legacy = ConstraintPersistentCodec.write(sampleConstraint());
        legacy.remove(COORDINATE_SPACE_TAG);
        legacy.remove(FIRST_DISPLAY_PLOT_LOCAL_TAG);
        legacy.remove(SECOND_DISPLAY_PLOT_LOCAL_TAG);
        PersistentConstraint decoded = ConstraintPersistentCodec.read(legacy);
        require(
                decoded.coordinateSpace() == PersistentConstraintCoordinateSpace.LEGACY_UNVERSIONED,
                "tagless persistent constraint was not routed to the legacy decoder"
        );
    }

    private static void verifyUnknownCoordinateSpaceFails() {
        CompoundTag malformed = ConstraintPersistentCodec.write(sampleConstraint());
        malformed.putString(COORDINATE_SPACE_TAG, "unknown_space");
        requireReadFailure(malformed, "unknown persistent coordinate space was silently accepted");
    }

    private static void verifyMalformedOptionalVectorFails() {
        CompoundTag malformed = ConstraintPersistentCodec.write(sampleConstraint());
        malformed.putString(FIRST_WORLD_TAG, "not_a_vector");
        requireReadFailure(malformed, "malformed optional persistent vector was silently ignored");
    }

    private static void verifyUnknownModeFails() {
        CompoundTag malformed = ConstraintPersistentCodec.write(sampleConstraint());
        malformed.putString(MODE_TAG, "not_a_mode");
        requireReadFailure(malformed, "unknown persistent constraint mode silently became FIXED");
    }

    private static PersistentConstraint sampleConstraint() {
        return new PersistentConstraint(
                PersistentConstraintCoordinateSpace.SAVED_PLOT_LOCAL_V1,
                "minecraft:overworld",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ConnectionMode.BEARING,
                new Vector3d(11.25D, 70.5D, -4.75D),
                new Vector3d(15.5D, 71.25D, 8.0D),
                new Vector3d(11.0D, 70.0D, -5.0D),
                new Vector3d(15.0D, 71.0D, 8.0D),
                new Vector3d(100.0D, 80.0D, 200.0D),
                new Vector3d(100.0D, 80.0D, 200.0D),
                new Vector3d(3.0D, 70.0D, 4.0D),
                new Vector3d(7.0D, 71.0D, 17.0D),
                new Vector3d(3.25D, 70.5D, 4.25D),
                new Vector3d(7.5D, 71.25D, 17.0D),
                new Quaterniond().rotateY(0.25D),
                new Vector3d(0.0D, 1.0D, 0.0D),
                new Vector3d(0.0D, 1.0D, 0.0D)
        );
    }

    private static void requireReadFailure(CompoundTag tag, String message) {
        boolean failed = false;
        try {
            ConstraintPersistentCodec.read(tag);
        } catch (IllegalArgumentException expected) {
            failed = true;
        }
        require(failed, message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
