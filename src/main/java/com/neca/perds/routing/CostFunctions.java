package com.neca.perds.routing;

import com.neca.perds.graph.EdgeStatus;

public final class CostFunctions {
    private CostFunctions() {}

    public static EdgeCostFunction travelTimeSeconds() {
        return edge -> edge.status() == EdgeStatus.CLOSED
                ? Double.POSITIVE_INFINITY
                : edge.weights().travelTime().toSeconds();
    }

    public static EdgeCostFunction distanceKm() {
        return edge -> edge.status() == EdgeStatus.CLOSED
                ? Double.POSITIVE_INFINITY
                : edge.weights().distanceKm();
    }
}

