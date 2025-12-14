package com.neca.perds.routing;

import com.neca.perds.graph.GraphReadView;
import com.neca.perds.model.NodeId;

@FunctionalInterface
public interface Heuristic {
    double estimate(GraphReadView graph, NodeId from, NodeId to);
}

