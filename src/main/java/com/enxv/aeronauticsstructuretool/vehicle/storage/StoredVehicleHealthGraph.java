package com.enxv.aeronauticsstructuretool.vehicle.storage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class StoredVehicleHealthGraph {
    private StoredVehicleHealthGraph() {
    }

    static Set<UUID> brokenIds(Map<UUID, Node> nodes) {
        Map<UUID, List<UUID>> dependents = new HashMap<>();
        Set<UUID> broken = new HashSet<>();
        for (Map.Entry<UUID, Node> entry : nodes.entrySet()) {
            UUID id = entry.getKey();
            Node node = entry.getValue();
            if (!node.valid()) {
                broken.add(id);
            }
            for (UUID dependency : node.dependencies()) {
                if (dependency == null || !nodes.containsKey(dependency)) {
                    broken.add(id);
                    continue;
                }
                dependents.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(id);
            }
        }

        ArrayDeque<UUID> queue = new ArrayDeque<>(broken);
        while (!queue.isEmpty()) {
            for (UUID dependent : dependents.getOrDefault(queue.removeFirst(), List.of())) {
                if (broken.add(dependent)) {
                    queue.addLast(dependent);
                }
            }
        }
        return Set.copyOf(broken);
    }

    record Node(boolean valid, List<UUID> dependencies) {
        Node {
            dependencies = Collections.unmodifiableList(new ArrayList<>(dependencies));
        }
    }
}
