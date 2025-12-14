package com.neca.perds.prediction;

import java.util.List;
import java.util.Objects;

public record RepositionPlan(List<RepositionMove> moves) {
    public RepositionPlan {
        Objects.requireNonNull(moves, "moves");
    }
}

