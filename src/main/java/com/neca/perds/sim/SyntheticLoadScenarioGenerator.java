package com.neca.perds.sim;

import com.neca.perds.graph.Edge;
import com.neca.perds.graph.EdgeStatus;
import com.neca.perds.graph.EdgeWeights;
import com.neca.perds.graph.GraphReadView;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;

public final class SyntheticLoadScenarioGenerator {
    private static final Duration CRITICAL_SERVICE_TIME = Duration.ofMinutes(45);

    public List<TimedEvent> generate(GraphReadView graph, Instant start, SyntheticLoadConfig config, long seed) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(config, "config");

        List<NodeId> candidateNodes = candidateNodes(graph);
        if (candidateNodes.isEmpty()) {
            throw new IllegalArgumentException("graph must contain at least one node");
        }

        List<Edge> openEdges = openEdges(graph, candidateNodes);
        SplittableRandom random = new SplittableRandom(seed);

        List<TimedEvent> events = new ArrayList<>();

        List<UnitId> unitIds = new ArrayList<>();
        for (int i = 0; i < config.unitCount(); i++) {
            UnitId unitId = new UnitId("U" + (i + 1));
            unitIds.add(unitId);

            NodeId nodeId = candidateNodes.get(i % candidateNodes.size());
            ResponseUnit unit = new ResponseUnit(
                    unitId,
                    UnitType.AMBULANCE,
                    UnitStatus.AVAILABLE,
                    nodeId,
                    Optional.empty(),
                    Optional.empty()
            );
            events.add(new TimedEvent(start, new SystemCommand.RegisterUnitCommand(unit)));
        }

        Duration duration = config.duration();
        Instant endExclusive = start.plus(duration);
        Instant latestReportAt = endExclusive.minus(CRITICAL_SERVICE_TIME);
        if (latestReportAt.isBefore(start)) {
            latestReportAt = start;
        }

        long reportWindowSeconds = Math.max(0, Duration.between(start, latestReportAt).toSeconds());
        for (int i = 0; i < config.incidentCount(); i++) {
            long offsetSeconds = reportWindowSeconds == 0 ? 0 : random.nextLong(reportWindowSeconds + 1);
            Instant reportedAt = start.plusSeconds(offsetSeconds);

            NodeId nodeId = candidateNodes.get(random.nextInt(candidateNodes.size()));
            IncidentSeverity severity = chooseSeverity(random);
            Duration serviceTime = serviceTime(severity);

            IncidentId incidentId = new IncidentId("I" + (i + 1));
            Incident incident = new Incident(
                    incidentId,
                    nodeId,
                    severity,
                    Set.of(UnitType.AMBULANCE),
                    IncidentStatus.REPORTED,
                    reportedAt,
                    Optional.empty()
            );

            events.add(new TimedEvent(reportedAt, new SystemCommand.ReportIncidentCommand(incident)));
            events.add(new TimedEvent(reportedAt.plus(serviceTime), new SystemCommand.ResolveIncidentCommand(incidentId)));
        }

        if (!config.prepositionInterval().isZero() && !config.prepositionHorizon().isZero()) {
            for (Instant t = start.plus(config.prepositionInterval()); t.isBefore(endExclusive); t = t.plus(config.prepositionInterval())) {
                events.add(new TimedEvent(t, new SystemCommand.PrepositionUnitsCommand(config.prepositionHorizon())));
            }
        }

        long durationSeconds = Math.max(1, duration.toSeconds());
        for (int i = 0; i < config.congestionEventCount(); i++) {
            if (openEdges.isEmpty()) {
                break;
            }
            Edge edge = openEdges.get(random.nextInt(openEdges.size()));
            long offsetSeconds = random.nextLong(durationSeconds);
            Instant at = start.plusSeconds(offsetSeconds);

            double factor = random.nextDouble() < 0.5 ? 0.5 : 2.0;
            long baseSeconds = Math.max(1, edge.weights().travelTime().toSeconds());
            long updatedSeconds = Math.max(1, Math.round(baseSeconds * factor));
            EdgeWeights updatedWeights = new EdgeWeights(edge.weights().distanceKm(), Duration.ofSeconds(updatedSeconds), edge.weights().resourceAvailability());

            events.add(new TimedEvent(at, new SystemCommand.UpdateEdgeCommand(edge.from(), edge.to(), updatedWeights, EdgeStatus.OPEN)));
        }

