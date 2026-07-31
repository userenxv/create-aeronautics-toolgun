package com.enxv.aeronauticsstructuretool;

public enum BlueprintSourceType {
    NATIVE,
    VMOD,
    CREATE_PHYSICAL;

    public boolean isImported() {
        return this == VMOD;
    }
}
