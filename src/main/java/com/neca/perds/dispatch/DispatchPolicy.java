package com.neca.perds.dispatch;

import com.neca.perds.model.Incident;
import com.neca.perds.system.SystemSnapshot;

import java.util.List;
import java.util.Optional;

public interface DispatchPolicy {
    /**
     * Chooses a single unit to dispatch for the given incident.
     * This is the legacy method that dispatches only one unit.
     *
     * @param snapshot current system state
     * @param incident the incident requiring dispatch
     * @return a dispatch decision if a suitable unit is found
     */
    Optional<DispatchDecision> choose(SystemSnapshot snapshot, Incident incident);

    /**
     * Chooses units to dispatch for all required unit types of the incident.
     * For multi-unit incidents (requiring multiple unit types), this returns
     * one dispatch decision per required unit type that can be fulfilled.
     *
     * <p>Default implementation delegates to {@link #choose} for backward compatibility.
     *
     * @param snapshot current system state
     * @param incident the incident requiring dispatch
     * @return list of dispatch decisions, one per fulfilled unit type requirement
     */
    default List<DispatchDecision> chooseAll(SystemSnapshot snapshot, Incident incident) {
        return choose(snapshot, incident).map(List::of).orElse(List.of());
    }
}

