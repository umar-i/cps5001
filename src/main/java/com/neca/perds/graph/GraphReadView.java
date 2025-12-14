package com.neca.perds.graph;

import com.neca.perds.model.Node;
import com.neca.perds.model.NodeId;

import java.util.Collection;
import java.util.Optional;

public interface GraphReadView {
    Optional<Node> getNode(NodeId id);

    Collection<NodeId> nodeIds();

    Collection<Edge> outgoingEdges(NodeId from);

    Optional<Edge> getEdge(NodeId from, NodeId to);

    long version();
}

