package com.neca.perds.sim;

import java.time.Duration;
import java.util.Objects;

public record SyntheticLoadConfig(
        Duration duration,
        int unitCount,
        int incidentCount,
        Duration prepositionInterval,
        Duration prepositionHorizon,
        int congestionEventCount,
        int unitOutageCount,
        Duration unitOutageDuration
) {
    public SyntheticLoadConfig {
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(prepositionInterval, "prepositionInterval");
        Objects.requireNonNull(prepositionHorizon, "prepositionHorizon");
        Objects.requireNonNull(unitOutageDuration, "unitOutageDuration");

        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("duration must be > 0");
        }
        if (unitCount < 0) {
            throw new IllegalArgumentException("unitCount must be >= 0");
        }
        if (incidentCount < 0) {
            throw new IllegalArgumentException("incidentCount must be >= 0");
        }
        if (prepositionInterval.isNegative()) {
            throw new IllegalArgumentException("prepositionInterval must be >= 0");
        }
        if (!prepositionInterval.isZero() && prepositionHorizon.isNegative()) {
            throw new IllegalArgumentException("prepositionHorizon must be >= 0");
        }
        if (congestionEventCount < 0) {
            throw new IllegalArgumentException("congestionEventCount must be >= 0");
        }
        if (unitOutageCount < 0) {
            throw new IllegalArgumentException("unitOutageCount must be >= 0");
        }
        if (!unitOutageDuration.isZero() && unitOutageDuration.isNegative()) {
            throw new IllegalArgumentException("unitOutageDuration must be >= 0");
        }
    }
}

