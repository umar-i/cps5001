package com.neca.perds.routing;

import com.neca.perds.graph.AdjacencyMapGraph;
import com.neca.perds.graph.Edge;
import com.neca.perds.graph.EdgeStatus;
import com.neca.perds.graph.EdgeWeights;
import com.neca.perds.model.GeoPoint;
import com.neca.perds.model.Node;
import com.neca.perds.model.NodeId;
import com.neca.perds.model.NodeType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AStarRouterPropertyTest {
    private static final double COST_EPSILON = 1e-9;

    @Test
    void aStarMatchesDijkstra_onRandomConnectedEuclideanGraphs() {
        long seed = 844_471_978_011_177_823L;
        var random = new Random(seed);

        var dijkstra = new DijkstraRouter();
        var aStar = new AStarRouter(new EuclideanHeuristic());

        int graphs = 50;
        for (int g = 0; g < graphs; g++) {
            int graphIndex = g;
            int nodeCount = 5 + random.nextInt(40);
            double edgeProbability = 0.10 + random.nextDouble() * 0.25;
            double closedProbability = random.nextDouble() * 0.15;
            var graph = randomEuclideanGraph(random, nodeCount, edgeProbability, closedProbability, true);
            long expectedVersion = graph.version();

            List<NodeId> nodeIds = new ArrayList<>(graph.nodeIds());
            int pairs = Math.min(60, nodeIds.size() * 3);
            for (int p = 0; p < pairs; p++) {
                int pairIndex = p;
                NodeId start = nodeIds.get(random.nextInt(nodeIds.size()));
                NodeId goal = nodeIds.get(random.nextInt(nodeIds.size()));

                Optional<Route> dijkstraRoute = dijkstra.findRoute(graph, start, goal, CostFunctions.distanceKm());
                Optional<Route> aStarRoute = aStar.findRoute(graph, start, goal, CostFunctions.distanceKm());

                assertTrue(dijkstraRoute.isPresent(), () -> debug(seed, graphIndex, pairIndex, start, goal) + " expected reachable");
                assertTrue(aStarRoute.isPresent(), () -> debug(seed, graphIndex, pairIndex, start, goal) + " expected reachable");

                assertRoutesEquivalent(
                        graph,
                        start,
                        goal,
                        expectedVersion,
                        dijkstraRoute.orElseThrow(),
                        aStarRoute.orElseThrow(),
                        seed,
                        graphIndex,
                        pairIndex
                );
            }
        }
    }

    @Test
    void aStarMatchesDijkstra_onRandomSparseEuclideanGraphs() {
        long seed = 735_152_120_157_167_397L;
        var random = new Random(seed);

        var dijkstra = new DijkstraRouter();
        var aStar = new AStarRouter(new EuclideanHeuristic());

        int graphs = 120;
        for (int g = 0; g < graphs; g++) {
            int graphIndex = g;
            int nodeCount = 1 + random.nextInt(35);
            double edgeProbability = random.nextDouble() * 0.20;
            double closedProbability = random.nextDouble() * 0.35;
            var graph = randomEuclideanGraph(random, nodeCount, edgeProbability, closedProbability, false);
            long expectedVersion = graph.version();

            List<NodeId> nodeIds = new ArrayList<>(graph.nodeIds());
            if (nodeIds.isEmpty()) {
                continue;
            }

            int pairs = Math.min(40, nodeIds.size() * 4);
            for (int p = 0; p < pairs; p++) {
                int pairIndex = p;
                NodeId start = nodeIds.get(random.nextInt(nodeIds.size()));
                NodeId goal = nodeIds.get(random.nextInt(nodeIds.size()));

                Optional<Route> dijkstraRoute = dijkstra.findRoute(graph, start, goal, CostFunctions.distanceKm());
                Optional<Route> aStarRoute = aStar.findRoute(graph, start, goal, CostFunctions.distanceKm());

                assertEquals(
                        dijkstraRoute.isPresent(),
                        aStarRoute.isPresent(),
                        () -> debug(seed, graphIndex, pairIndex, start, goal) + " reachability mismatch"
                );

                if (dijkstraRoute.isEmpty()) {
                    continue;
                }

                assertRoutesEquivalent(
                        graph,
                        start,
                        goal,
                        expectedVersion,
                        dijkstraRoute.orElseThrow(),
                        aStarRoute.orElseThrow(),
                        seed,
                        graphIndex,
                        pairIndex
                );
            }
        }
    }

    private static void assertRoutesEquivalent(
            AdjacencyMapGraph graph,
            NodeId start,
            NodeId goal,
            long expectedGraphVersion,
            Route dijkstraRoute,
            Route aStarRoute,
            long seed,
            int graphIndex,
            int pairIndex
    ) {
        assertEquals(
                expectedGraphVersion,
                dijkstraRoute.graphVersionUsed(),
                () -> debug(seed, graphIndex, pairIndex, start, goal) + " dijkstra graph version mismatch"
        );
        assertEquals(
                expectedGraphVersion,
                aStarRoute.graphVersionUsed(),
                () -> debug(seed, graphIndex, pairIndex, start, goal) + " a* graph version mismatch"
        );

        assertValidDistanceRoute(graph, start, goal, dijkstraRoute, seed, graphIndex, pairIndex, "dijkstra");
        assertValidDistanceRoute(graph, start, goal, aStarRoute, seed, graphIndex, pairIndex, "a*");

        assertEquals(
                dijkstraRoute.totalCost(),
                aStarRoute.totalCost(),
                COST_EPSILON,
                () -> debug(seed, graphIndex, pairIndex, start, goal) + " cost mismatch"
        );
    }

    private static void assertValidDistanceRoute(
            AdjacencyMapGraph graph,
            NodeId start,
            NodeId goal,
            Route route,
            long seed,
            int graphIndex,
            int pairIndex,
            String routerName
    ) {
        List<NodeId> path = route.nodes();
        assertFalse(path.isEmpty(), () -> debug(seed, graphIndex, pairIndex, start, goal) + " " + routerName + " empty path");
        assertEquals(
                start,
                path.get(0),
                () -> debug(seed, graphIndex, pairIndex, start, goal) + " " + routerName + " path must start at start"
        );
        assertEquals(
                goal,
                path.get(path.size() - 1),
                () -> debug(seed, graphIndex, pairIndex, start, goal) + " " + routerName + " path must end at goal"
        );

        double sumDistance = 0.0;
        Duration sumTime = Duration.ZERO;
        for (int i = 0; i < path.size() - 1; i++) {
            NodeId from = path.get(i);
            NodeId to = path.get(i + 1);
            Edge edge = graph.getEdge(from, to)
                    .orElseThrow(() -> new AssertionError(debug(seed, graphIndex, pairIndex, start, goal)
                            + " " + routerName + " missing edge " + from + " -> " + to));
            assertEquals(
                    EdgeStatus.OPEN,
                    edge.status(),
                    () -> debug(seed, graphIndex, pairIndex, start, goal) + " " + routerName + " path uses a CLOSED edge"
            );
            sumDistance += edge.weights().distanceKm();
            sumTime = sumTime.plus(edge.weights().travelTime());
        }

        assertEquals(
                sumDistance,
                route.totalCost(),
                COST_EPSILON,
                () -> debug(seed, graphIndex, pairIndex, start, goal) + " " + routerName + " totalCost mismatch"
        );
        assertEquals(
                sumDistance,
                route.totalDistanceKm(),
                COST_EPSILON,
                () -> debug(seed, graphIndex, pairIndex, start, goal) + " " + routerName + " totalDistanceKm mismatch"
        );
        assertEquals(
                sumTime,
                route.totalTravelTime(),
                () -> debug(seed, graphIndex, pairIndex, start, goal) + " " + routerName + " totalTravelTime mismatch"
        );
    }

    private static AdjacencyMapGraph randomEuclideanGraph(
            Random random,
            int nodeCount,
            double edgeProbability,
            double closedProbability,
            boolean forceConnected
    ) {
        var graph = new AdjacencyMapGraph();
        List<NodeId> nodeIds = new ArrayList<>(nodeCount);
        List<GeoPoint> points = new ArrayList<>(nodeCount);

        for (int i = 0; i < nodeCount; i++) {
            NodeId id = new NodeId("N" + i);
            GeoPoint point = new GeoPoint(random.nextDouble() * 1_000.0, random.nextDouble() * 1_000.0);
            nodeIds.add(id);
            points.add(point);
            graph.addNode(new Node(id, NodeType.CITY, Optional.of(point), "Node " + i));
        }

        for (int i = 0; i < nodeCount; i++) {
            for (int j = 0; j < nodeCount; j++) {
                if (i == j) {
                    continue;
                }
                if (random.nextDouble() >= edgeProbability) {
                    continue;
                }

                addEdge(
                        graph,
                        random,
                        nodeIds.get(i),
                        points.get(i),
                        nodeIds.get(j),
                        points.get(j),
                        random.nextDouble() < closedProbability ? EdgeStatus.CLOSED : EdgeStatus.OPEN
                );
            }
        }

        if (forceConnected && nodeCount >= 2) {
            for (int i = 0; i < nodeCount; i++) {
                int j = (i + 1) % nodeCount;
                addEdge(graph, random, nodeIds.get(i), points.get(i), nodeIds.get(j), points.get(j), EdgeStatus.OPEN);
                addEdge(graph, random, nodeIds.get(j), points.get(j), nodeIds.get(i), points.get(i), EdgeStatus.OPEN);
            }
        }

        return graph;
    }

    private static void addEdge(
            AdjacencyMapGraph graph,
            Random random,
            NodeId fromId,
            GeoPoint fromPoint,
            NodeId toId,
            GeoPoint toPoint,
            EdgeStatus status
    ) {
        double baseDistance = euclidean(fromPoint, toPoint);
        double distanceKm = baseDistance * (1.0 + random.nextDouble() * 2.5);
        Duration travelTime = Duration.ofMillis(Math.max(1L, Math.round(distanceKm * 1_000.0)));
        graph.putEdge(new Edge(fromId, toId, new EdgeWeights(distanceKm, travelTime, 1.0), status));
    }

    private static double euclidean(GeoPoint a, GeoPoint b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static String debug(long seed, int graphIndex, int pairIndex, NodeId start, NodeId goal) {
        return "seed=" + seed + " graph=" + graphIndex + " pair=" + pairIndex + " start=" + start + " goal=" + goal;
    }
}
