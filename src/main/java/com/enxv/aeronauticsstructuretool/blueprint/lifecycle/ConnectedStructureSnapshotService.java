package com.enxv.aeronauticsstructuretool.blueprint.lifecycle;


import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;

import java.io.IOException;
import java.util.Collection;

public final class ConnectedStructureSnapshotService {
    private ConnectedStructureSnapshotService() {
    }

    public static ConnectedStructureSnapshot capture(
            ServerSubLevel rootSubLevel
    ) throws IOException {
        Collection<SubLevel> connected = SubLevelHelper.getConnectedChain(rootSubLevel);
        double totalMass = connected.stream()
                .mapToDouble(subLevel -> ((ServerSubLevel) subLevel).getMassTracker().getMass())
                .sum();
        return new ConnectedStructureSnapshot(
                rootSubLevel.getUniqueId(),
                connected.size(),
                totalMass
        );
    }

}
