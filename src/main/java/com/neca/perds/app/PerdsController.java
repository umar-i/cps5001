package com.neca.perds.app;

import com.neca.perds.dispatch.DispatchCommand;
import com.neca.perds.dispatch.DispatchDecision;
import com.neca.perds.dispatch.DispatchEngine;
import com.neca.perds.graph.Graph;
import com.neca.perds.metrics.MetricsCollector;
import com.neca.perds.model.Assignment;
import com.neca.perds.model.DispatchCentre;
import com.neca.perds.model.DispatchCentreId;
import com.neca.perds.model.Incident;
import com.neca.perds.model.IncidentId;
import com.neca.perds.model.IncidentStatus;
import com.neca.perds.model.NodeId;
import com.neca.perds.model.ResponseUnit;
import com.neca.perds.model.UnitId;
import com.neca.perds.model.UnitStatus;
import com.neca.perds.prediction.DemandPredictor;
import com.neca.perds.prediction.PrepositioningStrategy;
import com.neca.perds.prediction.RepositionPlan;
import com.neca.perds.routing.CostFunctions;
import com.neca.perds.routing.DijkstraRouter;
import com.neca.perds.routing.EdgeCostFunction;
import com.neca.perds.routing.Route;
import com.neca.perds.routing.Router;
import com.neca.perds.sim.SystemCommand;
import com.neca.perds.sim.SystemCommandExecutor;
import com.neca.perds.system.SystemSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class PerdsController implements SystemCommandExecutor {
    private static final Router REROUTE_ROUTER = new DijkstraRouter();
    private static final EdgeCostFunction REROUTE_COST_FUNCTION = CostFunctions.travelTimeSeconds();

    private final Graph graph;
    private final DispatchEngine dispatchEngine;
    private final DemandPredictor demandPredictor;
    private final PrepositioningStrategy prepositioningStrategy;
    private final MetricsCollector metricsCollector;

    private final Map<UnitId, ResponseUnit> units = new HashMap<>();
    private final Map<DispatchCentreId, DispatchCentre> dispatchCentres = new HashMap<>();
    private final Map<IncidentId, Incident> incidents = new HashMap<>();
    private final Map<IncidentId, Assignment> assignments = new HashMap<>();
    private final AssignmentRouteIndex assignmentRouteIndex = new AssignmentRouteIndex();

    public PerdsController(
            Graph graph,
            DispatchEngine dispatchEngine,
            DemandPredictor demandPredictor,
            PrepositioningStrategy prepositioningStrategy,
            MetricsCollector metricsCollector
    ) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.dispatchEngine = Objects.requireNonNull(dispatchEngine, "dispatchEngine");
        this.demandPredictor = Objects.requireNonNull(demandPredictor, "demandPredictor");
        this.prepositioningStrategy = Objects.requireNonNull(prepositioningStrategy, "prepositioningStrategy");
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector");
    }

    public Graph graph() {
        return graph;
    }

    public SystemSnapshot snapshot(Instant now) {
        return new SystemSnapshot(
                graph,
                now,
                List.copyOf(units.values()),
                List.copyOf(dispatchCentres.values()),
                List.copyOf(incidents.values()),
                List.copyOf(assignments.values())
        );
    }

    @Override
    public void execute(SystemCommand command, Instant at) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(at, "at");

        NodeId changedEdgeFrom = null;
        NodeId changedEdgeTo = null;
        boolean changedEdgeMayInvalidateRoutes = false;
        String changedEdgeReason = null;

        switch (command) {
            case SystemCommand.ReportIncidentCommand c -> {
                incidents.put(c.incident().id(), c.incident());
                demandPredictor.observe(c.incident());
            }
            case SystemCommand.ResolveIncidentCommand c -> {
                resolveIncident(c.incidentId(), at);
            }
            case SystemCommand.AddNodeCommand c -> graph.addNode(c.node());
            case SystemCommand.RemoveNodeCommand c -> graph.removeNode(c.nodeId());
            case SystemCommand.PutEdgeCommand c -> graph.putEdge(c.edge());
            case SystemCommand.RemoveEdgeCommand c -> {
                graph.removeEdge(c.from(), c.to());
                changedEdgeFrom = c.from();
                changedEdgeTo = c.to();
                changedEdgeMayInvalidateRoutes = true;
                changedEdgeReason = "Edge removed (" + c.from() + " -> " + c.to() + ")";
            }
            case SystemCommand.UpdateEdgeCommand c -> {
                graph.updateEdge(c.from(), c.to(), c.weights(), c.status());
                changedEdgeFrom = c.from();
                changedEdgeTo = c.to();
                changedEdgeMayInvalidateRoutes = true;
                changedEdgeReason = "Edge updated (" + c.from() + " -> " + c.to() + ") status=" + c.status();
            }
            case SystemCommand.RegisterUnitCommand c -> units.put(c.unit().id(), c.unit());
            case SystemCommand.SetUnitStatusCommand c -> setUnitStatus(c.unitId(), c.status());
            case SystemCommand.MoveUnitCommand c -> moveUnit(c.unitId(), c.newNodeId());
            case SystemCommand.PrepositionUnitsCommand c -> prepositionUnits(c.horizon(), at);
        }

        if (changedEdgeMayInvalidateRoutes) {
            rerouteOrCancelAssignmentsUsingEdge(changedEdgeFrom, changedEdgeTo, changedEdgeReason, at);
        }

        SystemSnapshot snapshot = snapshot(at);
        long startedNanos = System.nanoTime();
        List<DispatchCommand> computed = dispatchEngine.compute(snapshot);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedNanos);
        metricsCollector.recordDispatchComputation(at, elapsed, snapshot.incidents().size(), snapshot.units().size());

        for (DispatchCommand dispatchCommand : computed) {
            applyDispatchCommand(dispatchCommand, at);
            metricsCollector.recordDispatchCommandApplied(at, dispatchCommand);
        }
    }

    private void applyDispatchCommand(DispatchCommand command, Instant at) {
        switch (command) {
            case DispatchCommand.AssignUnitCommand c -> applyAssignment(c, at);
            case DispatchCommand.RerouteUnitCommand c -> applyReroute(c);
            case DispatchCommand.CancelAssignmentCommand c -> cancelAssignment(c.incidentId());
        }
    }

    private void applyAssignment(DispatchCommand.AssignUnitCommand command, Instant at) {
        if (assignments.containsKey(command.incidentId())) {
            return;
        }

        Incident incident = incidents.get(command.incidentId());
        if (incident == null) {
            throw new IllegalStateException("Unknown incident: " + command.incidentId());
        }
        if (incident.status() != IncidentStatus.REPORTED && incident.status() != IncidentStatus.QUEUED) {
            return;
        }
        ResponseUnit unit = requireUnit(command.unitId());
        if (!unit.isAvailable()) {
            return;
        }

        Assignment assignment = new Assignment(command.incidentId(), command.unitId(), command.route(), at);
        assignments.put(
                command.incidentId(),
                assignment
        );
        assignmentRouteIndex.put(assignment.incidentId(), assignment.route());

        units.put(
                unit.id(),
                new ResponseUnit(
                        unit.id(),
                        unit.type(),
                        UnitStatus.EN_ROUTE,
                        unit.currentNodeId(),
                        Optional.of(command.incidentId()),
                        unit.homeDispatchCentreId()
                )
        );

        incidents.put(
                incident.id(),
                new Incident(
                        incident.id(),
                        incident.locationNodeId(),
                        incident.severity(),
                        incident.requiredUnitTypes(),
                        IncidentStatus.DISPATCHED,
                        incident.reportedAt(),
                        incident.resolvedAt()
                )
        );

        metricsCollector.recordDispatchDecision(at, new DispatchDecision(assignment, command.rationale()));
    }

    private void applyReroute(DispatchCommand.RerouteUnitCommand command) {
        ResponseUnit unit = requireUnit(command.unitId());
        Optional<IncidentId> incidentId = unit.assignedIncidentId();
        if (incidentId.isEmpty()) {
            return;
        }
        Assignment assignment = assignments.get(incidentId.get());
        if (assignment == null) {
            return;
        }
        if (!assignment.unitId().equals(command.unitId())) {
            return;
        }
        if (!command.newRoute().nodes().getFirst().equals(unit.currentNodeId())) {
            return;
        }

        assignments.put(
                assignment.incidentId(),
                new Assignment(assignment.incidentId(), assignment.unitId(), command.newRoute(), assignment.assignedAt())
        );
        assignmentRouteIndex.put(assignment.incidentId(), command.newRoute());
    }

    private void resolveIncident(IncidentId incidentId, Instant at) {
        Incident incident = incidents.get(incidentId);
        if (incident == null) {
            throw new IllegalStateException("Unknown incident: " + incidentId);
        }

        incidents.put(
                incident.id(),
                new Incident(
                        incident.id(),
                        incident.locationNodeId(),
                        incident.severity(),
                        incident.requiredUnitTypes(),
                        IncidentStatus.RESOLVED,
                        incident.reportedAt(),
                        Optional.of(at)
                )
        );
        assignmentRouteIndex.remove(incidentId);

        Assignment assignment = assignments.get(incidentId);
        if (assignment == null) {
            return;
        }

        ResponseUnit unit = requireUnit(assignment.unitId());
        units.put(
                unit.id(),
                new ResponseUnit(
                        unit.id(),
                        unit.type(),
                        UnitStatus.AVAILABLE,
                        unit.currentNodeId(),
                        Optional.empty(),
                        unit.homeDispatchCentreId()
                )
        );
    }

    private void setUnitStatus(UnitId unitId, UnitStatus status) {
        ResponseUnit unit = requireUnit(unitId);

        Optional<IncidentId> assignedIncidentId = unit.assignedIncidentId();
        if (assignedIncidentId.isPresent() && !isAssignmentCompatibleStatus(status)) {
            cancelAssignment(assignedIncidentId.get());
            unit = requireUnit(unitId);
        }

        units.put(
                unitId,
                new ResponseUnit(
                        unit.id(),
                        unit.type(),
                        status,
                        unit.currentNodeId(),
                        unit.assignedIncidentId(),
                        unit.homeDispatchCentreId()
                )
        );
    }

    private void moveUnit(UnitId unitId, NodeId newNodeId) {
        ResponseUnit unit = requireUnit(unitId);
        units.put(
                unitId,
                new ResponseUnit(
                        unit.id(),
                        unit.type(),
                        unit.status(),
                        newNodeId,
                        unit.assignedIncidentId(),
                        unit.homeDispatchCentreId()
                )
        );
    }

    private ResponseUnit requireUnit(UnitId unitId) {
        return Optional.ofNullable(units.get(unitId))
                .orElseThrow(() -> new IllegalStateException("Unknown unit: " + unitId));
    }

    private static boolean isAssignmentCompatibleStatus(UnitStatus status) {
        return status == UnitStatus.EN_ROUTE || status == UnitStatus.ON_SCENE;
    }

    private void rerouteOrCancelAssignmentsUsingEdge(NodeId from, NodeId to, String reason, Instant at) {
        if (from == null || to == null) {
            return;
        }

        var affectedIncidentIds = assignmentRouteIndex.incidentIdsUsingEdge(from, to).stream()
                .sorted(Comparator.comparing(IncidentId::value))
                .toList();

        for (IncidentId incidentId : affectedIncidentIds) {
            rerouteOrCancelAssignment(incidentId, reason, at);
        }
    }

    private void rerouteOrCancelAssignment(IncidentId incidentId, String reason, Instant at) {
        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(at, "at");

        Assignment assignment = assignments.get(incidentId);
        if (assignment == null) {
            return;
        }

        Incident incident = incidents.get(incidentId);
        if (incident == null || incident.status() == IncidentStatus.RESOLVED) {
            return;
        }

        ResponseUnit unit = units.get(assignment.unitId());
        if (unit == null) {
            cancelAssignmentWithReason(incidentId, reason, at);
            return;
        }
        if (unit.assignedIncidentId().isEmpty() || !unit.assignedIncidentId().get().equals(incidentId)) {
            return;
        }

        Optional<Route> newRoute = REROUTE_ROUTER.findRoute(
                graph,
                unit.currentNodeId(),
                incident.locationNodeId(),
                REROUTE_COST_FUNCTION
        );

        if (newRoute.isPresent()) {
            DispatchCommand reroute = new DispatchCommand.RerouteUnitCommand(
                    unit.id(),
                    newRoute.get(),
                    reason == null ? "Route recalculated due to edge update" : reason
            );
            applyDispatchCommand(reroute, at);
            metricsCollector.recordDispatchCommandApplied(at, reroute);
            return;
        }

        cancelAssignmentWithReason(incidentId, reason, at);
    }

    private void cancelAssignmentWithReason(IncidentId incidentId, String reason, Instant at) {
        String message = reason == null ? "Route became unreachable after edge update" : reason;
        DispatchCommand cancel = new DispatchCommand.CancelAssignmentCommand(incidentId, message);
        applyDispatchCommand(cancel, at);
        metricsCollector.recordDispatchCommandApplied(at, cancel);
    }

    private void cancelAssignment(IncidentId incidentId) {
        assignmentRouteIndex.remove(incidentId);
        assignments.remove(incidentId);
        Incident incident = incidents.get(incidentId);
        if (incident != null && incident.status() != IncidentStatus.RESOLVED) {
            incidents.put(
                    incident.id(),
                    new Incident(
                            incident.id(),
                            incident.locationNodeId(),
                            incident.severity(),
                            incident.requiredUnitTypes(),
                            IncidentStatus.QUEUED,
                            incident.reportedAt(),
                            incident.resolvedAt()
                    )
            );
        }

        var unitIdsToClear = units.values().stream()
                .filter(u -> u.assignedIncidentId().isPresent() && u.assignedIncidentId().get().equals(incidentId))
                .map(ResponseUnit::id)
                .toList();
        for (UnitId unitId : unitIdsToClear) {
            ResponseUnit unit = units.get(unitId);
            if (unit == null) {
                continue;
            }
            if (unit.assignedIncidentId().isEmpty() || !unit.assignedIncidentId().get().equals(incidentId)) {
                continue;
            }

            UnitStatus newStatus = unit.status();
            if (unit.status() == UnitStatus.EN_ROUTE || unit.status() == UnitStatus.ON_SCENE) {
                newStatus = UnitStatus.AVAILABLE;
            }

            units.put(
                    unit.id(),
                    new ResponseUnit(
                            unit.id(),
                            unit.type(),
                            newStatus,
                            unit.currentNodeId(),
                            Optional.empty(),
                            unit.homeDispatchCentreId()
                    )
            );
        }
    }

    private void prepositionUnits(Duration horizon, Instant at) {
        var forecast = demandPredictor.forecast(at, horizon);
        RepositionPlan plan = prepositioningStrategy.plan(snapshot(at), forecast);
        for (var move : plan.moves()) {
            ResponseUnit unit = units.get(move.unitId());
            if (unit == null || !unit.isAvailable()) {
                continue;
            }
            if (unit.currentNodeId().equals(move.targetNodeId())) {
                continue;
            }
            if (graph.getNode(move.targetNodeId()).isEmpty()) {
                continue;
            }

            units.put(
                    unit.id(),
                    new ResponseUnit(
                            unit.id(),
                            unit.type(),
                            unit.status(),
                            move.targetNodeId(),
                            Optional.empty(),
                            unit.homeDispatchCentreId()
                    )
            );
        }
    }
}
