package com.neca.perds.app;

import com.neca.perds.dispatch.DefaultDispatchEngine;
import com.neca.perds.dispatch.NearestAvailableUnitPolicy;
import com.neca.perds.dispatch.SeverityThenOldestPrioritizer;
import com.neca.perds.graph.AdjacencyMapGraph;
import com.neca.perds.graph.Edge;
import com.neca.perds.graph.EdgeStatus;
import com.neca.perds.graph.EdgeWeights;
import com.neca.perds.metrics.InMemoryMetricsCollector;
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
import com.neca.perds.prediction.NoOpDemandPredictor;
import com.neca.perds.prediction.NoOpPrepositioningStrategy;
import com.neca.perds.sim.SystemCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for dispatch centre association features in PerdsController.
 */
final class PerdsControllerDispatchCentreTest {
    private static final Instant T0 = Instant.parse("2025-01-01T00:00:00Z");

    private AdjacencyMapGraph graph;
    private PerdsController controller;
    private NodeId nodeA; // Dispatch centre location
    private NodeId nodeB;
    private NodeId nodeC; // Incident location

    @BeforeEach
    void setUp() {
        graph = new AdjacencyMapGraph();
        nodeA = new NodeId("A");
        nodeB = new NodeId("B");
        nodeC = new NodeId("C");

        var dispatchEngine = new DefaultDispatchEngine(
                new SeverityThenOldestPrioritizer(),
                new NearestAvailableUnitPolicy()
        );

        controller = new PerdsController(
                graph,
                dispatchEngine,
                new NoOpDemandPredictor(),
                new NoOpPrepositioningStrategy(),
                new InMemoryMetricsCollector()
        );

        // Set up graph: A -- B -- C
        controller.execute(new SystemCommand.AddNodeCommand(
                new Node(nodeA, NodeType.CITY, Optional.empty(), "A")), T0);
        controller.execute(new SystemCommand.AddNodeCommand(
                new Node(nodeB, NodeType.CITY, Optional.empty(), "B")), T0);
        controller.execute(new SystemCommand.AddNodeCommand(
                new Node(nodeC, NodeType.CITY, Optional.empty(), "C")), T0);

        EdgeWeights weights = new EdgeWeights(5.0, Duration.ofSeconds(300), 1.0);
        controller.execute(new SystemCommand.PutEdgeCommand(
                new Edge(nodeA, nodeB, weights, EdgeStatus.OPEN)), T0);
        controller.execute(new SystemCommand.PutEdgeCommand(
                new Edge(nodeB, nodeA, weights, EdgeStatus.OPEN)), T0);
        controller.execute(new SystemCommand.PutEdgeCommand(
                new Edge(nodeB, nodeC, weights, EdgeStatus.OPEN)), T0);
        controller.execute(new SystemCommand.PutEdgeCommand(
                new Edge(nodeC, nodeB, weights, EdgeStatus.OPEN)), T0);
    }

    @Test
    void registerDispatchCentre() {
        DispatchCentreId centreId = new DispatchCentreId("DC1");
        DispatchCentre centre = new DispatchCentre(centreId, nodeA, Set.of());

        controller.execute(new SystemCommand.RegisterDispatchCentreCommand(centre), T0);

        var snapshot = controller.snapshot(T0);
        assertEquals(1, snapshot.dispatchCentres().size());
        assertTrue(snapshot.dispatchCentres().stream()
                .anyMatch(dc -> dc.id().equals(centreId)));
    }

