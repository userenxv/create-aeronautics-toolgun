package com.enxv.aeronauticsstructuretool.vehicle.storage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class StoredVehicleHealthGraphRegressionCheck {
    private StoredVehicleHealthGraphRegressionCheck() {
    }

    public static void main(String[] args) {
        healthySingleVehicle();
        healthyDependencyCycle();
        missingDependencyPropagation();
        invalidDependencyPropagation();
        longDependencyChain();
    }

    private static void healthySingleVehicle() {
        UUID vehicle = id(1);
        requireBroken(Map.of(vehicle, node(true)), Set.of(), "healthy vehicle");
    }

    private static void healthyDependencyCycle() {
        UUID first = id(10);
        UUID second = id(11);
        requireBroken(
                Map.of(first, node(true, second), second, node(true, first)),
                Set.of(),
                "healthy dependency cycle"
        );
    }

    private static void missingDependencyPropagation() {
        UUID root = id(20);
        UUID child = id(21);
        UUID missing = id(22);
        UUID unrelated = id(23);
        requireBroken(
                Map.of(
                        root, node(true, child),
                        child, node(true, missing),
                        unrelated, node(true)
                ),
                Set.of(root, child),
                "missing dependency propagation"
        );
    }

    private static void invalidDependencyPropagation() {
        UUID root = id(30);
        UUID sharedParent = id(31);
        UUID broken = id(32);
        UUID unrelated = id(33);
        requireBroken(
                Map.of(
                        root, node(true, sharedParent),
                        sharedParent, node(true, broken),
                        broken, node(false),
                        unrelated, node(true)
                ),
                Set.of(root, sharedParent, broken),
                "invalid dependency propagation"
        );
    }

    private static void longDependencyChain() {
        int length = 50_000;
        Map<UUID, StoredVehicleHealthGraph.Node> nodes = new LinkedHashMap<>();
        for (int index = 0; index < length; index++) {
            UUID current = id(100_000L + index);
            if (index + 1 == length) {
                nodes.put(current, node(false));
            } else {
                nodes.put(current, node(true, id(100_001L + index)));
            }
        }
        Set<UUID> broken = StoredVehicleHealthGraph.brokenIds(nodes);
        if (broken.size() != length || !broken.contains(id(100_000L))) {
            throw new IllegalStateException("long dependency chain was not fully classified");
        }
    }

    private static StoredVehicleHealthGraph.Node node(boolean valid, UUID... dependencies) {
        return new StoredVehicleHealthGraph.Node(valid, List.of(dependencies));
    }

    private static UUID id(long value) {
        return new UUID(0L, value);
    }

    private static void requireBroken(
            Map<UUID, StoredVehicleHealthGraph.Node> nodes,
            Set<UUID> expected,
            String description
    ) {
        Set<UUID> actual = StoredVehicleHealthGraph.brokenIds(nodes);
        if (!actual.equals(expected)) {
            throw new IllegalStateException(description + " classified " + actual + " instead of " + expected);
        }
    }
}
