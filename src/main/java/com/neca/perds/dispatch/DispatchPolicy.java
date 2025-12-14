package com.neca.perds.dispatch;

import com.neca.perds.model.Incident;
import com.neca.perds.system.SystemSnapshot;

import java.util.Optional;

public interface DispatchPolicy {
    Optional<DispatchDecision> choose(SystemSnapshot snapshot, Incident incident);
}

