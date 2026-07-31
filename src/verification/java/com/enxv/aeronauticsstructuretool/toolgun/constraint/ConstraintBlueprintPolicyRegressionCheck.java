package com.enxv.aeronauticsstructuretool.toolgun.constraint;

import com.enxv.aeronauticsstructuretool.blueprint.placement.BlueprintPlacementDiagnostics;
import com.enxv.aeronauticsstructuretool.core.FailureMessages;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class ConstraintBlueprintPolicyRegressionCheck {
    private ConstraintBlueprintPolicyRegressionCheck() {
    }

    public static void main(String[] args) throws Exception {
        require(
                !ConstraintBlueprintService.requiresEndpointAlignmentWarning(0.5D),
                "endpoint offset equal to 0.5 blocks must not warn"
        );
        require(
                ConstraintBlueprintService.requiresEndpointAlignmentWarning(0.5001D),
                "endpoint offset above 0.5 blocks must warn"
        );
        expectIOException(() -> ConstraintBlueprintService.requiresEndpointAlignmentWarning(Double.NaN));
        expectIOException(() -> ConstraintBlueprintService.requiresEndpointAlignmentWarning(-1.0D));
        verifySpecificFailureSelection();
        verifyIndividualPlacementWarnings();
        verifyMalformedEntriesBecomeWarnings();
    }

    private static void verifySpecificFailureSelection() {
        IOException failure = new IOException(
                "plot print failed",
                new IllegalStateException("missing Sable sublevel data at storage index 7")
        );
        require(
                "missing Sable sublevel data at storage index 7".equals(
                        FailureMessages.describe(failure, "printing failed")
                ),
                "printing feedback did not select the most specific failure reason"
        );
    }

    private static void verifyIndividualPlacementWarnings() {
        BlueprintPlacementDiagnostics diagnostics = new BlueprintPlacementDiagnostics("regression");
        diagnostics.warn("toolgun constraints", "endpoint offset exceeded 0.5 blocks");
        diagnostics.run("Create belts", () -> {
            throw new IOException("belt controller was missing");
        });
        require(diagnostics.warnings().size() == 2, "placement warnings were collapsed into a count");
        require(
                diagnostics.warnings().get(0).message().contains("endpoint offset exceeded 0.5 blocks"),
                "explicit placement warning lost its reason"
        );
        require(
                diagnostics.warnings().get(1).message().contains("belt controller was missing"),
                "caught placement warning lost its exception reason"
        );
    }

    private static void verifyMalformedEntriesBecomeWarnings() {
        ListTag entries = new ListTag();
        entries.add(new CompoundTag());
        entries.add(new CompoundTag());

        List<String> warnings = ConstraintBlueprintService.restore(
                null,
                entries,
                Map.of(),
                ignored -> {
                    throw new IllegalStateException("malformed constraints must not reach registration");
                }
        );
        require(warnings.size() == 2, "malformed constraint entries must each produce a warning");
        require(
                warnings.get(0).contains("was not restored"),
                "malformed constraint warning lost its explicit outcome"
        );
    }

    private static void expectIOException(CheckedAction action) throws Exception {
        try {
            action.run();
        } catch (IOException expected) {
            return;
        }
        throw new IllegalStateException("invalid endpoint alignment was accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }
}
