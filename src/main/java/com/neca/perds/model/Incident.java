package com.neca.perds.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Represents an emergency incident requiring response.
 *
 * @param id unique identifier for the incident
 * @param locationNodeId the node where the incident occurred
 * @param severity the severity level of the incident
 * @param requiredUnitTypes the types of units required to respond
 * @param status current status of the incident
 * @param reportedAt when the incident was reported
 * @param resolvedAt when the incident was resolved, if applicable
 * @param requiredCapacity minimum capacity required from responding units (default 1)
 * @param requiredSpecializationLevel minimum specialization level required (default 1)
 */
public record Incident(
        IncidentId id,
        NodeId locationNodeId,
        IncidentSeverity severity,
        Set<UnitType> requiredUnitTypes,
        IncidentStatus status,
        Instant reportedAt,
        Optional<Instant> resolvedAt,
        int requiredCapacity,
        int requiredSpecializationLevel
) {
    /** Default required capacity when not specified. */
    public static final int DEFAULT_REQUIRED_CAPACITY = 1;
    
    /** Default required specialization level when not specified. */
    public static final int DEFAULT_REQUIRED_SPECIALIZATION_LEVEL = 1;

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
        if (requiredCapacity < 1) {
            throw new IllegalArgumentException("requiredCapacity must be >= 1");
        }
        if (requiredSpecializationLevel < ResponseUnit.MIN_SPECIALIZATION_LEVEL 
                || requiredSpecializationLevel > ResponseUnit.MAX_SPECIALIZATION_LEVEL) {
            throw new IllegalArgumentException(
                    "requiredSpecializationLevel must be between " + ResponseUnit.MIN_SPECIALIZATION_LEVEL 
                    + " and " + ResponseUnit.MAX_SPECIALIZATION_LEVEL);
        }
    }

    /**
     * Convenience constructor with default capacity and specialization requirements.
     */
    public Incident(
            IncidentId id,
            NodeId locationNodeId,
            IncidentSeverity severity,
            Set<UnitType> requiredUnitTypes,
            IncidentStatus status,
            Instant reportedAt,
            Optional<Instant> resolvedAt
    ) {
        this(id, locationNodeId, severity, requiredUnitTypes, status, reportedAt, resolvedAt,
                DEFAULT_REQUIRED_CAPACITY, DEFAULT_REQUIRED_SPECIALIZATION_LEVEL);
    }
}

