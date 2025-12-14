package com.neca.perds.graph;

import com.neca.perds.model.NodeId;

import java.util.Objects;

public record Edge(NodeId from, NodeId to, EdgeWeights weights, EdgeStatus status) {
    public Edge {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(weights, "weights");
        Objects.requireNonNull(status, "status");
    }
}

