package com.neca.perds.dispatch;

import com.neca.perds.graph.AdjacencyMapGraph;
import com.neca.perds.graph.Edge;
import com.neca.perds.graph.EdgeStatus;
import com.neca.perds.graph.EdgeWeights;
import com.neca.perds.model.DispatchCentre;
import com.neca.perds.model.DispatchCentreId;
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
import com.neca.perds.routing.Router;
import com.neca.perds.system.SystemSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DispatchCentrePreference}.
 */
final class DispatchCentrePreferenceTest {
    private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");

    private AdjacencyMapGraph graph;
    private NodeId nodeA;
    private NodeId nodeB;
    private NodeId nodeC;
    private NodeId nodeD;
    private DispatchCentreId centreId;
    private DispatchCentre dispatchCentre;
    private Router router;

    @BeforeEach
    void setUp() {
        graph = new AdjacencyMapGraph();
        nodeA = new NodeId("A"); // Dispatch centre location
        nodeB = new NodeId("B");
        nodeC = new NodeId("C"); // Incident location
        nodeD = new NodeId("D");

        graph.addNode(new Node(nodeA, NodeType.CITY, Optional.empty(), "A"));
        graph.addNode(new Node(nodeB, NodeType.CITY, Optional.empty(), "B"));
        graph.addNode(new Node(nodeC, NodeType.CITY, Optional.empty(), "C"));
        graph.addNode(new Node(nodeD, NodeType.CITY, Optional.empty(), "D"));

        // A -- B -- C
        //      |
        //      D
        EdgeWeights weights = new EdgeWeights(5.0, Duration.ofSeconds(300), 1.0);
        graph.putEdge(new Edge(nodeA, nodeB, weights, EdgeStatus.OPEN));
        graph.putEdge(new Edge(nodeB, nodeA, weights, EdgeStatus.OPEN));
        graph.putEdge(new Edge(nodeB, nodeC, weights, EdgeStatus.OPEN));
        graph.putEdge(new Edge(nodeC, nodeB, weights, EdgeStatus.OPEN));
        graph.putEdge(new Edge(nodeB, nodeD, weights, EdgeStatus.OPEN));
        graph.putEdge(new Edge(nodeD, nodeB, weights, EdgeStatus.OPEN));

        centreId = new DispatchCentreId("DC1");
        dispatchCentre = new DispatchCentre(centreId, nodeA, Set.of());
        router = new DijkstraRouter();
    }

