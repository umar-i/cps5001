package com.neca.perds.prediction;

import com.neca.perds.system.SystemSnapshot;

import java.util.List;
import java.util.Objects;

public final class NoOpPrepositioningStrategy implements PrepositioningStrategy {
    @Override
    public RepositionPlan plan(SystemSnapshot snapshot, DemandForecast forecast) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(forecast, "forecast");
        return new RepositionPlan(List.of());
    }
}

