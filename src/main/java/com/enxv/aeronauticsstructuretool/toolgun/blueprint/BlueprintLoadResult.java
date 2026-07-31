package com.enxv.aeronauticsstructuretool.toolgun.blueprint;

public record BlueprintLoadResult(boolean successful, String failureReason) {
    public static BlueprintLoadResult success() {
        return new BlueprintLoadResult(true, null);
    }

    public static BlueprintLoadResult failure(String reason) {
        return new BlueprintLoadResult(false, reason);
    }
}
