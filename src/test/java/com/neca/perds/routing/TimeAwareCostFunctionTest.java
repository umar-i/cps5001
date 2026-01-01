package com.neca.perds.routing;

import com.neca.perds.graph.AdjacencyMapGraph;
import com.neca.perds.graph.Edge;
import com.neca.perds.graph.EdgeStatus;
import com.neca.perds.graph.EdgeWeights;
import com.neca.perds.model.Node;
import com.neca.perds.model.NodeId;
import com.neca.perds.model.NodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TimeAwareCostFunction}.
 */
final class TimeAwareCostFunctionTest {
    private static final ZoneId UTC = ZoneId.of("UTC");
    
    private Edge openEdge;
    private Edge closedEdge;
    private CongestionProfile rushHourProfile;

    @BeforeEach
    void setUp() {
        EdgeWeights weights = new EdgeWeights(10.0, Duration.ofSeconds(600), 1.0);
        openEdge = new Edge(new NodeId("A"), new NodeId("B"), weights, EdgeStatus.OPEN);
        closedEdge = new Edge(new NodeId("A"), new NodeId("B"), weights, EdgeStatus.CLOSED);
        
        rushHourProfile = CongestionProfile.builder()
                .period(LocalTime.of(7, 0), LocalTime.of(9, 0), 1.5)
                .period(LocalTime.of(17, 0), LocalTime.of(19, 0), 1.7)
                .build();
    }

    @Test
    void appliesMultiplierDuringRushHour() {
        // 8:00 AM - during morning rush hour
        Instant morningRush = ZonedDateTime.of(2025, 1, 1, 8, 0, 0, 0, UTC).toInstant();
        
        TimeAwareCostFunction costFn = new TimeAwareCostFunction(
                CostFunctions.travelTimeSeconds(),
                rushHourProfile,
                () -> morningRush,
                UTC
        );
        
        // Base cost is 600 seconds, multiplied by 1.5
        assertEquals(900.0, costFn.cost(openEdge));
    }

    @Test
    void appliesNoMultiplierOffPeak() {
        // 12:00 PM - off-peak
        Instant offPeak = ZonedDateTime.of(2025, 1, 1, 12, 0, 0, 0, UTC).toInstant();
        
        TimeAwareCostFunction costFn = new TimeAwareCostFunction(
                CostFunctions.travelTimeSeconds(),
                rushHourProfile,
                () -> offPeak,
                UTC
        );
        
        // Base cost is 600 seconds, multiplier is 1.0
        assertEquals(600.0, costFn.cost(openEdge));
    }

    @Test
    void appliesEveningRushMultiplier() {
        // 6:00 PM - evening rush hour
        Instant eveningRush = ZonedDateTime.of(2025, 1, 1, 18, 0, 0, 0, UTC).toInstant();
        
        TimeAwareCostFunction costFn = new TimeAwareCostFunction(
                CostFunctions.travelTimeSeconds(),
                rushHourProfile,
                () -> eveningRush,
                UTC
        );
        
        // Base cost is 600 seconds, multiplied by 1.7
        assertEquals(1020.0, costFn.cost(openEdge));
    }

    @Test
    void preservesInfinityForClosedEdges() {
        Instant morningRush = ZonedDateTime.of(2025, 1, 1, 8, 0, 0, 0, UTC).toInstant();
        
        TimeAwareCostFunction costFn = new TimeAwareCostFunction(
                CostFunctions.travelTimeSeconds(),
                rushHourProfile,
                () -> morningRush,
                UTC
        );
        
        assertEquals(Double.POSITIVE_INFINITY, costFn.cost(closedEdge));
    }

    @Test
    void respondsToTimeChanges() {
        AtomicReference<Instant> currentTime = new AtomicReference<>();
        
        TimeAwareCostFunction costFn = new TimeAwareCostFunction(
                CostFunctions.travelTimeSeconds(),
                rushHourProfile,
                currentTime::get,
                UTC
        );
        
        // Morning rush
        currentTime.set(ZonedDateTime.of(2025, 1, 1, 8, 0, 0, 0, UTC).toInstant());
        assertEquals(900.0, costFn.cost(openEdge));
        
        // Off-peak
        currentTime.set(ZonedDateTime.of(2025, 1, 1, 12, 0, 0, 0, UTC).toInstant());
        assertEquals(600.0, costFn.cost(openEdge));
        
        // Evening rush
        currentTime.set(ZonedDateTime.of(2025, 1, 1, 18, 0, 0, 0, UTC).toInstant());
        assertEquals(1020.0, costFn.cost(openEdge));
    }

    @Test
    void worksWithDistanceCostFunction() {
        Instant morningRush = ZonedDateTime.of(2025, 1, 1, 8, 0, 0, 0, UTC).toInstant();
        
        TimeAwareCostFunction costFn = new TimeAwareCostFunction(
                CostFunctions.distanceKm(),
                rushHourProfile,
                () -> morningRush,
                UTC
        );
        
        // Base cost is 10.0 km, multiplied by 1.5
        assertEquals(15.0, costFn.cost(openEdge));
    }

    @Test
    void currentMultiplierReturnsCorrectValue() {
        AtomicReference<Instant> currentTime = new AtomicReference<>();
        
        TimeAwareCostFunction costFn = new TimeAwareCostFunction(
                CostFunctions.travelTimeSeconds(),
                rushHourProfile,
                currentTime::get,
                UTC
        );
        
        currentTime.set(ZonedDateTime.of(2025, 1, 1, 8, 0, 0, 0, UTC).toInstant());
        assertEquals(1.5, costFn.currentMultiplier());
        
        currentTime.set(ZonedDateTime.of(2025, 1, 1, 12, 0, 0, 0, UTC).toInstant());
        assertEquals(1.0, costFn.currentMultiplier());
    }

