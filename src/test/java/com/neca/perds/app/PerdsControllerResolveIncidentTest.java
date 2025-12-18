package com.neca.perds.app;

import com.neca.perds.dispatch.DefaultDispatchEngine;
import com.neca.perds.dispatch.NearestAvailableUnitPolicy;
import com.neca.perds.dispatch.SeverityThenOldestPrioritizer;
import com.neca.perds.graph.AdjacencyMapGraph;
import com.neca.perds.graph.Edge;
import com.neca.perds.graph.EdgeStatus;
import com.neca.perds.graph.EdgeWeights;
import com.neca.perds.metrics.InMemoryMetricsCollector;
import com.neca.perds.model.Incident;
import com.neca.perds.model.IncidentId;
import com.neca.perds.model.IncidentSeverity;
import com.neca.perds.model.IncidentStatus;
import com.neca.perds.model.Node;
import com.neca.perds.model.NodeId;
import com.neca.perds.model.NodeType;
import com.neca.perds.model.ResponseUnit;
import com.neca.perds.model.UnitId;
import com.neca.perds.model.UnitStatus;
import com.neca.perds.model.UnitType;
import com.neca.perds.prediction.NoOpDemandPredictor;
import com.neca.perds.prediction.NoOpPrepositioningStrategy;
import com.neca.perds.sim.SystemCommand;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PerdsControllerResolveIncidentTest {
    @Test
    void resolvingIncidentClearsAssignmentAndFreesUnit() {
        Instant t0 = Instant.parse("2025-01-01T00:00:00Z");

        NodeId a = new NodeId("A");
        NodeId b = new NodeId("B");

        var graph = new AdjacencyMapGraph();
        var dispatchEngine = new DefaultDispatchEngine(
                new SeverityThenOldestPrioritizer(),
                new NearestAvailableUnitPolicy()
        );

        var controller = new PerdsController(
                graph,
                dispatchEngine,
                new NoOpDemandPredictor(),
                new NoOpPrepositioningStrategy(),
                new InMemoryMetricsCollector()
        );

        controller.execute(new SystemCommand.AddNodeCommand(new Node(a, NodeType.CITY, Optional.empty(), "A")), t0);
        controller.execute(new SystemCommand.AddNodeCommand(new Node(b, NodeType.CITY, Optional.empty(), "B")), t0);
        controller.execute(new SystemCommand.PutEdgeCommand(new Edge(
                a,
                b,
                new EdgeWeights(1.0, Duration.ofSeconds(60), 1.0),
                EdgeStatus.OPEN
        )), t0);

        UnitId unitId = new UnitId("U1");
        controller.execute(new SystemCommand.RegisterUnitCommand(new ResponseUnit(
                unitId,
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                a,
                Optional.empty(),
                Optional.empty()
        )), t0);

        IncidentId incidentId = new IncidentId("I1");
        controller.execute(new SystemCommand.ReportIncidentCommand(new Incident(
                incidentId,
                b,
                IncidentSeverity.HIGH,
                Set.of(UnitType.AMBULANCE),
                IncidentStatus.REPORTED,
                t0,
                Optional.empty()
        )), t0);

        assertEquals(1, controller.snapshot(t0).assignments().size());

        Instant t1 = t0.plusSeconds(60);
        controller.execute(new SystemCommand.ResolveIncidentCommand(incidentId), t1);

        var snapshot = controller.snapshot(t1);
        assertEquals(0, snapshot.assignments().size());

        var incident = snapshot.incidents().stream()
                .filter(i -> i.id().equals(incidentId))
                .findFirst()
                .orElseThrow();
        assertEquals(IncidentStatus.RESOLVED, incident.status());
        assertEquals(Optional.of(t1), incident.resolvedAt());

        var unit = snapshot.units().stream()
                .filter(u -> u.id().equals(unitId))
                .findFirst()
                .orElseThrow();
        assertEquals(UnitStatus.AVAILABLE, unit.status());
        assertEquals(Optional.empty(), unit.assignedIncidentId());
    }
}
