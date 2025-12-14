package com.neca.perds.io;

import com.neca.perds.graph.EdgeStatus;
import com.neca.perds.model.IncidentId;
import com.neca.perds.model.UnitId;
import com.neca.perds.model.UnitStatus;
import com.neca.perds.sim.SystemCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class CsvScenarioLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsAndSortsEvents() throws Exception {
        Path eventsCsv = tempDir.resolve("events.csv");

        Files.writeString(eventsCsv, """
                time,command,arg1,arg2,arg3,arg4,arg5,arg6
                2025-01-01T00:02:00Z,RESOLVE_INCIDENT,I1
                2025-01-01T00:01:00Z,SET_UNIT_STATUS,U1,UNAVAILABLE
                2025-01-01T00:00:00Z,REPORT_INCIDENT,I1,C,HIGH,AMBULANCE
                2025-01-01T00:03:00Z,UPDATE_EDGE,A,B,5,600,1,CLOSED
                2025-01-01T00:04:00Z,PREPOSITION,1800
                """);

        var events = new CsvScenarioLoader().load(eventsCsv);
        assertEquals(5, events.size());
        assertEquals(Instant.parse("2025-01-01T00:00:00Z"), events.getFirst().time());
        assertEquals(Instant.parse("2025-01-01T00:04:00Z"), events.getLast().time());

        var report = assertInstanceOf(SystemCommand.ReportIncidentCommand.class, events.getFirst().command());
        assertEquals(new IncidentId("I1"), report.incident().id());

        var status = assertInstanceOf(SystemCommand.SetUnitStatusCommand.class, events.get(1).command());
        assertEquals(new UnitId("U1"), status.unitId());
        assertEquals(UnitStatus.UNAVAILABLE, status.status());

        var update = assertInstanceOf(SystemCommand.UpdateEdgeCommand.class, events.get(3).command());
        assertEquals(EdgeStatus.CLOSED, update.status());

        var preposition = assertInstanceOf(SystemCommand.PrepositionUnitsCommand.class, events.getLast().command());
        assertEquals(Duration.ofSeconds(1800), preposition.horizon());
    }
}