        long outageWindowSeconds = Math.max(0, durationSeconds - Math.max(0, config.unitOutageDuration().toSeconds()));
        for (int i = 0; i < config.unitOutageCount(); i++) {
            if (unitIds.isEmpty() || outageWindowSeconds == 0) {
                break;
            }
            UnitId unitId = unitIds.get(random.nextInt(unitIds.size()));
            Instant at = start.plusSeconds(random.nextLong(outageWindowSeconds));

            events.add(new TimedEvent(at, new SystemCommand.SetUnitStatusCommand(unitId, UnitStatus.UNAVAILABLE)));
            events.add(new TimedEvent(at.plus(config.unitOutageDuration()), new SystemCommand.SetUnitStatusCommand(unitId, UnitStatus.AVAILABLE)));
        }

        return normalizeTimes(events);
    }

    private static List<NodeId> candidateNodes(GraphReadView graph) {
        List<NodeId> cityLike = new ArrayList<>();
        for (NodeId nodeId : graph.nodeIds()) {
            Optional<Node> node = graph.getNode(nodeId);
            if (node.isPresent() && (node.get().type() == NodeType.CITY || node.get().type() == NodeType.DISPATCH_CENTRE)) {
                cityLike.add(nodeId);
            }
        }
        cityLike.sort(Comparator.comparing(NodeId::value));
        if (!cityLike.isEmpty()) {
            return List.copyOf(cityLike);
        }

        List<NodeId> all = new ArrayList<>(graph.nodeIds());
        all.sort(Comparator.comparing(NodeId::value));
        return List.copyOf(all);
    }

    private static List<Edge> openEdges(GraphReadView graph, List<NodeId> nodeIds) {
        List<Edge> edges = new ArrayList<>();
        for (NodeId from : nodeIds) {
            for (Edge edge : graph.outgoingEdges(from)) {
                if (edge.status() != EdgeStatus.OPEN) {
                    continue;
                }
                edges.add(edge);
            }
        }
        edges.sort(Comparator
                .comparing((Edge e) -> e.from().value())
                .thenComparing(e -> e.to().value()));
        return List.copyOf(edges);
    }

    private static IncidentSeverity chooseSeverity(SplittableRandom random) {
        double r = random.nextDouble();
        if (r < 0.25) {
            return IncidentSeverity.LOW;
        }
        if (r < 0.7) {
            return IncidentSeverity.MEDIUM;
        }
        if (r < 0.9) {
            return IncidentSeverity.HIGH;
        }
        return IncidentSeverity.CRITICAL;
    }

    private static Duration serviceTime(IncidentSeverity severity) {
        return switch (severity) {
            case LOW -> Duration.ofMinutes(10);
            case MEDIUM -> Duration.ofMinutes(20);
            case HIGH -> Duration.ofMinutes(30);
            case CRITICAL -> CRITICAL_SERVICE_TIME;
        };
    }

    private static List<TimedEvent> normalizeTimes(List<TimedEvent> events) {
        List<TimedEvent> sorted = new ArrayList<>(events);
        sorted.sort(Comparator
                .comparing(TimedEvent::time)
                .thenComparing(e -> commandPriority(e.command()))
                .thenComparing(e -> e.command().toString()));

        List<TimedEvent> normalized = new ArrayList<>(sorted.size());
        Instant last = null;
        for (TimedEvent event : sorted) {
            Instant time = event.time();
            if (last != null && !time.isAfter(last)) {
                time = last.plusMillis(1);
            }
            normalized.add(new TimedEvent(time, event.command()));
            last = time;
        }
        return List.copyOf(normalized);
    }

    private static int commandPriority(SystemCommand command) {
        return switch (command) {
            case SystemCommand.RegisterUnitCommand ignored -> 0;
            case SystemCommand.ReportIncidentCommand ignored -> 1;
            case SystemCommand.UpdateEdgeCommand ignored -> 2;
            case SystemCommand.SetUnitStatusCommand ignored -> 3;
            case SystemCommand.PrepositionUnitsCommand ignored -> 4;
            case SystemCommand.ResolveIncidentCommand ignored -> 5;
            default -> 6;
        };
    }
}

