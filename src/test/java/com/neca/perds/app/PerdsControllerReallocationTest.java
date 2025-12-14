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
import static org.junit.jupiter.api.Assertions.assertFalse;

final class PerdsControllerReallocationTest {
    @Test
    void cancelsAndReassignsWhenAssignedUnitBecomesUnavailable() {
        Instant t0 = Instant.parse("2025-01-01T00:00:00Z");

        NodeId a = new NodeId("A");
        NodeId b = new NodeId("B");
        NodeId c = new NodeId("C");

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
        controller.execute(new SystemCommand.AddNodeCommand(new Node(c, NodeType.CITY, Optional.empty(), "C")), t0);

        EdgeWeights fast = new EdgeWeights(5.0, Duration.ofSeconds(300), 1.0);
        EdgeWeights slow = new EdgeWeights(20.0, Duration.ofSeconds(1200), 1.0);
        controller.execute(new SystemCommand.PutEdgeCommand(new Edge(a, b, fast, EdgeStatus.OPEN)), t0);
        controller.execute(new SystemCommand.PutEdgeCommand(new Edge(b, c, fast, EdgeStatus.OPEN)), t0);
        controller.execute(new SystemCommand.PutEdgeCommand(new Edge(a, c, slow, EdgeStatus.OPEN)), t0);

        UnitId u1 = new UnitId("U1");
        UnitId u2 = new UnitId("U2");

        controller.execute(new SystemCommand.RegisterUnitCommand(new ResponseUnit(
                u1,
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                a,
                Optional.empty(),
                Optional.empty()
        )), t0);
        controller.execute(new SystemCommand.RegisterUnitCommand(new ResponseUnit(
                u2,
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                b,
                Optional.empty(),
                Optional.empty()
        )), t0);

        IncidentId incidentId = new IncidentId("I1");
        controller.execute(new SystemCommand.ReportIncidentCommand(new Incident(
                incidentId,
                c,
                IncidentSeverity.HIGH,
                Set.of(UnitType.AMBULANCE),
                IncidentStatus.REPORTED,
                t0,
                Optional.empty()
        )), t0);

        var initial = controller.snapshot(t0).assignments().stream()
                .filter(a1 -> a1.incidentId().equals(incidentId))
                .findFirst()
                .orElseThrow();
        assertEquals(u2, initial.unitId());

        Instant t1 = t0.plusSeconds(60);
        controller.execute(new SystemCommand.SetUnitStatusCommand(u2, UnitStatus.UNAVAILABLE), t1);

        var reassigned = controller.snapshot(t1).assignments().stream()
                .filter(a1 -> a1.incidentId().equals(incidentId))
                .findFirst()
                .orElseThrow();
        assertEquals(u1, reassigned.unitId());

        var unavailableUnit = controller.snapshot(t1).units().stream()
                .filter(u -> u.id().equals(u2))
                .findFirst()
                .orElseThrow();
        assertEquals(UnitStatus.UNAVAILABLE, unavailableUnit.status());
        assertFalse(unavailableUnit.assignedIncidentId().isPresent());
    }
}

