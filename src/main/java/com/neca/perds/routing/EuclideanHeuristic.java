package com.neca.perds.routing;

import com.neca.perds.graph.GraphReadView;
import com.neca.perds.model.GeoPoint;
import com.neca.perds.model.Node;
import com.neca.perds.model.NodeId;

import java.util.Optional;

public final class EuclideanHeuristic implements Heuristic {
    @Override
    public double estimate(GraphReadView graph, NodeId from, NodeId to) {
        Optional<Node> fromNode = graph.getNode(from);
        Optional<Node> toNode = graph.getNode(to);

        Optional<GeoPoint> fromPoint = fromNode.flatMap(Node::point);
        Optional<GeoPoint> toPoint = toNode.flatMap(Node::point);
        if (fromPoint.isEmpty() || toPoint.isEmpty()) {
            return 0.0;
        }

        double dx = fromPoint.get().x() - toPoint.get().x();
        double dy = fromPoint.get().y() - toPoint.get().y();
        return Math.sqrt(dx * dx + dy * dy);
    }
}

