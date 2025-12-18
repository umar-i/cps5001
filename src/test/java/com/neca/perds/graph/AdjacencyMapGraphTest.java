package com.neca.perds.graph;

import com.neca.perds.model.Node;
import com.neca.perds.model.NodeId;
import com.neca.perds.model.NodeType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class AdjacencyMapGraphTest {
    @Test
    void putEdge_requiresBothNodesToExist() {
        NodeId a = new NodeId("A");
        NodeId b = new NodeId("B");

        Edge edge = new Edge(
                a,
                b,
                new EdgeWeights(1.0, Duration.ofSeconds(1), 1.0),
                EdgeStatus.OPEN
        );

        var graph = new AdjacencyMapGraph();
        assertThrows(IllegalStateException.class, () -> graph.putEdge(edge));

        graph.addNode(new Node(a, NodeType.CITY, Optional.empty(), "A"));
        assertThrows(IllegalStateException.class, () -> graph.putEdge(edge));

        var graphMissingFrom = new AdjacencyMapGraph();
        graphMissingFrom.addNode(new Node(b, NodeType.CITY, Optional.empty(), "B"));
        assertThrows(IllegalStateException.class, () -> graphMissingFrom.putEdge(edge));

        graph.addNode(new Node(b, NodeType.CITY, Optional.empty(), "B"));
        assertDoesNotThrow(() -> graph.putEdge(edge));
    }
}
