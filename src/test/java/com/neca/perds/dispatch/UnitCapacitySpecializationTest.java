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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for unit capacity and specialization dispatch features.
 */
final class UnitCapacitySpecializationTest {
    private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");

    private AdjacencyMapGraph graph;
    private NodeId nodeA;
    private NodeId nodeB;
    private NodeId nodeC;

    @BeforeEach
    void setUp() {
        graph = new AdjacencyMapGraph();
        nodeA = new NodeId("A");
        nodeB = new NodeId("B");
        nodeC = new NodeId("C");

        graph.addNode(new Node(nodeA, NodeType.CITY, Optional.empty(), "A"));
        graph.addNode(new Node(nodeB, NodeType.CITY, Optional.empty(), "B"));
        graph.addNode(new Node(nodeC, NodeType.CITY, Optional.empty(), "C"));

        // All edges have same weight so we can focus on capacity/specialization
        EdgeWeights weights = new EdgeWeights(5.0, Duration.ofSeconds(300), 1.0);
        graph.putEdge(new Edge(nodeA, nodeC, weights, EdgeStatus.OPEN));
        graph.putEdge(new Edge(nodeB, nodeC, weights, EdgeStatus.OPEN));
    }

    @Test
    void filtersOutUnitsWithInsufficientCapacity() {
        // Incident requires capacity of 3
        Incident incident = new Incident(
                new IncidentId("I1"),
                nodeC,
                IncidentSeverity.HIGH,
                Set.of(UnitType.AMBULANCE),
                IncidentStatus.REPORTED,
                NOW,
                Optional.empty(),
                3,  // requiredCapacity
                1   // requiredSpecializationLevel
        );

        // Unit with capacity 2 (insufficient)
        ResponseUnit lowCapacity = new ResponseUnit(
                new UnitId("A1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA,
                Optional.empty(),
                Optional.empty(),
                2,  // capacity
                1   // specializationLevel
        );
        // Unit with capacity 3 (sufficient)
        ResponseUnit highCapacity = new ResponseUnit(
                new UnitId("A2"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeB,
                Optional.empty(),
                Optional.empty(),
                3,  // capacity
                1   // specializationLevel
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(lowCapacity, highCapacity), List.of(), List.of(incident), List.of()
        );

        var policy = new NearestAvailableUnitPolicy(new DijkstraRouter(), CostFunctions.travelTimeSeconds());
        List<DispatchDecision> decisions = policy.chooseAll(snapshot, incident);

        assertEquals(1, decisions.size());
        assertEquals(new UnitId("A2"), decisions.getFirst().assignment().unitId(),
                "Should dispatch unit with sufficient capacity");
    }

    @Test
    void filtersOutUnitsWithInsufficientSpecialization() {
        // Incident requires specialization level 2
        Incident incident = new Incident(
                new IncidentId("I1"),
                nodeC,
                IncidentSeverity.HIGH,
                Set.of(UnitType.AMBULANCE),
                IncidentStatus.REPORTED,
                NOW,
                Optional.empty(),
                1,  // requiredCapacity
                2   // requiredSpecializationLevel (intermediate)
        );

        // Unit with specialization 1 (basic - insufficient)
        ResponseUnit basicUnit = new ResponseUnit(
                new UnitId("A1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA,
                Optional.empty(),
                Optional.empty(),
                1,  // capacity
                1   // specializationLevel (basic)
        );
        // Unit with specialization 2 (intermediate - sufficient)
        ResponseUnit advancedUnit = new ResponseUnit(
                new UnitId("A2"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeB,
                Optional.empty(),
                Optional.empty(),
                1,  // capacity
                2   // specializationLevel (intermediate)
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(basicUnit, advancedUnit), List.of(), List.of(incident), List.of()
        );

        var policy = new NearestAvailableUnitPolicy(new DijkstraRouter(), CostFunctions.travelTimeSeconds());
        List<DispatchDecision> decisions = policy.chooseAll(snapshot, incident);

        assertEquals(1, decisions.size());
        assertEquals(new UnitId("A2"), decisions.getFirst().assignment().unitId(),
                "Should dispatch unit with sufficient specialization");
    }

    @Test
    void prefersHigherSpecializationForSevereIncidents() {
        // HIGH severity incident - should prefer higher specialization when costs equal
        Incident incident = new Incident(
                new IncidentId("I1"),
                nodeC,
                IncidentSeverity.HIGH,
                Set.of(UnitType.AMBULANCE),
                IncidentStatus.REPORTED,
                NOW,
                Optional.empty()
        );

        // Both units at same location (same travel cost)
        ResponseUnit basicUnit = new ResponseUnit(
                new UnitId("A1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA,
                Optional.empty(),
                Optional.empty(),
                1,  // capacity
                1   // specializationLevel (basic)
        );
        ResponseUnit advancedUnit = new ResponseUnit(
                new UnitId("A2"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA,  // Same location
                Optional.empty(),
                Optional.empty(),
                1,  // capacity
                3   // specializationLevel (advanced)
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(basicUnit, advancedUnit), List.of(), List.of(incident), List.of()
        );

        var policy = new NearestAvailableUnitPolicy(new DijkstraRouter(), CostFunctions.travelTimeSeconds());
        List<DispatchDecision> decisions = policy.chooseAll(snapshot, incident);

        assertEquals(1, decisions.size());
        assertEquals(new UnitId("A2"), decisions.getFirst().assignment().unitId(),
                "Should prefer higher specialization for HIGH severity incident");
    }

    @Test
    void prefersHigherSpecializationForCriticalIncidents() {
        // CRITICAL severity incident
        Incident incident = new Incident(
                new IncidentId("I1"),
                nodeC,
                IncidentSeverity.CRITICAL,
                Set.of(UnitType.AMBULANCE),
                IncidentStatus.REPORTED,
                NOW,
                Optional.empty()
        );

        ResponseUnit basicUnit = new ResponseUnit(
                new UnitId("A1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA,
                Optional.empty(),
                Optional.empty(),
                1, 1  // basic
        );
        ResponseUnit intermediateUnit = new ResponseUnit(
                new UnitId("A2"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA,
                Optional.empty(),
                Optional.empty(),
                1, 2  // intermediate
        );
        ResponseUnit advancedUnit = new ResponseUnit(
                new UnitId("A3"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA,
                Optional.empty(),
                Optional.empty(),
                1, 3  // advanced
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(basicUnit, intermediateUnit, advancedUnit), List.of(), List.of(incident), List.of()
        );

        var policy = new NearestAvailableUnitPolicy(new DijkstraRouter(), CostFunctions.travelTimeSeconds());
        List<DispatchDecision> decisions = policy.chooseAll(snapshot, incident);

        assertEquals(1, decisions.size());
        assertEquals(new UnitId("A3"), decisions.getFirst().assignment().unitId(),
                "Should prefer highest specialization for CRITICAL severity incident");
    }

    @Test
    void doesNotPreferSpecializationForLowSeverity() {
        // LOW severity incident - should use normal selection (alphabetical ID for same cost)
        Incident incident = new Incident(
                new IncidentId("I1"),
                nodeC,
                IncidentSeverity.LOW,
                Set.of(UnitType.AMBULANCE),
                IncidentStatus.REPORTED,
                NOW,
                Optional.empty()
        );

        ResponseUnit basicUnit = new ResponseUnit(
                new UnitId("A1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA,
                Optional.empty(),
                Optional.empty(),
                1, 1  // basic
        );
        ResponseUnit advancedUnit = new ResponseUnit(
                new UnitId("A2"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA,
                Optional.empty(),
                Optional.empty(),
                1, 3  // advanced
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(basicUnit, advancedUnit), List.of(), List.of(incident), List.of()
        );

        var policy = new NearestAvailableUnitPolicy(new DijkstraRouter(), CostFunctions.travelTimeSeconds());
        List<DispatchDecision> decisions = policy.chooseAll(snapshot, incident);

        assertEquals(1, decisions.size());
        // For LOW severity, tie-break by ID, so A1 should be selected
        assertEquals(new UnitId("A1"), decisions.getFirst().assignment().unitId(),
                "For LOW severity, should use default tie-breaking (ID)");
    }

    @Test
    void noDispatchWhenNoUnitMeetsRequirements() {
        // Incident requires capacity 5 and specialization 3
        Incident incident = new Incident(
                new IncidentId("I1"),
                nodeC,
                IncidentSeverity.HIGH,
                Set.of(UnitType.AMBULANCE),
                IncidentStatus.REPORTED,
                NOW,
                Optional.empty(),
                5,  // requiredCapacity
                3   // requiredSpecializationLevel
        );

        // Unit that doesn't meet requirements
        ResponseUnit unit = new ResponseUnit(
                new UnitId("A1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA,
                Optional.empty(),
                Optional.empty(),
                2,  // capacity (insufficient)
                2   // specializationLevel (insufficient)
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(unit), List.of(), List.of(incident), List.of()
        );

        var policy = new NearestAvailableUnitPolicy(new DijkstraRouter(), CostFunctions.travelTimeSeconds());
        List<DispatchDecision> decisions = policy.chooseAll(snapshot, incident);

        assertTrue(decisions.isEmpty(), "Should not dispatch when no unit meets requirements");
    }

    @Test
    void multiSourcePolicyRespectsCapacityAndSpecialization() {
        // Test that MultiSourceNearestAvailableUnitPolicy also filters correctly
        Incident incident = new Incident(
                new IncidentId("I1"),
                nodeC,
                IncidentSeverity.HIGH,
                Set.of(UnitType.AMBULANCE),
                IncidentStatus.REPORTED,
                NOW,
                Optional.empty(),
                2,  // requiredCapacity
                2   // requiredSpecializationLevel
        );

        ResponseUnit insufficientUnit = new ResponseUnit(
                new UnitId("A1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA,
                Optional.empty(),
                Optional.empty(),
                1, 1  // insufficient
        );
        ResponseUnit sufficientUnit = new ResponseUnit(
                new UnitId("A2"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeB,
                Optional.empty(),
                Optional.empty(),
                2, 2  // meets requirements
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(insufficientUnit, sufficientUnit), List.of(), List.of(incident), List.of()
        );

        var policy = new MultiSourceNearestAvailableUnitPolicy(new DijkstraRouter(), CostFunctions.travelTimeSeconds());
        List<DispatchDecision> decisions = policy.chooseAll(snapshot, incident);

        assertEquals(1, decisions.size());
        assertEquals(new UnitId("A2"), decisions.getFirst().assignment().unitId());
    }

    @Test
    void backwardCompatibility_defaultsWork() {
        // Test that units and incidents created with convenience constructors work
        Incident incident = new Incident(
                new IncidentId("I1"),
                nodeC,
                IncidentSeverity.MEDIUM,
                Set.of(UnitType.AMBULANCE),
                IncidentStatus.REPORTED,
                NOW,
                Optional.empty()
        );

        ResponseUnit unit = new ResponseUnit(
                new UnitId("A1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA,
                Optional.empty(),
                Optional.empty()
        );

        // Verify defaults
        assertEquals(1, incident.requiredCapacity());
        assertEquals(1, incident.requiredSpecializationLevel());
        assertEquals(1, unit.capacity());
        assertEquals(1, unit.specializationLevel());

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(unit), List.of(), List.of(incident), List.of()
        );

        var policy = new NearestAvailableUnitPolicy(new DijkstraRouter(), CostFunctions.travelTimeSeconds());
        List<DispatchDecision> decisions = policy.chooseAll(snapshot, incident);

        assertEquals(1, decisions.size(), "Default units should be dispatched to default incidents");
    }

    @Test
    void meetsRequirementsMethod() {
        ResponseUnit unit = new ResponseUnit(
                new UnitId("A1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA,
                Optional.empty(),
                Optional.empty(),
                3,  // capacity
                2   // specializationLevel
        );

        // Test various requirement combinations
        assertTrue(unit.meetsRequirements(1, 1), "Should meet minimal requirements");
        assertTrue(unit.meetsRequirements(3, 2), "Should meet exact requirements");
        assertTrue(unit.meetsRequirements(2, 1), "Should meet lower requirements");
        assertFalse(unit.meetsRequirements(4, 1), "Should not meet higher capacity requirement");
        assertFalse(unit.meetsRequirements(1, 3), "Should not meet higher specialization requirement");
        assertFalse(unit.meetsRequirements(4, 3), "Should not meet both higher requirements");
    }
}