    @Test
    void unitReturnsToBaseAfterIncidentResolved() {
        // Register dispatch centre at A
        DispatchCentreId centreId = new DispatchCentreId("DC1");
        DispatchCentre centre = new DispatchCentre(centreId, nodeA, Set.of());
        controller.execute(new SystemCommand.RegisterDispatchCentreCommand(centre), T0);

        // Register unit with home at dispatch centre, starting at A
        UnitId unitId = new UnitId("U1");
        controller.execute(new SystemCommand.RegisterUnitCommand(new ResponseUnit(
                unitId,
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA, // Starting at home
                Optional.empty(),
                Optional.of(centreId)
        )), T0);

        // Report incident at C
        IncidentId incidentId = new IncidentId("I1");
        controller.execute(new SystemCommand.ReportIncidentCommand(new Incident(
                incidentId,
                nodeC,
                IncidentSeverity.HIGH,
                Set.of(UnitType.AMBULANCE),
                IncidentStatus.REPORTED,
                T0,
                Optional.empty()
        )), T0);

        // Verify unit is dispatched
        var snapshot1 = controller.snapshot(T0);
        assertEquals(1, snapshot1.assignments().size());
        var unit1 = snapshot1.units().stream()
                .filter(u -> u.id().equals(unitId))
                .findFirst().orElseThrow();
        assertEquals(UnitStatus.EN_ROUTE, unit1.status());

        // Simulate unit arriving at incident (move to C)
        Instant t1 = T0.plusSeconds(600); // After travel time
        controller.execute(new SystemCommand.MoveUnitCommand(unitId, nodeC), t1);
        controller.execute(new SystemCommand.SetUnitStatusCommand(unitId, UnitStatus.ON_SCENE), t1);

        // Resolve incident
        Instant t2 = t1.plusSeconds(300);
        controller.execute(new SystemCommand.ResolveIncidentCommand(incidentId), t2);

        // Verify unit is now REPOSITIONING back to home
        var snapshot2 = controller.snapshot(t2);
        var unit2 = snapshot2.units().stream()
                .filter(u -> u.id().equals(unitId))
                .findFirst().orElseThrow();
        assertEquals(UnitStatus.REPOSITIONING, unit2.status(),
                "Unit should be repositioning back to home after incident resolution");
        assertEquals(nodeC, unit2.currentNodeId(),
                "Unit should still be at incident location while repositioning");

        // Advance time to complete repositioning by executing any command
        // The return travel time is 600 seconds (C->B->A), so t3 should be after that
        Instant t3 = t2.plusSeconds(700);
        // Use SetUnitStatusCommand on a non-existent property or just report a dummy incident
        // Actually, the snapshot method doesn't trigger completeRepositionings
        // We need to execute a command. Let's use a harmless add node command
        controller.execute(new SystemCommand.AddNodeCommand(
                new Node(new NodeId("Z"), NodeType.CITY, Optional.empty(), "Z")), t3);

        var snapshot3 = controller.snapshot(t3);
        var unit3 = snapshot3.units().stream()
                .filter(u -> u.id().equals(unitId))
                .findFirst().orElseThrow();
        assertEquals(UnitStatus.AVAILABLE, unit3.status(),
                "Unit should be available after completing return to base");
        assertEquals(nodeA, unit3.currentNodeId(),
                "Unit should be back at home dispatch centre");
    }

    @Test
    void unitWithoutHomeDoesNotRepositionAfterIncident() {
        // Register unit WITHOUT home dispatch centre
        UnitId unitId = new UnitId("U1");
        controller.execute(new SystemCommand.RegisterUnitCommand(new ResponseUnit(
                unitId,
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA,
                Optional.empty(),
                Optional.empty() // No home
        )), T0);

        // Report and assign incident
        IncidentId incidentId = new IncidentId("I1");
        controller.execute(new SystemCommand.ReportIncidentCommand(new Incident(
                incidentId,
                nodeC,
                IncidentSeverity.HIGH,
                Set.of(UnitType.AMBULANCE),
                IncidentStatus.REPORTED,
                T0,
                Optional.empty()
        )), T0);

        // Move to incident and resolve
        Instant t1 = T0.plusSeconds(600);
        controller.execute(new SystemCommand.MoveUnitCommand(unitId, nodeC), t1);
        controller.execute(new SystemCommand.SetUnitStatusCommand(unitId, UnitStatus.ON_SCENE), t1);

        Instant t2 = t1.plusSeconds(300);
        controller.execute(new SystemCommand.ResolveIncidentCommand(incidentId), t2);

        // Unit should be AVAILABLE, not REPOSITIONING
        var snapshot = controller.snapshot(t2);
        var unit = snapshot.units().stream()
                .filter(u -> u.id().equals(unitId))
                .findFirst().orElseThrow();
        assertEquals(UnitStatus.AVAILABLE, unit.status(),
                "Unit without home should become AVAILABLE, not REPOSITIONING");
        assertEquals(nodeC, unit.currentNodeId(),
                "Unit should remain at incident location");
    }

