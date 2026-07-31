package com.enxv.aeronauticsstructuretool.blueprint.runtime;

import java.util.Objects;

public record RuntimeContraptionRestoreResult(Status status, String message, Throwable cause) {
    public RuntimeContraptionRestoreResult {
        status = Objects.requireNonNull(status, "status");
        message = Objects.requireNonNullElse(message, "");
    }

    public static RuntimeContraptionRestoreResult success() {
        return new RuntimeContraptionRestoreResult(Status.SUCCESS, "", null);
    }

    public static RuntimeContraptionRestoreResult retry(String message) {
        return new RuntimeContraptionRestoreResult(Status.RETRY, message, null);
    }

    public static RuntimeContraptionRestoreResult permanentFailure(String message) {
        return new RuntimeContraptionRestoreResult(Status.PERMANENT_FAILURE, message, null);
    }

    public static RuntimeContraptionRestoreResult permanentFailure(String message, Throwable cause) {
        return new RuntimeContraptionRestoreResult(Status.PERMANENT_FAILURE, message, cause);
    }

    public boolean succeeded() {
        return status == Status.SUCCESS;
    }

    public boolean retryable() {
        return status == Status.RETRY;
    }

    public enum Status {
        SUCCESS,
        RETRY,
        PERMANENT_FAILURE
    }
}
