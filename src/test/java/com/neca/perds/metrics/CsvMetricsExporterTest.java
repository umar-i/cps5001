package com.neca.perds.metrics;

import com.neca.perds.dispatch.DispatchCommand;
import com.neca.perds.model.IncidentId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CsvMetricsExporterTest {
    @TempDir
    Path tempDir;

    @Test
    void exportsCsvFiles() throws Exception {
        var metrics = new InMemoryMetricsCollector();
        metrics.recordDispatchComputation(Instant.parse("2025-01-01T00:00:00Z"), Duration.ofMillis(12), 2, 3);
        metrics.recordDispatchCommandApplied(
                Instant.parse("2025-01-01T00:01:00Z"),
                new DispatchCommand.CancelAssignmentCommand(new IncidentId("I1"), "test")
        );

        new CsvMetricsExporter(metrics).exportTo(tempDir);

        Path computations = tempDir.resolve("dispatch_computations.csv");
        Path decisions = tempDir.resolve("dispatch_decisions.csv");
        Path commands = tempDir.resolve("dispatch_commands_applied.csv");

        assertTrue(Files.exists(computations));
        assertTrue(Files.exists(decisions));
        assertTrue(Files.exists(commands));

        assertEquals(2, Files.readAllLines(computations).size());
        assertEquals("at,elapsedMillis,incidentsConsidered,unitsConsidered", Files.readAllLines(computations).getFirst());
        assertEquals("at,commandType,incidentId,unitId,routeNodes,totalTravelTimeSeconds,totalDistanceKm,score,details",
                Files.readAllLines(commands).getFirst());
    }
}

