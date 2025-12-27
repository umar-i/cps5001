package com.neca.perds.prediction;

import com.neca.perds.model.NodeId;
import com.neca.perds.model.ResponseUnit;
import com.neca.perds.model.UnitId;
import com.neca.perds.model.ZoneId;
import com.neca.perds.routing.CostFunctions;
import com.neca.perds.routing.DijkstraRouter;
import com.neca.perds.routing.EdgeCostFunction;
import com.neca.perds.routing.Route;
import com.neca.perds.routing.Router;
import com.neca.perds.routing.VirtualSourceGraphView;
import com.neca.perds.system.SystemSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MultiHotspotPrepositioningStrategy implements PrepositioningStrategy {
    private final int maxMoves;
    private final int maxZones;
    private final Router router;
    private final EdgeCostFunction costFunction;

    public MultiHotspotPrepositioningStrategy() {
        this(3, 3, new DijkstraRouter(), CostFunctions.travelTimeSeconds());
    }

    public MultiHotspotPrepositioningStrategy(int maxMoves, int maxZones) {
        this(maxMoves, maxZones, new DijkstraRouter(), CostFunctions.travelTimeSeconds());
    }

    public MultiHotspotPrepositioningStrategy(int maxMoves, int maxZones, Router router, EdgeCostFunction costFunction) {
        if (maxMoves < 0) {
            throw new IllegalArgumentException("maxMoves must be >= 0");
        }
        if (maxZones <= 0) {
            throw new IllegalArgumentException("maxZones must be > 0");
        }
        this.maxMoves = maxMoves;
        this.maxZones = maxZones;
        this.router = Objects.requireNonNull(router, "router");
        this.costFunction = Objects.requireNonNull(costFunction, "costFunction");
    }

    @Override
    public RepositionPlan plan(SystemSnapshot snapshot, DemandForecast forecast) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(forecast, "forecast");

        if (maxMoves == 0) {
            return new RepositionPlan(List.of());
        }

        Map<NodeId, List<ResponseUnit>> availableUnitsByNodeId = new HashMap<>();
        for (ResponseUnit unit : snapshot.units()) {
            if (!unit.isAvailable()) {
                continue;
            }
            availableUnitsByNodeId
                    .computeIfAbsent(unit.currentNodeId(), ignored -> new ArrayList<>())
                    .add(unit);
        }
        int availableUnits = availableUnitsByNodeId.values().stream().mapToInt(List::size).sum();
        if (availableUnits == 0) {
            return new RepositionPlan(List.of());
        }

        List<ZoneScore> rankedZones = rankedZones(snapshot, forecast);
        if (rankedZones.isEmpty()) {
            return new RepositionPlan(List.of());
        }

        int movesToPlan = Math.min(maxMoves, availableUnits);
        Map<ZoneId, Integer> desired = allocateMoves(rankedZones, movesToPlan);

        List<RepositionMove> moves = new ArrayList<>();
        for (ZoneScore zone : rankedZones) {
            int targetMoves = desired.getOrDefault(zone.zoneId, 0);
            for (int i = 0; i < targetMoves; i++) {
                if (moves.size() >= maxMoves) {
                    break;
                }
                if (availableUnitsByNodeId.isEmpty()) {
                    break;
                }

                Optional<ResponseUnit> chosen = chooseNearestUnitTo(snapshot, availableUnitsByNodeId, zone.targetNodeId);
                if (chosen.isEmpty()) {
                    break;
                }

                ResponseUnit unit = chosen.get();
                removeUnit(availableUnitsByNodeId, unit.id());

                if (!unit.currentNodeId().equals(zone.targetNodeId)) {
                    moves.add(new RepositionMove(
                            unit.id(),
                            zone.targetNodeId,
                            "zone=" + zone.zoneId + " expected=" + zone.score
                    ));
                }
            }
        }

        return new RepositionPlan(List.copyOf(moves));
    }

    private List<ZoneScore> rankedZones(SystemSnapshot snapshot, DemandForecast forecast) {
        List<ZoneScore> zones = new ArrayList<>();
        for (var entry : forecast.expectedIncidents().entrySet()) {
            ZoneId zoneId = entry.getKey();
            double score = entry.getValue();
            if (Double.isNaN(score) || score <= 0.0) {
                continue;
            }
            NodeId targetNodeId = new NodeId(zoneId.value());
            if (snapshot.graph().getNode(targetNodeId).isEmpty()) {
                continue;
            }
            zones.add(new ZoneScore(zoneId, targetNodeId, score));
        }

        zones.sort(Comparator
                .comparingDouble((ZoneScore z) -> z.score).reversed()
                .thenComparing(z -> z.zoneId.value()));

        if (zones.size() <= maxZones) {
            return List.copyOf(zones);
        }
        return List.copyOf(zones.subList(0, maxZones));
    }

    private Map<ZoneId, Integer> allocateMoves(List<ZoneScore> rankedZones, int movesToPlan) {
        double totalScore = rankedZones.stream().mapToDouble(z -> z.score).sum();
        if (totalScore <= 0.0) {
            return Map.of();
        }

        Map<ZoneId, Integer> base = new HashMap<>();
        Map<ZoneId, Double> remainder = new HashMap<>();

        int allocated = 0;
        for (ZoneScore zone : rankedZones) {
            double exact = movesToPlan * (zone.score / totalScore);
            int floor = (int) Math.floor(exact);
            base.put(zone.zoneId, floor);
            remainder.put(zone.zoneId, exact - floor);
            allocated += floor;
        }

        int remaining = movesToPlan - allocated;
        if (remaining > 0) {
            List<ZoneScore> byRemainder = new ArrayList<>(rankedZones);
            byRemainder.sort(Comparator
                    .comparingDouble((ZoneScore z) -> remainder.getOrDefault(z.zoneId, 0.0)).reversed()
                    .thenComparing(z -> z.zoneId.value()));

            for (int i = 0; i < remaining && i < byRemainder.size(); i++) {
                ZoneId zoneId = byRemainder.get(i).zoneId;
                base.put(zoneId, base.getOrDefault(zoneId, 0) + 1);
            }
        }

        return Map.copyOf(base);
    }

    private Optional<ResponseUnit> chooseNearestUnitTo(SystemSnapshot snapshot, Map<NodeId, List<ResponseUnit>> availableUnitsByNodeId, NodeId targetNodeId) {
        NodeId virtualSourceId = VirtualSourceGraphView.allocateVirtualSourceId(snapshot.graph(), availableUnitsByNodeId.keySet());
        var graph = new VirtualSourceGraphView(snapshot.graph(), virtualSourceId, availableUnitsByNodeId.keySet());

        Optional<Route> virtualRoute = router.findRoute(graph, virtualSourceId, targetNodeId, costFunction);
        if (virtualRoute.isEmpty()) {
            return Optional.empty();
        }

        Route route = VirtualSourceGraphView.stripVirtualSource(virtualRoute.get(), virtualSourceId);
        NodeId startNodeId = route.nodes().getFirst();

        List<ResponseUnit> candidates = availableUnitsByNodeId.get(startNodeId);
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        return candidates.stream()
                .min(Comparator.comparing((ResponseUnit u) -> u.id().value()));
    }

    private static void removeUnit(Map<NodeId, List<ResponseUnit>> availableUnitsByNodeId, UnitId unitId) {
        for (var entry : availableUnitsByNodeId.entrySet()) {
            entry.getValue().removeIf(u -> u.id().equals(unitId));
        }
        availableUnitsByNodeId.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    private record ZoneScore(ZoneId zoneId, NodeId targetNodeId, double score) {}
}

