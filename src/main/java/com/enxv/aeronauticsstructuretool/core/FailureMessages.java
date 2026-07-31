package com.enxv.aeronauticsstructuretool.core;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class FailureMessages {
    private static final int MAX_MESSAGE_LENGTH = 180;

    private FailureMessages() {
    }

    public static String describe(Throwable failure, String fallback) {
        String best = null;
        Throwable current = failure;
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        while (current != null && visited.add(current)) {
            String message = concise(current.getMessage());
            if (message != null) {
                best = message;
            }
            current = current.getCause();
        }
        String safeFallback = concise(fallback);
        return best != null ? best : safeFallback == null ? "unknown error" : safeFallback;
    }

    private static String concise(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String normalized = message.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= MAX_MESSAGE_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";
    }
}
