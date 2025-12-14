package com.neca.perds.prediction;

import com.neca.perds.model.Incident;

import java.time.Duration;
import java.time.Instant;

public final class ExponentialSmoothingDemandPredictor implements DemandPredictor {
    @Override
    public void observe(Incident incident) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public DemandForecast forecast(Instant at, Duration horizon) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}

