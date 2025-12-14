package com.neca.perds.prediction;

import com.neca.perds.model.NodeId;
import com.neca.perds.model.UnitId;
import com.neca.perds.model.ZoneId;
import com.neca.perds.system.SystemSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GreedyHotspotPrepositioningStrategy implements PrepositioningStrategy {
    private final int maxMoves;

    public GreedyHotspotPrepositioningStrategy() {
        this(1);
    }

    public GreedyHotspotPrepositioningStrategy(int maxMoves) {
        if (maxMoves < 0) {
            throw new IllegalArgumentException("maxMoves must be >= 0");
        }
        this.maxMoves = maxMoves;
    }

    @Override
    public RepositionPlan plan(SystemSnapshot snapshot, DemandForecast forecast) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(forecast, "forecast");

        if (maxMoves == 0) {
            return new RepositionPlan(List.of());
        }

        ZoneSelection selection = bestZone(forecast.expectedIncidents());
        if (selection == null) {
            return new RepositionPlan(List.of());
        }

        NodeId targetNodeId = new NodeId(selection.zoneId.value());
        if (snapshot.graph().getNode(targetNodeId).isEmpty()) {
            return new RepositionPlan(List.of());
        }

        List<RepositionMove> moves = new ArrayList<>();
        snapshot.units().stream()
                .filter(u -> u.isAvailable())
                .sorted(Comparator.comparing(u -> u.id().value()))
                .forEach(unit -> {
                    if (moves.size() >= maxMoves) {
                        return;
                    }
                    if (unit.currentNodeId().equals(targetNodeId)) {
                        return;
                    }

                    moves.add(new RepositionMove(
                            unit.id(),
                            targetNodeId,
                            "hotspot zone=" + selection.zoneId + " score=" + selection.score
                    ));
                });

        return new RepositionPlan(List.copyOf(moves));
    }

    private static ZoneSelection bestZone(Map<ZoneId, Double> expectedIncidents) {
        if (expectedIncidents.isEmpty()) {
            return null;
        }

        ZoneId bestZone = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (var entry : expectedIncidents.entrySet()) {
            ZoneId zoneId = entry.getKey();
            double score = entry.getValue();
            if (bestZone == null) {
                bestZone = zoneId;
                bestScore = score;
                continue;
            }

            int scoreComparison = Double.compare(score, bestScore);
            if (scoreComparison > 0) {
                bestZone = zoneId;
                bestScore = score;
                continue;
            }
            if (scoreComparison == 0 && zoneId.value().compareTo(bestZone.value()) < 0) {
                bestZone = zoneId;
                bestScore = score;
            }
        }

        if (bestZone == null || bestScore <= 0.0) {
            return null;
        }

        return new ZoneSelection(bestZone, bestScore);
    }

    private record ZoneSelection(ZoneId zoneId, double score) {}
}
