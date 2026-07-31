package com.enxv.aeronauticsstructuretool.blueprint.placement;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.core.FailureMessages;

import java.util.ArrayList;
import java.util.List;

public final class BlueprintPlacementDiagnostics {
    private final String blueprintName;
    private final List<Warning> warnings = new ArrayList<>();

    public BlueprintPlacementDiagnostics(String blueprintName) {
        this.blueprintName = blueprintName;
    }

    public void run(String feature, CheckedAction action) {
        try {
            action.run();
        } catch (Exception exception) {
            String detail = FailureMessages.describe(exception, exception.getClass().getSimpleName());
            this.warnings.add(new Warning(feature, detail));
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Blueprint '{}' could not restore non-critical feature '{}': {}; placement will continue",
                    this.blueprintName,
                    feature,
                    detail,
                    exception
            );
        }
    }

    public void warn(String feature, String detail) {
        String safeDetail = detail == null || detail.isBlank() ? "unspecified warning" : detail;
        this.warnings.add(new Warning(feature, safeDetail));
        AeronauticsStructureToolMod.LOGGER.warn(
                "Blueprint '{}' restored non-critical feature '{}' with a warning: {}",
                this.blueprintName,
                feature,
                safeDetail
        );
    }

    public List<Warning> warnings() {
        return List.copyOf(this.warnings);
    }

    public record Warning(String feature, String detail) {
        public Warning {
            feature = feature == null || feature.isBlank() ? "blueprint feature" : feature.trim();
            detail = detail == null || detail.isBlank() ? "unspecified warning" : detail.trim();
        }

        public String message() {
            return this.feature + ": " + this.detail;
        }
    }

    @FunctionalInterface
    public interface CheckedAction {
        void run() throws Exception;
    }
}
