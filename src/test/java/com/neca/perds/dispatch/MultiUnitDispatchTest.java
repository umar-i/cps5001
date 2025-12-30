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
 * Tests for multi-unit incident dispatch functionality.
 * Verifies that incidents requiring multiple unit types receive appropriate dispatch.
 */
final class MultiUnitDispatchTest {
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

        EdgeWeights weights = new EdgeWeights(5.0, Duration.ofSeconds(300), 1.0);
        graph.putEdge(new Edge(nodeA, nodeB, weights, EdgeStatus.OPEN));
        graph.putEdge(new Edge(nodeB, nodeC, weights, EdgeStatus.OPEN));
        graph.putEdge(new Edge(nodeA, nodeC, weights, EdgeStatus.OPEN));
    }

    @Test
    void nearestPolicyDispatchesMultipleUnitsForMultiTypeIncident() {
        // Incident requiring both ambulance and fire truck
        Incident incident = new Incident(
                new IncidentId("I1"),
                nodeC,
                IncidentSeverity.HIGH,
                Set.of(UnitType.AMBULANCE, UnitType.FIRE_TRUCK),
                IncidentStatus.REPORTED,
                NOW,
                Optional.empty()
        );

        ResponseUnit ambulance = new ResponseUnit(
                new UnitId("A1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA,
                Optional.empty(),
                Optional.empty()
        );
        ResponseUnit fireTruck = new ResponseUnit(
                new UnitId("F1"),
                UnitType.FIRE_TRUCK,
                UnitStatus.AVAILABLE,
                nodeB,
                Optional.empty(),
                Optional.empty()
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(ambulance, fireTruck), List.of(), List.of(incident), List.of()
        );

        var policy = new NearestAvailableUnitPolicy(new DijkstraRouter(), CostFunctions.travelTimeSeconds());
        List<DispatchDecision> decisions = policy.chooseAll(snapshot, incident);

        assertEquals(2, decisions.size(), "Should dispatch one unit per required type");
        
        // Verify both unit types are dispatched
        Set<UnitType> dispatchedTypes = Set.of(
                getUnitType(snapshot, decisions.get(0).assignment().unitId()),
                getUnitType(snapshot, decisions.get(1).assignment().unitId())
        );
        assertTrue(dispatchedTypes.contains(UnitType.AMBULANCE), "Should dispatch an ambulance");
        assertTrue(dispatchedTypes.contains(UnitType.FIRE_TRUCK), "Should dispatch a fire truck");
    }

    @Test
    void multiSourcePolicyDispatchesMultipleUnitsForMultiTypeIncident() {
        // Incident requiring both ambulance and fire truck
        Incident incident = new Incident(
                new IncidentId("I1"),
                nodeC,
                IncidentSeverity.HIGH,
                Set.of(UnitType.AMBULANCE, UnitType.FIRE_TRUCK),
                IncidentStatus.REPORTED,
                NOW,
                Optional.empty()
        );

        ResponseUnit ambulance = new ResponseUnit(
                new UnitId("A1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA,
                Optional.empty(),
                Optional.empty()
        );
        ResponseUnit fireTruck = new ResponseUnit(
                new UnitId("F1"),
                UnitType.FIRE_TRUCK,
                UnitStatus.AVAILABLE,
                nodeB,
                Optional.empty(),
                Optional.empty()
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(ambulance, fireTruck), List.of(), List.of(incident), List.of()
        );

        var policy = new MultiSourceNearestAvailableUnitPolicy(new DijkstraRouter(), CostFunctions.travelTimeSeconds());
        List<DispatchDecision> decisions = policy.chooseAll(snapshot, incident);

        assertEquals(2, decisions.size(), "Should dispatch one unit per required type");
        
        Set<UnitType> dispatchedTypes = Set.of(
                getUnitType(snapshot, decisions.get(0).assignment().unitId()),
                getUnitType(snapshot, decisions.get(1).assignment().unitId())
        );
        assertTrue(dispatchedTypes.contains(UnitType.AMBULANCE));
        assertTrue(dispatchedTypes.contains(UnitType.FIRE_TRUCK));
    }

    @Test
    void partialDispatchWhenNotAllTypesAvailable() {
        // Incident requiring ambulance, fire truck, and police - but no police available
        Incident incident = new Incident(
                new IncidentId("I1"),
                nodeC,
                IncidentSeverity.CRITICAL,
                Set.of(UnitType.AMBULANCE, UnitType.FIRE_TRUCK, UnitType.POLICE),
                IncidentStatus.REPORTED,
                NOW,
                Optional.empty()
        );

        ResponseUnit ambulance = new ResponseUnit(
                new UnitId("A1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA,
                Optional.empty(),
                Optional.empty()
        );
        ResponseUnit fireTruck = new ResponseUnit(
                new UnitId("F1"),
                UnitType.FIRE_TRUCK,
                UnitStatus.AVAILABLE,
                nodeB,
                Optional.empty(),
                Optional.empty()
        );
        // No police unit available

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(ambulance, fireTruck), List.of(), List.of(incident), List.of()
        );

        var policy = new NearestAvailableUnitPolicy(new DijkstraRouter(), CostFunctions.travelTimeSeconds());
        List<DispatchDecision> decisions = policy.chooseAll(snapshot, incident);

        assertEquals(2, decisions.size(), "Should dispatch available types even when some are missing");
    }

    @Test
    void doesNotDoubleDispatchSameUnitType() {
        // Incident requiring only ambulance - should only dispatch one ambulance even if multiple available
        Incident incident = new Incident(
                new IncidentId("I1"),
                nodeC,
                IncidentSeverity.HIGH,
                Set.of(UnitType.AMBULANCE),
                IncidentStatus.REPORTED,
                NOW,
                Optional.empty()
        );

        ResponseUnit ambulance1 = new ResponseUnit(
                new UnitId("A1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA,
                Optional.empty(),
                Optional.empty()
        );
        ResponseUnit ambulance2 = new ResponseUnit(
                new UnitId("A2"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeB,
                Optional.empty(),
                Optional.empty()
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(ambulance1, ambulance2), List.of(), List.of(incident), List.of()
        );

        var policy = new NearestAvailableUnitPolicy(new DijkstraRouter(), CostFunctions.travelTimeSeconds());
        List<DispatchDecision> decisions = policy.chooseAll(snapshot, incident);

        assertEquals(1, decisions.size(), "Should only dispatch one unit when only one type required");
    }

    @Test
    void skipsAlreadyAssignedUnitTypes() {
        // Incident with ambulance already assigned - should not dispatch another ambulance
        // Using QUEUED status to indicate incident is waiting for more unit types
        Incident incident = new Incident(
                new IncidentId("I1"),
                nodeC,
                IncidentSeverity.HIGH,
                Set.of(UnitType.AMBULANCE, UnitType.FIRE_TRUCK),
                IncidentStatus.QUEUED,  // Still waiting for more units
                NOW,
                Optional.empty()
        );

        ResponseUnit assignedAmbulance = new ResponseUnit(
                new UnitId("A1"),
                UnitType.AMBULANCE,
                UnitStatus.EN_ROUTE,
                nodeA,
                Optional.of(new IncidentId("I1")),
                Optional.empty()
        );
        ResponseUnit availableAmbulance = new ResponseUnit(
                new UnitId("A2"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeB,
                Optional.empty(),
                Optional.empty()
        );
        ResponseUnit fireTruck = new ResponseUnit(
                new UnitId("F1"),
                UnitType.FIRE_TRUCK,
                UnitStatus.AVAILABLE,
                nodeB,
                Optional.empty(),
                Optional.empty()
        );

        // Create an existing assignment for the ambulance
        var existingAssignment = new com.neca.perds.model.Assignment(
                incident.id(),
                assignedAmbulance.id(),
                new com.neca.perds.routing.Route(
                        List.of(nodeA, nodeC),
                        5.0,
                        300.0,
                        Duration.ofSeconds(300),
                        0L
                ),
                NOW
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW,
                List.of(assignedAmbulance, availableAmbulance, fireTruck),
                List.of(),
                List.of(incident),
                List.of(existingAssignment)
        );

        var policy = new NearestAvailableUnitPolicy(new DijkstraRouter(), CostFunctions.travelTimeSeconds());
        List<DispatchDecision> decisions = policy.chooseAll(snapshot, incident);

        assertEquals(1, decisions.size(), "Should only dispatch fire truck since ambulance already assigned");
        assertEquals(new UnitId("F1"), decisions.getFirst().assignment().unitId());
    }

    @Test
    void defaultDispatchEngineHandlesMultiUnitDispatch() {
        // Test end-to-end through DefaultDispatchEngine
        Incident incident = new Incident(
                new IncidentId("I1"),
                nodeC,
                IncidentSeverity.HIGH,
                Set.of(UnitType.AMBULANCE, UnitType.FIRE_TRUCK),
                IncidentStatus.REPORTED,
                NOW,
                Optional.empty()
        );

        ResponseUnit ambulance = new ResponseUnit(
                new UnitId("A1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA,
                Optional.empty(),
                Optional.empty()
        );
        ResponseUnit fireTruck = new ResponseUnit(
                new UnitId("F1"),
                UnitType.FIRE_TRUCK,
                UnitStatus.AVAILABLE,
                nodeB,
                Optional.empty(),
                Optional.empty()
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(ambulance, fireTruck), List.of(), List.of(incident), List.of()
        );

        var engine = new DefaultDispatchEngine(
                new SeverityThenOldestPrioritizer(),
                new NearestAvailableUnitPolicy(new DijkstraRouter(), CostFunctions.travelTimeSeconds())
        );

        List<DispatchCommand> commands = engine.compute(snapshot);

        assertEquals(2, commands.size(), "Should produce two dispatch commands");
        
        // Verify both are AssignUnitCommands for the same incident
        for (DispatchCommand cmd : commands) {
            assertInstanceOf(DispatchCommand.AssignUnitCommand.class, cmd);
            DispatchCommand.AssignUnitCommand assignCmd = (DispatchCommand.AssignUnitCommand) cmd;
            assertEquals(incident.id(), assignCmd.incidentId());
        }
    }

    @Test
    void backwardCompatibility_singleUnitIncident() {
        // Verify single-type incidents still work correctly
        Incident incident = new Incident(
                new IncidentId("I1"),
                nodeC,
                IncidentSeverity.LOW,
                Set.of(UnitType.AMBULANCE),
                IncidentStatus.REPORTED,
                NOW,
                Optional.empty()
        );

        ResponseUnit ambulance = new ResponseUnit(
                new UnitId("A1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA,
                Optional.empty(),
                Optional.empty()
        );

        SystemSnapshot snapshot = new SystemSnapshot(
                graph, NOW, List.of(ambulance), List.of(), List.of(incident), List.of()
        );

        var policy = new NearestAvailableUnitPolicy(new DijkstraRouter(), CostFunctions.travelTimeSeconds());
        
        // Test both choose() and chooseAll()
        Optional<DispatchDecision> singleDecision = policy.choose(snapshot, incident);
        List<DispatchDecision> allDecisions = policy.chooseAll(snapshot, incident);

        assertTrue(singleDecision.isPresent(), "choose() should return a decision");
        assertEquals(1, allDecisions.size(), "chooseAll() should return one decision");
        assertEquals(singleDecision.get().assignment().unitId(), allDecisions.getFirst().assignment().unitId(),
                "Both methods should choose the same unit");
    }

    private UnitType getUnitType(SystemSnapshot snapshot, UnitId unitId) {
        return snapshot.units().stream()
                .filter(u -> u.id().equals(unitId))
                .findFirst()
                .map(ResponseUnit::type)
                .orElseThrow();
    }
}
