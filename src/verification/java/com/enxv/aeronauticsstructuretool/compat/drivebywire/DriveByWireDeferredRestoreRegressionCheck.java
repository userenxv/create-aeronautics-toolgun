package com.enxv.aeronauticsstructuretool.compat.drivebywire;

public final class DriveByWireDeferredRestoreRegressionCheck {
    private DriveByWireDeferredRestoreRegressionCheck() {
    }

    public static void main(String[] args) {
        requireRetry(result(0, 0, 1, 1, true), "explicit deferred endpoint");
        requireRetry(result(0, 0, 0, 0, false), "unattempted snapshot");
        requireComplete(result(1, 0, 0, 1, true), "newly restored snapshot");
        requireComplete(result(0, 1, 0, 1, true), "idempotently existing snapshot");
    }

    private static DriveByWireApiBridge.RestoreResult result(
            int restored,
            int existing,
            int deferred,
            int expected,
            boolean attempted
    ) {
        return new DriveByWireApiBridge.RestoreResult(
                restored,
                existing,
                deferred,
                0,
                expected,
                attempted
        );
    }

    private static void requireRetry(DriveByWireApiBridge.RestoreResult result, String description) {
        if (!DriveByWireRestoreService.shouldRetry(result)) {
            throw new AssertionError(description + " must be retried");
        }
    }

    private static void requireComplete(DriveByWireApiBridge.RestoreResult result, String description) {
        if (DriveByWireRestoreService.shouldRetry(result)) {
            throw new AssertionError(description + " must not be retried");
        }
    }
}
