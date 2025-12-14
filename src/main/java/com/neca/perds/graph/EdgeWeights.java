package com.neca.perds.graph;

import java.time.Duration;
import java.util.Objects;

public record EdgeWeights(double distanceKm, Duration travelTime, double resourceAvailability) {
    public EdgeWeights {
        Objects.requireNonNull(travelTime, "travelTime");
        if (distanceKm < 0) {
            throw new IllegalArgumentException("distanceKm must be >= 0");
        }
        if (travelTime.isNegative()) {
            throw new IllegalArgumentException("travelTime must be >= 0");
        }
        if (resourceAvailability < 0 || resourceAvailability > 1) {
            throw new IllegalArgumentException("resourceAvailability must be in [0, 1]");
        }
    }
}

