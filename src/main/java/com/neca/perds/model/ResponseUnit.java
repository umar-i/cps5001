package com.neca.perds.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents an emergency response unit (ambulance, fire truck, police car, etc.).
 *
 * @param id unique identifier for the unit
 * @param type the type of unit (AMBULANCE, FIRE_TRUCK, POLICE)
 * @param status current operational status
 * @param currentNodeId the node where the unit is currently located
 * @param assignedIncidentId the incident this unit is assigned to, if any
 * @param homeDispatchCentreId the dispatch centre this unit belongs to, if any
 * @param capacity resource capacity of the unit (e.g., patient capacity, water capacity).
 *                 Higher values indicate more resources. Default is 1.
 * @param specializationLevel skill/equipment level (1=basic, 2=intermediate, 3=advanced).
 *                            Higher values indicate more specialized capabilities.
 */
public record ResponseUnit(
        UnitId id,
        UnitType type,
        UnitStatus status,
        NodeId currentNodeId,
        Optional<IncidentId> assignedIncidentId,
        Optional<DispatchCentreId> homeDispatchCentreId,
        int capacity,
        int specializationLevel
) {
    /** Default capacity for units when not specified. */
    public static final int DEFAULT_CAPACITY = 1;
    
    /** Default specialization level (basic). */
    public static final int DEFAULT_SPECIALIZATION_LEVEL = 1;
    
    /** Minimum valid specialization level. */
    public static final int MIN_SPECIALIZATION_LEVEL = 1;
    
    /** Maximum valid specialization level. */
    public static final int MAX_SPECIALIZATION_LEVEL = 3;

    public ResponseUnit {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(currentNodeId, "currentNodeId");
        Objects.requireNonNull(assignedIncidentId, "assignedIncidentId");
        Objects.requireNonNull(homeDispatchCentreId, "homeDispatchCentreId");
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        if (specializationLevel < MIN_SPECIALIZATION_LEVEL || specializationLevel > MAX_SPECIALIZATION_LEVEL) {
            throw new IllegalArgumentException(
                    "specializationLevel must be between " + MIN_SPECIALIZATION_LEVEL + " and " + MAX_SPECIALIZATION_LEVEL);
        }
    }

    /**
     * Convenience constructor with default capacity and specialization.
     */
    public ResponseUnit(
            UnitId id,
            UnitType type,
            UnitStatus status,
            NodeId currentNodeId,
            Optional<IncidentId> assignedIncidentId,
            Optional<DispatchCentreId> homeDispatchCentreId
    ) {
        this(id, type, status, currentNodeId, assignedIncidentId, homeDispatchCentreId,
                DEFAULT_CAPACITY, DEFAULT_SPECIALIZATION_LEVEL);
    }

    /**
     * Returns true if this unit can be assigned to an incident.
     * Units that are AVAILABLE or REPOSITIONING (can be interrupted) are eligible.
     */
    public boolean isAvailable() {
        return (status == UnitStatus.AVAILABLE || status == UnitStatus.REPOSITIONING) 
                && assignedIncidentId.isEmpty();
    }

    /**
     * Returns true if this unit meets the capacity and specialization requirements.
     *
     * @param requiredCapacity minimum required capacity (use 0 or 1 for no requirement)
     * @param requiredSpecialization minimum required specialization level (use 1 for no requirement)
     * @return true if unit meets or exceeds both requirements
     */
    public boolean meetsRequirements(int requiredCapacity, int requiredSpecialization) {
        return this.capacity >= requiredCapacity && this.specializationLevel >= requiredSpecialization;
    }

    public ResponseUnit withStatusAndAssignment(UnitStatus newStatus, Optional<IncidentId> newAssignedIncidentId) {
        return new ResponseUnit(id, type, newStatus, currentNodeId, newAssignedIncidentId, homeDispatchCentreId,
                capacity, specializationLevel);
    }
}

