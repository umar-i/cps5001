package com.neca.perds.graph;

import com.neca.perds.model.Node;
import com.neca.perds.model.NodeId;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class AdjacencyMapGraph implements Graph {
    private final Map<NodeId, Node> nodes = new HashMap<>();
    private final Map<NodeId, Map<NodeId, Edge>> outgoing = new HashMap<>();
    private long version;

    @Override
    public Optional<Node> getNode(NodeId id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(nodes.get(id));
    }

    @Override
    public Collection<NodeId> nodeIds() {
        return Collections.unmodifiableSet(nodes.keySet());
    }

    @Override
    public Collection<Edge> outgoingEdges(NodeId from) {
        Objects.requireNonNull(from, "from");
        var edges = outgoing.get(from);
        if (edges == null) {
            return List.of();
        }
        return Collections.unmodifiableCollection(edges.values());
    }

    @Override
    public Optional<Edge> getEdge(NodeId from, NodeId to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        var edges = outgoing.get(from);
        if (edges == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(edges.get(to));
    }

    @Override
    public long version() {
        return version;
    }

    @Override
    public long addNode(Node node) {
        Objects.requireNonNull(node, "node");
        nodes.put(node.id(), node);
        outgoing.computeIfAbsent(node.id(), ignored -> new HashMap<>());
        return bumpVersion();
    }

    @Override
    public long removeNode(NodeId id) {
        Objects.requireNonNull(id, "id");
        nodes.remove(id);
        outgoing.remove(id);
        for (var entry : outgoing.entrySet()) {
            entry.getValue().remove(id);
        }
        return bumpVersion();
    }

    @Override
    public long putEdge(Edge edge) {
        Objects.requireNonNull(edge, "edge");
        outgoing.computeIfAbsent(edge.from(), ignored -> new HashMap<>()).put(edge.to(), edge);
        return bumpVersion();
    }

    @Override
    public long removeEdge(NodeId from, NodeId to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        var edges = outgoing.get(from);
        if (edges != null) {
            edges.remove(to);
        }
        return bumpVersion();
    }

    @Override
    public long updateEdge(NodeId from, NodeId to, EdgeWeights weights, EdgeStatus status) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(weights, "weights");
        Objects.requireNonNull(status, "status");

        var edges = outgoing.get(from);
        if (edges == null || !edges.containsKey(to)) {
            throw new IllegalStateException("Edge does not exist: " + from + " -> " + to);
        }

        edges.put(to, new Edge(from, to, weights, status));
        return bumpVersion();
    }

    private long bumpVersion() {
        version++;
        return version;
    }
}
