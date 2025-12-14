package com.neca.perds.dispatch;

import com.neca.perds.model.Assignment;
import com.neca.perds.model.Incident;
import com.neca.perds.model.IncidentId;
import com.neca.perds.model.IncidentStatus;
import com.neca.perds.model.ResponseUnit;
import com.neca.perds.model.UnitId;
import com.neca.perds.routing.CostFunctions;
import com.neca.perds.routing.DijkstraRouter;
import com.neca.perds.routing.EdgeCostFunction;
import com.neca.perds.routing.Route;
import com.neca.perds.routing.Router;
import com.neca.perds.system.SystemSnapshot;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(incident, "incident");

        if (!isDispatchable(incident)) {
            return Optional.empty();
        }
        if (hasAssignment(snapshot, incident.id())) {
            return Optional.empty();
        }

        Candidate best = null;
        for (ResponseUnit unit : snapshot.units()) {
            if (!unit.isAvailable()) {
                continue;
            }
            if (!incident.requiredUnitTypes().contains(unit.type())) {
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
            if (best == null || candidate.isBetterThan(best)) {
                best = candidate;
            }
        }

        if (best == null) {
            return Optional.empty();
        }

        var assignment = new Assignment(
                incident.id(),
                best.unit.id(),
                best.route,
                snapshot.now()
        );

        Map<String, Double> components = new LinkedHashMap<>();
        components.put("travelTimeSeconds", (double) best.route.totalTravelTime().toSeconds());
        components.put("distanceKm", best.route.totalDistanceKm());
        components.put("severityLevel", (double) incident.severity().level());

        DispatchRationale rationale = new DispatchRationale(-best.route.totalCost(), Map.copyOf(components));
        return Optional.of(new DispatchDecision(assignment, rationale));
    }

    private static boolean isDispatchable(Incident incident) {
        return incident.status() == IncidentStatus.REPORTED || incident.status() == IncidentStatus.QUEUED;
    }

    private static boolean hasAssignment(SystemSnapshot snapshot, IncidentId incidentId) {
        return snapshot.assignments().stream().anyMatch(a -> a.incidentId().equals(incidentId));
    }

    private record Candidate(ResponseUnit unit, Route route) {
        private boolean isBetterThan(Candidate other) {
            int costComparison = Double.compare(route.totalCost(), other.route.totalCost());
            if (costComparison != 0) {
                return costComparison < 0;
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
