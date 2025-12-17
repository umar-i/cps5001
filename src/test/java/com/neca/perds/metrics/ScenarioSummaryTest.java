package com.neca.perds.metrics;

import com.neca.perds.dispatch.DispatchCommand;
import com.neca.perds.dispatch.DispatchDecision;
import com.neca.perds.dispatch.DispatchRationale;
import com.neca.perds.graph.AdjacencyMapGraph;
import com.neca.perds.model.Assignment;
import com.neca.perds.model.Incident;
import com.neca.perds.model.IncidentId;
import com.neca.perds.model.IncidentSeverity;
import com.neca.perds.model.IncidentStatus;
import com.neca.perds.model.NodeId;
import com.neca.perds.model.ResponseUnit;
import com.neca.perds.model.UnitId;
import com.neca.perds.model.UnitStatus;
import com.neca.perds.model.UnitType;
import com.neca.perds.routing.Route;
import com.neca.perds.system.SystemSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ScenarioSummaryTest {
    @Test
    void summarizesKeyStatsAndPercentiles() {
        Instant t0 = Instant.parse("2025-01-01T00:00:00Z");
        var metrics = new InMemoryMetricsCollector();

        metrics.recordDispatchComputation(t0, Duration.ofMillis(10), 1, 1);
        metrics.recordDispatchComputation(t0.plusSeconds(1), Duration.ofMillis(30), 1, 1);
        metrics.recordDispatchComputation(t0.plusSeconds(2), Duration.ofMillis(20), 1, 1);

        IncidentId i1 = new IncidentId("I1");
        IncidentId i2 = new IncidentId("I2");
        UnitId u1 = new UnitId("U1");

        metrics.recordDispatchDecision(t0.plusSeconds(10), new DispatchDecision(
                new Assignment(i1, u1, routeSeconds("A", "B", 100), t0.plusSeconds(10)),
                new DispatchRationale(0.0, Map.of())
        ));
        metrics.recordDispatchDecision(t0.plusSeconds(20), new DispatchDecision(
                new Assignment(i2, u1, routeSeconds("A", "C", 200), t0.plusSeconds(20)),
                new DispatchRationale(0.0, Map.of())
        ));

        metrics.recordDispatchCommandApplied(t0, new DispatchCommand.AssignUnitCommand(i1, u1, routeSeconds("A", "B", 100), new DispatchRationale(0.0, Map.of())));
        metrics.recordDispatchCommandApplied(t0, new DispatchCommand.RerouteUnitCommand(u1, routeSeconds("A", "D", 50), "reroute"));
        metrics.recordDispatchCommandApplied(t0, new DispatchCommand.CancelAssignmentCommand(i2, "cancel"));

        var graph = new AdjacencyMapGraph();
        var snapshot = new SystemSnapshot(
                graph,
                t0,
                List.of(new ResponseUnit(u1, UnitType.AMBULANCE, UnitStatus.AVAILABLE, new NodeId("A"), Optional.empty(), Optional.empty())),
                List.of(),
                List.of(
                        new Incident(i1, new NodeId("A"), IncidentSeverity.LOW, Set.of(UnitType.AMBULANCE), IncidentStatus.RESOLVED, t0, Optional.of(t0.plusSeconds(60))),
                        new Incident(i2, new NodeId("A"), IncidentSeverity.LOW, Set.of(UnitType.AMBULANCE), IncidentStatus.QUEUED, t0, Optional.empty())
                ),
                List.of()
        );

        ScenarioSummary summary = ScenarioSummary.from(snapshot, metrics);

        assertEquals(2, summary.incidentsTotal());
        assertEquals(1, summary.incidentsResolved());
        assertEquals(1, summary.incidentsQueued());
        assertEquals(1, summary.unitsTotal());

        assertEquals(2, summary.decisions());
        assertEquals(1, summary.assignCommands());
        assertEquals(1, summary.rerouteCommands());
        assertEquals(1, summary.cancelCommands());

        assertEquals(20_000.0, summary.computeAvgMicros(), 1e-9);
        assertEquals(30_000, summary.computeP95Micros());
        assertEquals(30_000, summary.computeMaxMicros());

        assertEquals(150.0, summary.etaAvgSeconds(), 1e-9);
        assertEquals(200, summary.etaP95Seconds());

        assertEquals(15.0, summary.waitAvgSeconds(), 1e-9);
        assertEquals(20, summary.waitP95Seconds());
    }

    private static Route routeSeconds(String start, String end, long seconds) {
        return new Route(List.of(new NodeId(start), new NodeId(end)), seconds, 0.0, Duration.ofSeconds(seconds), 1L);
    }
}
