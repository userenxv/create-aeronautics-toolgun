package com.enxv.aeronauticsstructuretool.compat.synaxis;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.LoadedSubLevelCoordinates;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockTransform;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturePlan;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturedSubLevel;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class SynaxisControllerWireCompat {
    private static final String MANAGER =
            "com.verr1.synaxis.foundation.controllerwire.ControllerWireNetworkManager";
    private static final String SIGNAL_SOURCE =
            "com.verr1.synaxis.foundation.controllerwire.ControllerSignalSource";
    private static final String PACKETS = "com.verr1.synaxis.foundation.network.SynaxisPackets";

    private SynaxisControllerWireCompat() {
    }

    static void capture(
            ServerLevel level,
            CompoundTag plotTag,
            CapturePlan plan,
            SubLevel currentSubLevel
    ) throws IOException {
        if (!SynaxisReflection.isInstalled()) {
            return;
        }
        Class<?> managerClass = SynaxisReflection.requireClass(MANAGER);
        Object manager = invokePublicStatic(
                managerClass,
                "get",
                new Class<?>[]{net.minecraft.world.level.Level.class},
                level
        );
        Object rawConnections = invokePublic(managerClass, manager, "connections", new Class<?>[0]);
        if (!(rawConnections instanceof List<?> connections)) {
            throw new IOException("Synaxis controller-wire connections() returned a non-list value");
        }

        List<SynaxisBlueprintNbt.ControllerWire> captured = new ArrayList<>();
        int outsideCapture = 0;
        int staleSources = 0;
        for (Object connection : connections) {
            if (connection == null) {
                throw new IOException("Synaxis controller-wire manager returned a null connection");
            }
            Object sourceValue = SynaxisReflection.invoke(connection, "source");
            Object sinkValue = SynaxisReflection.invoke(connection, "sink");
            Object channelValue = SynaxisReflection.invoke(connection, "channel");
            Object directionValue = SynaxisReflection.invoke(connection, "direction");
            if (!(sourceValue instanceof BlockPos sourcePos)
                    || !(sinkValue instanceof BlockPos sinkPos)
                    || !(channelValue instanceof String channel)
                    || channel.isBlank()
                    || !(directionValue instanceof Direction direction)) {
                throw new IOException("Synaxis controller-wire manager returned an invalid connection record");
            }
            if (!sameSubLevel(currentSubLevel, Sable.HELPER.getContaining(level, sourcePos))) {
                continue;
            }
            if (!sourceHasChannel(level, sourcePos, channel)) {
                staleSources++;
                continue;
            }

            CapturedSubLevel sourceSubLevel = findCaptured(plan, sourcePos);
            CapturedSubLevel sinkSubLevel = findCaptured(plan, sinkPos);
            if (sourceSubLevel == null || sinkSubLevel == null) {
                outsideCapture++;
                continue;
            }
            captured.add(new SynaxisBlueprintNbt.ControllerWire(
                    sourceSubLevel.blueprintId(),
                    PlotBlockTransform.capture(sourceSubLevel.subLevel()).toSavedLocalBlockPos(sourcePos),
                    sinkSubLevel.blueprintId(),
                    PlotBlockTransform.capture(sinkSubLevel.subLevel()).toSavedLocalBlockPos(sinkPos),
                    direction,
                    channel
            ));
        }
        SynaxisBlueprintNbt.writeControllerWires(plotTag, captured);
        if (outsideCapture > 0) {
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Synaxis controller-wire capture omitted {} connection(s) outside the captured structure",
                    outsideCapture
            );
        }
        if (staleSources > 0) {
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Synaxis controller-wire capture omitted {} stale connection(s) whose source no longer exposes the channel",
                    staleSources
            );
        }
    }

    static RestoreOutcome restore(
            ServerLevel level,
            String blueprintName,
            Map<UUID, LoadedSubLevel> loadedSublevels,
            List<SynaxisBlueprintNbt.ControllerWire> requests
    ) throws IOException {
        if (requests.isEmpty()) {
            return RestoreOutcome.EMPTY;
        }
        if (!SynaxisReflection.isInstalled()) {
            throw new IOException("Blueprint '" + blueprintName + "' contains Synaxis controller wires but Synaxis is not loaded");
        }

        int restored = 0;
        int existing = 0;
        List<SynaxisControllerWireConnection> deferred = new ArrayList<>();
        for (SynaxisBlueprintNbt.ControllerWire request : requests) {
            LoadedSubLevel sourceLoaded = requireLoaded(loadedSublevels, request.sourceSublevelId(), "source");
            LoadedSubLevel sinkLoaded = requireLoaded(loadedSublevels, request.sinkSublevelId(), "sink");
            BlockPos sourceWorldPos = LoadedSubLevelCoordinates.toGlobalBlockPos(
                    sourceLoaded,
                    request.sourceLocalPos()
            );
            BlockPos sinkWorldPos = LoadedSubLevelCoordinates.toGlobalBlockPos(
                    sinkLoaded,
                    request.sinkLocalPos()
            );
            SynaxisControllerWireConnection connection = new SynaxisControllerWireConnection(
                    sourceWorldPos,
                    sinkWorldPos,
                    request.direction(),
                    request.channel()
            );
            if (!sourceHasChannel(level, sourceWorldPos, request.channel())) {
                deferred.add(connection);
                continue;
            }
            ConnectionResult result = createConnection(level, connection);
            if (result == ConnectionResult.RESTORED) {
                restored++;
            } else if (result == ConnectionResult.ALREADY_EXISTS) {
                existing++;
            } else if (result == ConnectionResult.RETRY_LATER) {
                deferred.add(connection);
            } else {
                throw new IOException(
                        "Synaxis rejected controller-wire connection " + sourceWorldPos + " -> " + sinkWorldPos
                                + " on channel '" + request.channel() + "'"
                );
            }
        }
        if (restored > 0) {
            syncDimension(level);
        }
        AeronauticsStructureToolMod.LOGGER.info(
                "Synaxis controller-wire restore for '{}': requested={} restored={} existing={} deferred={}",
                blueprintName,
                requests.size(),
                restored,
                existing,
                deferred.size()
        );
        return new RestoreOutcome(restored, existing, deferred);
    }

    public static boolean sourceHasChannel(ServerLevel level, BlockPos sourcePos, String channel) throws IOException {
        BlockEntity blockEntity = level.getBlockEntity(sourcePos);
        if (blockEntity == null) {
            return false;
        }
        Class<?> sourceClass = SynaxisReflection.requireClass(SIGNAL_SOURCE);
        if (!sourceClass.isInstance(blockEntity)) {
            return false;
        }
        Method method = SynaxisReflection.requirePublicMethod(
                sourceClass,
                "controllerWireHasChannel",
                String.class
        );
        Object result = SynaxisReflection.invokeMethod(method, blockEntity, channel);
        if (result instanceof Boolean value) {
            return value;
        }
        throw new IOException("Synaxis controllerWireHasChannel returned a non-boolean value");
    }

    public static ConnectionResult createConnection(
            ServerLevel level,
            SynaxisControllerWireConnection connection
    ) throws IOException {
        Object rawResult = SynaxisReflection.invokeStatic(
                MANAGER,
                "createConnection",
                new Class<?>[]{
                        net.minecraft.world.level.Level.class,
                        BlockPos.class,
                        BlockPos.class,
                        Direction.class,
                        String.class
                },
                level,
                connection.sourcePos(),
                connection.sinkPos(),
                connection.direction(),
                connection.channel()
        );
        String resultName = rawResult == null ? "" : rawResult.toString();
        return switch (resultName) {
            case "OK" -> ConnectionResult.RESTORED;
            case "FAIL_EXISTS" -> ConnectionResult.ALREADY_EXISTS;
            case "FAIL_NOT_SOURCE", "FAIL_UNKNOWN_CHANNEL" -> ConnectionResult.RETRY_LATER;
            case "FAIL_CLIENT_SIDE", "FAIL_INVALID", "FAIL_TOO_MANY_SOURCES",
                 "FAIL_TOO_MANY_SINKS", "FAIL_SAME_BLOCK" -> ConnectionResult.PERMANENT_FAILURE;
            default -> throw new IOException("Unknown Synaxis controller-wire result: '" + resultName + "'");
        };
    }

    public static void syncDimension(ServerLevel level) throws IOException {
        SynaxisReflection.invokeStatic(
                PACKETS,
                "syncControllerWireInDimension",
                new Class<?>[]{ServerLevel.class},
                level
        );
    }

    private static LoadedSubLevel requireLoaded(
            Map<UUID, LoadedSubLevel> loadedSublevels,
            UUID id,
            String endpoint
    ) throws IOException {
        LoadedSubLevel loaded = loadedSublevels.get(id);
        if (loaded == null) {
            throw new IOException("Synaxis controller-wire " + endpoint + " references missing sublevel " + id);
        }
        return loaded;
    }

    private static CapturedSubLevel findCaptured(CapturePlan plan, BlockPos worldPos) {
        for (CapturedSubLevel captured : plan.sublevels()) {
            if (PlotBlockTransform.capture(captured.subLevel()).containsPlotAbsolute(worldPos)) {
                return captured;
            }
        }
        return null;
    }

    private static boolean sameSubLevel(SubLevel expected, SubLevel actual) {
        return expected != null
                && actual != null
                && Objects.equals(expected.getUniqueId(), actual.getUniqueId());
    }

    private static Object invokePublicStatic(
            Class<?> type,
            String name,
            Class<?>[] parameterTypes,
            Object... arguments
    ) throws IOException {
        Method method = SynaxisReflection.requirePublicMethod(type, name, parameterTypes);
        return SynaxisReflection.invokeMethod(method, null, arguments);
    }

    private static Object invokePublic(
            Class<?> type,
            Object target,
            String name,
            Class<?>[] parameterTypes,
            Object... arguments
    ) throws IOException {
        Method method = SynaxisReflection.requirePublicMethod(type, name, parameterTypes);
        return SynaxisReflection.invokeMethod(method, target, arguments);
    }

    public enum ConnectionResult {
        RESTORED,
        ALREADY_EXISTS,
        RETRY_LATER,
        PERMANENT_FAILURE
    }

    record RestoreOutcome(
            int restored,
            int existing,
            List<SynaxisControllerWireConnection> deferred
    ) {
        static final RestoreOutcome EMPTY = new RestoreOutcome(0, 0, List.of());

        RestoreOutcome {
            deferred = List.copyOf(deferred);
        }
    }
}
