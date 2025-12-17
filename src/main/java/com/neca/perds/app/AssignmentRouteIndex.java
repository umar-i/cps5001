package com.neca.perds.app;

import com.neca.perds.model.IncidentId;
import com.neca.perds.model.NodeId;
import com.neca.perds.routing.Route;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class AssignmentRouteIndex {
    private final Map<EdgeKey, Set<IncidentId>> incidentIdsByEdge = new HashMap<>();
    private final Map<IncidentId, List<EdgeKey>> edgesByIncidentId = new HashMap<>();

    public void put(IncidentId incidentId, Route route) {
        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(route, "route");

        remove(incidentId);

        List<EdgeKey> edges = edgesOf(route);
        if (edges.isEmpty()) {
            return;
        }

        edgesByIncidentId.put(incidentId, edges);
        for (EdgeKey edge : edges) {
            incidentIdsByEdge.computeIfAbsent(edge, ignored -> new HashSet<>()).add(incidentId);
        }
    }

    public void remove(IncidentId incidentId) {
        Objects.requireNonNull(incidentId, "incidentId");

        List<EdgeKey> edges = edgesByIncidentId.remove(incidentId);
        if (edges == null || edges.isEmpty()) {
            return;
        }

        for (EdgeKey edge : edges) {
            Set<IncidentId> incidentIds = incidentIdsByEdge.get(edge);
            if (incidentIds == null) {
                continue;
            }
            incidentIds.remove(incidentId);
            if (incidentIds.isEmpty()) {
                incidentIdsByEdge.remove(edge);
            }
        }
    }

    public Set<IncidentId> incidentIdsUsingEdge(NodeId from, NodeId to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");

        Set<IncidentId> incidentIds = incidentIdsByEdge.get(new EdgeKey(from, to));
        if (incidentIds == null || incidentIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(incidentIds);
    }

    private static List<EdgeKey> edgesOf(Route route) {
        List<NodeId> nodes = route.nodes();
        if (nodes.size() < 2) {
            return List.of();
        }

        Set<EdgeKey> uniqueEdges = new HashSet<>();
        for (int i = 0; i < nodes.size() - 1; i++) {
            uniqueEdges.add(new EdgeKey(nodes.get(i), nodes.get(i + 1)));
        }
        return new ArrayList<>(uniqueEdges);
    }

    private record EdgeKey(NodeId from, NodeId to) {
        private EdgeKey {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
        }
    }
}

