package com.enxv.aeronauticsstructuretool.client.screen.toolmode;

import com.enxv.aeronauticsstructuretool.ClientToolState;

public final class ToolModeQueryRange {
    private static final long NEARBY_REFRESH_INTERVAL_MS = 500L;
    private static final long INFINITE_NEARBY_REFRESH_INTERVAL_MS = 5000L;

    private ToolModeQueryRange() {
    }

    public static int parse(String raw, int fallback, boolean survivalRestricted) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String trimmed = raw.trim();
        if (!survivalRestricted
                && ("0".equals(trimmed) || "*".equals(trimmed) || "all".equalsIgnoreCase(trimmed))) {
            return ClientToolState.INFINITE_NEARBY_QUERY_RANGE;
        }
        String digits = trimmed.replaceAll("[^0-9]", "");
        if (!digits.equals(trimmed)) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(digits);
            if (survivalRestricted && parsed == ClientToolState.INFINITE_NEARBY_QUERY_RANGE) {
                return ClientToolState.DEFAULT_SURVIVAL_NEARBY_QUERY_RANGE;
            }
            return parsed;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public static String format(int range) {
        return range == ClientToolState.INFINITE_NEARBY_QUERY_RANGE ? "0" : Integer.toString(range);
    }

    public static long refreshIntervalMillis(int range) {
        return range == ClientToolState.INFINITE_NEARBY_QUERY_RANGE
                ? INFINITE_NEARBY_REFRESH_INTERVAL_MS
                : NEARBY_REFRESH_INTERVAL_MS;
    }
}
