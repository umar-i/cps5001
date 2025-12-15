package com.neca.perds.dispatch;

import com.neca.perds.graph.Edge;
import com.neca.perds.graph.EdgeStatus;
import com.neca.perds.graph.EdgeWeights;
import com.neca.perds.graph.GraphReadView;
import com.neca.perds.model.Assignment;
import com.neca.perds.model.Incident;
import com.neca.perds.model.IncidentId;
import com.neca.perds.model.IncidentStatus;
import com.neca.perds.model.Node;
import com.neca.perds.model.NodeId;
import com.neca.perds.model.NodeType;
import com.neca.perds.model.ResponseUnit;
import com.neca.perds.model.UnitId;
import com.neca.perds.routing.CostFunctions;
import com.neca.perds.routing.DijkstraRouter;
import com.neca.perds.routing.EdgeCostFunction;
import com.neca.perds.routing.Route;
import com.neca.perds.routing.Router;
import com.neca.perds.system.SystemSnapshot;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class MultiSourceNearestAvailableUnitPolicy implements DispatchPolicy {
    private static final EdgeWeights VIRTUAL_EDGE_WEIGHTS = new EdgeWeights(0.0, Duration.ZERO, 1.0);
    private static final String VIRTUAL_NODE_LABEL = "__VIRTUAL_SOURCE__";

    private final Router router;
    private final EdgeCostFunction costFunction;

    public MultiSourceNearestAvailableUnitPolicy() {
        this(new DijkstraRouter(), CostFunctions.travelTimeSeconds());
    }

    public MultiSourceNearestAvailableUnitPolicy(Router router, EdgeCostFunction costFunction) {
        this.router = Objects.requireNonNull(router, "router");
        this.costFunction = Objects.requireNonNull(costFunction, "costFunction");
    }

    @Override
    public Optional<DispatchDecision> choose(SystemSnapshot snapshot, Incident incident) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(incident, "incident");

        if (!isDispatchable(incident)) {
            return Optional.empty();
        }
        if (hasAssignment(snapshot, incident.id())) {
            return Optional.empty();
        }

        Map<NodeId, List<ResponseUnit>> eligibleUnitsByNodeId = new HashMap<>();
        for (ResponseUnit unit : snapshot.units()) {
            if (!unit.isAvailable()) {
                continue;
            }
            if (!incident.requiredUnitTypes().contains(unit.type())) {
                continue;
            }
            eligibleUnitsByNodeId
                    .computeIfAbsent(unit.currentNodeId(), ignored -> new ArrayList<>())
                    .add(unit);
        }

        if (eligibleUnitsByNodeId.isEmpty()) {
            return Optional.empty();
        }

        NodeId virtualSourceId = virtualSourceId(snapshot.graph(), eligibleUnitsByNodeId.keySet());
        GraphReadView graph = new VirtualSourceGraphReadView(snapshot.graph(), virtualSourceId, eligibleUnitsByNodeId.keySet());

        Optional<Route> virtualRoute = router.findRoute(graph, virtualSourceId, incident.locationNodeId(), costFunction);
        if (virtualRoute.isEmpty()) {
            return Optional.empty();
        }

        Route route = stripVirtualSource(virtualRoute.get(), virtualSourceId);
        NodeId startNodeId = route.nodes().getFirst();

        ResponseUnit chosenUnit = chooseUnitAtStartNode(eligibleUnitsByNodeId.get(startNodeId));
        var assignment = new Assignment(
                incident.id(),
                chosenUnit.id(),
                route,
                snapshot.now()
        );

        Map<String, Double> components = new LinkedHashMap<>();
        components.put("travelTimeSeconds", (double) route.totalTravelTime().toSeconds());
        components.put("distanceKm", route.totalDistanceKm());
        components.put("severityLevel", (double) incident.severity().level());

        DispatchRationale rationale = new DispatchRationale(-route.totalCost(), Map.copyOf(components));
        return Optional.of(new DispatchDecision(assignment, rationale));
    }

    private static ResponseUnit chooseUnitAtStartNode(List<ResponseUnit> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("Expected at least one eligible unit at route start node");
        }
        return candidates.stream()
                .min(Comparator.comparing((ResponseUnit u) -> u.id().value()))
                .orElseThrow();
    }

    private static Route stripVirtualSource(Route route, NodeId virtualSourceId) {
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

    private static NodeId virtualSourceId(GraphReadView graph, Collection<NodeId> sourceNodes) {
        NodeId candidate = new NodeId(VIRTUAL_NODE_LABEL);
        if (graph.getNode(candidate).isEmpty() && !sourceNodes.contains(candidate)) {
            return candidate;
        }
        for (int i = 1; i < 10_000; i++) {
            NodeId withSuffix = new NodeId(VIRTUAL_NODE_LABEL + "_" + i);
            if (graph.getNode(withSuffix).isEmpty() && !sourceNodes.contains(withSuffix)) {
                return withSuffix;
            }
        }
        throw new IllegalStateException("Unable to allocate a virtual source node id without collisions");
    }

    private static boolean isDispatchable(Incident incident) {
        return incident.status() == IncidentStatus.REPORTED || incident.status() == IncidentStatus.QUEUED;
    }

    private static boolean hasAssignment(SystemSnapshot snapshot, IncidentId incidentId) {
        return snapshot.assignments().stream().anyMatch(a -> a.incidentId().equals(incidentId));
    }

    private static final class VirtualSourceGraphReadView implements GraphReadView {
        private final GraphReadView delegate;
        private final NodeId virtualSourceId;
        private final Node virtualNode;
        private final Map<NodeId, Edge> virtualEdgesByTo;
        private final List<NodeId> nodeIds;

        private VirtualSourceGraphReadView(GraphReadView delegate, NodeId virtualSourceId, Set<NodeId> sources) {
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
    }
}

