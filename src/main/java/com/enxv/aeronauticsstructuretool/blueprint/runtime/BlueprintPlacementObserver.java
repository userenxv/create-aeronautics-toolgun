package com.enxv.aeronauticsstructuretool.blueprint.runtime;

public interface BlueprintPlacementObserver {
    void onCompleted();

    void onFailed(String reason);
}
