package com.neca.perds.dispatch;

import com.neca.perds.model.Incident;
import com.neca.perds.model.Assignment;
import com.neca.perds.model.ResponseUnit;
import com.neca.perds.model.UnitStatus;
import com.neca.perds.system.SystemSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class DefaultDispatchEngine implements DispatchEngine {
    private final IncidentPrioritizer incidentPrioritizer;
    private final DispatchPolicy dispatchPolicy;

    public DefaultDispatchEngine(IncidentPrioritizer incidentPrioritizer, DispatchPolicy dispatchPolicy) {
        this.incidentPrioritizer = Objects.requireNonNull(incidentPrioritizer, "incidentPrioritizer");
        this.dispatchPolicy = Objects.requireNonNull(dispatchPolicy, "dispatchPolicy");
    }

    @Override
    public List<DispatchCommand> compute(SystemSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");

        Comparator<Incident> comparator = incidentPrioritizer.comparator();
        List<Incident> incidents = snapshot.incidents().stream().sorted(comparator).toList();

        SystemSnapshot workingSnapshot = snapshot;
        List<DispatchCommand> commands = new ArrayList<>();

        for (Incident incident : incidents) {
            Optional<DispatchDecision> decision = dispatchPolicy.choose(workingSnapshot, incident);
            if (decision.isEmpty()) {
                continue;
            }

            DispatchDecision dispatchDecision = decision.get();
            var assignment = dispatchDecision.assignment();

            commands.add(new DispatchCommand.AssignUnitCommand(
                    assignment.incidentId(),
                    assignment.unitId(),
                    assignment.route(),
                    dispatchDecision.rationale()
            ));

            ResponseUnit unit = findUnit(workingSnapshot, assignment.unitId());
            if (unit != null) {
                ResponseUnit updatedUnit = unit.withStatusAndAssignment(
                        UnitStatus.EN_ROUTE,
                        Optional.of(assignment.incidentId())
                );
                workingSnapshot = workingSnapshot
                        .withUpdatedUnit(updatedUnit)
                        .withAddedAssignment(assignment);
            } else {
                workingSnapshot = workingSnapshot.withAddedAssignment(assignment);
            }
        }
        return List.copyOf(commands);
    }

    private static ResponseUnit findUnit(SystemSnapshot snapshot, com.neca.perds.model.UnitId unitId) {
        for (ResponseUnit unit : snapshot.units()) {
            if (unit.id().equals(unitId)) {
                return unit;
            }
        }
        return null;
    }
}
