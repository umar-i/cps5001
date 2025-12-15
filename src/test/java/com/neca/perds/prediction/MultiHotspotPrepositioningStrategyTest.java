package com.neca.perds.prediction;

import com.neca.perds.graph.AdjacencyMapGraph;
import com.neca.perds.graph.Edge;
import com.neca.perds.graph.EdgeStatus;
import com.neca.perds.graph.EdgeWeights;
import com.neca.perds.model.Node;
import com.neca.perds.model.NodeId;
import com.neca.perds.model.NodeType;
import com.neca.perds.model.ResponseUnit;
import com.neca.perds.model.UnitId;
import com.neca.perds.model.UnitStatus;
import com.neca.perds.model.UnitType;
import com.neca.perds.model.ZoneId;
import com.neca.perds.system.SystemSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MultiHotspotPrepositioningStrategyTest {
    @Test
    void distributesMovesAcrossTopDemandZones() {
        Instant now = Instant.parse("2025-01-01T00:00:00Z");

        NodeId a = new NodeId("A");
        NodeId b = new NodeId("B");
        NodeId c = new NodeId("C");
        NodeId d = new NodeId("D");

        var graph = new AdjacencyMapGraph();
        graph.addNode(new Node(a, NodeType.CITY, Optional.empty(), "A"));
        graph.addNode(new Node(b, NodeType.CITY, Optional.empty(), "B"));
        graph.addNode(new Node(c, NodeType.CITY, Optional.empty(), "C"));
        graph.addNode(new Node(d, NodeType.CITY, Optional.empty(), "D"));

        graph.putEdge(new Edge(a, c, new EdgeWeights(1.0, Duration.ofSeconds(10), 1.0), EdgeStatus.OPEN));
        graph.putEdge(new Edge(b, c, new EdgeWeights(1.0, Duration.ofSeconds(100), 1.0), EdgeStatus.OPEN));
        graph.putEdge(new Edge(a, d, new EdgeWeights(1.0, Duration.ofSeconds(100), 1.0), EdgeStatus.OPEN));
        graph.putEdge(new Edge(b, d, new EdgeWeights(1.0, Duration.ofSeconds(10), 1.0), EdgeStatus.OPEN));

        var snapshot = new SystemSnapshot(
                graph,
                now,
                List.of(
                        new ResponseUnit(new UnitId("U1"), UnitType.AMBULANCE, UnitStatus.AVAILABLE, a, Optional.empty(), Optional.empty()),
                        new ResponseUnit(new UnitId("U2"), UnitType.AMBULANCE, UnitStatus.AVAILABLE, b, Optional.empty(), Optional.empty())
                ),
                List.of(),
                List.of(),
                List.of()
        );

        DemandForecast forecast = new DemandForecast(now, Duration.ofMinutes(30), Map.of(
                new ZoneId("C"), 0.6,
                new ZoneId("D"), 0.4
        ));

        PrepositioningStrategy strategy = new MultiHotspotPrepositioningStrategy(2, 2);
        RepositionPlan plan = strategy.plan(snapshot, forecast);

        assertEquals(2, plan.moves().size());
        assertEquals(new UnitId("U1"), plan.moves().get(0).unitId());
        assertEquals(new NodeId("C"), plan.moves().get(0).targetNodeId());
        assertEquals(new UnitId("U2"), plan.moves().get(1).unitId());
        assertEquals(new NodeId("D"), plan.moves().get(1).targetNodeId());
    }
}

