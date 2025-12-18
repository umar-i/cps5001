package com.neca.perds.app;

import com.neca.perds.dispatch.DefaultDispatchEngine;
import com.neca.perds.dispatch.DispatchCommand;
import com.neca.perds.dispatch.NearestAvailableUnitPolicy;
import com.neca.perds.dispatch.SeverityThenOldestPrioritizer;
import com.neca.perds.io.CsvGraphLoader;
import com.neca.perds.io.CsvScenarioLoader;
import com.neca.perds.metrics.InMemoryMetricsCollector;
import com.neca.perds.metrics.ScenarioSummary;
import com.neca.perds.model.IncidentId;
import com.neca.perds.model.UnitId;
import com.neca.perds.model.UnitStatus;
import com.neca.perds.prediction.NoOpDemandPredictor;
import com.neca.perds.prediction.NoOpPrepositioningStrategy;
import com.neca.perds.sim.SimulationEngine;
import com.neca.perds.sim.TimedEvent;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CsvScenarioEndToEndTest {
    @Test
    void runsMiniScenarioFromCsv_andResolvesAllIncidents() throws Exception {
        Path nodesCsv = scenarioPath("mini-nodes.csv");
        Path edgesCsv = scenarioPath("mini-edges.csv");
        Path eventsCsv = scenarioPath("mini-events.csv");

        var graph = new CsvGraphLoader().load(nodesCsv, edgesCsv);
        List<TimedEvent> events = new CsvScenarioLoader().load(eventsCsv);

        var metrics = new InMemoryMetricsCollector();
        var controller = new PerdsController(
                graph,
                new DefaultDispatchEngine(new SeverityThenOldestPrioritizer(), new NearestAvailableUnitPolicy()),
                new NoOpDemandPredictor(),
                new NoOpPrepositioningStrategy(),
                metrics
        );

        var engine = new SimulationEngine();
        engine.scheduleAll(events);

        Instant untilExclusive = events.getLast().time().plusSeconds(1);
        List<TimedEvent> executed = engine.runUntil(controller, untilExclusive);
        assertEquals(events.size(), executed.size());

        var snapshot = controller.snapshot(untilExclusive);
        ScenarioSummary summary = ScenarioSummary.from(snapshot, metrics);

        assertEquals(2, summary.incidentsTotal());
        assertEquals(2, summary.incidentsResolved());
        assertTrue(snapshot.assignments().isEmpty());

        assertReallocated(metrics, new IncidentId("I1"), new UnitId("U2"), new UnitId("U1"));
        assertAssigned(metrics, new IncidentId("I2"), new UnitId("U1"));

        var u1 = snapshot.units().stream().filter(u -> u.id().equals(new UnitId("U1"))).findFirst().orElseThrow();
        assertEquals(UnitStatus.AVAILABLE, u1.status());
        assertTrue(u1.assignedIncidentId().isEmpty());

        var u2 = snapshot.units().stream().filter(u -> u.id().equals(new UnitId("U2"))).findFirst().orElseThrow();
        assertEquals(UnitStatus.UNAVAILABLE, u2.status());
        assertTrue(u2.assignedIncidentId().isEmpty());
    }

    @Test
    void runsGridScenarioFromCsv_andReroutesAfterEdgeUpdate() throws Exception {
        Path nodesCsv = scenarioPath("grid-4x4-nodes.csv");
        Path edgesCsv = scenarioPath("grid-4x4-edges.csv");
        Path eventsCsv = scenarioPath("grid-4x4-events.csv");

        var graph = new CsvGraphLoader().load(nodesCsv, edgesCsv);
        List<TimedEvent> events = new CsvScenarioLoader().load(eventsCsv);

        var metrics = new InMemoryMetricsCollector();
        var controller = new PerdsController(
                graph,
                new DefaultDispatchEngine(new SeverityThenOldestPrioritizer(), new NearestAvailableUnitPolicy()),
                new NoOpDemandPredictor(),
                new NoOpPrepositioningStrategy(),
                metrics
        );

        var engine = new SimulationEngine();
        engine.scheduleAll(events);

        Instant untilExclusive = events.getLast().time().plusSeconds(1);
        List<TimedEvent> executed = engine.runUntil(controller, untilExclusive);
        assertEquals(events.size(), executed.size());

        var snapshot = controller.snapshot(untilExclusive);
        ScenarioSummary summary = ScenarioSummary.from(snapshot, metrics);

        assertEquals(6, summary.incidentsTotal());
        assertEquals(6, summary.incidentsResolved());
        assertTrue(snapshot.assignments().isEmpty());

        Optional<InMemoryMetricsCollector.DispatchCommandAppliedRecord> rerouteRecord = metrics.commandsApplied().stream()
                .filter(r -> r.command() instanceof DispatchCommand.RerouteUnitCommand)
                .findFirst();
        assertTrue(rerouteRecord.isPresent(), "Expected at least one reroute after UPDATE_EDGE");

        assertEquals(Instant.parse("2025-01-01T00:02:00Z"), rerouteRecord.get().at());

        DispatchCommand.RerouteUnitCommand reroute = (DispatchCommand.RerouteUnitCommand) rerouteRecord.get().command();
        assertFalse(routeContainsEdge(reroute.newRoute().nodes(), "N01", "N02"), "Rerouted path should avoid congested edge N01->N02");
    }

    private static Path scenarioPath(String fileName) {
        Path path = Path.of("data", "scenarios", fileName);
        assertTrue(Files.exists(path), "Missing dataset file: " + path);
        return path;
    }

    private static void assertReallocated(InMemoryMetricsCollector metrics, IncidentId incidentId, UnitId firstUnit, UnitId secondUnit) {
        Optional<Instant> firstAssignedAt = metrics.decisions().stream()
                .filter(r -> r.decision().assignment().incidentId().equals(incidentId))
                .filter(r -> r.decision().assignment().unitId().equals(firstUnit))
                .map(InMemoryMetricsCollector.DispatchDecisionRecord::at)
                .findFirst();
        Optional<Instant> secondAssignedAt = metrics.decisions().stream()
                .filter(r -> r.decision().assignment().incidentId().equals(incidentId))
                .filter(r -> r.decision().assignment().unitId().equals(secondUnit))
                .map(InMemoryMetricsCollector.DispatchDecisionRecord::at)
                .findFirst();

        assertTrue(firstAssignedAt.isPresent(), "Expected assignment of " + firstUnit + " to " + incidentId);
        assertTrue(secondAssignedAt.isPresent(), "Expected assignment of " + secondUnit + " to " + incidentId);
        assertTrue(firstAssignedAt.get().isBefore(secondAssignedAt.get()), "Expected reassignment ordering: " + firstUnit + " then " + secondUnit);
    }

    private static void assertAssigned(InMemoryMetricsCollector metrics, IncidentId incidentId, UnitId unitId) {
        boolean exists = metrics.decisions().stream()
                .anyMatch(r -> r.decision().assignment().incidentId().equals(incidentId)
                        && r.decision().assignment().unitId().equals(unitId));
        assertTrue(exists, "Expected assignment of " + unitId + " to " + incidentId);
    }

    private static boolean routeContainsEdge(List<com.neca.perds.model.NodeId> nodes, String from, String to) {
        var fromId = new com.neca.perds.model.NodeId(from);
        var toId = new com.neca.perds.model.NodeId(to);
        for (int i = 0; i < nodes.size() - 1; i++) {
            if (nodes.get(i).equals(fromId) && nodes.get(i + 1).equals(toId)) {
                return true;
            }
        }
        return false;
    }
}

