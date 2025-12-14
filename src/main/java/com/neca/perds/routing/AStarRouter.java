package com.neca.perds.routing;

import com.neca.perds.graph.GraphReadView;
import com.neca.perds.model.NodeId;

import java.util.Objects;
import java.util.Optional;

public final class AStarRouter implements Router {
    private final Heuristic heuristic;

    public AStarRouter(Heuristic heuristic) {
        this.heuristic = Objects.requireNonNull(heuristic, "heuristic");
    }

    @Override
    public Optional<Route> findRoute(GraphReadView graph, NodeId start, NodeId goal, EdgeCostFunction costFunction) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}

