package com.enxv.aeronauticsstructuretool.client.tool.state;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.BearingAxisMode;
import com.enxv.aeronauticsstructuretool.ConnectionMode;
import com.enxv.aeronauticsstructuretool.PlacementSnapMode;
import com.enxv.aeronauticsstructuretool.ToolPanel;
import com.enxv.aeronauticsstructuretool.WeldSelectionMode;

import net.minecraft.util.Mth;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Function;

public final class ClientToolSettingsStore {
    private static final Path SETTINGS_PATH = FMLPaths.GAMEDIR.get()
            .resolve("config")
            .resolve("create_aeronautics_toolgun-client.properties");

    private ClientToolSettingsStore() {
    }

    public static ClientToolPreferences load() {
        Properties properties = new Properties();
        if (Files.exists(SETTINGS_PATH)) {
            try (InputStream input = Files.newInputStream(SETTINGS_PATH)) {
                properties.load(input);
            } catch (IOException exception) {
                AeronauticsStructureToolMod.LOGGER.warn(
                        "Failed to read client tool settings from {}",
                        SETTINGS_PATH,
                        exception
                );
            }
        }

        ClientToolPreferences preferences = new ClientToolPreferences();
        preferences.panel = parsePanel(properties, "panel", ToolPanel.BLUEPRINTS);
        preferences.selectedFile = properties.getProperty("selectedFile", "");
        preferences.previewEnabled = parseBoolean(properties, "previewEnabled", true);
        preferences.hudEnabled = parseBoolean(properties, "hudEnabled", true);
        preferences.magneticMarkersEnabled = parseBoolean(properties, "magneticMarkersEnabled", true);
        preferences.bearingAxisVisualsEnabled = parseBoolean(properties, "bearingAxisVisualsEnabled", true);
        preferences.autoWeldEnabled = parseBoolean(properties, "autoWeldEnabled", false);
        preferences.rangeDeleteEnabled = parseBoolean(properties, "rangeDeleteEnabled", false);
        preferences.connectionMode = parseMode(
                properties,
                "connectionMode",
                ConnectionMode.FIXED,
                ConnectionMode::fromName
        );
        preferences.bearingAxisMode = parseMode(
                properties,
                "bearingAxisMode",
                BearingAxisMode.AUTO,
                BearingAxisMode::fromName
        );
        preferences.weldSelectionMode = parseMode(
                properties,
                "weldSelectionMode",
                WeldSelectionMode.FACE_POINTS,
                WeldSelectionMode::fromName
        );
        preferences.snapMode = parseMode(
                properties,
                "snapMode",
                PlacementSnapMode.LEGACY,
                PlacementSnapMode::fromName
        );
        preferences.rotationStep = ClientToolPreferences.clampRotationStep(
                parseInt(properties, "rotationStep", 15)
        );
        preferences.translateStep = ClientToolPreferences.clampTranslateStep(
                parseDouble(properties, "translateStep", 0.25D)
        );
        preferences.rotationDegrees = ClientToolPreferences.normalizeRotation(
                parseInt(properties, "rotationDegrees", 0)
        );
        preferences.weldAdjustStep = ClientToolPreferences.clampWeldAdjustStep(
                parseDouble(properties, "weldAdjustStep", 0.25D)
        );
        preferences.scalePercent = Mth.clamp(parseInt(properties, "scalePercent", 100), 25, 400);
        preferences.offsetX = parseInt(properties, "offsetX", 0);
        preferences.offsetY = parseInt(properties, "offsetY", 0);
        preferences.offsetZ = parseInt(properties, "offsetZ", 0);
        preferences.deleteRange = Mth.clamp(parseInt(properties, "deleteRange", 8), 0, 128);
        preferences.nearbyQueryRange = Math.max(
                ClientToolPreferences.INFINITE_NEARBY_QUERY_RANGE,
                parseInt(
                        properties,
                        "nearbyQueryRange",
                        ClientToolPreferences.DEFAULT_CREATIVE_NEARBY_QUERY_RANGE
                )
        );
        preferences.connectedSublevelProximity = ClientToolPreferences.clampConnectedSublevelProximity(
                parseDouble(properties, "connectedSublevelProximity", 0.5D)
        );
        return preferences;
    }

