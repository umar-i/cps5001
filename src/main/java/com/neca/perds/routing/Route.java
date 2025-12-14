package com.neca.perds.routing;

import com.neca.perds.model.NodeId;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record Route(
        List<NodeId> nodes,
        double totalCost,
        double totalDistanceKm,
        Duration totalTravelTime,
        long graphVersionUsed
) {
    public Route {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(totalTravelTime, "totalTravelTime");
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("nodes must not be empty");
        }
    }
}

