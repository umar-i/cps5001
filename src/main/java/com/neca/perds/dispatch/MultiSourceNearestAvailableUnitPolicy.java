package com.neca.perds.dispatch;

import com.neca.perds.model.Assignment;
import com.neca.perds.model.Incident;
import com.neca.perds.model.IncidentId;
import com.neca.perds.model.IncidentStatus;
import com.neca.perds.model.NodeId;
import com.neca.perds.model.ResponseUnit;
import com.neca.perds.model.UnitId;
import com.neca.perds.routing.CostFunctions;
import com.neca.perds.routing.DijkstraRouter;
import com.neca.perds.routing.EdgeCostFunction;
import com.neca.perds.routing.Route;
import com.neca.perds.routing.Router;
import com.neca.perds.routing.VirtualSourceGraphView;
import com.neca.perds.system.SystemSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MultiSourceNearestAvailableUnitPolicy implements DispatchPolicy {
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

        NodeId virtualSourceId = VirtualSourceGraphView.allocateVirtualSourceId(snapshot.graph(), eligibleUnitsByNodeId.keySet());
        var graph = new VirtualSourceGraphView(snapshot.graph(), virtualSourceId, eligibleUnitsByNodeId.keySet());

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

    private static boolean isDispatchable(Incident incident) {
        return incident.status() == IncidentStatus.REPORTED || incident.status() == IncidentStatus.QUEUED;
    }

    private static boolean hasAssignment(SystemSnapshot snapshot, IncidentId incidentId) {
        return snapshot.assignments().stream().anyMatch(a -> a.incidentId().equals(incidentId));
    }
}

