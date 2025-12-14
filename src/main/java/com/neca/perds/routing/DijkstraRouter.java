package com.neca.perds.routing;

import com.neca.perds.ds.BinaryHeapIndexedMinPriorityQueue;
import com.neca.perds.graph.GraphReadView;
import com.neca.perds.model.NodeId;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class DijkstraRouter implements Router {
    @Override
    public Optional<Route> findRoute(GraphReadView graph, NodeId start, NodeId goal, EdgeCostFunction costFunction) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(costFunction, "costFunction");

        long graphVersion = graph.version();
        if (start.equals(goal)) {
            return Optional.of(new Route(
                    List.of(start),
                    0.0,
                    0.0,
                    Duration.ZERO,
                    graphVersion
            ));
        }

        List<NodeId> nodeIds = new ArrayList<>(graph.nodeIds());
        Map<NodeId, Integer> indexByNodeId = new HashMap<>(nodeIds.size() * 2);
        for (int i = 0; i < nodeIds.size(); i++) {
            indexByNodeId.put(nodeIds.get(i), i);
        }

        Integer startIndex = indexByNodeId.get(start);
        Integer goalIndex = indexByNodeId.get(goal);
        if (startIndex == null || goalIndex == null) {
            return Optional.empty();
        }

        double[] dist = new double[nodeIds.size()];
        int[] prev = new int[nodeIds.size()];
        for (int i = 0; i < nodeIds.size(); i++) {
            dist[i] = Double.POSITIVE_INFINITY;
            prev[i] = -1;
        }

        dist[startIndex] = 0.0;

        var pq = new BinaryHeapIndexedMinPriorityQueue(nodeIds.size());
        pq.insert(startIndex, 0.0);

        while (!pq.isEmpty()) {
            int u = pq.extractMin();
            if (u == goalIndex) {
                break;
            }

            NodeId from = nodeIds.get(u);
            for (var edge : graph.outgoingEdges(from)) {
                Integer v = indexByNodeId.get(edge.to());
                if (v == null) {
                    continue;
                }

                double edgeCost = costFunction.cost(edge);
                if (Double.isNaN(edgeCost) || edgeCost < 0.0) {
                    throw new IllegalArgumentException("Edge cost must be non-negative and not NaN");
                }
                if (Double.isInfinite(edgeCost)) {
                    continue;
                }

                double alt = dist[u] + edgeCost;
                if (alt < dist[v]) {
                    dist[v] = alt;
                    prev[v] = u;
                    if (pq.contains(v)) {
                        pq.decreaseKey(v, alt);
                    } else {
                        pq.insert(v, alt);
                    }
                }
            }
        }

        if (Double.isInfinite(dist[goalIndex])) {
            return Optional.empty();
        }

        List<NodeId> path = reconstructPath(nodeIds, prev, goalIndex);
        Totals totals = computeTotals(graph, path, costFunction);
        return Optional.of(new Route(
                List.copyOf(path),
                dist[goalIndex],
                totals.totalDistanceKm,
                totals.totalTravelTime,
                graphVersion
        ));
    }

    private static List<NodeId> reconstructPath(List<NodeId> nodeIds, int[] prev, int goalIndex) {
        List<NodeId> reversed = new ArrayList<>();
        int current = goalIndex;
        while (current != -1) {
            reversed.add(nodeIds.get(current));
            current = prev[current];
        }
        Collections.reverse(reversed);
        return reversed;
    }

    private static Totals computeTotals(GraphReadView graph, List<NodeId> path, EdgeCostFunction costFunction) {
        double distanceKm = 0.0;
        Duration travelTime = Duration.ZERO;

        for (int i = 0; i < path.size() - 1; i++) {
            NodeId from = path.get(i);
            NodeId to = path.get(i + 1);
            var edge = graph.getEdge(from, to)
                    .orElseThrow(() -> new IllegalStateException("Missing edge in route: " + from + " -> " + to));

            double edgeCost = costFunction.cost(edge);
            if (Double.isNaN(edgeCost) || edgeCost < 0.0) {
                throw new IllegalArgumentException("Edge cost must be non-negative and not NaN");
            }

            distanceKm += edge.weights().distanceKm();
            travelTime = travelTime.plus(edge.weights().travelTime());
        }

        return new Totals(distanceKm, travelTime);
    }

    private record Totals(double totalDistanceKm, Duration totalTravelTime) {}
}