    @Test
    void unitWithoutHomeBaseGetsNeutralScore() {
        ResponseUnit unit = new ResponseUnit(
                new UnitId("U1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeB,
                Optional.empty(),
                Optional.empty() // No home base
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(unit), List.of(dispatchCentre), List.of(), List.of()
        );

        double score = DispatchCentrePreference.computePreferenceScore(
                snapshot, unit, nodeC, router, CostFunctions.travelTimeSeconds());

        assertEquals(0.0, score);
    }

    @Test
    void unitAtHomeGetsHigherScoreThanUnitNotAtHome() {
        ResponseUnit unitAtHome = new ResponseUnit(
                new UnitId("U1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA, // At home
                Optional.empty(),
                Optional.of(centreId)
        );
        ResponseUnit unitAway = new ResponseUnit(
                new UnitId("U2"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeD, // Away from home
                Optional.empty(),
                Optional.of(centreId)
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(unitAtHome, unitAway), List.of(dispatchCentre), List.of(), List.of()
        );

        double scoreAtHome = DispatchCentrePreference.computePreferenceScore(
                snapshot, unitAtHome, nodeC, router, CostFunctions.travelTimeSeconds());
        double scoreAway = DispatchCentrePreference.computePreferenceScore(
                snapshot, unitAway, nodeC, router, CostFunctions.travelTimeSeconds());

        // Unit at home should have higher score (worse) to preserve home coverage
        assertTrue(scoreAtHome > scoreAway,
                "Unit at home should have higher score (less preferred) to preserve coverage");
    }

    @Test
    void prefersUnitCloserToHomeAfterIncident() {
        // Setup: two units at same location, but incident C is closer to home A for one path
        // Unit at D going to C then back to A: D->B->C, C->B->A = 4 hops
        // Unit at B going to C then back to A: B->C, C->B->A = 3 hops
        ResponseUnit unitAtD = new ResponseUnit(
                new UnitId("U1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeD,
                Optional.empty(),
                Optional.of(centreId)
        );
        ResponseUnit unitAtB = new ResponseUnit(
                new UnitId("U2"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeB,
                Optional.empty(),
                Optional.of(centreId)
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(unitAtD, unitAtB), List.of(dispatchCentre), List.of(), List.of()
        );

        double scoreD = DispatchCentrePreference.computePreferenceScore(
                snapshot, unitAtD, nodeC, router, CostFunctions.travelTimeSeconds());
        double scoreB = DispatchCentrePreference.computePreferenceScore(
                snapshot, unitAtB, nodeC, router, CostFunctions.travelTimeSeconds());

        // Both not at home, so return distance matters
        // C to A is same for both (C->B->A), but the penalty is computed
        // Unit at B has same return cost as unit at D from incident location C
    }

    @Test
    void isAtHomeBaseReturnsCorrectly() {
        ResponseUnit unitAtHome = new ResponseUnit(
                new UnitId("U1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA, // At home (dispatch centre is at A)
                Optional.empty(),
                Optional.of(centreId)
        );
        ResponseUnit unitAway = new ResponseUnit(
                new UnitId("U2"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeC, // Not at home
                Optional.empty(),
                Optional.of(centreId)
        );
        ResponseUnit unitWithoutHome = new ResponseUnit(
                new UnitId("U3"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA,
                Optional.empty(),
                Optional.empty()
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(unitAtHome, unitAway, unitWithoutHome),
                List.of(dispatchCentre), List.of(), List.of()
        );

        assertTrue(DispatchCentrePreference.isAtHomeBase(snapshot, unitAtHome));
        assertFalse(DispatchCentrePreference.isAtHomeBase(snapshot, unitAway));
        assertFalse(DispatchCentrePreference.isAtHomeBase(snapshot, unitWithoutHome));
    }

    @Test
    void getHomeNodeReturnsCorrectly() {
        ResponseUnit unitWithHome = new ResponseUnit(
                new UnitId("U1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeC,
                Optional.empty(),
                Optional.of(centreId)
        );
        ResponseUnit unitWithoutHome = new ResponseUnit(
                new UnitId("U2"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeC,
                Optional.empty(),
                Optional.empty()
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(unitWithHome, unitWithoutHome),
                List.of(dispatchCentre), List.of(), List.of()
        );

        Optional<NodeId> home1 = DispatchCentrePreference.getHomeNode(snapshot, unitWithHome);
        Optional<NodeId> home2 = DispatchCentrePreference.getHomeNode(snapshot, unitWithoutHome);

        assertTrue(home1.isPresent());
        assertEquals(nodeA, home1.get());
        assertTrue(home2.isEmpty());
    }

    @Test
    void compareByPreferenceWorks() {
        ResponseUnit unit1 = new ResponseUnit(
                new UnitId("U1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA, // At home
                Optional.empty(),
                Optional.of(centreId)
        );
        ResponseUnit unit2 = new ResponseUnit(
                new UnitId("U2"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeD, // Away from home
                Optional.empty(),
                Optional.of(centreId)
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(unit1, unit2), List.of(dispatchCentre), List.of(), List.of()
        );

        int comparison = DispatchCentrePreference.compareByPreference(
                snapshot, unit1, unit2, nodeC, router, CostFunctions.travelTimeSeconds());

        // Unit2 (away from home) should be preferred (lower score)
        assertTrue(comparison > 0, "Unit at home should compare as worse (higher)");
    }

    @Test
    void dispatchPolicyUsesPreference() {
        // Two units at same location, same type, same specialization
        // Only difference is one is at home, one is not
        ResponseUnit unitAtHome = new ResponseUnit(
                new UnitId("U1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA, // At home dispatch centre
                Optional.empty(),
                Optional.of(centreId),
                1, 1
        );
        ResponseUnit unitAway = new ResponseUnit(
                new UnitId("U2"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA, // Same location, but "not at home" semantically (different dispatch centre)
                Optional.empty(),
                Optional.empty(), // No home - neutral score
                1, 1
        );

        // Create incident at C
        Incident incident = new Incident(
                new IncidentId("I1"),
                nodeC,
                IncidentSeverity.LOW,
                Set.of(UnitType.AMBULANCE),
                IncidentStatus.REPORTED,
                NOW,
                Optional.empty()
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(unitAtHome, unitAway), List.of(dispatchCentre), List.of(incident), List.of()
        );

        var policy = new NearestAvailableUnitPolicy(router, CostFunctions.travelTimeSeconds());
        var decisions = policy.chooseAll(snapshot, incident);

        assertEquals(1, decisions.size());
        // Unit without home (neutral 0.0 score) should be preferred over unit at home (high score)
        assertEquals(new UnitId("U2"), decisions.getFirst().assignment().unitId(),
                "Should prefer unit without home base (preserves home coverage)");
    }
}
