package com.enxv.aeronauticsstructuretool.client.screen.toolmode;

import com.enxv.aeronauticsstructuretool.ClientToolState;

public final class ToolModeQueryRange {
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
}
