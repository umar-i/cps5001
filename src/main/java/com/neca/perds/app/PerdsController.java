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

    private final IncidentManager incidentManager = new IncidentManager();
    private final UnitManager unitManager = new UnitManager();
    private final Map<DispatchCentreId, DispatchCentre> dispatchCentres = new HashMap<>();
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
                List.copyOf(unitManager.all()),
                List.copyOf(dispatchCentres.values()),
                List.copyOf(incidentManager.all()),
                List.copyOf(assignments.values())
        );
    }

    @Override
    public void execute(SystemCommand command, Instant at) {
        // First, complete any repositionings that have finished by this time
        unitManager.completeRepositionings(at);
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(at, "at");

        NodeId changedEdgeFrom = null;
        NodeId changedEdgeTo = null;
        boolean changedEdgeMayInvalidateRoutes = false;
        String changedEdgeReason = null;

        switch (command) {
            case SystemCommand.ReportIncidentCommand c -> {
                incidentManager.add(c.incident());
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
            case SystemCommand.RegisterUnitCommand c -> unitManager.register(c.unit());
            case SystemCommand.SetUnitStatusCommand c -> setUnitStatus(c.unitId(), c.status());
            case SystemCommand.MoveUnitCommand c -> unitManager.move(c.unitId(), c.newNodeId());
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

        if (!incidentManager.canDispatch(command.incidentId())) {
            Incident incident = incidentManager.get(command.incidentId())
                    .orElseThrow(() -> new IllegalStateException("Unknown incident: " + command.incidentId()));
            if (incident.status() != IncidentStatus.REPORTED && incident.status() != IncidentStatus.QUEUED) {
                return;
            }
        }
        ResponseUnit unit = unitManager.require(command.unitId());
        if (!unit.isAvailable()) {
            return;
        }

        Assignment assignment = new Assignment(command.incidentId(), command.unitId(), command.route(), at);
        assignments.put(command.incidentId(), assignment);
        assignmentRouteIndex.put(assignment.incidentId(), assignment.route());

        unitManager.assignToIncident(command.unitId(), command.incidentId());
        incidentManager.markDispatched(command.incidentId());

        metricsCollector.recordDispatchDecision(at, new DispatchDecision(assignment, command.rationale()));
    }

    private void applyReroute(DispatchCommand.RerouteUnitCommand command) {
        ResponseUnit unit = unitManager.require(command.unitId());
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
        incidentManager.resolve(incidentId, at);
        assignmentRouteIndex.remove(incidentId);

        Assignment assignment = assignments.remove(incidentId);
        if (assignment == null) {
            return;
        }

        unitManager.clearAssignment(assignment.unitId());
    }

    private void setUnitStatus(UnitId unitId, UnitStatus status) {
        ResponseUnit unit = unitManager.require(unitId);

        Optional<IncidentId> assignedIncidentId = unit.assignedIncidentId();
        if (assignedIncidentId.isPresent() && !UnitManager.isAssignmentCompatibleStatus(status)) {
            cancelAssignment(assignedIncidentId.get());
            unit = unitManager.require(unitId);
        }

        // Cancel any pending repositioning if status changes
        if (unit.status() == UnitStatus.REPOSITIONING && status != UnitStatus.REPOSITIONING) {
            unitManager.cancelRepositioning(unitId);
        }

        unitManager.setStatus(unitId, status);
    }

    private void rerouteOrCancelAssignmentsUsingEdge(NodeId from, NodeId to, String reason, Instant at) {
        if (from == null || to == null) {
            return;
        }

        var affectedIncidentIds = assignmentRouteIndex.incidentIdsUsingEdge(from, to).stream()
                .sorted(java.util.Comparator.comparing(IncidentId::value))
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

        if (incidentManager.isResolved(incidentId)) {
            return;
        }
        Incident incident = incidentManager.get(incidentId).orElse(null);
        if (incident == null) {
            return;
        }

        ResponseUnit unit = unitManager.get(assignment.unitId()).orElse(null);
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
        incidentManager.markQueued(incidentId);
        unitManager.clearAssignmentForIncident(incidentId);
    }

    private void prepositionUnits(Duration horizon, Instant at) {
        var forecast = demandPredictor.forecast(at, horizon);
        RepositionPlan plan = prepositioningStrategy.plan(snapshot(at), forecast);
        for (var move : plan.moves()) {
            ResponseUnit unit = unitManager.get(move.unitId()).orElse(null);
            if (unit == null || !unit.isAvailable()) {
                continue;
            }
            if (unit.currentNodeId().equals(move.targetNodeId())) {
                continue;
            }
            if (graph.getNode(move.targetNodeId()).isEmpty()) {
                continue;
            }

            // If route is available, use travel time; otherwise compute route
            Route route;
            if (move.route().isPresent()) {
                route = move.route().get();
            } else {
                Optional<Route> computed = REROUTE_ROUTER.findRoute(
                        graph, unit.currentNodeId(), move.targetNodeId(), REROUTE_COST_FUNCTION);
                if (computed.isEmpty()) {
                    continue; // No route available, skip this move
                }
                route = computed.get();
            }

            // Calculate arrival time based on travel time
            Instant arrivalAt = at.plus(route.totalTravelTime());

            // Start repositioning via UnitManager
            unitManager.startRepositioning(unit.id(), move.targetNodeId(), arrivalAt, move.reason());
        }
    }
}
