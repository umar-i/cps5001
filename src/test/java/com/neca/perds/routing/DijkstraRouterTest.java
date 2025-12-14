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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DijkstraRouterTest {
    @Test
    void findsShortestTravelTimePath_andComputesTotals() {
        var graph = new AdjacencyMapGraph();
        NodeId a = new NodeId("A");
        NodeId b = new NodeId("B");
        NodeId c = new NodeId("C");

        graph.addNode(new Node(a, NodeType.CITY, Optional.empty(), "A"));
        graph.addNode(new Node(b, NodeType.CITY, Optional.empty(), "B"));
        graph.addNode(new Node(c, NodeType.CITY, Optional.empty(), "C"));

        EdgeWeights fast = new EdgeWeights(5.0, Duration.ofSeconds(300), 1.0);
        EdgeWeights slow = new EdgeWeights(20.0, Duration.ofSeconds(1200), 1.0);
        graph.putEdge(new Edge(a, b, fast, EdgeStatus.OPEN));
        graph.putEdge(new Edge(b, c, fast, EdgeStatus.OPEN));
        graph.putEdge(new Edge(a, c, slow, EdgeStatus.OPEN));

        long expectedVersion = graph.version();

        var router = new DijkstraRouter();
        Route route = router.findRoute(graph, a, c, CostFunctions.travelTimeSeconds()).orElseThrow();

        assertEquals(expectedVersion, route.graphVersionUsed());
        assertEquals(java.util.List.of(a, b, c), route.nodes());
        assertEquals(600.0, route.totalCost(), 1e-9);
        assertEquals(10.0, route.totalDistanceKm(), 1e-9);
        assertEquals(Duration.ofSeconds(600), route.totalTravelTime());
    }

    @Test
    void returnsEmptyWhenUnreachable() {
        var graph = new AdjacencyMapGraph();
        NodeId a = new NodeId("A");
        NodeId b = new NodeId("B");

        graph.addNode(new Node(a, NodeType.CITY, Optional.empty(), "A"));
        graph.addNode(new Node(b, NodeType.CITY, Optional.empty(), "B"));

        var router = new DijkstraRouter();
        assertTrue(router.findRoute(graph, a, b, CostFunctions.travelTimeSeconds()).isEmpty());
    }

    @Test
    void startEqualsGoal_returnsZeroLengthRoute() {
        var graph = new AdjacencyMapGraph();
        NodeId a = new NodeId("A");
        graph.addNode(new Node(a, NodeType.CITY, Optional.empty(), "A"));

        var router = new DijkstraRouter();
        Route route = router.findRoute(graph, a, a, CostFunctions.travelTimeSeconds()).orElseThrow();

        assertEquals(java.util.List.of(a), route.nodes());
        assertEquals(0.0, route.totalCost(), 1e-9);
        assertEquals(0.0, route.totalDistanceKm(), 1e-9);
        assertEquals(Duration.ZERO, route.totalTravelTime());
    }
}
