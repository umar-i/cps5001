package com.neca.perds.model;

import java.util.Objects;
import java.util.Optional;

public record ResponseUnit(
        UnitId id,
        UnitType type,
        UnitStatus status,
        NodeId currentNodeId,
        Optional<IncidentId> assignedIncidentId,
        Optional<DispatchCentreId> homeDispatchCentreId
) {
    public ResponseUnit {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(currentNodeId, "currentNodeId");
        Objects.requireNonNull(assignedIncidentId, "assignedIncidentId");
        Objects.requireNonNull(homeDispatchCentreId, "homeDispatchCentreId");
    }

    public boolean isAvailable() {
        return status == UnitStatus.AVAILABLE && assignedIncidentId.isEmpty();
    }
}

