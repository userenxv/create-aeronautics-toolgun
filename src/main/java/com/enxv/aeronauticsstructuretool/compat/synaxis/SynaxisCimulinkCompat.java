package com.enxv.aeronauticsstructuretool.compat.synaxis;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.LoadedSubLevelCoordinates;
import com.enxv.aeronauticsstructuretool.blueprint.geometry.PlotBlockTransform;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturePlan;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturedSubLevel;
import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class SynaxisCimulinkCompat {
    private static final String WORLD_RUNTIMES =
            "com.verr1.synaxis.foundation.cimulink.game.runtime.CimulinkWorldRuntimes";
    private static final String ENDPOINT_PORT_REF =
            "com.verr1.synaxis.foundation.cimulink.game.endpoint.EndpointPortRef";
    private static final String ENDPOINT_ID =
            "com.verr1.synaxis.foundation.cimulink.game.endpoint.EndpointId";
    private static final String ENDPOINT_PROVIDER =
            "com.verr1.synaxis.foundation.cimulink.game.endpoint.CimulinkEndpointProvider";

    private SynaxisCimulinkCompat() {
    }

    static SynaxisBlueprintNbt.CimulinkManifest capture(
            ServerLevel level,
            CapturePlan plan
    ) throws IOException {
        if (!SynaxisReflection.isInstalled()) {
            return SynaxisBlueprintNbt.CimulinkManifest.EMPTY;
        }
        Object levelRuntime = levelRuntime(level);
        SynaxisReflection.invoke(levelRuntime, "flushPendingNetworks");
        Object runtime = SynaxisReflection.invoke(levelRuntime, "runtime");
        Object endpointRegistry = SynaxisReflection.invoke(runtime, "endpoints");
        Object rawLiveEndpoints = SynaxisReflection.invoke(endpointRegistry, "liveEndpoints");
        if (!(rawLiveEndpoints instanceof Collection<?> liveEndpoints)) {
            throw new IOException("Synaxis Cimulink liveEndpoints() returned a non-collection value");
        }

        Map<UUID, SynaxisBlueprintNbt.CimulinkEndpoint> capturedEndpoints = new LinkedHashMap<>();
        int outsideCapture = 0;
        for (Object endpoint : liveEndpoints) {
            if (endpoint == null) {
                throw new IOException("Synaxis Cimulink endpoint registry returned a null endpoint");
            }
            UUID endpointId = endpointUuid(SynaxisReflection.invoke(endpoint, "id"));
            Object address = SynaxisReflection.invoke(endpoint, "address");
            Object dimension = SynaxisReflection.invoke(address, "dimension");
            Object position = SynaxisReflection.invoke(address, "pos");
            if (!(position instanceof BlockPos addressPos)) {
                throw new IOException("Synaxis Cimulink endpoint address returned an invalid position");
            }
            if (!level.dimension().equals(dimension)) {
                continue;
            }
            CapturedSubLevel captured = findCaptured(plan, addressPos);
            if (captured == null) {
                outsideCapture++;
                continue;
            }
            capturedEndpoints.put(endpointId, new SynaxisBlueprintNbt.CimulinkEndpoint(
                    endpointId,
                    captured.blueprintId(),
                    PlotBlockTransform.capture(captured.subLevel()).toSavedLocalBlockPos(addressPos)
            ));
        }
        if (capturedEndpoints.isEmpty()) {
            return SynaxisBlueprintNbt.CimulinkManifest.EMPTY;
        }

        Object rawNetworks = SynaxisReflection.invoke(runtime, "networks");
        if (!(rawNetworks instanceof Collection<?> networks)) {
            throw new IOException("Synaxis Cimulink networks() returned a non-collection value");
        }
        List<SynaxisBlueprintNbt.CimulinkLink> capturedLinks = new ArrayList<>();
        Set<String> seenLinks = new LinkedHashSet<>();
        int linksOutsideCapture = 0;
        for (Object network : networks) {
            if (network == null) {
                throw new IOException("Synaxis Cimulink runtime returned a null network");
            }
            Object rawLinks = SynaxisReflection.invoke(network, "links");
            if (!(rawLinks instanceof Collection<?> links)) {
                throw new IOException("Synaxis Cimulink network links() returned a non-collection value");
            }
            for (Object link : links) {
                if (link == null) {
                    throw new IOException("Synaxis Cimulink network returned a null link");
                }
                if (isTombstoned(SynaxisReflection.invoke(link, "state"))) {
                    continue;
                }
                Object from = SynaxisReflection.invoke(link, "from");
                Object to = SynaxisReflection.invoke(link, "to");
                UUID fromEndpoint = endpointUuid(SynaxisReflection.invoke(from, "endpointId"));
                UUID toEndpoint = endpointUuid(SynaxisReflection.invoke(to, "endpointId"));
                if (!capturedEndpoints.containsKey(fromEndpoint) || !capturedEndpoints.containsKey(toEndpoint)) {
                    linksOutsideCapture++;
                    continue;
                }
                Object fromPortValue = SynaxisReflection.invoke(from, "port");
                Object toPortValue = SynaxisReflection.invoke(to, "port");
                if (!(fromPortValue instanceof String fromPort) || fromPort.isBlank()
                        || !(toPortValue instanceof String toPort) || toPort.isBlank()) {
                    throw new IOException("Synaxis Cimulink link returned an invalid port name");
                }
                String key = fromEndpoint + ":" + fromPort + "->" + toEndpoint + ":" + toPort;
                if (seenLinks.add(key)) {
                    capturedLinks.add(new SynaxisBlueprintNbt.CimulinkLink(
                            fromEndpoint,
                            fromPort,
                            toEndpoint,
                            toPort
                    ));
                }
            }
        }

        if (outsideCapture > 0 || linksOutsideCapture > 0) {
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Synaxis Cimulink capture omitted {} endpoint(s) and {} link(s) outside the captured structure",
                    outsideCapture,
                    linksOutsideCapture
            );
        }
        AeronauticsStructureToolMod.LOGGER.info(
                "Synaxis Cimulink capture: endpoints={} links={}",
                capturedEndpoints.size(),
                capturedLinks.size()
        );
        return new SynaxisBlueprintNbt.CimulinkManifest(
                new ArrayList<>(capturedEndpoints.values()),
                capturedLinks
        );
    }

    static void restore(
            ServerLevel level,
            String blueprintName,
            Map<UUID, LoadedSubLevel> loadedSublevels,
            SynaxisBlueprintNbt.CimulinkManifest manifest
    ) throws IOException {
        if (manifest.endpoints().isEmpty() && manifest.links().isEmpty()) {
            return;
        }
        if (manifest.endpoints().isEmpty()) {
            throw new IOException("Synaxis Cimulink manifest contains links without endpoints");
        }
        if (!SynaxisReflection.isInstalled()) {
            throw new IOException("Blueprint '" + blueprintName + "' contains Synaxis Cimulink data but Synaxis is not loaded");
        }

        Object levelRuntime = levelRuntime(level);
        SynaxisReflection.invoke(levelRuntime, "flushPendingNetworks");
        Class<?> endpointPortRefClass = SynaxisReflection.requireClass(ENDPOINT_PORT_REF);
        Class<?> endpointIdClass = SynaxisReflection.requireClass(ENDPOINT_ID);
        Method portRefFactory = SynaxisReflection.requirePublicMethod(
                endpointPortRefClass,
                "of",
                endpointIdClass,
                String.class
        );

        Map<UUID, Object> oldToNewEndpointIds = new LinkedHashMap<>();
        for (SynaxisBlueprintNbt.CimulinkEndpoint endpoint : manifest.endpoints()) {
            LoadedSubLevel loaded = loadedSublevels.get(endpoint.sublevelId());
            if (loaded == null) {
                throw new IOException(
                        "Synaxis Cimulink endpoint references missing sublevel " + endpoint.sublevelId()
                );
            }
            BlockPos worldPos = LoadedSubLevelCoordinates.toGlobalBlockPos(loaded, endpoint.localPos());
            Object endpointId = findEndpointId(level, worldPos);
            if (endpointId == null) {
                throw new IOException("Synaxis Cimulink endpoint is not registered at " + worldPos);
            }
            if (oldToNewEndpointIds.put(endpoint.oldEndpointId(), endpointId) != null) {
                throw new IOException("Synaxis Cimulink manifest contains duplicate endpoint UUID " + endpoint.oldEndpointId());
            }
        }

        int restored = 0;
        int existing = 0;
        for (SynaxisBlueprintNbt.CimulinkLink link : manifest.links()) {
            Object fromEndpoint = oldToNewEndpointIds.get(link.fromEndpointId());
            Object toEndpoint = oldToNewEndpointIds.get(link.toEndpointId());
            if (fromEndpoint == null || toEndpoint == null) {
                throw new IOException("Synaxis Cimulink link references an endpoint outside its manifest");
            }
            Object fromPortRef = SynaxisReflection.invokeMethod(
                    portRefFactory,
                    null,
                    fromEndpoint,
                    link.fromPort()
            );
            Object toPortRef = SynaxisReflection.invokeMethod(
                    portRefFactory,
                    null,
                    toEndpoint,
                    link.toPort()
            );
            if (hasExistingLink(levelRuntime, fromPortRef, toPortRef)) {
                existing++;
                continue;
            }
            Object linkRecord = SynaxisReflection.invoke(levelRuntime, "connect", fromPortRef, toPortRef);
            if (linkRecord == null) {
                throw new IOException("Synaxis Cimulink connect() returned no link record");
            }
            restored++;
        }
        if (restored > 0) {
            SynaxisReflection.invoke(levelRuntime, "flushPendingNetworks");
        }
        AeronauticsStructureToolMod.LOGGER.info(
                "Synaxis Cimulink restore for '{}': endpoints={} links={} restored={} existing={}",
                blueprintName,
                manifest.endpoints().size(),
                manifest.links().size(),
                restored,
                existing
        );
    }

    private static Object levelRuntime(ServerLevel level) throws IOException {
        return SynaxisReflection.invokeStatic(
                WORLD_RUNTIMES,
                "forLevel",
                new Class<?>[]{ServerLevel.class},
                level
        );
    }

    private static Object findEndpointId(ServerLevel level, BlockPos worldPos) throws IOException {
        BlockEntity blockEntity = level.getBlockEntity(worldPos);
        if (blockEntity == null) {
            return null;
        }
        Class<?> providerClass = SynaxisReflection.requireClass(ENDPOINT_PROVIDER);
        if (!providerClass.isInstance(blockEntity)) {
            return null;
        }
        Object endpoint = SynaxisReflection.invoke(blockEntity, "endpoint");
        return endpoint == null ? null : SynaxisReflection.invoke(endpoint, "id");
    }

    private static boolean hasExistingLink(
            Object levelRuntime,
            Object fromPortRef,
            Object toPortRef
    ) throws IOException {
        Object runtime = SynaxisReflection.invoke(levelRuntime, "runtime");
        Object rawNetworks = SynaxisReflection.invoke(runtime, "networks");
        if (!(rawNetworks instanceof Collection<?> networks)) {
            throw new IOException("Synaxis Cimulink networks() returned a non-collection value");
        }
        for (Object network : networks) {
            Object rawLinks = SynaxisReflection.invoke(network, "links");
            if (!(rawLinks instanceof Collection<?> links)) {
                throw new IOException("Synaxis Cimulink links() returned a non-collection value");
            }
            for (Object link : links) {
                if (link == null || isTombstoned(SynaxisReflection.invoke(link, "state"))) {
                    continue;
                }
                Object existingFrom = SynaxisReflection.invoke(link, "from");
                Object existingTo = SynaxisReflection.invoke(link, "to");
                if (Objects.equals(existingFrom, fromPortRef) && Objects.equals(existingTo, toPortRef)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static UUID endpointUuid(Object endpointId) throws IOException {
        Object value = SynaxisReflection.invoke(endpointId, "value");
        if (value instanceof UUID uuid) {
            return uuid;
        }
        throw new IOException("Synaxis Cimulink endpoint ID returned a non-UUID value");
    }

    private static boolean isTombstoned(Object state) {
        return state != null && "TOMBSTONED".equals(state.toString());
    }

    private static CapturedSubLevel findCaptured(CapturePlan plan, BlockPos worldPos) {
        for (CapturedSubLevel captured : plan.sublevels()) {
            if (PlotBlockTransform.capture(captured.subLevel()).containsPlotAbsolute(worldPos)) {
                return captured;
            }
        }
        return null;
    }
}
