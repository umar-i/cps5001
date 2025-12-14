package com.neca.perds.model;

import java.util.Objects;
import java.util.Set;

public record DispatchCentre(DispatchCentreId id, NodeId nodeId, Set<UnitId> unitIds) {
    public DispatchCentre {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(unitIds, "unitIds");
    }
}

