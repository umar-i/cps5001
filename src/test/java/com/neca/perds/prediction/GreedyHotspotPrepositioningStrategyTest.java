package com.neca.perds.prediction;

import com.neca.perds.graph.AdjacencyMapGraph;
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

final class GreedyHotspotPrepositioningStrategyTest {
    @Test
    void plansMoveToHighestDemandZone() {
        Instant now = Instant.parse("2025-01-01T00:00:00Z");

        var graph = new AdjacencyMapGraph();
        graph.addNode(new Node(new NodeId("A"), NodeType.CITY, Optional.empty(), "A"));
        graph.addNode(new Node(new NodeId("B"), NodeType.CITY, Optional.empty(), "B"));
        graph.addNode(new Node(new NodeId("C"), NodeType.CITY, Optional.empty(), "C"));

        var snapshot = new SystemSnapshot(
                graph,
                now,
                List.of(
                        new ResponseUnit(new UnitId("U1"), UnitType.AMBULANCE, UnitStatus.AVAILABLE, new NodeId("A"), Optional.empty(), Optional.empty()),
                        new ResponseUnit(new UnitId("U2"), UnitType.AMBULANCE, UnitStatus.AVAILABLE, new NodeId("B"), Optional.empty(), Optional.empty())
                ),
                List.of(),
                List.of(),
                List.of()
        );

        DemandForecast forecast = new DemandForecast(now, Duration.ofMinutes(30), Map.of(new ZoneId("C"), 0.9));
        PrepositioningStrategy strategy = new GreedyHotspotPrepositioningStrategy(1);
        RepositionPlan plan = strategy.plan(snapshot, forecast);

        assertEquals(1, plan.moves().size());
        assertEquals(new UnitId("U1"), plan.moves().getFirst().unitId());
        assertEquals(new NodeId("C"), plan.moves().getFirst().targetNodeId());
    }
}

