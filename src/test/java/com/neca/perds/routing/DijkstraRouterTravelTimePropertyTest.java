package com.neca.perds.routing;

import com.neca.perds.graph.AdjacencyMapGraph;
import com.neca.perds.graph.Edge;
import com.neca.perds.graph.EdgeStatus;
import com.neca.perds.graph.EdgeWeights;
import com.neca.perds.model.Node;
import com.neca.perds.model.NodeId;
import com.neca.perds.model.NodeType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DijkstraRouterTravelTimePropertyTest {
    private static final double COST_EPSILON = 1e-9;

    @Test
    void dijkstraMatchesReference_onRandomGraphs_usingTravelTimeCost() {
        long seed = 344_711_775_600_206_747L;
        var random = new Random(seed);

        var router = new DijkstraRouter();
        var costFunction = CostFunctions.travelTimeSeconds();

        int graphs = 60;
        for (int g = 0; g < graphs; g++) {
            int graphIndex = g;
            int nodeCount = 2 + random.nextInt(59);
            double edgeProbability = 0.05 + random.nextDouble() * 0.25;
            double closedProbability = random.nextDouble() * 0.30;

            AdjacencyMapGraph graph = randomGraph(random, nodeCount, edgeProbability, closedProbability);
            long expectedVersion = graph.version();

            List<NodeId> nodeIds = new ArrayList<>(graph.nodeIds());
            int pairs = Math.min(60, nodeIds.size() * 3);
            for (int p = 0; p < pairs; p++) {
                int pairIndex = p;
                NodeId start = nodeIds.get(random.nextInt(nodeIds.size()));
                NodeId goal = nodeIds.get(random.nextInt(nodeIds.size()));

                double referenceCost = referenceShortestCost(graph, start, goal, costFunction);
                Optional<Route> route = router.findRoute(graph, start, goal, costFunction);

                assertEquals(
                        Double.isInfinite(referenceCost),
                        route.isEmpty(),
                        () -> debug(seed, graphIndex, pairIndex, start, goal) + " reachability mismatch"
                );

                if (route.isEmpty()) {
                    continue;
                }

                Route found = route.orElseThrow();
                assertEquals(
                        expectedVersion,
                        found.graphVersionUsed(),
                        () -> debug(seed, graphIndex, pairIndex, start, goal) + " graph version mismatch"
                );

                assertValidTravelTimeRoute(graph, start, goal, found, seed, graphIndex, pairIndex);
                assertEquals(
                        referenceCost,
                        found.totalCost(),
                        COST_EPSILON,
                        () -> debug(seed, graphIndex, pairIndex, start, goal) + " cost mismatch"
                );
            }
        }
    }

    private static void assertValidTravelTimeRoute(
            AdjacencyMapGraph graph,
            NodeId start,
            NodeId goal,
            Route route,
            long seed,
            int graphIndex,
            int pairIndex
    ) {
        List<NodeId> path = route.nodes();
        assertFalse(path.isEmpty(), () -> debug(seed, graphIndex, pairIndex, start, goal) + " empty path");
        assertEquals(
                start,
                path.getFirst(),
                () -> debug(seed, graphIndex, pairIndex, start, goal) + " path must start at start"
        );
        assertEquals(
                goal,
                path.getLast(),
                () -> debug(seed, graphIndex, pairIndex, start, goal) + " path must end at goal"
        );

        double sumDistanceKm = 0.0;
        Duration sumTravelTime = Duration.ZERO;
        for (int i = 0; i < path.size() - 1; i++) {
            NodeId from = path.get(i);
            NodeId to = path.get(i + 1);
            Edge edge = graph.getEdge(from, to)
                    .orElseThrow(() -> new AssertionError(debug(seed, graphIndex, pairIndex, start, goal)
                            + " missing edge " + from + " -> " + to));

            assertEquals(
                    EdgeStatus.OPEN,
                    edge.status(),
                    () -> debug(seed, graphIndex, pairIndex, start, goal) + " path uses a CLOSED edge"
            );

            sumDistanceKm += edge.weights().distanceKm();
            sumTravelTime = sumTravelTime.plus(edge.weights().travelTime());
        }

        assertEquals(
                sumDistanceKm,
                route.totalDistanceKm(),
                COST_EPSILON,
                () -> debug(seed, graphIndex, pairIndex, start, goal) + " totalDistanceKm mismatch"
        );
        assertEquals(
                sumTravelTime,
                route.totalTravelTime(),
                () -> debug(seed, graphIndex, pairIndex, start, goal) + " totalTravelTime mismatch"
        );
        assertEquals(
                (double) sumTravelTime.toSeconds(),
                route.totalCost(),
                COST_EPSILON,
                () -> debug(seed, graphIndex, pairIndex, start, goal) + " totalCost mismatch"
        );
    }

    private static AdjacencyMapGraph randomGraph(
            Random random,
            int nodeCount,
            double edgeProbability,
            double closedProbability
    ) {
        var graph = new AdjacencyMapGraph();
        List<NodeId> nodeIds = new ArrayList<>(nodeCount);

        for (int i = 0; i < nodeCount; i++) {
            NodeId id = new NodeId("N" + i);
            nodeIds.add(id);
            graph.addNode(new Node(id, NodeType.CITY, Optional.empty(), "Node " + i));
        }

        for (int i = 0; i < nodeCount; i++) {
            for (int j = 0; j < nodeCount; j++) {
                if (i == j) {
                    continue;
                }
                if (random.nextDouble() >= edgeProbability) {
                    continue;
                }

                double distanceKm = 0.1 + random.nextDouble() * 50.0;
                double speedKmh = 30.0 + random.nextDouble() * 90.0;
                long travelTimeSeconds = Math.max(1L, Math.round(distanceKm / speedKmh * 3_600.0));

                EdgeStatus status = random.nextDouble() < closedProbability ? EdgeStatus.CLOSED : EdgeStatus.OPEN;
                graph.putEdge(new Edge(
                        nodeIds.get(i),
                        nodeIds.get(j),
                        new EdgeWeights(distanceKm, Duration.ofSeconds(travelTimeSeconds), 1.0),
                        status
                ));
            }
        }

        return graph;
    }

    private static double referenceShortestCost(AdjacencyMapGraph graph, NodeId start, NodeId goal, EdgeCostFunction costFunction) {
        if (start.equals(goal)) {
            return 0.0;
        }
        if (graph.getNode(start).isEmpty() || graph.getNode(goal).isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }

        Map<NodeId, Double> dist = new HashMap<>();
        for (NodeId nodeId : graph.nodeIds()) {
            dist.put(nodeId, Double.POSITIVE_INFINITY);
        }
        dist.put(start, 0.0);

        record State(NodeId nodeId, double cost) {}
        var pq = new PriorityQueue<State>(Comparator.comparingDouble(State::cost));
        pq.add(new State(start, 0.0));

        while (!pq.isEmpty()) {
            State current = pq.poll();
            double bestKnown = dist.getOrDefault(current.nodeId(), Double.POSITIVE_INFINITY);
            if (current.cost() > bestKnown) {
                continue;
            }
            if (current.nodeId().equals(goal)) {
                return current.cost();
            }

            for (Edge edge : graph.outgoingEdges(current.nodeId())) {
                double edgeCost = costFunction.cost(edge);
                if (Double.isInfinite(edgeCost)) {
                    continue;
                }
                if (Double.isNaN(edgeCost) || edgeCost < 0.0) {
                    throw new IllegalArgumentException("Edge cost must be non-negative and not NaN");
                }

                double alt = current.cost() + edgeCost;
                double existing = dist.getOrDefault(edge.to(), Double.POSITIVE_INFINITY);
                if (alt < existing) {
                    dist.put(edge.to(), alt);
                    pq.add(new State(edge.to(), alt));
                }
            }
        }

        return Double.POSITIVE_INFINITY;
    }

    private static String debug(long seed, int graphIndex, int pairIndex, NodeId start, NodeId goal) {
        return "seed=" + seed + " graph=" + graphIndex + " pair=" + pairIndex + " start=" + start + " goal=" + goal;
    }
}