    @Test
    void unitAlreadyAtHomeDoesNotReposition() {
        // Register dispatch centre at C (same as incident location)
        DispatchCentreId centreId = new DispatchCentreId("DC1");
        DispatchCentre centre = new DispatchCentre(centreId, nodeC, Set.of());
        controller.execute(new SystemCommand.RegisterDispatchCentreCommand(centre), T0);

        // Register unit with home at C, starting at A
        UnitId unitId = new UnitId("U1");
        controller.execute(new SystemCommand.RegisterUnitCommand(new ResponseUnit(
                unitId,
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA,
                Optional.empty(),
                Optional.of(centreId)
        )), T0);

        // Report incident at C (same as home base)
        IncidentId incidentId = new IncidentId("I1");
        controller.execute(new SystemCommand.ReportIncidentCommand(new Incident(
                incidentId,
                nodeC,
                IncidentSeverity.HIGH,
                Set.of(UnitType.AMBULANCE),
                IncidentStatus.REPORTED,
                T0,
                Optional.empty()
        )), T0);

        // Move to incident and resolve
        Instant t1 = T0.plusSeconds(600);
        controller.execute(new SystemCommand.MoveUnitCommand(unitId, nodeC), t1);
        controller.execute(new SystemCommand.SetUnitStatusCommand(unitId, UnitStatus.ON_SCENE), t1);

        Instant t2 = t1.plusSeconds(300);
        controller.execute(new SystemCommand.ResolveIncidentCommand(incidentId), t2);

        // Unit is already at home (C), so should not start repositioning
        var snapshot = controller.snapshot(t2);
        var unit = snapshot.units().stream()
                .filter(u -> u.id().equals(unitId))
                .findFirst().orElseThrow();
        assertEquals(UnitStatus.AVAILABLE, unit.status(),
                "Unit already at home should be AVAILABLE, not REPOSITIONING");
        assertEquals(nodeC, unit.currentNodeId());
    }

    @Test
    void repositioningCanBeInterruptedByNewIncident() {
        // Setup dispatch centre and unit
        DispatchCentreId centreId = new DispatchCentreId("DC1");
        DispatchCentre centre = new DispatchCentre(centreId, nodeA, Set.of());
        controller.execute(new SystemCommand.RegisterDispatchCentreCommand(centre), T0);

        UnitId unitId = new UnitId("U1");
        controller.execute(new SystemCommand.RegisterUnitCommand(new ResponseUnit(
                unitId,
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                nodeA,
                Optional.empty(),
                Optional.of(centreId)
        )), T0);

        // First incident at C
        IncidentId incident1 = new IncidentId("I1");
        controller.execute(new SystemCommand.ReportIncidentCommand(new Incident(
                incident1,
                nodeC,
                IncidentSeverity.HIGH,
                Set.of(UnitType.AMBULANCE),
                IncidentStatus.REPORTED,
                T0,
                Optional.empty()
        )), T0);

        // Move, work, resolve
        Instant t1 = T0.plusSeconds(600);
        controller.execute(new SystemCommand.MoveUnitCommand(unitId, nodeC), t1);
        controller.execute(new SystemCommand.SetUnitStatusCommand(unitId, UnitStatus.ON_SCENE), t1);
        Instant t2 = t1.plusSeconds(300);
        controller.execute(new SystemCommand.ResolveIncidentCommand(incident1), t2);

        // Unit should be REPOSITIONING
        var snapshot1 = controller.snapshot(t2);
        var unit1 = snapshot1.units().stream()
                .filter(u -> u.id().equals(unitId))
                .findFirst().orElseThrow();
        assertEquals(UnitStatus.REPOSITIONING, unit1.status());

        // New incident at B - unit should be reassigned (REPOSITIONING units are available)
        Instant t3 = t2.plusSeconds(100);
        IncidentId incident2 = new IncidentId("I2");
        controller.execute(new SystemCommand.ReportIncidentCommand(new Incident(
                incident2,
                nodeB,
                IncidentSeverity.CRITICAL,
                Set.of(UnitType.AMBULANCE),
                IncidentStatus.REPORTED,
                t3,
                Optional.empty()
        )), t3);

        // Unit should now be assigned to new incident
        var snapshot2 = controller.snapshot(t3);
        var unit2 = snapshot2.units().stream()
                .filter(u -> u.id().equals(unitId))
                .findFirst().orElseThrow();
        assertEquals(UnitStatus.EN_ROUTE, unit2.status(),
                "Repositioning unit should be reassigned to new incident");
        assertTrue(unit2.assignedIncidentId().isPresent());
        assertEquals(incident2, unit2.assignedIncidentId().get());
    }
}
