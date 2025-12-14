package com.neca.perds.system;

import com.neca.perds.graph.GraphReadView;
import com.neca.perds.model.Assignment;
import com.neca.perds.model.DispatchCentre;
import com.neca.perds.model.Incident;
import com.neca.perds.model.ResponseUnit;

import java.time.Instant;
import java.util.Collection;
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
}

