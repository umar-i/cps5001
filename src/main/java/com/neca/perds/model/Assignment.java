package com.neca.perds.model;

import com.neca.perds.routing.Route;

import java.time.Instant;
import java.util.Objects;

public record Assignment(IncidentId incidentId, UnitId unitId, Route route, Instant assignedAt) {
    public Assignment {
        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(unitId, "unitId");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(assignedAt, "assignedAt");
    }
}

