package com.neca.perds.dispatch;

import com.neca.perds.graph.AdjacencyMapGraph;
import com.neca.perds.graph.Edge;
import com.neca.perds.graph.EdgeStatus;
import com.neca.perds.graph.EdgeWeights;
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
import com.neca.perds.routing.CostFunctions;
import com.neca.perds.routing.DijkstraRouter;
import com.neca.perds.system.SystemSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NearestAvailableUnitPolicyTest {
    @Test
    void choosesNearestAvailableUnitOfRequiredType() {
        Instant now = Instant.parse("2025-01-01T00:00:00Z");

        var graph = new AdjacencyMapGraph();
        NodeId a = new NodeId("A");
        NodeId b = new NodeId("B");
        NodeId c = new NodeId("C");

        graph.addNode(new Node(a, NodeType.CITY, Optional.empty(), "A"));
        graph.addNode(new Node(b, NodeType.CITY, Optional.empty(), "B"));
        graph.addNode(new Node(c, NodeType.CITY, Optional.empty(), "C"));

        EdgeWeights fast = new EdgeWeights(5.0, Duration.ofSeconds(300), 1.0);
        graph.putEdge(new Edge(a, b, fast, EdgeStatus.OPEN));
        graph.putEdge(new Edge(b, c, fast, EdgeStatus.OPEN));
        graph.putEdge(new Edge(a, c, new EdgeWeights(20.0, Duration.ofSeconds(1200), 1.0), EdgeStatus.OPEN));

        ResponseUnit u1 = new ResponseUnit(
                new UnitId("U1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                a,
                Optional.empty(),
                Optional.empty()
        );
        ResponseUnit u2 = new ResponseUnit(
                new UnitId("U2"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                b,
                Optional.empty(),
                Optional.empty()
        );
        ResponseUnit fireTruck = new ResponseUnit(
                new UnitId("F1"),
                UnitType.FIRE_TRUCK,
                UnitStatus.AVAILABLE,
                b,
                Optional.empty(),
                Optional.empty()
        );

        Incident incident = new Incident(
                new IncidentId("I1"),
                c,
                IncidentSeverity.HIGH,
                Set.of(UnitType.AMBULANCE),
                IncidentStatus.REPORTED,
                now,
                Optional.empty()
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph,
                now,
                List.of(u1, u2, fireTruck),
                List.of(),
                List.of(incident),
                List.of()
        );

        var policy = new NearestAvailableUnitPolicy(new DijkstraRouter(), CostFunctions.travelTimeSeconds());
        DispatchDecision decision = policy.choose(snapshot, incident).orElseThrow();

        assertEquals(new UnitId("U2"), decision.assignment().unitId());
        assertEquals(List.of(b, c), decision.assignment().route().nodes());
    }

    @Test
    void returnsEmptyWhenIncidentNotDispatchable() {
        Instant now = Instant.parse("2025-01-01T00:00:00Z");
        var graph = new AdjacencyMapGraph();
        NodeId a = new NodeId("A");
        NodeId b = new NodeId("B");

        graph.addNode(new Node(a, NodeType.CITY, Optional.empty(), "A"));
        graph.addNode(new Node(b, NodeType.CITY, Optional.empty(), "B"));

        ResponseUnit unit = new ResponseUnit(
                new UnitId("U1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                a,
                Optional.empty(),
                Optional.empty()
        );
        Incident incident = new Incident(
                new IncidentId("I1"),
                b,
                IncidentSeverity.LOW,
                Set.of(UnitType.AMBULANCE),
                IncidentStatus.RESOLVED,
                now,
                Optional.of(now)
        );

        SystemSnapshot snapshot = new SystemSnapshot(graph, now, List.of(unit), List.of(), List.of(incident), List.of());

        var policy = new NearestAvailableUnitPolicy(new DijkstraRouter(), CostFunctions.travelTimeSeconds());
        assertTrue(policy.choose(snapshot, incident).isEmpty());
    }
}

