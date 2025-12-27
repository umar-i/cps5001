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
        if (totalCost < 0 || Double.isNaN(totalCost)) {
            throw new IllegalArgumentException("totalCost must be >= 0 and not NaN");
        }
        if (totalDistanceKm < 0 || Double.isNaN(totalDistanceKm)) {
            throw new IllegalArgumentException("totalDistanceKm must be >= 0 and not NaN");
        }
        if (totalTravelTime.isNegative()) {
            throw new IllegalArgumentException("totalTravelTime must be >= 0");
        }
    }
}

