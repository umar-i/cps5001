package com.neca.perds.metrics;

import com.neca.perds.dispatch.DispatchCommand;
import com.neca.perds.dispatch.DispatchDecision;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

public final class CsvMetricsExporter implements MetricsExporter {
    private final MetricsCollector metricsCollector;

    public CsvMetricsExporter(MetricsCollector metricsCollector) {
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector");
    }

    @Override
    public void exportTo(Path outputDirectory) throws IOException {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Files.createDirectories(outputDirectory);

        if (!(metricsCollector instanceof InMemoryMetricsCollector inMemory)) {
            throw new IllegalArgumentException("CsvMetricsExporter currently supports InMemoryMetricsCollector only");
        }

        exportComputations(inMemory, outputDirectory.resolve("dispatch_computations.csv"));
        exportDecisions(inMemory, outputDirectory.resolve("dispatch_decisions.csv"));
        exportCommandsApplied(inMemory, outputDirectory.resolve("dispatch_commands_applied.csv"));
    }

    private static void exportComputations(InMemoryMetricsCollector inMemory, Path path) throws IOException {
        try (var writer = Files.newBufferedWriter(path)) {
            writer.write("at,elapsedMillis,incidentsConsidered,unitsConsidered");
            writer.newLine();
            for (var record : inMemory.computations()) {
                writer.write(csvRow(
                        record.at().toString(),
                        String.valueOf(record.elapsed().toMillis()),
                        String.valueOf(record.incidentsConsidered()),
                        String.valueOf(record.unitsConsidered())
                ));
                writer.newLine();
            }
        }
    }

    private static void exportDecisions(InMemoryMetricsCollector inMemory, Path path) throws IOException {
        try (var writer = Files.newBufferedWriter(path)) {
            writer.write("at,incidentId,unitId,routeNodes,totalTravelTimeSeconds,totalDistanceKm,score,components");
            writer.newLine();
            for (var record : inMemory.decisions()) {
                DispatchDecision decision = record.decision();
                var assignment = decision.assignment();
                writer.write(csvRow(
                        record.at().toString(),
                        assignment.incidentId().toString(),
                        assignment.unitId().toString(),
                        String.join("|", assignment.route().nodes().stream().map(Object::toString).toList()),
                        String.valueOf(assignment.route().totalTravelTime().toSeconds()),
                        String.valueOf(assignment.route().totalDistanceKm()),
                        String.valueOf(decision.rationale().score()),
                        flattenComponents(decision.rationale().components())
                ));
                writer.newLine();
            }
        }
    }

    private static void exportCommandsApplied(InMemoryMetricsCollector inMemory, Path path) throws IOException {
        try (var writer = Files.newBufferedWriter(path)) {
            writer.write("at,commandType,incidentId,unitId,routeNodes,totalTravelTimeSeconds,totalDistanceKm,score,details");
            writer.newLine();
            for (var record : inMemory.commandsApplied()) {
                DispatchCommand command = record.command();
                writer.write(csvRow(commandRow(record.at().toString(), command)));
                writer.newLine();
            }
        }
    }

    private static String[] commandRow(String at, DispatchCommand command) {
        return switch (command) {
            case DispatchCommand.AssignUnitCommand c -> new String[] {
                    at,
                    "ASSIGN_UNIT",
                    c.incidentId().toString(),
                    c.unitId().toString(),
                    String.join("|", c.route().nodes().stream().map(Object::toString).toList()),
                    String.valueOf(c.route().totalTravelTime().toSeconds()),
                    String.valueOf(c.route().totalDistanceKm()),
                    String.valueOf(c.rationale().score()),
                    "components=" + flattenComponents(c.rationale().components())
            };
            case DispatchCommand.RerouteUnitCommand c -> new String[] {
                    at,
                    "REROUTE_UNIT",
                    "",
                    c.unitId().toString(),
                    String.join("|", c.newRoute().nodes().stream().map(Object::toString).toList()),
                    String.valueOf(c.newRoute().totalTravelTime().toSeconds()),
                    String.valueOf(c.newRoute().totalDistanceKm()),
                    "",
                    "reason=" + c.reason()
            };
            case DispatchCommand.CancelAssignmentCommand c -> new String[] {
                    at,
                    "CANCEL_ASSIGNMENT",
                    c.incidentId().toString(),
                    "",
                    "",
                    "",
                    "",
                    "",
                    "reason=" + c.reason()
            };
        };
    }

    private static String flattenComponents(java.util.Map<String, Double> components) {
        if (components.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("|");
        for (var entry : components.entrySet()) {
            joiner.add(entry.getKey() + "=" + entry.getValue());
        }
        return joiner.toString();
    }

    private static String csvRow(String... fields) {
        return String.join(",", escapeCsvFields(List.of(fields)));
    }

    private static List<String> escapeCsvFields(List<String> fields) {
        List<String> escaped = new ArrayList<>(fields.size());
        for (String field : fields) {
            escaped.add(escapeCsvField(field));
        }
        return escaped;
    }

    private static String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        boolean needsQuotes = field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r");
        if (!needsQuotes) {
            return field;
        }
        return "\"" + field.replace("\"", "\"\"") + "\"";
    }
}
