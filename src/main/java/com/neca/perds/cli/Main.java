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
import com.neca.perds.prediction.ExponentialSmoothingDemandPredictor;
import com.neca.perds.prediction.GreedyHotspotPrepositioningStrategy;
import com.neca.perds.sim.SimulationEngine;
import com.neca.perds.sim.SystemCommand;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

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
                new ExponentialSmoothingDemandPredictor(),
                new GreedyHotspotPrepositioningStrategy(),
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
            var demandPredictor = new ExponentialSmoothingDemandPredictor();
            var prepositioning = new GreedyHotspotPrepositioningStrategy();
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
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
        } catch (RuntimeException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
