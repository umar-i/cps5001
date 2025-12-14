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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AStarRouterTest {
    @Test
    void findsShortestDistancePath_withEuclideanHeuristic() {
        var graph = new AdjacencyMapGraph();
        NodeId a = new NodeId("A");
        NodeId b = new NodeId("B");
        NodeId c = new NodeId("C");

        graph.addNode(new Node(a, NodeType.CITY, Optional.of(new GeoPoint(0.0, 0.0)), "A"));
        graph.addNode(new Node(b, NodeType.CITY, Optional.of(new GeoPoint(1.0, 0.0)), "B"));
        graph.addNode(new Node(c, NodeType.CITY, Optional.of(new GeoPoint(2.0, 0.0)), "C"));

        graph.putEdge(new Edge(a, b, new EdgeWeights(1.0, Duration.ofSeconds(1), 1.0), EdgeStatus.OPEN));
        graph.putEdge(new Edge(b, c, new EdgeWeights(1.0, Duration.ofSeconds(1), 1.0), EdgeStatus.OPEN));
        graph.putEdge(new Edge(a, c, new EdgeWeights(3.0, Duration.ofSeconds(3), 1.0), EdgeStatus.OPEN));

        var router = new AStarRouter(new EuclideanHeuristic());
        Route route = router.findRoute(graph, a, c, CostFunctions.distanceKm()).orElseThrow();

        assertEquals(java.util.List.of(a, b, c), route.nodes());
        assertEquals(2.0, route.totalCost(), 1e-9);
        assertEquals(2.0, route.totalDistanceKm(), 1e-9);
        assertEquals(Duration.ofSeconds(2), route.totalTravelTime());
    }
}

