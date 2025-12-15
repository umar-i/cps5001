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

final class PerdsControllerRerouteOnCongestionTest {
    @Test
    void reroutesActiveAssignmentWhenEdgeTravelTimeChanges() {
        Instant t0 = Instant.parse("2025-01-01T00:00:00Z");

        NodeId b = new NodeId("B");
        NodeId c = new NodeId("C");
        NodeId d = new NodeId("D");

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

        controller.execute(new SystemCommand.AddNodeCommand(new Node(b, NodeType.CITY, Optional.empty(), "B")), t0);
        controller.execute(new SystemCommand.AddNodeCommand(new Node(c, NodeType.CITY, Optional.empty(), "C")), t0);
        controller.execute(new SystemCommand.AddNodeCommand(new Node(d, NodeType.CITY, Optional.empty(), "D")), t0);

        EdgeWeights directFast = new EdgeWeights(5.0, Duration.ofSeconds(100), 1.0);
        EdgeWeights directCongested = new EdgeWeights(5.0, Duration.ofSeconds(300), 1.0);
        EdgeWeights viaD = new EdgeWeights(5.0, Duration.ofSeconds(80), 1.0);

        controller.execute(new SystemCommand.PutEdgeCommand(new Edge(b, c, directFast, EdgeStatus.OPEN)), t0);
        controller.execute(new SystemCommand.PutEdgeCommand(new Edge(b, d, viaD, EdgeStatus.OPEN)), t0);
        controller.execute(new SystemCommand.PutEdgeCommand(new Edge(d, c, viaD, EdgeStatus.OPEN)), t0);

        UnitId u1 = new UnitId("U1");
        controller.execute(new SystemCommand.RegisterUnitCommand(new ResponseUnit(
                u1,
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
        assertEquals(u1, initial.unitId());
        assertEquals(java.util.List.of(b, c), initial.route().nodes());

        Instant t1 = t0.plusSeconds(60);
        controller.execute(new SystemCommand.UpdateEdgeCommand(b, c, directCongested, EdgeStatus.OPEN), t1);

        var updated = controller.snapshot(t1).assignments().stream()
                .filter(a1 -> a1.incidentId().equals(incidentId))
                .findFirst()
                .orElseThrow();
        assertEquals(u1, updated.unitId());
        assertEquals(java.util.List.of(b, d, c), updated.route().nodes());
    }
}

