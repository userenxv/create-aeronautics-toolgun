package com.enxv.aeronauticsstructuretool.network.handler;

import com.enxv.aeronauticsstructuretool.CompletePortableStructurePrinterEffectPayload;
import com.enxv.aeronauticsstructuretool.OpenPortableStructurePrinterPayload;
import com.enxv.aeronauticsstructuretool.SpawnPortableStructurePrinterEffectPayload;
import com.enxv.aeronauticsstructuretool.SyncLocalBlueprintPayload;
import com.enxv.aeronauticsstructuretool.SyncPortableStructurePrinterStatePayload;
import com.enxv.aeronauticsstructuretool.SyncQueryVehiclePreviewPayload;
import com.enxv.aeronauticsstructuretool.SyncQueryVehiclesPayload;
import com.enxv.aeronauticsstructuretool.MagneticGunStartResultPayload;
import com.enxv.aeronauticsstructuretool.core.ModConstants;
import com.enxv.aeronauticsstructuretool.blueprint.storage.BlueprintFileRepository;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.InvocationTargetException;

public final class ClientPayloadHandlers {
    private static final String TOOL_MODE_SCREEN = "com.enxv.aeronauticsstructuretool.client.screen.ToolModeScreen";
    private static final String PRINTER_SCREEN = "com.enxv.aeronauticsstructuretool.client.screen.PortableStructurePrinterScreen";
    private static final String PRINTER_EFFECTS = "com.enxv.aeronauticsstructuretool.client.render.PortableStructurePrinterClientEffects";
    private static final String MAGNETIC_GUN_CONTROLLER = "com.enxv.aeronauticsstructuretool.client.tool.MagneticGunClientController";

    private ClientPayloadHandlers() {
    }

    public static void handleOpenPortableStructurePrinter(OpenPortableStructurePrinterPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> invokeClientStatic(PRINTER_SCREEN, "open", OpenPortableStructurePrinterPayload.class, payload));
    }

    public static void handleSyncPortableStructurePrinterState(SyncPortableStructurePrinterStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> invokeClientStatic(PRINTER_SCREEN, "receiveState", SyncPortableStructurePrinterStatePayload.class, payload));
    }

    public static void handleSpawnPortableStructurePrinterEffect(SpawnPortableStructurePrinterEffectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> invokeClientStatic(PRINTER_EFFECTS, "spawn", SpawnPortableStructurePrinterEffectPayload.class, payload));
    }

    public static void handleCompletePortableStructurePrinterEffect(CompletePortableStructurePrinterEffectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> invokeClientStatic(PRINTER_EFFECTS, "complete", BlockPos.class, payload.printerPos()));
    }

    public static void handleMagneticGunStartResult(MagneticGunStartResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> invokeClientStatic(
                MAGNETIC_GUN_CONTROLLER,
                "handleStartResult",
                MagneticGunStartResultPayload.class,
                payload
        ));
    }

    public static void handleSyncLocalBlueprint(SyncLocalBlueprintPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) {
                return;
            }
            try {
                BlueprintFileRepository.write(
                        BlueprintFileRepository.clientDirectory(FMLPaths.GAMEDIR.get()),
                        payload.fileName(),
                        payload.fileContents()
                );
            } catch (Exception exception) {
                ModConstants.LOGGER.warn("Failed to sync client blueprint {}", payload.fileName(), exception);
            }
        });
    }

    public static void handleSyncQueryVehicles(SyncQueryVehiclesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> invokeClientStatic(TOOL_MODE_SCREEN, "receiveQueriedVehicles", SyncQueryVehiclesPayload.class, payload));
    }

    public static void handleSyncQueryVehiclePreview(SyncQueryVehiclePreviewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> invokeClientStatic(TOOL_MODE_SCREEN, "receiveQueryVehiclePreview", SyncQueryVehiclePreviewPayload.class, payload));
    }

    private static void invokeClientStatic(String className, String methodName, Class<?> parameterClass, Object value) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        try {
            Class<?> clientClass = Class.forName(className);
            clientClass.getMethod(methodName, parameterClass).invoke(null, value);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            ModConstants.LOGGER.warn("Failed to dispatch payload to client class {}", className, exception);
        } catch (InvocationTargetException exception) {
            ModConstants.LOGGER.warn("Client payload handler failed in {}", className, exception.getCause());
        }
    }
}
