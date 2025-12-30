package com.neca.perds.app;

import com.neca.perds.model.IncidentId;
import com.neca.perds.model.NodeId;
import com.neca.perds.model.ResponseUnit;
import com.neca.perds.model.UnitId;
import com.neca.perds.model.UnitStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Manages unit state: registration, status changes, movement, and repositioning.
 */
public final class UnitManager {
    private final Map<UnitId, ResponseUnit> units = new HashMap<>();
    private final Map<UnitId, PendingRepositioning> pendingRepositionings = new HashMap<>();

    public void register(ResponseUnit unit) {
        Objects.requireNonNull(unit, "unit");
        units.put(unit.id(), unit);
    }

    public Optional<ResponseUnit> get(UnitId id) {
        return Optional.ofNullable(units.get(id));
    }

    public ResponseUnit require(UnitId id) {
        return get(id).orElseThrow(() -> new IllegalStateException("Unknown unit: " + id));
    }

    public Collection<ResponseUnit> all() {
        return units.values();
    }

    public void setStatus(UnitId id, UnitStatus status) {
        ResponseUnit unit = require(id);
        units.put(id, new ResponseUnit(
                unit.id(),
                unit.type(),
                status,
                unit.currentNodeId(),
                unit.assignedIncidentId(),
                unit.homeDispatchCentreId(),
                unit.capacity(),
                unit.specializationLevel()
        ));
    }

    public void move(UnitId id, NodeId newNodeId) {
        ResponseUnit unit = require(id);
        units.put(id, new ResponseUnit(
                unit.id(),
                unit.type(),
                unit.status(),
                newNodeId,
                unit.assignedIncidentId(),
                unit.homeDispatchCentreId(),
                unit.capacity(),
                unit.specializationLevel()
        ));
    }

    public void assignToIncident(UnitId id, IncidentId incidentId) {
        ResponseUnit unit = require(id);
        cancelRepositioning(id);
        units.put(id, new ResponseUnit(
                unit.id(),
                unit.type(),
                UnitStatus.EN_ROUTE,
                unit.currentNodeId(),
                Optional.of(incidentId),
                unit.homeDispatchCentreId(),
                unit.capacity(),
                unit.specializationLevel()
        ));
    }

    public void clearAssignment(UnitId id) {
        ResponseUnit unit = units.get(id);
        if (unit == null) {
            return;
        }
        UnitStatus newStatus = unit.status();
        if (unit.status() == UnitStatus.EN_ROUTE || unit.status() == UnitStatus.ON_SCENE) {
            newStatus = UnitStatus.AVAILABLE;
        }
        units.put(id, new ResponseUnit(
                unit.id(),
                unit.type(),
                newStatus,
                unit.currentNodeId(),
                Optional.empty(),
                unit.homeDispatchCentreId(),
                unit.capacity(),
                unit.specializationLevel()
        ));
    }

    public void clearAssignmentForIncident(IncidentId incidentId) {
        List<UnitId> unitIds = units.values().stream()
                .filter(u -> u.assignedIncidentId().isPresent() && u.assignedIncidentId().get().equals(incidentId))
                .map(ResponseUnit::id)
                .toList();
        for (UnitId unitId : unitIds) {
            ResponseUnit unit = units.get(unitId);
            if (unit != null && unit.assignedIncidentId().isPresent() && unit.assignedIncidentId().get().equals(incidentId)) {
                clearAssignment(unitId);
            }
        }
    }

    public void startRepositioning(UnitId id, NodeId targetNodeId, Instant arrivalAt, String reason) {
        ResponseUnit unit = require(id);
        units.put(id, new ResponseUnit(
                unit.id(),
                unit.type(),
                UnitStatus.REPOSITIONING,
                unit.currentNodeId(),
                Optional.empty(),
                unit.homeDispatchCentreId(),
                unit.capacity(),
                unit.specializationLevel()
        ));
        pendingRepositionings.put(id, new PendingRepositioning(id, targetNodeId, arrivalAt, reason));
    }

    public void cancelRepositioning(UnitId id) {
        pendingRepositionings.remove(id);
    }

    public void completeRepositionings(Instant at) {
        List<UnitId> completed = pendingRepositionings.entrySet().stream()
                .filter(e -> !e.getValue().arrivalAt().isAfter(at))
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(UnitId::value))
                .toList();

        for (UnitId unitId : completed) {
            PendingRepositioning repositioning = pendingRepositionings.remove(unitId);
            if (repositioning == null) {
                continue;
            }
            ResponseUnit unit = units.get(unitId);
            if (unit == null || unit.status() != UnitStatus.REPOSITIONING) {
                continue;
            }
            units.put(unitId, new ResponseUnit(
                    unit.id(),
                    unit.type(),
                    UnitStatus.AVAILABLE,
                    repositioning.targetNodeId(),
                    Optional.empty(),
                    unit.homeDispatchCentreId(),
                    unit.capacity(),
                    unit.specializationLevel()
            ));
        }
    }

    public static boolean isAssignmentCompatibleStatus(UnitStatus status) {
        return status == UnitStatus.EN_ROUTE || status == UnitStatus.ON_SCENE;
    }

    /**
     * Record tracking a unit that is in transit to a new position.
     */
    public record PendingRepositioning(
            UnitId unitId,
            NodeId targetNodeId,
            Instant arrivalAt,
            String reason
    ) {
        public PendingRepositioning {
            Objects.requireNonNull(unitId, "unitId");
            Objects.requireNonNull(targetNodeId, "targetNodeId");
            Objects.requireNonNull(arrivalAt, "arrivalAt");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
