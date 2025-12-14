package com.neca.perds.prediction;

import com.neca.perds.model.ZoneId;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record DemandForecast(Instant generatedAt, Duration horizon, Map<ZoneId, Double> expectedIncidents) {
    public DemandForecast {
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(horizon, "horizon");
        Objects.requireNonNull(expectedIncidents, "expectedIncidents");
    }
}

