package com.neca.perds.routing;

import com.neca.perds.graph.Edge;
import com.neca.perds.graph.EdgeStatus;
import com.neca.perds.graph.EdgeWeights;
import com.neca.perds.graph.GraphReadView;
import com.neca.perds.model.Node;
import com.neca.perds.model.NodeId;
import com.neca.perds.model.NodeType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class VirtualSourceGraphView implements GraphReadView {
    private static final EdgeWeights VIRTUAL_EDGE_WEIGHTS = new EdgeWeights(0.0, Duration.ZERO, 1.0);
    private static final String VIRTUAL_NODE_LABEL = "__VIRTUAL_SOURCE__";
    /** Maximum attempts to find a unique virtual source node ID before giving up. */
    private static final int MAX_VIRTUAL_ID_ALLOCATION_ATTEMPTS = 10_000;

    private final GraphReadView delegate;
    private final NodeId virtualSourceId;
    private final Node virtualNode;
    private final Map<NodeId, Edge> virtualEdgesByTo;
    private final List<NodeId> nodeIds;

    public VirtualSourceGraphView(GraphReadView delegate, NodeId virtualSourceId, Collection<NodeId> sources) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.virtualSourceId = Objects.requireNonNull(virtualSourceId, "virtualSourceId");
        Objects.requireNonNull(sources, "sources");

        this.virtualNode = new Node(virtualSourceId, NodeType.CITY, Optional.empty(), VIRTUAL_NODE_LABEL);

        List<NodeId> sortedSources = new ArrayList<>(sources);
        sortedSources.sort(Comparator.comparing(NodeId::value));
        Map<NodeId, Edge> edges = new HashMap<>(sortedSources.size() * 2);
        for (NodeId to : sortedSources) {
            edges.put(to, new Edge(virtualSourceId, to, VIRTUAL_EDGE_WEIGHTS, EdgeStatus.OPEN));
        }
        this.virtualEdgesByTo = Map.copyOf(edges);

        List<NodeId> ids = new ArrayList<>(delegate.nodeIds());
        ids.add(virtualSourceId);
        this.nodeIds = List.copyOf(ids);
    }

    public static NodeId allocateVirtualSourceId(GraphReadView graph, Collection<NodeId> sourceNodes) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(sourceNodes, "sourceNodes");

        NodeId candidate = new NodeId(VIRTUAL_NODE_LABEL);
        if (graph.getNode(candidate).isEmpty() && !sourceNodes.contains(candidate)) {
            return candidate;
        }
        for (int i = 1; i < MAX_VIRTUAL_ID_ALLOCATION_ATTEMPTS; i++) {
            NodeId withSuffix = new NodeId(VIRTUAL_NODE_LABEL + "_" + i);
            if (graph.getNode(withSuffix).isEmpty() && !sourceNodes.contains(withSuffix)) {
                return withSuffix;
            }
        }
        throw new IllegalStateException("Unable to allocate a virtual source node id without collisions");
    }

    @Override
    public Optional<Node> getNode(NodeId id) {
        Objects.requireNonNull(id, "id");
        if (id.equals(virtualSourceId)) {
            return Optional.of(virtualNode);
        }
        return delegate.getNode(id);
    }

    @Override
    public Collection<NodeId> nodeIds() {
        return nodeIds;
    }

    @Override
    public Collection<Edge> outgoingEdges(NodeId from) {
        Objects.requireNonNull(from, "from");
        if (from.equals(virtualSourceId)) {
            return virtualEdgesByTo.values();
        }
        return delegate.outgoingEdges(from);
    }

    @Override
    public Optional<Edge> getEdge(NodeId from, NodeId to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.equals(virtualSourceId)) {
            return Optional.ofNullable(virtualEdgesByTo.get(to));
        }
        return delegate.getEdge(from, to);
    }

    @Override
    public long version() {
        return delegate.version();
    }

    /**
     * Strips the virtual source node from the beginning of a route.
     * If the route does not start with the virtual source, returns it unchanged.
     *
     * @param route the route potentially starting with a virtual source node
     * @param virtualSourceId the virtual source node ID to strip
     * @return a new route without the virtual source prefix, or the original route if unchanged
     * @throws IllegalArgumentException if route.nodes is empty
     * @throws IllegalStateException if route contains only the virtual source node
     */
    public static Route stripVirtualSource(Route route, NodeId virtualSourceId) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(virtualSourceId, "virtualSourceId");

        if (route.nodes().isEmpty()) {
            throw new IllegalArgumentException("route.nodes must not be empty");
        }
        if (!route.nodes().getFirst().equals(virtualSourceId)) {
            return route;
        }
        List<NodeId> nodes = route.nodes().subList(1, route.nodes().size());
        if (nodes.isEmpty()) {
            throw new IllegalStateException("Route contains only the virtual source node");
        }
        return new Route(
                List.copyOf(nodes),
                route.totalCost(),
                route.totalDistanceKm(),
                route.totalTravelTime(),
                route.graphVersionUsed()
        );
    }
}

