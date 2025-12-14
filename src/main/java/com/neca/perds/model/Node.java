package com.neca.perds.model;

import java.util.Objects;
import java.util.Optional;

public record Node(NodeId id, NodeType type, Optional<GeoPoint> point, String label) {
    public Node {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(label, "label");
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
    }
}

