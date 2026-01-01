package com.neca.perds.dispatch;

import com.neca.perds.graph.AdjacencyMapGraph;
import com.neca.perds.graph.Edge;
import com.neca.perds.graph.EdgeStatus;
import com.neca.perds.graph.EdgeWeights;
import com.neca.perds.model.DispatchCentre;
import com.neca.perds.model.DispatchCentreId;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ReturnToBasePolicy}.
 */
final class ReturnToBasePolicyTest {
    private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");

    private AdjacencyMapGraph graph;
    private NodeId nodeA;
    private NodeId nodeB;
    private NodeId nodeC;
    private DispatchCentreId centreId;
    private DispatchCentre dispatchCentre;
    private ReturnToBasePolicy policy;

    @BeforeEach
    void setUp() {
        graph = new AdjacencyMapGraph();
        nodeA = new NodeId("A");
        nodeB = new NodeId("B");
        nodeC = new NodeId("C");

        graph.addNode(new Node(nodeA, NodeType.CITY, Optional.empty(), "A"));
        graph.addNode(new Node(nodeB, NodeType.CITY, Optional.empty(), "B"));
        graph.addNode(new Node(nodeC, NodeType.CITY, Optional.empty(), "C"));

        EdgeWeights weights = new EdgeWeights(5.0, Duration.ofSeconds(300), 1.0);
        graph.putEdge(new Edge(nodeA, nodeB, weights, EdgeStatus.OPEN));
        graph.putEdge(new Edge(nodeB, nodeA, weights, EdgeStatus.OPEN));
        graph.putEdge(new Edge(nodeB, nodeC, weights, EdgeStatus.OPEN));
        graph.putEdge(new Edge(nodeC, nodeB, weights, EdgeStatus.OPEN));

        centreId = new DispatchCentreId("DC1");
        dispatchCentre = new DispatchCentre(centreId, nodeA, Set.of());

        policy = ReturnToBasePolicy.builder()
                .router(new DijkstraRouter())
                .costFunction(CostFunctions.travelTimeSeconds())
                .build();
    }

    @Test
    void returnsDecisionForUnitAwayFromHome() {
        ResponseUnit unit = new ResponseUnit(
                new UnitId("U1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeC, // Away from home
                Optional.empty(),
                Optional.of(centreId)
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(unit), List.of(dispatchCentre), List.of(), List.of()
        );

        Optional<ReturnToBasePolicy.ReturnToBaseDecision> decision = 
                policy.shouldReturnToBase(snapshot, unit);

        assertTrue(decision.isPresent());
        assertEquals(unit.id(), decision.get().unitId());
        assertEquals(centreId, decision.get().dispatchCentreId());
        assertEquals(nodeA, decision.get().homeNodeId());
        assertNotNull(decision.get().route());
        assertTrue(decision.get().route().totalDistanceKm() > 0);
    }

    @Test
    void returnsEmptyForUnitAlreadyAtHome() {
        ResponseUnit unit = new ResponseUnit(
                new UnitId("U1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA, // At home
                Optional.empty(),
                Optional.of(centreId)
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(unit), List.of(dispatchCentre), List.of(), List.of()
        );

        Optional<ReturnToBasePolicy.ReturnToBaseDecision> decision = 
                policy.shouldReturnToBase(snapshot, unit);

        assertTrue(decision.isEmpty());
    }

    @Test
    void returnsEmptyForUnitWithoutHomeBase() {
        ResponseUnit unit = new ResponseUnit(
                new UnitId("U1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeC,
                Optional.empty(),
                Optional.empty() // No home base
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(unit), List.of(dispatchCentre), List.of(), List.of()
        );

        Optional<ReturnToBasePolicy.ReturnToBaseDecision> decision = 
                policy.shouldReturnToBase(snapshot, unit);

        assertTrue(decision.isEmpty());
    }

    @Test
    void returnsEmptyForUnitWithAssignment() {
        ResponseUnit unit = new ResponseUnit(
                new UnitId("U1"),
                UnitType.AMBULANCE,
                UnitStatus.EN_ROUTE,
                nodeC,
                Optional.of(new com.neca.perds.model.IncidentId("I1")), // Assigned
                Optional.of(centreId)
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(unit), List.of(dispatchCentre), List.of(), List.of()
        );

        Optional<ReturnToBasePolicy.ReturnToBaseDecision> decision = 
                policy.shouldReturnToBase(snapshot, unit);

        assertTrue(decision.isEmpty());
    }

    @Test
    void respectsMinimumDistanceThreshold() {
        ReturnToBasePolicy policyWithThreshold = ReturnToBasePolicy.builder()
                .router(new DijkstraRouter())
                .costFunction(CostFunctions.travelTimeSeconds())
                .minReturnDistanceKm(20.0) // Threshold higher than actual distance
                .build();

        ResponseUnit unit = new ResponseUnit(
                new UnitId("U1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeC, // 10km from home (nodeA)
                Optional.empty(),
                Optional.of(centreId)
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(unit), List.of(dispatchCentre), List.of(), List.of()
        );

        Optional<ReturnToBasePolicy.ReturnToBaseDecision> decision = 
                policyWithThreshold.shouldReturnToBase(snapshot, unit);

        assertTrue(decision.isEmpty(), "Should not return when below distance threshold");
    }

    @Test
    void findsAllUnitsToReturn() {
        ResponseUnit unit1 = new ResponseUnit(
                new UnitId("U1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeC, // Away from home
                Optional.empty(),
                Optional.of(centreId)
        );
        ResponseUnit unit2 = new ResponseUnit(
                new UnitId("U2"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA, // At home - should not return
                Optional.empty(),
                Optional.of(centreId)
        );
        ResponseUnit unit3 = new ResponseUnit(
                new UnitId("U3"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeB, // Away from home
                Optional.empty(),
                Optional.of(centreId)
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(unit1, unit2, unit3), List.of(dispatchCentre), List.of(), List.of()
        );

        Map<UnitId, ReturnToBasePolicy.ReturnToBaseDecision> decisions = 
                policy.findUnitsToReturn(snapshot);

        assertEquals(2, decisions.size());
        assertTrue(decisions.containsKey(new UnitId("U1")));
        assertTrue(decisions.containsKey(new UnitId("U3")));
        assertFalse(decisions.containsKey(new UnitId("U2"))); // At home
    }

    @Test
    void builderValidation() {
        assertThrows(NullPointerException.class, () ->
                ReturnToBasePolicy.builder()
                        .costFunction(CostFunctions.travelTimeSeconds())
                        .build()); // Missing router

        assertThrows(NullPointerException.class, () ->
                ReturnToBasePolicy.builder()
                        .router(new DijkstraRouter())
                        .build()); // Missing costFunction

        assertThrows(IllegalArgumentException.class, () ->
                ReturnToBasePolicy.builder()
                        .router(new DijkstraRouter())
                        .costFunction(CostFunctions.travelTimeSeconds())
                        .minReturnDistanceKm(-1.0)
                        .build());
    }

    @Test
    void accessorMethods() {
        var router = new DijkstraRouter();
        var costFunction = CostFunctions.travelTimeSeconds();
        double minDistance = 5.0;

        ReturnToBasePolicy p = ReturnToBasePolicy.builder()
                .router(router)
                .costFunction(costFunction)
                .minReturnDistanceKm(minDistance)
                .build();

        assertSame(router, p.router());
        assertSame(costFunction, p.costFunction());
        assertEquals(minDistance, p.minReturnDistanceKm());
    }
}
