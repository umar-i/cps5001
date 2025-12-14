package com.neca.perds.routing;

import com.neca.perds.graph.GraphReadView;
import com.neca.perds.model.NodeId;

import java.util.Optional;

public interface Router {
    Optional<Route> findRoute(GraphReadView graph, NodeId start, NodeId goal, EdgeCostFunction costFunction);
}

