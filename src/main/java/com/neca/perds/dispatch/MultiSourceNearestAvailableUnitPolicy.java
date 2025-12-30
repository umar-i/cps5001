package com.neca.perds.dispatch;

import com.neca.perds.model.Assignment;
import com.neca.perds.model.Incident;
import com.neca.perds.model.IncidentId;
import com.neca.perds.model.IncidentSeverity;
import com.neca.perds.model.IncidentStatus;
import com.neca.perds.model.NodeId;
import com.neca.perds.model.ResponseUnit;
import com.neca.perds.model.UnitId;
import com.neca.perds.model.UnitType;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
        List<DispatchDecision> decisions = chooseAll(snapshot, incident);
        return decisions.isEmpty() ? Optional.empty() : Optional.of(decisions.getFirst());
    }

    @Override
    public List<DispatchDecision> chooseAll(SystemSnapshot snapshot, Incident incident) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(incident, "incident");

        if (!isDispatchable(incident)) {
            return List.of();
        }

        // Determine which unit types still need to be dispatched
        Set<UnitType> alreadyAssignedTypes = getAssignedUnitTypes(snapshot, incident.id());
        Set<UnitType> neededTypes = new HashSet<>(incident.requiredUnitTypes());
        neededTypes.removeAll(alreadyAssignedTypes);

        if (neededTypes.isEmpty()) {
            return List.of();
        }

        // Track units we've already selected in this round to avoid double-selection
        Set<UnitId> selectedUnitIds = new HashSet<>();
        List<DispatchDecision> decisions = new ArrayList<>();

        // For each needed type, find the best available unit using multi-source routing
        for (UnitType neededType : neededTypes) {
            Optional<DispatchDecision> decision = chooseForType(snapshot, incident, neededType, selectedUnitIds);
            if (decision.isPresent()) {
                selectedUnitIds.add(decision.get().assignment().unitId());
                decisions.add(decision.get());
            }
        }

        return List.copyOf(decisions);
    }

    private Optional<DispatchDecision> chooseForType(
            SystemSnapshot snapshot,
            Incident incident,
            UnitType requiredType,
            Set<UnitId> excludedUnitIds
    ) {
        Map<NodeId, List<ResponseUnit>> eligibleUnitsByNodeId = new HashMap<>();
        for (ResponseUnit unit : snapshot.units()) {
            if (!unit.isAvailable()) {
                continue;
            }
            if (unit.type() != requiredType) {
                continue;
            }
            if (excludedUnitIds.contains(unit.id())) {
                continue;
            }
            // Check capacity and specialization requirements
            if (!unit.meetsRequirements(incident.requiredCapacity(), incident.requiredSpecializationLevel())) {
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

        Route route = VirtualSourceGraphView.stripVirtualSource(virtualRoute.get(), virtualSourceId);
        NodeId startNodeId = route.nodes().getFirst();

        ResponseUnit chosenUnit = chooseUnitAtStartNode(eligibleUnitsByNodeId.get(startNodeId), incident);
        return Optional.of(createDecision(incident, chosenUnit, route, snapshot));
    }

    private DispatchDecision createDecision(Incident incident, ResponseUnit unit, Route route, SystemSnapshot snapshot) {
        var assignment = new Assignment(
                incident.id(),
                unit.id(),
                route,
                snapshot.now()
        );

        Map<String, Double> components = new LinkedHashMap<>();
        components.put("travelTimeSeconds", (double) route.totalTravelTime().toSeconds());
        components.put("distanceKm", route.totalDistanceKm());
        components.put("severityLevel", (double) incident.severity().level());

        DispatchRationale rationale = new DispatchRationale(-route.totalCost(), Map.copyOf(components));
        return new DispatchDecision(assignment, rationale);
    }

    /**
     * Chooses the best unit at the start node. For severe incidents, prefers higher specialization.
     */
    private static ResponseUnit chooseUnitAtStartNode(List<ResponseUnit> candidates, Incident incident) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("Expected at least one eligible unit at route start node");
        }
        
        Comparator<ResponseUnit> comparator;
        if (incident.severity().level() >= IncidentSeverity.HIGH.level()) {
            // For severe incidents, prefer higher specialization, then lower ID
            comparator = Comparator
                    .comparing((ResponseUnit u) -> -u.specializationLevel())
                    .thenComparing(u -> u.id().value());
        } else {
            // For non-severe incidents, just use ID for deterministic selection
            comparator = Comparator.comparing(u -> u.id().value());
        }
        
        return candidates.stream()
                .min(comparator)
                .orElseThrow();
    }

    private static boolean isDispatchable(Incident incident) {
        return incident.status() == IncidentStatus.REPORTED || incident.status() == IncidentStatus.QUEUED;
    }

    /**
     * Returns the set of unit types that have already been assigned to the incident.
     */
    private static Set<UnitType> getAssignedUnitTypes(SystemSnapshot snapshot, IncidentId incidentId) {
        Set<UnitType> assignedTypes = new HashSet<>();
        for (var assignment : snapshot.assignments()) {
            if (assignment.incidentId().equals(incidentId)) {
                // Find the unit to get its type
                for (var unit : snapshot.units()) {
                    if (unit.id().equals(assignment.unitId())) {
                        assignedTypes.add(unit.type());
                        break;
                    }
                }
            }
        }
        return assignedTypes;
    }
}

