package com.neca.perds.routing;

import com.neca.perds.graph.Edge;

@FunctionalInterface
public interface EdgeCostFunction {
    double cost(Edge edge);
}

