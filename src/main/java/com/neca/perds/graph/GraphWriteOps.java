package com.neca.perds.graph;

import com.neca.perds.model.Node;
import com.neca.perds.model.NodeId;

public interface GraphWriteOps {
    long addNode(Node node);

    long removeNode(NodeId id);

    long putEdge(Edge edge);

    long removeEdge(NodeId from, NodeId to);

    long updateEdge(NodeId from, NodeId to, EdgeWeights weights, EdgeStatus status);
}

