package com.neca.perds.dispatch;

import com.neca.perds.model.Assignment;
import com.neca.perds.model.Incident;
import com.neca.perds.model.IncidentId;
import com.neca.perds.model.IncidentSeverity;
import com.neca.perds.model.IncidentStatus;
import com.neca.perds.model.ResponseUnit;
import com.neca.perds.model.UnitId;
import com.neca.perds.model.UnitType;
import com.neca.perds.routing.CostFunctions;
import com.neca.perds.routing.DijkstraRouter;
import com.neca.perds.routing.EdgeCostFunction;
import com.neca.perds.routing.Route;
import com.neca.perds.routing.Router;
import com.neca.perds.system.SystemSnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class NearestAvailableUnitPolicy implements DispatchPolicy {
    private final Router router;
    private final EdgeCostFunction costFunction;

    public NearestAvailableUnitPolicy() {
        this(new DijkstraRouter(), CostFunctions.travelTimeSeconds());
    }

    public NearestAvailableUnitPolicy(Router router, EdgeCostFunction costFunction) {
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

        // For each needed type, find the best available unit
        for (UnitType neededType : neededTypes) {
            Candidate best = null;
            for (ResponseUnit unit : snapshot.units()) {
                if (!unit.isAvailable()) {
                    continue;
                }
                if (unit.type() != neededType) {
                    continue;
                }
                if (selectedUnitIds.contains(unit.id())) {
                    continue;
                }
                // Check capacity and specialization requirements
                if (!unit.meetsRequirements(incident.requiredCapacity(), incident.requiredSpecializationLevel())) {
                    continue;
                }

                Optional<Route> route = router.findRoute(
                        snapshot.graph(),
                        unit.currentNodeId(),
                        incident.locationNodeId(),
                        costFunction
                );
                if (route.isEmpty()) {
                    continue;
                }

                Candidate candidate = new Candidate(unit, route.get());
                if (best == null || candidate.isBetterThan(best, incident)) {
                    best = candidate;
                }
            }

            if (best != null) {
                selectedUnitIds.add(best.unit.id());
                decisions.add(createDecision(incident, best, snapshot));
            }
        }

        return List.copyOf(decisions);
    }

    private DispatchDecision createDecision(Incident incident, Candidate candidate, SystemSnapshot snapshot) {
        var assignment = new Assignment(
                incident.id(),
                candidate.unit.id(),
                candidate.route,
                snapshot.now()
        );

        Map<String, Double> components = new LinkedHashMap<>();
        components.put("travelTimeSeconds", (double) candidate.route.totalTravelTime().toSeconds());
        components.put("distanceKm", candidate.route.totalDistanceKm());
        components.put("severityLevel", (double) incident.severity().level());

        DispatchRationale rationale = new DispatchRationale(-candidate.route.totalCost(), Map.copyOf(components));
        return new DispatchDecision(assignment, rationale);
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

    private record Candidate(ResponseUnit unit, Route route) {
        /**
         * Compares candidates considering travel cost, specialization preference for severe incidents,
         * distance, and unit ID as tiebreaker.
         * 
         * For HIGH/CRITICAL incidents, prefers higher specialization when costs are similar.
         */
        private boolean isBetterThan(Candidate other, Incident incident) {
            int costComparison = Double.compare(route.totalCost(), other.route.totalCost());
            if (costComparison != 0) {
                return costComparison < 0;
            }
            
            // For severe incidents, prefer higher specialization when costs are equal
            if (incident.severity().level() >= IncidentSeverity.HIGH.level()) {
                int specComparison = Integer.compare(unit.specializationLevel(), other.unit.specializationLevel());
                if (specComparison != 0) {
                    return specComparison > 0; // Higher specialization is better
                }
            }
            
            int distanceComparison = Double.compare(route.totalDistanceKm(), other.route.totalDistanceKm());
            if (distanceComparison != 0) {
                return distanceComparison < 0;
            }
            UnitId thisId = unit.id();
            UnitId otherId = other.unit.id();
            return thisId.value().compareTo(otherId.value()) < 0;
        }
    }
}
