package com.neca.perds.system;

import com.neca.perds.graph.GraphReadView;
import com.neca.perds.model.Assignment;
import com.neca.perds.model.DispatchCentre;
import com.neca.perds.model.Incident;
import com.neca.perds.model.ResponseUnit;
import com.neca.perds.model.UnitId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record SystemSnapshot(
        GraphReadView graph,
        Instant now,
        Collection<ResponseUnit> units,
        Collection<DispatchCentre> dispatchCentres,
        Collection<Incident> incidents,
        Collection<Assignment> assignments
) {
    public SystemSnapshot {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(units, "units");
        Objects.requireNonNull(dispatchCentres, "dispatchCentres");
        Objects.requireNonNull(incidents, "incidents");
        Objects.requireNonNull(assignments, "assignments");
    }

    public SystemSnapshot withUpdatedUnit(ResponseUnit updatedUnit) {
        Map<UnitId, ResponseUnit> unitMap = new HashMap<>();
        for (ResponseUnit unit : units) {
            unitMap.put(unit.id(), unit);
        }
        unitMap.put(updatedUnit.id(), updatedUnit);
        return new SystemSnapshot(graph, now, List.copyOf(unitMap.values()), dispatchCentres, incidents, assignments);
    }

    public SystemSnapshot withAddedAssignment(Assignment assignment) {
        List<Assignment> newAssignments = new ArrayList<>(assignments);
        newAssignments.add(assignment);
        return new SystemSnapshot(graph, now, units, dispatchCentres, incidents, List.copyOf(newAssignments));
    }
}

