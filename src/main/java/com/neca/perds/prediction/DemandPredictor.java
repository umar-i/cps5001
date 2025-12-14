package com.neca.perds.prediction;

import com.neca.perds.model.Incident;

import java.time.Duration;
import java.time.Instant;

public interface DemandPredictor {
    void observe(Incident incident);

    DemandForecast forecast(Instant at, Duration horizon);
}

