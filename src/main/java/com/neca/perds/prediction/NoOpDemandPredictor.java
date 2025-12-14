package com.neca.perds.prediction;

import com.neca.perds.model.Incident;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public final class NoOpDemandPredictor implements DemandPredictor {
    @Override
    public void observe(Incident incident) {
        Objects.requireNonNull(incident, "incident");
    }

    @Override
    public DemandForecast forecast(Instant at, Duration horizon) {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(horizon, "horizon");
        return new DemandForecast(at, horizon, Map.of());
    }
}

