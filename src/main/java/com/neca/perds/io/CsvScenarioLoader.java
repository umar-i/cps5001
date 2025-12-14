package com.neca.perds.io;

import com.neca.perds.graph.EdgeStatus;
import com.neca.perds.graph.EdgeWeights;
import com.neca.perds.model.Incident;
import com.neca.perds.model.IncidentId;
import com.neca.perds.model.IncidentSeverity;
import com.neca.perds.model.IncidentStatus;
import com.neca.perds.model.NodeId;
import com.neca.perds.model.ResponseUnit;
import com.neca.perds.model.UnitId;
import com.neca.perds.model.UnitStatus;
import com.neca.perds.model.UnitType;
import com.neca.perds.sim.SystemCommand;
import com.neca.perds.sim.TimedEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class CsvScenarioLoader {
    public List<TimedEvent> load(Path eventsCsv) throws IOException {
        Objects.requireNonNull(eventsCsv, "eventsCsv");

        List<TimedEvent> events = new ArrayList<>();
        try (var reader = Files.newBufferedReader(eventsCsv)) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (shouldSkip(line)) {
                    continue;
                }

                var fields = CsvUtils.splitLine(line);
                if (looksLikeHeader(fields)) {
                    continue;
                }
                if (fields.size() < 2) {
                    throw new IllegalArgumentException("events.csv line " + lineNo + ": expected at least 2 columns (time, command)");
                }

                Instant time = Instant.parse(fields.get(0).trim());
                String commandName = fields.get(1).trim().toUpperCase(Locale.ROOT);

                SystemCommand command = switch (commandName) {
                    case "REGISTER_UNIT" -> parseRegisterUnit(fields, lineNo);
                    case "REPORT_INCIDENT" -> parseReportIncident(fields, lineNo, time);
                    case "RESOLVE_INCIDENT" -> new SystemCommand.ResolveIncidentCommand(
                            new IncidentId(require(fields, 2, "incidentId", lineNo))
                    );
                    case "PREPOSITION", "PREPOSITION_UNITS" -> new SystemCommand.PrepositionUnitsCommand(
                            Duration.ofSeconds(parseLongOrDefault(fields, 2, 3600L))
                    );
                    case "SET_UNIT_STATUS" -> new SystemCommand.SetUnitStatusCommand(
                            new UnitId(require(fields, 2, "unitId", lineNo)),
                            UnitStatus.valueOf(require(fields, 3, "status", lineNo).toUpperCase(Locale.ROOT))
                    );
                    case "MOVE_UNIT" -> new SystemCommand.MoveUnitCommand(
                            new UnitId(require(fields, 2, "unitId", lineNo)),
                            new NodeId(require(fields, 3, "nodeId", lineNo))
                    );
                    case "UPDATE_EDGE" -> new SystemCommand.UpdateEdgeCommand(
                            new NodeId(require(fields, 2, "from", lineNo)),
                            new NodeId(require(fields, 3, "to", lineNo)),
                            new EdgeWeights(
                                    Double.parseDouble(require(fields, 4, "distanceKm", lineNo)),
                                    Duration.ofSeconds(Long.parseLong(require(fields, 5, "travelTimeSeconds", lineNo))),
                                    Double.parseDouble(require(fields, 6, "resourceAvailability", lineNo))
                            ),
                            EdgeStatus.valueOf(require(fields, 7, "status", lineNo).toUpperCase(Locale.ROOT))
                    );
                    default -> throw new IllegalArgumentException("events.csv line " + lineNo + ": unknown command '" + commandName + "'");
                };

                events.add(new TimedEvent(time, command));
            }
        }

        events.sort(TimedEvent::compareTo);
        return List.copyOf(events);
    }

    private static SystemCommand parseRegisterUnit(List<String> fields, int lineNo) {
        UnitId id = new UnitId(require(fields, 2, "unitId", lineNo));
        UnitType type = UnitType.valueOf(require(fields, 3, "type", lineNo).toUpperCase(Locale.ROOT));
        UnitStatus status = UnitStatus.valueOf(require(fields, 4, "status", lineNo).toUpperCase(Locale.ROOT));
        NodeId nodeId = new NodeId(require(fields, 5, "nodeId", lineNo));

        return new SystemCommand.RegisterUnitCommand(new ResponseUnit(
                id,
                type,
                status,
                nodeId,
                Optional.empty(),
                Optional.empty()
        ));
    }

    private static SystemCommand parseReportIncident(List<String> fields, int lineNo, Instant at) {
        IncidentId id = new IncidentId(require(fields, 2, "incidentId", lineNo));
        NodeId nodeId = new NodeId(require(fields, 3, "nodeId", lineNo));
        IncidentSeverity severity = IncidentSeverity.valueOf(require(fields, 4, "severity", lineNo).toUpperCase(Locale.ROOT));
        Set<UnitType> requiredUnitTypes = parseRequiredUnitTypes(require(fields, 5, "requiredUnitTypes", lineNo), lineNo);

        return new SystemCommand.ReportIncidentCommand(new Incident(
                id,
                nodeId,
                severity,
                requiredUnitTypes,
                IncidentStatus.REPORTED,
                at,
                Optional.empty()
        ));
    }

    private static Set<UnitType> parseRequiredUnitTypes(String raw, int lineNo) {
        String[] parts = raw.split("\\|");
        Set<UnitType> types = new HashSet<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            types.add(UnitType.valueOf(trimmed.toUpperCase(Locale.ROOT)));
        }
        if (types.isEmpty()) {
            throw new IllegalArgumentException("events.csv line " + lineNo + ": requiredUnitTypes must not be empty");
        }
        return Set.copyOf(types);
    }

    private static boolean shouldSkip(String line) {
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.startsWith("#");
    }

    private static boolean looksLikeHeader(List<String> fields) {
        if (fields.isEmpty()) {
            return false;
        }
        return fields.getFirst().trim().equalsIgnoreCase("time");
    }

    private static String require(List<String> fields, int index, String name, int lineNo) {
        if (fields.size() <= index) {
            throw new IllegalArgumentException("events.csv line " + lineNo + ": missing " + name);
        }
        String value = fields.get(index).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("events.csv line " + lineNo + ": missing " + name);
        }
        return value;
    }

    private static long parseLongOrDefault(List<String> fields, int index, long defaultValue) {
        if (fields.size() <= index) {
            return defaultValue;
        }
        String raw = fields.get(index).trim();
        if (raw.isEmpty()) {
            return defaultValue;
        }
        return Long.parseLong(raw);
    }
}
