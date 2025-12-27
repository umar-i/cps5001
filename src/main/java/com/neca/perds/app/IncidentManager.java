package com.neca.perds.app;

import com.neca.perds.model.Incident;
import com.neca.perds.model.IncidentId;
import com.neca.perds.model.IncidentStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Manages incident lifecycle: creation, status transitions, and resolution.
 */
public final class IncidentManager {
    private final Map<IncidentId, Incident> incidents = new HashMap<>();

    public void add(Incident incident) {
        Objects.requireNonNull(incident, "incident");
        incidents.put(incident.id(), incident);
    }

    public Optional<Incident> get(IncidentId id) {
        return Optional.ofNullable(incidents.get(id));
    }

    public Incident require(IncidentId id) {
        return get(id).orElseThrow(() -> new IllegalStateException("Unknown incident: " + id));
    }

    public Collection<Incident> all() {
        return incidents.values();
    }

    public void updateStatus(IncidentId id, IncidentStatus newStatus) {
        Incident incident = require(id);
        incidents.put(id, new Incident(
                incident.id(),
                incident.locationNodeId(),
                incident.severity(),
                incident.requiredUnitTypes(),
                newStatus,
                incident.reportedAt(),
                incident.resolvedAt()
        ));
    }

    public void resolve(IncidentId id, Instant resolvedAt) {
        Incident incident = require(id);
        incidents.put(id, new Incident(
                incident.id(),
                incident.locationNodeId(),
                incident.severity(),
                incident.requiredUnitTypes(),
                IncidentStatus.RESOLVED,
                incident.reportedAt(),
                Optional.of(resolvedAt)
        ));
    }

    public void markDispatched(IncidentId id) {
        updateStatus(id, IncidentStatus.DISPATCHED);
    }

    public void markQueued(IncidentId id) {
        Incident incident = incidents.get(id);
        if (incident != null && incident.status() != IncidentStatus.RESOLVED) {
            updateStatus(id, IncidentStatus.QUEUED);
        }
    }

    public boolean isResolved(IncidentId id) {
        Incident incident = incidents.get(id);
        return incident != null && incident.status() == IncidentStatus.RESOLVED;
    }

    public boolean canDispatch(IncidentId id) {
        Incident incident = incidents.get(id);
        return incident != null && 
               (incident.status() == IncidentStatus.REPORTED || incident.status() == IncidentStatus.QUEUED);
    }
}
