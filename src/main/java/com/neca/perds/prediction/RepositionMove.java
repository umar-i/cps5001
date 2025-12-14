package com.neca.perds.prediction;

import com.neca.perds.model.NodeId;
import com.neca.perds.model.UnitId;

import java.util.Objects;

public record RepositionMove(UnitId unitId, NodeId targetNodeId, String reason) {
    public RepositionMove {
        Objects.requireNonNull(unitId, "unitId");
        Objects.requireNonNull(targetNodeId, "targetNodeId");
        Objects.requireNonNull(reason, "reason");
    }
}

