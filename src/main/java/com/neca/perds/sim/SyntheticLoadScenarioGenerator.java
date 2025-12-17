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

        Duration duration = config.duration();
        long sequence = 0L;
        List<TimedEvent> events = new ArrayList<>();

        int hotspotCount = Math.min(3, candidateNodes.size());
        List<NodeId> hotspotA = pickDistinct(candidateNodes, random, hotspotCount);
        List<NodeId> hotspotB = pickDistinct(candidateNodes, random, hotspotCount);
        Instant hotspotShiftAt = start.plus(duration.dividedBy(2));

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
            events.add(new TimedEvent(start.plusNanos(sequence++), new SystemCommand.RegisterUnitCommand(unit)));
        }

        Instant endExclusive = start.plus(duration);
        Instant latestReportAt = endExclusive.minus(CRITICAL_SERVICE_TIME);
        if (latestReportAt.isBefore(start)) {
            latestReportAt = start;
        }

        long reportWindowSeconds = Math.max(0, Duration.between(start, latestReportAt).toSeconds());
        for (int i = 0; i < config.incidentCount(); i++) {
            long offsetSeconds = reportWindowSeconds == 0 ? 0 : random.nextLong(reportWindowSeconds + 1);
            Instant reportedAt = start.plusSeconds(offsetSeconds).plusNanos(sequence++);

            List<NodeId> hotspots = reportedAt.isBefore(hotspotShiftAt) ? hotspotA : hotspotB;
            boolean chooseHotspot = !hotspots.isEmpty() && random.nextDouble() < 0.75;
            NodeId nodeId = chooseHotspot
                    ? hotspots.get(random.nextInt(hotspots.size()))
                    : candidateNodes.get(random.nextInt(candidateNodes.size()));
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
            events.add(new TimedEvent(reportedAt.plus(serviceTime).plusNanos(sequence++), new SystemCommand.ResolveIncidentCommand(incidentId)));
        }

        if (!config.prepositionInterval().isZero() && !config.prepositionHorizon().isZero()) {
            for (Instant t = start.plus(config.prepositionInterval()); t.isBefore(endExclusive); t = t.plus(config.prepositionInterval())) {
                events.add(new TimedEvent(t.plusNanos(sequence++), new SystemCommand.PrepositionUnitsCommand(config.prepositionHorizon())));
            }
        }

        long durationSeconds = Math.max(1, duration.toSeconds());
        for (int i = 0; i < config.congestionEventCount(); i++) {
            if (openEdges.isEmpty()) {
                break;
            }
            Edge edge = openEdges.get(random.nextInt(openEdges.size()));
            long offsetSeconds = random.nextLong(durationSeconds);
            Instant at = start.plusSeconds(offsetSeconds).plusNanos(sequence++);

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
            Instant at = start.plusSeconds(random.nextLong(outageWindowSeconds)).plusNanos(sequence++);

            events.add(new TimedEvent(at, new SystemCommand.SetUnitStatusCommand(unitId, UnitStatus.UNAVAILABLE)));
            events.add(new TimedEvent(at.plus(config.unitOutageDuration()).plusNanos(sequence++), new SystemCommand.SetUnitStatusCommand(unitId, UnitStatus.AVAILABLE)));
        }

        List<TimedEvent> sorted = new ArrayList<>(events);
        sorted.sort(Comparator.comparing(TimedEvent::time));
        return List.copyOf(sorted);
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

    private static List<NodeId> pickDistinct(List<NodeId> candidates, SplittableRandom random, int count) {
        if (count <= 0 || candidates.isEmpty()) {
            return List.of();
        }
        if (count >= candidates.size()) {
            return List.copyOf(candidates);
        }

        List<NodeId> pool = new ArrayList<>(candidates);
        for (int i = pool.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            NodeId tmp = pool.get(i);
            pool.set(i, pool.get(j));
            pool.set(j, tmp);
        }
        return List.copyOf(pool.subList(0, count));
    }

    private static Duration serviceTime(IncidentSeverity severity) {
        return switch (severity) {
            case LOW -> Duration.ofMinutes(10);
            case MEDIUM -> Duration.ofMinutes(20);
            case HIGH -> Duration.ofMinutes(30);
            case CRITICAL -> CRITICAL_SERVICE_TIME;
        };
    }

}
