package com.neca.perds.app;

import com.neca.perds.model.IncidentId;
import com.neca.perds.model.NodeId;
import com.neca.perds.routing.Route;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AssignmentRouteIndexTest {
    @Test
    void incidentIdsUsingEdgeReturnsIndexedIncidents() {
        var index = new AssignmentRouteIndex();
        IncidentId incidentId = new IncidentId("I1");

        index.put(incidentId, route("A", "B", "C"));

        assertEquals(java.util.Set.of(incidentId), index.incidentIdsUsingEdge(new NodeId("A"), new NodeId("B")));
        assertEquals(java.util.Set.of(incidentId), index.incidentIdsUsingEdge(new NodeId("B"), new NodeId("C")));
        assertTrue(index.incidentIdsUsingEdge(new NodeId("A"), new NodeId("C")).isEmpty());
    }

    @Test
    void putReplacesExistingRouteIndex() {
        var index = new AssignmentRouteIndex();
        IncidentId incidentId = new IncidentId("I1");

        index.put(incidentId, route("A", "B", "C"));
        index.put(incidentId, route("A", "D"));

        assertTrue(index.incidentIdsUsingEdge(new NodeId("A"), new NodeId("B")).isEmpty());
        assertEquals(java.util.Set.of(incidentId), index.incidentIdsUsingEdge(new NodeId("A"), new NodeId("D")));
    }

    @Test
    void removeUnindexesEdgesForThatIncidentOnly() {
        var index = new AssignmentRouteIndex();

        IncidentId incident1 = new IncidentId("I1");
        IncidentId incident2 = new IncidentId("I2");

        index.put(incident1, route("A", "D"));
        index.put(incident2, route("A", "D"));

        assertEquals(java.util.Set.of(incident1, incident2), index.incidentIdsUsingEdge(new NodeId("A"), new NodeId("D")));

        index.remove(incident1);
        assertEquals(java.util.Set.of(incident2), index.incidentIdsUsingEdge(new NodeId("A"), new NodeId("D")));

        index.remove(incident2);
        assertTrue(index.incidentIdsUsingEdge(new NodeId("A"), new NodeId("D")).isEmpty());
    }

    private static Route route(String... nodeIds) {
        List<NodeId> nodes = java.util.Arrays.stream(nodeIds).map(NodeId::new).toList();
        return new Route(nodes, 0.0, 0.0, Duration.ZERO, 1L);
    }
}