    public static void save(ClientToolPreferences preferences) {
        Properties properties = new Properties();
        properties.setProperty("panel", preferences.panel.name());
        properties.setProperty("selectedFile", preferences.selectedFile);
        properties.setProperty("previewEnabled", Boolean.toString(preferences.previewEnabled));
        properties.setProperty("hudEnabled", Boolean.toString(preferences.hudEnabled));
        properties.setProperty("magneticMarkersEnabled", Boolean.toString(preferences.magneticMarkersEnabled));
        properties.setProperty("bearingAxisVisualsEnabled", Boolean.toString(preferences.bearingAxisVisualsEnabled));
        properties.setProperty("autoWeldEnabled", Boolean.toString(preferences.autoWeldEnabled));
        properties.setProperty("rangeDeleteEnabled", Boolean.toString(preferences.rangeDeleteEnabled));
        properties.setProperty("connectionMode", preferences.connectionMode.name());
        properties.setProperty("bearingAxisMode", preferences.bearingAxisMode.name());
        properties.setProperty("weldSelectionMode", preferences.weldSelectionMode.name());
        properties.setProperty("snapMode", preferences.snapMode.name());
        properties.setProperty("rotationStep", Integer.toString(preferences.rotationStep));
        properties.setProperty("translateStep", Double.toString(preferences.translateStep));
        properties.setProperty("rotationDegrees", Integer.toString(preferences.rotationDegrees));
        properties.setProperty("weldAdjustStep", Double.toString(preferences.weldAdjustStep));
        properties.setProperty("scalePercent", Integer.toString(preferences.scalePercent));
        properties.setProperty("offsetX", Integer.toString(preferences.offsetX));
        properties.setProperty("offsetY", Integer.toString(preferences.offsetY));
        properties.setProperty("offsetZ", Integer.toString(preferences.offsetZ));
        properties.setProperty("deleteRange", Integer.toString(preferences.deleteRange));
        properties.setProperty("nearbyQueryRange", Integer.toString(preferences.nearbyQueryRange));
        properties.setProperty(
                "connectedSublevelProximity",
                Double.toString(preferences.connectedSublevelProximity)
        );
        try {
            Files.createDirectories(SETTINGS_PATH.getParent());
            try (OutputStream output = Files.newOutputStream(SETTINGS_PATH)) {
                properties.store(output, "Create Aeronautics Toolgun Client Settings");
            }
        } catch (IOException exception) {
            AeronauticsStructureToolMod.LOGGER.error(
                    "Failed to save client tool settings to {}",
                    SETTINGS_PATH,
                    exception
            );
        }
    }

    private static boolean parseBoolean(Properties properties, String key, boolean fallback) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        if ("true".equalsIgnoreCase(raw.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw.trim())) {
            return false;
        }
        warnInvalid(key, raw, fallback);
        return fallback;
    }

    private static int parseInt(Properties properties, String key, int fallback) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            warnInvalid(key, raw, fallback);
            return fallback;
        }
    }

    private static double parseDouble(Properties properties, String key, double fallback) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException exception) {
            warnInvalid(key, raw, fallback);
            return fallback;
        }
    }

    private static ToolPanel parsePanel(Properties properties, String key, ToolPanel fallback) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return ToolPanel.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            warnInvalid(key, raw, fallback);
            return fallback;
        }
    }

    private static <T> T parseMode(
            Properties properties,
            String key,
            T fallback,
            Function<String, T> parser
    ) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return parser.apply(raw);
        } catch (IllegalArgumentException exception) {
            warnInvalid(key, raw, fallback);
            return fallback;
        }
    }

    private static void warnInvalid(String key, String raw, Object fallback) {
        AeronauticsStructureToolMod.LOGGER.warn(
                "Invalid client tool setting {}='{}'; using {}",
                key,
                raw,
                fallback
        );
    }
}
