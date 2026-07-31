package com.enxv.aeronauticsstructuretool.compat.synaxis;

import java.util.List;

public record SynaxisLoadResult(List<SynaxisControllerWireConnection> deferredControllerWires) {
    public SynaxisLoadResult {
        deferredControllerWires = List.copyOf(deferredControllerWires);
    }
}
