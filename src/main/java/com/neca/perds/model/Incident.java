package com.neca.perds.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record Incident(
        IncidentId id,
        NodeId locationNodeId,
        IncidentSeverity severity,
        Set<UnitType> requiredUnitTypes,
        IncidentStatus status,
        Instant reportedAt,
        Optional<Instant> resolvedAt
) {
    public Incident {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(locationNodeId, "locationNodeId");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(requiredUnitTypes, "requiredUnitTypes");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reportedAt, "reportedAt");
        Objects.requireNonNull(resolvedAt, "resolvedAt");
        if (requiredUnitTypes.isEmpty()) {
            throw new IllegalArgumentException("requiredUnitTypes must not be empty");
        }
    }
}

