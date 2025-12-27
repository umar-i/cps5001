package com.neca.perds.prediction;

import com.neca.perds.model.NodeId;
import com.neca.perds.model.UnitId;
import com.neca.perds.routing.Route;

import java.util.Objects;
import java.util.Optional;

public record RepositionMove(UnitId unitId, NodeId targetNodeId, String reason, Optional<Route> route) {
    public RepositionMove {
        Objects.requireNonNull(unitId, "unitId");
        Objects.requireNonNull(targetNodeId, "targetNodeId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(route, "route");
    }

    /**
     * Convenience constructor without route (for backward compatibility).
     */
    public RepositionMove(UnitId unitId, NodeId targetNodeId, String reason) {
        this(unitId, targetNodeId, reason, Optional.empty());
    }
}

