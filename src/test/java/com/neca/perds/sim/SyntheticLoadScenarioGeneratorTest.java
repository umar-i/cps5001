package com.neca.perds.sim;

import com.neca.perds.graph.AdjacencyMapGraph;
import com.neca.perds.graph.Edge;
import com.neca.perds.graph.EdgeStatus;
import com.neca.perds.graph.EdgeWeights;
import com.neca.perds.model.Node;
import com.neca.perds.model.NodeId;
import com.neca.perds.model.NodeType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SyntheticLoadScenarioGeneratorTest {
    @Test
    void generatesDeterministicStrictlyIncreasingEvents() {
        var graph = new AdjacencyMapGraph();
        NodeId a = new NodeId("A");
        NodeId b = new NodeId("B");
        graph.addNode(new Node(a, NodeType.CITY, Optional.empty(), "A"));
        graph.addNode(new Node(b, NodeType.CITY, Optional.empty(), "B"));
        graph.putEdge(new Edge(a, b, new EdgeWeights(1.0, Duration.ofSeconds(60), 1.0), EdgeStatus.OPEN));
        graph.putEdge(new Edge(b, a, new EdgeWeights(1.0, Duration.ofSeconds(60), 1.0), EdgeStatus.OPEN));

        SyntheticLoadConfig config = new SyntheticLoadConfig(
                Duration.ofHours(1),
                2,
                3,
                Duration.ofMinutes(15),
                Duration.ofMinutes(30),
                2,
                1,
                Duration.ofMinutes(10)
        );

        var generator = new SyntheticLoadScenarioGenerator();
        Instant start = Instant.parse("2025-01-01T00:00:00Z");

        var events1 = generator.generate(graph, start, config, 123L);
        var events2 = generator.generate(graph, start, config, 123L);

        assertEquals(events1, events2);
        assertEquals(15, events1.size());

        for (int i = 1; i < events1.size(); i++) {
            assertTrue(events1.get(i).time().isAfter(events1.get(i - 1).time()));
        }
    }
}

