package com.enxv.aeronauticsstructuretool.toolgun.blueprint;

public record BlueprintSaveResult(
        boolean successful,
        String fileName,
        byte[] fileContents,
        String failureReason
) {
    public BlueprintSaveResult {
        fileContents = fileContents == null ? new byte[0] : fileContents.clone();
    }

    public static BlueprintSaveResult success(String fileName, byte[] fileContents) {
        return new BlueprintSaveResult(true, fileName, fileContents, null);
    }

    public static BlueprintSaveResult failure(String reason) {
        return new BlueprintSaveResult(false, "", new byte[0], reason);
    }

    @Override
    public byte[] fileContents() {
        return fileContents.clone();
    }
}
