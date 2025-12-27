package com.neca.perds.routing;

import com.neca.perds.graph.GraphReadView;
import com.neca.perds.model.GeoPoint;
import com.neca.perds.model.Node;
import com.neca.perds.model.NodeId;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A* heuristic using Euclidean distance between node coordinates.
 * <p>
 * When nodes lack {@link GeoPoint} coordinates, this heuristic returns 0.0
 * (admissible but degrades A* to Dijkstra-like behavior). A warning is logged
 * once per node missing coordinates to aid debugging.
 * </p>
 */
public final class EuclideanHeuristic implements Heuristic {
    private static final System.Logger LOGGER = System.getLogger(EuclideanHeuristic.class.getName());

    // Track nodes we've already warned about to avoid log spam
    private final Set<NodeId> warnedNodes = ConcurrentHashMap.newKeySet();

    @Override
    public double estimate(GraphReadView graph, NodeId from, NodeId to) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");

        Optional<Node> fromNode = graph.getNode(from);
        Optional<Node> toNode = graph.getNode(to);

        Optional<GeoPoint> fromPoint = fromNode.flatMap(Node::point);
        Optional<GeoPoint> toPoint = toNode.flatMap(Node::point);

        if (fromPoint.isEmpty()) {
            warnMissingCoordinates(from, "from");
            return 0.0;
        }
        if (toPoint.isEmpty()) {
            warnMissingCoordinates(to, "to");
            return 0.0;
        }

        double dx = fromPoint.get().x() - toPoint.get().x();
        double dy = fromPoint.get().y() - toPoint.get().y();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private void warnMissingCoordinates(NodeId nodeId, String role) {
        if (warnedNodes.add(nodeId)) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "EuclideanHeuristic: Node ''{0}'' ({1}) has no coordinates; " +
                    "returning 0.0 (A* degrades to Dijkstra behavior)", nodeId.value(), role);
        }
    }
}

