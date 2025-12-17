package com.neca.perds.cli;

import com.neca.perds.app.PerdsController;
import com.neca.perds.dispatch.DefaultDispatchEngine;
import com.neca.perds.dispatch.MultiSourceNearestAvailableUnitPolicy;
import com.neca.perds.dispatch.SeverityThenOldestPrioritizer;
import com.neca.perds.graph.AdjacencyMapGraph;
import com.neca.perds.graph.Edge;
import com.neca.perds.graph.EdgeStatus;
import com.neca.perds.graph.EdgeWeights;
import com.neca.perds.io.CsvGraphLoader;
import com.neca.perds.io.CsvScenarioLoader;
import com.neca.perds.metrics.CsvMetricsExporter;
import com.neca.perds.metrics.InMemoryMetricsCollector;
import com.neca.perds.metrics.ScenarioSummary;
import com.neca.perds.model.Incident;
import com.neca.perds.model.IncidentId;
import com.neca.perds.model.IncidentSeverity;
import com.neca.perds.model.IncidentStatus;
import com.neca.perds.model.IncidentId;
import com.neca.perds.model.Node;
import com.neca.perds.model.NodeId;
import com.neca.perds.model.NodeType;
import com.neca.perds.model.ResponseUnit;
import com.neca.perds.model.UnitId;
import com.neca.perds.model.UnitStatus;
import com.neca.perds.model.UnitType;
import com.neca.perds.prediction.AdaptiveEnsembleDemandPredictor;
import com.neca.perds.prediction.MultiHotspotPrepositioningStrategy;
import com.neca.perds.prediction.NoOpPrepositioningStrategy;
import com.neca.perds.prediction.SlidingWindowDemandPredictor;
import com.neca.perds.sim.SimulationEngine;
import com.neca.perds.sim.SystemCommand;
import com.neca.perds.sim.SyntheticLoadConfig;
import com.neca.perds.sim.SyntheticLoadScenarioGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Supplier;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("demo")) {
            runDemo();
            return;
        }
        if (args[0].equalsIgnoreCase("scenario")) {
            runScenario(args);
            return;
        }
        if (args[0].equalsIgnoreCase("evaluate")) {
            runEvaluation(args);
            return;
        }
        if (args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("--help") || args[0].equalsIgnoreCase("-h")) {
            printUsage();
            return;
        }

        System.err.println("Unknown command: " + args[0]);
        printUsage();
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  mvn -q -DskipTests package && java -jar target/perds-0.1.0-SNAPSHOT.jar demo");
        System.out.println("  java -jar target/perds-0.1.0-SNAPSHOT.jar scenario <nodes.csv> <edges.csv> <events.csv> [outDir]");
        System.out.println("  java -jar target/perds-0.1.0-SNAPSHOT.jar evaluate <nodes.csv> <edges.csv> <outDir> [runs] [seed]");
    }

    private static void runDemo() {
        Instant t0 = Instant.parse("2025-01-01T00:00:00Z");

        NodeId a = new NodeId("A");
        NodeId b = new NodeId("B");
        NodeId c = new NodeId("C");

        var graph = new AdjacencyMapGraph();
        var dispatchEngine = new DefaultDispatchEngine(
                new SeverityThenOldestPrioritizer(),
                new MultiSourceNearestAvailableUnitPolicy()
        );

        var controller = new PerdsController(
                graph,
                dispatchEngine,
                new AdaptiveEnsembleDemandPredictor(),
                new MultiHotspotPrepositioningStrategy(),
                new InMemoryMetricsCollector()
        );

        controller.execute(new SystemCommand.AddNodeCommand(new Node(a, NodeType.CITY, Optional.empty(), "City A")), t0);
        controller.execute(new SystemCommand.AddNodeCommand(new Node(b, NodeType.CITY, Optional.empty(), "City B")), t0);
        controller.execute(new SystemCommand.AddNodeCommand(new Node(c, NodeType.CITY, Optional.empty(), "City C")), t0);

        EdgeWeights fast = new EdgeWeights(5.0, Duration.ofSeconds(300), 1.0);
        EdgeWeights slow = new EdgeWeights(20.0, Duration.ofSeconds(1200), 1.0);
        controller.execute(new SystemCommand.PutEdgeCommand(new Edge(a, b, fast, EdgeStatus.OPEN)), t0);
        controller.execute(new SystemCommand.PutEdgeCommand(new Edge(b, a, fast, EdgeStatus.OPEN)), t0);
        controller.execute(new SystemCommand.PutEdgeCommand(new Edge(b, c, fast, EdgeStatus.OPEN)), t0);
        controller.execute(new SystemCommand.PutEdgeCommand(new Edge(c, b, fast, EdgeStatus.OPEN)), t0);
        controller.execute(new SystemCommand.PutEdgeCommand(new Edge(a, c, slow, EdgeStatus.OPEN)), t0);
        controller.execute(new SystemCommand.PutEdgeCommand(new Edge(c, a, slow, EdgeStatus.OPEN)), t0);

        controller.execute(new SystemCommand.RegisterUnitCommand(new ResponseUnit(
                new UnitId("U1"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                a,
                Optional.empty(),
                Optional.empty()
        )), t0);
        controller.execute(new SystemCommand.RegisterUnitCommand(new ResponseUnit(
                new UnitId("U2"),
                UnitType.AMBULANCE,
                UnitStatus.AVAILABLE,
                b,
                Optional.empty(),
                Optional.empty()
        )), t0);

        Incident incident = new Incident(
                new IncidentId("I1"),
                c,
                IncidentSeverity.HIGH,
                Set.of(UnitType.AMBULANCE),
                IncidentStatus.REPORTED,
                t0,
                Optional.empty()
        );
        controller.execute(new SystemCommand.ReportIncidentCommand(incident), t0);

        var snapshot = controller.snapshot(t0);
        System.out.println("Assignments:");
        snapshot.assignments().forEach(a1 -> System.out.println(
                "  incident=" + a1.incidentId()
                        + " unit=" + a1.unitId()
                        + " route=" + a1.route().nodes()
                        + " eta=" + a1.route().totalTravelTime()
        ));
    }

    private static void runScenario(String[] args) {
        if (args.length < 4) {
            printUsage();
            return;
        }

        Path nodesCsv = Path.of(args[1]);
        Path edgesCsv = Path.of(args[2]);
        Path eventsCsv = Path.of(args[3]);
        Path outDir = args.length >= 5 ? Path.of(args[4]) : null;

        InMemoryMetricsCollector metrics = new InMemoryMetricsCollector();
        var dispatchEngine = new DefaultDispatchEngine(
                new SeverityThenOldestPrioritizer(),
                new MultiSourceNearestAvailableUnitPolicy()
        );

        try {
            var graph = new CsvGraphLoader().load(nodesCsv, edgesCsv);
            var demandPredictor = new AdaptiveEnsembleDemandPredictor();
            var prepositioning = new MultiHotspotPrepositioningStrategy();
            var controller = new PerdsController(graph, dispatchEngine, demandPredictor, prepositioning, metrics);

            var events = new CsvScenarioLoader().load(eventsCsv);
            var engine = new SimulationEngine();
            engine.scheduleAll(events);

            Instant untilExclusive = events.isEmpty()
                    ? Instant.EPOCH
                    : events.getLast().time().plusSeconds(1);
            var executed = engine.runUntil(controller, untilExclusive);

            if (outDir != null) {
                new CsvMetricsExporter(metrics).exportTo(outDir);
                System.out.println("Exported metrics to: " + outDir.toAbsolutePath());
            }

            var snapshot = controller.snapshot(untilExclusive);
            long resolved = snapshot.incidents().stream().filter(i -> i.status() == IncidentStatus.RESOLVED).count();
            long queued = snapshot.incidents().stream().filter(i -> i.status() == IncidentStatus.QUEUED).count();
            System.out.println("Executed events: " + executed.size());
            System.out.println("Incidents: total=" + snapshot.incidents().size() + " resolved=" + resolved + " queued=" + queued);
            System.out.println("Assignments: total=" + snapshot.assignments().size());
            printSummary(snapshot, metrics, executed);
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
        } catch (RuntimeException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private record EvaluationVariant(
            String name,
            Supplier<com.neca.perds.prediction.DemandPredictor> predictorFactory,
            Supplier<com.neca.perds.prediction.PrepositioningStrategy> prepositioningFactory
    ) {
        private EvaluationVariant {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            if (predictorFactory == null) {
                throw new IllegalArgumentException("predictorFactory must not be null");
            }
            if (prepositioningFactory == null) {
                throw new IllegalArgumentException("prepositioningFactory must not be null");
            }
        }
    }

    private record EvaluationRow(
            int run,
            long seed,
            String variant,
            ScenarioSummary summary,
            String predictorWeights
    ) {
        private EvaluationRow {
            if (run <= 0) {
                throw new IllegalArgumentException("run must be >= 1");
            }
            if (variant == null || variant.isBlank()) {
                throw new IllegalArgumentException("variant must not be blank");
            }
            if (summary == null) {
                throw new IllegalArgumentException("summary must not be null");
            }
        }
    }

    private static void runEvaluation(String[] args) {
        if (args.length < 4) {
            printUsage();
            return;
        }

        Path nodesCsv = Path.of(args[1]);
        Path edgesCsv = Path.of(args[2]);
        Path outDir = Path.of(args[3]);

        int runs = args.length >= 5 ? Integer.parseInt(args[4]) : 10;
        long seed = args.length >= 6 ? Long.parseLong(args[5]) : 1L;
        if (runs <= 0) {
            System.err.println("runs must be > 0");
            return;
        }

        List<EvaluationVariant> variants = List.of(
                new EvaluationVariant("no_preposition", AdaptiveEnsembleDemandPredictor::new, NoOpPrepositioningStrategy::new),
                new EvaluationVariant("sliding_preposition", SlidingWindowDemandPredictor::new, MultiHotspotPrepositioningStrategy::new),
                new EvaluationVariant("adaptive_preposition", AdaptiveEnsembleDemandPredictor::new, MultiHotspotPrepositioningStrategy::new)
        );

        try {
            Files.createDirectories(outDir);

            var sizingGraph = new CsvGraphLoader().load(nodesCsv, edgesCsv);
            SyntheticLoadConfig config = defaultLoadConfig(sizingGraph.nodeIds().size());
            Instant start = Instant.parse("2025-01-01T00:00:00Z");

            var generator = new SyntheticLoadScenarioGenerator();
            List<EvaluationRow> rows = new ArrayList<>();
            for (int run = 1; run <= runs; run++) {
                long runSeed = seed + (run - 1L);
                for (EvaluationVariant variant : variants) {
                    rows.add(runOneEvaluation(nodesCsv, edgesCsv, outDir, generator, config, start, run, runSeed, variant));
                }
            }

            writeEvaluationFiles(outDir, config, rows);
            System.out.println("Wrote evaluation output to: " + outDir.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
        } catch (RuntimeException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static SyntheticLoadConfig defaultLoadConfig(int nodeCount) {
        int unitCount = Math.min(40, Math.max(10, nodeCount / 4));
        int incidentCount = unitCount * 20;
        int congestionEvents = Math.max(20, nodeCount * 3);
        int unitOutages = Math.max(0, unitCount / 4);

        return new SyntheticLoadConfig(
                Duration.ofHours(2),
                unitCount,
                incidentCount,
                Duration.ofMinutes(15),
                Duration.ofMinutes(30),
                congestionEvents,
                unitOutages,
                Duration.ofMinutes(10)
        );
    }

    private static EvaluationRow runOneEvaluation(
            Path nodesCsv,
            Path edgesCsv,
            Path outDir,
            SyntheticLoadScenarioGenerator generator,
            SyntheticLoadConfig config,
            Instant start,
            int run,
            long seed,
            EvaluationVariant variant
    ) throws IOException {
        var graph = new CsvGraphLoader().load(nodesCsv, edgesCsv);
        var dispatchEngine = new DefaultDispatchEngine(
                new SeverityThenOldestPrioritizer(),
                new MultiSourceNearestAvailableUnitPolicy()
        );

        var metrics = new InMemoryMetricsCollector();
        var predictor = variant.predictorFactory().get();
        var prepositioning = variant.prepositioningFactory().get();
        var controller = new PerdsController(graph, dispatchEngine, predictor, prepositioning, metrics);

        var events = generator.generate(graph, start, config, seed);
        var engine = new SimulationEngine();
        engine.scheduleAll(events);

        Instant untilExclusive = events.isEmpty() ? start : events.getLast().time().plusMillis(1);
        var executed = engine.runUntil(controller, untilExclusive);

        new CsvMetricsExporter(metrics).exportTo(outDir.resolve("runs").resolve(variant.name()).resolve("run-" + run));

        var snapshot = controller.snapshot(untilExclusive);
        ScenarioSummary summary = ScenarioSummary.from(snapshot, metrics);

        String weights = predictor instanceof AdaptiveEnsembleDemandPredictor adaptive
                ? flattenWeights(adaptive.weights())
                : "";

        long prepositionCommands = executed.stream().filter(e -> e.command() instanceof SystemCommand.PrepositionUnitsCommand).count();
        if (prepositionCommands > 0 && prepositioning instanceof NoOpPrepositioningStrategy) {
            weights = weights.isBlank() ? "preposition=no-op" : weights + "|preposition=no-op";
        }

        return new EvaluationRow(run, seed, variant.name(), summary, weights);
    }

    private static void writeEvaluationFiles(Path outDir, SyntheticLoadConfig config, List<EvaluationRow> rows) throws IOException {
        Path summaryCsv = outDir.resolve("evaluation_summary.csv");
        Path aggregateCsv = outDir.resolve("evaluation_aggregate.csv");
        Path aggregateMd = outDir.resolve("evaluation_aggregate.md");

        try (var writer = Files.newBufferedWriter(summaryCsv)) {
            writer.write("run,seed,variant,incidentsTotal,incidentsResolved,incidentsQueued,unitsTotal,decisions,assignCommands,rerouteCommands,cancelCommands,computeAvgMillis,computeP95Millis,computeMaxMillis,etaAvgSeconds,etaP95Seconds,waitAvgSeconds,waitP95Seconds,predictorWeights");
            writer.newLine();
            for (EvaluationRow row : rows) {
                ScenarioSummary s = row.summary();
                writer.write(csvRow(
                        String.valueOf(row.run()),
                        String.valueOf(row.seed()),
                        row.variant(),
                        String.valueOf(s.incidentsTotal()),
                        String.valueOf(s.incidentsResolved()),
                        String.valueOf(s.incidentsQueued()),
                        String.valueOf(s.unitsTotal()),
                        String.valueOf(s.decisions()),
                        String.valueOf(s.assignCommands()),
                        String.valueOf(s.rerouteCommands()),
                        String.valueOf(s.cancelCommands()),
                        String.valueOf(s.computeAvgMillis()),
                        String.valueOf(s.computeP95Millis()),
                        String.valueOf(s.computeMaxMillis()),
                        String.valueOf(s.etaAvgSeconds()),
                        String.valueOf(s.etaP95Seconds()),
                        String.valueOf(s.waitAvgSeconds()),
                        String.valueOf(s.waitP95Seconds()),
                        row.predictorWeights()
                ));
                writer.newLine();
            }
        }

        Map<String, List<EvaluationRow>> byVariant = new HashMap<>();
        for (EvaluationRow row : rows) {
            byVariant.computeIfAbsent(row.variant(), ignored -> new ArrayList<>()).add(row);
        }

        try (var writer = Files.newBufferedWriter(aggregateCsv)) {
            writer.write("variant,runs,etaAvgSecondsMean,etaAvgSecondsP95Runs,waitAvgSecondsMean,waitAvgSecondsP95Runs,computeAvgMillisMean,cancelCommandsMean,rerouteCommandsMean");
            writer.newLine();

            for (var entry : byVariant.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                String variant = entry.getKey();
                List<EvaluationRow> variantRows = entry.getValue();

                List<Double> etaAvg = variantRows.stream().map(r -> r.summary().etaAvgSeconds()).toList();
                List<Double> waitAvg = variantRows.stream().map(r -> r.summary().waitAvgSeconds()).toList();
                List<Double> computeAvg = variantRows.stream().map(r -> r.summary().computeAvgMillis()).toList();
                List<Double> cancels = variantRows.stream().map(r -> (double) r.summary().cancelCommands()).toList();
                List<Double> reroutes = variantRows.stream().map(r -> (double) r.summary().rerouteCommands()).toList();

                writer.write(csvRow(
                        variant,
                        String.valueOf(variantRows.size()),
                        String.valueOf(mean(etaAvg)),
                        String.valueOf(p95(etaAvg)),
                        String.valueOf(mean(waitAvg)),
                        String.valueOf(p95(waitAvg)),
                        String.valueOf(mean(computeAvg)),
                        String.valueOf(mean(cancels)),
                        String.valueOf(mean(reroutes))
                ));
                writer.newLine();
            }
        }

        try (var writer = Files.newBufferedWriter(aggregateMd)) {
            writer.write("# Evaluation Aggregate");
            writer.newLine();
            writer.newLine();
            writer.write("Synthetic load config:");
            writer.newLine();
            writer.write("- duration=" + config.duration());
            writer.newLine();
            writer.write("- unitCount=" + config.unitCount());
            writer.newLine();
            writer.write("- incidentCount=" + config.incidentCount());
            writer.newLine();
            writer.write("- congestionEventCount=" + config.congestionEventCount());
            writer.newLine();
            writer.write("- unitOutageCount=" + config.unitOutageCount());
            writer.newLine();
            writer.newLine();

            writer.write("| variant | runs | etaAvgSecondsMean | etaAvgSecondsP95Runs | waitAvgSecondsMean | waitAvgSecondsP95Runs | computeAvgMillisMean | cancelCommandsMean | rerouteCommandsMean |");
            writer.newLine();
            writer.write("|---|---:|---:|---:|---:|---:|---:|---:|---:|");
            writer.newLine();

            for (var entry : byVariant.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                String variant = entry.getKey();
                List<EvaluationRow> variantRows = entry.getValue();

                List<Double> etaAvg = variantRows.stream().map(r -> r.summary().etaAvgSeconds()).toList();
                List<Double> waitAvg = variantRows.stream().map(r -> r.summary().waitAvgSeconds()).toList();
                List<Double> computeAvg = variantRows.stream().map(r -> r.summary().computeAvgMillis()).toList();
                List<Double> cancels = variantRows.stream().map(r -> (double) r.summary().cancelCommands()).toList();
                List<Double> reroutes = variantRows.stream().map(r -> (double) r.summary().rerouteCommands()).toList();

                writer.write("| " + variant
                        + " | " + variantRows.size()
                        + " | " + mean(etaAvg)
                        + " | " + p95(etaAvg)
                        + " | " + mean(waitAvg)
                        + " | " + p95(waitAvg)
                        + " | " + mean(computeAvg)
                        + " | " + mean(cancels)
                        + " | " + mean(reroutes)
                        + " |");
                writer.newLine();
            }
        }
    }

    private static double mean(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    private static double p95(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int index = (int) Math.ceil(0.95 * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    private static String flattenWeights(Map<String, Double> weights) {
        if (weights.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("|");
        for (var entry : weights.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            joiner.add(entry.getKey() + "=" + entry.getValue());
        }
        return joiner.toString();
    }

    private static String csvRow(String... fields) {
        StringJoiner joiner = new StringJoiner(",");
        for (String field : fields) {
            joiner.add(escapeCsvField(field));
        }
        return joiner.toString();
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

    private static void printSummary(com.neca.perds.system.SystemSnapshot snapshot, InMemoryMetricsCollector metrics, java.util.List<com.neca.perds.sim.TimedEvent> executed) {
        long computations = metrics.computations().size();
        long totalComputeMillis = 0;
        long maxComputeMillis = 0;
        for (var record : metrics.computations()) {
            long elapsedMillis = record.elapsed().toMillis();
            totalComputeMillis += elapsedMillis;
            maxComputeMillis = Math.max(maxComputeMillis, elapsedMillis);
        }

        long assigns = 0;
        long reroutes = 0;
        long cancels = 0;
        for (var record : metrics.commandsApplied()) {
            switch (record.command()) {
                case com.neca.perds.dispatch.DispatchCommand.AssignUnitCommand ignored -> assigns++;
                case com.neca.perds.dispatch.DispatchCommand.RerouteUnitCommand ignored -> reroutes++;
                case com.neca.perds.dispatch.DispatchCommand.CancelAssignmentCommand ignored -> cancels++;
            }
        }

        Map<IncidentId, Instant> reportedAtByIncidentId = new HashMap<>();
        for (var incident : snapshot.incidents()) {
            reportedAtByIncidentId.put(incident.id(), incident.reportedAt());
        }

        long decisions = metrics.decisions().size();
        long totalEtaSeconds = 0;
        long totalWaitSeconds = 0;
        long waitSamples = 0;
        for (var record : metrics.decisions()) {
            var assignment = record.decision().assignment();
            totalEtaSeconds += assignment.route().totalTravelTime().toSeconds();

            Instant reportedAt = reportedAtByIncidentId.get(assignment.incidentId());
            if (reportedAt != null && !record.at().isBefore(reportedAt)) {
                totalWaitSeconds += Duration.between(reportedAt, record.at()).toSeconds();
                waitSamples++;
            }
        }

        long prepositionCommands = executed.stream().filter(e -> e.command() instanceof com.neca.perds.sim.SystemCommand.PrepositionUnitsCommand).count();
        long edgeUpdates = executed.stream().filter(e -> e.command() instanceof com.neca.perds.sim.SystemCommand.UpdateEdgeCommand).count();

        System.out.println("Dispatch summary:");
        if (computations > 0) {
            System.out.println("  compute: count=" + computations
                    + " avgMs=" + (totalComputeMillis / (double) computations)
                    + " maxMs=" + maxComputeMillis);
        } else {
            System.out.println("  compute: count=0");
        }
        if (decisions > 0) {
            System.out.println("  decisions: count=" + decisions
                    + " avgEtaS=" + (totalEtaSeconds / (double) decisions)
                    + " avgWaitS=" + (waitSamples == 0 ? "n/a" : String.valueOf(totalWaitSeconds / (double) waitSamples)));
        } else {
            System.out.println("  decisions: count=0");
        }
        System.out.println("  commands: assign=" + assigns + " reroute=" + reroutes + " cancel=" + cancels);
        System.out.println("  events: preposition=" + prepositionCommands + " edgeUpdates=" + edgeUpdates);
    }
}
