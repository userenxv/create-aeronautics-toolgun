package com.enxv.aeronauticsstructuretool.vehicle.storage;

import org.joml.Vector3d;

import java.util.UUID;

public record StoredVehicleSnapshot(
        UUID id,
        String displayName,
        String fullName,
        Vector3d position,
        boolean broken
) {
}