    @Test
    void accessorMethodsReturnCorrectValues() {
        EdgeCostFunction base = CostFunctions.travelTimeSeconds();
        Instant now = Instant.now();
        
        TimeAwareCostFunction costFn = new TimeAwareCostFunction(
                base, rushHourProfile, () -> now, UTC
        );
        
        assertSame(base, costFn.baseCostFunction());
        assertSame(rushHourProfile, costFn.congestionProfile());
    }

    @Test
    void factoryMethodsWork() {
        Instant morningRush = ZonedDateTime.of(2025, 1, 1, 8, 0, 0, 0, UTC).toInstant();
        
        EdgeCostFunction costFn = CostFunctions.travelTimeWithCongestion(
                rushHourProfile, () -> morningRush
        );
        
        assertInstanceOf(TimeAwareCostFunction.class, costFn);
    }

    @Test
    void factoryMethodWithZoneIdWorks() {
        ZoneId newYork = ZoneId.of("America/New_York");
        // 8:00 AM in New York = 13:00 UTC
        Instant newYorkMorning = ZonedDateTime.of(2025, 1, 1, 8, 0, 0, 0, newYork).toInstant();
        
        EdgeCostFunction costFn = CostFunctions.travelTimeWithCongestion(
                rushHourProfile, () -> newYorkMorning, newYork
        );
        
        // Should see morning rush multiplier when interpreted in New York time
        assertEquals(900.0, costFn.cost(openEdge));
    }

    @Test
    void integratesWithRouting() {
        AdjacencyMapGraph graph = new AdjacencyMapGraph();
        NodeId nodeA = new NodeId("A");
        NodeId nodeB = new NodeId("B");
        NodeId nodeC = new NodeId("C");

        graph.addNode(new Node(nodeA, NodeType.CITY, Optional.empty(), "A"));
        graph.addNode(new Node(nodeB, NodeType.CITY, Optional.empty(), "B"));
        graph.addNode(new Node(nodeC, NodeType.CITY, Optional.empty(), "C"));

        EdgeWeights weights = new EdgeWeights(5.0, Duration.ofSeconds(300), 1.0);
        graph.putEdge(new Edge(nodeA, nodeB, weights, EdgeStatus.OPEN));
        graph.putEdge(new Edge(nodeB, nodeC, weights, EdgeStatus.OPEN));

        // Test during rush hour
        Instant morningRush = ZonedDateTime.of(2025, 1, 1, 8, 0, 0, 0, UTC).toInstant();
        EdgeCostFunction rushHourCost = CostFunctions.travelTimeWithCongestion(
                rushHourProfile, () -> morningRush, UTC
        );

        Router router = new DijkstraRouter();
        Optional<Route> route = router.findRoute(graph, nodeA, nodeC, rushHourCost);

        assertTrue(route.isPresent());
        assertEquals(900.0, route.get().totalCost()); // 600 * 1.5
    }

    @Test
    void offPeakRoutingHasLowerCost() {
        AdjacencyMapGraph graph = new AdjacencyMapGraph();
        NodeId nodeA = new NodeId("A");
        NodeId nodeB = new NodeId("B");

        graph.addNode(new Node(nodeA, NodeType.CITY, Optional.empty(), "A"));
        graph.addNode(new Node(nodeB, NodeType.CITY, Optional.empty(), "B"));

        EdgeWeights weights = new EdgeWeights(5.0, Duration.ofSeconds(300), 1.0);
        graph.putEdge(new Edge(nodeA, nodeB, weights, EdgeStatus.OPEN));

        AtomicReference<Instant> currentTime = new AtomicReference<>();
        EdgeCostFunction costFn = CostFunctions.travelTimeWithCongestion(
                rushHourProfile, currentTime::get, UTC
        );

        Router router = new DijkstraRouter();

        // Rush hour route
        currentTime.set(ZonedDateTime.of(2025, 1, 1, 8, 0, 0, 0, UTC).toInstant());
        Optional<Route> rushHourRoute = router.findRoute(graph, nodeA, nodeB, costFn);

        // Off-peak route
        currentTime.set(ZonedDateTime.of(2025, 1, 1, 12, 0, 0, 0, UTC).toInstant());
        Optional<Route> offPeakRoute = router.findRoute(graph, nodeA, nodeB, costFn);

        assertTrue(rushHourRoute.isPresent());
        assertTrue(offPeakRoute.isPresent());
        assertTrue(rushHourRoute.get().totalCost() > offPeakRoute.get().totalCost(),
                "Rush hour route should have higher cost");
    }

    @Test
    void rejectsNullParameters() {
        assertThrows(NullPointerException.class, () ->
                new TimeAwareCostFunction(null, rushHourProfile, Instant::now));
        
        assertThrows(NullPointerException.class, () ->
                new TimeAwareCostFunction(CostFunctions.travelTimeSeconds(), null, Instant::now));
        
        assertThrows(NullPointerException.class, () ->
                new TimeAwareCostFunction(CostFunctions.travelTimeSeconds(), rushHourProfile, null));
        
        assertThrows(NullPointerException.class, () ->
                new TimeAwareCostFunction(CostFunctions.travelTimeSeconds(), rushHourProfile, Instant::now, null));
    }

    @Test
    void handlesNaNBaseCost() {
        EdgeCostFunction nanCost = edge -> Double.NaN;
        TimeAwareCostFunction costFn = new TimeAwareCostFunction(
                nanCost, rushHourProfile, Instant::now, UTC
        );
        
        assertTrue(Double.isNaN(costFn.cost(openEdge)));
    }
}
