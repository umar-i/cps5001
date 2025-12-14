package com.neca.perds.dispatch;

import com.neca.perds.model.Assignment;

import java.util.Objects;

public record DispatchDecision(Assignment assignment, DispatchRationale rationale) {
    public DispatchDecision {
        Objects.requireNonNull(assignment, "assignment");
        Objects.requireNonNull(rationale, "rationale");
    }
}

