package com.neca.perds.dispatch;

import com.neca.perds.model.IncidentId;
import com.neca.perds.model.UnitId;
import com.neca.perds.routing.Route;

import java.util.Objects;

public sealed interface DispatchCommand
        permits DispatchCommand.AssignUnitCommand, DispatchCommand.RerouteUnitCommand, DispatchCommand.CancelAssignmentCommand {

    record AssignUnitCommand(IncidentId incidentId, UnitId unitId, Route route, DispatchRationale rationale)
            implements DispatchCommand {
        public AssignUnitCommand {
            Objects.requireNonNull(incidentId, "incidentId");
            Objects.requireNonNull(unitId, "unitId");
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(rationale, "rationale");
        }
    }

    record RerouteUnitCommand(UnitId unitId, Route newRoute, String reason) implements DispatchCommand {
        public RerouteUnitCommand {
            Objects.requireNonNull(unitId, "unitId");
            Objects.requireNonNull(newRoute, "newRoute");
            Objects.requireNonNull(reason, "reason");
        }
    }

    record CancelAssignmentCommand(IncidentId incidentId, String reason) implements DispatchCommand {
        public CancelAssignmentCommand {
            Objects.requireNonNull(incidentId, "incidentId");
            Objects.requireNonNull(reason, "reason");
        }
    }
}

