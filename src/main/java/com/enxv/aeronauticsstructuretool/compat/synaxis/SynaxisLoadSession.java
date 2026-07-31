package com.enxv.aeronauticsstructuretool.compat.synaxis;

import com.enxv.aeronauticsstructuretool.blueprint.model.LoadedSubLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SynaxisLoadSession {
    private final String blueprintName;
    private final boolean hasGlobalCimulinkManifest;
    private final List<SynaxisBlueprintNbt.ControllerWire> controllerWires = new ArrayList<>();
    private final List<SynaxisBlueprintNbt.CompanionJoint> companionJoints = new ArrayList<>();
    private final List<SynaxisBlueprintNbt.CimulinkEndpoint> cimulinkEndpoints = new ArrayList<>();
    private final List<SynaxisBlueprintNbt.CimulinkLink> cimulinkLinks = new ArrayList<>();

    public SynaxisLoadSession(String blueprintName, CompoundTag root) throws IOException {
        this.blueprintName = blueprintName;
        this.hasGlobalCimulinkManifest = SynaxisBlueprintNbt.hasCimulinkManifest(root);
        if (hasGlobalCimulinkManifest) {
            mergeCimulink(SynaxisBlueprintNbt.readCimulink(root));
        }
    }

    public void collectPlot(CompoundTag plotTag) throws IOException {
        controllerWires.addAll(SynaxisBlueprintNbt.readControllerWires(plotTag));
        companionJoints.addAll(SynaxisBlueprintNbt.readCompanionJoints(plotTag));
        if (!hasGlobalCimulinkManifest) {
            mergeCimulink(SynaxisBlueprintNbt.readCimulink(plotTag));
        }
    }

    public SynaxisLoadResult complete(
            ServerLevel level,
            Map<UUID, LoadedSubLevel> loadedSublevels
    ) throws IOException {
        SynaxisCompanionJointCompat.restore(
                level,
                blueprintName,
                loadedSublevels,
                companionJoints
        );
        SynaxisControllerWireCompat.RestoreOutcome controllerResult =
                SynaxisControllerWireCompat.restore(
                        level,
                        blueprintName,
                        loadedSublevels,
                        controllerWires
                );
        SynaxisCimulinkCompat.restore(
                level,
                blueprintName,
                loadedSublevels,
                new SynaxisBlueprintNbt.CimulinkManifest(cimulinkEndpoints, cimulinkLinks)
        );
        return new SynaxisLoadResult(controllerResult.deferred());
    }

    private void mergeCimulink(SynaxisBlueprintNbt.CimulinkManifest manifest) {
        cimulinkEndpoints.addAll(manifest.endpoints());
        cimulinkLinks.addAll(manifest.links());
    }
}
