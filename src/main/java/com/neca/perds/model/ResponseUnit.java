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

    /**
     * Returns true if this unit can be assigned to an incident.
     * Units that are AVAILABLE or REPOSITIONING (can be interrupted) are eligible.
     */
    public boolean isAvailable() {
        return (status == UnitStatus.AVAILABLE || status == UnitStatus.REPOSITIONING) 
                && assignedIncidentId.isEmpty();
    }
}

