package com.neca.perds.dispatch;

import com.neca.perds.model.Incident;
import com.neca.perds.model.Assignment;
import com.neca.perds.model.ResponseUnit;
import com.neca.perds.model.UnitId;
import com.neca.perds.model.UnitStatus;
import com.neca.perds.system.SystemSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        Map<UnitId, ResponseUnit> workingUnits = new HashMap<>();
        for (ResponseUnit unit : snapshot.units()) {
            workingUnits.put(unit.id(), unit);
        }
        List<Assignment> workingAssignments = new ArrayList<>(snapshot.assignments());

        List<DispatchCommand> commands = new ArrayList<>();
        for (Incident incident : incidents) {
            SystemSnapshot workingSnapshot = new SystemSnapshot(
                    snapshot.graph(),
                    snapshot.now(),
                    List.copyOf(workingUnits.values()),
                    snapshot.dispatchCentres(),
                    snapshot.incidents(),
                    List.copyOf(workingAssignments)
            );

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

            workingAssignments.add(assignment);
            ResponseUnit unit = workingUnits.get(assignment.unitId());
            if (unit != null) {
                workingUnits.put(
                        unit.id(),
                        new ResponseUnit(
                                unit.id(),
                                unit.type(),
                                UnitStatus.EN_ROUTE,
                                unit.currentNodeId(),
                                Optional.of(assignment.incidentId()),
                                unit.homeDispatchCentreId()
                        )
                );
            }
        }
        return List.copyOf(commands);
    }
}
